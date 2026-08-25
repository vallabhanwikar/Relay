/**
 * Delivery - GitHub App, pull requests, evidence publishing.
 *
 * <p>The surface where Relay's work becomes visible to a human: webhook intake, repository
 * discovery, and PR creation with the Evidence Package attached as a comment and as checks
 * (ticket F15).
 *
 * <p>Least-privilege by construction - contents plus pull-requests scopes only. Relay proposes;
 * it never merges. Auto-merge of low-risk verified patches is Level 5 on the trust ladder and
 * is explicitly post-MVP, gated on a measured false-repair rate. Autonomy is earned, not
 * shipped.
 *
 * <p>Phase 5 definition of done: spec change pushed -> PR with evidence appears in under
 * 15 minutes, against a real repository.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Delivery",
        allowedDependencies = {"evidence::api"}
)
package com.relay.delivery;
