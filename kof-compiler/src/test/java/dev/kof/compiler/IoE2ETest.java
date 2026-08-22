package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof.io end-to-end tests: File, Path and Directory over the real filesystem.
 *
 * Every test runs the compiled program twice — JVM and Native — against its
 * own @TempDir (no /tmp or C:\ assumptions, CI-safe on Linux/macOS/Windows).
 * JVM and Native must observe the same semantics.
 */
class IoE2ETest {

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

    private void both(Path tempDir, String name, String body, String expected) throws IOException {
        Path sourceJvm = tempDir.resolve(name + "Jvm.kf");
        Files.writeString(sourceJvm, "main() {\n" + body.formatted(q(tempDir.resolve("jvm").toString())) + "\n}");
        runJvm(sourceJvm, tempDir.resolve("jvm-out"), expected);
        Path sourceNative = tempDir.resolve(name + "Native.kf");
        Files.writeString(sourceNative, "main() {\n" + body.formatted(q(tempDir.resolve("native").toString())) + "\n}");
        runNative(sourceNative, tempDir.resolve("native-out"), expected);
    }

    private static String q(String dir) {
        return dir.replace("\\", "\\\\");
    }

    private static String base(String dir, String sub) {
        return q(dir + java.io.File.separator + sub);
    }

    @Test
    void fileTextRoundTrip(@TempDir Path tempDir) throws IOException {
        String dir = q(tempDir.toString());
        both(tempDir, "fileText", """
            var f = File("%s/hello.txt")
            println(f.writeText("Hello Kof"))
            println(f.exists())
            println(f.isFile())
            println(f.readText())
            println(f.size())
            """.formatted(dir), "true\ntrue\ntrue\nHello Kof\n9");
    }

    @Test
    void fileAppendText(@TempDir Path tempDir) throws IOException {
        String dir = q(tempDir.toString());
        both(tempDir, "fileAppend", """
            var f = File("%s/append.txt")
            f.writeText("a")
            println(f.appendText("b"))
            println(f.appendText("c"))
            println(f.readText())
            """.formatted(dir), "true\ntrue\ntrue\nabc");
    }

    @Test
    void fileUnicode(@TempDir Path tempDir) throws IOException {
        String dir = q(tempDir.toString());
        both(tempDir, "fileUnicode", """
            var f = File("%s/uni.txt")
            var texto = "Olá Kof\\n日本語\\n中文\\n😀"
            f.writeText(texto)
            println(f.readText())
            """.formatted(dir), "Olá Kof\n日本語\n中文\n😀");
    }

    @Test
    void fileEmpty(@TempDir Path tempDir) throws IOException {
        String dir = q(tempDir.toString());
        both(tempDir, "fileEmpty", """
            var f = File("%s/empty.txt")
            f.writeText("")
            println(f.size())
            println(f.readText() == "")
            """.formatted(dir), "0\ntrue");
    }

    @Test
    void fileLarge(@TempDir Path tempDir) throws IOException {
        String dir = q(tempDir.toString());
        both(tempDir, "fileLarge", """
            var f = File("%s/large.txt")
            var chunk = "0123456789abcdef"
            var i = 0
            while (i < 200000) {
                f.appendText(chunk)
                i = i + 1
            }
            println(f.size())
            """.formatted(dir), "3200000");
    }

    @Test
    void fileBytes(@TempDir Path tempDir) throws IOException {
        String dir = q(tempDir.toString());
        both(tempDir, "fileBytes", """
            var f = File("%s/data.bin")
            var b = new Int[4]
            b[0] = 65
            b[1] = 0
            b[2] = 255
            b[3] = 66
            println(f.writeBytes(b))
            println(f.readBytes().length)
            println(f.readBytes()[0])
            println(f.readBytes()[1])
            println(f.readBytes()[2])
            println(f.appendBytes(b))
            println(f.size())
            """.formatted(dir), "true\n4\n65\n0\n255\ntrue\n8");
    }

