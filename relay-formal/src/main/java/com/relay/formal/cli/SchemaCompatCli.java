package com.relay.formal.cli;

import com.relay.formal.compat.CompatibilityQuery;
import com.relay.formal.compat.CompatibilityQuery.Direction;
import com.relay.formal.compat.CompatibilityResult;
import com.relay.formal.compat.CompatibilityResult.CounterexampleFound;
import com.relay.formal.compat.CompatibilityResult.Failed;
import com.relay.formal.compat.CompatibilityResult.Proven;
import com.relay.formal.compat.CompatibilityResult.Unproven;
import com.relay.formal.compat.SchemaCompatibilityChecker;
import com.relay.formal.compat.Usage;
import com.relay.formal.json.Json;
import com.relay.formal.schema.OpenApiLocator;
import com.relay.formal.schema.SchemaReader;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Command-line entry point: compare two versions of an API contract for one consumer.
 *
 * <pre>
 * java -jar relay-formal.jar --old payments-v1.json --new payments-v2.json \
 *      --operation "GET /payments/{id}" --status 200 --uses status,amount.value
 * </pre>
 *
 * Prints a JSON verdict. Exit status: 0 proven, 1 counterexample, 2 unproven, 3 error. Nothing
 * here is required by Relay itself; it exists so a verdict can be reproduced from a shell.
 */
public final class SchemaCompatCli {

    public static final int PROVEN = 0;
    public static final int COUNTEREXAMPLE = 1;
    public static final int UNPROVEN = 2;
    public static final int ERROR = 3;

    private static final String USAGE = """
            usage: relay-formal --old FILE --new FILE (--pointer PTR | --operation "VERB /path" [--status 200])
                                [--direction response|request] [--uses p1,p2,...]
                                [--tolerate path=NULL_SAFE|UNKNOWN_ENUM_SAFE ...] [--strict-mapper]
                                [--timeout-seconds N] [--max-depth N]
              --pointer      JSON pointer to the schema in both files, e.g. /components/schemas/Payment
              --operation    an OpenAPI operation; uses its application/json body or response schema
              --uses         fields the consumer reads (response) or sends (request); default: all
              --tolerate     a consumer tolerance at a path; repeatable
              --strict-mapper the consumer fails on unknown properties
            """;

