package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FLT001 — ponto flutuante no target Native é gap diagnosticado, nunca
 * resultado silenciosamente errado: os bits vivem na pilha como inteiros
 * (SSE real é trabalho futuro do backend). JVM compila e executa normal.
 */
class FloatingPointGapE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void fpArithmeticRejectedOnNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var d = 3.5 + 1.5
                    println(d)
                }
                """);
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("out-n"), Target.NATIVE);
        assertFalse(nativeResult.success(), "FP arithmetic must fail on Native");
        assertTrue(nativeResult.diagnostics().getDiagnostics().toString().contains("FLT001"),
                () -> "" + nativeResult.diagnostics().getDiagnostics());
        CompilationResult jvmResult = driver.compile(source, tempDir.resolve("out-v"), Target.JVM);
        assertTrue(jvmResult.success(), "Same code must compile on JVM");
    }

    @Test
    void fpComparisonRejectedOnNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    if (2.5 > 1.0) {
                        println("maior")
                    }
                }
                """);
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertFalse(nativeResult.success());
        assertTrue(nativeResult.diagnostics().getDiagnostics().toString().contains("FLT001"));
    }

    @Test
    void fpPrintingRejectedOnNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var d = 3.5
                    println(d)
                }
                """);
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertFalse(nativeResult.success());
        assertTrue(nativeResult.diagnostics().getDiagnostics().toString().contains("FLT001"));
    }

    @Test
    void fpStringConcatRejectedOnNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var s = "valor: " + 2.25
                    println(s)
                }
                """);
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertFalse(nativeResult.success());
        assertTrue(nativeResult.diagnostics().getDiagnostics().toString().contains("FLT001"),
                () -> "" + nativeResult.diagnostics().getDiagnostics());
    }

    @Test
    void intMathStillWorksOnNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var x = 3 + 1
                    println(x)
                }
                """);
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(nativeResult.success(),
                () -> "" + nativeResult.diagnostics().getDiagnostics());
    }
}