    @Test
    void fileDelete(@TempDir Path tempDir) throws IOException {
        String dir = q(tempDir.toString());
        both(tempDir, "fileDelete", """
            var f = File("%s/tmp.txt")
            f.writeText("x")
            println(f.delete())
            println(f.exists())
            println(f.delete())
            """.formatted(dir), "true\nfalse\nfalse");
    }

    @Test
    void fileMissing(@TempDir Path tempDir) throws IOException {
        String dir = q(tempDir.toString());
        both(tempDir, "fileMissing", """
            var f = File("%s/nope.txt")
            println(f.exists())
            println(f.isFile())
            println(f.size())
            """.formatted(dir), "false\nfalse\n-1");
    }

    @Test
    void directoryBasics(@TempDir Path tempDir) throws IOException {
        String dir = q(tempDir.toString());
        both(tempDir, "dirBasics", """
            var d = Directory("%s/data")
            println(d.exists())
            println(d.create())
            println(d.exists())
            println(d.isDirectory())
            println(d.create())
            """.formatted(dir), "false\ntrue\ntrue\ntrue\nfalse");
    }

    @Test
    void directoryCreateDirectories(@TempDir Path tempDir) throws IOException {
        String dir = q(tempDir.toString());
        both(tempDir, "dirCreateDirs", """
            var p = Path("%s/a/b/c")
            println(p.createDirectories())
            var d = Directory("%s/a/b/c")
            println(d.isDirectory())
            """.formatted(dir, dir), "true\ntrue");
    }

    @Test
    void directoryList(@TempDir Path tempDir) throws IOException {
        String dir = q(tempDir.toString());
        both(tempDir, "dirList", """
            var d = Directory("%s/listing")
            d.createDirectories()
            File("%s/listing/a.txt").writeText("1")
            File("%s/listing/b.txt").writeText("2")
            File("%s/listing/c.txt").writeText("3")
            var total = 0
            for (var entry in d.list()) {
                println(entry.name)
                total = total + 1
            }
            println(total)
            """.formatted(dir, dir, dir, dir), "a.txt\nb.txt\nc.txt\n3");
    }

    @Test
    void directoryDelete(@TempDir Path tempDir) throws IOException {
        String dir = q(tempDir.toString());
        both(tempDir, "dirDelete", """
            var d = Directory("%s/rmdir")
            d.create()
            println(d.delete())
            println(d.exists())
            println(d.delete())
            """.formatted(dir), "true\nfalse\nfalse");
    }

    @Test
    void pathOperations(@TempDir Path tempDir) throws IOException {
        String dir = q(tempDir.toString());
        both(tempDir, "pathOps", """
            var p = Path("data/users/mel.txt")
            println(p.fileName())
            println(p.extension())
            println(p.parent().fileName())
            println(p.resolve("extra.txt"))
            println(Path("a/./b/../c").normalize())
            println(p.isAbsolute())
            println(p.toAbsolute().isAbsolute())
            """.formatted(dir), "mel.txt\ntxt\nusers\ndata/users/mel.txt/extra.txt\na/c\nfalse\ntrue");
    }

    @Test
    void pathNormalize(@TempDir Path tempDir) throws IOException {
        both(tempDir, "pathNorm", """
            println(Path("foo/../bar").normalize())
            println(Path("/a/../b").normalize())
            println(Path("a//b").normalize())
            println(Path("a/b/../..").normalize())
            """.formatted(), "bar\n/b\na/b\n.");
    }

    @Test
    void pathParentChains(@TempDir Path tempDir) throws IOException {
        both(tempDir, "pathChain", """
            var p = Path("data/users.txt")
            p.parent().createDirectories()
            p.writeText("Mel")
            println(File("data/users.txt").readText())
            println(p.size())
            """.formatted(), "Mel\n3");
    }
}