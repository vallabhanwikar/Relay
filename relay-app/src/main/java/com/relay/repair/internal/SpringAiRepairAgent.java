package com.relay.repair.internal;

import org.springframework.stereotype.Service;

/**
 * TODO(Phase 4): Spring AI agent with allowlisted tools and minimal context construction from
 * the dependency graph; agent loop propose -> proof engine -> failure interpretation -> bounded
 * retry (max 3).
 *
 * <p>Context engineering is the whole game here. The temptation is to hand the model the
 * repository; the discipline is to hand it the change, the affected symbols with signatures,
 * and the relevant tests - which is only possible because {@code repository} already built the
 * symbol graph. Blog post #4 is written from the measured comparison: a local coding model with
 * a proof engine beats a frontier model without one.
 */
@Service
class SpringAiRepairAgent {
}
