package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NativeE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void nativeHelloWorld(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, "fun main() { println(\"Hello, Kof!\") }");
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed");
        // Check that the binary exists
        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
    }

    @Test
    void nativeArithmetic(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var x = 10
                var y = 20
                println(x + y)
            }
            """);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void nativeIfElse(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var x = 10
                if (x > 5) {
                    println("greater")
                } else {
                    println("smaller")
                }
            }
            """);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void nativeWhileLoop(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var i = 0
                while (i < 3) {
                    println(i)
                    i = i + 1
                }
            }
            """);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void nativeForLoop(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                for (var i = 0; i < 3; i++) {
                    println(i)
                }
            }
            """);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void nativeFunctionCall(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun add(Int a, Int b): Int {
                return a + b
            }
            fun main() {
                println(add(2, 3))
            }
            """);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void nativeRecordInstantiation(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record Point(Int x, Int y)
            fun main() {
                var p = Point(10, 20)
                println(p.x())
            }
            """);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed");
    }

    // ── Real Execution Tests ──────────────────────────────────────
    // These tests compile → assemble → link → RUN the native binary
    // and assert on stdout + exit code.

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

    @Test
    void execVirtualDispatchOverride(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                fun speak(): String = "animal"
            }
            class Dog extends Animal {
                fun speak(): String = "dog"
            }
            fun main() {
                var a = new Dog()
                println(a.speak())
            }
            """);
        runNative(source, tempDir.resolve("out"), "dog");
    }

    @Test
    void execVirtualDispatchNoOverride(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                fun speak(): String = "animal"
            }
            class Dog extends Animal {
            }
            fun main() {
                var a = new Dog()
                println(a.speak())
            }
            """);
        runNative(source, tempDir.resolve("out"), "animal");
    }

    @Test
    void execInstanceMethod(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class User {
                fun name(): String = "Mel"
            }
            fun main() {
                var user = new User()
                println(user.name())
            }
            """);
        runNative(source, tempDir.resolve("out"), "Mel");
    }

    @Test
    void execFieldAssignment(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class User {
                String name
            }
            fun main() {
                var u = new User()
                u.name = "Mel"
                println(u.name)
            }
            """);
        runNative(source, tempDir.resolve("out"), "Mel");
    }

    @Test
    void execVirtualDispatchWithArg(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                fun describe(Int n): String = "animal"
            }
            class Dog extends Animal {
                fun describe(Int n): String = "dog"
            }
            fun main() {
                var a = new Dog()
                println(a.describe(7))
            }
            """);
        runNative(source, tempDir.resolve("out"), "dog");
    }

    @Test
    void execStringLength(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var s = "Hello"
                println(s.length)
            }
            """);
        runNative(source, tempDir.resolve("out"), "5");
    }

    @Test
    void execStringCharAt(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var s = "Hello"
                println(s.charAt(0))
                println(s.charAt(4))
            }
            """);
        runNative(source, tempDir.resolve("out"), "72\n111");
    }

    @Test
    void execStringSubstring(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var s = "Hello"
                println(s.substring(1, 4))
            }
            """);
        runNative(source, tempDir.resolve("out"), "ell");
    }

    @Test
    void execStringContains(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var s = "Hello"
                println(s.contains("ell"))
            }
            """);
        runNative(source, tempDir.resolve("out"), "true");
    }

    @Test
    void execStringStartsWith(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var s = "Hello"
                println(s.startsWith("He"))
            }
            """);
        runNative(source, tempDir.resolve("out"), "true");
    }

    @Test
    void execStringEndsWith(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var s = "Hello"
                println(s.endsWith("lo"))
            }
            """);
        runNative(source, tempDir.resolve("out"), "true");
    }

    @Test
    void execStringConcat(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var s = "Hello"
                println(s.concat(" World"))
            }
            """);
        runNative(source, tempDir.resolve("out"), "Hello World");
    }

    @Test
    void execNegativeInt(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                println(-42)
            }
            """);
        runNative(source, tempDir.resolve("out"), "-42");
    }

    @Test
    void execInstanceOf(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
            }
            class Dog extends Animal {
            }
            fun main() {
                var a = new Dog()
                println(a instanceof Dog)
                println(a instanceof Animal)
            }
            """);
        runNative(source, tempDir.resolve("out"), "true\ntrue");
    }

    @Test
    void execIfElse(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var x = 10
                if (x > 5) {
                    println("greater")
                } else {
                    println("smaller")
                }
            }
            """);
        runNative(source, tempDir.resolve("out"), "greater");
    }

    @Test
    void execWhileLoopRuns(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var i = 0
                while (i < 3) {
                    println(i)
                    i = i + 1
                }
            }
            """);
        runNative(source, tempDir.resolve("out"), "0\n1\n2");
    }

    @Test
    void execForLoopRuns(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                for (var i = 0; i < 3; i++) {
                    println(i)
                }
            }
            """);
        runNative(source, tempDir.resolve("out"), "0\n1\n2");
    }

    @Test
    void execBreak(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var i = 0
                while (true) {
                    if (i == 3) { break }
                    println(i)
                    i = i + 1
                }
            }
            """);
        runNative(source, tempDir.resolve("out"), "0\n1\n2");
    }

    @Test
    void execContinue(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var i = 0
                while (i < 5) {
                    i = i + 1
                    if (i == 2) { continue }
                    println(i)
                }
            }
            """);
        runNative(source, tempDir.resolve("out"), "1\n3\n4\n5");
    }

    @Test
    void execStringEquals(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var a = "Hello"
                var b = "Hello"
                println(a == b)
            }
            """);
        runNative(source, tempDir.resolve("out"), "true");
    }

    @Test
    void execIntComparison(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                println(3 < 5)
                println(3 > 5)
                println(3 == 3)
                println(3 != 4)
            }
            """);
        runNative(source, tempDir.resolve("out"), "true\nfalse\ntrue\ntrue");
    }

    @Test
    void execSwitch(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var x = 2
                switch (x) {
                    case 1: println("one")
                    case 2: println("two")
                    default: println("other")
                }
            }
            """);
        runNative(source, tempDir.resolve("out"), "two");
    }

    @Test
    void execDoWhile(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var i = 0
                do {
                    println(i)
                    i = i + 1
                } while (i < 3)
            }
            """);
        runNative(source, tempDir.resolve("out"), "0\n1\n2");
    }

    @Test
    void execArray(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var arr = new Int[3]
                arr[0] = 10
                arr[1] = 20
                arr[2] = 30
                println(arr[1])
                println(arr.length)
            }
            """);
        runNative(source, tempDir.resolve("out"), "20\n3");
    }

    @Test
    void execRecursion(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun fib(Int n): Int {
                if (n <= 1) { return n }
                return fib(n - 1) + fib(n - 2)
            }
            fun main() {
                println(fib(10))
            }
            """);
        runNative(source, tempDir.resolve("out"), "55");
    }

    @Test
    void execSubtraction(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                println(10 - 3)
                println(3 - 10)
                println(2 - 1 - 1)
            }
            """);
        runNative(source, tempDir.resolve("out"), "7\n-7\n0");
    }
}
