package com.relay.delivery.internal;

import org.springframework.stereotype.Service;

/**
 * TODO(Phase 5): GitHub App with least-privilege permissions (contents + pull-requests only);
 * webhook intake; PR creation with the Evidence Package as comment + checks.
 *
 * <p>Listens for {@code EvidencePackagePublished}. The event boundary matters: delivery is the
 * one module that talks to the outside world on Relay's behalf, and keeping it downstream of a
 * frozen, hashed package means nothing reaches a customer repository that was not first
 * recorded as evidence.
 *
 * <p>F21 (post-MVP) adds a second intake here - PR-triggered verification - which reuses the
 * same proof engine rather than rebuilding one. One engine, two doors.
 */
@Service
class GitHubDeliveryService {
}
