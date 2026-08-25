package com.relay.evidence.api;

/**
 * A statement that must hold. The root of every evidence chain.
 *
 * <p>Examples from the plan: "all v1-valid requests remain valid under the patched client";
 * "response field mapping preserved". An invariant with no {@code Check} behind it is a claim,
 * not evidence, and the package renderer marks it as such.
 */
public record Invariant(
        String id,
        String statement,
        InvariantKind kind
) {
}
