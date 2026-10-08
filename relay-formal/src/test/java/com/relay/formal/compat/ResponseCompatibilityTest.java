package com.relay.formal.compat;

import com.relay.formal.compat.CompatibilityResult.CounterexampleFound;
import com.relay.formal.compat.CompatibilityResult.Proven;
import com.relay.formal.compat.Usage.Tolerance;
import com.relay.formal.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.relay.formal.compat.CompatTestSupport.PAYMENT_V1;
import static com.relay.formal.compat.CompatTestSupport.assertCounterexample;
import static com.relay.formal.compat.CompatTestSupport.assertProven;
import static com.relay.formal.compat.CompatTestSupport.assertResponseCounterexample;
import static com.relay.formal.compat.CompatTestSupport.at;
import static com.relay.formal.compat.CompatTestSupport.check;
import static com.relay.formal.compat.CompatTestSupport.paymentWith;
import static com.relay.formal.compat.CompatTestSupport.response;
import static com.relay.formal.compat.CompatTestSupport.schema;
import static com.relay.formal.compat.CompatTestSupport.violates;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Response direction: can the provider, under the new schema, send something this consumer does
 * not accept? Each test is one row of the change taxonomy, run through the real solver.
 */
class ResponseCompatibilityTest {

    private static final Usage READS_STATUS_AND_VALUE = Usage.of("status", "amount.value");

    // ---------------------------------------------------------------- baselines

    @Test
    @DisplayName("an unchanged schema is proven compatible even when the consumer uses every field")
    void identicalSchemasAreProven() {
        Proven proven = assertProven(response(PAYMENT_V1, PAYMENT_V1, Usage.allFields()));
        assertTrue(proven.assumptions().stream().anyMatch(a -> a.contains("usage unknown")),
                "an all-fields check must say it assumed full usage");
        assertEquals("z3", proven.solver().name());
    }

    // ---------------------------------------------------------------- enums

    @Test
    @DisplayName("a new enum value the consumer reads is a counterexample naming that value")
    void addedEnumValueIsACounterexample() {
        String v2 = paymentWith("\"enum\": [\"PAID\", \"FAILED\"]", "\"enum\": [\"PAID\", \"FAILED\", \"PARTIALLY_REFUNDED\"]");

        CounterexampleFound found = assertResponseCounterexample(PAYMENT_V1, v2, READS_STATUS_AND_VALUE);

        assertEquals("PARTIALLY_REFUNDED", at(found.witness(), "status"));
        assertTrue(violates(found, "status", "must be one of [PAID, FAILED]"), found.violations().toString());
        assertTrue(found.exact(), "no opaque constraints are involved, so the witness is exact");
    }

    @Test
    @DisplayName("a new enum value is proven safe when the consumer has a default branch")
    void addedEnumValueIsSafeForTolerantConsumer() {
        String v2 = paymentWith("\"enum\": [\"PAID\", \"FAILED\"]", "\"enum\": [\"PAID\", \"FAILED\", \"PARTIALLY_REFUNDED\"]");
        Usage tolerant = Usage.builder()
                .uses("status", Tolerance.UNKNOWN_ENUM_SAFE)
                .uses("amount.value")
                .build();

        Proven proven = assertProven(response(PAYMENT_V1, v2, tolerant));
        assertTrue(proven.assumptions().stream().anyMatch(a -> a.contains("UNKNOWN_ENUM_SAFE")),
                "a proof that relied on a tolerance must say so");
    }

    @Test
    @DisplayName("a removed enum value is proven safe: the consumer already handles the rest")
    void removedEnumValueIsProven() {
        String v2 = paymentWith("\"enum\": [\"PAID\", \"FAILED\"]", "\"enum\": [\"PAID\"]");
        assertProven(response(PAYMENT_V1, v2, READS_STATUS_AND_VALUE));
    }

    // ---------------------------------------------------------------- fields

    @Test
    @DisplayName("removing a required field nobody reads is proven safe, not reported")
    void removedUnreadFieldIsProven() {
        String v2 = PAYMENT_V1
                .replace("\"required\": [\"id\", \"status\", \"amount\"]", "\"required\": [\"status\", \"amount\"]")
                .replace("\"id\":     {\"type\": \"string\", \"maxLength\": 36},", "");

        assertProven(response(PAYMENT_V1, v2, READS_STATUS_AND_VALUE));
    }

