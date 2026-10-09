package com.relay.formal.compat;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Model;
import com.microsoft.z3.Params;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;
import com.microsoft.z3.Version;
import com.microsoft.z3.Z3Exception;
import com.relay.formal.compat.CompatibilityQuery.Direction;
import com.relay.formal.compat.CompatibilityResult.CounterexampleFound;
import com.relay.formal.compat.CompatibilityResult.Failed;
import com.relay.formal.compat.CompatibilityResult.Proven;
import com.relay.formal.compat.CompatibilityResult.Unproven;
import com.relay.formal.compat.CompatibilityResult.Violation;
import com.relay.formal.schema.FieldPath;
import com.relay.formal.schema.Schema;
import com.relay.formal.schema.SchemaNavigator;
import com.relay.formal.smt.SchemaEncoder;
import com.relay.formal.smt.SchemaEncoder.Clause;
import com.relay.formal.smt.SymbolicPayload;
import com.relay.formal.smt.WitnessDecoder;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Decides whether a schema change can break one consumer, with Z3.
 *
 * <p>The question is always existential: is there a payload that one side may produce and the
 * other side does not accept?
 *
 * <pre>
 *   RESPONSE:  exists p.  ValidNew(p)  and not Expects(p)    (Expects = old contract, scoped to usage)
 *   REQUEST:   exists p.  Sends(p)     and not AcceptsNew(p) (Sends   = old contract, scoped to usage)
 * </pre>
 *
 * <p>UNSAT is a proof that no such payload exists: {@link Proven}. SAT yields a model, decoded
 * into a concrete JSON witness: {@link CounterexampleFound}. Soundness rests on one rule - the
 * producing side may be over-approximated (more payloads), the accepting side may not. Every
 * encoding shortcut in {@link SchemaEncoder} respects it, and the ones that make a witness
 * inexact are listed on the result.
 */
public final class SchemaCompatibilityChecker {

    /** Smallest array length bound explored, before schema constants raise it. */
    private static final int MIN_LENGTH_BOUND = 3;

    public CompatibilityResult check(CompatibilityQuery query) {
        long started = System.nanoTime();
        List<String> assumptions = new ArrayList<>();
        CompatibilityResult.Solver solver = new CompatibilityResult.Solver("z3", z3Version());
        try (Context ctx = new Context()) {
            return run(query, ctx, assumptions, solver, started);
        } catch (Z3Exception | IllegalArgumentException | IllegalStateException e) {
            return new Failed(e.getClass().getSimpleName() + ": " + e.getMessage(), assumptions, solver,
                    elapsedSince(started));
        }
    }

