/**
 * Shared kernel - the small set of types every module is allowed to speak.
 *
 * <p>This module is OPEN by design: its internals are visible to all other modules and it is
 * declared as a shared module on {@code @Modulithic}, so it never needs to appear in anyone's
 * {@code allowedDependencies}. That privilege is exactly why it must stay small.
 *
 * <p><b>The rule for this package:</b> a type belongs here only if it is a value type in the
 * ubiquitous language that at least three modules must exchange - identifiers, the evidence
 * ladder, patch provenance. Behaviour does not belong here. Entities do not belong here.
 * When this package starts growing, the boundary being violated is somewhere else.
 *
 * <p>{@code CandidatePatch} lives here rather than in {@code repair} on purpose. Section 4 of
 * the plan is explicit that a patch may come from a human, from Relay's own agent, or from any
 * external coding agent - agents are Relay's suppliers, not its rivals. If the proof engine
 * had to import {@code repair} to understand a patch, that neutrality would be a lie told in
 * documentation and contradicted by the dependency graph.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        displayName = "Shared Kernel"
)
package com.relay.shared;
