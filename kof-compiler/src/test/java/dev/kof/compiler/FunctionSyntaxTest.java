package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Function declaration syntax tests (no `fun` keyword).
 *
 * Kof declares functions without `fun`:
 *   main() { ... }                          // entry point, void implicit
 *   String saudacao() { ... }               // return type before the name
 *   saudacao(): String { ... }              // return type after parameters
 *   void fazIsso() { ... }                  // explicit void
 *   Bool positivo(Int x) = x > 0            // expression body
 *   int dobro(int x) { ... }                // primitive types in any case
 */
class FunctionSyntaxTest {

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

    private static final String ALL_FORMS = """
            String saudacao() {
                return "oi"
            }
            despedida(): String {
                return "tchau"
            }
            void fazIsso() {
                println("feito")
            }
            Bool positivo(Int x) = x > 0
            int dobro(int x) {
                return x * 2
            }
            main() {
                println(saudacao())
                println(despedida())
                fazIsso()
                println(positivo(3))
                println(dobro(21))
            }
            """;

    @Test
    void allFunctionFormsJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ALL_FORMS);
        runJvm(source, tempDir.resolve("out"), "oi\ntchau\nfeito\ntrue\n42");
    }

    @Test
    void allFunctionFormsNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ALL_FORMS);
        runNative(source, tempDir.resolve("out"), "oi\ntchau\nfeito\ntrue\n42");
    }

    private static final String CLASS_METHOD_FORMS = """
            class Calc {
                Int value
                void reset() {
                    value = 0
                }
                Int getValue() {
                    return value
                }
                Bool positivo(Int x) = x > 0
                String nome() {
                    return "calc"
                }
                emDobro(): Int {
                    return value * 2
                }
            }
            main() {
                var c = new Calc()
                c.reset()
                c.value = 10
                println(c.getValue())
                println(c.positivo(3))
                println(c.nome())
                println(c.emDobro())
            }
            """;

    @Test
    void classMethodFormsJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, CLASS_METHOD_FORMS);
        runJvm(source, tempDir.resolve("out"), "10\ntrue\ncalc\n20");
    }

    @Test
    void classMethodFormsNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, CLASS_METHOD_FORMS);
        runNative(source, tempDir.resolve("out"), "10\ntrue\ncalc\n20");
    }
}