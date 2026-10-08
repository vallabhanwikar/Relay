package com.relay.formal.smt;

import com.microsoft.z3.Expr;
import com.microsoft.z3.IntNum;
import com.microsoft.z3.Model;
import com.microsoft.z3.RatNum;
import com.relay.formal.json.Json;
import com.relay.formal.schema.FieldPath;
import com.relay.formal.smt.SymbolicPayload.Kind;
import com.relay.formal.smt.SymbolicPayload.PathVars;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads a satisfying model back into a concrete JSON payload. */
public final class WitnessDecoder {

    private static final Pattern Z3_ESCAPE = Pattern.compile("\\\\u\\{([0-9a-fA-F]{1,6})}");

    private final Model model;
    private final SymbolicPayload payload;
    private final Set<String> approximations = new TreeSet<>();

    private WitnessDecoder(Model model, SymbolicPayload payload) {
        this.model = model;
        this.payload = payload;
    }

    /** The decoded payload plus any reason it is less than exact. */
    public record Witness(Object value, Set<String> approximations) {
    }

    public static Witness decode(Model model, SymbolicPayload payload) {
        WitnessDecoder decoder = new WitnessDecoder(model, payload);
        Object root = decoder.valueAt(FieldPath.ROOT);
        return new Witness(root, decoder.approximations);
    }

    private Object valueAt(FieldPath path) {
        PathVars v = payload.vars(path);
        Kind kind = Kind.of(((IntNum) eval(v.kind())).getInt());
        return switch (kind) {
            case NULL -> Json.NULL;
            case BOOLEAN -> eval(v.bool()).isTrue();
            case NUMBER -> number(eval(v.number()));
            case STRING -> unescape(eval(v.string()).getString());
            case OBJECT -> object(path);
            case ARRAY -> array(path, ((IntNum) eval(v.length())).getInt());
        };
    }

    private Map<String, Object> object(FieldPath path) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (FieldPath candidate : payload.paths().keySet()) {
            if (candidate.isRoot() || candidate.isItems() || !candidate.parent().equals(path)) {
                continue;
            }
            if (eval(payload.present(candidate)).isTrue()) {
                result.put(candidate.last(), valueAt(candidate));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private List<Object> array(FieldPath path, int length) {
        FieldPath items = path.items();
        Object element;
        if (payload.paths().containsKey(items)) {
            element = valueAt(items);
        } else {
            element = Json.NULL;
            if (length > 0) {
                approximations.add("elements of " + path + " are unconstrained and shown as null");
            }
        }
        List<Object> result = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            result.add(element);
        }
        return Collections.unmodifiableList(result);
    }

    private Object number(Expr<?> value) {
        if (value instanceof IntNum i) {
            return new BigDecimal(i.getBigInteger());
        }
        RatNum rational = (RatNum) value;
        BigDecimal numerator = new BigDecimal(rational.getBigIntNumerator());
        BigDecimal denominator = new BigDecimal(rational.getBigIntDenominator());
        try {
            return numerator.divide(denominator);
        } catch (ArithmeticException nonTerminating) {
            approximations.add("a rational value was rounded to 16 significant digits");
            return numerator.divide(denominator, MathContext.DECIMAL64);
        }
    }

    private Expr<?> eval(Expr<?> expr) {
        return model.eval(expr, true);
    }

    /** Z3 prints non-printable and non-ASCII characters as {@code \\u{hex}}. */
    static String unescape(String z3) {
        Matcher matcher = Z3_ESCAPE.matcher(z3);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out,
                    Matcher.quoteReplacement(new String(Character.toChars(Integer.parseInt(matcher.group(1), 16)))));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
