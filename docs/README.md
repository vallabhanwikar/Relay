# Documentation

| Path | Contents |
|---|---|
| `docs/adr/` | Architecture decision records — one per non-obvious trade-off |
| `docs/spec/` | TLA+ specifications and TLC configurations |
| `research/` | Discovery interview notes, verbatim (Weeks 12–24, Gate B input) |

Generated documentation is not committed. `make docs` writes the module canvas and PlantUML
component diagrams to `relay-app/target/spring-modulith-docs/`, generated from the code so they
cannot drift from it. CI uploads them as a build artifact on every run.

## ADRs worth writing early

The decisions a Staff-level reviewer will ask about, and which are much easier to write down now
than to reconstruct in week 20:

1. Modular monolith over microservices
2. Postgres recursive CTEs over a graph database
3. Temporal over hand-rolled queues for verification durability
4. Proof engine built before the repair agent
5. Sandbox isolation level — Docker now, gVisor/microVM documented as a future step, not an MVP one
6. Content hashing and canonicalisation of the Evidence Package
