package com.relay.impact.internal;

import org.springframework.stereotype.Service;

/**
 * TODO(ticket 14): ChangeEvent x dependency graph -> ImpactReport (files/methods/services).
 * TODO(ticket 15): blast-radius renderer (JSON + HTML summary).
 *
 * <p>Listens for {@code ChangeSetDetected} and publishes {@code ImpactAnalysed}. Both hops go
 * through the Spring Modulith event publication registry, so a failure here is retried rather
 * than dropped - the durability that would otherwise require the message broker Section 5
 * rules out.
 */
@Service
class ImpactAnalyser {
}
