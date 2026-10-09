package com.relay.formal.smt;

import com.microsoft.z3.ArithExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.RatNum;
import com.microsoft.z3.RealExpr;
import com.microsoft.z3.RealSort;
import com.relay.formal.compat.Usage.Tolerance;
import com.relay.formal.schema.FieldPath;
import com.relay.formal.schema.Schema;
import com.relay.formal.schema.Schema.AnySchema;
import com.relay.formal.schema.Schema.ArraySchema;
import com.relay.formal.schema.Schema.BooleanSchema;
import com.relay.formal.schema.Schema.CompositeSchema;
import com.relay.formal.schema.Schema.NeverSchema;
import com.relay.formal.schema.Schema.NullSchema;
import com.relay.formal.schema.Schema.NumberSchema;
import com.relay.formal.schema.Schema.ObjectSchema;
import com.relay.formal.schema.Schema.StringSchema;
import com.relay.formal.schema.Schema.TruncatedSchema;
import com.relay.formal.smt.SymbolicPayload.Kind;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Encodes "this payload is valid under this schema" as SMT clauses, restricted to a set of
 * relevant paths.
 *
 * <p>Each clause is one human-readable requirement at one path ("status must be one of [PAID,
 * FAILED]"), guarded so that it only bites when the path is present and holds the right kind.
 * Keeping requirements separate is what lets the checker report exactly which expectation a
 * counterexample breaks, instead of "the formula is false".
 *
 * <p><b>Projection.</b> Requirements are emitted only for paths in the relevant set. On the
 * producer's side that over-approximates what the producer may send, which can only add
 * counterexamples (each of which is replayed). On the consumer's side it is the definition of
 * the consumer's expectation: the old contract, as far as the consumer's code uses it.
 */
public final class SchemaEncoder {

    /** One requirement at one path. */
    public record Clause(FieldPath path, String requirement, BoolExpr formula) {
    }

    private final SymbolicPayload payload;
    private final Context ctx;
    private final Set<FieldPath> relevant;
    private final Function<FieldPath, Set<Tolerance>> tolerances;
    private final BigDecimal multipleOfScale;
    private final Set<String> truncations = new TreeSet<>();

    /**
     * @param relevant   the paths to constrain; must be closed under ancestors
     * @param tolerances consumer tolerances per path; only applied when encoding expectations
     * @param multipleOfScale a power of ten that turns every {@code multipleOf} in play into a
     *                        whole number; see {@code SchemaNavigator.multipleOfScale}
     */
    public SchemaEncoder(SymbolicPayload payload, Set<FieldPath> relevant,
                         Function<FieldPath, Set<Tolerance>> tolerances, BigDecimal multipleOfScale) {
        this.payload = payload;
        this.multipleOfScale = multipleOfScale;
        this.ctx = payload.ctx();
        this.relevant = relevant;
        this.tolerances = tolerances;
    }

    /** Clauses requiring the payload root to be valid under {@code schema}. */
    public List<Clause> encode(Schema schema) {
        List<Clause> clauses = new ArrayList<>();
        encodeAt(schema, FieldPath.ROOT, clauses);
        return clauses;
    }

    /** Subtrees the encoder could not see into, e.g. recursive references past the bound. */
    public Set<String> truncations() {
        return Set.copyOf(truncations);
    }

    private void encodeAt(Schema schema, FieldPath path, List<Clause> out) {
        BoolExpr present = payload.present(path);
        switch (schema) {
            case AnySchema any -> {
                // Anything goes, including below this path.
            }
            case TruncatedSchema truncated -> truncations.add(path + ": " + truncated.reason());
            case NeverSchema never -> out.add(new Clause(path, "must be absent", ctx.mkNot(present)));
            case NullSchema nul -> out.add(new Clause(path, "must be null",
                    ctx.mkImplies(present, payload.isKind(path, Kind.NULL))));
            case CompositeSchema composite -> encodeComposite(composite, path, out);
            case BooleanSchema bool -> encodeBoolean(bool, path, out);
            case NumberSchema number -> encodeNumber(number, path, out);
            case StringSchema string -> encodeString(string, path, out);
            case ObjectSchema object -> encodeObject(object, path, out);
            case ArraySchema array -> encodeArray(array, path, out);
        }
    }

