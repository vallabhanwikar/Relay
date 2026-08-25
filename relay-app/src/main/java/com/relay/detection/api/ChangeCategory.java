package com.relay.detection.api;

/**
 * The twelve change categories the detector classifies (plan, Section 8, Phase 1), plus the
 * two sets that exist to keep the benchmark honest.
 *
 * <p>{@link #SEMANTIC_ONLY} is the strategic category: the spec did not change at all, but
 * behaviour did - HTTP 200 with {@code customer_id} now meaning {@code account_id}. Spec diffs
 * cannot see it; only replay plus semantic diff can. Almost nobody catches this today.
 */
public enum ChangeCategory {

    FIELD_RENAME,
    FIELD_REMOVAL,
    TYPE_CHANGE,
    NEW_REQUIRED_FIELD,
    ENUM_ADDITION,
    ENUM_REMOVAL,
    ENUM_REPURPOSE,
    ENDPOINT_MOVED,
    ENDPOINT_REMOVED,
    AUTH_CHANGE,
    PAGINATION_CHANGE,
    NESTED_SCHEMA_CHANGE,
    ERROR_SCHEMA_CHANGE,

    /** Behaviour changed while the spec did not. Replay-detectable only. */
    SEMANTIC_ONLY,

    /** Looks breaking, is not. The false-positive discipline set. */
    NO_OP
}
