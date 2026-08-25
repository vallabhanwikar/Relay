package com.relay.shared;

import java.util.UUID;

/**
 * Typed identifiers. Every one of these is a UUID underneath, which is precisely why they are
 * wrapped: an {@code ImpactId} passed where a {@code PatchId} was expected is a compile error
 * here and a silent evidence-integrity bug otherwise.
 */
public final class Ids {

    private Ids() {
    }

    public record ExternalApiId(UUID value) {
        public static ExternalApiId random() {
            return new ExternalApiId(UUID.randomUUID());
        }
    }

    public record ApiVersionId(UUID value) {
        public static ApiVersionId random() {
            return new ApiVersionId(UUID.randomUUID());
        }
    }

    public record ChangeSetId(UUID value) {
        public static ChangeSetId random() {
            return new ChangeSetId(UUID.randomUUID());
        }
    }

    public record RepositoryId(UUID value) {
        public static RepositoryId random() {
            return new RepositoryId(UUID.randomUUID());
        }
    }

    public record ImpactId(UUID value) {
        public static ImpactId random() {
            return new ImpactId(UUID.randomUUID());
        }
    }

    public record PatchId(UUID value) {
        public static PatchId random() {
            return new PatchId(UUID.randomUUID());
        }
    }

    public record VerificationRunId(UUID value) {
        public static VerificationRunId random() {
            return new VerificationRunId(UUID.randomUUID());
        }
    }

    public record EvidencePackageId(UUID value) {
        public static EvidencePackageId random() {
            return new EvidencePackageId(UUID.randomUUID());
        }
    }

    public record BenchCaseId(String value) {
    }
}
