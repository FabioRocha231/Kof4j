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

    private static int freePort() throws IOException {
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @Test
    void nativeServerAcceptsAndResponds200(@TempDir Path tempDir) throws Exception {
        int port;
        try (java.net.ServerSocket ss = new java.net.ServerSocket(0)) {
            port = ss.getLocalPort();
        }
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, SERVER_T1.replace("PORT", String.valueOf(port)));
        // força uma CompilationResult nova (o driver compartilhado viaja estado entre compilações)
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(source, tempDir.resolve("classes"), Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path binary = tempDir.resolve("classes/Default/Main");
        assertTrue(Files.exists(binary), "Native binary should exist");

        ProcessBuilder pb = new ProcessBuilder(binary.toString());
        pb.redirectErrorStream(true);
        serverProcess = pb.start();

        // aguarda servidor subir
        long deadline = System.currentTimeMillis() + 5000;
        boolean up = false;
        while (System.currentTimeMillis() < deadline) {
            if (!serverProcess.isAlive()) {
                String out = new String(serverProcess.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8);
                fail("server exited early: " + out);
            }
            try (Socket probe = new Socket()) {
                probe.connect(new java.net.InetSocketAddress("127.0.0.1", port), 200);
                up = true;
                break;
            } catch (IOException e) { /* retry */ }
            Thread.sleep(50);
        }
        assertTrue(up, "server should accept connections");

        // GET /hello — o accept loop retorna sempre hello neste estágio (T1)
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.getOutputStream().write("GET /hello HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            s.getOutputStream().flush();
            byte[] buf = s.getInputStream().readAllBytes();
            String response = new String(buf, StandardCharsets.UTF_8);
            assertTrue(response.startsWith("HTTP/1.1 200"),
                    "expected 200, got: " + response);
            assertTrue(response.endsWith("hello"),
                    "expected 'hello' body, got: " + response);
        }
    }
}
