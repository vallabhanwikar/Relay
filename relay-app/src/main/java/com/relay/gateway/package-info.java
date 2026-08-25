/**
 * Model Gateway - tier routing, telemetry, escalation.
 *
 * <p>One interface in front of every model, deterministic or otherwise. Routes each task to the
 * cheapest tier that can do it - deterministic code path, small local model, local coding model,
 * optional frontier - and records confidence, escalation and per-task token cost (ticket F14).
 *
 * <p>The telemetry is not incidental instrumentation; it is the evidence behind the unit
 * economics claim the plan wants to be able to make: roughly 70% deterministic, 25% local, 5%
 * frontier. A work-mix ratio you measured is an interview answer. One you assert is a guess.
 *
 * <p>MCP-ready (F20): an MCP tool schema is an {@code ExternalApi} subtype, so tool-change
 * reliability reuses this module rather than needing a new one.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Model Gateway",
        allowedDependencies = {}
)
package com.relay.gateway;
