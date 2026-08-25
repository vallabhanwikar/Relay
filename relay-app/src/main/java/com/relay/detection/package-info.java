/**
 * Detection - structural diff and change taxonomy.
 *
 * <p>Turns a pair of spec versions into typed {@code ChangeEvent}s across the twelve categories
 * (ticket 7-8): field rename, field removal, type change, new required field, enum
 * addition/removal/repurpose, endpoint moved/removed, auth change, pagination change, nested
 * schema change, error schema change. Starts from openapi-diff and owns the semantic
 * classification layer on top of it.
 *
 * <p>This module is verified software, not just software: ticket 9 puts a jqwik property suite
 * over it ({@code diff(spec, spec)} is always empty; removing a required field is never
 * classified non-breaking; round-trip and monotonicity), and shrunk counterexamples are
 * committed back into the bench as regression cases.
 *
 * <p>Zero AI lives here, permanently. That is what makes the Week-12 milestone demo credible.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Detection",
        allowedDependencies = {"ingestion::api"}
)
package com.relay.detection;
