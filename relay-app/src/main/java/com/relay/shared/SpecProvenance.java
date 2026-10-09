package com.relay.shared;

/**
 * Who wrote the formal body of an obligation (design v3, "Spec provenance").
 *
 * <p>This is the answer to the circularity of an AI writing both the code and its spec. A model
 * may propose a contract; only a human may make it load-bearing; only a tool may prove it.
 */
public enum SpecProvenance {

    /** Derived by deterministic code from an artifact: an OpenAPI diff, a protocol template. */
    MECHANICAL,

    /** Written by a developer, e.g. JML already in the consumer's source. */
    HUMAN,

    /** Proposed by a model. Never raises evidence above E0 until a human approves it. */
    LLM_PROPOSED,

    /** Proposed by a model and approved by a named human; recorded with the approver. */
    HUMAN_APPROVED;

    /** Whether a verdict on an obligation with this provenance may count as evidence. */
    public boolean mayCarryProof() {
        return this != LLM_PROPOSED;
    }
}
