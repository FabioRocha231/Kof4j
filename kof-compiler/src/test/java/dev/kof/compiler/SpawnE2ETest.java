package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * spawn — concurrent tasks on the JVM backend (virtual threads).
 *
 * Semantics: spawn <call> or spawn { ... } runs the task concurrently;
 * the program waits for all spawned tasks before exiting (implicit join).
 * The Native target reports CONC001 (not supported yet — documented gap).
 */
class SpawnE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path source, Path outDir) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    private static final String SPAWN_SOURCE = """
            void tarefa(String nome, Int vezes) {
                for (var i = 0; i < vezes; i = i + 1) {
                    println(nome + ":" + i)
                }
            }
            main() {
                println("inicio")
                spawn tarefa("a", 3)
                spawn tarefa("b", 2)
                spawn {
                    println("bloco")
                }
                println("fim")
            }
            """;

    @Test
    void spawnRunsConcurrentlyAndJoins(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SPAWN_SOURCE);
        String output = runJvm(source, tempDir.resolve("out"));
        List<String> lines = output.lines().toList();
        assertTrue(lines.contains("inicio"), "main should print inicio first: " + lines);
        assertTrue(lines.contains("fim"), "main should not block on spawn: " + lines);
        for (String expected : List.of("a:0", "a:1", "a:2", "b:0", "b:1", "bloco")) {
            assertTrue(lines.contains(expected), "missing " + expected + " in: " + lines);
        }
    }

    @Test
    void spawnFunctionWithReturnValueIsDiscarded(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                Int calcula(Int x) {
                    return x * 2
                }
                main() {
                    spawn calcula(21)
                    println("feito")
                }
                """);
        String output = runJvm(source, tempDir.resolve("out"));
        assertTrue(output.contains("feito"), output);
    }

    @Test
    void nativeReportsConc001(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    spawn println("x")
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertFalse(result.success(), "Native spawn should be rejected");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> d.code().equals("CONC001")), "Should report CONC001");
    }
}