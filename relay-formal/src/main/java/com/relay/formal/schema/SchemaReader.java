package com.relay.formal.schema;

import com.relay.formal.json.Json;
import com.relay.formal.schema.Schema.AnySchema;
import com.relay.formal.schema.Schema.ArraySchema;
import com.relay.formal.schema.Schema.BooleanSchema;
import com.relay.formal.schema.Schema.CompositeSchema;
import com.relay.formal.schema.Schema.NeverSchema;
import com.relay.formal.schema.Schema.NullSchema;
import com.relay.formal.schema.Schema.NumberSchema;
import com.relay.formal.schema.Schema.ObjectSchema;
import com.relay.formal.schema.Schema.StringSchema;
import com.relay.formal.schema.Schema.TruncatedSchema;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Turns a JSON Schema (OpenAPI 3.0 or 3.1 flavour) into a {@link Schema}.
 *
 * <p>Local references ({@code #/components/schemas/Payment}) are resolved against the document
 * the schema came from. A reference chain longer than {@code maxRefDepth} becomes a
 * {@link TruncatedSchema}; that is how recursive types terminate.
 *
 * <p>Keywords the prover does not model are not silently dropped: each one is recorded in
 * {@link Result#ignoredKeywords()} and surfaces as an assumption on every verdict.
 */
public final class SchemaReader {

    /** Keywords that only describe or annotate; ignoring them changes no verdict. */
    private static final Set<String> ANNOTATIONS = Set.of(
            "title", "description", "example", "examples", "default", "deprecated", "readOnly",
            "writeOnly", "externalDocs", "xml", "discriminator", "$schema", "$id", "$comment",
            "contentMediaType", "contentEncoding");

    /** Keywords this reader models. */
    private static final Set<String> MODELLED = Set.of(
            "$ref", "type", "nullable", "enum", "const", "allOf", "anyOf", "oneOf", "properties",
            "required", "additionalProperties", "items", "minimum", "maximum", "exclusiveMinimum",
            "exclusiveMaximum", "multipleOf", "minLength", "maxLength", "pattern", "format",
            "minItems", "maxItems");

    private final Map<String, Object> document;
    private final int maxRefDepth;
    private final Set<String> ignored = new TreeSet<>();

    private SchemaReader(Map<String, Object> document, int maxRefDepth) {
        this.document = document;
        this.maxRefDepth = maxRefDepth;
    }

    /** The parsed schema plus every keyword that was present but not modelled. */
    public record Result(Schema schema, Set<String> ignoredKeywords) {
        public Result {
            ignoredKeywords = Set.copyOf(ignoredKeywords);
        }
    }

    /**
     * Read the schema at {@code pointer} (a JSON Pointer such as
     * {@code /components/schemas/Payment}) inside {@code document}.
     */
    public static Result read(Map<String, Object> document, String pointer, int maxRefDepth) {
        SchemaReader reader = new SchemaReader(document, maxRefDepth);
        Object node = reader.resolvePointer(pointer);
        Schema schema = reader.readNode(node, 0);
        return new Result(schema, reader.ignored);
    }

    /** Read a schema that is itself the whole document. */
    public static Result read(Map<String, Object> schemaDocument, int maxRefDepth) {
        return read(schemaDocument, "", maxRefDepth);
    }

    /** Read a schema node that may reference other parts of {@code document}. */
    public static Result readNode(Map<String, Object> document, Object node, int maxRefDepth) {
        SchemaReader reader = new SchemaReader(document, maxRefDepth);
        Schema schema = reader.readNode(node, 0);
        return new Result(schema, reader.ignored);
    }

    private Schema readNode(Object node, int refDepth) {
        if (node instanceof Boolean b) {
            return b ? new AnySchema() : new NeverSchema();
        }
        if (!(node instanceof Map<?, ?> raw)) {
            throw new SchemaException("expected a schema object but found " + Json.write(node));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) raw;

        for (String key : map.keySet()) {
            if (!MODELLED.contains(key) && !ANNOTATIONS.contains(key) && !key.startsWith("x-")) {
                ignored.add(key);
            }
        }

        if (map.containsKey("$ref")) {
            if (refDepth >= maxRefDepth) {
                return new TruncatedSchema("reference " + map.get("$ref") + " beyond depth " + maxRefDepth);
            }
            Schema target = readNode(resolveRef(String.valueOf(map.get("$ref"))), refDepth + 1);
            // OpenAPI 3.1 allows siblings next to $ref; they narrow the target.
            Map<String, Object> siblings = new LinkedHashMap<>(map);
            siblings.remove("$ref");
            ANNOTATIONS.forEach(siblings::remove);
            if (siblings.isEmpty()) {
                return target;
            }
            return new CompositeSchema(CompositeSchema.Mode.ALL_OF,
                    List.of(target, readNode(siblings, refDepth)));
        }

        List<Schema> conjuncts = new ArrayList<>();
        Schema base = readTyped(map, refDepth);
        if (base != null) {
            conjuncts.add(base);
        }
        readComposite(map, "allOf", CompositeSchema.Mode.ALL_OF, refDepth).ifPresent(conjuncts::add);
        readComposite(map, "anyOf", CompositeSchema.Mode.ANY_OF, refDepth).ifPresent(conjuncts::add);
        readComposite(map, "oneOf", CompositeSchema.Mode.ONE_OF, refDepth).ifPresent(conjuncts::add);

        if (conjuncts.isEmpty()) {
            return new AnySchema();
        }
        Schema combined = conjuncts.size() == 1
                ? conjuncts.get(0)
                : new CompositeSchema(CompositeSchema.Mode.ALL_OF, conjuncts);
        if (base == null && Boolean.TRUE.equals(map.get("nullable"))) {
            // OpenAPI 3.0 idiom: {nullable: true, allOf: [{$ref: ...}]}.
            return new CompositeSchema(CompositeSchema.Mode.ANY_OF, List.of(new NullSchema(), combined));
        }
        return combined;
    }

    private java.util.Optional<Schema> readComposite(Map<String, Object> map, String key,
                                                     CompositeSchema.Mode mode, int refDepth) {
        Object value = map.get(key);
        if (value == null) {
            return java.util.Optional.empty();
        }
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw new SchemaException(key + " must be a non-empty array");
        }
        List<Schema> options = new ArrayList<>();
        for (Object option : list) {
            options.add(readNode(option, refDepth));
        }
        return java.util.Optional.of(new CompositeSchema(mode, options));
    }

    /**
     * The part of the schema expressed by {@code type} and its keywords, or {@code null} when
     * the node has no type information of its own (a bare {@code allOf}, say).
     */
    private Schema readTyped(Map<String, Object> map, int refDepth) {
        Set<String> types = new LinkedHashSet<>();
        Object type = map.get("type");
        if (type instanceof String s) {
            types.add(s);
        } else if (type instanceof List<?> list) {
            list.forEach(t -> types.add(String.valueOf(t)));
        } else if (type != null) {
            throw new SchemaException("type must be a string or an array of strings");
        }

        List<Object> enumValues = enumValues(map);
        boolean nullTyped = types.remove("null");
        boolean nullable = nullTyped
                || Boolean.TRUE.equals(map.get("nullable"))
                || (enumValues != null && enumValues.contains(Json.NULL));

        if (types.isEmpty()) {
            types.addAll(inferTypes(map));
        }
        if (types.isEmpty()) {
            if (enumValues != null) {
                return enumOnly(enumValues, nullable);
            }
            if (type != null) {
                // type: ["null"] alone.
                return new NullSchema();
            }
            return null;
        }

        List<Schema> alternatives = new ArrayList<>();
        for (String t : types) {
            alternatives.add(readSingleType(t, map, enumValues, nullable, refDepth));
        }
        if (alternatives.size() == 1) {
            return alternatives.get(0);
        }
        return new CompositeSchema(CompositeSchema.Mode.ANY_OF, alternatives);
    }

    /** Pre-3.1 documents often omit {@code type} next to {@code properties} or {@code items}. */
    private static Set<String> inferTypes(Map<String, Object> map) {
        Set<String> inferred = new LinkedHashSet<>();
        if (map.containsKey("properties") || map.containsKey("required")
                || map.containsKey("additionalProperties")) {
            inferred.add("object");
        }
        if (map.containsKey("items") || map.containsKey("minItems") || map.containsKey("maxItems")) {
            inferred.add("array");
        }
        return inferred;
    }

    private Schema readSingleType(String type, Map<String, Object> map, List<Object> enumValues,
                                  boolean nullable, int refDepth) {
        return switch (type) {
            case "string" -> new StringSchema(
                    stringEnum(enumValues),
                    integer(map, "minLength"),
                    integer(map, "maxLength"),
                    string(map, "pattern"),
                    string(map, "format"),
                    nullable);
            case "integer", "number" -> readNumber(type.equals("integer"), map, enumValues, nullable);
            case "boolean" -> new BooleanSchema(booleanEnum(enumValues), nullable);
            case "object" -> readObject(map, nullable, refDepth);
            case "array" -> new ArraySchema(
                    map.containsKey("items") ? readNode(map.get("items"), refDepth) : new AnySchema(),
                    integer(map, "minItems"),
                    integer(map, "maxItems"),
                    nullable);
            default -> throw new SchemaException("unknown type '" + type + "'");
        };
    }

    private NumberSchema readNumber(boolean integer, Map<String, Object> map, List<Object> enumValues,
                                    boolean nullable) {
        BigDecimal minimum = decimal(map, "minimum");
        BigDecimal maximum = decimal(map, "maximum");
        boolean exclusiveMin = false;
        boolean exclusiveMax = false;

        Object exMin = map.get("exclusiveMinimum");
        if (exMin instanceof Boolean b) {
            exclusiveMin = b;                       // OpenAPI 3.0: modifies minimum
        } else if (exMin instanceof BigDecimal d) {
            if (minimum == null || d.compareTo(minimum) >= 0) {
                minimum = d;                        // OpenAPI 3.1 / JSON Schema 2020-12
                exclusiveMin = true;
            }
        }
        Object exMax = map.get("exclusiveMaximum");
        if (exMax instanceof Boolean b) {
            exclusiveMax = b;
        } else if (exMax instanceof BigDecimal d) {
            if (maximum == null || d.compareTo(maximum) <= 0) {
                maximum = d;
                exclusiveMax = true;
            }
        }

        List<BigDecimal> numbers = new ArrayList<>();
        if (enumValues != null) {
            for (Object v : enumValues) {
                if (v instanceof BigDecimal d) {
                    numbers.add(d);
                }
            }
        }
        BigDecimal multipleOf = decimal(map, "multipleOf");
        if (multipleOf != null && multipleOf.signum() <= 0) {
            throw new SchemaException("multipleOf must be positive");
        }
        return new NumberSchema(integer, minimum, exclusiveMin, maximum, exclusiveMax, multipleOf,
                numbers, nullable);
    }

    private ObjectSchema readObject(Map<String, Object> map, boolean nullable, int refDepth) {
        Map<String, Schema> properties = new LinkedHashMap<>();
        Object props = map.get("properties");
        if (props instanceof Map<?, ?> propMap) {
            for (Map.Entry<?, ?> entry : propMap.entrySet()) {
                properties.put(String.valueOf(entry.getKey()), readNode(entry.getValue(), refDepth));
            }
        }
        Set<String> required = new LinkedHashSet<>();
        Object req = map.get("required");
        if (req instanceof List<?> list) {
            list.forEach(r -> required.add(String.valueOf(r)));
        }
        Object additional = map.get("additionalProperties");
        Schema additionalSchema = additional == null ? new AnySchema() : readNode(additional, refDepth);
        return new ObjectSchema(properties, required, additionalSchema, nullable);
    }

    /** A schema that has an enum but no type: infer the type from the values. */
    private Schema enumOnly(List<Object> values, boolean nullable) {
        List<Schema> options = new ArrayList<>();
        List<String> strings = stringEnum(values);
        if (!strings.isEmpty()) {
            options.add(new StringSchema(strings, null, null, null, null, false));
        }
        List<BigDecimal> numbers = new ArrayList<>();
        values.forEach(v -> {
            if (v instanceof BigDecimal d) {
                numbers.add(d);
            }
        });
        if (!numbers.isEmpty()) {
            options.add(new NumberSchema(false, null, false, null, false, null, numbers, false));
        }
        List<Boolean> booleans = booleanEnum(values);
        if (!booleans.isEmpty()) {
            options.add(new BooleanSchema(booleans, false));
        }
        if (nullable) {
            options.add(new NullSchema());
        }
        if (options.isEmpty()) {
            throw new SchemaException("enum contains only values the prover does not model");
        }
        return options.size() == 1 ? options.get(0) : new CompositeSchema(CompositeSchema.Mode.ANY_OF, options);
    }

    private static List<Object> enumValues(Map<String, Object> map) {
        if (map.containsKey("const")) {
            return List.of(map.get("const"));
        }
        Object values = map.get("enum");
        if (values == null) {
            return null;
        }
        if (!(values instanceof List<?> list) || list.isEmpty()) {
            throw new SchemaException("enum must be a non-empty array");
        }
        return new ArrayList<>(list);
    }

    private static List<String> stringEnum(List<Object> values) {
        List<String> result = new ArrayList<>();
        if (values != null) {
            values.forEach(v -> {
                if (v instanceof String s) {
                    result.add(s);
                }
            });
        }
        return result;
    }

    private static List<Boolean> booleanEnum(List<Object> values) {
        List<Boolean> result = new ArrayList<>();
        if (values != null) {
            values.forEach(v -> {
                if (v instanceof Boolean b) {
                    result.add(b);
                }
            });
        }
        return result;
    }

    private static Integer integer(Map<String, Object> map, String key) {
        BigDecimal value = decimal(map, key);
        if (value == null) {
            return null;
        }
        try {
            return value.intValueExact();
        } catch (ArithmeticException e) {
            throw new SchemaException(key + " must be an integer");
        }
    }

    private static BigDecimal decimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal d) {
            return d;
        }
        if (value instanceof Boolean) {
            return null; // 3.0 boolean exclusive* flags are handled by the caller
        }
        throw new SchemaException(key + " must be a number");
    }

    private static String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Object resolveRef(String ref) {
        if (!ref.startsWith("#")) {
            throw new SchemaException("only local references are supported, found '" + ref + "'");
        }
        return resolvePointer(ref.substring(1));
    }

    private Object resolvePointer(String pointer) {
        Object node = document;
        if (pointer.isEmpty()) {
            return node;
        }
        if (!pointer.startsWith("/")) {
            throw new SchemaException("JSON pointer must start with '/': " + pointer);
        }
        for (String rawToken : pointer.substring(1).split("/", -1)) {
            String token = rawToken.replace("~1", "/").replace("~0", "~");
            if (node instanceof Map<?, ?> map && map.containsKey(token)) {
                node = map.get(token);
            } else if (node instanceof List<?> list && token.matches("\\d+")
                    && Integer.parseInt(token) < list.size()) {
                node = list.get(Integer.parseInt(token));
            } else {
                throw new SchemaException("pointer '" + pointer + "' does not resolve");
            }
        }
        return node;
    }

    public static final class SchemaException extends RuntimeException {
        public SchemaException(String message) {
            super(message);
        }
    }
}
