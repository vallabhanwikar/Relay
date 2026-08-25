# Relay

**Dependabot for the APIs you don't control, with a proof engine that shows its work.**

When a third-party API changes, Relay detects the change, maps its blast radius across your
code, verifies (or generates) the fix, and emits an auditable **Evidence Package**.

The Evidence Package — not the patch — is the product.

> **Status: Phase 0.** This repository is a skeleton. The module boundaries, the build, and the
> tests that enforce those boundaries are real; the modules themselves are stubs carrying the
> ticket numbers that will fill them. Nothing below describes working software yet.

---

## Why this exists

Three classes of integration failure, and who catches them today:

| Class | Example | Who catches it |
|---|---|---|
| **Hard failure** | HTTP 500, timeout, connection refused | Monitoring. Not Relay's job. |
| **Contract failure** | Field removed, type changed, new required field | Contract tests — *if* the contract is known and current |
| **Semantic failure** | HTTP 200, but `customer_id` now means `account_id` | Almost nobody |

The third class is the interesting one. A spec diff cannot see it; only behavioural comparison —
replay plus semantic diff — can. That is where Relay lives.

Relay works specifically where the provider does **not** cooperate: third-party SaaS APIs where
you cannot ask the vendor to run your contract tests, and where no lockfile exists because it
isn't your code.

## The core loop

```
API change → Change Detector → Semantic Analysis → Dependency Graph → Impact Analysis
    → [patch from ANY source: human, Relay agent, external agent]
    → PROOF ENGINE → Evidence Package → Risk Score → GitHub PR
```

Coding agents are Relay's **suppliers, not its rivals**. Anyone can wire an LLM to write a
patch. The machinery that decides whether a patch is trustworthy is the part that's hard, and
it's the part that works with zero AI involved.

## The evidence ladder

Every verdict Relay records carries exactly one level. Model confidence is never evidence.

| Level | Meaning | Source in Relay |
|---|---|---|
| **E0** | AI opinion only | Model self-assessment — recorded, never load-bearing |
| **E1** | Static | Compile, static analysis, ArchUnit |
| **E2** | Example | The consumer repo's own unit and integration tests |
| **E3** | Generative | jqwik properties, fuzzed inputs |
| **E4** | Adversarial | PIT mutation score over affected classes |
| **E5** | Behavioural | WireMock traffic replay + semantic diff |
| **E6** | Formal | TLA+/TLC over the verification workflow |

Two consequences the proof engine implements:

- **Test-suite strength weighting** — before "the tests pass" counts as E2 evidence for a patch,
  PIT runs against the affected classes. A suite that kills <60% of mutants has its E2 verdicts
  marked `WEAK` in the package.
- **Adaptive verification depth** — the orchestrator runs the cheapest sufficient pipeline for
  the risk. The depth policy is itself recorded, so a reviewer sees *why* stages were skipped
  rather than wondering whether they failed silently.

## Architecture

A **modular monolith** — one Spring Boot application with enforced module boundaries. Not
microservices. A solo builder's scarcest resource is integration overhead; a modulith buys
module discipline without the ops tax.

```
com.relay
├── shared        Shared kernel — Ids, EvidenceLevel, Verdict, CandidatePatch  [OPEN module]
├── ingestion     Spec store, git/URL watchers
├── detection     Structural diff, change taxonomy, severity classification
├── repository    JavaParser indexing, symbol graph, HTTP call-site discovery
├── impact        Graph traversal, blast radius
├── proof         Sandbox orchestration, checks, risk score          ← the heart
├── evidence      Evidence Graph, package rendering, content hashing ← the product
├── repair        Spring AI agent, allowlisted tools                 ← built last, on purpose
├── gateway       Model tier routing, telemetry
└── delivery      GitHub App, PR + evidence publishing
```

Module dependencies are declared, not discovered:

```
ingestion ← detection ← impact → proof → evidence → delivery
                ↑                  ↑
           repository          repair → gateway
```

**The proof engine does not depend on the repair agent.** That is the product position expressed
as a dependency rule, and `ArchitectureRulesTest` fails the build if it is ever violated. Patches
arrive as `CandidatePatch` whether a human, Relay's agent, or Claude Code wrote them.

Boundaries are enforced on every commit by `ModularityTests` (Spring Modulith) and
`ArchitectureRulesTest` (ArchUnit). Adding a cross-module dependency requires editing the target
module's `package-info.java` and saying why.

## Technology

