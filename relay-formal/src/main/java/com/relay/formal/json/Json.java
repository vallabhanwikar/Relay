package com.relay.formal.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A deliberately small JSON reader and writer.
 *
 * <p>The formal module depends on Z3 and nothing else, so a proof can be re-run from its inputs
 * with one jar on the classpath (design v3, UC10). JSON values are represented as plain Java:
 * {@link Map} (insertion-ordered), {@link List}, {@link String}, {@link BigDecimal},
 * {@link Boolean} and {@link #NULL}. Numbers are always {@link BigDecimal} so that a schema's
 * {@code maximum: 0.1} reaches the solver exactly, not as a binary approximation.
 */
public final class Json {

    /** JSON {@code null}. Distinct from Java {@code null}, which means "absent". */
    public static final Object NULL = new Object() {
        @Override
        public String toString() {
            return "null";
        }
    };

    private Json() {
    }

    public static Object parse(String text) {
        Parser parser = new Parser(text);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw parser.error("unexpected trailing content");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new JsonException("expected a JSON object at the top level");
        }
        return (Map<String, Object>) value;
    }

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out, -1, 0);
        return out.toString();
    }

    public static String writePretty(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out, 2, 0);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out, int indent, int depth) {
        if (value == null || value == NULL) {
            out.append("null");
        } else if (value instanceof String s) {
            writeString(s, out);
        } else if (value instanceof Boolean b) {
            out.append(b);
        } else if (value instanceof BigDecimal d) {
            out.append(d.toString()); // exact: new BigDecimal(d.toString()).equals(d)
        } else if (value instanceof Number n) {
            out.append(n);
        } else if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                out.append("{}");
                return;
            }
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                newline(out, indent, depth + 1);
                writeString(String.valueOf(entry.getKey()), out);
                out.append(indent >= 0 ? ": " : ":");
                write(entry.getValue(), out, indent, depth + 1);
            }
            newline(out, indent, depth);
            out.append('}');
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                out.append("[]");
                return;
            }
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                newline(out, indent, depth + 1);
                write(list.get(i), out, indent, depth + 1);
            }
            newline(out, indent, depth);
            out.append(']');
        } else {
            throw new JsonException("cannot write " + value.getClass().getName() + " as JSON");
        }
    }

    private static void newline(StringBuilder out, int indent, int depth) {
        if (indent < 0) {
            return;
        }
        out.append('\n');
        out.append(" ".repeat(indent * depth));
    }

    private static void writeString(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    /** Thrown for malformed input. Carries the character offset where parsing stopped. */
    public static final class JsonException extends RuntimeException {
        public JsonException(String message) {
            super(message);
        }
    }

    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return pos >= text.length();
        }

        JsonException error(String message) {
            return new JsonException(message + " at offset " + pos);
        }

        void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        Object readValue() {
            if (atEnd()) {
                throw error("unexpected end of input");
            }
            char c = text.charAt(pos);
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", NULL);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield readNumber();
                    }
                    throw error("unexpected character '" + c + "'");
                }
            };
        }

        private Object readLiteral(String word, Object value) {
            if (!text.startsWith(word, pos)) {
                throw error("expected " + word);
            }
            pos += word.length();
            return value;
        }

        private Map<String, Object> readObject() {
            Map<String, Object> result = new LinkedHashMap<>();
            pos++;
            skipWhitespace();
            if (!atEnd() && text.charAt(pos) == '}') {
                pos++;
                return Collections.unmodifiableMap(result);
            }
            while (true) {
                skipWhitespace();
                if (atEnd() || text.charAt(pos) != '"') {
                    throw error("expected a property name");
                }
                String key = readString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                if (result.containsKey(key)) {
                    throw error("duplicate property '" + key + "'");
                }
                result.put(key, readValue());
                skipWhitespace();
                if (atEnd()) {
                    throw error("unterminated object");
                }
                char c = text.charAt(pos++);
                if (c == '}') {
                    return Collections.unmodifiableMap(result);
                }
                if (c != ',') {
                    throw error("expected ',' or '}'");
                }
            }
        }

        private List<Object> readArray() {
            List<Object> result = new ArrayList<>();
            pos++;
            skipWhitespace();
            if (!atEnd() && text.charAt(pos) == ']') {
                pos++;
                return Collections.unmodifiableList(result);
            }
            while (true) {
                skipWhitespace();
                result.add(readValue());
                skipWhitespace();
                if (atEnd()) {
                    throw error("unterminated array");
                }
                char c = text.charAt(pos++);
                if (c == ']') {
                    return Collections.unmodifiableList(result);
                }
                if (c != ',') {
                    throw error("expected ',' or ']'");
                }
            }
        }

        private void expect(char c) {
            if (atEnd() || text.charAt(pos) != c) {
                throw error("expected '" + c + "'");
            }
            pos++;
        }

        private String readString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw error("unterminated string");
                }
                char c = text.charAt(pos++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (atEnd()) {
                    throw error("unterminated escape");
                }
                char e = text.charAt(pos++);
                switch (e) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (pos + 4 > text.length()) {
                            throw error("truncated unicode escape");
                        }
                        out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw error("invalid escape '\\" + e + "'");
                }
            }
        }

        private BigDecimal readNumber() {
            int start = pos;
            if (text.charAt(pos) == '-') {
                pos++;
            }
            while (!atEnd() && "0123456789.eE+-".indexOf(text.charAt(pos)) >= 0) {
                pos++;
            }
            try {
                return new BigDecimal(text.substring(start, pos));
            } catch (NumberFormatException e) {
                pos = start;
                throw error("malformed number");
            }
        }
    }
}