    @Test
    @DisplayName("removing a required field the consumer reads produces a payload without it")
    void removedReadFieldIsACounterexample() {
        String v2 = PAYMENT_V1
                .replace("\"required\": [\"id\", \"status\", \"amount\"]", "\"required\": [\"status\", \"amount\"]")
                .replace("\"id\":     {\"type\": \"string\", \"maxLength\": 36},", "");

        CounterexampleFound found = assertResponseCounterexample(PAYMENT_V1, v2, Usage.of("id", "status"));

        // Once v2 stops declaring "id", the provider may omit it or send any value at all. Which
        // of the two the solver shows varies between Z3 versions; both are real counterexamples.
        assertTrue(found.violations().stream().anyMatch(v -> v.path().toString().equals("id")),
                found.violations().toString());
    }

    @Test
    @DisplayName("an added optional field is proven safe with Spring Boot's lenient mapper")
    void addedFieldIsSafeForLenientMapper() {
        String v2 = paymentWith("\"fee\":", "\"refundedAt\": {\"type\": \"string\"}, \"fee\":");
        assertProven(response(PAYMENT_V1, v2, READS_STATUS_AND_VALUE));
    }

    @Test
    @DisplayName("an added field breaks a consumer whose mapper fails on unknown properties")
    void addedFieldBreaksStrictMapper() {
        String v2 = paymentWith("\"fee\":", "\"refundedAt\": {\"type\": \"string\"}, \"fee\":");
        CompatibilityQuery query = CompatibilityQuery
                .response(schema(PAYMENT_V1), schema(v2), READS_STATUS_AND_VALUE)
                .withRejectsUnknownProperties(true);

        CounterexampleFound found = assertCounterexample(check(query), query);

        assertTrue(violates(found, "refundedAt", "FAIL_ON_UNKNOWN_PROPERTIES"), found.violations().toString());
    }

    // ---------------------------------------------------------------- nullability

    @Test
    @DisplayName("a field that becomes nullable breaks a consumer that dereferences it")
    void newlyNullableFieldIsACounterexample() {
        String v2 = paymentWith("\"value\":    {\"type\": \"number\", \"minimum\": 0}",
                "\"value\":    {\"type\": \"number\", \"minimum\": 0, \"nullable\": true}");

        CounterexampleFound found = assertResponseCounterexample(PAYMENT_V1, v2, READS_STATUS_AND_VALUE);

        assertEquals(Json.NULL, at(found.witness(), "amount", "value"));
        assertTrue(violates(found, "amount.value", "must not be null"), found.violations().toString());
    }

    @Test
    @DisplayName("a field that becomes nullable is proven safe when the consumer null-checks it")
    void newlyNullableFieldIsSafeWhenNullChecked() {
        String v2 = paymentWith("\"value\":    {\"type\": \"number\", \"minimum\": 0}",
                "\"value\":    {\"type\": \"number\", \"minimum\": 0, \"nullable\": true}");
        Usage nullSafe = Usage.builder().uses("status").uses("amount.value", Tolerance.NULL_SAFE).build();

        assertProven(response(PAYMENT_V1, v2, nullSafe));
    }

    // ---------------------------------------------------------------- types and ranges

    @Test
    @DisplayName("a type change on a read field is a counterexample")
    void typeChangeIsACounterexample() {
        String v2 = paymentWith("\"value\":    {\"type\": \"number\", \"minimum\": 0}",
                "\"value\":    {\"type\": \"string\"}");

        CounterexampleFound found = assertResponseCounterexample(PAYMENT_V1, v2, READS_STATUS_AND_VALUE);

        assertInstanceOf(String.class, at(found.witness(), "amount", "value"));
        assertTrue(violates(found, "amount.value", "must be a number"), found.violations().toString());
    }

    @Test
    @DisplayName("integer is a subset of number: number to integer is safe, the reverse is not")
    void integerNarrowingIsSafeButWideningIsNot() {
        String asNumber = PAYMENT_V1;
        String asInteger = paymentWith("\"value\":    {\"type\": \"number\", \"minimum\": 0}",
                "\"value\":    {\"type\": \"integer\", \"minimum\": 0}");

        assertProven(response(asNumber, asInteger, READS_STATUS_AND_VALUE));

        CounterexampleFound found = assertResponseCounterexample(asInteger, asNumber, READS_STATUS_AND_VALUE);
        BigDecimal value = (BigDecimal) at(found.witness(), "amount", "value");
        assertTrue(value.stripTrailingZeros().scale() > 0, "the witness must be a fraction, was " + value);
        assertTrue(violates(found, "amount.value", "whole number"), found.violations().toString());
    }

