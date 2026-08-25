package com.relay.impact.api;

import com.relay.detection.api.ChangeCategory;
import com.relay.shared.Ids;

/** One code location affected by one change, with the reason it was flagged. */
public record AffectedSite(
        Ids.RepositoryId repositoryId,
        String filePath,
        int lineNumber,
        String method,
        String service,
        ChangeCategory dueTo,
        String explanation
) {
}
