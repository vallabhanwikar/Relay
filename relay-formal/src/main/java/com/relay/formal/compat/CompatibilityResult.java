package com.relay.formal.compat;

import com.relay.formal.json.Json;
import com.relay.formal.schema.FieldPath;

import java.time.Duration;
import java.util.List;

/**
 * The verdict for one {@link CompatibilityQuery}. Four outcomes, never collapsed:
 * an {@link Unproven} or {@link Failed} result is not a pass, and nothing downstream may treat
 * it as one (design v3, "Core concepts").
 */
public sealed interface CompatibilityResult
        permits CompatibilityResult.Proven, CompatibilityResult.CounterexampleFound,
        CompatibilityResult.Unproven, CompatibilityResult.Failed {

    /** Conditions the verdict holds under, in plain English. Always shown with the verdict. */
    List<String> assumptions();

    Solver solver();

    Duration elapsed();

    /** The solver and exact version that produced a verdict - recorded so it can be replayed. */
    record Solver(String name, String version) {
    }

    /** The solver showed that no payload breaks the consumer. Evidence level E6b. */
    record Proven(List<String> assumptions, Solver solver, Duration elapsed) implements CompatibilityResult {
        public Proven {
            assumptions = List.copyOf(assumptions);
        }
    }

    /**
     * A concrete payload that the new contract allows and the consumer does not accept.
     *
     * @param witness        the payload as a JSON value tree ({@link Json} representation)
     * @param violations     which consumer expectations the witness breaks
     * @param exact          true when every constraint involved was encoded exactly; false when
     *                       the witness relies on an opaque predicate (a {@code pattern} or
     *                       {@code format}) or an over-approximation, so replay must confirm it
     * @param approximations why the witness is not exact, if it is not
     */
    record CounterexampleFound(
            Object witness,
            List<Violation> violations,
            boolean exact,
            List<String> approximations,
            List<String> assumptions,
            Solver solver,
            Duration elapsed
    ) implements CompatibilityResult {
        public CounterexampleFound {
            violations = List.copyOf(violations);
            approximations = List.copyOf(approximations);
            assumptions = List.copyOf(assumptions);
        }

        public String witnessJson() {
            return Json.writePretty(witness);
        }
    }

    /** The solver could not decide - timeout, resource limit, or an incomplete theory. */
    record Unproven(String reason, List<String> assumptions, Solver solver, Duration elapsed)
            implements CompatibilityResult {
        public Unproven {
            assumptions = List.copyOf(assumptions);
        }
    }

    /** The check could not run: malformed schema, solver error. Treated as a tool fault. */
    record Failed(String error, List<String> assumptions, Solver solver, Duration elapsed)
            implements CompatibilityResult {
        public Failed {
            assumptions = List.copyOf(assumptions);
        }
    }

    /** One consumer expectation the witness breaks, e.g. "status must be one of [PAID, FAILED]". */
    record Violation(FieldPath path, String expectation) {
        @Override
        public String toString() {
            return path + ": " + expectation;
        }
    }
}
