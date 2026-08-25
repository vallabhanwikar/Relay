package com.relay.gateway.api;

import java.time.Duration;

/**
 * @param confidence  self-reported and therefore E0 evidence at best - useful for routing
 *                    decisions, never admissible as proof of anything
 * @param escalated   whether the gateway moved above the tier it first tried
 */
public record ModelResponse(
        String content,
        ModelTier tierUsed,
        boolean escalated,
        double confidence,
        int promptTokens,
        int completionTokens,
        Duration latency
) {
}
