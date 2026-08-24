package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * assert(cond[, "message"]) — the testing primitive of the language.
 * Throws when the condition is false (exit code != 0 powers `kof test`).
 * JVM + Native parity.
 */
class AssertE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private int runJvmExit(Path source, Path outDir) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            return ec;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    private int runNativeExit(Path source, Path outDir) throws IOException {
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
            return p.waitFor();
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    private static final String PASSING = """
            main() {
                assert(2 + 2 == 4)
                assert("kof" == "kof", "strings iguais")
                assert(!false)
                assert(if (true) true else false)
                println("ok")
            }
            """;

    private static final String FAILING = """
            main() {
                assert(1 + 1 == 3)
                println("unreachable")
            }
            """;

    private static final String FAILING_MESSAGE = """
            main() {
                assert(1 + 1 == 3, "soma errada")
                println("unreachable")
            }
            """;

    @Test
    void passingJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, PASSING);
        assertEquals(0, runJvmExit(source, tempDir.resolve("out")));
    }

    @Test
    void passingNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, PASSING);
        assertEquals(0, runNativeExit(source, tempDir.resolve("out")));
    }

    @Test
    void failingJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, FAILING);
        assertEquals(1, runJvmExit(source, tempDir.resolve("out")));
    }

    @Test
    void failingNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, FAILING);
        assertEquals(1, runNativeExit(source, tempDir.resolve("out")));
    }

    @Test
    void failingWithMessage(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, FAILING_MESSAGE);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), result.diagnostics().getDiagnostics().toString());
    }
}