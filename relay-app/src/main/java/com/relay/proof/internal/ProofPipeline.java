package com.relay.proof.internal;

import org.springframework.stereotype.Service;

/**
 * TODO(ticket 16): sandbox runner - docker build/test, no network, CPU/mem caps, artifact capture.
 * TODO(ticket 17): proof pipeline skeleton in Temporal - compile -> unit -> integration stages.
 * TODO(ticket 18): WireMock record/replay + dynamic-field normaliser + semantic differ.
 *
 * <p>Phase 3 definition of done: replay correctly flags a seeded semantic failure - HTTP 200,
 * wrong meaning - that all example tests pass. That single test case is the whole argument for
 * why the E5 rung exists.
 *
 * <p>TLA+ (ticket 19 neighbourhood, F12) specifies this module's concurrency: overlapping
 * changes to the same dependency, timeout during verify, retry semantics. The spec lives in
 * {@code docs/spec/} and is checked with TLC in CI.
 */
@Service
class ProofPipeline {
}
