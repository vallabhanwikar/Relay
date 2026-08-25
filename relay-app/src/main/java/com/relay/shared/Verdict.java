package com.relay.shared;

/**
 * The outcome of running one check. Deliberately three-valued: a check that could not be run -
 * missing fixture, sandbox timeout, unsupported language - is {@link #INCONCLUSIVE}, never
 * {@code PASS}. Collapsing "did not run" into "passed" is the single failure mode that would
 * make an Evidence Package worthless.
 */
public enum Verdict {
    PASS,
    FAIL,
    INCONCLUSIVE
}
