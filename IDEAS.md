# Parking lot

Scope creep is listed in the plan as a HIGH severity risk and named "the v1 disease". The
mitigation is mechanical: a new idea goes in this file, not into the current phase. Nothing here
is scheduled. Nothing here is built before Gate B says GO.

Each entry gets one line on what it is and one on what would have to be true to start it.

## Locked behind Gate B (Week 24+)

**F18 — Auto-merge policy engine (trust ladder Level 5)**
Auto-merge low-risk verified patches. Unlocks only on a measured false-repair rate; autonomy is
earned, not shipped.

**F19 — Python / Node / Go consumer support; GraphQL, SOAP; GitLab, Bitbucket**
Breadth. Unlocks when the Java + OpenAPI + GitHub lane is demonstrably finished, not before —
breadth before depth is how the detector never reaches 95%.

**F20 — MCP tool-change reliability**
Treat an agent tool schema as an API that can break. The domain model already reserves room: an
MCP tool *is* an `ExternalApi` subtype. Unlocks if MCP tool churn turns out to hurt people as
much as API churn does.

**F21 — PR-triggered verification mode**
The same proof engine and Evidence Package, fired by inbound PRs (including AI-generated ones)
instead of upstream API changes. Higher event frequency, but contested by CodeRabbit, Qodo and
GitHub. Deliberately the *second* door: the shared engine makes it cheap to add later, and
building it first would mean competing before there is anything to compete with. Unlocks after
Gate A.

**F22 — Adversarial verifier agent**
A second constrained agent that tries to break the candidate patch — edge inputs, concurrency
probes — while the proof engine verifies it. Findings enter the Evidence Graph at E4. Unlocks
when the E4 mutation rung is boring and the repair rate is stable.

## Not doing (and why)

Recorded so the decision does not get relitigated every time someone suggests it.

- **API gateway** — Relay observes integrations; it does not sit in the request path.
- **Generic monitoring** — hard failures are already someone else's solved problem.
- **Auto-deploy / auto-rollback** — trust ladder Levels 6–7. Vision slides only.
- **Fine-tuning** — the differentiator is the proof engine, not the model.
- **Kafka** — the Spring Modulith event registry gives durable in-process events; a broker would
  be ops sprawl bought with no additional guarantee at this scale.
- **Neo4j** — bounded-depth reachability over a graph that fits in Postgres. Recursive CTEs.
- **Multi-tenant SaaS hardening** — a Gate B concern, and only on GO.
- **Whole-system simulation, fault injection, hypervisors** — that is Antithesis's lane. Relay
  stays out of it deliberately; complementary is a better position than second-best.

## Unfiled

Things noticed while building. No commitment implied.

_(empty — add as they come up)_
