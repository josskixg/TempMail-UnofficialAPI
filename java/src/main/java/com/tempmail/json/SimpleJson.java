package com.tempmail.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal zero-dependency JSON parser and builder.
 * Supports objects, arrays, strings, numbers, booleans, and null.
 */
public final class SimpleJson {

    private SimpleJson() {}

    // --- Parsing ---

    public static JsonValue parse(String json) {
        if (json == null || json.trim().isEmpty()) return new JsonValue(JsonType.NULL, null);
        Parser p = new Parser(json.trim());
        return p.parseValue();
    }

    public static JsonObject parseObject(String json) {
        return parse(json).asObject();
    }

    public static JsonArray parseArray(String json) {
        return parse(json).asArray();
    }

    // --- Types ---

    public enum JsonType { OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL }

    public static class JsonValue {
        final JsonType type;
        final Object value;

        JsonValue(JsonType type, Object value) {
            this.type = type;
            this.value = value;
        }

        public boolean isNull() { return type == JsonType.NULL; }
        public boolean isObject() { return type == JsonType.OBJECT; }
        public boolean isArray() { return type == JsonType.ARRAY; }

        public JsonObject asObject() { return (JsonObject) value; }
        public JsonArray asArray() { return (JsonArray) value; }

        public String asString() {
            if (type == JsonType.NULL) return "";
            return value != null ? value.toString() : "";
        }

        public int asInt() {
            if (value instanceof Number) return ((Number) value).intValue();
            try { return Integer.parseInt(asString()); } catch (NumberFormatException e) { return 0; }
        }

        public long asLong() {
            if (value instanceof Number) return ((Number) value).longValue();
            try { return Long.parseLong(asString()); } catch (NumberFormatException e) { return 0L; }
        }

        public boolean asBoolean() {
            if (type == JsonType.BOOLEAN) return (Boolean) value;
            return Boolean.parseBoolean(asString());
        }
    }

    public static class JsonObject {
        private final Map<String, JsonValue> map = new LinkedHashMap<>();

