package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Native float/double agora é XMM (FLT001 removido) — parity JVM/JS/Native.
 */
class FloatingPointGapE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void fpArithmeticWorksOnNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var d = 3.5 + 1.5
                    println(d)
                }
                """);
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("out-n"), Target.NATIVE);
        assertTrue(nativeResult.success(), () -> "" + nativeResult.diagnostics().getDiagnostics());
        // also verify runtime output via binary execution
        Path bin = tempDir.resolve("out-n/Default/Main");
        if (Files.exists(bin)) {
            Process p = new ProcessBuilder(bin.toString()).start();
            String out = new String(p.getInputStream().readAllBytes());
            try { p.waitFor(); } catch (InterruptedException e) { throw new IOException(e); }
            assertTrue(out.contains("5"), "native output should contain 5: " + out);
        }
        CompilationResult jvmResult = driver.compile(source, tempDir.resolve("out-v"), Target.JVM);
        assertTrue(jvmResult.success(), "Same code must compile on JVM");
    }

    @Test
    void fpComparisonWorksOnNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    if (2.5 > 1.0) {
                        println("maior")
                    }
                }
                """);
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(nativeResult.success(), () -> "" + nativeResult.diagnostics().getDiagnostics());
        Path bin = tempDir.resolve("out/Default/Main");
        if (Files.exists(bin)) {
            Process p = new ProcessBuilder(bin.toString()).start();
            String out = new String(p.getInputStream().readAllBytes());
            try { p.waitFor(); } catch (InterruptedException e) { throw new IOException(e); }
            assertTrue(out.contains("maior"), "expected maior: " + out);
        }
    }

    @Test
    void fpPrintingWorksOnNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var d = 3.5
                    println(d)
                }
                """);
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(nativeResult.success(), () -> "" + nativeResult.diagnostics().getDiagnostics());
    }

    @Test
    void fpStringConcatWorksOnNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var s = "valor: " + 2.25
                    println(s)
                }
                """);
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(nativeResult.success(), () -> "" + nativeResult.diagnostics().getDiagnostics());
        Path bin = tempDir.resolve("out/Default/Main");
        if (Files.exists(bin)) {
            Process p = new ProcessBuilder(bin.toString()).start();
            String out = new String(p.getInputStream().readAllBytes());
            try { p.waitFor(); } catch (InterruptedException e) { throw new IOException(e); }
            assertTrue(out.contains("valor:"), "expected valor: in " + out);
        }
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
