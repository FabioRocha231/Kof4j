package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * spawn — concurrent tasks on the JVM backend (virtual threads) and
 * on the Native backend (pthread — CONC001 fechado 31/08).
 *
 * Semantics: spawn <call> or spawn { ... } runs the task concurrently;
 * the program waits for all spawned tasks before exiting (implicit join).
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
    void nativeSpawnStmtRuns(@TempDir Path tempDir) throws IOException, InterruptedException {
        // CONC001 fechado: spawn stmt com pthread + join implícito no fim do main
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    spawn {
                        println("inside")
                    }
                    println("after")
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Native spawn should compile: " + result.diagnostics().getDiagnostics());
        Path bin = tempDir.resolve("out").resolve("Default/Main");
        ProcessBuilder pb = new ProcessBuilder(bin.toString()).redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        assertTrue(output.contains("inside"), "task rodou: " + output);
        assertTrue(output.contains("after"), "main continuou: " + output);
    }

    @Test
    void nativeSpawnExprAwait(@TempDir Path tempDir) throws IOException, InterruptedException {
        // CONC001: spawn-expr com Handle tipado + await (join no handle)
        Path source = tempDir.resolve("Main2.kf");
        Files.writeString(source, """
                Int work(Int x) { return x * 2 }
                main() {
                    val r = spawn work(21)
                    println(await r)
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out2"), Target.NATIVE);
        assertTrue(result.success(), "Native spawn-expr should compile: " + result.diagnostics().getDiagnostics());
        Path bin = tempDir.resolve("out2").resolve("Default/Main");
        ProcessBuilder pb = new ProcessBuilder(bin.toString()).redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        assertTrue(output.contains("42"), "await devolve o valor: " + output);
    }
}