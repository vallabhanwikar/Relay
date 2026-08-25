package com.relay.evidence.api;

import com.relay.shared.EvidenceLevel;

/**
 * An executable verification of an invariant - a jqwik property, a replay comparison, a compile
 * step, a contract test. The {@link EvidenceLevel} a check can produce is a property of the
 * check itself, which is what stops a model opinion from being filed as behavioural evidence.
 */
public record Check(
        String id,
        String invariantId,
        String description,
        EvidenceLevel produces
) {
}
