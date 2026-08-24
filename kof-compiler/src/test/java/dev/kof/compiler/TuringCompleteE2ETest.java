package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Prova de Turing-completude do Kof: a função de Ackermann é
 * não-primitiva-recursiva — qualquer linguagem que a compute com
 * recursão + condicionais é Turing-completa (Church–Turing).
 *
 * O mesmo programa roda nos três targets: JVM, Native (asm x86-64) e JS
 * (GraalJS) — ack(3, 4) = 125.
 */
class TuringCompleteE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String ACKERMANN = """
            ack(Int m, Int n): Int {
                if (m == 0) {
                    return n + 1
                }
                if (n == 0) {
                    return ack(m - 1, 1)
                }
                return ack(m - 1, ack(m, n - 1))
            }

            main() {
                println(ack(3, 4))
                var i = 0
                var acc: Long = 0
                while (i < 1000000) {
                    acc = acc + i
                    i = i + 1
                }
                println(acc)
            }
            """;

    @Test
    void ackermannOnJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ACKERMANN);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-cp", outDir.toString(),
                    "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit: " + output);
            assertEquals("125\n499999500000", output);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    @Test
    void ackermannOnNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ACKERMANN);
        Path outDir = tempDir.resolve("native-out");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit: " + output);
            assertEquals("125\n499999500000", output);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    @Test
    void ackermannOnJs(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ACKERMANN);
        Path outDir = tempDir.resolve("js-out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path entry = outDir.resolve("Default.mjs");
        assertTrue(Files.exists(entry), "Default.mjs should exist");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(entry, out,
                java.io.InputStream.nullInputStream(), java.io.OutputStream.nullOutputStream(),
                false, new String[0]);
        assertEquals(0, ec, "JS exit");
        assertEquals("125\n499999500000", out.toString(java.nio.charset.StandardCharsets.UTF_8).trim());
    }
}