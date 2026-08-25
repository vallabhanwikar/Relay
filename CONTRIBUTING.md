# Working on Relay

A solo project with conventions anyway, because the commit history is part of the portfolio
artifact. A reviewer reading `git log` should be able to follow the reasoning.

## Commit conventions

[Conventional Commits](https://www.conventionalcommits.org/), scoped by application module.

```
<type>(<scope>): <subject>

<body — why, not what>

Refs: #<ticket>
```

**Types:** `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci`, `chore`, `spec` (TLA+),
`bench` (RelayBench cases or scoring).

**Scopes** are the ten modules plus the cross-cutting ones:

```
shared  ingestion  detection  repository  impact  proof  evidence  repair  gateway  delivery
bench   ui         sandbox    ops
```

Examples:

```
feat(detection): classify enum repurpose as RISKY, not SAFE

An enum value whose meaning changed while its name did not is invisible to a
structural diff. Classifying it RISKY forces the full pipeline, so replay gets a
chance to catch it. Found by the jqwik monotonicity property.

Refs: #8

test(proof): seed a semantic failure that all example tests pass

The E5 rung only earns its place if there is a case the lower rungs provably
miss. This is that case.

Refs: #18
```

The body matters more than the subject. Anyone can read the diff; the reason a trade-off went
one way is the thing that is gone in six months.

## Branching

`main` is always green. Work on `phase-<n>/<ticket>-<slug>`, e.g. `phase-1/07-openapi-diff`.
Squash-merge, keeping the reasoning in the merge commit body.

## The rules that block a merge

CI enforces these; they are not style preferences.

1. **Module boundaries hold.** `ModularityTests` and `ArchitectureRulesTest` pass. If you need a
   new cross-module dependency, edit the target module's `package-info.java` and explain the
   dependency in the Javadoc. Making the test pass by widening `allowedDependencies` without
   writing down why is how a modulith becomes a big ball of mud with extra ceremony.

2. **The proof engine stays neutral about patch provenance.** `com.relay.proof` must never
   depend on `com.relay.repair`. This has its own test.

3. **Only `gateway` calls a model.** Every model call is routed and metered there, or the
   measured work-mix ratio stops being measured.

4. **New scope goes in `IDEAS.md`, not in the sprint.** Section 11 of the plan names scope creep
   as the v1 disease. F18–F22 are locked behind Gate B.

5. **A new bench case ships with the feature that needed it.** Especially a shrunk jqwik
   counterexample — those become regression cases.

## Adding an application module

Don't, unless the plan changed. Ten modules is the design. If it genuinely must happen:

1. Create `com.relay.<name>` with a `package-info.java` carrying `@ApplicationModule`, an
   explicit `allowedDependencies`, and Javadoc saying what the module owns and what it does not.
2. Put exposed types in `<name>.api` with `@NamedInterface("api")`; everything else in
   `<name>.internal`.
3. Add the module to the expected list in `ModularityTests` and to the layer rules in
   `ArchitectureRulesTest`. The test failing until you do this is intentional.
4. Update the diagram in `README.md`.

## Dependencies

Each arrives with the phase that needs it. `relay-app/pom.xml` carries a commented list of what
is coming and when. A dependency added ahead of the phase that justifies it slows every build
between now and then for nothing.

## Ticket 0

Before any code: employment agreement, moonlighting clause, IP assignment, outside-activity
policy. Personal hardware, personal accounts, personal time, zero overlap with employer systems
or data. If the agreement is ambiguous, the safe posture is a public non-commercial portfolio
project — no revenue, no incorporation — until it is clarified.

This is a 30-minute read that protects 24 weeks of work.
