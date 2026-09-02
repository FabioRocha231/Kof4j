package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Debugger fase 5 (Native/DWARF) — o NativeBackend emite `.file 1 "<fonte>"`
 * + `.loc 1 <linha> 0` por instrução (a partir do {@code KofDebugInfo} que o
 * driver já popula, mesma fonte das line tables do JVM). O `as` converte em
 * `.debug_line` (DWARF) no ELF final; `objdump --dwarf=decodedline` lê a
 * linha Kof de cada instrução — o programador depura Kof, nunca o asm.
 */
class NativeDwarfLineInfoTest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void nativeEmitDwarfLineInfo(@TempDir Path tempDir) throws Exception {
        String src = """
                main() {
                    println("dwarf")
                    var x = 1
                }
                """;
        Path file = tempDir.resolve("Main.kf");
        Files.writeString(file, src);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile failed: " + result.diagnostics().getDiagnostics());

        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binario ausente");

        String lines = runCmd("objdump", "--dwarf=decodedline", bin.toString());
        assertTrue(lines.contains("Main.kf"),
                "DWARF .debug_line deve referenciar a fonte Kof; got: " + lines);

        // `Nome de ficheiro  Nº de linha  ...` — extrai as linhas Kof mapeadas
        Pattern row = Pattern.compile("(?m)^\\s*Main\\.kf\\s+([0-9]+)\\s");
        java.util.TreeSet<Integer> kofLines = new java.util.TreeSet<>();
        Matcher m = row.matcher(lines);
        while (m.find()) kofLines.add(Integer.parseInt(m.group(1)));
        assertFalse(kofLines.isEmpty(), "nenhuma linha Kof mapeada no .debug_line; got: " + lines);

        // o corpo do main (linhas 2 e 3 do Kof) tem que aparecer
        assertTrue(kofLines.contains(2) || kofLines.contains(3),
                "linha do corpo do main (2/3) ausente no DWARF; mapeadas: " + kofLines + "\n" + lines);

        // e o programa continua executando (o .loc é aditivo ao código gerado)
        String out = runBinary(bin);
        assertEquals("dwarf", out, "output do binario com DWARF");
    }

    private static String runBinary(Path bin) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(bin.toString());
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "exit code, output: " + output);
        return output;
    }

    private static String runCmd(String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        p.getInputStream().transferTo(buf);
        int ec = p.waitFor();
        assertTrue(ec == 0 || ec == 1, "cmd exit " + ec + " para " + String.join(" ", cmd));
        return buf.toString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
