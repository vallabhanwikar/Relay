package com.relay.formal.schema;

import com.relay.formal.schema.Schema.ArraySchema;
import com.relay.formal.schema.Schema.CompositeSchema;
import com.relay.formal.schema.Schema.ObjectSchema;
import com.relay.formal.schema.Schema.TruncatedSchema;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/** Structural queries over a {@link Schema}: what is declared where, and how deep it goes. */
public final class SchemaNavigator {

    private SchemaNavigator() {
    }

    /**
     * Every schema that can govern {@code path}, following all alternatives of composites.
     * Empty when the path is not declared anywhere.
     */
    public static List<Schema> at(Schema root, FieldPath path) {
        List<Schema> current = List.of(root);
        for (String segment : path.segments()) {
            List<Schema> next = new ArrayList<>();
            for (Schema schema : current) {
                flatten(schema, s -> {
                    if (s instanceof ObjectSchema obj && !segment.equals(FieldPath.ITEMS)) {
                        Schema child = obj.properties().get(segment);
                        if (child != null) {
                            next.add(child);
                        }
                    } else if (s instanceof ArraySchema arr && segment.equals(FieldPath.ITEMS)) {
                        next.add(arr.items());
                    }
                });
            }
            current = next;
        }
        return current;
    }

    /** Property names declared by any object alternative of {@code schema}. */
    public static Set<String> declaredNames(Schema schema) {
        Set<String> names = new LinkedHashSet<>();
        flatten(schema, s -> {
            if (s instanceof ObjectSchema obj) {
                names.addAll(obj.properties().keySet());
            }
        });
        return names;
    }

    /** True when some alternative of {@code schema} is an array. */
    public static boolean mayBeArray(Schema schema) {
        boolean[] found = {false};
        flatten(schema, s -> found[0] |= s instanceof ArraySchema);
        return found[0];
    }

    /**
     * Every path at or below {@code base} that {@code root} declares, down to {@code maxDepth}
     * segments. Paths that exist below the bound are reported to {@code truncated}.
     */
    public static Set<FieldPath> enumerate(Schema root, FieldPath base, int maxDepth, Set<String> truncated) {
        Set<FieldPath> result = new TreeSet<>();
        result.add(base);
        for (Schema schema : at(root, base)) {
            walk(schema, base, maxDepth, result, truncated);
        }
        return result;
    }

    private static void walk(Schema schema, FieldPath path, int maxDepth, Set<FieldPath> out, Set<String> truncated) {
        flatten(schema, s -> {
            if (s instanceof TruncatedSchema t) {
                truncated.add(path + ": " + t.reason());
                return;
            }
            List<FieldPath> children = new ArrayList<>();
            List<Schema> childSchemas = new ArrayList<>();
            if (s instanceof ObjectSchema obj) {
                obj.properties().forEach((name, child) -> {
                    children.add(path.child(name));
                    childSchemas.add(child);
                });
            } else if (s instanceof ArraySchema arr) {
                children.add(path.items());
                childSchemas.add(arr.items());
            }
            for (int i = 0; i < children.size(); i++) {
                FieldPath child = children.get(i);
                if (child.depth() > maxDepth) {
                    truncated.add("fields below " + path + " (deeper than " + maxDepth + " levels)");
                    continue;
                }
                if (out.add(child)) {
                    walk(childSchemas.get(i), child, maxDepth, out, truncated);
                }
            }
        });
    }

    /**
     * {@code paths} plus everything {@code root} requires beneath them: required properties of
     * objects, and the elements of arrays that must be non-empty. Used so that a witness is a
     * complete, valid document rather than only the fields the consumer reads.
     */
    public static Set<FieldPath> withRequired(Schema root, Set<FieldPath> paths, int maxDepth) {
        Set<FieldPath> result = new TreeSet<>(paths);
        java.util.ArrayDeque<FieldPath> work = new java.util.ArrayDeque<>(paths);
        while (!work.isEmpty()) {
            FieldPath path = work.pop();
            List<FieldPath> required = new ArrayList<>();
            for (Schema schema : at(root, path)) {
                flatten(schema, s -> {
                    if (s instanceof ObjectSchema obj) {
                        obj.required().forEach(name -> required.add(path.child(name)));
                    } else if (s instanceof ArraySchema arr && arr.minItems() != null && arr.minItems() > 0) {
                        required.add(path.items());
                    }
                });
            }
            for (FieldPath child : required) {
                if (child.depth() <= maxDepth && result.add(child)) {
                    work.push(child);
                }
            }
        }
        return result;
    }

    /**
     * The smallest power of ten that makes every {@code multipleOf} in the given schemas a whole
     * number: 1 for {@code multipleOf: 5}, 100 for {@code multipleOf: 0.01}.
     */
    public static java.math.BigDecimal multipleOfScale(Schema... schemas) {
        int[] scale = {0};
        for (Schema schema : schemas) {
            visitAll(schema, s -> {
                if (s instanceof Schema.NumberSchema n && n.multipleOf() != null) {
                    scale[0] = Math.max(scale[0], n.multipleOf().stripTrailingZeros().scale());
                }
            });
        }
        return java.math.BigDecimal.ONE.scaleByPowerOfTen(scale[0]);
    }

    /** The largest {@code minItems}/{@code maxItems} constant anywhere in {@code schema}. */
    public static int largestArrayLengthConstant(Schema schema) {
        int[] max = {0};
        visitAll(schema, s -> {
            if (s instanceof ArraySchema arr) {
                if (arr.minItems() != null) {
                    max[0] = Math.max(max[0], arr.minItems());
                }
                if (arr.maxItems() != null) {
                    max[0] = Math.max(max[0], arr.maxItems());
                }
            }
        });
        return max[0];
    }

    /** Applies {@code action} to {@code schema} and, through composites, to each alternative. */
    public static void flatten(Schema schema, Consumer<Schema> action) {
        if (schema instanceof CompositeSchema composite) {
            composite.options().forEach(option -> flatten(option, action));
        } else {
            action.accept(schema);
        }
    }

    private static void visitAll(Schema schema, Consumer<Schema> action) {
        action.accept(schema);
        switch (schema) {
            case CompositeSchema c -> c.options().forEach(o -> visitAll(o, action));
            case ObjectSchema o -> {
                o.properties().values().forEach(p -> visitAll(p, action));
                visitAll(o.additionalProperties(), action);
            }
            case ArraySchema a -> visitAll(a.items(), action);
            default -> {
            }
        }
    }
}
