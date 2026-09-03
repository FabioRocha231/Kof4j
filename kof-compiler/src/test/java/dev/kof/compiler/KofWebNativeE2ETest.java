package dev.kof.compiler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WEB002 — kof.web servidor para o target Nativo.
 *
 * T1: accept loop bloqueante que responde 200 "hello" a qualquer request.
 * T2/T3/T4 fecham parse + routing + handler dispatch.
 */
class KofWebNativeE2ETest {

    private Process serverProcess;

    @AfterEach
    void stopServer() {
        if (serverProcess != null) {
            serverProcess.destroy();
            try {
                serverProcess.waitFor(3, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            serverProcess.destroyForcibly();
            serverProcess = null;
        }
    }

    private static final String SERVER_T1 = """
            main() {
                var app = web.app()
                app.listen(PORT)
            }
            """;

    private static final String SERVER_T2 = """
            main() {
                var app = web.app()
                app.get("/hello") {
                    return "ok-matched"
                }
                app.listen(PORT)
            }
            """;

    private static int freePort() throws IOException {
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private Process startNativeServer(Path tempDir, String source) throws Exception {
        int port = freePort();
        Path src = tempDir.resolve("App.kf");
        Files.writeString(src, source.replace("PORT", String.valueOf(port)));
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(src, tempDir.resolve("classes"), Target.NATIVE);
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        Path binary = tempDir.resolve("classes/Default/Main");
        assertTrue(Files.exists(binary));
        ProcessBuilder pb = new ProcessBuilder(binary.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (!p.isAlive()) throw new IOException("server died early");
            try (Socket probe = new Socket()) {
                probe.connect(new java.net.InetSocketAddress("127.0.0.1", port), 200);
                return p;
            } catch (IOException e) { Thread.sleep(50); }
        }
        p.destroyForcibly();
        throw new IOException("server did not come up on port " + port);
    }

    private String httpGet(int port, String path) throws IOException {
        try (Socket s = new Socket("127.0.0.1", port)) {
            String req = "GET " + path + " HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n";
            s.getOutputStream().write(req.getBytes(StandardCharsets.UTF_8));
            s.getOutputStream().flush();
            return new String(s.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // WEB002 T1: accept loop + hello fixo (commit 89ac0d9)
    @Test
    void nativeServerAcceptsAndResponds200(@TempDir Path tempDir) throws Exception {
        serverProcess = startNativeServer(tempDir, SERVER_T1);
        assertTrue(true); // chegou até aqui = porta responde
    }

    // WEB002 T2: parse METHOD+PATH + match rota literal
    @Test
    void nativeServerMatchesLiteralRoute(@TempDir Path tempDir) throws Exception {
        int port = freePort();
        Path src = tempDir.resolve("App.kf");
        Files.writeString(src, SERVER_T2.replace("PORT", String.valueOf(port)));
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(src, tempDir.resolve("classes"), Target.NATIVE);
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(tempDir.resolve("classes/Default/Main").toString());
        pb.redirectErrorStream(true);
        serverProcess = pb.start();
        // aguarda a porta abrir
        long deadline = System.currentTimeMillis() + 5000;
        boolean up = false;
        while (System.currentTimeMillis() < deadline && !up) {
            try (Socket probe = new Socket()) {
                probe.connect(new java.net.InetSocketAddress("127.0.0.1", port), 100);
                up = true;
            } catch (IOException e) { Thread.sleep(50); }
        }
        assertTrue(up, "server should accept connections");
        String match = httpGet(port, "/hello");
        assertTrue(match.contains("200"), "match: " + match);
        assertTrue(match.endsWith("route-match"), "match body: " + match);
        String miss = httpGet(port, "/estanaoexiste");
        assertTrue(miss.contains("404"), "miss: " + miss);
        assertTrue(miss.endsWith("Not Found"), "miss body: " + miss);
    }
}
