package com.relay.shared;

/**
 * Severity classification for a detected change (ticket 8).
 *
 * <p>{@link #RISKY} is the interesting one and the reason a two-valued enum will not do: a
 * change that the spec says is additive but whose behaviour may have shifted - a repurposed
 * enum value, a changed pagination default - is neither safe nor provably breaking until the
 * replay stage runs. The plan calls these semantic failures and calls them Relay's moat.
 */
public enum Severity {
    SAFE,
    RISKY,
    BREAKING
}
