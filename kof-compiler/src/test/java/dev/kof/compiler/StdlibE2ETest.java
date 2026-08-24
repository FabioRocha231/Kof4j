package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;


class StdlibE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path source, Path outDir, String expected) throws IOException {
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
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    private static final String TIME_SOURCE = """
            main() {
                var t = now()
                println(t > 1700000000000)
                var t2 = now()
                println(t2 >= t)
                println(t2 - t >= 0)
            }
            """;

    @Test
    void nowJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, TIME_SOURCE);
        runJvm(source, tempDir.resolve("out"), "true\ntrue\ntrue");
    }

    @Test
    void nowNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, TIME_SOURCE);
        runNative(source, tempDir.resolve("out"), "true\ntrue\ntrue");
    }

    private static final String IO_SOURCE = """
            main() {
                var rc = writeFile("%s", "kof io funciona")
                println(rc)
                var content = readFile("%s")
                println(content)
                println(content.length() > 0)
            }
            """;

    @Test
    void fileIoJvm(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("data.txt");
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, IO_SOURCE.formatted(file.toString(), file.toString()));
        runJvm(source, tempDir.resolve("out"), "0\nkof io funciona\ntrue");
    }

    @Test
    void fileIoNative(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("data.txt");
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, IO_SOURCE.formatted(file.toString(), file.toString()));
        runNative(source, tempDir.resolve("out"), "0\nkof io funciona\ntrue");
    }
}