package com.relay.shared;

/** The kinds of claim Relay knows how to discharge, each with its own verifiers. */
public enum ObligationKind {

    /** Payloads one side may produce are accepted by the other (Z3 schema verifier). */
    SCHEMA_COMPATIBILITY,

    /** A method satisfies its JML contract (OpenJML). */
    JML_CONTRACT,

    /** A changed method throws no unchecked exception within a loop bound (JBMC). */
    NO_RUNTIME_EXCEPTION,

    /** An integration protocol keeps an invariant, e.g. at most one charge per key (Apalache, TLC). */
    PROTOCOL_INVARIANT
}
