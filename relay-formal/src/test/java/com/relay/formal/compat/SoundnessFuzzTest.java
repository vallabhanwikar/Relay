package com.relay.formal.compat;

import com.relay.formal.compat.CompatibilityResult.CounterexampleFound;
import com.relay.formal.compat.CompatibilityResult.Proven;
import com.relay.formal.json.Json;
import com.relay.formal.schema.Schema;
import com.relay.formal.schema.Schema.AnySchema;
import com.relay.formal.schema.Schema.ArraySchema;
import com.relay.formal.schema.Schema.BooleanSchema;
import com.relay.formal.schema.Schema.NumberSchema;
import com.relay.formal.schema.Schema.ObjectSchema;
import com.relay.formal.schema.Schema.StringSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Differential soundness check: the solver against brute force.
 *
 * <p>Random small schema pairs (an old schema and a mutation of it) go through the prover with
 * usage "all fields". Then an independent oracle - {@link ConcreteValidator} over thousands of
 * random payloads - tries to contradict each verdict:
 * <ul>
 *   <li>PROVEN: no sampled payload may be valid under the new schema and invalid under the old.
 *       One such payload is an unsound proof, the single worst bug this module can have.</li>
 *   <li>exact COUNTEREXAMPLE: the witness must be valid under the new schema and invalid under
 *       the old. Anything else is a fabricated counterexample.</li>
 * </ul>
 * The seed is fixed so a failure reproduces; override it to explore further.
 */
class SoundnessFuzzTest {

    // Override for a longer hunt: mvn test -Drelay.fuzz.seed=7 -Drelay.fuzz.pairs=5000
    private static final long SEED = Long.getLong("relay.fuzz.seed", 20261008L);
    private static final int PAIRS = Integer.getInteger("relay.fuzz.pairs", 150);
    private static final int SAMPLES_PER_PROOF = 3000;

    private static final List<String> NAMES = List.of("a", "b", "c");
    private static final List<String> STRINGS = List.of("A", "B", "C", "", "AAAA");
    private static final List<BigDecimal> NUMBERS = List.of(
            new BigDecimal("-2"), new BigDecimal("-1"), BigDecimal.ZERO, new BigDecimal("0.5"), BigDecimal.ONE,
            new BigDecimal("2"), new BigDecimal("3"), new BigDecimal("4"), new BigDecimal("6"), BigDecimal.TEN);

    private final Random random = new Random(SEED);
    private final SchemaCompatibilityChecker checker = new SchemaCompatibilityChecker();

    @Test
    @DisplayName("no PROVEN verdict is contradicted and every exact counterexample is real")
    void solverAgreesWithBruteForce() {
        int proven = 0;
        int counterexamples = 0;
        for (int i = 0; i < PAIRS; i++) {
            Schema oldSchema = randomSchema(0);
            Schema newSchema = mutate(oldSchema, 0);
            CompatibilityResult result = checker.check(
                    CompatibilityQuery.response(oldSchema, newSchema, Usage.allFields()));

            if (result instanceof Proven) {
                proven++;
                for (int s = 0; s < SAMPLES_PER_PROOF; s++) {
                    Object payload = randomValue(newSchema, 0);
                    if (ConcreteValidator.valid(newSchema, payload) && !ConcreteValidator.valid(oldSchema, payload)) {
                        fail("UNSOUND PROOF\n old: " + oldSchema + "\n new: " + newSchema
                                + "\n payload: " + Json.write(payload));
                    }
                }
            } else if (result instanceof CounterexampleFound found) {
                counterexamples++;
                if (found.exact()) {
                    assertTrue(ConcreteValidator.valid(newSchema, found.witness()),
                            "witness not valid under new\n new: " + newSchema + "\n witness: " + found.witnessJson());
                    assertTrue(!ConcreteValidator.valid(oldSchema, found.witness()),
                            "witness accepted by old, so it is not a counterexample\n old: " + oldSchema
                                    + "\n witness: " + found.witnessJson() + "\n violations: " + found.violations());
                }
            } else if (result instanceof CompatibilityResult.Unproven u && u.reason().startsWith("vacuous")) {
                // The mutation produced a schema nothing satisfies (minItems 2, maxItems 1). The
                // checker must say so rather than "prove" compatibility; double-check the claim.
                for (int s = 0; s < SAMPLES_PER_PROOF; s++) {
                    Object payload = randomValue(newSchema, 0);
                    assertTrue(!ConcreteValidator.valid(newSchema, payload),
                            "reported vacuous, but this payload is valid under new: " + Json.write(payload));
                }
            } else {
                fail("unexpected verdict " + result + "\n old: " + oldSchema + "\n new: " + newSchema);
            }
        }
        assertTrue(proven > 10 && counterexamples > 10,
                "the generator should exercise both outcomes; got " + proven + " proven, " + counterexamples + " counterexamples");
    }

    // ------------------------------------------------------------------ schema generation

