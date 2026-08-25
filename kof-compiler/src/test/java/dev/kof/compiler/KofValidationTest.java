package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KofValidationTest {
    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void validationJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                assert(validation.required("hello"))
                assert(!validation.required(""))
                assert(!validation.notBlank("  "))
                assert(validation.notBlank(" a "))
                assert(validation.minLength("abc", 2))
                assert(!validation.minLength("a", 2))
                assert(validation.maxLength("abc", 5))
                assert(!validation.maxLength("abc", 2))
                assert(validation.lengthBetween("abcd", 2, 5))
                assert(!validation.lengthBetween("a", 2, 5))
                assert(validation.isEmail("test@example.com"))
                assert(!validation.isEmail("bad-email"))
                assert(validation.isUrl("https://example.com"))
                assert(!validation.isUrl("ftp://x"))
                assert(validation.matches("hello", "ell"))
                assert(!validation.matches("hello", "xyz"))
                assert(validation.isInt("123"))
                assert(!validation.isInt("12a"))
                assert(validation.inRange(5, 1, 10))
                assert(!validation.inRange(15, 1, 10))
                assert(validation.min(5, 3))
                assert(!validation.min(2, 3))
                assert(validation.max(5, 10))
                assert(!validation.max(15, 10))
                println("ok")
            }
            """, "ok");
    }

    @Test
    void validationNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                assert(validation.required("hello"))
                assert(!validation.required(""))
                assert(validation.isEmail("a@b.c"))
                assert(!validation.isEmail("a@b"))
                assert(validation.inRange(5, 1, 10))
                println("ok")
            }
            """, "ok");
    }

    @Test
    void validationJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                println(validation.required("hi"))
                println(validation.isUrl("https://kof.dev"))
                println(validation.isEmail("a@b.c"))
                println("done")
            }
            """, "true\ntrue\ntrue\ndone");
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
