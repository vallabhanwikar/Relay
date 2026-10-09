package com.relay.shared;

import java.util.List;
import java.util.Objects;

/**
 * A concrete witness that an obligation fails (design v3, "Counterexample").
 *
 * <p>Produced by {@code proof}, rendered by {@code evidence}, consumed by {@code repair} for its
 * bounded retry loop - three modules, which is why it lives in the shared kernel. A witness is
 * replayed as a generated test before it is reported; one that does not reproduce turns the
 * verdict into {@link Verdict#ERROR}.
 *
 * @param witness    the payload (JSON), the input assignment, or the trace, as text
 * @param violations which expectations the witness breaks, one per line of the PR comment
 * @param exact      false when the witness relies on an opaque or over-approximated constraint
 */
public record Counterexample(Kind kind, String witness, List<String> violations, boolean exact) {

    public enum Kind {
        /** A JSON payload, from schema compatibility. */
        PAYLOAD,
        /** Argument and field values, from OpenJML or JBMC. */
        INPUTS,
        /** A state sequence, from Apalache or TLC. */
        TRACE
    }

    public Counterexample {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(witness, "witness");
        violations = List.copyOf(violations);
    }
}
