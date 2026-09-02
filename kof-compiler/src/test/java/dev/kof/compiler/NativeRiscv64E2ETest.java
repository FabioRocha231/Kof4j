package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NATIVE002 — E2E riscv64 (qemu). O backend cross emite o {@code kof_main} em
 * asm (stack machine, mesma semântica do x86_64) + runtime em asm puro
 * (raw syscalls, sem C — binário estático, {@code riscv64-linux-gnu-as} +
 * {@code riscv64-linux-gnu-ld}); o binário roda em {@code qemu-riscv64}.
 *
 * Pula (assume) quando a toolchain cruzada ou o qemu não existem, como
 * {@code NativeE2ETest} faz quando o assembler nativo falta.
 */
class NativeRiscv64E2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c).redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (p.waitFor() != 0 || out.isEmpty()) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private void assumeToolchain() {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
    }

    private String runRiscv64(Path tempDir, String source) throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, source);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(src, outDir, Target.NATIVE_RISCV64);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        ProcessBuilder pb = new ProcessBuilder("qemu-riscv64", binFile.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec;
        try {
            ec = p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running riscv64 binary", e);
        }
        assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
        return output;
    }

    @Test
    void riscv64HelloWorld(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, "main() { println(\"Hello, Kof!\") }");
        assertEquals("Hello, Kof!", out);
    }

    @Test
    void riscv64ArithmeticAndLocal(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                println("Hello")
                var x = 10
                println(x + 5)
            }
            """);
        assertEquals("Hello\n15", out);
    }

    @Test
    void riscv64IfElseComparisonsAndArithmetic(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                var x = 10
                if (x > 5) {
                    println("greater")
                } else {
                    println("smaller")
                }
                var a = 7
                var b = 3
                println(a - b)
                println(a * b)
                if (a == b) { println("eq") } else { println("ne") }
            }
            """);
        assertEquals("greater\n4\n21\nne", out);
    }

    @Test
    void riscv64DivisionModuloNegative(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = runRiscv64(tempDir, """
            main() {
                println(20 / 4)
                println(17 % 5)
                println(-7)
            }
            """);
        assertEquals("5\n2\n-7", out);
    }
}
