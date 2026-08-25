/**
 * Impact - graph traversal and blast radius.
 *
 * <p>Joins typed change events against the dependency graph to answer the question a consumer
 * engineer actually has: which of my files, methods and services does this upstream change
 * break, and what do the affected requests look like (ticket 14-15)?
 *
 * <p>The Phase 2 build gate is >= 80% impact precision AND recall on supported change types
 * across the bench. Recall matters because a missed impact is a broken consumer; precision
 * matters because an impact report that cries wolf gets ignored, and an ignored report is
 * worth the same as no report.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Impact Analysis",
        allowedDependencies = {"detection::api", "repository::api"}
)
package com.relay.impact;
