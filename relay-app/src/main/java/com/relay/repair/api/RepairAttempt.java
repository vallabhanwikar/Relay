package com.relay.repair.api;

import com.relay.shared.Ids;

import java.time.Duration;

/**
 * Telemetry for one repair attempt, whether or not it succeeded. Feeds the RelayBench
 * scoreboard's repair-success, latency and token metrics.
 */
public record RepairAttempt(
        Ids.ImpactId impactId,
        int attemptNumber,
        boolean produced,
        boolean verified,
        int tokensUsed,
        Duration elapsed,
        String failureInterpretation
) {
}