        public void put(String key, JsonValue value) { map.put(key, value); }
        public boolean has(String key) { return map.containsKey(key); }
        public JsonValue get(String key) { return map.getOrDefault(key, new JsonValue(JsonType.NULL, null)); }
        public String getString(String key) { return get(key).asString(); }
        public int getInt(String key) { return get(key).asInt(); }
        public long getLong(String key) { return get(key).asLong(); }
        public boolean getBoolean(String key) { return get(key).asBoolean(); }
        public JsonObject getObject(String key) {
            JsonValue v = get(key);
            return v.isObject() ? v.asObject() : new JsonObject();
        }
        public JsonArray getArray(String key) {
            JsonValue v = get(key);
            return v.isArray() ? v.asArray() : new JsonArray();
        }

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, JsonValue> e : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append(escape(e.getKey())).append(":").append(valueToJson(e.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
    }

    public static class JsonArray {
        private final List<JsonValue> list = new ArrayList<>();

        public void add(JsonValue value) { list.add(value); }
        public int size() { return list.size(); }
        public boolean isEmpty() { return list.isEmpty(); }
        public JsonValue get(int index) { return list.get(index); }
        public List<JsonValue> elements() { return list; }

        public String toJson() {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(valueToJson(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
    }

    // --- Builder ---

    public static JsonObject object() { return new JsonObject(); }
    public static JsonArray array() { return new JsonArray(); }

    public static JsonValue val(String s) { return new JsonValue(JsonType.STRING, s); }
    public static JsonValue val(Number n) { return new JsonValue(JsonType.NUMBER, n); }
    public static JsonValue val(boolean b) { return new JsonValue(JsonType.BOOLEAN, b); }
    public static JsonValue nullVal() { return new JsonValue(JsonType.NULL, null); }

    // --- Internal ---

    private static String valueToJson(JsonValue v) {
        switch (v.type) {
            case STRING: return escape((String) v.value);
            case NUMBER: return v.value.toString();
            case BOOLEAN: return v.value.toString();
            case NULL: return "null";
            case OBJECT: return v.asObject().toJson();
            case ARRAY: return v.asArray().toJson();
            default: return "null";
        }
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    private static class Parser {
        private final String json;
        private int pos;

        Parser(String json) {
            this.json = json;
            this.pos = 0;
        }

        JsonValue parseValue() {
            skipWhitespace();
            if (pos >= json.length()) return new JsonValue(JsonType.NULL, null);
            char c = json.charAt(pos);
            if (c == '{') return new JsonValue(JsonType.OBJECT, parseObj());
            if (c == '[') return new JsonValue(JsonType.ARRAY, parseArr());
            if (c == '"') return new JsonValue(JsonType.STRING, parseString());
            if (c == 't' || c == 'f') return new JsonValue(JsonType.BOOLEAN, parseBoolean());
            if (c == 'n') { parseNull(); return new JsonValue(JsonType.NULL, null); }
            return new JsonValue(JsonType.NUMBER, parseNumber());
        }

        private JsonObject parseObj() {
            JsonObject obj = new JsonObject();
            pos++; // skip '{'
            skipWhitespace();
            if (pos < json.length() && json.charAt(pos) == '}') { pos++; return obj; }
            while (pos < json.length()) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                JsonValue value = parseValue();
                obj.put(key, value);
                skipWhitespace();
                if (pos < json.length() && json.charAt(pos) == ',') { pos++; continue; }
                if (pos < json.length() && json.charAt(pos) == '}') { pos++; break; }
            }
            return obj;
        }

        private JsonArray parseArr() {
            JsonArray arr = new JsonArray();
            pos++; // skip '['
            skipWhitespace();
            if (pos < json.length() && json.charAt(pos) == ']') { pos++; return arr; }
            while (pos < json.length()) {
                arr.add(parseValue());
                skipWhitespace();
                if (pos < json.length() && json.charAt(pos) == ',') { pos++; continue; }
                if (pos < json.length() && json.charAt(pos) == ']') { pos++; break; }
            }
            return arr;
        }

        private String parseString() {
            skipWhitespace();
            if (pos >= json.length() || json.charAt(pos) != '"') return "";
            pos++; // skip opening '"'
            StringBuilder sb = new StringBuilder();
            while (pos < json.length()) {
                char c = json.charAt(pos);
                if (c == '\\') {
                    pos++;
                    if (pos < json.length()) {
                        char esc = json.charAt(pos);
                        switch (esc) {
                            case '"': sb.append('"'); break;
                            case '\\': sb.append('\\'); break;
                            case 'n': sb.append('\n'); break;
                            case 'r': sb.append('\r'); break;
                            case 't': sb.append('\t'); break;
                            case '/': sb.append('/'); break;
                            case 'u':
                                if (pos + 4 < json.length()) {
                                    String hex = json.substring(pos + 1, pos + 5);
                                    sb.append((char) Integer.parseInt(hex, 16));
                                    pos += 4;
                                }
                                break;
                            default: sb.append(esc);
                        }
                    }
                } else if (c == '"') {
                    pos++; // skip closing '"'
                    return sb.toString();
                } else {
                    sb.append(c);
                }
                pos++;
            }
            return sb.toString();
        }

        private Number parseNumber() {
            skipWhitespace();
            int start = pos;
            boolean isFloat = false;
            if (pos < json.length() && json.charAt(pos) == '-') pos++;
            while (pos < json.length() && Character.isDigit(json.charAt(pos))) pos++;
            if (pos < json.length() && json.charAt(pos) == '.') { isFloat = true; pos++; }
            while (pos < json.length() && Character.isDigit(json.charAt(pos))) pos++;
            if (pos < json.length() && (json.charAt(pos) == 'e' || json.charAt(pos) == 'E')) {
                isFloat = true; pos++;
                if (pos < json.length() && (json.charAt(pos) == '+' || json.charAt(pos) == '-')) pos++;
                while (pos < json.length() && Character.isDigit(json.charAt(pos))) pos++;
            }
            String numStr = json.substring(start, pos);
            if (isFloat) return Double.parseDouble(numStr);
            long l = Long.parseLong(numStr);
            if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return (int) l;
            return l;
        }

        private Boolean parseBoolean() {
            if (json.startsWith("true", pos)) { pos += 4; return true; }
            if (json.startsWith("false", pos)) { pos += 5; return false; }
            return false;
        }

        private void parseNull() {
            if (json.startsWith("null", pos)) pos += 4;
        }

        private void expect(char c) {
            skipWhitespace();
            if (pos < json.length() && json.charAt(pos) == c) pos++;
        }

        private void skipWhitespace() {
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) pos++;
        }
    }
}
