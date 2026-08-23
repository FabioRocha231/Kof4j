package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Debug metadata (Fase 1/2 do debugger): every IR operation carries its
 * source position, and the JVM backend emits SourceFile + LineNumberTable
 * mapping bytecode to Kof lines. The Kof identity survives the backend.
 */
class DebugInfoE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            Int soma(Int a, Int b) {
                var total = a + b
                return total
            }
            main() {
                var x = soma(20, 22)
                println(x)
            }
            """;

    @Test
    void jvmClassHasSourceFileAndLineNumbers(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC);
        Path out = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, out, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());

        byte[] classBytes = Files.readAllBytes(out.resolve("Default/Main.class"));
        String javap = runJavap(out.resolve("Default/Main.class"));

        assertTrue(javap.contains("SourceFile: \"Main.kf\""),
                "class file must carry the Kof source file name");
        assertTrue(javap.contains("LineNumberTable"),
                "class file must carry a LineNumberTable");
        assertTrue(javap.contains("line 2:") || javap.contains("line 3:"),
                "line numbers must reference Kof source lines");
    }

    @Test
    void nativeBinaryStillRuns(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC);
        Path out = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, out, Target.NATIVE);
        assertTrue(result.success(), "Native should still compile: " + result.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "Native binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native should run");
            assertEquals("42", output);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    private String runJavap(Path classFile) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder("javap", "-v", classFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            return out;
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }
}