    private SchemaCompatCli() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        Map<String, List<String>> options;
        try {
            options = parse(args);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            err.print(USAGE);
            return ERROR;
        }
        try {
            CompatibilityQuery query = buildQuery(options);
            CompatibilityResult result = new SchemaCompatibilityChecker().check(query);
            out.println(Json.writePretty(report(query, result)));
            return switch (result) {
                case Proven p -> PROVEN;
                case CounterexampleFound c -> COUNTEREXAMPLE;
                case Unproven u -> UNPROVEN;
                case Failed f -> ERROR;
            };
        } catch (IOException | RuntimeException e) {
            err.println("error: " + e.getMessage());
            return ERROR;
        }
    }

    private static CompatibilityQuery buildQuery(Map<String, List<String>> options) throws IOException {
        Direction direction = Direction.valueOf(single(options, "direction", "response").toUpperCase());
        Map<String, Object> oldDoc = Json.parseObject(Files.readString(Path.of(required(options, "old"))));
        Map<String, Object> newDoc = Json.parseObject(Files.readString(Path.of(required(options, "new"))));
        int maxDepth = Integer.parseInt(single(options, "max-depth", "6"));

        String oldPointer;
        String newPointer;
        if (options.containsKey("pointer")) {
            oldPointer = newPointer = single(options, "pointer", "");
        } else {
            String operation = required(options, "operation");
            String status = single(options, "status", "200");
            oldPointer = direction == Direction.RESPONSE
                    ? OpenApiLocator.responseSchema(oldDoc, operation, status)
                    : OpenApiLocator.requestSchema(oldDoc, operation);
            newPointer = direction == Direction.RESPONSE
                    ? OpenApiLocator.responseSchema(newDoc, operation, status)
                    : OpenApiLocator.requestSchema(newDoc, operation);
        }
        SchemaReader.Result oldSchema = SchemaReader.read(oldDoc, oldPointer, maxDepth + 2);
        SchemaReader.Result newSchema = SchemaReader.read(newDoc, newPointer, maxDepth + 2);

        Usage.Builder usage = Usage.builder();
        boolean declared = false;
        for (String list : options.getOrDefault("uses", List.of())) {
            for (String path : list.split(",")) {
                if (!path.isBlank()) {
                    usage.uses(path.strip());
                    declared = true;
                }
            }
        }
        for (String tolerance : options.getOrDefault("tolerate", List.of())) {
            String[] kv = tolerance.split("=", 2);
            if (kv.length != 2) {
                throw new IllegalArgumentException("--tolerate expects path=TOLERANCE, got " + tolerance);
            }
            usage.uses(kv[0].strip(), Usage.Tolerance.valueOf(kv[1].strip().toUpperCase()));
            declared = true;
        }

        Set<String> ignored = new LinkedHashSet<>(oldSchema.ignoredKeywords());
        ignored.addAll(newSchema.ignoredKeywords());
        CompatibilityQuery base = direction == Direction.RESPONSE
                ? CompatibilityQuery.response(oldSchema.schema(), newSchema.schema(), declared ? usage.build() : Usage.allFields())
                : CompatibilityQuery.request(oldSchema.schema(), newSchema.schema(), declared ? usage.build() : Usage.allFields());
        return base
                .withRejectsUnknownProperties(options.containsKey("strict-mapper"))
                .withTimeout(Duration.ofSeconds(Long.parseLong(single(options, "timeout-seconds", "30"))))
                .withMaxDepth(maxDepth)
                .withIgnoredKeywords(ignored);
    }

    /** The verdict as JSON, in the shape the evidence package stores (design v3). */
    public static Map<String, Object> report(CompatibilityQuery query, CompatibilityResult result) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("verdict", switch (result) {
            case Proven p -> "PROVEN";
            case CounterexampleFound c -> "COUNTEREXAMPLE";
            case Unproven u -> "UNPROVEN";
            case Failed f -> "ERROR";
        });
        if (result instanceof Proven) {
            report.put("level", "E6b");
        }
        report.put("direction", query.direction().name());
        report.put("solver", ordered("name", result.solver().name(), "version", result.solver().version()));
        report.put("elapsedMs", new java.math.BigDecimal(result.elapsed().toMillis()));
        switch (result) {
            case CounterexampleFound c -> {
                report.put("witness", c.witness());
                report.put("violations", c.violations().stream()
                        .map(v -> ordered("path", v.path().toString(), "expectation", v.expectation()))
                        .toList());
                report.put("exact", c.exact());
                report.put("approximations", c.approximations());
            }
            case Unproven u -> report.put("reason", u.reason());
            case Failed f -> report.put("error", f.error());
            case Proven p -> {
            }
        }
        report.put("assumptions", result.assumptions());
        return report;
    }

    private static Map<String, Object> ordered(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }

    private static Map<String, List<String>> parse(String[] args) {
        Map<String, List<String>> options = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("unexpected argument: " + arg);
            }
            String name = arg.substring(2);
            if (name.equals("strict-mapper")) {
                options.computeIfAbsent(name, k -> new java.util.ArrayList<>()).add("true");
                continue;
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("missing value for " + arg);
            }
            options.computeIfAbsent(name, k -> new java.util.ArrayList<>()).add(args[++i]);
        }
        return options;
    }

    private static String required(Map<String, List<String>> options, String name) {
        List<String> values = options.get(name);
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("missing --" + name);
        }
        return values.get(values.size() - 1);
    }

    private static String single(Map<String, List<String>> options, String name, String fallback) {
        List<String> values = options.get(name);
        return values == null || values.isEmpty() ? fallback : values.get(values.size() - 1);
    }
}
