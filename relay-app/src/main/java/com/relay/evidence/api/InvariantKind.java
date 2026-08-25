package com.relay.evidence.api;

/** What sort of thing an invariant asserts. Drives which checks can discharge it. */
public enum InvariantKind {
    COMPILATION,
    BEHAVIOURAL_EQUIVALENCE,
    CONTRACT_CONFORMANCE,
    TYPE_SAFETY,
    CONCURRENCY
}
