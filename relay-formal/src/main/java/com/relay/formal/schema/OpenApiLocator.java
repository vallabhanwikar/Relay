package com.relay.formal.schema;

import java.util.Locale;
import java.util.Map;

/**
 * Finds the JSON pointer of an operation's request or response body schema inside an OpenAPI
 * document, so callers can say "GET /payments/{id}, 200" instead of spelling the pointer.
 */
public final class OpenApiLocator {

    private static final String JSON = "application/json";

    private OpenApiLocator() {
    }

    /** Pointer to the {@code application/json} schema of one response, e.g. status {@code 200}. */
    public static String responseSchema(Map<String, Object> document, String operation, String status) {
        String[] parts = split(operation);
        String base = "/paths/" + escape(parts[1]) + "/" + parts[0] + "/responses/";
        Map<?, ?> responses = (Map<?, ?>) navigate(document, "/paths/" + escape(parts[1]) + "/" + parts[0] + "/responses");
        String key = responses.containsKey(status) ? status
                : responses.containsKey(status.charAt(0) + "XX") ? status.charAt(0) + "XX"
                : "default";
        return requireSchema(document, base + escape(key) + "/content/" + escape(JSON) + "/schema", operation);
    }

    /** Pointer to the {@code application/json} schema of the operation's request body. */
    public static String requestSchema(Map<String, Object> document, String operation) {
        String[] parts = split(operation);
        return requireSchema(document,
                "/paths/" + escape(parts[1]) + "/" + parts[0] + "/requestBody/content/" + escape(JSON) + "/schema",
                operation);
    }

    private static String requireSchema(Map<String, Object> document, String pointer, String operation) {
        navigate(document, pointer);
        return pointer;
    }

    /** "GET /payments/{id}" into {"get", "/payments/{id}"}. */
    private static String[] split(String operation) {
        String[] parts = operation.strip().split("\\s+", 2);
        if (parts.length != 2 || !parts[1].startsWith("/")) {
            throw new SchemaReader.SchemaException("operation must look like 'GET /payments/{id}', got '" + operation + "'");
        }
        return new String[]{parts[0].toLowerCase(Locale.ROOT), parts[1]};
    }

    private static String escape(String token) {
        return token.replace("~", "~0").replace("/", "~1");
    }

    private static Object navigate(Map<String, Object> document, String pointer) {
        Object node = document;
        for (String raw : pointer.substring(1).split("/")) {
            String token = raw.replace("~1", "/").replace("~0", "~");
            if (!(node instanceof Map<?, ?> map) || !map.containsKey(token)) {
                throw new SchemaReader.SchemaException("OpenAPI document has nothing at " + pointer);
            }
            node = map.get(token);
        }
        return node;
    }
}
