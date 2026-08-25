package com.relay.delivery.api;

import com.relay.shared.Ids;

import java.time.Instant;

/** A pull request carrying an Evidence Package has been opened on a consumer repository. */
public record PullRequestPublished(
        Ids.EvidencePackageId packageId,
        Ids.RepositoryId repositoryId,
        String pullRequestUrl,
        int pullRequestNumber,
        Instant publishedAt
) {
}