    private Schema randomSchema(int depth) {
        int pick = random.nextInt(depth >= 2 ? 4 : 6);
        boolean nullable = random.nextInt(4) == 0;
        return switch (pick) {
            case 0 -> new StringSchema(randomSubset(STRINGS.subList(0, 3)), null,
                    random.nextBoolean() ? null : 1 + random.nextInt(3), null, null, nullable);
            case 1 -> {
                Integer lo = random.nextBoolean() ? null : random.nextInt(4) - 1;
                Integer hi = random.nextBoolean() ? null : 1 + random.nextInt(6);
                yield new NumberSchema(random.nextBoolean(), dec(lo), random.nextInt(3) == 0, dec(hi),
                        random.nextInt(3) == 0, random.nextInt(3) == 0 ? BigDecimal.valueOf(1 + random.nextInt(3)) : null,
                        List.of(), nullable);
            }
            case 2 -> new BooleanSchema(random.nextInt(3) == 0 ? List.of(true) : List.of(), nullable);
            case 3 -> new AnySchema();
            case 4 -> {
                Map<String, Schema> props = new LinkedHashMap<>();
                Set<String> required = new LinkedHashSet<>();
                for (String name : NAMES) {
                    if (random.nextInt(3) > 0) {
                        props.put(name, randomSchema(depth + 1));
                        if (random.nextBoolean()) {
                            required.add(name);
                        }
                    }
                }
                yield new ObjectSchema(props, required, new AnySchema(), nullable);
            }
            default -> new ArraySchema(randomSchema(depth + 1),
                    random.nextBoolean() ? null : random.nextInt(2),
                    random.nextBoolean() ? null : 1 + random.nextInt(2), nullable);
        };
    }

    /** A small random edit, applied at the root or somewhere below it. */
    private Schema mutate(Schema schema, int depth) {
        if (schema instanceof ObjectSchema o && !o.properties().isEmpty() && random.nextBoolean()) {
            Map<String, Schema> props = new LinkedHashMap<>(o.properties());
            String victim = new ArrayList<>(props.keySet()).get(random.nextInt(props.size()));
            Set<String> required = new LinkedHashSet<>(o.required());
            switch (random.nextInt(4)) {
                case 0 -> props.put(victim, mutate(props.get(victim), depth + 1));
                case 1 -> {
                    if (!required.remove(victim)) {
                        required.add(victim);
                    }
                }
                case 2 -> {
                    props.remove(victim);
                    required.remove(victim);
                }
                default -> props.put("z", randomSchema(depth + 1));
            }
            return new ObjectSchema(props, required, o.additionalProperties(), o.nullable());
        }
        if (schema instanceof ArraySchema a && random.nextBoolean()) {
            return new ArraySchema(mutate(a.items(), depth + 1), a.minItems(), a.maxItems(), a.nullable());
        }
        if (random.nextInt(4) == 0) {
            return randomSchema(depth);
        }
        return switch (schema) {
            case StringSchema s -> new StringSchema(randomSubset(STRINGS.subList(0, 3)), s.minLength(),
                    random.nextBoolean() ? s.maxLength() : Integer.valueOf(1 + random.nextInt(4)), null, null, random.nextInt(3) == 0);
            case NumberSchema n -> new NumberSchema(random.nextBoolean(), n.minimum(), !n.exclusiveMinimum(),
                    random.nextBoolean() ? n.maximum() : dec(random.nextInt(8) - 2), n.exclusiveMaximum(),
                    random.nextBoolean() ? null : BigDecimal.valueOf(1 + random.nextInt(4)), List.of(), random.nextBoolean());
            case BooleanSchema b -> new BooleanSchema(b.enumValues().isEmpty() ? List.of(false) : List.of(), !b.nullable());
            case ArraySchema a -> new ArraySchema(a.items(), random.nextInt(3), random.nextBoolean() ? null : 1 + random.nextInt(2), a.nullable());
            default -> randomSchema(depth);
        };
    }

    // ------------------------------------------------------------------ payload sampling

    /** A random value, biased towards the shape of {@code schema} so that many samples are valid. */
    private Object randomValue(Schema shape, int depth) {
        if (random.nextInt(8) == 0 || depth > 3) {
            return anyScalar();
        }
        return switch (shape) {
            case ObjectSchema o -> {
                Map<String, Object> map = new LinkedHashMap<>();
                for (String name : List.of("a", "b", "c", "z")) {
                    Schema child = o.properties().getOrDefault(name, new AnySchema());
                    if (random.nextInt(4) > 0) {
                        map.put(name, randomValue(child, depth + 1));
                    }
                }
                yield map;
            }
            case ArraySchema a -> {
                List<Object> list = new ArrayList<>();
                int length = random.nextInt(4);
                Object first = randomValue(a.items(), depth + 1);
                for (int i = 0; i < length; i++) {
                    list.add(random.nextBoolean() ? first : randomValue(a.items(), depth + 1));
                }
                yield list;
            }
            case AnySchema any -> random.nextBoolean() ? anyScalar()
                    : randomValue(random.nextBoolean() ? new ObjectSchema(Map.of(), Set.of(), any, false)
                    : new ArraySchema(any, null, null, false), depth + 1);
            default -> anyScalar();
        };
    }

    private Object anyScalar() {
        return switch (random.nextInt(5)) {
            case 0 -> Json.NULL;
            case 1 -> random.nextBoolean();
            case 2, 3 -> NUMBERS.get(random.nextInt(NUMBERS.size()));
            default -> STRINGS.get(random.nextInt(STRINGS.size()));
        };
    }

    private <T> List<T> randomSubset(List<T> values) {
        if (random.nextBoolean()) {
            return List.of();
        }
        List<T> subset = new ArrayList<>();
        values.forEach(v -> {
            if (random.nextBoolean()) {
                subset.add(v);
            }
        });
        return subset.isEmpty() ? List.of(values.get(0)) : subset;
    }

    private static BigDecimal dec(Integer value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
