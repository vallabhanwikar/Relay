package com.relay.formal.compat;

import com.relay.formal.schema.FieldPath;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * What the consumer actually touches: for a response, the fields its code reads; for a request,
 * the fields it sends. This is what scopes a proof to one codebase (design v3, "Why read set
 * matters") - a removed field that nobody reads is proven safe instead of reported.
 *
 * <p>{@link #allFields()} is the sound default when usage is unknown (no repository analysis
 * yet, or reflective access the analyser could not follow): it assumes the consumer depends on
 * every field the old contract declared. That over-approximates the consumer, so it can only
 * produce extra counterexamples, never a false proof.
 */
public final class Usage {

    /**
     * How a consumer tolerates deviations at one path. Each tolerance is a claim about consumer
     * code; in v3 it comes from repository analysis or from a human, and it is listed on every
     * verdict that relied on it.
     */
    public enum Tolerance {
        /** The code null-checks this field, so absence and {@code null} are both handled. */
        NULL_SAFE,
        /** The code has a default branch for unknown enum values at this field. */
        UNKNOWN_ENUM_SAFE
    }

    private final boolean allFields;
    private final Map<FieldPath, Set<Tolerance>> paths;

    private Usage(boolean allFields, Map<FieldPath, Set<Tolerance>> paths) {
        this.allFields = allFields;
        this.paths = paths;
    }

    public static Usage allFields() {
        return new Usage(true, Map.of());
    }

    /** Usage of exactly these paths, in text form ({@code lines[].sku}), with no tolerances. */
    public static Usage of(String... paths) {
        Builder builder = builder();
        for (String path : paths) {
            builder.uses(path);
        }
        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isAllFields() {
        return allFields;
    }

    /** The declared paths. Empty when {@link #isAllFields()}. */
    public Set<FieldPath> paths() {
        return paths.keySet();
    }

    public Set<Tolerance> tolerancesAt(FieldPath path) {
        Set<Tolerance> tolerances = paths.get(path);
        return tolerances == null ? Set.of() : tolerances;
    }

    @Override
    public String toString() {
        return allFields ? "all fields" : paths.toString();
    }

    public static final class Builder {
        private final Map<FieldPath, Set<Tolerance>> paths = new LinkedHashMap<>();

        public Builder uses(String path, Tolerance... tolerances) {
            return uses(FieldPath.parse(path), tolerances);
        }

        public Builder uses(FieldPath path, Tolerance... tolerances) {
            Set<Tolerance> set = paths.computeIfAbsent(path, p -> EnumSet.noneOf(Tolerance.class));
            set.addAll(java.util.List.of(tolerances));
            return this;
        }

        public Usage build() {
            Map<FieldPath, Set<Tolerance>> copy = new LinkedHashMap<>();
            paths.forEach((path, tolerances) -> copy.put(path,
                    tolerances.isEmpty() ? Set.of() : Set.copyOf(tolerances)));
            return new Usage(false, java.util.Collections.unmodifiableMap(copy));
        }
    }
}
