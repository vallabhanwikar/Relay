package com.relay.repository.api;

import com.relay.shared.Ids;

import java.util.List;

/**
 * Read side of the endpoint-to-code dependency graph. This is the port impact analysis
 * traverses; the recursive-CTE implementation stays internal so the storage decision can be
 * revisited without touching consumers.
 */
public interface DependencyGraph {

    /** Every call site targeting the given external endpoint, across all indexed repositories. */
    List<CallSite> callSitesFor(String endpoint);

    /**
     * Methods transitively reachable from the given call site, up to {@code maxDepth}.
     * Bounded on purpose - unbounded reachability in a large repository returns "everything",
     * which is a blast radius report nobody can act on.
     */
    List<String> reachableFrom(CallSite callSite, int maxDepth);

    boolean isIndexed(Ids.RepositoryId repositoryId);
}
