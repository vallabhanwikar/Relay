package com.relay.impact.api;

import java.util.List;

/**
 * The summary a reviewer reads first: how far this change reaches.
 *
 * @param sampleAffectedRequests recorded requests that would traverse the affected sites -
 *                               concrete evidence that the blast radius is real, not inferred
 */
public record BlastRadius(
        int repositoryCount,
        int fileCount,
        int methodCount,
        int serviceCount,
        List<AffectedSite> sites,
        List<String> sampleAffectedRequests
) {
    public BlastRadius {
        sites = List.copyOf(sites);
        sampleAffectedRequests = List.copyOf(sampleAffectedRequests);
    }
}
