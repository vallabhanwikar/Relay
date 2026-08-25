package com.relay.shared;

/**
 * The evidence ladder (plan, Section 6). Every verdict Relay records carries exactly one level,
 * and the level - not model confidence - is what makes a verdict comparable to another.
 *
 * <p>The ordering matters and is load-bearing: {@code PASS} at {@link #E3_GENERATIVE} outranks
 * {@code PASS} at {@link #E0_AI_OPINION}. {@link #E0_AI_OPINION} is recorded but never
 * load-bearing - it exists so a reviewer can see what the model thought and see that Relay did
 * not rely on it.
 */
public enum EvidenceLevel {

    /** Model self-assessment. Recorded, never load-bearing. */
    E0_AI_OPINION,

    /** Compile, static analysis, ArchUnit. */
    E1_STATIC,

    /** The consumer repository's own unit and integration tests. */
    E2_EXAMPLE,

    /** jqwik properties, fuzzed inputs. */
    E3_GENERATIVE,

    /** PIT mutation score over affected classes; later, the adversarial agent. */
    E4_ADVERSARIAL,

    /** WireMock traffic replay plus semantic diff - behaviour under real recorded traffic. */
    E5_BEHAVIOURAL,

    /** TLA+/TLC over the verification workflow. The documented ceiling of the ladder. */
    E6_FORMAL;

    /** True when this level is strong enough to carry a verdict on its own. */
    public boolean isLoadBearing() {
        return this != E0_AI_OPINION;
    }

    public boolean outranks(EvidenceLevel other) {
        return this.ordinal() > other.ordinal();
    }
}
