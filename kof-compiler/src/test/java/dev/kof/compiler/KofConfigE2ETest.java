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
    void nativeAndJsRunConfig(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Config.kf");
        Files.writeString(source, """
                main() {
                    println(config.int("server.port", 8080))
                    println(config.str("app.name", "fallback"))
                }
                """);
        // Native: config implementado em asm (kof_config_lookup) — roda de verdade
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "" + nativeResult.diagnostics().getDiagnostics());
        Path bin = tempDir.resolve("native-out/Default/Main");
        assertTrue(Files.exists(bin), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit: " + output);
            assertTrue(output.contains("8080"), "Native output: " + output);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
        // JS: config via process.env
        CompilationResult jsResult = driver.compile(source, tempDir.resolve("js-out"), Target.JS);
        assertTrue(jsResult.success(), "JS should support config: " + jsResult.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            Path jsEntry = findJsEntry(tempDir.resolve("js-out"));
            int ec = dev.kof.runtime.KofJsRunner.run(jsEntry, buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String out = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit code, output: " + out);
            assertTrue(out.contains("8080"), "JS output: " + out);
        }
    }

    @Test
    void requiredKeyPresentAllTargets(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Config.kf");
        Files.writeString(source, """
                main() {
                    println(config.required("database.url"))
                }
                """);
        // JVM
        String out = run(tempDir, Files.readString(source),
                Map.of("KOF_DATABASE_URL", "jdbc:h2:mem:req"), null);
        assertTrue(out.contains("jdbc:h2:mem:req"), "JVM output: " + out);
        // Native
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "" + nativeResult.diagnostics().getDiagnostics());
        Path bin = tempDir.resolve("native-out/Default/Main");
        assertTrue(Files.exists(bin), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            pb.environment().put("KOF_DATABASE_URL", "jdbc:h2:mem:req");
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit: " + output);
            assertTrue(output.contains("jdbc:h2:mem:req"), "Native output: " + output);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
        // JS: config via arquivo kof.config no CWD do processo (o runner JS
        // herda o CWD do surefire — escrevemos o arquivo e limpamos no fim)
        CompilationResult jsResult = driver.compile(source, tempDir.resolve("js-out"), Target.JS);
        assertTrue(jsResult.success(), "JS: " + jsResult.diagnostics().getDiagnostics());
        Path cwdConfig = Path.of("kof.config").toAbsolutePath();
        Files.writeString(cwdConfig, "database.url = jdbc:h2:mem:req\n");
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
             java.io.ByteArrayOutputStream errBuf = new java.io.ByteArrayOutputStream()) {
            Path jsEntry = findJsEntry(tempDir.resolve("js-out"));
            int ec = dev.kof.runtime.KofJsRunner.run(jsEntry, buf,
                    java.io.InputStream.nullInputStream(), errBuf);
            String outJs = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            String errJs = errBuf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit, out: " + outJs + " err: " + errJs);
            assertTrue(outJs.contains("jdbc:h2:mem:req"), "JS output: " + outJs + " err: " + errJs);
        } finally {
            Files.deleteIfExists(cwdConfig);
        }
    }

    @Test
    void requiredKeyMissingFailsFast(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Config.kf");
        Files.writeString(source, """
                main() {
                    println(config.required("app.missing.key"))
                }
                """);
        // JVM: deve falhar com mensagem clara (não null silencioso)
        CompilationResult result = driver.compile(source, tempDir.resolve("classes"), Target.JVM);
        assertTrue(result.success(), "" + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp",
                    tempDir.resolve("classes").toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertNotEquals(0, ec, "Should fail with missing key, output: '" + output + "'");
            assertTrue(output.contains("app.missing.key"),
                    "Error must name the missing key, output: '" + output + "'");
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }

        // Native: panic no runtime asm
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(), "" + nativeResult.diagnostics().getDiagnostics());
        Path bin = tempDir.resolve("native-out/Default/Main");
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertNotEquals(0, ec, "Native should panic on missing key, output: " + output);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    @Test
    void interpolationResolvesReferences(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Config.kf");
        Files.writeString(source, """
                main() {
                    println(config.str("db.url", "MISS"))
                    println(config.str("a", "MISS"))
                    println(config.str("cyclic", "MISS"))
                }
                """);
        Path cfg = tempDir.resolve("work/kof.config");
        Files.createDirectories(cfg.getParent());
        Files.writeString(cfg, """
                # P2: interpolação ${key}
                db.host = localhost
                db.port = 5432
                db.url = jdbc:pg://${db.host}:${db.port}/app
                a = ${db.host}
                b = x
                c = ${b}
                cyclic = ${cyclic}
                missing = ${no.such.key}
                """);
        // JVM
        String out = run(tempDir, Files.readString(source), Map.of(), tempDir.resolve("work"));
        assertTrue(out.contains("jdbc:pg://localhost:5432/app"),
                "JVM interpolação composta, out: " + out);
        assertTrue(out.contains("localhost"), "JVM ref simples, out: " + out);
        assertTrue(out.contains("${cyclic}"),
                "JVM ciclo -> valor literal inalterado, out: " + out);
    }

    private static Path findJsEntry(Path dir) throws IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs"))
                    .findFirst().orElseThrow(() -> new IOException("no .mjs in " + dir));
        }
    }
}