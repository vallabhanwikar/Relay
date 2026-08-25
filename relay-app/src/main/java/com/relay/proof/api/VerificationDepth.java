package com.relay.proof.api;

/**
 * Adaptive verification depth (plan, Section 6). Which stages of the pipeline run.
 *
 * <p>Recorded in the Evidence Package so a reviewer can see which stages were skipped and why -
 * a skipped stage that nobody can see is indistinguishable from a stage that silently failed.
 */
public enum VerificationDepth {

    /** Compile + static analysis + the consumer's own tests. Non-breaking changes. */
    STANDARD,

    /** Adds mutation scoring over affected classes. Breaking changes. */
    DEEP,

    /** Adds WireMock replay and semantic diff. Breaking or semantic changes. */
    FULL
}
