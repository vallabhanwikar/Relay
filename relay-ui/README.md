# relay-ui

Next.js + TypeScript dashboard. Phase 5 (ticket F16): changes feed, blast radius view, evidence
viewer, bench scoreboard.

Not scaffolded yet, deliberately — a UI built before there is anything to display becomes a
maintenance cost that produces no evidence.

One constraint fixed now, because it is expensive to retrofit: **the evidence viewer renders
server-produced JSON; it never constructs an Evidence Package.** The package a human reads in a
browser and the package a script verifies by hash must be the same object.
