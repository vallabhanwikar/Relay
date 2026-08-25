package com.relay.evidence.internal;

import org.springframework.stereotype.Service;

/**
 * TODO(ticket 19): Evidence Graph schema + package renderer (JSON + HTML) + content hashing.
 *
 * <p>Phase 3 definition of done: an Evidence Package renders for every bench case. Rendering
 * is server-side JSON -> HTML; the Next.js dashboard displays it but does not construct it, so
 * the package is identical whether a human reads it in a browser or a script verifies its hash.
 *
 * <p>Canonicalisation before hashing is the subtle part: map ordering, timestamp precision and
 * log references all have to be normalised, or two identical runs produce different hashes and
 * the hash stops meaning anything.
 */
@Service
class EvidenceGraphService {
}
