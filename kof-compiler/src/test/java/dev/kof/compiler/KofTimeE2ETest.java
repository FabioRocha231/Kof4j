package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;


/**
 * End-to-end tests for {@code kof.time} — sleep, now e scheduler.
 */
class KofTimeE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path tempDir, String kofSource, String expected) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, kofSource);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-Dfile.encoding=UTF-8",
                    "-Dstdout.encoding=UTF-8", "-cp", outDir.toString(), "Default.Main");
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

    @Test
    void sleepPausesForMs(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, """
                main() {
                    var t0 = time.now()
                    time.sleep(250)
                    var t1 = time.now()
                    println(t1 - t0 >= 200)
                }
                """, "true");
    }

    @Test
    void intervalRunsPeriodicallyUntilCancelled(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, """
                main() {
                    var ticks = 0
                    var job = time.interval(100, () -> {
                        ticks = ticks + 1
                    })
                    time.sleep(450)
                    time.cancel(job)
                    var after = ticks
                    time.sleep(300)
                    println(ticks == after)
                    println(ticks >= 2)
                }
                """, "true\ntrue");
    }

    @Test
    void nowReturnsEpochMillis(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, """
                main() {
                    println(time.now() > 1700000000000)
                }
                """, "true");
    }

    @Test
    void nativeAndJsReportTime001(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    time.sleep(10)
                }
                """);
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("native"), Target.NATIVE);
        assertFalse(nativeResult.success(), "Native should reject time.sleep");
        assertTrue(nativeResult.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> d.message().contains("TIME001")), "" + nativeResult.diagnostics().getDiagnostics());
        CompilationResult jsResult = driver.compile(source, tempDir.resolve("js"), Target.JS);
        assertFalse(jsResult.success(), "JS should reject time.sleep");
        assertTrue(jsResult.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> d.message().contains("TIME001")), "" + jsResult.diagnostics().getDiagnostics());
    }
}