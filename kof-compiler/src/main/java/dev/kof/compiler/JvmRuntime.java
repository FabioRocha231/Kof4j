package dev.kof.compiler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


final class JvmRuntime {

    private JvmRuntime() {}

static boolean hasRuntimeFn(String methodName) {
        return methodName.startsWith("kof_json_")
                || methodName.startsWith("kof_io_")
                || methodName.startsWith("kof_web_")
                || methodName.startsWith("kof_ui_")
                || methodName.startsWith("kof_sec_")
                || methodName.equals("kof_now")
                || methodName.equals("kof_read_line")
                || methodName.equals("kof_read_file")
                || methodName.equals("kof_write_file")
                || methodName.equals("kof_spawn");
    }

    static void ensureCompiled(Path outputDir, List<IRClass> classes) throws IOException {
        Path runtimeDir = outputDir.resolve("dev/kof/runtime");
        if (Files.exists(runtimeDir.resolve("KofRuntime.class"))) return;
        Files.createDirectories(runtimeDir);
        Path sourceFile = outputDir.resolve("KofRuntime.java");
        Files.writeString(sourceFile, source(classes));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IOException("JVM runtime requires a full JDK (javac not available)");
        }
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        int rc = compiler.run(null, null, err, "-d", outputDir.toString(),
                "-classpath", outputDir.toString(), sourceFile.toString());
        if (rc != 0) {
            String detail = err.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            throw new IOException("failed to compile KofRuntime helper (javac exit " + rc + "): "
                    + (detail.isEmpty() ? "unknown error" : detail));
        }
        Files.deleteIfExists(sourceFile);
    }

    static String callDescriptor(String methodName) {
        return switch (methodName) {
            case "kof_json_encode_int" -> "(I)Ljava/lang/String;";
            case "kof_json_encode_long" -> "(J)Ljava/lang/String;";
            case "kof_json_encode_bool" -> "(I)Ljava/lang/String;";
            case "kof_json_encode_string" -> "(Ljava/lang/String;)Ljava/lang/String;";
            case "kof_json_encode_list" -> "(Ljava/util/List;I)Ljava/lang/String;";
            case "kof_json_encode_array", "kof_json_encode" -> "(Ljava/lang/Object;)Ljava/lang/String;";
            case "kof_json_decode_int", "kof_json_decode_bool" -> "(Ljava/lang/String;)I";
            case "kof_json_decode_long" -> "(Ljava/lang/String;)J";
            case "kof_json_decode_string" -> "(Ljava/lang/String;)Ljava/lang/String;";
            case "kof_json_decode_int_list", "kof_json_decode_string_list", "kof_json_decode_list"
                    -> "(Ljava/lang/String;)Ljava/util/ArrayList;";
            case "kof_now" -> "()J";
            case "kof_read_line" -> "()Ljava/lang/String;";
            case "kof_read_file" -> "(Ljava/lang/String;)Ljava/lang/String;";
            case "kof_write_file" -> "(Ljava/lang/String;Ljava/lang/String;)I";
            case "kof_spawn" -> "(Ljava/lang/Object;)V";
            case "kof_io_file_exists", "kof_io_file_is_file", "kof_io_file_is_dir" -> "(Ljava/lang/String;)I";
            case "kof_io_read_text" -> "(Ljava/lang/String;)Ljava/lang/String;";
            case "kof_io_write_text", "kof_io_append_text" -> "(Ljava/lang/String;Ljava/lang/String;)I";
            case "kof_io_read_bytes" -> "(Ljava/lang/String;)[I";
            case "kof_io_write_bytes", "kof_io_append_bytes" -> "(Ljava/lang/String;[I)I";
            case "kof_io_delete", "kof_io_dir_create", "kof_io_dir_create_dirs", "kof_io_dir_delete"
                    -> "(Ljava/lang/String;)I";
            case "kof_io_file_size" -> "(Ljava/lang/String;)J";
            case "kof_io_file_name", "kof_io_path_parent", "kof_io_path_file_name",
                    "kof_io_path_extension", "kof_io_path_normalize", "kof_io_path_to_absolute"
                    -> "(Ljava/lang/String;)Ljava/lang/String;";
            case "kof_io_path_resolve" -> "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";
            case "kof_io_path_is_absolute" -> "(Ljava/lang/String;)I";
            case "kof_ui_color_to_css" -> "(I)Ljava/lang/String;";
            case "kof_ui_window_new", "kof_ui_label_new" -> "(Ljava/lang/String;)I";
            case "kof_ui_window_set_title", "kof_ui_label_set_text" -> "(ILjava/lang/String;)V";
            case "kof_ui_window_bind" -> "(II)V";
            case "kof_ui_window_title", "kof_ui_label_text" -> "(I)Ljava/lang/String;";
            case "kof_ui_window_show", "kof_ui_window_close", "kof_ui_label_remove" -> "(I)V";
            case "kof_io_dir_list" -> "(Ljava/lang/String;)Ljava/util/ArrayList;";
            case "kof_web_app_new" -> "()Ljava/lang/String;";
            case "kof_web_route" -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V";
            case "kof_web_use" -> "(Ljava/lang/String;Ljava/lang/Object;)V";
            case "kof_web_listen" -> "(Ljava/lang/String;I)V";
            case "kof_web_port" -> "(Ljava/lang/String;)I";
            case "kof_web_close" -> "(Ljava/lang/String;)V";
            case "kof_web_param", "kof_web_query", "kof_web_header"
                    -> "(Ljava/lang/String;)Ljava/lang/String;";
            case "kof_web_body", "kof_web_method", "kof_web_path" -> "()Ljava/lang/String;";
            // ── kof.security (docs/security.md §5) ───────────────────
            case "kof_sec_sha256", "kof_sec_sha512", "kof_sec_redact", "kof_sec_secret_get",
                    "kof_sec_password_hash", "kof_sec_auth_user" -> "(Ljava/lang/String;)Ljava/lang/String;";
            case "kof_sec_hmac_sha256", "kof_sec_aesgcm_encrypt", "kof_sec_aesgcm_decrypt",
                    "kof_sec_secret_get_default", "kof_sec_jwt_create", "kof_sec_jwt_verify",
                    "kof_sec_cors_allowed" -> "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";
            case "kof_sec_jwt_create_ttl" -> "(Ljava/lang/String;Ljava/lang/String;I)Ljava/lang/String;";
            case "kof_sec_jwt_verify_iss_aud"
                    -> "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";
            case "kof_sec_random_hex" -> "(I)Ljava/lang/String;";
            case "kof_sec_random_int" -> "(I)I";
            case "kof_sec_constant_time_equals", "kof_sec_password_verify",
                    "kof_sec_password_needs_rehash", "kof_sec_csrf_valid" -> "(Ljava/lang/String;Ljava/lang/String;)I";
            case "kof_sec_jwt_secret", "kof_sec_csrf_token", "kof_sec_csp_header",
                    "kof_sec_hsts_header", "kof_sec_content_type_options_header",
                    "kof_sec_frame_header", "kof_sec_referrer_header", "kof_sec_auth_token",
                    "kof_sec_auth_claims" -> "()Ljava/lang/String;";
            case "kof_sec_auth_secret", "kof_sec_auth_authenticated",
                    "kof_sec_auth_has_role", "kof_sec_auth_has_permission" -> "(Ljava/lang/String;)I";
            default -> "(Ljava/lang/String;)Ljava/lang/Object;";
        };
    }

    static String callReturnDescriptor(String methodName) {
        return switch (methodName) {
            case "kof_json_decode_int", "kof_json_decode_bool" -> "I";
            case "kof_json_decode_long", "kof_now" -> "J";
            case "kof_json_decode_int_list", "kof_json_decode_string_list", "kof_json_decode_list"
                    -> "Ljava/util/ArrayList;";
            case "kof_json_decode_string", "kof_read_line", "kof_read_file" -> "Ljava/lang/String;";
            case "kof_write_file" -> "I";
            case "kof_io_file_exists", "kof_io_file_is_file", "kof_io_file_is_dir",
                    "kof_io_write_text", "kof_io_append_text", "kof_io_write_bytes", "kof_io_append_bytes",
                    "kof_io_delete", "kof_io_dir_create", "kof_io_dir_create_dirs", "kof_io_dir_delete",
                    "kof_io_path_is_absolute" -> "I";
            case "kof_io_read_text", "kof_io_file_name", "kof_io_path_parent", "kof_io_path_file_name",
                    "kof_io_path_extension", "kof_io_path_normalize", "kof_io_path_resolve",
                    "kof_io_path_to_absolute" -> "Ljava/lang/String;";
            case "kof_io_read_bytes" -> "[I";
            case "kof_io_file_size" -> "J";
            case "kof_io_dir_list" -> "Ljava/util/ArrayList;";
            case "kof_web_app_new", "kof_web_param", "kof_web_query", "kof_web_header",
                    "kof_web_body", "kof_web_method", "kof_web_path" -> "Ljava/lang/String;";
            case "kof_web_port" -> "I";
            // ── kof.security (docs/security.md §5) ───────────────────
            case "kof_sec_sha256", "kof_sec_sha512", "kof_sec_hmac_sha256", "kof_sec_redact",
                    "kof_sec_secret_get", "kof_sec_secret_get_default", "kof_sec_password_hash",
                    "kof_sec_aesgcm_encrypt", "kof_sec_aesgcm_decrypt", "kof_sec_jwt_create",
                    "kof_sec_jwt_create_ttl", "kof_sec_jwt_verify", "kof_sec_jwt_verify_iss_aud",
                    "kof_sec_jwt_secret", "kof_sec_random_hex", "kof_sec_csrf_token",
                    "kof_sec_csp_header", "kof_sec_hsts_header", "kof_sec_content_type_options_header",
                    "kof_sec_frame_header", "kof_sec_referrer_header", "kof_sec_auth_token",
                    "kof_sec_auth_claims", "kof_sec_auth_user" -> "Ljava/lang/String;";
            case "kof_sec_random_int", "kof_sec_constant_time_equals", "kof_sec_password_verify",
                    "kof_sec_password_needs_rehash", "kof_sec_csrf_valid", "kof_sec_cors_allowed",
                    "kof_sec_auth_secret", "kof_sec_auth_authenticated", "kof_sec_auth_has_role",
                    "kof_sec_auth_has_permission" -> "I";
            default -> "Ljava/lang/Object;";
        };
    }

    private static String source(List<IRClass> classes) {
        StringBuilder decoders = new StringBuilder();
        for (IRClass clazz : classes) {
            String internal = clazz.name();
            if (internal == null || internal.isBlank() || internal.equals("java/lang/Object")) continue;
            if ("Main".equals(internal) || internal.endsWith("/Main")) continue;
            String javaName = internal.replace('/', '.');
            String mangle = javaName.replace('.', '_');
            decoders.append("""
                        public static Object kof_json_decode_%s(String json) throws Exception {
                            return kof_json_decode_object(json, Class.forName("%s"));
                        }

                    """.formatted(mangle, javaName));
        }
        return """
            package dev.kof.runtime;

            import java.lang.reflect.Field;
            import java.lang.reflect.Modifier;
            import java.lang.reflect.RecordComponent;
            import java.util.ArrayList;
            import java.util.LinkedHashMap;
            import java.util.List;
            import java.util.Map;

            /**
             * JSON helpers for the JVM target of the Kof compiler.
             * Generated by JvmRuntime at build time; the native target has its
             * own assembly implementations of the same functions.
             */
            public final class KofRuntime {

                private KofRuntime() {}

                public static String kof_json_encode_int(int value) {
                    return Integer.toString(value);
                }

                public static String kof_json_encode_long(long value) {
                    return Long.toString(value);
                }

                public static String kof_json_encode_bool(int value) {
                    return value != 0 ? "true" : "false";
                }

                public static String kof_json_encode_string(String value) {
                    if (value == null) return "null";
                    StringBuilder sb = new StringBuilder(value.length() + 2);
                    sb.append('"');
                    for (int i = 0; i < value.length(); i++) {
                        char c = value.charAt(i);
                        switch (c) {
                            case '"' -> sb.append("\\\\\\"");
                            case '\\\\' -> sb.append("\\\\\\\\");
                            case '\\n' -> sb.append("\\\\n");
                            case '\\r' -> sb.append("\\\\r");
                            case '\\t' -> sb.append("\\\\t");
                            default -> {
                                if (c < 0x20) {
                                    sb.append("\\\\u");
                                    String hex = Integer.toHexString(c);
                                    sb.append("0".repeat(4 - hex.length()));
                                    sb.append(hex);
                                } else {
                                    sb.append(c);
                                }
                            }
                        }
                    }
                    sb.append('"');
                    return sb.toString();
                }

                public static String kof_json_encode_list(List<?> list, int tag) {
                    StringBuilder sb = new StringBuilder("[");
                    for (int i = 0; i < list.size(); i++) {
                        if (i > 0) sb.append(',');
                        Object e = list.get(i);
                        switch (tag) {
                            case 1 -> sb.append(kof_json_encode_string((String) e));
                            case 2 -> sb.append(kof_json_encode_bool(((Integer) e).intValue()));
                            default -> sb.append(kof_json_encode(e));
                        }
                    }
                    sb.append(']');
                    return sb.toString();
                }

                public static String kof_json_encode_array(Object array) {
                    StringBuilder sb = new StringBuilder("[");
                    int length = java.lang.reflect.Array.getLength(array);
                    for (int i = 0; i < length; i++) {
                        if (i > 0) sb.append(',');
                        sb.append(kof_json_encode(java.lang.reflect.Array.get(array, i)));
                    }
                    sb.append(']');
                    return sb.toString();
                }

                public static String kof_json_encode(Object value) {
                    if (value == null) return "null";
                    if (value instanceof String s) return kof_json_encode_string(s);
                    if (value instanceof Integer i) return kof_json_encode_int(i);
                    if (value instanceof Long l) return kof_json_encode_long(l);
                    if (value instanceof Boolean b) return kof_json_encode_bool(b ? 1 : 0);
                    if (value instanceof List<?> l) {
                        StringBuilder sb = new StringBuilder("[");
                        for (int i = 0; i < l.size(); i++) {
                            if (i > 0) sb.append(',');
                            sb.append(kof_json_encode(l.get(i)));
                        }
                        sb.append(']');
                        return sb.toString();
                    }
                    if (value.getClass().isArray()) return kof_json_encode_array(value);
                    return kof_json_encode_object(value);
                }

                private static String kof_json_encode_object(Object value) {
                    StringBuilder sb = new StringBuilder("{");
                    boolean first = true;
                    for (Field f : value.getClass().getDeclaredFields()) {
                        if (Modifier.isStatic(f.getModifiers())) continue;
                        try {
                            f.setAccessible(true);
                            Object v = f.get(value);
                            if (!first) sb.append(',');
                            first = false;
                            sb.append(kof_json_encode_string(f.getName()));
                            sb.append(':');
                            sb.append(kof_json_encode(v));
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException("cannot encode field " + f.getName(), e);
                        }
                    }
                    sb.append('}');
                    return sb.toString();
                }

                public static int kof_json_decode_int(String json) {
                    return Integer.parseInt(json.trim());
                }

                public static long kof_json_decode_long(String json) {
                    return Long.parseLong(json.trim());
                }

                public static int kof_json_decode_bool(String json) {
                    return Boolean.parseBoolean(json.trim()) ? 1 : 0;
                }

                public static String kof_json_decode_string(String json) {
                    String s = json.trim();
                    if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
                        return s.substring(1, s.length() - 1);
                    }
                    return s;
                }

                public static ArrayList<Integer> kof_json_decode_int_list(String json) {
                    Object parsed = kof_json_parse(json);
                    ArrayList<Integer> result = new ArrayList<>();
                    if (parsed instanceof List<?> l) {
                        for (Object e : l) result.add(((Number) e).intValue());
                    }
                    return result;
                }

                public static ArrayList<String> kof_json_decode_string_list(String json) {
                    Object parsed = kof_json_parse(json);
                    ArrayList<String> result = new ArrayList<>();
                    if (parsed instanceof List<?> l) {
                        for (Object e : l) result.add(e == null ? null : String.valueOf(e));
                    }
                    return result;
                }

                public static ArrayList<Object> kof_json_decode_list(String json) {
                    Object parsed = kof_json_parse(json);
                    if (parsed instanceof ArrayList<?> l) return new ArrayList<Object>(l);
                    return new ArrayList<Object>();
                }

                public static Object kof_json_decode_object(String json, Class<?> type) throws Exception {
                    return kof_json_bind(type, kof_json_parse(json));
                }

                private static Object kof_json_bind(Class<?> type, Object value) throws Exception {
                    if (value == null) return null;
                    if (type == String.class) return value instanceof String s ? s : String.valueOf(value);
                    if (type == int.class || type == Integer.class || type == long.class || type == Long.class
                            || type == byte.class || type == short.class || type == float.class || type == double.class
                            || type == Number.class) {
                        return value;
                    }
                    if (type == boolean.class || type == Boolean.class) {
                        return value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
                    }
                    if (type == char.class || type == Character.class) {
                        return value.toString().charAt(0);
                    }
                    if (type.isAssignableFrom(ArrayList.class) || type == List.class || type == java.util.Collection.class) {
                        if (value instanceof List<?> l) return new ArrayList<Object>(l);
                    }
                    if (value instanceof Map<?, ?> m) {
                        if (type.isRecord()) {
                            RecordComponent[] comps = type.getRecordComponents();
                            Class<?>[] argTypes = new Class<?>[comps.length];
                            Object[] args = new Object[comps.length];
                            for (int i = 0; i < comps.length; i++) {
                                argTypes[i] = comps[i].getType();
                                args[i] = kof_json_bind(comps[i].getType(), m.get(comps[i].getName()));
                            }
                            return type.getDeclaredConstructor(argTypes).newInstance(args);
                        }
                        Object obj = type.getDeclaredConstructor().newInstance();
                        for (Field f : type.getDeclaredFields()) {
                            if (Modifier.isStatic(f.getModifiers())) continue;
                            if (!m.containsKey(f.getName())) continue;
                            f.setAccessible(true);
                            Object v = kof_json_bind(f.getType(), m.get(f.getName()));
                            if (v == null) continue;
                            if (f.getType() == int.class) f.setInt(obj, ((Number) v).intValue());
                            else if (f.getType() == long.class) f.setLong(obj, ((Number) v).longValue());
                            else if (f.getType() == short.class) f.setShort(obj, ((Number) v).shortValue());
                            else if (f.getType() == byte.class) f.setByte(obj, ((Number) v).byteValue());
                            else if (f.getType() == float.class) f.setFloat(obj, ((Number) v).floatValue());
                            else if (f.getType() == double.class) f.setDouble(obj, ((Number) v).doubleValue());
                            else if (f.getType() == boolean.class) f.setBoolean(obj, (Boolean) v);
                            else f.set(obj, v);
                        }
                        return obj;
                    }
                    return value;
                }

                public static Object kof_json_parse(String json) {
                    JsonParser p = new JsonParser(json);
                    Object v = p.parseValue();
                    p.skipWs();
                    if (p.pos < json.length()) throw new IllegalArgumentException("trailing JSON content at " + p.pos);
                    return v;
                }

                private static final class JsonParser {
                    private final String s;
                    private int pos;

                    JsonParser(String s) {
                        this.s = s;
                    }

                    void skipWs() {
                        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
                    }

                    Object parseValue() {
                        skipWs();
                        if (pos >= s.length()) throw new IllegalArgumentException("unexpected end of JSON");
                        char c = s.charAt(pos);
                        if (c == '{') return parseObject();
                        if (c == '[') return parseArray();
                        if (c == '"') return parseString();
                        if (c == 't') { expect("true"); return Boolean.TRUE; }
                        if (c == 'f') { expect("false"); return Boolean.FALSE; }
                        if (c == 'n') { expect("null"); return null; }
                        if (c == '-' || (c >= '0' && c <= '9')) return parseNumber();
                        throw new IllegalArgumentException("unexpected JSON char '" + c + "' at " + pos);
                    }

                    private void expect(String word) {
                        if (!s.startsWith(word, pos)) throw new IllegalArgumentException("invalid JSON literal at " + pos);
                        pos += word.length();
                    }

                    private Map<String, Object> parseObject() {
                        Map<String, Object> map = new LinkedHashMap<>();
                        pos++;
                        skipWs();
                        if (pos < s.length() && s.charAt(pos) == '}') { pos++; return map; }
                        while (true) {
                            skipWs();
                            String key = parseString();
                            skipWs();
                            if (pos >= s.length() || s.charAt(pos) != ':') throw new IllegalArgumentException("expected ':' at " + pos);
                            pos++;
                            map.put(key, parseValue());
                            skipWs();
                            if (pos >= s.length()) throw new IllegalArgumentException("unterminated JSON object");
                            char c = s.charAt(pos);
                            if (c == ',') { pos++; continue; }
                            if (c == '}') { pos++; return map; }
                            throw new IllegalArgumentException("expected ',' or '}' at " + pos);
                        }
                    }

                    private List<Object> parseArray() {
                        List<Object> list = new ArrayList<>();
                        pos++;
                        skipWs();
                        if (pos < s.length() && s.charAt(pos) == ']') { pos++; return list; }
                        while (true) {
                            list.add(parseValue());
                            skipWs();
                            if (pos >= s.length()) throw new IllegalArgumentException("unterminated JSON array");
                            char c = s.charAt(pos);
                            if (c == ',') { pos++; continue; }
                            if (c == ']') { pos++; return list; }
                            throw new IllegalArgumentException("expected ',' or ']' at " + pos);
                        }
                    }

                    private String parseString() {
                        if (pos >= s.length() || s.charAt(pos) != '"') throw new IllegalArgumentException("expected string at " + pos);
                        pos++;
                        StringBuilder sb = new StringBuilder();
                        while (true) {
                            if (pos >= s.length()) throw new IllegalArgumentException("unterminated JSON string");
                            char c = s.charAt(pos);
                            if (c == '"') { pos++; return sb.toString(); }
                            if (c == '\\\\') {
                                pos++;
                                if (pos >= s.length()) throw new IllegalArgumentException("unterminated escape");
                                char e = s.charAt(pos);
                                switch (e) {
                                    case '"' -> sb.append('"');
                                    case '\\\\' -> sb.append('\\\\');
                                    case '/' -> sb.append('/');
                                    case 'n' -> sb.append('\\n');
                                    case 't' -> sb.append('\\t');
                                    case 'r' -> sb.append('\\r');
                                    case 'b' -> sb.append('\\b');
                                    case 'f' -> sb.append('\\f');
                                    case 'u' -> {
                                        if (pos + 4 >= s.length()) throw new IllegalArgumentException("bad \\\\u escape");
                                        sb.append((char) Integer.parseInt(s.substring(pos + 1, pos + 5), 16));
                                        pos += 4;
                                    }
                                    default -> throw new IllegalArgumentException("bad escape '\\\\" + e + "'");
                                }
                                pos++;
                            } else {
                                sb.append(c);
                                pos++;
                            }
                        }
                    }

                    private Object parseNumber() {
                        int start = pos;
                        if (pos < s.length() && s.charAt(pos) == '-') pos++;
                        while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                        boolean isDouble = false;
                        if (pos < s.length() && s.charAt(pos) == '.') {
                            isDouble = true;
                            pos++;
                            while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                        }
                        if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                            isDouble = true;
                            pos++;
                            if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
                            while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                        }
                        String num = s.substring(start, pos);
                        if (isDouble) return Double.parseDouble(num);
                        long l = Long.parseLong(num);
                        if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return (int) l;
                        return l;
                    }
                }

            %s

                public static int kof_ui_window_new(String title) {
                    return 1;
                }

                public static void kof_ui_window_set_title(int window, String title) {
                }

                public static String kof_ui_window_title(int window) {
                    return "";
                }

                public static void kof_ui_window_bind(int window, int label) {
                }

                public static void kof_ui_window_show(int window) {
                }

                public static void kof_ui_window_close(int window) {
                }

                public static int kof_ui_label_new(String text) {
                    return 1;
                }

                public static void kof_ui_label_set_text(int label, String text) {
                }

                public static String kof_ui_label_text(int label) {
                    return "";
                }

                public static void kof_ui_label_remove(int label) {
                }

                public static String kof_ui_color_to_css(int color) {
                    int r = (color >>> 24) & 0xFF;
                    int g = (color >>> 16) & 0xFF;
                    int b = (color >>> 8) & 0xFF;
                    int a = color & 0xFF;
                    if (a == 255) {
                        return "rgb(" + r + ", " + g + ", " + b + ")";
                    }
                    return "rgba(" + r + ", " + g + ", " + b + ", " + a + ")";
                }

                // ── kof.time ───────────────────────────────────────

                public static long kof_now() {
                    return System.currentTimeMillis();
                }

                // ── kof.io ─────────────────────────────────────────

                public static String kof_read_line() {
                    try {
                        return new java.io.BufferedReader(new java.io.InputStreamReader(System.in)).readLine();
                    } catch (java.io.IOException e) {
                        return null;
                    }
                }

                public static String kof_read_file(String path) {
                    try {
                        return java.nio.file.Files.readString(java.nio.file.Path.of(path));
                    } catch (java.io.IOException e) {
                        return null;
                    }
                }

                public static int kof_write_file(String path, String content) {
                    try {
                        java.nio.file.Files.writeString(java.nio.file.Path.of(path), content);
                        return 0;
                    } catch (java.io.IOException e) {
                        return -1;
                    }
                }

                // ── kof.concurrent ─────────────────────────────────

                private static final java.util.concurrent.atomic.AtomicInteger KOF_ACTIVE_TASKS =
                        new java.util.concurrent.atomic.AtomicInteger();

                static {
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        while (KOF_ACTIVE_TASKS.get() > 0) {
                            Thread.onSpinWait();
                        }
                    }, "kof-wait-tasks"));
                }

                public static void kof_spawn(Object task) {
                    KOF_ACTIVE_TASKS.incrementAndGet();
                    Thread.startVirtualThread(() -> {
                        try {
                            task.getClass().getMethod("invoke").invoke(task);
                        } catch (Exception e) {
                            Throwable cause = e.getCause() != null ? e.getCause() : e;
                            System.err.println("spawn task failed: " + cause.getMessage());
                        } finally {
                            KOF_ACTIVE_TASKS.decrementAndGet();
                        }
                    });
                }

                // ── kof.io — File / Path / Directory ──────────────

                private static java.nio.file.Path p(String path) {
                    return java.nio.file.Path.of(path);
                }

                public static int kof_io_file_exists(String path) {
                    return java.nio.file.Files.exists(p(path)) ? 1 : 0;
                }

                public static int kof_io_file_is_file(String path) {
                    return java.nio.file.Files.isRegularFile(p(path)) ? 1 : 0;
                }

                public static int kof_io_file_is_dir(String path) {
                    return java.nio.file.Files.isDirectory(p(path)) ? 1 : 0;
                }

                public static String kof_io_read_text(String path) {
                    try {
                        return java.nio.file.Files.readString(p(path), java.nio.charset.StandardCharsets.UTF_8);
                    } catch (java.io.IOException e) {
                        return null;
                    }
                }

                public static int kof_io_write_text(String path, String content) {
                    try {
                        java.nio.file.Files.writeString(p(path), content, java.nio.charset.StandardCharsets.UTF_8);
                        return 1;
                    } catch (java.io.IOException e) {
                        return 0;
                    }
                }

                public static int kof_io_append_text(String path, String content) {
                    try {
                        java.nio.file.Files.writeString(p(path), content, java.nio.charset.StandardCharsets.UTF_8,
                                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                        return 1;
                    } catch (java.io.IOException e) {
                        return 0;
                    }
                }

                public static int[] kof_io_read_bytes(String path) {
                    try {
                        byte[] b = java.nio.file.Files.readAllBytes(p(path));
                        int[] out = new int[b.length];
                        for (int i = 0; i < b.length; i++) out[i] = b[i] & 0xFF;
                        return out;
                    } catch (java.io.IOException e) {
                        return null;
                    }
                }

                public static int kof_io_write_bytes(String path, int[] bytes) {
                    try {
                        byte[] b = new byte[bytes.length];
                        for (int i = 0; i < bytes.length; i++) b[i] = (byte) (bytes[i] & 0xFF);
                        java.nio.file.Files.write(p(path), b);
                        return 1;
                    } catch (java.io.IOException e) {
                        return 0;
                    }
                }

                public static int kof_io_append_bytes(String path, int[] bytes) {
                    try {
                        byte[] b = new byte[bytes.length];
                        for (int i = 0; i < bytes.length; i++) b[i] = (byte) (bytes[i] & 0xFF);
                        java.nio.file.Files.write(p(path), b,
                                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                        return 1;
                    } catch (java.io.IOException e) {
                        return 0;
                    }
                }

                public static int kof_io_delete(String path) {
                    try {
                        if (!java.nio.file.Files.exists(p(path))) return 0;
                        java.nio.file.Files.deleteIfExists(p(path));
                        return 1;
                    } catch (java.io.IOException e) {
                        return 0;
                    }
                }

                public static long kof_io_file_size(String path) {
                    try {
                        return java.nio.file.Files.size(p(path));
                    } catch (java.io.IOException e) {
                        return -1;
                    }
                }

                public static String kof_io_file_name(String path) {
                    java.nio.file.Path pp = p(path).getFileName();
                    return pp == null ? path : pp.toString();
                }

                public static String kof_io_path_resolve(String base, String child) {
                    return p(base).resolve(child).toString();
                }

                public static String kof_io_path_parent(String path) {
                    java.nio.file.Path pp = p(path).getParent();
                    return pp == null ? null : pp.toString();
                }

                public static String kof_io_path_file_name(String path) {
                    return kof_io_file_name(path);
                }

                public static String kof_io_path_extension(String path) {
                    String name = kof_io_file_name(path);
                    int dot = name.lastIndexOf('.');
                    return dot <= 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1);
                }

                public static String kof_io_path_normalize(String path) {
                    String n = p(path).normalize().toString();
                    return n.isEmpty() ? "." : n;
                }

                public static int kof_io_path_is_absolute(String path) {
                    return p(path).isAbsolute() ? 1 : 0;
                }

                public static String kof_io_path_to_absolute(String path) {
                    return p(path).toAbsolutePath().toString();
                }

                public static int kof_io_dir_create(String path) {
                    try {
                        java.nio.file.Files.createDirectory(p(path));
                        return 1;
                    } catch (java.io.IOException e) {
                        return 0;
                    }
                }

                public static int kof_io_dir_create_dirs(String path) {
                    try {
                        java.nio.file.Files.createDirectories(p(path));
                        return 1;
                    } catch (java.io.IOException e) {
                        return 0;
                    }
                }

                public static int kof_io_dir_delete(String path) {
                    try {
                        if (!java.nio.file.Files.exists(p(path))) return 0;
                        java.nio.file.Files.deleteIfExists(p(path));
                        return 1;
                    } catch (java.io.IOException e) {
                        return 0;
                    }
                }

                public static java.util.ArrayList<String> kof_io_dir_list(String path) {
                    try (var stream = java.nio.file.Files.list(p(path))) {
                        return stream.map(java.nio.file.Path::getFileName)
                                .map(java.nio.file.Path::toString).sorted()
                                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
                    } catch (java.io.IOException e) {
                        return null;
                    }
                }

                // ── kof.web — native web stack ────────────────────

                private static final java.util.concurrent.ConcurrentHashMap<String, WebApp> KOF_WEB_APPS =
                        new java.util.concurrent.ConcurrentHashMap<>();
                private static final java.util.concurrent.atomic.AtomicInteger KOF_WEB_SEQ =
                        new java.util.concurrent.atomic.AtomicInteger();
                private static final ThreadLocal<WebRequest> KOF_WEB_REQUEST = new ThreadLocal<>();

                public static final class WebRoute {
                    final String method;
                    final String[] segments;
                    final boolean[] params;
                    final Object handler;

                    WebRoute(String method, String path, Object handler) {
                        this.method = method;
                        String[] raw = path.split("/");
                        this.segments = new String[raw.length];
                        this.params = new boolean[raw.length];
                        for (int i = 0; i < raw.length; i++) {
                            this.segments[i] = raw[i];
                            this.params[i] = raw[i].startsWith(":");
                        }
                        this.handler = handler;
                    }
                }

                public static final class WebRequest {
                    final String method;
                    final String path;
                    final String query;
                    final String rawHeaders;
                    final String body;
                    final java.util.Map<String, String> params = new java.util.HashMap<>();
                    final java.util.Map<String, String> queryParams = new java.util.HashMap<>();
                    final java.util.Map<String, String> headers = new java.util.HashMap<>();

                    WebRequest(String method, String path, String query, String rawHeaders, String body) {
                        this.method = method;
                        this.path = path;
                        this.query = query;
                        this.rawHeaders = rawHeaders;
                        this.body = body;
                        if (!query.isEmpty()) {
                            for (String pair : query.split("&")) {
                                int eq = pair.indexOf('=');
                                if (eq < 0) queryParams.put(pair, "");
                                else queryParams.put(pair.substring(0, eq), pair.substring(eq + 1));
                            }
                        }
                        String[] lines = rawHeaders.split("\\r\\n");
                        for (int i = 1; i < lines.length; i++) {
                            int colon = lines[i].indexOf(':');
                            if (colon > 0) {
                                headers.put(lines[i].substring(0, colon).trim().toLowerCase(),
                                        lines[i].substring(colon + 1).trim());
                            }
                        }
                    }

                    String param(String name) {
                        return params.get(name);
                    }

                    String query(String name) {
                        return queryParams.get(name);
                    }

                    String header(String name) {
                        return headers.get(name.toLowerCase());
                    }
                }

                public static final class WebApp {
                    final String id;
                    final java.util.List<WebRoute> routes = new java.util.ArrayList<>();
                    final java.util.List<Object> middlewares = new java.util.ArrayList<>();
                    volatile java.net.ServerSocket serverSocket;
                    volatile boolean running;

                    WebApp(String id) {
                        this.id = id;
                    }
                }

                public static String kof_web_app_new() {
                    String id = "app" + KOF_WEB_SEQ.incrementAndGet();
                    KOF_WEB_APPS.put(id, new WebApp(id));
                    return id;
                }

                private static WebApp kof_web_app(String appId) {
                    WebApp app = KOF_WEB_APPS.get(appId);
                    if (app == null) throw new IllegalArgumentException("unknown web app: " + appId);
                    return app;
                }

                public static void kof_web_route(String appId, String method, String path, Object handler) {
                    if (handler == null) throw new IllegalArgumentException("route handler is null");
                    kof_web_app(appId).routes.add(new WebRoute(method.toUpperCase(), path, handler));
                }

                public static void kof_web_use(String appId, Object handler) {
                    if (handler == null) throw new IllegalArgumentException("middleware is null");
                    kof_web_app(appId).middlewares.add(handler);
                }

                public static int kof_web_port(String appId) {
                    java.net.ServerSocket ss = kof_web_app(appId).serverSocket;
                    return ss == null ? -1 : ss.getLocalPort();
                }

                public static void kof_web_close(String appId) {
                    WebApp app = kof_web_app(appId);
                    app.running = false;
                    if (app.serverSocket != null) {
                        try {
                            app.serverSocket.close();
                        } catch (java.io.IOException ignored) {
                        }
                    }
                }

                public static void kof_web_listen(String appId, int port) {
                    WebApp app = kof_web_app(appId);
                    if (app.serverSocket != null) {
                        throw new IllegalStateException("app already listening: " + appId);
                    }
                    try {
                        app.serverSocket = new java.net.ServerSocket(port, 64,
                                java.net.InetAddress.getByName("0.0.0.0"));
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("cannot bind port " + port + ": " + e.getMessage(), e);
                    }
                    app.running = true;
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> kof_web_close(appId)));
                    while (app.running) {
                        try {
                            java.net.Socket client = app.serverSocket.accept();
                            client.setSoTimeout(15000);
                            Thread.startVirtualThread(() -> kof_web_handle(app, client));
                        } catch (java.io.IOException e) {
                            if (!app.running) break;
                        }
                    }
                }

                private static void kof_web_handle(WebApp app, java.net.Socket client) {
                    try (client) {
                        WebRequest req = readRequest(client.getInputStream());
                        String response = kof_web_dispatch(app, req);
                        client.getOutputStream().write(response.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        client.getOutputStream().flush();
                    } catch (Exception e) {
                        System.err.println("kof web connection error: " + e.getMessage());
                    }
                }

                private static String kof_web_dispatch(WebApp app, WebRequest req) {
                    KOF_WEB_REQUEST.set(req);
                    try {
                        for (Object middleware : app.middlewares) {
                            Object result = kof_web_invoke(middleware, req);
                            if (result != null) {
                                return kof_web_build(200, "OK", String.valueOf(result));
                            }
                        }
                        for (WebRoute route : app.routes) {
                            if (!route.method.equals(req.method)) continue;
                            String[] pathSegs = req.path.split("/");
                            if (pathSegs.length != route.segments.length) continue;
                            boolean match = true;
                            java.util.Map<String, String> params = new java.util.HashMap<>();
                            for (int i = 0; i < pathSegs.length; i++) {
                                if (route.params[i]) {
                                    params.put(route.segments[i].substring(1), pathSegs[i]);
                                } else if (!route.segments[i].equals(pathSegs[i])) {
                                    match = false;
                                    break;
                                }
                            }
                            if (!match) continue;
                            req.params.putAll(params);
                            Object result = kof_web_invoke(route.handler, req);
                            if (result == null) {
                                return kof_web_build(404, "Not Found", "{\\"error\\": \\"not found\\"}");
                            }
                            return kof_web_build(200, "OK", String.valueOf(result));
                        }
                        return kof_web_build(404, "Not Found", "{\\"error\\": \\"not found\\"}");
                    } catch (Exception e) {
                        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                        return kof_web_build(500, "Internal Server Error",
                                "{\\"error\\": \\"handler error: " + msg + "\\"}");
                    } finally {
                        KOF_WEB_REQUEST.remove();
                    }
                }

                private static Object kof_web_invoke(Object target, WebRequest req) throws Exception {
                    try {
                        return target.getClass().getMethod("invoke").invoke(target);
                    } catch (NoSuchMethodException e) {
                        return target.getClass()
                                .getMethod("invoke", String.class, String.class, String.class,
                                        String.class, String.class)
                                .invoke(target, req.method, req.path, req.body, req.query, req.rawHeaders);
                    }
                }

                private static String kof_web_build(int status, String statusText, String body) {
                    byte[] bodyBytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    String contentType = "text/plain; charset=utf-8";
                    String trimmed = body.trim();
                    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                        contentType = "application/json; charset=utf-8";
                    }
                    return "HTTP/1.1 " + status + " " + statusText + "\\r\\n"
                            + "Content-Type: " + contentType + "\\r\\n"
                            + "Content-Length: " + bodyBytes.length + "\\r\\n"
                            + "Connection: close\\r\\n"
                            + "\\r\\n"
                            + body;
                }

                private static WebRequest readRequest(java.io.InputStream in) throws java.io.IOException {
                    StringBuilder head = new StringBuilder();
                    byte[] buffer = new byte[8192];
                    int headerEnd = -1;
                    while (true) {
                        int n = in.read(buffer);
                        if (n == -1) throw new java.io.IOException("connection closed before headers");
                        head.append(new String(buffer, 0, n, java.nio.charset.StandardCharsets.UTF_8));
                        headerEnd = head.indexOf("\\r\\n\\r\\n");
                        if (headerEnd >= 0) break;
                        if (head.length() > 65536) throw new java.io.IOException("headers too large");
                    }

                    String requestText = head.toString();
                    String headerBlock = requestText.substring(0, headerEnd);
                    StringBuilder body = new StringBuilder(requestText.substring(headerEnd + 4));

                    int contentLength = 0;
                    for (String line : headerBlock.split("\\r\\n")) {
                        if (line.toLowerCase().startsWith("content-length:")) {
                            try {
                                contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                    while (body.length() < contentLength) {
                        int n = in.read(buffer);
                        if (n == -1) break;
                        body.append(new String(buffer, 0, n, java.nio.charset.StandardCharsets.UTF_8));
                    }
                    if (body.length() > contentLength) {
                        body.setLength(contentLength);
                    }

                    String[] lines = headerBlock.split("\\r\\n");
                    String[] parts = lines.length > 0 ? lines[0].split(" ") : new String[0];
                    String method = parts.length > 0 ? parts[0] : "GET";
                    String fullPath = parts.length > 1 ? parts[1] : "/";
                    String path = fullPath;
                    String query = "";
                    int q = fullPath.indexOf('?');
                    if (q >= 0) {
                        path = fullPath.substring(0, q);
                        query = fullPath.substring(q + 1);
                    }
                    return new WebRequest(method, path, query, headerBlock, body.toString());
                }

                public static String kof_web_param(String name) {
                    WebRequest req = KOF_WEB_REQUEST.get();
                    return req == null ? null : req.param(name);
                }

                public static String kof_web_query(String name) {
                    WebRequest req = KOF_WEB_REQUEST.get();
                    return req == null ? null : req.query(name);
                }

                public static String kof_web_header(String name) {
                    WebRequest req = KOF_WEB_REQUEST.get();
                    return req == null ? null : req.header(name);
                }

                public static String kof_web_body() {
                    WebRequest req = KOF_WEB_REQUEST.get();
                    return req == null ? null : req.body;
                }

                public static String kof_web_method() {
                    WebRequest req = KOF_WEB_REQUEST.get();
                    return req == null ? null : req.method;
                }

                public static String kof_web_path() {
                    WebRequest req = KOF_WEB_REQUEST.get();
                    return req == null ? null : req.path;
                }

                // ── kof.security (docs/security.md §5) ──────────────────

                private static final java.security.SecureRandom KOF_SEC_RANDOM = new java.security.SecureRandom();
                private static volatile String KOF_AUTH_SECRET = System.getenv("KOF_JWT_SECRET");
                private static final ThreadLocal<String> KOF_AUTH_CLAIMS = new ThreadLocal<>();
                private static final ThreadLocal<String> KOF_CSRF_TOKEN = new ThreadLocal<>();
                private static final int KOF_PBKDF2_ITERATIONS = 600_000;

                private static final char[] KOF_SEC_HEX = "0123456789abcdef".toCharArray();

                private static String kof_sec_hex(byte[] bytes) {
                    StringBuilder sb = new StringBuilder(bytes.length * 2);
                    for (byte b : bytes) {
                        sb.append(KOF_SEC_HEX[(b >> 4) & 0xF]);
                        sb.append(KOF_SEC_HEX[b & 0xF]);
                    }
                    return sb.toString();
                }

                private static byte[] kof_sec_fromHex(String hex) {
                    if (hex == null || (hex.length() & 1) != 0) throw new IllegalArgumentException("invalid hex");
                    byte[] out = new byte[hex.length() / 2];
                    for (int i = 0; i < out.length; i++) {
                        out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
                    }
                    return out;
                }

                public static String kof_sec_sha256(String data) {
                    try {
                        return kof_sec_hex(java.security.MessageDigest.getInstance("SHA-256")
                                .digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    } catch (java.security.NoSuchAlgorithmException e) {
                        throw new RuntimeException(e);
                    }
                }

                public static String kof_sec_sha512(String data) {
                    try {
                        return kof_sec_hex(java.security.MessageDigest.getInstance("SHA-512")
                                .digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    } catch (java.security.NoSuchAlgorithmException e) {
                        throw new RuntimeException(e);
                    }
                }

                public static String kof_sec_hmac_sha256(String key, String data) {
                    try {
                        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                        mac.init(new javax.crypto.spec.SecretKeySpec(
                                key.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
                        return kof_sec_hex(mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                public static String kof_sec_random_hex(int bytes) {
                    if (bytes < 0 || bytes > 4096) throw new IllegalArgumentException("invalid length: " + bytes);
                    byte[] buf = new byte[bytes];
                    KOF_SEC_RANDOM.nextBytes(buf);
                    return kof_sec_hex(buf);
                }

                public static int kof_sec_random_int(int bound) {
                    if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
                    return KOF_SEC_RANDOM.nextInt(bound);
                }

                public static boolean kof_sec_constant_time_equals(String a, String b) {
                    if (a == null || b == null) return a == b;
                    return java.security.MessageDigest.isEqual(
                            a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }

                public static String kof_sec_redact(String value) {
                    if (value == null) return null;
                    if (value.length() <= 8) return "********";
                    return value.substring(0, 4) + "********" + value.substring(value.length() - 4);
                }

                public static String kof_sec_secret_get(String name) {
                    return System.getenv(name);
                }

                public static String kof_sec_secret_get_default(String name, String fallback) {
                    String v = System.getenv(name);
                    return v == null ? fallback : v;
                }

                // password hashing — pbkdf2$sha256$<iterations>$<saltB64>$<hashB64>

                public static String kof_sec_password_hash(String password) {
                    try {
                        byte[] salt = new byte[16];
                        KOF_SEC_RANDOM.nextBytes(salt);
                        javax.crypto.SecretKeyFactory f = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                        javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                                password.toCharArray(), salt, KOF_PBKDF2_ITERATIONS, 256);
                        byte[] dk = f.generateSecret(spec).getEncoded();
                        return "pbkdf2$sha256$" + KOF_PBKDF2_ITERATIONS + "$"
                                + java.util.Base64.getEncoder().encodeToString(salt) + "$"
                                + java.util.Base64.getEncoder().encodeToString(dk);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                public static boolean kof_sec_password_verify(String password, String hash) {
                    if (hash == null) return false;
                    String[] parts = hash.split("\\\\$");
                    if (parts.length != 5 || !"pbkdf2".equals(parts[0]) || !"sha256".equals(parts[1])) return false;
                    try {
                        int iterations = Integer.parseInt(parts[2]);
                        byte[] salt = java.util.Base64.getDecoder().decode(parts[3]);
                        byte[] expected = java.util.Base64.getDecoder().decode(parts[4]);
                        javax.crypto.SecretKeyFactory f = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                        javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                                password.toCharArray(), salt, iterations, expected.length * 8);
                        byte[] actual = f.generateSecret(spec).getEncoded();
                        return java.security.MessageDigest.isEqual(expected, actual);
                    } catch (Exception e) {
                        return false;
                    }
                }

                public static boolean kof_sec_password_needs_rehash(String hash) {
                    if (hash == null) return true;
                    String[] parts = hash.split("\\\\$");
                    if (parts.length != 5 || !"pbkdf2".equals(parts[0]) || !"sha256".equals(parts[1])) return true;
                    try {
                        return Integer.parseInt(parts[2]) < KOF_PBKDF2_ITERATIONS;
                    } catch (NumberFormatException e) {
                        return true;
                    }
                }

                // AES-GCM — aesgcm$<ivB64>$<ciphertextAndTagB64>

                public static String kof_sec_aesgcm_encrypt(String plaintext, String keyHex) {
                    try {
                        byte[] key = kof_sec_fromHex(keyHex);
                        if (key.length != 32) throw new IllegalArgumentException("AES-GCM key must be 32 bytes (64 hex chars)");
                        byte[] iv = new byte[12];
                        KOF_SEC_RANDOM.nextBytes(iv);
                        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
                        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"),
                                new javax.crypto.spec.GCMParameterSpec(128, iv));
                        byte[] ct = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        return "aesgcm$" + java.util.Base64.getEncoder().encodeToString(iv) + "$"
                                + java.util.Base64.getEncoder().encodeToString(ct);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                public static String kof_sec_aesgcm_decrypt(String ciphertext, String keyHex) {
                    try {
                        byte[] key = kof_sec_fromHex(keyHex);
                        if (key.length != 32) throw new IllegalArgumentException("AES-GCM key must be 32 bytes (64 hex chars)");
                        String[] parts = ciphertext.split("\\\\$");
                        if (parts.length != 3 || !"aesgcm".equals(parts[0])) throw new IllegalArgumentException("invalid ciphertext format");
                        byte[] iv = java.util.Base64.getDecoder().decode(parts[1]);
                        byte[] ct = java.util.Base64.getDecoder().decode(parts[2]);
                        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
                        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"),
                                new javax.crypto.spec.GCMParameterSpec(128, iv));
                        return new String(cipher.doFinal(ct), java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        throw new RuntimeException("decryption failed: " + e.getMessage());
                    }
                }

                // JWT — HS256 only; the algorithm is never taken from the token.

                private static String kof_sec_b64url(byte[] data) {
                    return java.util.Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(data);
                }

                private static byte[] kof_sec_b64urlDecode(String s) {
                    return java.util.Base64.getUrlDecoder().decode(s);
                }

                private static String kof_sec_jwt_sign(String headerB64, String payloadB64, String secret) {
                    try {
                        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                        mac.init(new javax.crypto.spec.SecretKeySpec(
                                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
                        return kof_sec_b64url(mac.doFinal(
                                (headerB64 + "." + payloadB64).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                public static String kof_sec_jwt_secret() {
                    String secret = System.getenv("KOF_JWT_SECRET");
                    if (secret != null && !secret.isBlank()) return secret;
                    return kof_sec_random_hex(32);
                }

                public static String kof_sec_jwt_create(String claimsJson, String secret) {
                    return kof_sec_jwt_create_ttl(claimsJson, secret, 3600);
                }

                public static String kof_sec_jwt_create_ttl(String claimsJson, String secret, int ttlSeconds) {
                    Object parsed = kof_json_parse(claimsJson);
                    if (!(parsed instanceof Map<?, ?>)) throw new IllegalArgumentException("JWT claims must be a JSON object");
                    int lastBrace = claimsJson.lastIndexOf('}');
                    if (lastBrace < 0) throw new IllegalArgumentException("JWT claims must be a JSON object");
                    String head = claimsJson.substring(0, lastBrace).trim();
                    String sep = head.isEmpty() || head.endsWith("{") ? "" : ",";
                    long now = System.currentTimeMillis() / 1000;
                    String payload = head + sep + "\\"iat\\":" + now + ",\\"exp\\":" + (now + ttlSeconds) + "}";
                    String headerB64 = kof_sec_b64url("{\\"alg\\":\\"HS256\\",\\"typ\\":\\"JWT\\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    String payloadB64 = kof_sec_b64url(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    return headerB64 + "." + payloadB64 + "." + kof_sec_jwt_sign(headerB64, payloadB64, secret);
                }

                public static String kof_sec_jwt_verify(String token, String secret) {
                    return kof_sec_jwt_verify_iss_aud(token, secret, null, null);
                }

                public static String kof_sec_jwt_verify_iss_aud(String token, String secret, String issuer, String audience) {
                    if (token == null || secret == null) throw new IllegalArgumentException("invalid token or secret");
                    String[] parts = token.split("\\\\.");
                    if (parts.length != 3) throw new IllegalArgumentException("malformed token");
                    try {
                        String headerJson = new String(kof_sec_b64urlDecode(parts[0]), java.nio.charset.StandardCharsets.UTF_8);
                        if (!headerJson.contains("\\"HS256\\"")) throw new IllegalArgumentException("algorithm not allowed");
                        String expected = kof_sec_jwt_sign(parts[0], parts[1], secret);
                        if (!kof_sec_constant_time_equals(expected, parts[2])) throw new IllegalArgumentException("invalid signature");
                        String payloadJson = new String(kof_sec_b64urlDecode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
                        Object parsed = kof_json_parse(payloadJson);
                        if (!(parsed instanceof Map<?, ?> claims)) throw new IllegalArgumentException("invalid payload");
                        Object exp = claims.get("exp");
                        if (exp instanceof Number n && n.longValue() * 1000 <= System.currentTimeMillis()) {
                            throw new IllegalArgumentException("token expired");
                        }
                        if (issuer != null) {
                            Object iss = claims.get("iss");
                            if (!(iss instanceof String s && s.equals(issuer))) {
                                throw new IllegalArgumentException("issuer mismatch");
                            }
                        }
                        if (audience != null) {
                            Object aud = claims.get("aud");
                            if (!(aud instanceof String s && s.equals(audience))) {
                                throw new IllegalArgumentException("audience mismatch");
                            }
                        }
                        return payloadJson;
                    } catch (IllegalArgumentException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new IllegalArgumentException("malformed token");
                    }
                }

                // Web security context (kof.security.auth) — request-scoped.

                public static boolean kof_sec_auth_secret(String secret) {
                    if (secret == null || secret.isBlank()) return false;
                    KOF_AUTH_SECRET = secret;
                    return true;
                }

                private static String kof_sec_auth_bearerToken() {
                    WebRequest req = KOF_WEB_REQUEST.get();
                    if (req == null) return null;
                    String auth = req.header("Authorization");
                    if (auth == null) return null;
                    if (auth.startsWith("Bearer ")) return auth.substring(7);
                    if (auth.startsWith("bearer ")) return auth.substring(7);
                    return auth;
                }

                private static boolean kof_sec_auth_resolve() {
                    String cached = KOF_AUTH_CLAIMS.get();
                    if (cached != null) return true;
                    String token = kof_sec_auth_bearerToken();
                    if (token == null || KOF_AUTH_SECRET == null || KOF_AUTH_SECRET.isBlank()) return false;
                    try {
                        KOF_AUTH_CLAIMS.set(kof_sec_jwt_verify(token, KOF_AUTH_SECRET));
                        return true;
                    } catch (IllegalArgumentException e) {
                        return false;
                    }
                }

                public static String kof_sec_auth_token() {
                    return kof_sec_auth_bearerToken();
                }

                public static boolean kof_sec_auth_authenticated() {
                    return kof_sec_auth_resolve();
                }

                public static String kof_sec_auth_claims() {
                    if (!kof_sec_auth_resolve()) return null;
                    return KOF_AUTH_CLAIMS.get();
                }

                public static String kof_sec_auth_user() {
                    Object claims = kof_sec_auth_claims();
                    if (claims == null) return null;
                    try {
                        Object parsed = kof_json_parse((String) claims);
                        if (parsed instanceof Map<?, ?> m && m.get("sub") instanceof String sub) return sub;
                    } catch (IllegalArgumentException ignored) {
                    }
                    return null;
                }

                public static boolean kof_sec_auth_has_role(String role) {
                    return kof_sec_auth_claimContains("roles", role);
                }

                public static boolean kof_sec_auth_has_permission(String permission) {
                    return kof_sec_auth_claimContains("permissions", permission);
                }

                private static boolean kof_sec_auth_claimContains(String claim, String value) {
                    Object claims = kof_sec_auth_claims();
                    if (claims == null) return false;
                    try {
                        Object parsed = kof_json_parse((String) claims);
                        if (!(parsed instanceof Map<?, ?> m)) return false;
                        Object v = m.get(claim);
                        if (v instanceof String s) return s.equals(value);
                        if (v instanceof List<?> list) {
                            for (Object item : list) {
                                if (item instanceof String s && s.equals(value)) return true;
                            }
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                    return false;
                }

                // CSRF / CORS / security headers

                public static String kof_sec_csrf_token() {
                    String existing = KOF_CSRF_TOKEN.get();
                    if (existing != null) return existing;
                    String token = kof_sec_random_hex(32);
                    KOF_CSRF_TOKEN.set(token);
                    return token;
                }

                public static boolean kof_sec_csrf_valid(String token) {
                    String expected = KOF_CSRF_TOKEN.get();
                    if (expected == null || token == null) return false;
                    return kof_sec_constant_time_equals(expected, token);
                }

                public static boolean kof_sec_cors_allowed(String origin, String allowed) {
                    if (allowed == null) return false;
                    if ("*".equals(allowed)) return true;
                    for (String a : allowed.split(",")) {
                        if (a.trim().equals(origin)) return true;
                    }
                    return false;
                }

                public static String kof_sec_csp_header() {
                    return "default-src 'self'; frame-ancestors 'none'; base-uri 'self'";
                }

                public static String kof_sec_hsts_header() {
                    return "max-age=31536000; includeSubDomains";
                }

                public static String kof_sec_content_type_options_header() {
                    return "nosniff";
                }

                public static String kof_sec_frame_header() {
                    return "DENY";
                }

                public static String kof_sec_referrer_header() {
                    return "no-referrer";
                }
            }
            """.formatted(decoders);
    }
}