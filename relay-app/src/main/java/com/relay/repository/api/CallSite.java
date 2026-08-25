package com.relay.repository.api;

import com.relay.shared.Ids;

/**
 * One place in consumer code that calls an external endpoint.
 *
 * @param endpoint  the external endpoint in {@code METHOD /path} form
 * @param client    which HTTP client abstraction was used - affects how a repair must be shaped
 * @param confidence how sure the indexer is that this call site targets this endpoint. Path
 *                   templates assembled at runtime cannot always be resolved statically;
 *                   recording the uncertainty is better than pretending it away, and low
 *                   confidence propagates into the blast radius report rather than being hidden.
 */
public record CallSite(
        Ids.RepositoryId repositoryId,
        String filePath,
        int lineNumber,
        String enclosingMethod,
        String endpoint,
        HttpClientKind client,
        double confidence
) {
}