    @Test
    @DisplayName("a lower bound that drops below the old one admits a value the consumer never saw")
    void widenedRangeIsACounterexample() {
        String v2 = paymentWith("\"value\":    {\"type\": \"number\", \"minimum\": 0}",
                "\"value\":    {\"type\": \"number\", \"minimum\": -100}");

        CounterexampleFound found = assertResponseCounterexample(PAYMENT_V1, v2, READS_STATUS_AND_VALUE);

        BigDecimal value = (BigDecimal) at(found.witness(), "amount", "value");
        assertTrue(value.signum() < 0, "expected a negative amount, got " + value);
        assertTrue(violates(found, "amount.value", ">= 0"), found.violations().toString());
    }

    @Test
    @DisplayName("an exclusive bound is honoured exactly: minimum 0 vs exclusiveMinimum 0")
    void exclusiveBoundsAreExact() {
        String exclusive = paymentWith("\"value\":    {\"type\": \"number\", \"minimum\": 0}",
                "\"value\":    {\"type\": \"number\", \"exclusiveMinimum\": 0}");

        assertProven(response(PAYMENT_V1, exclusive, READS_STATUS_AND_VALUE));

        CounterexampleFound found = assertResponseCounterexample(exclusive, PAYMENT_V1, READS_STATUS_AND_VALUE);
        assertEquals(0, ((BigDecimal) at(found.witness(), "amount", "value")).signum());
    }

    @Test
    @DisplayName("multipleOf is exact: multiples of 10 are multiples of 5, not the reverse")
    void multipleOfIsExact() {
        String by5 = paymentWith("\"fee\":  {\"type\": \"integer\", \"minimum\": 0}",
                "\"fee\":  {\"type\": \"integer\", \"minimum\": 0, \"multipleOf\": 5}");
        String by10 = paymentWith("\"fee\":  {\"type\": \"integer\", \"minimum\": 0}",
                "\"fee\":  {\"type\": \"integer\", \"minimum\": 0, \"multipleOf\": 10}");

        assertProven(response(by5, by10, Usage.of("fee")));
        CounterexampleFound found = assertResponseCounterexample(by10, by5, Usage.of("fee"));
        BigDecimal fee = (BigDecimal) at(found.witness(), "fee");
        assertEquals(5, fee.remainder(BigDecimal.TEN).intValue());
    }

    @Test
    @DisplayName("decimal multipleOf (money in cents) is decided too")
    void decimalMultipleOf() {
        String cents = paymentWith("\"value\":    {\"type\": \"number\", \"minimum\": 0}",
                "\"value\":    {\"type\": \"number\", \"minimum\": 0, \"multipleOf\": 0.01}");
        String tenths = paymentWith("\"value\":    {\"type\": \"number\", \"minimum\": 0}",
                "\"value\":    {\"type\": \"number\", \"minimum\": 0, \"multipleOf\": 0.1}");

        assertProven(response(cents, tenths, READS_STATUS_AND_VALUE));
        CounterexampleFound found = assertResponseCounterexample(tenths, cents, READS_STATUS_AND_VALUE);
        BigDecimal value = (BigDecimal) at(found.witness(), "amount", "value");
        assertEquals(2, value.stripTrailingZeros().scale(), "expected a whole number of cents, got " + value);
    }

    @Test
    @DisplayName("a longer maximum length on a read string is a counterexample")
    void longerStringIsACounterexample() {
        String v2 = paymentWith("\"id\":     {\"type\": \"string\", \"maxLength\": 36}",
                "\"id\":     {\"type\": \"string\", \"maxLength\": 64}");

        CounterexampleFound found = assertResponseCounterexample(PAYMENT_V1, v2, Usage.of("id"));

        String id = (String) at(found.witness(), "id");
        assertTrue(id.length() > 36 && id.length() <= 64, "witness length " + id.length());
    }

    // ---------------------------------------------------------------- arrays

