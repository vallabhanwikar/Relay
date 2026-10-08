package com.relay.formal.cli;

import com.relay.formal.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The weekend-1 demo from the design doc, end to end on the example OpenAPI documents in
 * {@code relay-formal/examples}: payments v2 adds a status, drops a field and adds two more.
 */
class SchemaCompatCliTest {

    private static final String V1 = example("payments-api-v1.json");
    private static final String V2 = example("payments-api-v2.json");

    private record Outcome(int exit, Map<String, Object> report, String err) {
    }

    private static Outcome run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = SchemaCompatCli.run(args,
                new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        String text = out.toString(StandardCharsets.UTF_8);
        return new Outcome(exit, text.isBlank() ? Map.of() : Json.parseObject(text), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("v2's new status is a counterexample for a consumer that switches on status")
    void newStatusBreaksStrictSwitch() {
        Outcome outcome = run("--old", V1, "--new", V2, "--operation", "GET /payments/{id}",
                "--uses", "status,amount.value");

        assertEquals(SchemaCompatCli.COUNTEREXAMPLE, outcome.exit(), outcome.err());
        assertEquals("COUNTEREXAMPLE", outcome.report().get("verdict"));
        Map<?, ?> witness = (Map<?, ?>) outcome.report().get("witness");
        assertEquals("PARTIALLY_REFUNDED", witness.get("status"));
    }

    @Test
    @DisplayName("with a default branch on status, the whole v2 change is proven safe for this consumer")
    void tolerantConsumerIsProven() {
        Outcome outcome = run("--old", V1, "--new", V2, "--operation", "GET /payments/{id}",
                "--uses", "amount.value", "--tolerate", "status=UNKNOWN_ENUM_SAFE");

        assertEquals(SchemaCompatCli.PROVEN, outcome.exit(), outcome.report().toString());
        assertEquals("E6b", outcome.report().get("level"));
        assertTrue(((List<?>) outcome.report().get("assumptions")).stream()
                .anyMatch(a -> a.toString().contains("UNKNOWN_ENUM_SAFE")));
    }

    @Test
    @DisplayName("a consumer reading the removed legacyRef field gets a payload without it")
    void removedFieldBreaksItsReader() {
        Outcome outcome = run("--old", V1, "--new", V2, "--pointer", "/components/schemas/Payment",
                "--uses", "legacyRef");

        assertEquals(SchemaCompatCli.COUNTEREXAMPLE, outcome.exit());
        assertTrue(Json.write(outcome.report().get("violations")).contains("legacyRef"));
    }

    @Test
    @DisplayName("the request body change (optional metadata) is proven safe")
    void requestChangeIsProven() {
        Outcome outcome = run("--old", V1, "--new", V2, "--operation", "POST /payments",
                "--direction", "request");

        assertEquals(SchemaCompatCli.PROVEN, outcome.exit(), outcome.report().toString());
    }

    @Test
    @DisplayName("bad arguments exit with the error status and a usage message")
    void badArguments() {
        Outcome outcome = run("--old", V1);
        assertEquals(SchemaCompatCli.ERROR, outcome.exit());
        assertTrue(outcome.err().contains("missing --new"), outcome.err());
    }

    private static String example(String name) {
        for (Path candidate : List.of(Path.of("examples", name), Path.of("relay-formal", "examples", name))) {
            if (Files.exists(candidate)) {
                return candidate.toString();
            }
        }
        throw new IllegalStateException("example not found: " + name);
    }
}
