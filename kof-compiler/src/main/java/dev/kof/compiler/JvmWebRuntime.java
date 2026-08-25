package dev.kof.compiler;

import java.util.List;

/**
 * Runtime do kof.web + kof.http — gerado no KofRuntime junto com o
 * JvmRuntime. Separado num arquivo próprio porque o constant pool do
 * javac limita cada string a 65535 bytes.
 */
final class JvmWebRuntime {

    private JvmWebRuntime() {}

    static String source() {
        return """
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

                // ── kof.http — HTTP client (JDK java.net.http) ───────────
                private static final java.util.concurrent.atomic.AtomicInteger KOF_HTTP_TIMEOUT =
                        new java.util.concurrent.atomic.AtomicInteger(15);

                public static void kof_http_timeout_set(int seconds) {
                    KOF_HTTP_TIMEOUT.set(seconds);
                }

                public static String kof_http_get(String url) throws Exception {
                    return kof_http_request(url, "GET", null, null);
                }

                public static String kof_http_get_headers(String url, String headers) throws Exception {
                    return kof_http_request(url, "GET", headers, null);
                }

                public static String kof_http_delete(String url) throws Exception {
                    return kof_http_request(url, "DELETE", null, null);
                }

                public static String kof_http_delete_headers(String url, String headers) throws Exception {
                    return kof_http_request(url, "DELETE", headers, null);
                }

                public static String kof_http_options(String url) throws Exception {
                    return kof_http_request(url, "OPTIONS", null, null);
                }

                public static String kof_http_options_headers(String url, String headers) throws Exception {
                    return kof_http_request(url, "OPTIONS", headers, null);
                }

                public static String kof_http_post(String url, String body) throws Exception {
                    return kof_http_request(url, "POST", null, body);
                }

                public static String kof_http_post_headers(String url, String body, String headers) throws Exception {
                    return kof_http_request(url, "POST", headers, body);
                }

                public static String kof_http_put(String url, String body) throws Exception {
                    return kof_http_request(url, "PUT", null, body);
                }

                public static String kof_http_put_headers(String url, String body, String headers) throws Exception {
                    return kof_http_request(url, "PUT", headers, body);
                }

                public static String kof_http_patch(String url, String body) throws Exception {
                    return kof_http_request(url, "PATCH", null, body);
                }

                public static String kof_http_patch_headers(String url, String body, String headers) throws Exception {
                    return kof_http_request(url, "PATCH", headers, body);
                }

                public static int kof_http_status(String url) throws Exception {
                    java.net.http.HttpRequest.Builder b = java.net.http.HttpRequest.newBuilder(
                            java.net.URI.create(url))
                            .timeout(java.time.Duration.ofSeconds(KOF_HTTP_TIMEOUT.get()))
                            .method("GET", java.net.http.HttpRequest.BodyPublishers.noBody());
                    java.net.http.HttpResponse<String> r = java.net.http.HttpClient.newHttpClient()
                            .send(b.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                    return r.statusCode();
                }

                private static String kof_http_request(String url, String method, String headers, String body)
                        throws Exception {
                    java.net.http.HttpRequest.Builder b = java.net.http.HttpRequest.newBuilder(
                            java.net.URI.create(url))
                            .timeout(java.time.Duration.ofSeconds(KOF_HTTP_TIMEOUT.get()));
                    if (headers != null && !headers.isBlank()) {
                        for (String line : headers.split("\\n")) {
                            int c = line.indexOf(':');
                            if (c > 0) {
                                b.header(line.substring(0, c).trim(), line.substring(c + 1).trim());
                            }
                        }
                    }
                    if (body != null) {
                        b.method(method, java.net.http.HttpRequest.BodyPublishers.ofString(body));
                    } else {
                        b.method(method, java.net.http.HttpRequest.BodyPublishers.noBody());
                    }
                    java.net.http.HttpResponse<String> r = java.net.http.HttpClient.newHttpClient()
                            .send(b.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                    return r.body();
                }

                // ── kof.mq — messageria em memória (pub/sub + filas) ─────
                private static final java.util.concurrent.ConcurrentHashMap<String,
                        java.util.concurrent.CopyOnWriteArrayList<Object>> KOF_MQ_SUBS =
                        new java.util.concurrent.ConcurrentHashMap<>();
                private static final java.util.concurrent.atomic.AtomicInteger KOF_MQ_SEQ =
                        new java.util.concurrent.atomic.AtomicInteger();
                private static final java.util.concurrent.ConcurrentHashMap<String,
                        java.util.concurrent.ArrayBlockingQueue<Object>> KOF_MQ_QUEUES =
                        new java.util.concurrent.ConcurrentHashMap<>();

                public static void kof_mq_publish(String topic, Object msg) {
                    java.util.List<Object> subs = KOF_MQ_SUBS.get(topic);
                    if (subs != null) {
                        for (Object fn : subs) {
                            try {
                                fn.getClass().getMethod("invoke", Object.class).invoke(fn, msg);
                            } catch (java.lang.reflect.InvocationTargetException e) {
                                if (e.getCause() instanceof RuntimeException re) throw re;
                                throw new RuntimeException(e.getCause());
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }

                public static void kof_mq_subscribe(String topic, Object fn) {
                    KOF_MQ_SUBS.computeIfAbsent(topic, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                            .add(fn);
                }

                public static void kof_mq_unsubscribe(String topic, Object fn) {
                    java.util.List<Object> subs = KOF_MQ_SUBS.get(topic);
                    if (subs != null) {
                        subs.remove(fn);
                    }
                }

                public static String kof_mq_queue() {
                    return "mq-" + KOF_MQ_SEQ.incrementAndGet();
                }

                public static void kof_mq_push(String q, Object item) {
                    KOF_MQ_QUEUES.computeIfAbsent(q,
                            k -> new java.util.concurrent.ArrayBlockingQueue<>(1024)).add(item);
                }

                public static Object kof_mq_pop(String q) {
                    java.util.concurrent.ArrayBlockingQueue<Object> queue = KOF_MQ_QUEUES.get(q);
                    return queue == null ? null : queue.poll();
                }

                public static int kof_mq_queue_size(String q) {
                    java.util.concurrent.ArrayBlockingQueue<Object> queue = KOF_MQ_QUEUES.get(q);
                    return queue == null ? 0 : queue.size();
                }

""";
    }
}
