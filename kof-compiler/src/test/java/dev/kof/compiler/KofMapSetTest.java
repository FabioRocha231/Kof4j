package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KofMapSetTest {
    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void mapSetJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                var m = mapOf()
                m.put("a", 1)
                m.put("b", 2)
                assert(m.get("a") == 1)
                assert(m.get("b") == 2)
                assert(m.contains("a"))
                assert(!m.contains("c"))
                assert(m.size() == 2)
                assert(m.remove("a") == 1)
                assert(m.size() == 1)
                assert(m.keys().size() == 1)
                assert(m.values().size() == 1)
                m.clear()
                assert(m.isEmpty())
                assert(m.size() == 0)

                var s = setOf()
                s.add(1)
                s.add(2)
                s.add(1)
                assert(s.size() == 2)
                assert(s.contains(1))
                assert(!s.contains(3))
                assert(s.remove(1))
                assert(s.size() == 1)
                s.clear()
                assert(s.isEmpty())
                println("ok")
            }
            """, "ok");
    }

    @Test
    void mapSetJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                var m = mapOf()
                m.put("x", 10)
                println(m.get("x"))
                println(m.size())
                var s = setOf(1, 2, 3)
                println(s.size())
                println(s.contains(2))
                println("done")
            }
            """, "10\n1\n3\ntrue\ndone");
    }

    @Test
    void mapSetNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                var m = mapOf()
                m.put("a", 1)
                m.put("b", 2)
                assert(m.get("a") == 1)
                assert(m.contains("b"))
                assert(m.size() == 2)
                assert(m.remove("a") == 1)
                m.clear()
                assert(m.isEmpty())

                var s = setOf(1, 2, 3)
                assert(s.size() == 3)
                assert(s.contains(2))
                assert(s.remove(1))
                println("ok-native")
            }
            """, "ok-native");
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
