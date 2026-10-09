package com.relay.shared;

/**
 * The outcome of discharging one obligation with one verifier (design v3, "Core concepts").
 *
 * <p>Four values, never collapsed. A verdict means nothing without the evidence level that
 * produced it: {@code PROVEN} at {@link EvidenceLevel#E2_EXAMPLE} says "the consumer's tests
 * found nothing", {@code PROVEN} at {@link EvidenceLevel#E6B_FORMAL_PROOF} says "a solver showed
 * no input can break this". The level, not the word, carries the strength.
 *
 * <p>The failure mode this enum exists to prevent: a check that could not run - sandbox timeout,
 * solver gave up, unsupported construct - being reported as a pass. That is {@link #UNPROVEN},
 * and nothing downstream may treat it as {@link #PROVEN}.
 */
public enum Verdict {

    /** The verifier found no violation at its evidence level. */
    PROVEN,

    /**
     * The verifier produced a concrete witness of a violation - a payload, input values or a
     * trace - which is replayed as a failing test before it is reported.
     */
    COUNTEREXAMPLE,

    /** The verifier could not decide: timeout, bound reached, construct outside its scope. */
    UNPROVEN,

    /**
     * The check itself failed, or a counterexample did not reproduce on replay. A tool fault,
     * logged for investigation; never evidence in either direction.
     */
    ERROR;

    /** Only {@link #PROVEN} may count towards merging; everything else needs a human. */
    public boolean isPass() {
        return this == PROVEN;
    }
}
