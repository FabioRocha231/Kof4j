package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Idiomatic consolidation: field initializers, unicode escapes and
 * typed empty List construction (items 4, 10, 9 of the idiom guidelines).
 */
class IdiomaticCoreE2ETest {

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

    private static final String FIELD_INITIALIZERS = """
            class Config {
                String host = "localhost"
                Int port = 8080
                Long timeout = 5000
                Bool debug = true
            }
            class Sub extends Config {
                String name = "sub"
            }
            main() {
                var c = Config()
                println(c.host)
                println(c.port)
                println(c.timeout)
                println(c.debug)
                var s = Sub()
                println(s.host)
                println(s.name)
            }
            """;

    @Test
    void fieldInitializersJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, FIELD_INITIALIZERS);
        runJvm(source, tempDir.resolve("out"), "localhost\n8080\n5000\ntrue\nlocalhost\nsub");
    }

    @Test
    void fieldInitializersNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, FIELD_INITIALIZERS);
        runNative(source, tempDir.resolve("out"), "localhost\n8080\n5000\ntrue\nlocalhost\nsub");
    }

    private static final String UNICODE_ESCAPES = """
            main() {
                println("linha\\nquebra")
                println("tab\\tsep")
                println("esc:\\u001b[31mvermelho")
                println("aspas: \\"")
                println("barra: \\\\")
            }
            """;

    @Test
    void unicodeEscapesJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, UNICODE_ESCAPES);
        runJvm(source, tempDir.resolve("out"), "linha\nquebra\ntab\tsep\nesc:\u001b[31mvermelho\naspas: \"\nbarra: \\");
    }

    @Test
    void unicodeEscapesNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, UNICODE_ESCAPES);
        runNative(source, tempDir.resolve("out"), "linha\nquebra\ntab\tsep\nesc:\u001b[31mvermelho\naspas: \"\nbarra: \\");
    }

    private static final String TYPED_EMPTY_LIST = """
            class User(String name, Int age)
            main() {
                var users = listOf<User>()
                users.add(User("Mel", 30))
                users.add(User("Ana", 25))
                var u = users.get(0)
                println(u.name)
                println(u.age)
                var total = 0
                for (var user in users) {
                    total = total + user.age
                }
                println(total)
            }
            """;

    @Test
    void typedEmptyListJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, TYPED_EMPTY_LIST);
        runJvm(source, tempDir.resolve("out"), "Mel\n30\n55");
    }

    @Test
    void typedEmptyListNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, TYPED_EMPTY_LIST);
        runNative(source, tempDir.resolve("out"), "Mel\n30\n55");
    }
}