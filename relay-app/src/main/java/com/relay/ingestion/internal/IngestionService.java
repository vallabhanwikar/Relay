package com.relay.ingestion.internal;

import org.springframework.stereotype.Service;

/**
 * TODO(ticket 6): OpenAPI ingestion endpoint + version store - spec upload, URL poll, git watch.
 *
 * <p>Definition of done for the surrounding phase: the detector reaches >= 95% precision/recall
 * on the 40-case bench, which is only meaningful if ingestion normalises deterministically
 * first. Normalisation order (key sorting, $ref resolution, whitespace) is part of the
 * content hash contract - changing it later invalidates stored hashes.
 */
@Service
class IngestionService {
}