| Layer | Choice | Why |
|---|---|---|
| Runtime | Java 21 (records, virtual threads) | Virtual threads for IO-heavy orchestration |
| Framework | Spring Boot 3.5 + Spring Modulith | Enforced boundaries + generated module docs |
| Data | PostgreSQL 16 + pgvector | System of record *and* similarity search. One database. |
| Workflow | Temporal | Durable, replayable verification runs |
| Code intelligence | JavaParser | Deterministic AST/symbols — never ask an LLM what the code structure is |
| Spec diffing | openapi-diff + custom taxonomy | Proven differ; own the semantic layer |
| Testing | JUnit 5, jqwik, Testcontainers, PIT, ArchUnit | The proof engine must itself be verified software |
| Replay | WireMock + custom normaliser | Record/replay third-party traffic without touching real providers |
| Formal | TLA+ / TLC | Model-check the verification workflow's concurrency |
| AI | Spring AI (Ollama local, vLLM prod) | Model portability; the engine is indifferent to who wrote the patch |

**Deliberate NOs:** no API gateway, no generic monitoring, no auto-deploy, no fine-tuning, no
Kafka, no Neo4j, no multi-tenant SaaS hardening. Each is a decision, not an omission.

## Getting started

```bash
# Start Postgres + pgvector, Redis, Temporal, Ollama, Jaeger
make dev-up

# Pull the local coding model (once)
docker exec relay-ollama ollama pull qwen2.5-coder:7b

# Build and run the full test suite
make build

# The fast loop — just the module boundary tests, seconds not minutes
make arch
```

| Service | URL |
|---|---|
| Temporal UI | http://localhost:8233 |
| Jaeger traces | http://localhost:16686 |
| Actuator | http://localhost:8080/actuator/modulith |

`make docs` regenerates the module canvas and PlantUML component diagrams into
`relay-app/target/spring-modulith-docs/` — generated from the code, so they cannot drift from it.

## RelayBench

The benchmark harness was built **before** the features it measures. That ordering is the point:
it means every claim in this repository has a number behind it, and it means a red scoreboard in
week 3 is the plan working rather than the plan failing.

Cases live in `relaybench/cases/`, one directory each, holding a consumer fixture repo, an old
and new spec, recorded traffic, and the expected impact, repair and verdicts. Scored metrics:
detection accuracy, impact precision/recall, compile rate, verified-repair rate, **false-repair
rate** (target <1% — a repair that passes proof but violates expected verdicts is the worst
outcome in the system and has its own named counter), wall-clock and token cost per case, and
the deterministic/local/frontier work mix.

A 20% hold-out set is never used during development and is scored only at the Week 12 and
Week 24 gates.

## Roadmap

| Phase | Weeks | Deliverable |
|---|---|---|
| **0** | 1–2 | Foundations, CI, docker-compose, modulith skeleton, RelayBench v0 ← *you are here* |
| **1** | 3–6 | Deterministic change detection + property suite over the detector |
| **2** | 7–10 | Repository intelligence and impact analysis |
| **3** | 11–14 | **The proof engine** — sandbox, pipeline, Evidence Graph, TLA+ spec |
| **4** | 15–18 | The repair agent, finally |
| **5** | 19–22 | Delivery surface — GitHub App, dashboard, tracing |
| **6** | 23–24 | Showcase — full bench run, demo, architecture doc |

The **Week 12 milestone demo contains zero AI**. A human writes a patch for a detected breaking
change, Relay verifies it, emits an Evidence Package, and a reviewer reads exactly what was
proven. That inversion is deliberate: it demonstrates that the system's credibility rests on
deterministic machinery, and it means half this project survives any model disappointment.

## Where Relay sits

| Neighbour | They own | Relay's boundary |
|---|---|---|
| Antithesis | Whole-system deterministic simulation, fault injection | Relay never simulates whole systems. One upstream change, one consumer codebase, one evidence package. |
| Dependabot / Renovate | Version bumps via lockfiles and registries | No lockfile exists for someone else's REST API. Adjacent, not overlapping. |
| Coding agents | Generating the patch | Relay owns detection, blast radius, verification, evidence. Agents are suppliers. |
| Pact | Consumer-driven contracts between *cooperating* teams | Relay works when the provider does not cooperate. |
| CodeRabbit / Qodo | LLM judgment on PR diffs | They produce E0–E1 opinions about diffs. Relay produces E2–E5 machine evidence about behaviour. |

## Licence

Apache-2.0. See [LICENSE](LICENSE).

This is a personal portfolio project, built on personal time and personal hardware, with no
relationship to any employer's systems or data.
