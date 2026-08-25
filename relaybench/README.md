# RelayBench

The eval harness, built **before** the features it measures.

Structurally this is also an agent training environment: tasks plus verifiable scoring. It is
the single most reusable asset in the project.

## Case format

```
relaybench/cases/CASE-042-enum-repurpose/
├── case.yaml           # metadata: category, difficulty, language, description
├── repo/               # consumer fixture repo — must build and test cleanly at HEAD
├── spec-old.yaml       # upstream API v1
├── spec-new.yaml       # upstream API v2
├── traffic/            # recorded request/response pairs for replay
└── expected/
    ├── impact.json     # files and methods that SHOULD be flagged
    ├── repair.diff     # a known-good reference patch
    └── verdicts.json   # which invariants must pass and which must fail
```

`expected/verdicts.json` is the file that makes a false repair detectable: a patch can compile,
pass every test, and still be wrong. Without a statement of which invariants *must fail* for a
broken patch, a green pipeline proves nothing.

## Categories

Target 100 cases by Week 24, across:

field rename · field removal · type change · new required field · enum addition ·
enum removal · enum repurpose · endpoint moved · endpoint removed · auth change ·
pagination change · nested schema change · error schema change

Plus the two sets that keep the bench honest:

- **SEMANTIC-ONLY** — the spec did not change; behaviour did. Replay-detectable only. This set
  is the entire argument for the E5 rung.
- **NO-OP** — changes that look breaking and are not. Measures false-positive discipline.

## Scored metrics

| Metric | Target |
|---|---|
| Detection accuracy | ≥95% precision/recall (Phase 1 gate) |
| Impact precision / recall | ≥80% both (Phase 2 gate) |
| Compile rate | — |
| Verified-repair rate | 50–60% with the local model (Phase 4 gate) |
| **False-repair rate** | **<1%** |
| Wall-clock and token cost per case | tracked |
| Work mix (deterministic / local / frontier) | ~70 / 25 / 5 |

The false-repair rate has its own named counter because it is the worst outcome the system can
produce: a patch that passes proof but violates expected verdicts means the Evidence Package
lied, and an Evidence Package that can lie is worth nothing at all.

## Hold-out

20% of cases are never used during development. Scored only at Week 12 and Week 24. This is the
mitigation for benchmark overfitting — teaching to the test is easy and invisible without it.

## Growth schedule

| Phase | Cases |
|---|---|
| 0 | 10, hand-written |
| 1 | 40, across all 12 categories + no-op set |
| 2 | 60, multi-repo, seeded from real past projects |
| 6 | 100, full run + published scoreboard |

Phase 2 fixtures are seeded from real repositories rather than toys — a Jira-like ticketing
backend, a Twitter system-design project — because a benchmark made only of clean fixtures
measures a system that will never meet clean code.

## Status

Not yet implemented. Ticket 4: case schema + runner CLI + scoreboard JSON. Ticket 5: cases 1–10.
The CI `bench` job is already wired and reports the missing harness on every run.
