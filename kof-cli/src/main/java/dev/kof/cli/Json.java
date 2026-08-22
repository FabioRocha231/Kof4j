package dev.kof.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Json — minimal JSON reader/writer used by the LSP server.
 *
 * Deliberately dependency-free: the Kof tooling must not pull a JSON library
 * into the distribution for one protocol. Handles the subset needed by
 * Language Server Protocol messages (objects, arrays, strings, numbers,
 * booleans, null).
 */
final class Json {

    private Json() {}

    // ── Parse ────────────────────────────────────────────────────

    static Object parse(String text) {
        Parser p = new Parser(text);
        Object value = p.parseValue();
        p.skipWs();
        if (!p.atEnd()) throw new IllegalArgumentException("trailing JSON content");
        return value;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return i >= s.length();
        }

        void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        Object parseValue() {
            skipWs();
            if (atEnd()) throw new IllegalArgumentException("unexpected end of JSON");
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> { expect("true"); yield Boolean.TRUE; }
                case 'f' -> { expect("false"); yield Boolean.FALSE; }
                case 'n' -> { expect("null"); yield null; }
                default -> parseNumber();
            };
        }

        private void expect(String word) {
            if (!s.startsWith(word, i)) throw new IllegalArgumentException("invalid JSON literal");
            i += word.length();
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            i++; // {
            skipWs();
            if (!atEnd() && s.charAt(i) == '}') { i++; return map; }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                if (atEnd() || s.charAt(i) != ':') throw new IllegalArgumentException("expected ':'");
                i++;
                map.put(key, parseValue());
                skipWs();
                if (atEnd()) throw new IllegalArgumentException("unterminated object");
                char c = s.charAt(i++);
                if (c == '}') return map;
                if (c != ',') throw new IllegalArgumentException("expected ',' or '}'");
            }
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            i++; // [
            skipWs();
            if (!atEnd() && s.charAt(i) == ']') { i++; return list; }
            while (true) {
                list.add(parseValue());
                skipWs();
                if (atEnd()) throw new IllegalArgumentException("unterminated array");
                char c = s.charAt(i++);
                if (c == ']') return list;
                if (c != ',') throw new IllegalArgumentException("expected ',' or ']'");
            }
        }

        private String parseString() {
            if (atEnd() || s.charAt(i) != '"') throw new IllegalArgumentException("expected string");
            i++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) throw new IllegalArgumentException("unterminated string");
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (atEnd()) throw new IllegalArgumentException("unterminated escape");
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (i + 4 > s.length()) throw new IllegalArgumentException("bad unicode escape");
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> throw new IllegalArgumentException("bad escape: \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private Number parseNumber() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            String num = s.substring(start, i);
            try {
                if (num.contains(".") || num.contains("e") || num.contains("E")) return Double.parseDouble(num);
                return Long.parseLong(num);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid number: " + num);
            }
        }
    }

    // ── Write ────────────────────────────────────────────────────

    static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            sb.append('"').append(escape(s)).append('"');
        } else if (value instanceof Boolean b) {
            sb.append(b);
        } else if (value instanceof Number n) {
            sb.append(n);
        } else if (value instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(escape(String.valueOf(e.getKey()))).append("\":");
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (value instanceof List<?> l) {
            sb.append('[');
            boolean first = true;
            for (Object item : l) {
                if (!first) sb.append(',');
                first = false;
                write(sb, item);
            }
            sb.append(']');
        } else {
            sb.append('"').append(escape(String.valueOf(value))).append('"');
        }
    }

    static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}