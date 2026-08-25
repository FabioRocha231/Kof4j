package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KofObservabilityTest {
    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void observabilityJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                assert(observability.health() == "UP")
                assert(observability.readiness())
                assert(observability.liveness())
                val c1 = observability.counter("obsJvmCounter")
                assert(c1 == 1)
                val c2 = observability.counter("obsJvmCounter")
                assert(c2 == 2)
                val c3 = observability.increment("obsJvmCounter", 3)
                assert(c3 == 5)
                observability.gauge("obsJvmGauge", 42)
                val r1 = observability.requestId()
                assert(r1.length() > 0)
                val r2 = observability.correlationId()
                assert(r2.length() > 0)
                assert(r1 != r2)
                println("ok")
            }
            """, "ok");
    }

    @Test
    void observabilityNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                assert(observability.health() == "UP")
                assert(observability.readiness())
                assert(observability.liveness())
                val c1 = observability.counter("obsNativeCounter")
                assert(c1 == 1)
                val c2 = observability.counter("obsNativeCounter")
                assert(c2 == 2)
                val c3 = observability.increment("obsNativeCounter", 5)
                assert(c3 == 7)
                observability.gauge("obsNativeGauge", 99)
                val r1 = observability.requestId()
                assert(r1.length() > 0)
                val r2 = observability.correlationId()
                assert(r2.length() > 0)
                println("ok")
            }
            """, "ok");
    }

    @Test
    void observabilityJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                println(observability.health())
                println(observability.readiness())
                println(observability.liveness())
                println(observability.counter("obsJsCounter"))
                println(observability.counter("obsJsCounter"))
                println(observability.increment("obsJsCounter", 10))
                observability.gauge("obsJsGauge", 7)
                println(observability.requestId().length() > 0)
                println(observability.correlationId().length() > 0)
                println("done")
            }
            """, "UP\ntrue\ntrue\n1\n2\n12\ntrue\ntrue\ndone");
    }

    private String runJvm(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            if (expected != null) assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runNative(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile failed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        try {
            Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit code, output: " + output);
            if (expected != null) assertEquals(expected, output, "Native output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runJs(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(findJsEntry(outDir), buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String output = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit code, output: " + output);
            if (expected != null) assertEquals(expected, output, "JS output");
            return output;
        }
    }

    private static Path findJsEntry(Path dir) throws java.io.IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs"))
                    .findFirst().orElseThrow(() -> new java.io.IOException("no .mjs in " + dir));
        }
    }
}
