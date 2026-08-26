package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KofConcurrency2Test {
    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void cancelCooperativeJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                Int trabalho() {
                    var i = 0
                    while (i < 10000 && !cancelled()) {
                        time.sleep(1)
                        i++
                    }
                    if (cancelled()) {
                        println("cancelado")
                    } else {
                        println("completo")
                    }
                    return i
                }

                main() {
                    val r = spawn trabalho()
                    time.sleep(30)
                    assert(cancel(r))
                    await r
                    println("fim")
                }
                """, "cancelado\nfim");
    }


    @Test
    void cancelledOutsideIsFalse(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                main() {
                    assert(!cancelled())
                    println("ok")
                }
                """, "ok");
    }

    @Test
    void selectAnyJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                String rapida() { return "primeira" }

                String lenta() {
                    time.sleep(300)
                    return "segunda"
                }

                main() {
                    val a = spawn lenta()
                    val b = spawn rapida()
                    val v = selectAny(a, b)
                    println(v)
                }
                """, "primeira");
    }

    @Test
    void selectAnyNativeGap(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, """
                Int t1() { return 1 }
                Int t2() { return 2 }
                main() {
                    val a = spawn t1()
                    val b = spawn t2()
                    println(selectAny(a, b))
                }
                """);
        CompilationResult r = driver.compile(f, tmp.resolve("out"), Target.NATIVE);
        assertFalse(r.success(), "Native selectAny deve gapar CONC001");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "CONC001".equals(d.code())),
                "Esperado CONC001");
    }

    @Test
    void cancelJsSequential(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
                Int t() { return 9 }
                main() {
                    val r = spawn t()
                    assert(cancel(r) == 0)
                    assert(cancelled() == 0)
                    println(await r)
                }
                """, "9");
    }

    // ── helpers ──
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
