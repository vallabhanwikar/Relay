/**
 * Ingestion - spec store and watchers.
 *
 * <p>Owns the answer to "what did this API look like at time T". Accepts OpenAPI documents by
 * upload, URL poll, or git watch (ticket 6), normalises them, and stores every version
 * immutably. Publishes {@code SpecVersionIngested} and knows nothing about what anyone
 * downstream does with it.
 *
 * <p>Immutability is not incidental. An Evidence Package asserts things about a specific pair
 * of spec versions; if a stored version could be edited after the fact, every package
 * referencing it would silently become a lie.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Ingestion",
        allowedDependencies = {}
)
package com.relay.ingestion;
