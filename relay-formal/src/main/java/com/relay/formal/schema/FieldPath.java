package com.relay.formal.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * A location inside a JSON payload: a list of property names, with {@link #ITEMS} standing for
 * "every element of this array".
 *
 * <p>The text form is the one people write in a usage declaration: {@code status},
 * {@code amount.value}, {@code lines[].sku}, and the empty string for the payload root. The
 * text form cannot express property names containing {@code .} or {@code []}; the segment form
 * can, so code that builds paths programmatically should use {@link #child(String)}.
 */
public record FieldPath(List<String> segments) implements Comparable<FieldPath> {

    /** The segment that denotes "every element of the array at the parent path". */
    public static final String ITEMS = "[]";

    public static final FieldPath ROOT = new FieldPath(List.of());

    public FieldPath {
        segments = List.copyOf(segments);
    }

    public static FieldPath parse(String text) {
        String trimmed = text.strip();
        if (trimmed.isEmpty() || trimmed.equals("$")) {
            return ROOT;
        }
        List<String> segments = new ArrayList<>();
        for (String part : trimmed.split("\\.", -1)) {
            String name = part;
            int arrays = 0;
            while (name.endsWith(ITEMS)) {
                name = name.substring(0, name.length() - ITEMS.length());
                arrays++;
            }
            if (!name.isEmpty()) {
                segments.add(name);
            } else if (arrays == 0) {
                throw new IllegalArgumentException("empty segment in path '" + text + "'");
            }
            for (int i = 0; i < arrays; i++) {
                segments.add(ITEMS);
            }
        }
        return new FieldPath(segments);
    }

    public boolean isRoot() {
        return segments.isEmpty();
    }

    public FieldPath child(String segment) {
        List<String> next = new ArrayList<>(segments);
        next.add(segment);
        return new FieldPath(next);
    }

    public FieldPath items() {
        return child(ITEMS);
    }

    public FieldPath parent() {
        if (isRoot()) {
            throw new IllegalStateException("the root has no parent");
        }
        return new FieldPath(segments.subList(0, segments.size() - 1));
    }

    public String last() {
        if (isRoot()) {
            throw new IllegalStateException("the root has no name");
        }
        return segments.get(segments.size() - 1);
    }

    /** True when this path addresses array elements rather than a named property. */
    public boolean isItems() {
        return !isRoot() && last().equals(ITEMS);
    }

    public int depth() {
        return segments.size();
    }

    /** This path and every ancestor up to and including the root. */
    public List<FieldPath> selfAndAncestors() {
        List<FieldPath> result = new ArrayList<>();
        for (int i = segments.size(); i >= 0; i--) {
            result.add(new FieldPath(segments.subList(0, i)));
        }
        return result;
    }

    @Override
    public int compareTo(FieldPath other) {
        int byDepth = Integer.compare(depth(), other.depth());
        return byDepth != 0 ? byDepth : toString().compareTo(other.toString());
    }

    @Override
    public String toString() {
        if (isRoot()) {
            return "$";
        }
        StringBuilder out = new StringBuilder();
        for (String segment : segments) {
            if (segment.equals(ITEMS)) {
                out.append(ITEMS);
            } else {
                if (!out.isEmpty()) {
                    out.append('.');
                }
                out.append(segment);
            }
        }
        return out.toString();
    }
}
