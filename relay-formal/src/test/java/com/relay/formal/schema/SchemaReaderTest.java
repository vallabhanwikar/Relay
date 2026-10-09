package com.relay.formal.schema;

import com.relay.formal.json.Json;
import com.relay.formal.schema.Schema.ArraySchema;
import com.relay.formal.schema.Schema.CompositeSchema;
import com.relay.formal.schema.Schema.NeverSchema;
import com.relay.formal.schema.Schema.NullSchema;
import com.relay.formal.schema.Schema.NumberSchema;
import com.relay.formal.schema.Schema.ObjectSchema;
import com.relay.formal.schema.Schema.StringSchema;
import com.relay.formal.schema.Schema.TruncatedSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaReaderTest {

    private static SchemaReader.Result read(String json) {
        return SchemaReader.read(Json.parseObject(json), 8);
    }

    @Test
    @DisplayName("OpenAPI 3.0 nullable and 3.1 type arrays both produce a nullable schema")
    void nullableForms() {
        StringSchema v30 = assertInstanceOf(StringSchema.class, read("{\"type\": \"string\", \"nullable\": true}").schema());
        StringSchema v31 = assertInstanceOf(StringSchema.class, read("{\"type\": [\"string\", \"null\"]}").schema());
        assertTrue(v30.nullable());
        assertTrue(v31.nullable());
        assertInstanceOf(NullSchema.class, read("{\"type\": [\"null\"]}").schema());
    }

    @Test
    @DisplayName("3.0 boolean and 3.1 numeric exclusive bounds normalise to the same schema")
    void exclusiveBounds() {
        NumberSchema v30 = assertInstanceOf(NumberSchema.class,
                read("{\"type\": \"number\", \"minimum\": 0, \"exclusiveMinimum\": true}").schema());
        NumberSchema v31 = assertInstanceOf(NumberSchema.class,
                read("{\"type\": \"number\", \"exclusiveMinimum\": 0}").schema());
        assertEquals(v30, v31);
        assertEquals(0, v31.minimum().compareTo(BigDecimal.ZERO));
        assertTrue(v31.exclusiveMinimum());
    }

    @Test
    @DisplayName("local references resolve; the 3.0 nullable-allOf idiom keeps its null")
    void referencesResolve() {
        String doc = """
                {
                  "components": {"schemas": {"Money": {"type": "object", "required": ["value"],
                                                        "properties": {"value": {"type": "number"}}}}},
                  "type": "object",
                  "properties": {
                    "price":    {"$ref": "#/components/schemas/Money"},
                    "discount": {"nullable": true, "allOf": [{"$ref": "#/components/schemas/Money"}]}
                  }
                }
                """;
        ObjectSchema root = assertInstanceOf(ObjectSchema.class, read(doc).schema());
        ObjectSchema price = assertInstanceOf(ObjectSchema.class, root.properties().get("price"));
        assertEquals(Set.of("value"), price.required());

        CompositeSchema discount = assertInstanceOf(CompositeSchema.class, root.properties().get("discount"));
        assertEquals(CompositeSchema.Mode.ANY_OF, discount.mode());
        assertInstanceOf(NullSchema.class, discount.options().get(0));
    }

    @Test
    @DisplayName("a recursive reference stops at the bound instead of looping")
    void recursionIsBounded() {
        String doc = """
                {"components": {"schemas": {"Node": {"type": "array", "items": {"$ref": "#/components/schemas/Node"}}}},
                 "$ref": "#/components/schemas/Node"}
                """;
        Schema schema = SchemaReader.read(Json.parseObject(doc), "/components/schemas/Node", 2).schema();
        ArraySchema level0 = assertInstanceOf(ArraySchema.class, schema);
        ArraySchema level1 = assertInstanceOf(ArraySchema.class, level0.items());
        ArraySchema level2 = assertInstanceOf(ArraySchema.class, level1.items());
        assertInstanceOf(TruncatedSchema.class, level2.items());
    }

    @Test
    @DisplayName("additionalProperties false, enums without a type, and unmodelled keywords")
    void miscellaneous() {
        ObjectSchema closed = assertInstanceOf(ObjectSchema.class,
                read("{\"type\": \"object\", \"additionalProperties\": false}").schema());
        assertInstanceOf(NeverSchema.class, closed.additionalProperties());

        StringSchema untyped = assertInstanceOf(StringSchema.class, read("{\"enum\": [\"A\", \"B\"]}").schema());
        assertEquals(List.of("A", "B"), untyped.enumValues());

        SchemaReader.Result result = read("{\"type\": \"array\", \"uniqueItems\": true, \"x-internal\": 1, \"description\": \"d\"}");
        assertEquals(Set.of("uniqueItems"), result.ignoredKeywords(),
                "vendor extensions and annotations are not 'ignored'; real keywords are");
    }

    @Test
    @DisplayName("malformed schemas fail loudly")
    void malformedSchemas() {
        assertThrows(SchemaReader.SchemaException.class, () -> read("{\"type\": \"strnig\"}"));
        assertThrows(SchemaReader.SchemaException.class, () -> read("{\"$ref\": \"other.json#/X\"}"));
        assertThrows(SchemaReader.SchemaException.class, () -> read("{\"$ref\": \"#/missing\"}"));
        assertFalse(read("{}").schema() instanceof NullSchema);
    }
}
