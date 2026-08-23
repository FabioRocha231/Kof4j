package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Idiomatic language behavior end-to-end (JVM + Native):
 *
 * - name resolution by symbol, not declaration order (methods may call
 *   methods declared later);
 * - return type inference for expression-body methods;
 * - private members;
 * - field access and mutation without `this` (read, assign, ++/--);
 * - construction without `new`, including classes with no explicit
 *   constructor.
 */
class IdiomaticE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

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
        assumeTrue(isLinux(), "Native target runs on Linux");
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

    private void both(Path tempDir, String name, String program, String expected) throws IOException {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        runJvm(source, tempDir.resolve("jvm"), expected);
        runNative(source, tempDir.resolve("native"), expected);
    }

    @Test
    void methodCallBeforeDeclaration(@TempDir Path tempDir) throws IOException {
        both(tempDir, "order", """
            class Parser {
                parse() {
                    return tokenize()
                }
                tokenize() {
                    return "tok"
                }
            }
            main() {
                var p = Parser()
                println(p.parse())
            }
            """, "tok");
    }

    @Test
    void methodCallAfterDeclaration(@TempDir Path tempDir) throws IOException {
        both(tempDir, "order2", """
            class Parser {
                tokenize() {
                    return "tok"
                }
                parse() {
                    return tokenize()
                }
            }
            main() {
                var p = Parser()
                println(p.parse())
            }
            """, "tok");
    }

    @Test
    void privateMethodCalledFromSibling(@TempDir Path tempDir) throws IOException {
        both(tempDir, "priv", """
            class Parser {
                private tokenize() {
                    return "tok"
                }
                parse() {
                    return tokenize()
                }
            }
            main() {
                var p = Parser()
                println(p.parse())
            }
            """, "tok");
    }

    @Test
    void fieldAccessWithoutThis(@TempDir Path tempDir) throws IOException {
        both(tempDir, "fieldthis", """
            class Counter {
                Int count
                constructor(Int start) {
                    count = start
                }
                increment() {
                    count++
                }
                decrement() {
                    count--
                }
                value() {
                    return count
                }
            }
            main() {
                var c = Counter(5)
                c.increment()
                c.increment()
                c.decrement()
                println(c.value())
            }
            """, "6");
    }

    @Test
    void classWithoutConstructor(@TempDir Path tempDir) throws IOException {
        both(tempDir, "noctor", """
            class Empty {
                ping() {
                    return "pong"
                }
            }
            main() {
                var e = Empty()
                println(e.ping())
            }
            """, "pong");
    }

    @Test
    void compactClassConstruction(@TempDir Path tempDir) throws IOException {
        both(tempDir, "compact", """
            class User(String name, String email)
            main() {
                var u = User("Mel", "mel@kof.dev")
                println(u.name)
                println(u.email)
            }
            """, "Mel\nmel@kof.dev");
    }

    @Test
    void chainedSelfCalls(@TempDir Path tempDir) throws IOException {
        both(tempDir, "chain", """
            class Calc {
                Int value
                constructor() {
                    value = 1
                }
                add(Int n) {
                    value = value + n
                    return this
                }
                result() {
                    return value
                }
            }
            main() {
                var c = Calc()
                println(c.add(2).add(3).result())
            }
            """, "6");
    }
}