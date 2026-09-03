package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Regressão do null-safety `Type?` + narrowing (`if (x != null)`).
 *
 * 02/09: antes do fix o narrowing compilava mas o JVM emitia
 * `getfield "?".length` (owner "?" inválido) para `String?.length` →
 * erro de launcher/verificação. O idioma documentado no corpus
 * (`training/idioms/strings.md`, `errors.md`) agora roda de verdade.
 */
class NullSafetyE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void nullablePropertyAccessJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            String? maybe() {
                return "kof"
            }
            main() {
                var s = maybe()
                if (s != null) {
                    println(s.length)
                    println(s.substring(0, 2))
                }
                println("done")
            }
            """, "3\nko\ndone");
    }

    @Test
    void nullableNullBranchJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            String? maybe(Bool yes) {
                if (yes) return "abc"
                return null
            }
            main() {
                var s = maybe(false)
                if (s != null) {
                    println("non-null")
                } else {
                    println("null")
                }
                var t = maybe(true)
                if (t != null) {
                    println(t.length)
                }
                println("done")
            }
            """, "null\n3\ndone");
    }

    @Test
    void nullableParamPassingJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            String shout(String s) {
                return s.toUpperCase()
            }
            main() {
                String? name = "mel"
                if (name != null) {
                    println(shout(name))
                }
                println("done")
            }
            """, "MEL\ndone");
    }

    @Test
    void nullableMapGetNarrowingNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                var m = mapOf("k", "v")
                var v = m.get("k")
                if (v != null) {
                    println(v.length)
                } else {
                    println("null")
                }
                println("done")
            }
            """, "1\ndone");
    }

    @Test
    void nullableReadTextNarrowingJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                var t = readFile("/definitely/missing/file.txt")
                if (t != null) {
                    println("content")
                } else {
                    println("missing")
                }
                println("done")
            }
            """, "missing\ndone");
    }

    @Test
    void readLineEofIsNullJvm(@TempDir Path tmp) throws Exception {
        runJvmEmptyStdin(tmp, """
            main() {
                var line = readLine()
                if (line == null) {
                    println("eof")
                } else {
                    println("got:" + line)
                }
                println("done")
            }
            """, "eof\ndone");
    }

    @Test
    void readLineEofIsNullNative(@TempDir Path tmp) throws Exception {
        runNativeEmptyStdin(tmp, """
            main() {
                var line = readLine()
                if (line == null) {
                    println("eof")
                } else {
                    println("got:" + line)
                }
                println("done")
            }
            """, "eof\ndone");
    }

    private String runJvm(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runJvmEmptyStdin(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
            p.getOutputStream().close();   // EOF no stdin do filho
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runNativeEmptyStdin(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile failed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        try {
            Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
            p.getOutputStream().close();   // EOF no stdin do filho
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit code, output: " + output);
            assertEquals(expected, output, "Native output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runNative(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile failed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        try {
            Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit code, output: " + output);
            assertEquals(expected, output, "Native output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }
}