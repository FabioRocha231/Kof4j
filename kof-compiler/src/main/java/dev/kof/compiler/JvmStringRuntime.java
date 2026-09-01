package dev.kof.compiler;

/**
 * Runtime do String → numérico (OBS-010) — gerado no KofRuntime junto com o JvmRuntime.
 * Separado num arquivo próprio porque o constant pool do
 * javac limita cada string a 65535 bytes.
 */
final class JvmStringRuntime {

    private JvmStringRuntime() {}

    static String source() {
        return """
                // ── String → numérico (OBS-010: toInt/toLong/toDouble/toFloat)
                // As conversões do String são funções do runtime Kof — o
                // java.lang.String não tem toInt().

                public static int kof_string_to_int(String s) {
                    return Integer.parseInt(s.trim());
                }

                public static long kof_string_to_long(String s) {
                    return Long.parseLong(s.trim());
                }

                public static double kof_string_to_double(String s) {
                    return Double.parseDouble(s.trim());
                }

                public static float kof_string_to_float(String s) {
                    return Float.parseFloat(s.trim());
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

                // ── kof.validation (G4) ─────────────────────────────────

                public static boolean kof_validation_required(String value) {
                    return value != null && !value.isEmpty();
                }

                public static boolean kof_validation_notBlank(String value) {
                    return value != null && !value.trim().isEmpty();
                }

                public static boolean kof_validation_minLength(String value, int min) {
                    return value != null && value.length() >= min;
                }

                public static boolean kof_validation_maxLength(String value, int max) {
                    return value != null && value.length() <= max;
                }

                public static boolean kof_validation_lengthBetween(String value, int min, int max) {
                    return value != null && value.length() >= min && value.length() <= max;
                }

                public static boolean kof_validation_isEmail(String value) {
                    if (value == null) return false;
                    if (value.indexOf(' ') >= 0 || value.indexOf(9) >= 0 || value.indexOf(10) >= 0) return false;
                    int at = value.indexOf('@');
                    if (at <= 0 || at != value.lastIndexOf('@') || at == value.length() - 1) return false;
                    String domain = value.substring(at + 1);
                    int dot = domain.indexOf('.');
                    if (dot <= 0 || dot == domain.length() - 1) return false;
                    return true;
                }

                public static boolean kof_validation_isUrl(String value) {
                    if (value == null) return false;
                    return value.startsWith("http://") || value.startsWith("https://");
                }

                public static boolean kof_validation_matches(String value, String pattern) {
                    if (value == null || pattern == null) return false;
                    try { return java.util.regex.Pattern.compile(pattern).matcher(value).find(); } catch (Exception e) { return false; }
                }

                public static boolean kof_validation_isInt(String value) {
                    if (value == null) return false;
                    try { Integer.parseInt(value.trim()); return true; } catch (Exception e) { return false; }
                }

                public static boolean kof_validation_isLong(String value) {
                    if (value == null) return false;
                    try { Long.parseLong(value.trim()); return true; } catch (Exception e) { return false; }
                }

                public static boolean kof_validation_inRange(int value, int min, int max) {
                    return value >= min && value <= max;
                }

                public static boolean kof_validation_min(int value, int min) {
                    return value >= min;
                }

                public static boolean kof_validation_max(int value, int max) {
                    return value <= max;
                }

                // ── kof.observability (G5) ────────────────────────────

                private static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> KOF_OBS_COUNTERS = new java.util.concurrent.ConcurrentHashMap<>();
                private static final java.util.concurrent.ConcurrentHashMap<String, Integer> KOF_OBS_GAUGES = new java.util.concurrent.ConcurrentHashMap<>();
                // histograma: name → [sum, count] (mutuamente sincronizado)
                private static final java.util.concurrent.ConcurrentHashMap<String, long[]> KOF_OBS_HISTOGRAMS = new java.util.concurrent.ConcurrentHashMap<>();

                public static String kof_observability_health() {
                    return "UP";
                }

                public static boolean kof_observability_readiness() {
                    return true;
                }

                public static boolean kof_observability_liveness() {
                    return true;
                }

                public static int kof_observability_counter(String name) {
                    if (name == null) name = "";
                    return KOF_OBS_COUNTERS.computeIfAbsent(name, k -> new java.util.concurrent.atomic.AtomicInteger(0)).incrementAndGet();
                }

                public static int kof_observability_increment(String name, int delta) {
                    if (name == null) name = "";
                    return KOF_OBS_COUNTERS.computeIfAbsent(name, k -> new java.util.concurrent.atomic.AtomicInteger(0)).addAndGet(delta);
                }

                public static void kof_observability_gauge(String name, int value) {
                    if (name == null) name = "";
                    KOF_OBS_GAUGES.put(name, value);
                }

                public static void kof_observability_histogram(String name, int value) {
                    if (name == null) name = "";
                    long[] entry = KOF_OBS_HISTOGRAMS.computeIfAbsent(name, k -> new long[2]);
                    synchronized (entry) {
                        entry[0] += value;      // sum
                        entry[1] += 1;          // count
                    }
                }

                /** Exporta counters, gauges e histograms em formato Prometheus
                 *  (text exposition format). Histogramas sem buckets: expostos
                 *  como name_count (counter) + name_sum (gauge). */
                public static String kof_observability_metrics() {
                    StringBuilder sb = new StringBuilder();
                    sb.append(sortedCounterLines(KOF_OBS_COUNTERS, "counter"));
                    sb.append(sortedIntLines(KOF_OBS_GAUGES, "gauge"));
                    // histogramas: sum/count por name
                    java.util.List<String> hnames = new java.util.ArrayList<>(KOF_OBS_HISTOGRAMS.keySet());
                    java.util.Collections.sort(hnames);
                    for (String base0 : hnames) {
                        String base = promName(base0, "");
                        long[] e = KOF_OBS_HISTOGRAMS.get(base0);
                        long sum, count;
                        synchronized (e) { sum = e[0]; count = e[1]; }
                        sb.append("# TYPE ").append(base).append("_count counter\\n");
                        sb.append(base).append("_count ").append(count).append('\\n');
                        sb.append("# TYPE ").append(base).append("_sum gauge\\n");
                        sb.append(base).append("_sum ").append(sum).append('\\n');
                    }
                    return sb.toString();
                }

                private static String sortedCounterLines(
                        java.util.Map<String, java.util.concurrent.atomic.AtomicInteger> m, String type) {
                    StringBuilder sb = new StringBuilder();
                    java.util.List<String> names = new java.util.ArrayList<>(m.keySet());
                    java.util.Collections.sort(names);
                    for (String k : names) {
                        String n = promName(k, "");
                        sb.append("# TYPE ").append(n).append(' ').append(type).append('\\n');
                        sb.append(n).append(' ').append(m.get(k).get()).append('\\n');
                    }
                    return sb.toString();
                }

                private static String sortedIntLines(java.util.Map<String, Integer> m, String type) {
                    StringBuilder sb = new StringBuilder();
                    java.util.List<String> names = new java.util.ArrayList<>(m.keySet());
                    java.util.Collections.sort(names);
                    for (String k : names) {
                        String n = promName(k, "");
                        sb.append("# TYPE ").append(n).append(' ').append(type).append('\\n');
                        sb.append(n).append(' ').append(m.get(k)).append('\\n');
                    }
                    return sb.toString();
                }

                /** Nome Prometheus: sanitiza para [a-zA-Z0-9_:] e acrescenta sufixo. */
                private static String promName(String name, String suffix) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < name.length(); i++) {
                        char c = name.charAt(i);
                        if (Character.isLetterOrDigit(c) || c == '_') sb.append(c);
                        else sb.append('_');
                    }
                    if (sb.length() == 0) sb.append("k");
                    sb.append(suffix);
                    return sb.toString();
                }

                public static String kof_observability_request_id() {
                    return java.util.UUID.randomUUID().toString();
                }

                public static String kof_observability_correlation_id() {
                    return kof_observability_request_id();
                }

                // ── kof.security G9 (rate limiting / sessions / API keys) ──

                private static final java.util.concurrent.ConcurrentHashMap<String, long[]> KOF_RATE_LIMIT = new java.util.concurrent.ConcurrentHashMap<>();
                private static final java.util.concurrent.ConcurrentHashMap<String, String> KOF_SESSIONS = new java.util.concurrent.ConcurrentHashMap<>();
                private static final java.util.concurrent.ConcurrentHashMap<String, Boolean> KOF_API_KEYS = new java.util.concurrent.ConcurrentHashMap<>();

                public static boolean kof_sec_rate_limit(String key, int limit, int windowSeconds) {
                    if (key == null) key = "";
                    if (limit <= 0 || windowSeconds <= 0) return false;
                    long now = System.currentTimeMillis();
                    long windowMillis = windowSeconds * 1000L;
                    long[] entry = KOF_RATE_LIMIT.computeIfAbsent(key, k -> new long[]{now, 0});
                    synchronized (entry) {
                        if (now - entry[0] >= windowMillis) {
                            entry[0] = now;
                            entry[1] = 1;
                            return true;
                        }
                        if (entry[1] < limit) {
                            entry[1]++;
                            return true;
                        }
                        return false;
                    }
                }

                public static String kof_sec_session_create(String data) {
                    String id = kof_sec_random_hex(16);
                    KOF_SESSIONS.put(id, data == null ? "" : data);
                    return id;
                }

                public static String kof_sec_session_get(String id) {
                    if (id == null) return null;
                    return KOF_SESSIONS.get(id);
                }

                public static boolean kof_sec_session_destroy(String id) {
                    if (id == null) return false;
                    return KOF_SESSIONS.remove(id) != null;
                }

                public static String kof_sec_api_key_generate() {
                    String key = kof_sec_random_hex(32);
                    KOF_API_KEYS.put(key, Boolean.TRUE);
                    return key;
                }

                public static boolean kof_sec_api_key_valid(String key) {
                    if (key == null) return false;
                    return KOF_API_KEYS.containsKey(key);
                }

                // ── higher-order em List (P1-residual) ─────────────────
                // Lambdas sintéticas expõem invoke(...) tipado; reflection
                // genérica localiza pelo arity com boxing automático.

                private static Object kof_ho_invoke(Object lambda, Object[] args) throws Exception {
                    for (var m : lambda.getClass().getMethods()) {
                        if (!m.getName().equals("invoke")) continue;
                        if (m.getParameterCount() != args.length) continue;
                        if (m.isSynthetic()) continue;
                        try { return m.invoke(lambda, args); } catch (IllegalArgumentException ignored) {}
                    }
                    throw new IllegalStateException("lambda invoke não encontrado (" + args.length + " args)");
                }

                public static java.util.ArrayList<Object> kof_list_map(
                        java.util.ArrayList<?> list, Object lambda) throws Exception {
                    var out = new java.util.ArrayList<Object>();
                    for (Object o : list) out.add(kof_ho_invoke(lambda, new Object[]{o}));
                    return out;
                }

                public static java.util.ArrayList<Object> kof_list_filter(
                        java.util.ArrayList<?> list, Object lambda) throws Exception {
                    var out = new java.util.ArrayList<Object>();
                    for (Object o : list) {
                        Object keep = kof_ho_invoke(lambda, new Object[]{o});
                        if (Boolean.TRUE.equals(keep) || Integer.valueOf(1).equals(keep)) out.add(o);
                    }
                    return out;
                }

                public static Object kof_list_reduce(
                        java.util.ArrayList<?> list, Object initial, Object lambda) throws Exception {
                    Object acc = initial;
                    for (Object o : list) acc = kof_ho_invoke(lambda, new Object[]{acc, o});
                    return acc;
                }

                // ── kof.enum (P1) ──────────────────────────────────────

                public static String kof_enum_value_of(java.util.List<?> values, String name) {
                    if (values != null && name != null) {
                        for (Object v : values) {
                            if (name.equals(v)) return (String) v;
                        }
                    }
                    return null;
                }

                // ── kof.tetris — hidden easter egg ────────────────────
                // `tetris.run()` starts a simplified terminal tetris.
                // Keys: a=left d=right s=down w=rotate space=hard drop
                //       q=quit. On POSIX the terminal switches to raw mode
                //       (stty) so single keystrokes work without Enter.

                public static void kof_tetris_run() {
                    final int COLS = 10;
                    final int ROWS = 20;
                    final int[][] board = new int[ROWS][COLS];
                    final int[][][] SHAPES = {
                            {{1, 1, 1, 1}},
                            {{1, 1}, {1, 1}},
                            {{0, 1, 0}, {1, 1, 1}},
                            {{0, 1, 1}, {1, 1, 0}},
                            {{1, 1, 0}, {0, 1, 1}},
                            {{1, 0, 0}, {1, 1, 1}},
                            {{0, 0, 1}, {1, 1, 1}}
                    };
                    final String ESC = "" + (char) 27;
                    final java.util.Random rnd = new java.util.Random(System.nanoTime());
                    final java.io.PrintStream out = System.out;

                    boolean raw = false;
                    try {
                        Process p = new ProcessBuilder("stty", "raw", "-echo")
                                .redirectErrorStream(true).start();
                        raw = p.waitFor() == 0;
                    } catch (Exception ignored) {
                    }
                    if (raw) {
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                            try {
                                new ProcessBuilder("stty", "sane").start().waitFor();
                            } catch (Exception ignored) {
                            }
                        }, "kof-tetris-restore"));
                    }

                    int[][] cur = SHAPES[rnd.nextInt(SHAPES.length)];
                    int cx = 4;
                    int cy = 0;
                    int score = 0;
                    int level = 1;
                    long dropAt = System.currentTimeMillis() + 600;
                    boolean over = false;

                    while (!over) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(ESC).append("[2J").append(ESC).append("[H");
                        sb.append("kof.tetris  score=").append(score)
                                .append("  level=").append(level);
                        sb.append((char) 10);
                        sb.append("+----------+").append((char) 10);
                        for (int y = 0; y < ROWS; y++) {
                            sb.append('|');
                            for (int x = 0; x < COLS; x++) {
                                boolean cell = board[y][x] != 0;
                                if (!cell) {
                                    for (int py = 0; py < cur.length; py++) {
                                        for (int px = 0; px < cur[py].length; px++) {
                                            if (cur[py][px] != 0 && cy + py == y && cx + px == x) {
                                                cell = true;
                                            }
                                        }
                                    }
                                }
                                sb.append(cell ? '#' : '.');
                            }
                            sb.append('|').append((char) 10);
                        }
                        sb.append("+----------+").append((char) 10);
                        sb.append("a:left d:right s:down w:rotate space:drop q:quit")
                                .append((char) 10);
                        out.print(sb);

                        long now = System.currentTimeMillis();
                        if (now >= dropAt) {
                            if (kof_tetris_fits(cur, cx, cy + 1, board)) {
                                cy++;
                            } else {
                                for (int y = 0; y < cur.length; y++) {
                                    for (int x = 0; x < cur[y].length; x++) {
                                        if (cur[y][x] != 0) {
                                            board[cy + y][cx + x] = 1;
                                        }
                                    }
                                }
                                int lines = kof_tetris_clear_lines(board);
                                if (lines > 0) {
                                    score += lines * 100 * level;
                                    level = 1 + score / 1000;
                                }
                                cur = SHAPES[rnd.nextInt(SHAPES.length)];
                                cx = 4;
                                cy = 0;
                                if (!kof_tetris_fits(cur, cx, cy, board)) {
                                    over = true;
                                    break;
                                }
                            }
                            dropAt = now + kof_tetris_drop_ms(level);
                        }

                        int key = kof_tetris_key();
                        if (key == 'q' || key == -1) {
                            break;
                        }
                        if (key == 'a' && kof_tetris_fits(cur, cx - 1, cy, board)) {
                            cx--;
                        }
                        if (key == 'd' && kof_tetris_fits(cur, cx + 1, cy, board)) {
                            cx++;
                        }
                        if (key == 's' && kof_tetris_fits(cur, cx, cy + 1, board)) {
                            cy++;
                        }
                        if (key == ' ') {
                            while (kof_tetris_fits(cur, cx, cy + 1, board)) {
                                cy++;
                            }
                        }
                        if (key == 'w') {
                            int[][] rotated = kof_tetris_rotate(cur);
                            int rcx = cx;
                            if (!kof_tetris_fits(rotated, rcx, cy, board)) {
                                rcx = cx - 1;
                            }
                            if (!kof_tetris_fits(rotated, rcx, cy, board)) {
                                rcx = cx + 1;
                            }
                            if (kof_tetris_fits(rotated, rcx, cy, board)) {
                                cur = rotated;
                                cx = rcx;
                            }
                        }

                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException ignored) {
                        }
                    }

                    out.print(ESC + "[2J" + ESC + "[H");
                    if (over) {
                        out.println("GAME OVER  score=" + score + "  level=" + level);
                    } else {
                        out.println("kof.tetris  score=" + score + "  bye");
                    }
                }

                private static boolean kof_tetris_fits(int[][] s, int bx, int by, int[][] board) {
                    for (int y = 0; y < s.length; y++) {
                        for (int x = 0; x < s[y].length; x++) {
                            if (s[y][x] == 0) {
                                continue;
                            }
                            int gx = bx + x;
                            int gy = by + y;
                            if (gx < 0 || gx >= 10 || gy >= 20) {
                                return false;
                            }
                            if (gy >= 0 && board[gy][gx] != 0) {
                                return false;
                            }
                        }
                    }
                    return true;
                }

                private static int[][] kof_tetris_rotate(int[][] s) {
                    int h = s.length;
                    int w = s[0].length;
                    int[][] r = new int[w][h];
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            r[x][h - 1 - y] = s[y][x];
                        }
                    }
                    return r;
                }

                private static int kof_tetris_clear_lines(int[][] board) {
                    int rows = 0;
                    for (int y = 0; y < 20; y++) {
                        boolean full = true;
                        for (int x = 0; x < 10; x++) {
                            if (board[y][x] == 0) {
                                full = false;
                                break;
                            }
                        }
                        if (full) {
                            rows++;
                        }
                    }
                    if (rows == 0) {
                        return 0;
                    }
                    int[][] next = new int[20][10];
                    int dst = 19;
                    for (int y = 19; y >= 0; y--) {
                        boolean full = true;
                        for (int x = 0; x < 10; x++) {
                            if (board[y][x] == 0) {
                                full = false;
                                break;
                            }
                        }
                        if (full) {
                            continue;
                        }
                        for (int x = 0; x < 10; x++) {
                            next[dst][x] = board[y][x];
                        }
                        dst--;
                    }
                    for (int y = 0; y < 20; y++) {
                        for (int x = 0; x < 10; x++) {
                            board[y][x] = next[y][x];
                        }
                    }
                    return rows;
                }

                private static int kof_tetris_drop_ms(int level) {
                    int ms = 600 - (level - 1) * 40;
                    return ms < 100 ? 100 : ms;
                }

                private static int kof_tetris_key() {
                    try {
                        java.io.InputStream in = System.in;
                        if (in.available() > 0) {
                            return in.read();
                        }
                    } catch (java.io.IOException ignored) {
                    }
                    return 0;
                }
""";
    }
}
