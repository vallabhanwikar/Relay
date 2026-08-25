/**
 * Repair - the constrained Spring AI agent.
 *
 * <p>Deliberately the LAST major component built (plan, Change 1). Coding agents commoditise
 * patches; nobody commoditises evidence. Building the proof engine first means half this
 * project survives any model disappointment, and it means the Week-12 milestone demo contains
 * zero AI.
 *
 * <p>The agent is constrained on three axes:
 * <ul>
 *   <li><b>Allowlisted tools</b> - read-file, propose-patch, run-proof. Nothing else.
 *   <li><b>Minimal context</b> - built from the dependency graph: the change, affected symbols
 *       and signatures, relevant tests. Never whole-repository dumps.
 *   <li><b>Bounded retry</b> - propose -> proof engine -> failure interpretation -> retry, max 3.
 * </ul>
 *
 * <p>Phase 4 definition of done: >= 50-60% verified-repair rate on suitable bench change types
 * with the LOCAL model. The false-repair rate - a patch that passes proof but violates expected
 * verdicts - targets &lt;1% and is the worst outcome in the system, tracked under its own counter.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Repair Agent",
        allowedDependencies = {"impact::api", "repository::api", "gateway::api", "proof::api"}
)
package com.relay.repair;
