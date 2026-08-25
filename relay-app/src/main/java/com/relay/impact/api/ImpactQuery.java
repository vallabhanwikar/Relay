package com.relay.impact.api;

import com.relay.shared.Ids;

import java.util.Optional;

/** Read side, for modules that need the analysis after the fact rather than on the event. */
public interface ImpactQuery {

    Optional<ImpactAnalysed> findById(Ids.ImpactId impactId);
}
