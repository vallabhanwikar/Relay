package com.relay.gateway.api;

/**
 * @param taskKind        used for routing and for grouping telemetry by task type
 * @param minimumTier     floor when a task is known to need a capable model
 * @param allowEscalation whether the gateway may move up a tier on low confidence
 */
public record ModelRequest(
        String taskKind,
        String prompt,
        ModelTier minimumTier,
        boolean allowEscalation
) {
}