    private CompatibilityResult run(CompatibilityQuery query, Context ctx, List<String> assumptions,
                                    CompatibilityResult.Solver solverInfo, long started) {
        SymbolicPayload payload = new SymbolicPayload(ctx);
        Set<String> truncations = new TreeSet<>();
        Scope scope = query.direction() == Direction.RESPONSE
                ? responseScope(query, truncations)
                : requestScope(query, truncations);
        Schema producer = query.direction() == Direction.RESPONSE ? query.newSchema() : query.oldSchema();
        Schema acceptor = query.direction() == Direction.RESPONSE ? query.oldSchema() : query.newSchema();

        // The producing side also constrains what it requires beyond the consumer's scope, so the
        // witness is a whole valid document. Adding true constraints keeps the encoding sound.
        Set<FieldPath> producerScope = query.direction() == Direction.RESPONSE
                ? SchemaNavigator.withRequired(producer, scope.relevant(), query.maxDepth())
                : scope.relevant();
        producerScope.forEach(payload::vars);

        BigDecimal multipleOfScale = SchemaNavigator.multipleOfScale(query.oldSchema(), query.newSchema());
        SchemaEncoder producerEncoder = new SchemaEncoder(payload, producerScope, p -> Set.of(), multipleOfScale);
        List<Clause> produces = new ArrayList<>(producerEncoder.encode(producer));

        SchemaEncoder acceptorEncoder = new SchemaEncoder(payload, scope.relevant(),
                query.direction() == Direction.RESPONSE ? query.usage()::tolerancesAt : p -> Set.of(),
                multipleOfScale);
        List<Clause> accepts = new ArrayList<>(acceptorEncoder.encode(acceptor));

        for (FieldPath path : scope.notSent()) {
            produces.add(new Clause(path, "is not sent by the consumer",
                    ctx.mkNot(payload.present(path))));
        }
        for (FieldPath path : scope.unknownToConsumer()) {
            accepts.add(new Clause(path, "is not a field the consumer's mapper knows (FAIL_ON_UNKNOWN_PROPERTIES)",
                    ctx.mkImplies(acceptorEncoder.isObject(path.parent()), ctx.mkNot(payload.present(path)))));
        }
        truncations.addAll(producerEncoder.truncations());
        truncations.addAll(acceptorEncoder.truncations());

        int lengthBound = Math.max(MIN_LENGTH_BOUND, 2 + Math.max(
                SchemaNavigator.largestArrayLengthConstant(query.oldSchema()),
                SchemaNavigator.largestArrayLengthConstant(query.newSchema())));

        describeAssumptions(query, scope, truncations, payload, lengthBound, assumptions);

        Solver solver = ctx.mkSolver();
        Params params = ctx.mkParams();
        params.add("timeout", (int) Math.min(Integer.MAX_VALUE, query.timeout().toMillis()));
        solver.setParameters(params);
        payload.wellFormedness(lengthBound).forEach(solver::add);
        produces.forEach(c -> solver.add(c.formula()));

        // Vacuity guard: if the producing side admits no payload at all, UNSAT below would be a
        // "proof" about nothing. That is a modelling error, not a result.
        Status producible = solver.check();
        if (producible == Status.UNSATISFIABLE) {
            return new Unproven("vacuous: no payload satisfies the producing side's constraints; "
                    + "check the schema and the declared usage", assumptions, solverInfo, elapsedSince(started));
        }
        if (producible == Status.UNKNOWN) {
            return new Unproven("solver returned unknown on the producing side: " + solver.getReasonUnknown(),
                    assumptions, solverInfo, elapsedSince(started));
        }

        solver.add(ctx.mkNot(ctx.mkAnd(accepts.stream().map(Clause::formula).toArray(BoolExpr[]::new))));

        Status status = solver.check();
        if (status == Status.UNSATISFIABLE) {
            return new Proven(assumptions, solverInfo, elapsedSince(started));
        }
        if (status == Status.UNKNOWN) {
            return new Unproven("solver returned unknown: " + solver.getReasonUnknown(), assumptions, solverInfo,
                    elapsedSince(started));
        }

        Model model = smallestModel(ctx, solver, payload);
        List<Violation> violations = new ArrayList<>();
        for (Clause clause : accepts) {
            if (model.eval(clause.formula(), true).isFalse()) {
                violations.add(new Violation(clause.path(), clause.requirement()));
            }
        }
        violations.sort(Comparator.comparing(Violation::path).thenComparing(Violation::expectation));

        WitnessDecoder.Witness witness = WitnessDecoder.decode(model, payload);
        Set<String> approximations = new TreeSet<>(witness.approximations());
        payload.opaqueDescriptions().forEach(d -> approximations.add(d + " is modelled as an opaque predicate"));
        truncations.forEach(t -> approximations.add("not expanded: " + t));
        query.ignoredKeywords().forEach(k -> approximations.add("schema keyword '" + k + "' is not modelled"));

        return new CounterexampleFound(witness.value(), violations, approximations.isEmpty(),
                List.copyOf(approximations), assumptions, solverInfo, elapsedSince(started));
    }

    /**
     * Prefer a small witness - short arrays and strings - when one exists. A witness is read by
     * a person and replayed as a test, so "lines": [{...}] beats ten copies of the same line.
     */
    private static Model smallestModel(Context ctx, Solver solver, SymbolicPayload payload) {
        solver.push();
        for (SymbolicPayload.PathVars v : payload.paths().values()) {
            solver.add(ctx.mkLe(v.length(), ctx.mkInt(1)));
            solver.add(ctx.mkLe(ctx.mkLength(v.string()), ctx.mkInt(24)));
        }
        if (solver.check() == Status.SATISFIABLE) {
            Model small = solver.getModel();
            solver.pop();
            return small;
        }
        solver.pop();
        solver.check();
        return solver.getModel();
    }

    /** The paths in play, and which of them get extra directional constraints. */
    private record Scope(Set<FieldPath> relevant, Set<FieldPath> notSent, Set<FieldPath> unknownToConsumer) {
    }

