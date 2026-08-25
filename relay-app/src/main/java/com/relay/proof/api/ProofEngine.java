package com.relay.proof.api;

import com.relay.shared.CandidatePatch;
import com.relay.shared.Ids;

/**
 * The port every patch source calls. Submission is asynchronous by design - verification runs
 * take minutes and execute in a durable workflow, so callers get a run id and learn the outcome
 * from {@link ProofCompleted}.
 */
public interface ProofEngine {

    /**
     * Queue a candidate patch for verification.
     *
     * @param depthHint requested depth; the orchestrator may run deeper if the risk warrants it
     *                  but never shallower than the change severity requires
     */
    Ids.VerificationRunId submit(CandidatePatch patch, VerificationDepth depthHint);
}