    private void encodeComposite(CompositeSchema composite, FieldPath path, List<Clause> out) {
        if (composite.mode() == CompositeSchema.Mode.ALL_OF) {
            composite.options().forEach(option -> encodeAt(option, path, out));
            return;
        }
        // oneOf is encoded as anyOf. On the producer's side that is an over-approximation (sound);
        // on the consumer's side it means the consumer handles every declared variant.
        List<BoolExpr> alternatives = new ArrayList<>();
        for (Schema option : composite.options()) {
            List<Clause> branch = new ArrayList<>();
            encodeAt(option, path, branch);
            alternatives.add(ctx.mkAnd(branch.stream().map(Clause::formula).toArray(BoolExpr[]::new)));
        }
        String keyword = composite.mode() == CompositeSchema.Mode.ONE_OF ? "oneOf" : "anyOf";
        out.add(new Clause(path, "must match one of " + alternatives.size() + " " + keyword + " alternatives",
                ctx.mkOr(alternatives.toArray(BoolExpr[]::new))));
    }

    /**
     * The clauses shared by every typed schema: nullability and kind. Returns the guard under
     * which value constraints apply ("present and of this kind").
     */
    private BoolExpr typed(FieldPath path, Kind kind, String kindName, boolean nullable, List<Clause> out) {
        BoolExpr present = payload.present(path);
        BoolExpr isNull = payload.isKind(path, Kind.NULL);
        boolean nullSafe = nullable || tolerances.apply(path).contains(Tolerance.NULL_SAFE);
        if (!nullSafe) {
            out.add(new Clause(path, "must not be null", ctx.mkImplies(present, ctx.mkNot(isNull))));
        }
        out.add(new Clause(path, "must be " + kindName,
                ctx.mkImplies(ctx.mkAnd(present, ctx.mkNot(isNull)), payload.isKind(path, kind))));
        return ctx.mkAnd(present, payload.isKind(path, kind));
    }

    private void encodeBoolean(BooleanSchema schema, FieldPath path, List<Clause> out) {
        BoolExpr guard = typed(path, Kind.BOOLEAN, "a boolean", schema.nullable(), out);
        if (!schema.enumValues().isEmpty() && !enumTolerant(path)) {
            BoolExpr value = payload.vars(path).bool();
            BoolExpr[] options = schema.enumValues().stream()
                    .map(b -> ctx.mkEq(value, ctx.mkBool(b))).toArray(BoolExpr[]::new);
            out.add(new Clause(path, "must be one of " + schema.enumValues(),
                    ctx.mkImplies(guard, ctx.mkOr(options))));
        }
    }

    private void encodeNumber(NumberSchema schema, FieldPath path, List<Clause> out) {
        BoolExpr guard = typed(path, Kind.NUMBER, schema.integer() ? "an integer" : "a number",
                schema.nullable(), out);
        RealExpr value = payload.vars(path).number();
        if (schema.integer()) {
            out.add(new Clause(path, "must be a whole number",
                    ctx.mkImplies(guard, ctx.mkIsInteger(value))));
        }
        if (schema.minimum() != null) {
            RatNum bound = payload.decimal(schema.minimum());
            BoolExpr ok = schema.exclusiveMinimum() ? ctx.mkGt(value, bound) : ctx.mkGe(value, bound);
            out.add(new Clause(path, "must be " + (schema.exclusiveMinimum() ? "> " : ">= ") + plain(schema.minimum()),
                    ctx.mkImplies(guard, ok)));
        }
        if (schema.maximum() != null) {
            RatNum bound = payload.decimal(schema.maximum());
            BoolExpr ok = schema.exclusiveMaximum() ? ctx.mkLt(value, bound) : ctx.mkLe(value, bound);
            out.add(new Clause(path, "must be " + (schema.exclusiveMaximum() ? "< " : "<= ") + plain(schema.maximum()),
                    ctx.mkImplies(guard, ok)));
        }
        if (schema.multipleOf() != null) {
            // value = k * step  <=>  value * D is a whole number t with t mod (step * D) = 0, where D
            // is one power of ten shared by every multipleOf in both schemas. Sharing D keeps both
            // sides on the same integer t, so the question stays in linear integer arithmetic;
            // a per-step encoding (value / step is an integer) mixes reals and integers and Z3
            // times out on it even for multipleOf 0.1 versus 0.01.
            BigInteger modulus = schema.multipleOf().multiply(multipleOfScale).toBigIntegerExact();
            ArithExpr<RealSort> scaled = ctx.mkMul(value, ctx.mkReal(multipleOfScale.toPlainString()));
            BoolExpr whole = ctx.mkAnd(ctx.mkIsInteger(scaled), ctx.mkEq(
                    ctx.mkMod(ctx.mkReal2Int(scaled), ctx.mkInt(modulus.toString())), ctx.mkInt(0)));
            out.add(new Clause(path, "must be a multiple of " + plain(schema.multipleOf()),
                    ctx.mkImplies(guard, whole)));
        }
        if (!schema.enumValues().isEmpty() && !enumTolerant(path)) {
            BoolExpr[] options = schema.enumValues().stream()
                    .map(v -> ctx.mkEq(value, payload.decimal(v))).toArray(BoolExpr[]::new);
            out.add(new Clause(path, "must be one of " + schema.enumValues().stream().map(SchemaEncoder::plain).toList(),
                    ctx.mkImplies(guard, ctx.mkOr(options))));
        }
    }

