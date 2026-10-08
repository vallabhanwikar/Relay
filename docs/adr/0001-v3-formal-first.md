# ADR 0001: v3, formal evidence first, with two entry paths

- **Status:** accepted
- **Date:** 2026-10-08
- **Design:** "Relay v3 — Unified Design: Integration Evidence + Proof Gate" (Claude doc)

## Context

v2 put formal methods (TLA+/TLC) at the top of the evidence ladder and at the end of the
roadmap. A second idea, a proof gate for AI-written pull requests ("VeriCode"), overlapped
with Relay's proof engine almost entirely. It differed in the trigger (any PR rather than an
upstream API change) and in where the spec comes from (inferred rather than diffed).

## Decision

1. **One engine, two entry paths.** The API path starts from an upstream spec change. The Gate
   path starts from any pull request. Both reduce the change to **obligations**, which the
   proof module discharges without knowing where they came from.
2. **Formal evidence comes first.** Schema compatibility is decided with Z3 (`relay-formal`),
   starting in Phase 1. It needs no AI and no repository analysis to produce a proof or a
   concrete counterexample.
3. **Verdicts are four-valued:** PROVEN, COUNTEREXAMPLE, UNPROVEN, ERROR. E6 splits into
   E6A (bounded) and E6B (unbounded proof).
4. **Spec provenance is recorded on every obligation.** An LLM-proposed spec carries no proof
   until a named human approves it.
5. **Verifiers live in `relay-formal` as plain libraries.** The proof module adapts them.
   OpenJML (GPLv2) runs only as a separate process in `sandbox-runner/openjml`.

## Consequences

- Phase 0's code is kept. v3 adds to it: `relay-formal`, new shared types, a split E6.
- The Week-12 zero-AI demo now carries formal evidence (E6b) rather than tests only.
- Claims must stay within the claim boundary: "obligation O-12 is PROVEN by Z3 for this
  consumer's read set", never "this integration is formally verified".
- Scope risk rises. The Gate path does not start until Gate A is passed.
