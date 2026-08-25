package com.relay.evidence.api;

import com.relay.shared.Ids;

import java.time.Instant;
import java.util.List;

/**
 * The aggregate root and Relay's product: a frozen, content-hashed export of the evidence
 * subgraph for one change.
 *
 * @param contentHash hash over the canonicalised package. Freezing is the point - a package
 *                    whose contents could drift after publication proves nothing, and the hash
 *                    is what a reviewer or auditor checks it against months later.
 */
public record EvidencePackage(
        Ids.EvidencePackageId id,
        Ids.VerificationRunId runId,
        Ids.ChangeSetId changeSetId,
        List<Invariant> invariants,
        List<Check> checks,
        List<CheckRun> runs,
        String contentHash,
        Instant frozenAt
) {
    public EvidencePackage {
        invariants = List.copyOf(invariants);
        checks = List.copyOf(checks);
        runs = List.copyOf(runs);
    }
}
