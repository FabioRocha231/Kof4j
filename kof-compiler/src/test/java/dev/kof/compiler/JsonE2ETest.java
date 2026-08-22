package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JSON end-to-end tests: encode/decode parity between JVM and Native.
 *
 * Covers: int, long, bool, String (with escapes), List<Int>, List<String>,
 * int arrays, object encode/decode (JVM only), record encode/decode (JVM only),
 * and the diagnostic guards for unsupported combinations.
 */
class JsonE2ETest {

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

    // ── JVM ───────────────────────────────────────────────────────

    @Test
    void jvmEncodePrimitives(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                println(json.encode(42))
                println(json.encode(true))
                println(json.encode("hi \\"there\\""))
                println(json.encode(9000000000))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "42\ntrue\n\"hi \\\"there\\\"\"\n9000000000");
    }

    @Test
    void jvmEncodeDecodeLists(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                println(json.encode(listOf(1, 2, 3)))
                println(json.encode(listOf("a", "b")))
                var dl = json.decode<List<Int>>("[1, 2, 3]")
                println(dl.size())
                println(dl.get(0) + dl.get(2))
                var dl2 = json.decode<List<String>>("[\\"x\\", \\"y\\"]")
                println(dl2.get(1))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "[1,2,3]\n[\"a\",\"b\"]\n3\n4\ny");
    }

    @Test
    void jvmEncodeArray(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var arr = new Int[3]
                arr[0] = 7
                arr[1] = 8
                arr[2] = 9
                println(json.encode(arr))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "[7,8,9]");
    }

    @Test
    void jvmDecodeScalars(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                println(json.decode<Int>("77"))
                println(json.decode<Bool>("true"))
                println(json.decode<String>("\\"hello\\""))
                println(json.decode<Long>("9000000000"))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "77\ntrue\nhello\n9000000000");
    }

    @Test
    void jvmEncodeDecodeObject(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class User {
                String name
                Int age
            }
            fun main() {
                var u = new User()
                u.name = "Mel"
                u.age = 30
                println(json.encode(u))
                var du = json.decode<User>("{\\"name\\": \\"Ana\\", \\"age\\": 25}")
                println(du.name)
                println(du.age)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "{\"name\":\"Mel\",\"age\":30}\nAna\n25");
    }

    @Test
    void jvmEncodeDecodeRecord(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record Point(Int x, Int y)
            fun main() {
                var p = new Point(3, 4)
                println(json.encode(p))
                var dp = json.decode<Point>("{\\"x\\": 10, \\"y\\": 20}")
                println(dp.x)
                println(dp.y)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "{\"x\":3,\"y\":4}\n10\n20");
    }

    // ── Native ────────────────────────────────────────────────────

    @Test
    void nativeEncodePrimitives(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                println(json.encode(42))
                println(json.encode(true))
                println(json.encode("hi"))
            }
            """);
        runNative(source, tempDir.resolve("out"), "42\ntrue\n\"hi\"");
    }

    @Test
    void nativeEncodeDecodeLists(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                println(json.encode(listOf(1, 2, 3)))
                println(json.encode(listOf("a", "b")))
                var dl = json.decode<List<Int>>("[1, 2, 3]")
                println(dl.size())
                println(dl.get(0) + dl.get(2))
                var dl2 = json.decode<List<String>>("[\\"x\\", \\"y\\"]")
                println(dl2.get(1))
            }
            """);
        runNative(source, tempDir.resolve("out"), "[1,2,3]\n[\"a\",\"b\"]\n3\n4\ny");
    }

    @Test
    void nativeEncodeArray(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var arr = new Int[3]
                arr[0] = 7
                arr[1] = 8
                arr[2] = 9
                println(json.encode(arr))
            }
            """);
        runNative(source, tempDir.resolve("out"), "[7,8,9]");
    }

    @Test
    void nativeDecodeScalars(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                println(json.decode<Int>("77"))
                println(json.decode<Bool>("true"))
                println(json.decode<String>("\\"hello\\""))
            }
            """);
        runNative(source, tempDir.resolve("out"), "77\ntrue\nhello");
    }

    // ── Diagnostics ───────────────────────────────────────────────

    @Test
    void floatNotSupported(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                println(json.encode(1.5))
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "Float encode should be rejected");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> d.code().equals("JSN001")), "Should report JSN001");
    }

    @Test
    void nativeObjectNotSupported(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class User {
                String name
            }
            fun main() {
                var u = new User()
                println(json.encode(u))
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertFalse(result.success(), "Native object encode should be rejected");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> d.code().equals("JSN002")), "Should report JSN002");
    }

    @Test
    void decodeArrayNotSupported(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var a = json.decode<Int[]>("[1, 2]")
                println(a.length)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "Array decode should be rejected");
    }
}