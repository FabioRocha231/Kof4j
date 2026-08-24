package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


/**
 * End-to-end tests for the Kof-native configuration module ({@code kof.config}).
 *
 * Sources, in precedence order: explicit file ({@code KOF_CONFIG}),
 * environment variable ({@code KOF_<KEY>}), profile file
 * ({@code kof.<KOF_PROFILE>.config}) and default file ({@code kof.config}).
 */
class KofConfigE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String run(Path tempDir, String kofSource, Map<String, String> env,
                       Path workDir) throws IOException {
        Path source = tempDir.resolve("Config.kf");
        Files.writeString(source, kofSource);
        Path outDir = tempDir.resolve("classes");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            if (env != null) {
                Map<String, String> pbEnv = pb.environment();
                for (var e : env.entrySet()) pbEnv.put(e.getKey(), e.getValue());
            }
            if (workDir != null) pb.directory(workDir.toFile());
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    @Test
    void envVarByConvention(@TempDir Path tempDir) throws IOException {
        String out = run(tempDir, """
                main() {
                    println(config.str("database.url", "default"))
                    println(config.int("server.port", 8080))
                    println(config.bool("app.debug", false))
                }
                """, Map.of("KOF_DATABASE_URL", "jdbc:h2:mem:test",
                        "KOF_SERVER_PORT", "9090",
                        "KOF_APP_DEBUG", "true"), null);
        assertEquals("jdbc:h2:mem:test\n9090\ntrue", out);
    }

    @Test
    void defaultsUsedWhenMissing(@TempDir Path tempDir) throws IOException {
        String out = run(tempDir, """
                main() {
                    println(config.str("missing.key", "fallback"))
                    println(config.int("missing.port", 1234))
                    println(config.bool("missing.flag", true))
                    println(config.long("missing.big", 9999999999))
                    println(config.has("missing.key"))
                }
                """, null, null);
        assertEquals("fallback\n1234\ntrue\n9999999999\nfalse", out);
    }

    @Test
    void explicitConfigFileWins(@TempDir Path tempDir) throws IOException {
        Path cfg = tempDir.resolve("app.config");
        Files.writeString(cfg, """
                # kof config
                server.port=18001
                app.name = webapp
                app.debug=false
                """);
        String out = run(tempDir, """
                main() {
                    println(config.int("server.port", 0))
                    println(config.str("app.name", "?"))
                    println(config.bool("app.debug", true))
                    println(config.has("server.port"))
                }
                """, Map.of("KOF_CONFIG", cfg.toString()), null);
        assertEquals("18001\nwebapp\nfalse\ntrue", out);
    }

    @Test
    void profileFile(@TempDir Path tempDir) throws IOException {
        Path work = tempDir.resolve("work");
        Files.createDirectories(work);
        Files.writeString(work.resolve("kof.prod.config"), "server.port=20000\n");
        Files.writeString(work.resolve("kof.config"), "server.port=10000\n");
        String out = run(tempDir, """
                main() {
                    println(config.int("server.port", 0))
                }
                """, Map.of("KOF_PROFILE", "prod"), work);
        assertEquals("20000", out);
    }

    @Test
    void defaultFileInWorkDir(@TempDir Path tempDir) throws IOException {
        Path work = tempDir.resolve("work");
        Files.createDirectories(work);
        Files.writeString(work.resolve("kof.config"), "server.port=10000\n");
        String out = run(tempDir, """
                main() {
                    println(config.int("server.port", 0))
                }
                """, null, work);
        assertEquals("10000", out);
    }

    @Test
    void envFunction(@TempDir Path tempDir) throws IOException {
        String out = run(tempDir, """
                main() {
                    println(config.env("KOF_DIRECT"))
                    println(config.env("KOF_ABSENT"))
                }
                """, Map.of("KOF_DIRECT", "hello"), null);
        assertEquals("hello\nnull", out);
    }

    @Test
    void explicitFileBeatsEnvAndProfile(@TempDir Path tempDir) throws IOException {
        Path cfg = tempDir.resolve("explicit.config");
        Files.writeString(cfg, "server.port=30000\n");
        Path work = tempDir.resolve("work");
        Files.createDirectories(work);
        Files.writeString(work.resolve("kof.config"), "server.port=10000\n");
        String out = run(tempDir, """
                main() {
                    println(config.int("server.port", 0))
                }
                """, Map.of("KOF_CONFIG", cfg.toString(), "KOF_SERVER_PORT", "40000"), work);
        assertEquals("30000", out);
    }

    @Test
    void nativeAndJsReportConf001(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Config.kf");
        Files.writeString(source, """
                main() {
                    println(config.int("server.port", 8080))
                }
                """);
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("native-out"), Target.NATIVE);
        assertFalse(nativeResult.success());
        assertTrue(nativeResult.diagnostics().getDiagnostics().toString().contains("CONF001"),
                nativeResult.diagnostics().getDiagnostics().toString());

        CompilationResult jsResult = driver.compile(source, tempDir.resolve("js-out"), Target.JS);
        assertFalse(jsResult.success());
        assertTrue(jsResult.diagnostics().getDiagnostics().toString().contains("CONF001"),
                jsResult.diagnostics().getDiagnostics().toString());
    }
}