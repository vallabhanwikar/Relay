package com.relay.proof.api;

import com.relay.shared.Ids;
import com.relay.shared.Verdict;

import java.time.Instant;

/**
 * A verification run has finished. Consumed by evidence to assemble the package.
 *
 * <p>{@code verdict} may be {@link Verdict#INCONCLUSIVE} - a sandbox timeout or an unbuildable
 * consumer repo is a real outcome that must reach the reviewer, not an error to swallow.
 */
public record ProofCompleted(
        Ids.VerificationRunId runId,
        Ids.PatchId patchId,
        Ids.ImpactId impactId,
        Verdict verdict,
        RiskScore riskScore,
        VerificationDepth depthUsed,
        Instant completedAt
) {
}
