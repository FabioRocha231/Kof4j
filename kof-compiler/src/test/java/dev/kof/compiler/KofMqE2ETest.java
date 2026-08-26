package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;


/**
 * End-to-end tests for {@code kof.mq} — in-memory messaging: pub/sub event
 * bus (Kof lambdas as handlers) and bounded queues.
 */
class KofMqE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path tempDir, String kofSource, String expected) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, kofSource);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-Dfile.encoding=UTF-8",
                    "-Dstdout.encoding=UTF-8", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    @Test
    void publishDeliversToSubscribers(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, """
                main() {
                    mq.subscribe("order.created", (msg) -> {
                        println("pedido: " + msg)
                    })
                    mq.publish("order.created", "12345")
                    mq.publish("order.created", "67890")
                }
                """, "pedido: 12345\npedido: 67890");
    }

    @Test
    void unsubscribeStopsDelivery(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, """
                main() {
                    var h = (msg) -> {
                        println("got:" + msg)
                    }
                    mq.subscribe("topic", h)
                    mq.publish("topic", "one")
                    mq.unsubscribe("topic", h)
                    mq.publish("topic", "two")
                }
                """, "got:one");
    }

    @Test
    void queuePushPopAndSize(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, """
                main() {
                    var q = mq.queue()
                    mq.push(q, "job-1")
                    mq.push(q, "job-2")
                    println(mq.queueSize(q))
                    println(mq.pop(q))
                    println(mq.pop(q))
                    println(mq.pop(q))
                }
                """, "2\njob-1\njob-2\nnull");
    }

    @Test
    void jsSupportsMqAndNativeStillGaps(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    mq.subscribe("topic", (msg) -> {
                        println("got:" + msg)
                    })
                    mq.publish("topic", "hello")
                }
                """);
        // JS should now succeed
        CompilationResult jsResult = driver.compile(source, tempDir.resolve("js"), Target.JS);
        assertTrue(jsResult.success(), "JS should support mq.publish: " + jsResult.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            Path jsEntry = findJsEntry(tempDir.resolve("js"));
            int ec = dev.kof.runtime.KofJsRunner.run(jsEntry, buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String out = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit code, output: " + out);
            assertEquals("got:hello", out, "JS output");
        }
        // Native still gaps
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("native"), Target.NATIVE);
        assertFalse(nativeResult.success(), "Native should reject mq.publish");
        assertTrue(nativeResult.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> d.message().contains("MQ001")), "" + nativeResult.diagnostics().getDiagnostics());
    }

    private static Path findJsEntry(Path dir) throws IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs"))
                    .findFirst().orElseThrow(() -> new IOException("no .mjs in " + dir));
        }
    }
}