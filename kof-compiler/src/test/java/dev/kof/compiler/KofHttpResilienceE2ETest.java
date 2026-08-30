package dev.kof.compiler;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for {@code kof.http} retry + circuit breaker (JVM).
 */
class KofHttpResilienceE2ETest {

    private static final String JAVA_BIN = java.nio.file.Path.of(
            System.getProperty("java.home"), "bin", "java").toString();

    private final CompilerDriver driver = new CompilerDriver();
    private HttpServer server;
    private ExecutorService pool;
    private final AtomicInteger flakyHits = new AtomicInteger();

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
        if (pool != null) pool.shutdownNow();
    }

    private void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        pool = Executors.newFixedThreadPool(2);
        server.setExecutor(pool);
        server.createContext("/ok", ex -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/flaky", ex -> {
            int n = flakyHits.incrementAndGet();
            byte[] body = ("ok-" + n).getBytes(StandardCharsets.UTF_8);
            if (n <= 2) {
                ex.sendResponseHeaders(500, body.length);
            } else {
                ex.sendResponseHeaders(200, body.length);
            }
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
    }

    private int basePort() {
        return server.getAddress().getPort();
    }

    private String runApp(Path tempDir, String[] args) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main(args) {
                    var base = args[0]
                    var closedUrl = args[1]
                    http.retry(2)
                    var a = http.get(base + "/flaky")
                    println("retry=" + a)
                    http.retry(0)
                    http.circuit(1)
                    try {
                        var b = http.get(closedUrl)
                        println("fail=" + b)
                    } catch (Exception e) {
                        println("fail=caught")
                    }
                    try {
                        var c = http.get(base + "/ok")
                        println("open=" + c)
                    } catch (Exception e) {
                        println("open=caught")
                    }
                    http.circuit(0)
                    var d = http.get(base + "/ok")
                    println("recover=" + d)
                }
                """);
        Path outDir = tempDir.resolve("classes");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(JAVA_BIN);
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-cp");
        cmd.add(outDir.toString());
        cmd.add("Default.Main");
        cmd.addAll(java.util.Arrays.asList(args));
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    private int closedPort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @Test
    void retrySucceedsAfterFlakyFailures(@TempDir Path tempDir) throws IOException {
        startServer();
        String out = runApp(tempDir, new String[]{"http://127.0.0.1:" + basePort()});
        assertTrue(out.contains("retry=ok-3"), "retry should succeed on 3rd attempt, got: " + out);
        assertEquals(3, flakyHits.get(), "flaky server should have been hit exactly 3 times");
    }

    @Test
    void circuitOpensAndFailFastsThenRecovers(@TempDir Path tempDir) throws IOException {
        startServer();
        String closed = "http://127.0.0.1:" + closedPort();
        String out = runApp(tempDir, new String[]{"http://127.0.0.1:" + basePort(), closed});
        assertTrue(out.contains("fail=caught"), "closed port should throw, got: " + out);
        assertTrue(out.contains("open=caught"), "circuit should be open (fail fast), got: " + out);
        assertTrue(out.contains("recover=ok"), "circuit(0) should recover, got: " + out);
    }
}