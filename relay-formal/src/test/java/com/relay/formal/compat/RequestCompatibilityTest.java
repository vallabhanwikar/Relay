package com.relay.formal.compat;

import com.relay.formal.compat.CompatibilityResult.CounterexampleFound;
import com.relay.formal.compat.CompatibilityResult.Unproven;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.relay.formal.compat.CompatTestSupport.assertProven;
import static com.relay.formal.compat.CompatTestSupport.assertRequestCounterexample;
import static com.relay.formal.compat.CompatTestSupport.at;
import static com.relay.formal.compat.CompatTestSupport.request;
import static com.relay.formal.compat.CompatTestSupport.violates;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Request direction: can the consumer, sending what it sent before, produce a body the provider
 * no longer accepts?
 */
class RequestCompatibilityTest {

    private static final String CREATE_PAYMENT_V1 = """
            {
              "type": "object",
              "required": ["amount", "currency"],
              "properties": {
                "amount":   {"type": "integer", "minimum": 1},
                "currency": {"type": "string", "enum": ["INR", "USD", "EUR"]},
                "memo":     {"type": "string", "maxLength": 140}
              }
            }
            """;

    private static final Usage SENDS_AMOUNT_CURRENCY = Usage.of("amount", "currency");

    @Test
    @DisplayName("a new required request field the consumer never sends is a counterexample")
    void newRequiredFieldIsACounterexample() {
        String v2 = CREATE_PAYMENT_V1
                .replace("\"required\": [\"amount\", \"currency\"]", "\"required\": [\"amount\", \"currency\", \"idempotencyKey\"]")
                .replace("\"memo\":", "\"idempotencyKey\": {\"type\": \"string\"}, \"memo\":");

        CounterexampleFound found = assertRequestCounterexample(CREATE_PAYMENT_V1, v2, SENDS_AMOUNT_CURRENCY);

        assertFalse(((Map<?, ?>) found.witness()).containsKey("idempotencyKey"));
        assertTrue(violates(found, "idempotencyKey", "must be present"), found.violations().toString());
    }

    @Test
    @DisplayName("a new optional request field is proven safe")
    void newOptionalFieldIsProven() {
        String v2 = CREATE_PAYMENT_V1.replace("\"memo\":", "\"idempotencyKey\": {\"type\": \"string\"}, \"memo\":");
        assertProven(request(CREATE_PAYMENT_V1, v2, SENDS_AMOUNT_CURRENCY));
    }

    @Test
    @DisplayName("a narrowed request enum is a counterexample carrying the dropped value")
    void narrowedEnumIsACounterexample() {
        String v2 = CREATE_PAYMENT_V1.replace("[\"INR\", \"USD\", \"EUR\"]", "[\"INR\", \"USD\"]");

        CounterexampleFound found = assertRequestCounterexample(CREATE_PAYMENT_V1, v2, SENDS_AMOUNT_CURRENCY);

        assertEquals("EUR", at(found.witness(), "currency"));
    }

    @Test
    @DisplayName("a tightened limit on a field the consumer does not send is proven safe")
    void tightenedUnsentFieldIsProven() {
        String v2 = CREATE_PAYMENT_V1.replace("\"maxLength\": 140", "\"maxLength\": 20");
        assertProven(request(CREATE_PAYMENT_V1, v2, SENDS_AMOUNT_CURRENCY));
    }

    @Test
    @DisplayName("the same tightened limit is a counterexample once the consumer sends the field")
    void tightenedSentFieldIsACounterexample() {
        String v2 = CREATE_PAYMENT_V1.replace("\"maxLength\": 140", "\"maxLength\": 20");

        CounterexampleFound found = assertRequestCounterexample(CREATE_PAYMENT_V1, v2,
                Usage.of("amount", "currency", "memo"));

        assertTrue(((String) at(found.witness(), "memo")).length() > 20);
    }

    @Test
    @DisplayName("declared usage that omits a required field is completed, never made vacuous")
    void usageOmittingRequiredFieldIsCompleted() {
        // The consumer declares it sends only "amount", but the old contract required "currency"
        // too; a consumer that worked must have sent it. Without completion the producing side
        // would be contradictory and every check would be "proven".
        String v2 = CREATE_PAYMENT_V1.replace("[\"INR\", \"USD\", \"EUR\"]", "[\"INR\"]");

        CounterexampleFound found = assertRequestCounterexample(CREATE_PAYMENT_V1, v2, Usage.of("amount"));

        assertTrue(violates(found, "currency", "must be one of [INR]"), found.violations().toString());
    }

    @Test
    @DisplayName("an unsatisfiable producing side is reported as vacuous, not proven")
    void vacuousCheckIsUnproven() {
        String impossible = """
                {"type": "object", "required": ["n"], "properties": {"n": {"type": "integer", "minimum": 5, "maximum": 1}}}
                """;

        CompatibilityResult result = request(impossible, CREATE_PAYMENT_V1, Usage.allFields());

        Unproven unproven = assertInstanceOf(Unproven.class, result);
        assertTrue(unproven.reason().startsWith("vacuous"), unproven.reason());
    }
}