    /**
     * Response: the consumer's read paths (each covering its subtree under the old contract), plus
     * their ancestors. With a strict mapper, fields the new schema adds next to read objects also
     * matter, because their mere presence fails deserialisation.
     */
    private static Scope responseScope(CompatibilityQuery query, Set<String> truncations) {
        Set<FieldPath> used = expandUsage(query, truncations);
        Set<FieldPath> relevant = withAncestors(used);
        Set<FieldPath> unknown = new TreeSet<>();
        if (query.rejectsUnknownProperties()) {
            for (FieldPath path : List.copyOf(relevant)) {
                Set<String> oldNames = namesAt(query.oldSchema(), path);
                for (String name : namesAt(query.newSchema(), path)) {
                    if (!oldNames.contains(name)) {
                        unknown.add(path.child(name));
                    }
                }
            }
            relevant.addAll(unknown);
        }
        return new Scope(relevant, Set.of(), unknown);
    }

    /**
     * Request: the fields the consumer sends (each covering its subtree under the old contract),
     * plus every property either schema declares next to them. The consumer never sends the
     * latter, and including them is what lets the solver see a newly required field.
     */
    private static Scope requestScope(CompatibilityQuery query, Set<String> truncations) {
        // A consumer that worked under the old contract necessarily sent what it required, even if
        // the declared usage forgot to list it; otherwise "not sent" and "required" would make the
        // producing side contradictory and every check vacuously proven.
        Set<FieldPath> sent = SchemaNavigator.withRequired(query.oldSchema(),
                withAncestors(expandUsage(query, truncations)), query.maxDepth());
        Set<FieldPath> relevant = new TreeSet<>(sent);
        for (FieldPath path : sent) {
            Set<String> names = new LinkedHashSet<>(namesAt(query.oldSchema(), path));
            names.addAll(namesAt(query.newSchema(), path));
            names.forEach(name -> relevant.add(path.child(name)));
        }
        Set<FieldPath> notSent = new TreeSet<>(relevant);
        notSent.removeAll(sent);
        return new Scope(relevant, notSent, Set.of());
    }

    private static Set<FieldPath> expandUsage(CompatibilityQuery query, Set<String> truncations) {
        Set<FieldPath> bases = query.usage().isAllFields() ? Set.of(FieldPath.ROOT) : query.usage().paths();
        Set<FieldPath> expanded = new TreeSet<>();
        for (FieldPath base : bases) {
            expanded.addAll(SchemaNavigator.enumerate(query.oldSchema(), base, query.maxDepth(), truncations));
        }
        return expanded;
    }

    private static Set<FieldPath> withAncestors(Set<FieldPath> paths) {
        Set<FieldPath> result = new TreeSet<>();
        paths.forEach(p -> result.addAll(p.selfAndAncestors()));
        return result;
    }

    private static Set<String> namesAt(Schema root, FieldPath path) {
        Set<String> names = new LinkedHashSet<>();
        SchemaNavigator.at(root, path).forEach(s -> names.addAll(SchemaNavigator.declaredNames(s)));
        return names;
    }

    private static void describeAssumptions(CompatibilityQuery query, Scope scope, Set<String> truncations,
                                            SymbolicPayload payload, int lengthBound, List<String> out) {
        if (query.direction() == Direction.RESPONSE) {
            out.add("the consumer accepts what the old schema promised for the fields it uses");
            if (!query.rejectsUnknownProperties()) {
                out.add("the consumer's JSON mapper ignores unknown properties (Spring Boot default)");
            }
        } else {
            out.add("the consumer sends only what the old schema allowed, for the fields it sends");
        }
        out.add(query.usage().isAllFields()
                ? "usage unknown: the consumer is assumed to use every field the old schema declares"
                : "the consumer uses only " + query.usage().paths() + " (and everything beneath them)");
        for (FieldPath path : query.usage().paths()) {
            for (Usage.Tolerance tolerance : query.usage().tolerancesAt(path)) {
                out.add(path + " is handled as " + tolerance + " by consumer code");
            }
        }
        truncations.forEach(t -> out.add("not checked: " + t));
        query.ignoredKeywords().forEach(k -> out.add("schema keyword '" + k + "' is not modelled"));
        payload.opaqueDescriptions().forEach(d -> out.add(d + " is compared symbolically, not evaluated"));
        out.add("arrays explored up to " + (lengthBound - 1)
                + " items; complete, since every length bound in either schema is smaller");
    }

    private static Duration elapsedSince(long started) {
        return Duration.ofNanos(System.nanoTime() - started);
    }

    private static String z3Version() {
        try {
            return Version.getFullVersion();
        } catch (Throwable e) {
            return "unknown";
        }
    }
}
