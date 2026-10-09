package com.relay.formal.smt;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.BoolSort;
import com.microsoft.z3.CharSort;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FuncDecl;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.RatNum;
import com.microsoft.z3.RealExpr;
import com.microsoft.z3.SeqExpr;
import com.microsoft.z3.Sort;
import com.relay.formal.schema.FieldPath;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * One unknown JSON payload, described path by path as SMT variables.
 *
 * <p>Every path gets the same six variables: whether it is present, which JSON kind it holds,
 * and one value variable per scalar kind plus a length for arrays. Exactly one value variable
 * is meaningful, selected by the kind. {@link #wellFormedness(int)} adds the axioms that make
 * any model a real JSON document: a present child implies an object parent, array elements
 * exist exactly when the array is non-empty.
 *
 * <p><b>Arrays use one representative element.</b> {@code lines[]} stands for every element of
 * {@code lines}. This is complete for the constraints the encoder emits: they are per-element,
 * so if some element breaks an expectation, the payload whose elements are all copies of that
 * element breaks it too, and is still valid under the producer's schema. ({@code uniqueItems}
 * is the exception, which is why the reader reports it as not modelled.)
 */
public final class SymbolicPayload {

    /** JSON value kinds, as stored in each path's {@code kind} variable. */
    public enum Kind {
        NULL, BOOLEAN, NUMBER, STRING, OBJECT, ARRAY;

        public static Kind of(int code) {
            return values()[code];
        }
    }

    /** The variables describing one path. */
    public record PathVars(
            FieldPath path,
            BoolExpr present,
            IntExpr kind,
            SeqExpr<CharSort> string,
            RealExpr number,
            BoolExpr bool,
            IntExpr length
    ) {
    }

    private final Context ctx;
    private final SortedMap<FieldPath, PathVars> paths = new TreeMap<>();
    private final Map<String, FuncDecl<BoolSort>> opaque = new LinkedHashMap<>();
    private final List<String> opaqueDescriptions = new ArrayList<>();

    public SymbolicPayload(Context ctx) {
        this.ctx = ctx;
        vars(FieldPath.ROOT);
    }

    public Context ctx() {
        return ctx;
    }

    /** The variables for {@code path}, creating them (and its ancestors') on first use. */
    public PathVars vars(FieldPath path) {
        PathVars existing = paths.get(path);
        if (existing != null) {
            return existing;
        }
        if (!path.isRoot()) {
            vars(path.parent());
        }
        String id = "p" + paths.size();
        PathVars created = new PathVars(
                path,
                ctx.mkBoolConst(id + "_present"),
                ctx.mkIntConst(id + "_kind"),
                (SeqExpr<CharSort>) ctx.mkConst(id + "_str", ctx.getStringSort()),
                ctx.mkRealConst(id + "_num"),
                ctx.mkBoolConst(id + "_bool"),
                ctx.mkIntConst(id + "_len"));
        paths.put(path, created);
        return created;
    }

    public SortedMap<FieldPath, PathVars> paths() {
        return Collections.unmodifiableSortedMap(paths);
    }

    public BoolExpr present(FieldPath path) {
        return vars(path).present();
    }

    public BoolExpr isKind(FieldPath path, Kind kind) {
        return ctx.mkEq(vars(path).kind(), ctx.mkInt(kind.ordinal()));
    }

    /**
     * An uninterpreted predicate standing for a check the solver does not model natively - a
     * regex {@code pattern} or a {@code format}. The same key always yields the same predicate,
     * which is what makes this sound: when both schemas require {@code format: date-time}, both
     * sides constrain the same unknown predicate and the requirement cancels out. When only the
     * consumer's side requires it, the solver is free to make it false and reports a candidate
     * counterexample, which is then marked inexact for replay to confirm.
     */
    public BoolExpr opaque(String key, String description, Expr<?> argument) {
        FuncDecl<BoolSort> predicate = opaque.computeIfAbsent(key, k -> {
            opaqueDescriptions.add(description);
            Sort domain = argument.getSort();
            return ctx.mkFuncDecl("opaque" + opaque.size(), domain, ctx.getBoolSort());
        });
        return (BoolExpr) predicate.apply(argument);
    }

    public List<String> opaqueDescriptions() {
        return List.copyOf(opaqueDescriptions);
    }

    public SeqExpr<CharSort> stringLiteral(String value) {
        // Context.mkString escapes control and non-ASCII characters but not the backslash, so a
        // literal backslash-u{41} in a JSON value would be read back as "A". Escape it first.
        return ctx.mkString(value.replace("\\", "\\u{5c}"));
    }

    public RatNum decimal(BigDecimal value) {
        return ctx.mkReal(value.toPlainString());
    }

    /**
     * Axioms every real JSON payload satisfies, for all paths created so far.
     *
     * @param lengthBound exclusive upper bound on array lengths explored. Sound as long as it is
     *                    larger than every length constant in either schema: lengths only appear
     *                    in comparisons with those constants, so any longer array behaves
     *                    exactly like one of length {@code lengthBound - 1}.
     */
    public List<BoolExpr> wellFormedness(int lengthBound) {
        List<BoolExpr> axioms = new ArrayList<>();
        for (PathVars v : paths.values()) {
            FieldPath path = v.path();
            axioms.add(ctx.mkLe(ctx.mkInt(0), v.kind()));
            axioms.add(ctx.mkLe(v.kind(), ctx.mkInt(Kind.values().length - 1)));
            axioms.add(ctx.mkLe(ctx.mkInt(0), v.length()));
            axioms.add(ctx.mkLt(v.length(), ctx.mkInt(lengthBound)));
            if (path.isRoot()) {
                axioms.add(v.present());
            } else if (path.isItems()) {
                FieldPath array = path.parent();
                axioms.add(ctx.mkEq(v.present(), ctx.mkAnd(
                        present(array),
                        isKind(array, Kind.ARRAY),
                        ctx.mkGt(vars(array).length(), ctx.mkInt(0)))));
            } else {
                FieldPath parent = path.parent();
                axioms.add(ctx.mkImplies(v.present(),
                        ctx.mkAnd(present(parent), isKind(parent, Kind.OBJECT))));
            }
        }
        return axioms;
    }
}
