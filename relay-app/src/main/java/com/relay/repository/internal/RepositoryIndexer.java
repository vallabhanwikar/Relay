package com.relay.repository.internal;

import org.springframework.stereotype.Service;

/**
 * TODO(ticket 11): parse repo -> symbols + method table in Postgres.
 * TODO(ticket 12): HTTP call-site detectors for RestTemplate, WebClient, Feign.
 * TODO(ticket 13): endpoint <-> call-site edges; recursive-CTE reachability queries.
 *
 * <p>Known limitation to document rather than hide (Section 11): Lombok-heavy and
 * code-generated sources defeat plain JavaParser symbol solving. Bench fixtures include a
 * Lombok-heavy repo early precisely so this shows up as a measured number instead of a
 * surprise, with Eclipse JDT as the documented fallback.
 */
@Service
class RepositoryIndexer {
}
