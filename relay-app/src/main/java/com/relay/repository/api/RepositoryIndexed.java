package com.relay.repository.api;

import com.relay.shared.Ids;

import java.time.Instant;

/** A consumer repository has been parsed and its symbol graph stored. */
public record RepositoryIndexed(
        Ids.RepositoryId repositoryId,
        String commitSha,
        int symbolCount,
        int callSiteCount,
        int unresolvedCount,
        Instant indexedAt
) {
}
