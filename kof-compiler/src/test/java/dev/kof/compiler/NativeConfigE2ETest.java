package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof.config no target Native — mesma semântica do JVM (KofConfigE2ETest):
 * precedência KOF_CONFIG &gt; env KOF_&lt;KEY&gt; &gt; perfil/kof.config no
 * diretório de trabalho, typed str/int com default em valor inválido,
 * trim nas bordas e linhas "#" comentadas.
 */
class NativeConfigE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    /**
     * Compila para nativo e roda o binário com cwd = tempDir/work-&lt;nome&gt;.
     * O callback opcional roda depois de criar esse diretório — é onde os
     * testes gravam os arquivos kof.config / kof.&lt;perfil&gt;.config.
     */
    private String runNative(Path tempDir, String name, String kofSource,
                             Map<String, String> env, Consumer<Path> workSetup) throws IOException {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, kofSource);
        Path work = tempDir.resolve("work-" + name);
        Files.createDirectories(work);
        if (workSetup != null) workSetup.accept(work);
        Path outDir = tempDir.resolve("native-" + name);
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.directory(work.toFile());
            pb.redirectErrorStream(true);
            if (env != null) {
                for (var e : env.entrySet()) pb.environment().put(e.getKey(), e.getValue());
            }
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    private String runNative(Path tempDir, String name, String kofSource,
                             Map<String, String> env) throws IOException {
        return runNative(tempDir, name, kofSource, env, null);
    }

    private static final String PROBE = """
            main() {
                println(config.str("%s", "%s"))
            }
            """;

    @Test
    void strFallsBackToDefault(@TempDir Path tempDir) throws IOException {
        String out = runNative(tempDir, "dflt", PROBE.formatted("nao.existe", "fallback"), null);
        assertEquals("fallback", out);
    }

    @Test
    void envOverridesWinWithoutFiles(@TempDir Path tempDir) throws IOException {
        String out = runNative(tempDir, "env", """
                main() {
                    println(config.str("cfg.mensagem", "padrao"))
                    println(config.int("cfg.porta", 9999))
                    println(config.bool("cfg.ligado", false))
                    println(config.env("KOF_CFG_HOME"))
                }
                """, Map.of("KOF_CFG_MENSAGEM", "via env",
                            "KOF_CFG_PORTA", "8080",
                            "KOF_CFG_LIGADO", "true",
                            "KOF_CFG_HOME", "/home/mel"));
        assertEquals("via env\n8080\ntrue\n/home/mel", out);
    }

    @Test
    void explicitFileBeatsEnv(@TempDir Path tempDir) throws IOException {
        Path cfgFile = tempDir.resolve("explicito.config");
        Files.writeString(cfgFile, "cfg.mensagem = via arquivo\n");
        String out = runNative(tempDir, "expl",
                PROBE.formatted("cfg.mensagem", "padrao"),
                Map.of("KOF_CONFIG", cfgFile.toString(),
                       "KOF_CFG_MENSAGEM", "via env"));
        assertEquals("via arquivo", out);
    }

    @Test
    void defaultFileInWorkingDirectory(@TempDir Path tempDir) throws IOException {
        String out = runNative(tempDir, "wd", """
                main() {
                    println(config.str("cfg.mensagem", "padrao"))
                    println(config.int("cfg.porta", 9999))
                    println(config.has("cfg.porta"))
                }
                """, null, work -> {
            try {
                Files.writeString(work.resolve("kof.config"),
                        "cfg.mensagem = do cwd\ncfg.porta = 3000\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals("do cwd\n3000\ntrue", out);
    }

    @Test
    void profileFileUsedWhenSet(@TempDir Path tempDir) throws IOException {
        String out = runNative(tempDir, "prof",
                PROBE.formatted("cfg.mensagem", "padrao"),
                Map.of("KOF_PROFILE", "prod"), work -> {
            try {
                Files.writeString(work.resolve("kof.prod.config"),
                        "cfg.mensagem = perfil prod\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals("perfil prod", out);
    }

    @Test
    void invalidTypedValuesUseDefaults(@TempDir Path tempDir) throws IOException {
        String out = runNative(tempDir, "typed", """
                main() {
                    println(config.int("cfg.num", 7))
                    println(config.int("cfg.ok", 7))
                    println(config.bool("cfg.b", true))
                }
                """, null, work -> {
            try {
                Files.writeString(work.resolve("kof.config"),
                        "cfg.num = abc\ncfg.ok = 42\ncfg.b = talvez\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        // "abc" nao parseia -> 7; 42 ok; "talvez" nao eh bool -> default true
        assertEquals("7\n42\ntrue", out);
    }

    @Test
    void commentsAndBlankLinesIgnored(@TempDir Path tempDir) throws IOException {
        String out = runNative(tempDir, "comments",
                PROBE.formatted("cfg.valor", "x"),
                null, work -> {
            try {
                Files.writeString(work.resolve("kof.config"),
                        "# comentario\n\n   # outro\n   cfg.valor   =   aparado   \n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals("aparado", out);
    }

    @Test
    void longAndNegativeInts(@TempDir Path tempDir) throws IOException {
        String out = runNative(tempDir, "ints", """
                main() {
                    println(config.int("cfg.neg", -5))
                    println(config.long("cfg.big", -1))
                    println(config.int("cfg.maior", 2147483647))
                }
                """, null, work -> {
            try {
                Files.writeString(work.resolve("kof.config"),
                        "cfg.neg = -12\ncfg.big = 9000000000\ncfg.maior = 2147483647\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals("-12\n9000000000\n2147483647", out);
    }
}
