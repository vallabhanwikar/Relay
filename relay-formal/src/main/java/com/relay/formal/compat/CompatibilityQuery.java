package com.relay.formal.compat;

import com.relay.formal.schema.Schema;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * One schema-compatibility question for the prover.
 *
 * <ul>
 *   <li>{@link Direction#RESPONSE}: can the provider, under the new schema, send something the
 *       consumer - written against the old schema and scoped to what it reads - does not
 *       accept?</li>
 *   <li>{@link Direction#REQUEST}: can the consumer, sending what it sent under the old schema,
 *       produce something the provider no longer accepts under the new one?</li>
 * </ul>
 *
 * @param rejectsUnknownProperties the consumer's JSON mapper fails on unknown fields (Jackson
 *                                 {@code FAIL_ON_UNKNOWN_PROPERTIES}); Spring Boot's default is
 *                                 {@code false}. Only meaningful for responses.
 * @param maxDepth                 how deep {@link Usage#allFields()} enumerates; deeper fields
 *                                 are reported as unchecked
 * @param ignoredKeywords          schema keywords the reader did not model, carried into the
 *                                 verdict's assumptions
 */
public record CompatibilityQuery(
        Direction direction,
        Schema oldSchema,
        Schema newSchema,
        Usage usage,
        boolean rejectsUnknownProperties,
        Duration timeout,
        int maxDepth,
        Set<String> ignoredKeywords
) {

    public enum Direction {
        RESPONSE, REQUEST
    }

    public CompatibilityQuery {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(oldSchema, "oldSchema");
        Objects.requireNonNull(newSchema, "newSchema");
        Objects.requireNonNull(usage, "usage");
        Objects.requireNonNull(timeout, "timeout");
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be at least 1");
        }
        ignoredKeywords = Set.copyOf(ignoredKeywords);
    }

    public static CompatibilityQuery response(Schema oldSchema, Schema newSchema, Usage usage) {
        return new CompatibilityQuery(Direction.RESPONSE, oldSchema, newSchema, usage, false,
                Duration.ofSeconds(30), 6, Set.of());
    }

    public static CompatibilityQuery request(Schema oldSchema, Schema newSchema, Usage usage) {
        return new CompatibilityQuery(Direction.REQUEST, oldSchema, newSchema, usage, false,
                Duration.ofSeconds(30), 6, Set.of());
    }

    public CompatibilityQuery withRejectsUnknownProperties(boolean rejects) {
        return new CompatibilityQuery(direction, oldSchema, newSchema, usage, rejects, timeout,
                maxDepth, ignoredKeywords);
    }

    public CompatibilityQuery withTimeout(Duration newTimeout) {
        return new CompatibilityQuery(direction, oldSchema, newSchema, usage, rejectsUnknownProperties,
                newTimeout, maxDepth, ignoredKeywords);
    }

    public CompatibilityQuery withMaxDepth(int depth) {
        return new CompatibilityQuery(direction, oldSchema, newSchema, usage, rejectsUnknownProperties,
                timeout, depth, ignoredKeywords);
    }

    public CompatibilityQuery withIgnoredKeywords(Set<String> keywords) {
        return new CompatibilityQuery(direction, oldSchema, newSchema, usage, rejectsUnknownProperties,
                timeout, maxDepth, keywords);
    }
}
