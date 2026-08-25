/**
 * Proof Engine - sandbox orchestration, checks, risk score. The heart of Relay.
 *
 * <p>Takes a candidate patch from any source and runs the verification pipeline: compile ->
 * static analysis -> unit tests -> Testcontainers integration tests -> contract checks ->
 * WireMock traffic replay with semantic diff -> risk score (ticket 16-18).
 *
 * <p>Two upgrades from the evidence ladder are properties of this module, not of evidence:
 * <ul>
 *   <li><b>Test-suite strength weighting</b> - before counting "tests pass" as E2 evidence, PIT
 *       runs against the affected classes. A suite that kills &lt;60% of mutants is flagged and
 *       its E2 verdicts are marked WEAK.
 *   <li><b>Adaptive verification depth</b> - the orchestrator picks the cheapest sufficient
 *       pipeline for the risk: non-breaking change gets E1-E2 only; breaking or semantic change
 *       gets the full pipeline through E4 mutation scoring and E5 replay. The depth policy is
 *       itself recorded, so a reviewer can see WHY stages were skipped rather than wondering.
 * </ul>
 *
 * <p>This module does not depend on {@code repair}, and that is the product position expressed
 * as a dependency rule: patches arrive as {@link com.relay.shared.CandidatePatch} regardless of
 * whether a human, Relay's agent, or Claude Code wrote them.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Proof Engine",
        allowedDependencies = {"impact::api"}
)
package com.relay.proof;