    private void encodeString(StringSchema schema, FieldPath path, List<Clause> out) {
        BoolExpr guard = typed(path, Kind.STRING, "a string", schema.nullable(), out);
        var value = payload.vars(path).string();
        if (!schema.enumValues().isEmpty() && !enumTolerant(path)) {
            BoolExpr[] options = schema.enumValues().stream()
                    .map(v -> ctx.mkEq(value, payload.stringLiteral(v))).toArray(BoolExpr[]::new);
            out.add(new Clause(path, "must be one of " + schema.enumValues(),
                    ctx.mkImplies(guard, ctx.mkOr(options))));
        }
        if (schema.minLength() != null) {
            out.add(new Clause(path, "must be at least " + schema.minLength() + " characters",
                    ctx.mkImplies(guard, ctx.mkGe(ctx.mkLength(value), ctx.mkInt(schema.minLength())))));
        }
        if (schema.maxLength() != null) {
            out.add(new Clause(path, "must be at most " + schema.maxLength() + " characters",
                    ctx.mkImplies(guard, ctx.mkLe(ctx.mkLength(value), ctx.mkInt(schema.maxLength())))));
        }
        if (schema.pattern() != null) {
            out.add(new Clause(path, "must match pattern " + schema.pattern(),
                    ctx.mkImplies(guard, payload.opaque("pattern:" + schema.pattern(),
                            "pattern " + schema.pattern(), value))));
        }
        if (schema.format() != null) {
            out.add(new Clause(path, "must have format " + schema.format(),
                    ctx.mkImplies(guard, payload.opaque("format:" + schema.format(),
                            "format " + schema.format(), value))));
        }
    }

    private void encodeObject(ObjectSchema schema, FieldPath path, List<Clause> out) {
        BoolExpr guard = typed(path, Kind.OBJECT, "an object", schema.nullable(), out);
        for (FieldPath child : relevantChildren(path)) {
            if (child.isItems()) {
                continue;
            }
            String name = child.last();
            Schema declared = schema.properties().get(name);
            if (declared != null) {
                boolean nullSafe = tolerances.apply(child).contains(Tolerance.NULL_SAFE);
                if (schema.required().contains(name) && !nullSafe) {
                    out.add(new Clause(child, "must be present",
                            ctx.mkImplies(guard, payload.present(child))));
                }
                encodeAt(declared, child, out);
            } else if (schema.additionalProperties() instanceof NeverSchema) {
                out.add(new Clause(child, "must be absent (not declared, additionalProperties: false)",
                        ctx.mkImplies(guard, ctx.mkNot(payload.present(child)))));
            } else {
                encodeAt(schema.additionalProperties(), child, out);
            }
        }
    }

    private void encodeArray(ArraySchema schema, FieldPath path, List<Clause> out) {
        BoolExpr guard = typed(path, Kind.ARRAY, "an array", schema.nullable(), out);
        var length = payload.vars(path).length();
        if (schema.minItems() != null) {
            out.add(new Clause(path, "must have at least " + schema.minItems() + " items",
                    ctx.mkImplies(guard, ctx.mkGe(length, ctx.mkInt(schema.minItems())))));
        }
        if (schema.maxItems() != null) {
            out.add(new Clause(path, "must have at most " + schema.maxItems() + " items",
                    ctx.mkImplies(guard, ctx.mkLe(length, ctx.mkInt(schema.maxItems())))));
        }
        FieldPath items = path.items();
        if (relevant.contains(items)) {
            encodeAt(schema.items(), items, out);
        }
    }

    private boolean enumTolerant(FieldPath path) {
        return tolerances.apply(path).contains(Tolerance.UNKNOWN_ENUM_SAFE);
    }

    private List<FieldPath> relevantChildren(FieldPath path) {
        List<FieldPath> children = new ArrayList<>();
        for (FieldPath candidate : relevant) {
            if (!candidate.isRoot() && candidate.depth() == path.depth() + 1 && candidate.parent().equals(path)) {
                children.add(candidate);
            }
        }
        return children;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /** "The path is present and holds an object" - the guard for per-property requirements. */
    public BoolExpr isObject(FieldPath path) {
        return ctx.mkAnd(payload.present(path), payload.isKind(path, Kind.OBJECT));
    }
}
