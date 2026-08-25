/**
 * Repository Intelligence - JavaParser indexing and the symbol graph.
 *
 * <p>Owns "what does the consumer's code actually do with this API". Parses consumer
 * repositories into symbols and call-graph slices (ticket 11), discovers HTTP call sites across
 * RestTemplate, WebClient, Feign and plain HttpClient (ticket 12), and stores the
 * endpoint-to-code dependency graph in Postgres as recursive CTEs (ticket 13).
 *
 * <p>No graph database. That is a decision, not an omission: the traversals Relay needs are
 * bounded-depth reachability over a graph that fits comfortably in Postgres, and Section 5
 * lists Neo4j among the deliberate NOs. Blog post #2 is the defence of this trade-off.
 *
 * <p>Note on naming: the package is {@code repository} to match the plan's module list. Types
 * inside it avoid the {@code *Repository} suffix - the indexed-repository aggregate is
 * {@code IndexedRepository} and its store is {@code IndexedRepositoryStore} - so that Spring
 * Data repositories elsewhere stay unambiguous.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Repository Intelligence",
        allowedDependencies = {}
)
package com.relay.repository;
