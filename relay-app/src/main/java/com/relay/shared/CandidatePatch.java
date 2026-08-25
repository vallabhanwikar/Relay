package com.relay.shared;

import java.time.Instant;

/**
 * A patch awaiting proof, from any source.
 *
 * @param id       identity of this candidate
 * @param impactId the impact analysis this patch responds to
 * @param diff     unified diff against the consumer repository at {@code baseCommit}
 * @param baseCommit the commit the diff applies to - without it the proof is not reproducible
 * @param source   provenance, recorded in the Evidence Package but never used to skip checks
 */
public record CandidatePatch(
        Ids.PatchId id,
        Ids.ImpactId impactId,
        String diff,
        String baseCommit,
        PatchSource source,
        Instant proposedAt
) {
}
