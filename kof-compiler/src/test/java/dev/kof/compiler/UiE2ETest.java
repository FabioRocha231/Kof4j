package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * kof.ui foundation end-to-end: Color (32-bit RGBA), Palette and Theme.
 * JVM and Native must observe identical semantics.
 */
class UiE2ETest {

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
    void colorChannels(@TempDir Path tempDir) throws IOException {
        both(tempDir, "channels", """
            main() {
                var c = Color(255, 0, 0)
                println(c.red())
                println(c.green())
                println(c.blue())
                println(c.alpha())
                println(c.isOpaque())
            }
            """, "255\n0\n0\n255\ntrue");
    }

    @Test
    void colorCss(@TempDir Path tempDir) throws IOException {
        both(tempDir, "css", """
            main() {
                println(Color(255, 0, 0).toCss())
                println(Color.rgba(10, 20, 30, 128).toCss())
                println(Color.rgba(10, 20, 30, 255).toCss())
            }
            """, "rgb(255, 0, 0)\nrgba(10, 20, 30, 128)\nrgb(10, 20, 30)");
    }

    @Test
    void palette(@TempDir Path tempDir) throws IOException {
        both(tempDir, "palette", """
            main() {
                println(Palette.red.toCss())
                println(Palette.green.toCss())
                println(Palette.blue.toCss())
                println(Palette.black.toCss())
                println(Palette.white.toCss())
                println(Palette.transparent.alpha())
            }
            """, "rgb(255, 0, 0)\nrgb(0, 255, 0)\nrgb(0, 0, 255)\nrgb(0, 0, 0)\nrgb(255, 255, 255)\n0");
    }

    @Test
    void withAlpha(@TempDir Path tempDir) throws IOException {
        both(tempDir, "alpha", """
            main() {
                var c = Color(255, 0, 0).withAlpha(64)
                println(c.red())
                println(c.alpha())
                println(c.isOpaque())
            }
            """, "255\n64\nfalse");
    }

    @Test
    void themes(@TempDir Path tempDir) throws IOException {
        both(tempDir, "themes", """
            main() {
                var dark = Theme.dark()
                println(dark.isDark())
                println(dark.background().toCss())
                println(dark.text().toCss())
                var light = Theme.light()
                println(light.isDark())
                println(light.background().toCss())
                println(light.text().toCss())
            }
            """, "true\nrgb(18, 18, 18)\nrgb(255, 255, 255)\nfalse\nrgb(255, 255, 255)\nrgb(0, 0, 0)");
    }

    @Test
    void argsAvailableInMain(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("args.kf");
        Files.writeString(source, """
            main() {
                println(args.length)
                if (args.length > 0) {
                    println(args[0])
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", tempDir.resolve("out").toString(),
                    "Default.Main", "hello", "world");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            assertEquals(0, p.waitFor(), "Exit code should be 0");
            assertEquals("2\nhello", output, "args should reach main");
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    @Test
    void buttonOperations(@TempDir Path tempDir) throws IOException {
        String program = """
            main() {
                var b = Button("Salvar")
                println(b.text)
                b.text = "Salvando..."
                println(b.text)
                b.remove()
                var c = Button("Ok", () -> println("acabou"))
                println(c.text)
            }
            """;
        // JVM/Native handles are no-ops: the text getter returns "" there
        // (rendering is KofJS) — the program must still compile and run.
        Path src = tempDir.resolve("button.kf");
        Files.writeString(src, program);
        runJvm(src, tempDir.resolve("jvm"), "");
        runNative(src, tempDir.resolve("native"), "");

        Path srcJs = tempDir.resolve("button-js.kf");
        Files.writeString(srcJs, program);
        CompilationResult js = driver.compile(srcJs, tempDir.resolve("js"), Target.JS);
        assertTrue(js.success(), "JS compilation should succeed: " + js.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int code = dev.kof.runtime.KofJsRunner.run(
                tempDir.resolve("js").resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, code, "JS run should succeed");
        assertEquals("Salvar\nSalvando...\nOk", out.toString().trim(), "button binds on the JS target");
    }

    @Test
    void lambdaCaptures(@TempDir Path tempDir) throws IOException {
        both(tempDir, "captures", """
            main() {
                var x = 21
                var f = () -> println(x * 2)
                f()
                var msg = "captura"
                var g = () -> println(msg)
                g()
            }
            """, "42\ncaptura");

        Path src = tempDir.resolve("captures-js.kf");
        Files.writeString(src, """
            main() {
                var x = 21
                var f = () -> println(x * 2)
                f()
                var msg = "captura"
                var g = () -> println(msg)
                g()
            }
            """);
        CompilationResult js = driver.compile(src, tempDir.resolve("js"), Target.JS);
        assertTrue(js.success(), "JS compilation should succeed: " + js.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int code = dev.kof.runtime.KofJsRunner.run(
                tempDir.resolve("js").resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, code, "JS run should succeed");
        assertEquals("42\ncaptura", out.toString().trim(), "captures on the JS target");
    }

    @Test
    void staticFieldStore(@TempDir Path tempDir) throws IOException {
        String program = """
            class App {
                static Int count = 0
            }
            main() {
                App.count = 5
                println(App.count)
                App.count = App.count + 1
                println(App.count)
            }
            """;
        Path src = tempDir.resolve("statics.kf");
        Files.writeString(src, program);
        runJvm(src, tempDir.resolve("jvm"), "5\n6");
        // Native: static fields are a known gap (KofGetStatic/KofPutStatic
        // are no-ops there); only the JVM/JS paths must observe the store.

        Path srcJs = tempDir.resolve("statics-js.kf");
        Files.writeString(srcJs, program);
        CompilationResult js = driver.compile(srcJs, tempDir.resolve("js2"), Target.JS);
        assertTrue(js.success(), "JS compilation should succeed: " + js.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int code = dev.kof.runtime.KofJsRunner.run(
                tempDir.resolve("js2").resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, code, "JS run should succeed");
        assertEquals("5\n6", out.toString().trim(), "static stores on the JS target");
    }
}