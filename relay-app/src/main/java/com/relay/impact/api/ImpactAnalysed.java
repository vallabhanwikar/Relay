package com.relay.impact.api;

import com.relay.shared.Ids;

import java.time.Instant;

/** Impact analysis is complete for a change set. The trigger for repair and for proof. */
public record ImpactAnalysed(
        Ids.ImpactId impactId,
        Ids.ChangeSetId changeSetId,
        BlastRadius blastRadius,
        Instant analysedAt
) {
}
