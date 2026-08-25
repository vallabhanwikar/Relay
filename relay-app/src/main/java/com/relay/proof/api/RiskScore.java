package com.relay.proof.api;

import com.relay.shared.EvidenceLevel;

/**
 * Composite risk for a verified patch.
 *
 * @param value            0.0 (safe) to 1.0 (dangerous)
 * @param highestEvidence  the strongest level that produced a load-bearing PASS
 * @param weakTestSuite    true when PIT killed &lt;60% of mutants over the affected classes, in
 *                         which case the E2 verdicts backing this score are marked WEAK
 * @param skippedStages    stages the depth policy chose not to run, named so the reviewer sees them
 */
public record RiskScore(
        double value,
        EvidenceLevel highestEvidence,
        boolean weakTestSuite,
        java.util.List<String> skippedStages,
        String rationale
) {
    public RiskScore {
        skippedStages = java.util.List.copyOf(skippedStages);
    }
}
