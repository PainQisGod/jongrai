package jongrai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** The one place JSON is escaped and parsed. JDK only, so this stays tiny on purpose. */
public final class Json {

    private Json() {}

    /** A JSON string literal; null becomes the JSON null. Escapes every control character. */
    public static String str(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                // </script> safety when JSON is ever inlined into a page
                case '<' -> sb.append("\\u003c");
                default -> {
                    if (c < 0x20 || c == ' ' || c == ' ') sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    public static String list(List<String> items) {
        return items.stream().map(Json::str).collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * Parses JSON into Map / List / String / Double / Boolean / null.
     * Throws IllegalArgumentException on malformed input.
     */
    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != text.length()) throw p.error("trailing characters");
        return v;
    }

    /** Walks a parsed tree by object keys and array indexes; null when any step is missing. */
    public static Object at(Object node, Object... path) {
        for (Object step : path) {
            if (step instanceof String k && node instanceof Map<?, ?> m) node = m.get(k);
            else if (step instanceof Integer n && node instanceof List<?> l && n < l.size()) node = l.get(n);
            else return null;
        }
        return node;
    }

    private static final class Parser {
        final String s;
        int i;

        Parser(String s) { this.s = s; }

        Object value() {
            if (i >= s.length()) throw error("unexpected end");
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            ws();
            if (peek('}')) { i++; return m; }
            while (true) {
                ws();
                if (!peek('"')) throw error("expected key");
                String k = string();
                ws();
                expect(':');
                ws();
                m.put(k, value());
                ws();
                if (peek(',')) { i++; continue; }
                expect('}');
                return m;
            }
        }

        List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++;
            ws();
            if (peek(']')) { i++; return l; }
            while (true) {
                ws();
                l.add(value());
                ws();
                if (peek(',')) { i++; continue; }
                expect(']');
                return l;
            }
        }

        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                if (i >= s.length()) break;
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (i + 4 > s.length()) throw error("bad \\u escape");
                        try {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error("bad \\u escape");
                        }
                        i += 4;
                    }
                    default -> sb.append(e);
                }
            }
            throw error("unterminated string");
        }

        Object number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (start == i) throw error("unexpected character");
            try {
                return Double.parseDouble(s.substring(start, i));
            } catch (NumberFormatException e) {
                throw error("bad number");
            }
        }

        Object literal(String word, Object v) {
            if (!s.startsWith(word, i)) throw error("unexpected character");
            i += word.length();
            return v;
        }

        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        boolean peek(char c) { return i < s.length() && s.charAt(i) == c; }
        void expect(char c) { if (!peek(c)) throw error("expected '" + c + "'"); i++; }
        IllegalArgumentException error(String msg) { return new IllegalArgumentException("Bad JSON at " + i + ": " + msg); }
    }
}
