package com.relay.repair.api;

import com.relay.shared.CandidatePatch;
import com.relay.shared.Ids;

import java.util.Optional;

/**
 * Attempts a repair for an analysed impact. Returns empty when the agent cannot produce a
 * candidate within its retry budget - an honest miss, which the bench records as such rather
 * than emitting a patch nobody verified.
 */
public interface RepairAgent {

    Optional<CandidatePatch> attempt(Ids.ImpactId impactId);
}
