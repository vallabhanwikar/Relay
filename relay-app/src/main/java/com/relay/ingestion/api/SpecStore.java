package com.relay.ingestion.api;

import com.relay.shared.Ids;

import java.util.Optional;

/**
 * Read side of the spec store, exposed so that downstream modules can fetch the two documents
 * a diff is computed from. Write access stays internal - specs enter only through ingestion's
 * own endpoints and watchers.
 */
public interface SpecStore {

    /** Raw normalised spec document for a stored version. */
    Optional<String> findDocument(Ids.ApiVersionId versionId);

    /** The version immediately preceding {@code versionId} for the same API, if any. */
    Optional<Ids.ApiVersionId> findPrevious(Ids.ApiVersionId versionId);
}
