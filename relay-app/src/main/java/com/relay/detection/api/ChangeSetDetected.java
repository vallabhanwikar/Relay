package com.relay.detection.api;

import com.relay.shared.Ids;

import java.time.Instant;
import java.util.List;

/**
 * The typed result of diffing two spec versions. Consumed by impact analysis.
 *
 * <p>An empty {@code changes} list is a real and expected outcome - it is what a re-poll of an
 * unchanged spec produces - and must not be treated as a detector failure.
 */
public record ChangeSetDetected(
        Ids.ChangeSetId changeSetId,
        Ids.ExternalApiId apiId,
        Ids.ApiVersionId fromVersion,
        Ids.ApiVersionId toVersion,
        List<ChangeEvent> changes,
        Instant detectedAt
) {
    public ChangeSetDetected {
        changes = List.copyOf(changes);
    }
}
