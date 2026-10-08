package com.relay.formal.schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A resolved JSON Schema, reduced to the constructs the prover encodes.
 *
 * <p>Every variant except {@link AnySchema}, {@link NeverSchema} and {@link CompositeSchema}
 * carries a {@code nullable} flag, which is how OpenAPI 3.0's {@code nullable: true} and
 * OpenAPI 3.1's {@code type: [X, "null"]} both arrive here. References are already resolved;
 * a reference chain deeper than the reader's bound arrives as {@link TruncatedSchema}, so the
 * prover can say that it stopped instead of pretending the subtree was checked.
 */
public sealed interface Schema
        permits Schema.AnySchema, Schema.NeverSchema, Schema.NullSchema, Schema.BooleanSchema,
        Schema.NumberSchema, Schema.StringSchema, Schema.ObjectSchema, Schema.ArraySchema,
        Schema.CompositeSchema, Schema.TruncatedSchema {

    /** Accepts every JSON value: {@code {}} or {@code true}. */
    record AnySchema() implements Schema {
    }

    /** Accepts nothing: {@code false}. Used for {@code additionalProperties: false}. */
    record NeverSchema() implements Schema {
    }

    /** Accepts only JSON {@code null}: OpenAPI 3.1 {@code type: "null"}. */
    record NullSchema() implements Schema {
    }

    record BooleanSchema(List<Boolean> enumValues, boolean nullable) implements Schema {
        public BooleanSchema {
            enumValues = List.copyOf(enumValues);
        }
    }

    /**
     * {@code type: integer} or {@code type: number}. Bounds are exact decimals. Exclusive bounds
     * are normalised from both the 3.0 boolean form and the 3.1 numeric form.
     */
    record NumberSchema(
            boolean integer,
            BigDecimal minimum,
            boolean exclusiveMinimum,
            BigDecimal maximum,
            boolean exclusiveMaximum,
            BigDecimal multipleOf,
            List<BigDecimal> enumValues,
            boolean nullable
    ) implements Schema {
        public NumberSchema {
            enumValues = List.copyOf(enumValues);
        }
    }

    /**
     * {@code type: string}. {@code pattern} and {@code format} are kept as text; the encoder
     * treats them as opaque predicates, which is sound for proofs (see {@code SchemaEncoder}).
     */
    record StringSchema(
            List<String> enumValues,
            Integer minLength,
            Integer maxLength,
            String pattern,
            String format,
            boolean nullable
    ) implements Schema {
        public StringSchema {
            enumValues = List.copyOf(enumValues);
        }
    }

    /**
     * {@code type: object}. {@code additionalProperties} is {@link AnySchema} when extra fields
     * are allowed (the default), {@link NeverSchema} when forbidden, or a schema they must match.
     */
    record ObjectSchema(
            Map<String, Schema> properties,
            Set<String> required,
            Schema additionalProperties,
            boolean nullable
    ) implements Schema {
        public ObjectSchema {
            properties = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(properties));
            required = Set.copyOf(required);
        }
    }

    record ArraySchema(Schema items, Integer minItems, Integer maxItems, boolean nullable) implements Schema {
    }

    /** {@code allOf}, {@code anyOf} or {@code oneOf} over resolved alternatives. */
    record CompositeSchema(Mode mode, List<Schema> options) implements Schema {
        public CompositeSchema {
            options = List.copyOf(options);
        }

        public enum Mode {
            ALL_OF, ANY_OF, ONE_OF
        }
    }

    /**
     * A subtree the reader did not expand - a reference chain past the depth bound, usually a
     * recursive type. Encoded as unconstrained, and always reported as an assumption.
     */
    record TruncatedSchema(String reason) implements Schema {
    }
}
