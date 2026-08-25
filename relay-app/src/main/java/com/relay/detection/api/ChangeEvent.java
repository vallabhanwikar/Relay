package com.relay.detection.api;

import com.relay.shared.Severity;

/**
 * One typed, classified difference between two spec versions.
 *
 * @param jsonPointer location in the spec document, so a reviewer can navigate to the change
 * @param endpoint    the affected endpoint in {@code METHOD /path} form, when the change is
 *                    endpoint-scoped; null for document-level changes such as auth
 */
public record ChangeEvent(
        ChangeCategory category,
        Severity severity,
        String jsonPointer,
        String endpoint,
        String before,
        String after,
        String rationale
) {
}
