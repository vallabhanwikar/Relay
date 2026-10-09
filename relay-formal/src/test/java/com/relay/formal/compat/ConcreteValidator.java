package com.relay.formal.compat;

import com.relay.formal.json.Json;
import com.relay.formal.schema.Schema;
import com.relay.formal.schema.Schema.AnySchema;
import com.relay.formal.schema.Schema.ArraySchema;
import com.relay.formal.schema.Schema.BooleanSchema;
import com.relay.formal.schema.Schema.CompositeSchema;
import com.relay.formal.schema.Schema.NeverSchema;
import com.relay.formal.schema.Schema.NullSchema;
import com.relay.formal.schema.Schema.NumberSchema;
import com.relay.formal.schema.Schema.ObjectSchema;
import com.relay.formal.schema.Schema.StringSchema;
import com.relay.formal.schema.Schema.TruncatedSchema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * A plain, non-symbolic JSON Schema validator for the same subset the prover encodes.
 *
 * <p>It shares no code with the SMT encoding on purpose. Tests use it to check every witness the
 * solver produces: a witness that the producing schema rejects would mean the encoding is wrong,
 * and the counterexample would be fiction. This is the unit-level version of the replay guard
 * in design v3 (UC3 step 3).
 */
final class ConcreteValidator {

    private ConcreteValidator() {
    }

    static boolean valid(Schema schema, Object value) {
        return switch (schema) {
            case AnySchema a -> true;
            case TruncatedSchema t -> true;
            case NeverSchema n -> false;
            case NullSchema n -> value == Json.NULL;
            case CompositeSchema c -> switch (c.mode()) {
                case ALL_OF -> c.options().stream().allMatch(o -> valid(o, value));
                case ANY_OF -> c.options().stream().anyMatch(o -> valid(o, value));
                case ONE_OF -> c.options().stream().filter(o -> valid(o, value)).count() == 1;
            };
            case BooleanSchema b -> nullOk(b.nullable(), value)
                    || (value instanceof Boolean v && (b.enumValues().isEmpty() || b.enumValues().contains(v)));
            case NumberSchema n -> nullOk(n.nullable(), value) || (value instanceof BigDecimal d && number(n, d));
            case StringSchema s -> nullOk(s.nullable(), value) || (value instanceof String v && string(s, v));
            case ObjectSchema o -> nullOk(o.nullable(), value) || (value instanceof Map<?, ?> m && object(o, m));
            case ArraySchema a -> nullOk(a.nullable(), value) || (value instanceof List<?> l && array(a, l));
        };
    }

    private static boolean nullOk(boolean nullable, Object value) {
        return nullable && value == Json.NULL;
    }

    private static boolean number(NumberSchema n, BigDecimal d) {
        if (n.integer() && d.stripTrailingZeros().scale() > 0) {
            return false;
        }
        if (n.minimum() != null) {
            int c = d.compareTo(n.minimum());
            if (n.exclusiveMinimum() ? c <= 0 : c < 0) {
                return false;
            }
        }
        if (n.maximum() != null) {
            int c = d.compareTo(n.maximum());
            if (n.exclusiveMaximum() ? c >= 0 : c > 0) {
                return false;
            }
        }
        if (n.multipleOf() != null && d.remainder(n.multipleOf()).signum() != 0) {
            return false;
        }
        return n.enumValues().isEmpty() || n.enumValues().stream().anyMatch(e -> e.compareTo(d) == 0);
    }

    private static boolean string(StringSchema s, String v) {
        int length = v.codePointCount(0, v.length());
        if (s.minLength() != null && length < s.minLength()) {
            return false;
        }
        if (s.maxLength() != null && length > s.maxLength()) {
            return false;
        }
        if (s.pattern() != null && !java.util.regex.Pattern.compile(s.pattern()).matcher(v).find()) {
            return false;
        }
        return s.enumValues().isEmpty() || s.enumValues().contains(v);
    }

    private static boolean object(ObjectSchema o, Map<?, ?> m) {
        for (String name : o.required()) {
            if (!m.containsKey(name)) {
                return false;
            }
        }
        for (Map.Entry<?, ?> entry : m.entrySet()) {
            Schema declared = o.properties().get(String.valueOf(entry.getKey()));
            Schema governing = declared != null ? declared : o.additionalProperties();
            if (!valid(governing, entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static boolean array(ArraySchema a, List<?> l) {
        if (a.minItems() != null && l.size() < a.minItems()) {
            return false;
        }
        if (a.maxItems() != null && l.size() > a.maxItems()) {
            return false;
        }
        return l.stream().allMatch(e -> valid(a.items(), e));
    }
}