    private static final String ORDER_V1 = """
            {
              "type": "object",
              "required": ["lines"],
              "properties": {
                "lines": {
                  "type": "array",
                  "minItems": 1,
                  "items": {
                    "type": "object",
                    "required": ["sku", "state"],
                    "properties": {
                      "sku":   {"type": "string"},
                      "state": {"type": "string", "enum": ["OPEN", "SHIPPED"]}
                    }
                  }
                }
              }
            }
            """;

    @Test
    @DisplayName("an enum widened inside array elements is found through the representative element")
    void enumInsideArrayIsACounterexample() {
        String v2 = ORDER_V1.replace("[\"OPEN\", \"SHIPPED\"]", "[\"OPEN\", \"SHIPPED\", \"RETURNED\"]");

        CounterexampleFound found = assertResponseCounterexample(ORDER_V1, v2, Usage.of("lines[].state"));

        assertEquals("RETURNED", at(found.witness(), "lines", 0, "state"));
        assertEquals(1, ((List<?>) at(found.witness(), "lines")).size(), "witness should be minimal");
    }

    @Test
    @DisplayName("an array that may now be empty breaks a consumer that relied on minItems")
    void droppedMinItemsIsACounterexample() {
        String v2 = ORDER_V1.replace("\"minItems\": 1,", "");

        CounterexampleFound found = assertResponseCounterexample(ORDER_V1, v2, Usage.of("lines"));

        assertEquals(List.of(), at(found.witness(), "lines"));
        assertTrue(violates(found, "lines", "at least 1 items"), found.violations().toString());
    }

    // ---------------------------------------------------------------- composites, refs, opaque

    @Test
    @DisplayName("a new oneOf variant the consumer does not know is a counterexample")
    void newVariantIsACounterexample() {
        String v1 = """
                {"oneOf": [
                  {"type": "object", "required": ["card"],  "properties": {"card":  {"type": "string"}}, "additionalProperties": false},
                  {"type": "object", "required": ["iban"],  "properties": {"iban":  {"type": "string"}}, "additionalProperties": false}
                ]}
                """;
        String v2 = """
                {"oneOf": [
                  {"type": "object", "required": ["card"],  "properties": {"card":  {"type": "string"}}, "additionalProperties": false},
                  {"type": "object", "required": ["iban"],  "properties": {"iban":  {"type": "string"}}, "additionalProperties": false},
                  {"type": "object", "required": ["upi"],   "properties": {"upi":   {"type": "string"}}, "additionalProperties": false}
                ]}
                """;

        CounterexampleFound found = assertResponseCounterexample(v1, v2, Usage.allFields());

        assertTrue(Json.write(found.witness()).contains("upi"), Json.write(found.witness()));
    }

    @Test
    @DisplayName("the same pattern on both sides cancels out; a pattern only the consumer relied on does not")
    void opaquePatternsAreSound() {
        String patterned = paymentWith("\"id\":     {\"type\": \"string\", \"maxLength\": 36}",
                "\"id\":     {\"type\": \"string\", \"maxLength\": 36, \"pattern\": \"^pay_[a-z0-9]+$\"}");

        assertProven(response(patterned, patterned, Usage.of("id")));

        CompatibilityResult result = response(patterned, PAYMENT_V1, Usage.of("id"));
        CounterexampleFound found = assertInstanceOf(CounterexampleFound.class, result);
        assertFalse(found.exact(), "a witness that rests on an opaque predicate must be marked for replay");
        assertTrue(found.approximations().stream().anyMatch(a -> a.contains("pattern")), found.approximations().toString());
    }

    @Test
    @DisplayName("recursive references terminate and the truncation is reported")
    void recursiveSchemasTerminate() {
        String tree = """
                {
                  "components": {"schemas": {"Node": {
                    "type": "object",
                    "required": ["name"],
                    "properties": {
                      "name":     {"type": "string"},
                      "children": {"type": "array", "items": {"$ref": "#/components/schemas/Node"}}
                    }
                  }}},
                  "$ref": "#/components/schemas/Node"
                }
                """;

        Proven proven = assertProven(response(tree, tree, Usage.allFields()));

        assertTrue(proven.assumptions().stream().anyMatch(a -> a.startsWith("not checked")),
                "a bounded check must say where it stopped: " + proven.assumptions());
    }
}
