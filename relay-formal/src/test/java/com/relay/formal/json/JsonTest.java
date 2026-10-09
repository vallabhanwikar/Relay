package com.relay.formal.json;

import com.relay.formal.schema.FieldPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {

    @Test
    @DisplayName("round-trips objects, arrays, escapes and exact decimals")
    void roundTrip() {
        String text = "{\"a\":[1,2.50,-3e2],\"s\":\"q\\\"u\\\\o\\n\\u00e9\",\"n\":null,\"t\":true}";
        Object parsed = Json.parse(text);

        Map<?, ?> map = (Map<?, ?>) parsed;
        assertEquals(new BigDecimal("2.50"), ((List<?>) map.get("a")).get(1));
        assertEquals("q\"u\\o\né", map.get("s"));
        assertEquals(Json.NULL, map.get("n"));
        assertEquals(parsed, Json.parse(Json.write(parsed)));
        assertEquals(parsed, Json.parse(Json.writePretty(parsed)));
    }

    @Test
    @DisplayName("rejects malformed input and duplicate keys")
    void rejectsMalformed() {
        assertThrows(Json.JsonException.class, () -> Json.parse("{\"a\":1,}"));
        assertThrows(Json.JsonException.class, () -> Json.parse("{\"a\":1,\"a\":2}"));
        assertThrows(Json.JsonException.class, () -> Json.parse("[1 2]"));
        assertThrows(Json.JsonException.class, () -> Json.parse("tru"));
    }

    @Test
    @DisplayName("field paths parse and print in the usage notation")
    void fieldPaths() {
        FieldPath path = FieldPath.parse("lines[].amount.value");
        assertEquals(List.of("lines", "[]", "amount", "value"), path.segments());
        assertEquals("lines[].amount.value", path.toString());
        assertEquals(FieldPath.ROOT, FieldPath.parse(""));
        assertEquals(List.of("[]", "sku"), FieldPath.parse("[].sku").segments());
        assertTrue(FieldPath.parse("lines[]").isItems());
        assertEquals(5, FieldPath.parse("a.b.c.d").selfAndAncestors().size());
    }
}
