package com.relay.evidence.api;

import com.relay.shared.Ids;

import java.time.Instant;

/** An Evidence Package has been frozen and is ready to attach to a pull request. */
public record EvidencePackagePublished(
        Ids.EvidencePackageId packageId,
        Ids.VerificationRunId runId,
        String contentHash,
        Instant publishedAt
) {
}
