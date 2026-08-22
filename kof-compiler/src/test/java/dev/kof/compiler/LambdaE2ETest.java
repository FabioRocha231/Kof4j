package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lambda and if-expression end-to-end tests (JVM + Native parity).
 *
 * Lambdas compile to synthetic classes (Lambda0, Lambda1, ...) with an
 * invoke method; the call f(x) dispatches virtually. No captures yet.
 */
class LambdaE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    private String runNative(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    private static final String LAMBDAS = """
            main() {
                var f = (x: Int) -> x * 2
                println(f(21))
                var g = (a: Int, b: Int) -> a + b
                println(g(3, 4))
                var h = () -> 99
                println(h())
                var s = (nome: String) -> "ola " + nome
                println(s("kof"))
            }
            """;

    @Test
    void lambdasJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, LAMBDAS);
        runJvm(source, tempDir.resolve("out"), "42\n7\n99\nola kof");
    }

    @Test
    void lambdasNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, LAMBDAS);
        runNative(source, tempDir.resolve("out"), "42\n7\n99\nola kof");
    }

    private static final String IF_EXPRS = """
            main() {
                var v = if (5 > 3) 10 else 20
                println(v)
                var s = if (5 < 3) "maior" else "menor"
                println(s)
                var n = if (2 + 2 == 4) 100 else 0
                println(n)
                println(if (true) "yes" else "no")
                var chain = if (v == 10) if (n == 100) "both" else "v" else "n"
                println(chain)
            }
            """;

    @Test
    void ifExprsJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, IF_EXPRS);
        runJvm(source, tempDir.resolve("out"), "10\nmenor\n100\nyes\nboth");
    }

    @Test
    void ifExprsNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, IF_EXPRS);
        runNative(source, tempDir.resolve("out"), "10\nmenor\n100\nyes\nboth");
    }
}