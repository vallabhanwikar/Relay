package com.relay.detection.internal;

import org.springframework.stereotype.Service;

/**
 * TODO(ticket 7): integrate openapi-diff; map raw diff to the typed ChangeEvent taxonomy.
 * TODO(ticket 8): severity classifier (breaking / risky / safe) with an explicit rule table.
 * TODO(ticket 9): jqwik property suite; wire shrunk counterexamples into the bench.
 *
 * <p>The rule table is deliberately a table, not scattered conditionals - it is the artefact a
 * reviewer reads to decide whether they trust the classification, and blog post #1 is written
 * from it.
 */
@Service
class DetectionService {
}
