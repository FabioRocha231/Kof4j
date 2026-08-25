package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KofSecurityG9Test {
    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void securityG9Jvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                // rate limiting: 2 per 60s
                assert(security.rateLimit("g9JvmKey", 2, 60))
                assert(security.rateLimit("g9JvmKey", 2, 60))
                assert(!security.rateLimit("g9JvmKey", 2, 60))
                // different key independent
                assert(security.rateLimit("g9JvmOther", 2, 60))
                // sessions
                val sid = security.sessionCreate("hello")
                assert(sid.length() > 0)
                assert(security.sessionGet(sid) == "hello")
                assert(security.sessionDestroy(sid))
                assert(security.sessionGet(sid) == null)
                // api keys
                val key = security.apiKeyGenerate()
                assert(key.length() > 0)
                assert(security.apiKeyValid(key))
                assert(!security.apiKeyValid("invalid-" + key))
                println("ok")
            }
            """, "ok");
    }

    @Test
    void securityG9Native(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                assert(security.rateLimit("g9NativeKey", 2, 60))
                assert(security.rateLimit("g9NativeKey", 2, 60))
                assert(!security.rateLimit("g9NativeKey", 2, 60))
                val sid = security.sessionCreate("native-data")
                assert(sid.length() > 0)
                assert(security.sessionGet(sid) == "native-data")
                assert(security.sessionDestroy(sid))
                assert(security.apiKeyValid(security.apiKeyGenerate()))
                println("ok")
            }
            """, "ok");
    }

    @Test
    void securityG9Js(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                println(security.rateLimit("g9JsKey", 2, 60))
                println(security.rateLimit("g9JsKey", 2, 60))
                println(security.rateLimit("g9JsKey", 2, 60))
                val sid = security.sessionCreate("js-data")
                println(sid.length() > 0)
                println(security.sessionGet(sid) == "js-data")
                println(security.sessionDestroy(sid))
                val k = security.apiKeyGenerate()
                println(k.length() > 0)
                println(security.apiKeyValid(k))
                println("done")
            }
            """, "true\ntrue\nfalse\ntrue\ntrue\ntrue\ntrue\ntrue\ndone");
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
