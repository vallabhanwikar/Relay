package com.relay.gateway.api;

/**
 * Routing tiers, cheapest first. {@link #DETERMINISTIC} is a tier on purpose: the gateway's
 * first question for any task is whether a model is needed at all.
 */
public enum ModelTier {
    DETERMINISTIC,
    SMALL_LOCAL,
    CODING_LOCAL,
    FRONTIER
}
