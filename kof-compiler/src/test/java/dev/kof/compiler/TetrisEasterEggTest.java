package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof.tetris easter egg (EGG001):
 *
 * - `main() { tetris.run() }` compiles on the JVM target and the generated
 *   program starts the terminal tetris, exiting cleanly on `q`;
 * - the call is rejected with a clear diagnostic on targets that cannot
 *   render an interactive terminal session (Native/JS).
 */
class TetrisEasterEggTest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void tetrisRunCompilesAndQuitsOnJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("tetris.kf");
        Files.writeString(source, """
            main() {
                tetris.run()
            }
            """);
        Path outDir = tempDir.resolve("jvm");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (OutputStream stdin = p.getOutputStream()) {
                stdin.write("q\n".getBytes());
                stdin.flush();
            }
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0");
            assertTrue(output.contains("kof.tetris"), "Output should contain the tetris banner");
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    @Test
    void tetrisRunRejectedOnNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("tetris.kf");
        Files.writeString(source, """
            main() {
                tetris.run()
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("native"), Target.NATIVE);
        assertFalse(result.success(), "Native should reject tetris.run()");
        assertTrue(hasCode(result, "EGG001"), "Diagnostic should carry EGG001");
    }

    @Test
    void tetrisRunRejectedOnJs(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("tetris.kf");
        Files.writeString(source, """
            main() {
                tetris.run()
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("js"), Target.JS);
        assertFalse(result.success(), "JS should reject tetris.run()");
        assertTrue(hasCode(result, "EGG001"), "Diagnostic should carry EGG001");
    }

    private static boolean hasCode(CompilationResult result, String code) {
        return result.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> code.equals(d.code()));
    }
}