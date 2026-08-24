package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;


/**
 * End-to-end tests for the Kof-native logging module ({@code kof.log}).
 *
 * Level control via {@code KOF_LOG_LEVEL} (debug < info < warn < error < off;
 * default info). info/debug go to stdout; warn/error go to stderr.
 */
class KofLogE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String LOG_PROGRAM = """
            main() {
                log.debug("detail message")
                log.info("hello from kof")
                log.warn("careful")
                log.error("boom")
            }
            """;

    private String[] run(Path tempDir, String kofSource, String level) throws IOException {
        return run(tempDir, kofSource, level, null);
    }

    private String[] run(Path tempDir, String kofSource, String level,
                         Map<String, String> extraEnv) throws IOException {
        Path source = tempDir.resolve("Log.kf");
        Files.writeString(source, kofSource);
        Path outDir = tempDir.resolve("classes");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(false);
            if (level != null) pb.environment().put("KOF_LOG_LEVEL", level);
            if (extraEnv != null) {
                for (var e : extraEnv.entrySet()) pb.environment().put(e.getKey(), e.getValue());
            }
            Process p = pb.start();
            String stdout = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            String stderr = new String(p.getErrorStream().readAllBytes());
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, stderr: '" + stderr + "'");
            return new String[]{stdout, stderr};
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    private static final Pattern LINE = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} (DEBUG|INFO|WARN|ERROR) .*");

    @Test
    void defaultLevelIsInfo(@TempDir Path tempDir) throws IOException {
        String[] out = run(tempDir, LOG_PROGRAM, null);
        assertTrue(LINE.matcher(out[0].trim()).matches(), "stdout: " + out[0]);
        assertTrue(out[0].trim().endsWith("INFO hello from kof"), out[0]);
        assertFalse(out[0].contains("detail message"), "debug should be suppressed by default");
        assertTrue(out[1].contains("WARN careful"), out[1]);
        assertTrue(out[1].contains("ERROR boom"), out[1]);
    }

    @Test
    void debugLevelShowsDebug(@TempDir Path tempDir) throws IOException {
        String[] out = run(tempDir, LOG_PROGRAM, "debug");
        assertTrue(out[0].contains("DEBUG detail message"), out[0]);
        assertTrue(out[0].contains("INFO hello from kof"), out[0]);
    }

    @Test
    void errorLevelSuppressesInfo(@TempDir Path tempDir) throws IOException {
        String[] out = run(tempDir, LOG_PROGRAM, "error");
        assertFalse(out[0].contains("hello from kof"), out[0]);
        assertTrue(out[1].contains("ERROR boom"), out[1]);
    }

    @Test
    void offSuppressesEverything(@TempDir Path tempDir) throws IOException {
        String[] out = run(tempDir, LOG_PROGRAM, "off");
        assertEquals("", out[0].trim());
        assertEquals("", out[1].trim());
    }

    @Test
    void warnGoesToStderr(@TempDir Path tempDir) throws IOException {
        String[] out = run(tempDir, """
                main() {
                    log.warn("to stderr")
                }
                """, null);
        assertEquals("", out[0].trim());
        assertTrue(out[1].contains("WARN to stderr"), out[1]);
    }

    @Test
    void logInsideWebHandler(@TempDir Path tempDir) throws IOException {
        // logging works inside web handlers (same generated runtime)
        int port = freePort();
        Path source = tempDir.resolve("Web.kf");
        Files.writeString(source, """
                main() {
                    var app = web.app()
                    app.get("/log") {
                        log.info("handler called")
                        return "done"
                    }
                    app.listen(PORT)
                }
                """.replace("PORT", String.valueOf(port)));
        Path outDir = tempDir.resolve("classes");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), result.diagnostics().getDiagnostics().toString());
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process server = pb.start();
        StringBuilder serverOut = new StringBuilder();
        Thread drain = new Thread(() -> {
            try {
                byte[] buffer = new byte[4096];
                int n;
                while ((n = server.getInputStream().read(buffer)) != -1) {
                    serverOut.append(new String(buffer, 0, n, java.nio.charset.StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
            }
        });
        drain.start();
        try {
            int attempt = 0;
            while (attempt < 40) {
                try (java.net.Socket probe = new java.net.Socket()) {
                    probe.connect(new java.net.InetSocketAddress("127.0.0.1", port), 200);
                    break;
                } catch (IOException e) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                attempt++;
            }
            try (java.net.Socket socket = new java.net.Socket("127.0.0.1", port)) {
                socket.setSoTimeout(5000);
                socket.getOutputStream().write(("GET /log HTTP/1.1\r\nHost: x\r\n\r\n")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                socket.getOutputStream().flush();
                socket.getInputStream().readAllBytes();
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            server.destroy();
            try {
                drain.join(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            assertTrue(serverOut.toString().contains("INFO handler called"), serverOut.toString());
        } finally {
            server.destroyForcibly();
        }
    }

    private int freePort() throws IOException {
        try (java.net.ServerSocket probe = new java.net.ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    @Test
    void jsonStructuredMode(@TempDir Path tempDir) throws IOException {
        String[] out = run(tempDir, """
                main() {
                    log.info("hello json")
                }
                """, null, Map.of("KOF_LOG_JSON", "1"));
        String line = out[0].trim();
        assertTrue(line.startsWith("{\"ts\":\"2"), line);
        assertTrue(line.contains("\"level\":\"INFO\""), line);
        assertTrue(line.contains("\"msg\":\"hello json\""), line);
        assertFalse(line.contains("requestId"), "no request context: " + line);
    }

    @Test
    void jsonModeEscapesQuotes(@TempDir Path tempDir) throws IOException {
        String[] out = run(tempDir, """
                main() {
                    log.warn("say \\"hi\\"")
                }
                """, null, Map.of("KOF_LOG_JSON", "1"));
        assertTrue(out[1].contains("\\\"hi\\\""), out[1]);
    }

    @Test
    void webRequestsCarryCorrelationId(@TempDir Path tempDir) throws IOException, InterruptedException {
        int port = freePort();
        Path source = tempDir.resolve("WebLog.kf");
        Files.writeString(source, """
                main() {
                    var app = web.app()
                    app.get("/x") {
                        log.info("handled")
                        return "ok"
                    }
                    app.listen(PORT)
                }
                """.replace("PORT", String.valueOf(port)));
        Path outDir = tempDir.resolve("classes");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), result.diagnostics().getDiagnostics().toString());
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        pb.environment().put("KOF_LOG_JSON", "1");
        Process server = pb.start();
        StringBuilder serverOut = new StringBuilder();
        Thread drain = new Thread(() -> {
            try {
                byte[] buffer = new byte[4096];
                int n;
                while ((n = server.getInputStream().read(buffer)) != -1) {
                    serverOut.append(new String(buffer, 0, n, java.nio.charset.StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
            }
        });
        drain.start();
        try {
            int attempt = 0;
            while (attempt < 40) {
                try (java.net.Socket probe = new java.net.Socket()) {
                    probe.connect(new java.net.InetSocketAddress("127.0.0.1", port), 200);
                    break;
                } catch (IOException e) {
                    Thread.sleep(100);
                }
                attempt++;
            }
            String request = "GET /x HTTP/1.1\r\nHost: x\r\n\r\n";
            for (int i = 0; i < 2; i++) {
                try (java.net.Socket socket = new java.net.Socket("127.0.0.1", port)) {
                    socket.setSoTimeout(5000);
                    socket.getOutputStream().write(request.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    socket.getOutputStream().flush();
                    socket.getInputStream().readAllBytes();
                }
            }
            Thread.sleep(400);
            server.destroy();
            drain.join(2000);
            String out = serverOut.toString();
            int first = out.indexOf("handled");
            int second = out.indexOf("handled", first + 1);
            assertTrue(first >= 0 && second >= 0, out);
            String firstId = out.substring(out.indexOf("requestId"), out.indexOf("}", first));
            String secondId = out.substring(out.indexOf("requestId", first + 1), out.indexOf("}", second));
            assertTrue(firstId.contains("requestId\":\""), firstId);
            assertTrue(secondId.contains("requestId\":\""), secondId);
            assertFalse(firstId.equals(secondId), "request ids must differ per request");
        } finally {
            server.destroyForcibly();
        }
    }

    @Test
    void nativeAndJsReportLog001(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Log.kf");
        Files.writeString(source, """
                main() {
                    log.info("hi")
                }
                """);
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("native-out"), Target.NATIVE);
        assertFalse(nativeResult.success());
        assertTrue(nativeResult.diagnostics().getDiagnostics().toString().contains("LOG001"),
                nativeResult.diagnostics().getDiagnostics().toString());

        CompilationResult jsResult = driver.compile(source, tempDir.resolve("js-out"), Target.JS);
        assertFalse(jsResult.success());
        assertTrue(jsResult.diagnostics().getDiagnostics().toString().contains("LOG001"),
                jsResult.diagnostics().getDiagnostics().toString());
    }
}