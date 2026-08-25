# sandbox-runner

Disposable build/test containers. Phase 3 (ticket 16).

Constraints, all of which are evidence properties rather than ops preferences:

- **No network.** A verification run that can reach the internet is a run whose result depends on
  the internet, which makes it unreproducible.
- **CPU and memory capped.** A runaway consumer test must not take the host down.
- **Read-only base image**, writable overlay only.
- **Artifact capture** — logs, exit state, timings — flows into `CheckRun` records.
- **Disposable.** One container per run. No reuse, so no state leaks between runs.

Docker for the MVP. gVisor / microVM isolation is a documented future step, not an MVP one:
Section 7 is explicit that cheap and honest isolation beats expensive isolation that delays the
proof engine.
