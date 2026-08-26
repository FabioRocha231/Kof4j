package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KofAwaitTest {
    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void awaitJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                String trabalho() {
                    return "feito"
                }

                main() {
                    val r = spawn trabalho()
                    assert(r != null)
                    val v = await r
                    println(v)
                }
                """, "feito");
    }

    @Test
    void awaitJs(@TempDir Path tmp) throws Exception {
        // JS single-threaded: execução sequencial inline, handle memoiza o valor.
        // Paralelismo real é JVM-only — semântica de VALOR idêntica nos testes.
        runJs(tmp, """
                String calc() {
                    return "js-ok"
                }

                Int soma(a: Int, b: Int) {
                    return a + b
                }

                main() {
                    val r1 = spawn calc()
                    println(await r1)
                    val r2 = spawn soma(2, 3)
                    println((await r2) == 5)
                    spawn { println("fire") }
                    println("done")
                }
                """, "js-ok\ntrue\nfire\ndone");
    }

    @Test
    void awaitNativeGap(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, """
                String t() { return "x" }
                main() {
                    val r = spawn t()
                    val v = await r
                    println(v)
                }
                """);
        CompilationResult result = driver.compile(file, tmp.resolve("out"), Target.NATIVE);
        assertFalse(result.success(), "Native spawn-expr/await deve reportar CONC001");
        assertTrue(result.diagnostics().getDiagnostics().stream().anyMatch(d -> "CONC001".equals(d.code())),
                "Esperado CONC001: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void awaitPrimitiveJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                Int n() { return 42 }
                Bool flag() { return true }

                main() {
                    val r1 = spawn n()
                    val r3 = spawn flag()
                    assert((await r1) == 42)
                    assert(await r3)
                    println(await r1)
                }
                """, "42");
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
            assertEquals(expected, output, "JVM output");
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
            assertEquals(expected, output, "JS output");
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
