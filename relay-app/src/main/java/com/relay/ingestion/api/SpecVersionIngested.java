package com.relay.ingestion.api;

import com.relay.shared.Ids;

import java.time.Instant;

/**
 * A new version of an upstream API spec has been stored. Head of the core loop.
 *
 * @param contentHash SHA-256 of the normalised spec document; two ingests of byte-identical
 *                    content produce the same hash, which is how re-polling the same URL
 *                    avoids generating phantom change sets
 */
public record SpecVersionIngested(
        Ids.ApiVersionId versionId,
        Ids.ExternalApiId apiId,
        String versionLabel,
        String contentHash,
        SpecSource source,
        Instant ingestedAt
) {
}
