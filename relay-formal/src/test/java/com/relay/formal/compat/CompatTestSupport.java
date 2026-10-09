package com.relay.formal.compat;

import com.relay.formal.compat.CompatibilityQuery.Direction;
import com.relay.formal.compat.CompatibilityResult.CounterexampleFound;
import com.relay.formal.compat.CompatibilityResult.Proven;
import com.relay.formal.json.Json;
import com.relay.formal.schema.FieldPath;
import com.relay.formal.schema.Schema;
import com.relay.formal.schema.SchemaReader;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Shared fixtures and assertions for the compatibility tests. */
final class CompatTestSupport {

    /**
     * The running example from the design doc: a payments API response. The consumer in most
     * tests reads {@code status} and {@code amount.value}.
     */
    static final String PAYMENT_V1 = """
            {
              "type": "object",
              "required": ["id", "status", "amount"],
              "properties": {
                "id":     {"type": "string", "maxLength": 36},
                "status": {"type": "string", "enum": ["PAID", "FAILED"]},
                "amount": {
                  "type": "object",
                  "required": ["value", "currency"],
                  "properties": {
                    "value":    {"type": "number", "minimum": 0},
                    "currency": {"type": "string", "minLength": 3, "maxLength": 3}
                  }
                },
                "fee":  {"type": "integer", "minimum": 0},
                "note": {"type": "string", "nullable": true}
              }
            }
            """;

    private static final SchemaCompatibilityChecker CHECKER = new SchemaCompatibilityChecker();

    private CompatTestSupport() {
    }

    static Schema schema(String json) {
        return SchemaReader.read(Json.parseObject(json), 8).schema();
    }

    /** {@link #PAYMENT_V1} with one textual edit applied; fails loudly if the edit misses. */
    static String paymentWith(String target, String replacement) {
        if (!PAYMENT_V1.contains(target)) {
            throw new IllegalArgumentException("fixture does not contain: " + target);
        }
        return PAYMENT_V1.replace(target, replacement);
    }

    static CompatibilityResult response(String oldJson, String newJson, Usage usage) {
        return CHECKER.check(CompatibilityQuery.response(schema(oldJson), schema(newJson), usage));
    }

    static CompatibilityResult request(String oldJson, String newJson, Usage usage) {
        return CHECKER.check(CompatibilityQuery.request(schema(oldJson), schema(newJson), usage));
    }

    static CompatibilityResult check(CompatibilityQuery query) {
        return CHECKER.check(query);
    }

    static Proven assertProven(CompatibilityResult result) {
        return assertInstanceOf(Proven.class, result, "expected PROVEN, got " + describe(result));
    }

    /**
     * Asserts a counterexample and checks it independently: the witness must be accepted by the
     * producing schema according to {@link ConcreteValidator}, which shares no code with the
     * encoder. A witness that fails this check would be an encoding bug.
     */
    static CounterexampleFound assertCounterexample(CompatibilityResult result, CompatibilityQuery query) {
        CounterexampleFound found = assertInstanceOf(CounterexampleFound.class, result,
                "expected COUNTEREXAMPLE, got " + describe(result));
        assertFalse(found.violations().isEmpty(), "a counterexample must name what it violates");
        if (found.exact()) {
            Schema producer = query.direction() == Direction.RESPONSE ? query.newSchema() : query.oldSchema();
            assertTrue(ConcreteValidator.valid(producer, found.witness()),
                    "witness is not valid under the producing schema: " + found.witnessJson());
        }
        return found;
    }

    static CounterexampleFound assertResponseCounterexample(String oldJson, String newJson, Usage usage) {
        CompatibilityQuery query = CompatibilityQuery.response(schema(oldJson), schema(newJson), usage);
        return assertCounterexample(CHECKER.check(query), query);
    }

    static CounterexampleFound assertRequestCounterexample(String oldJson, String newJson, Usage usage) {
        CompatibilityQuery query = CompatibilityQuery.request(schema(oldJson), schema(newJson), usage);
        return assertCounterexample(CHECKER.check(query), query);
    }

    static boolean violates(CounterexampleFound found, String path, String expectationFragment) {
        FieldPath target = FieldPath.parse(path);
        return found.violations().stream()
                .anyMatch(v -> v.path().equals(target) && v.expectation().contains(expectationFragment));
    }

    /** Navigates the witness: {@code at(witness, "amount", "value")}. */
    @SuppressWarnings("unchecked")
    static Object at(Object json, Object... steps) {
        Object current = json;
        for (Object step : steps) {
            current = step instanceof Integer i
                    ? ((List<Object>) current).get(i)
                    : ((Map<String, Object>) current).get((String) step);
        }
        return current;
    }

    static String describe(CompatibilityResult result) {
        if (result instanceof CounterexampleFound c) {
            return "COUNTEREXAMPLE " + c.violations() + " witness=" + Json.write(c.witness());
        }
        return result.toString();
    }
}
