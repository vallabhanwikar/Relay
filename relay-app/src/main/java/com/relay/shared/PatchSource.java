package com.relay.shared;

/**
 * Where a candidate patch came from. The proof engine treats all sources identically - that
 * neutrality is the product position (plan, Section 3: "agents are Relay's suppliers, not
 * rivals"), and it is also what lets Relay demonstrate value at zero AI in the Week-12 demo.
 */
public enum PatchSource {
    HUMAN,
    RELAY_AGENT,
    EXTERNAL_AGENT
}
