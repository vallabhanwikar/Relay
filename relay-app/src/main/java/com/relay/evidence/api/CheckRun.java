package com.relay.evidence.api;

import com.relay.shared.Verdict;

import java.time.Duration;
import java.time.Instant;

/**
 * One execution of a check inside a sandbox. Everything needed to argue the run was real and
 * to attempt reproducing it.
 *
 * @param inputsHash hash of the inputs, so an identical rerun is recognisable as such
 * @param exitState  raw exit state from the sandbox, kept verbatim rather than interpreted
 */
public record CheckRun(
        String id,
        String checkId,
        String containerId,
        String inputsHash,
        Verdict verdict,
        String exitState,
        String logsRef,
        Duration duration,
        Instant ranAt
) {
}
