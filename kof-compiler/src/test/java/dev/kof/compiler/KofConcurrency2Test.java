package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KofConcurrency2Test {
    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void cancelCooperativeJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                Int trabalho() {
                    var i = 0
                    while (i < 10000 && !cancelled()) {
                        time.sleep(1)
                        i++
                    }
                    if (cancelled()) {
                        println("cancelado")
                    } else {
                        println("completo")
                    }
                    return i
                }

                main() {
                    val r = spawn trabalho()
                    time.sleep(30)
                    assert(cancel(r))
                    await r
                    println("fim")
                }
                """, "cancelado\nfim");
    }


    @Test
    void cancelledOutsideIsFalse(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                main() {
                    assert(!cancelled())
                    println("ok")
                }
                """, "ok");
    }

    @Test
    void selectAnyJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                String rapida() { return "primeira" }

                String lenta() {
                    time.sleep(300)
                    return "segunda"
                }

                main() {
                    val a = spawn lenta()
                    val b = spawn rapida()
                    val v = selectAny(a, b)
                    println(v)
                }
                """, "primeira");
    }

    @Test
    void selectAnyNative(@TempDir Path tmp) throws Exception {
        // CONC001 residual fechado: selectAny nativo (polling 1ms sobre o handle).
        // Os handles são criados JUNTOS (spawn-all-up-front) e o selectAny vem
        // depois — evitam o bug pré-existente de spawn→await→spawn (thread já
        // finalizada + novo pthread_create corrompe a pilha da main).
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, """
                Int t1() { return 1 }
                Int t2() { time.sleep(300); return 2 }
                main() {
                    val a = spawn t1()
                    val b = spawn t2()
                    time.sleep(50)
                    println(selectAny(a, b))
                }
                """);
        CompilationResult r = driver.compile(f, tmp.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), "Native selectAny deve compilar: " + r.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out").resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        assertEquals("1", output, "a task rápida (1) deve vencer a lenta (2)");
    }

    @Test
    void cancelCooperativeNative(@TempDir Path tmp) throws Exception {
        // CONC001 residual fechado: cancel cooperativo nativo (flag por TID).
        // Worker checa !cancelled() no loop; main cancela após 30ms. Spawn único
        // (evita o bug pré-existente de spawn→await→spawn).
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, """
                Int trabalho() {
                    var i = 0
                    while (i < 100000 && !cancelled()) {
                        time.sleep(1)
                        i++
                    }
                    if (cancelled()) { println("cancelado") } else { println("completo") }
                    return i
                }
                main() {
                    val r = spawn trabalho()
                    time.sleep(30)
                    assert(cancel(r))
                    await r
                    println("fim")
                }
                """);
        CompilationResult r = driver.compile(f, tmp.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), "Native cancel/cooperativo deve compilar: " + r.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out").resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        assertEquals("cancelado\nfim", output, "o worker deve ver o cancel e encerrar cedo");
    }

    @Test
    void awaitTimeoutJvm(@TempDir Path tmp) throws Exception {
        // G8/CONC residual: awaitTimeout(r, ms) -> valor no prazo; lança no estouro
        // (capturável via try/catch). JVM: Future.get(ms).
        runJvm(tmp, """
                Int lenta() { time.sleep(400); return 9 }
                Int rapida() { return 42 }
                main() {
                    val r = spawn lenta()
                    try {
                        awaitTimeout(r, 50)
                        println("in")
                    } catch (String e) {
                        println("err")
                    }
                    val q = spawn rapida()
                    var w = awaitTimeout(q, 100)
                    println("q=" + w)
                    var f = await r
                    println("f=" + f)
                }
                """, "err\nq=42\nf=9");
    }

    @Test
    void awaitTimeoutNative(@TempDir Path tmp) throws Exception {
        // awaitTimeout no Native: polling 1ms com deadline; estouro -> kof_throw_string
        // (try/catch do usuário).
        // Ordem segura p/ o bug pré-existente spawn->(task)->spawn (SIGSEGV no
        // próximo pthread_create): a 1ª task é uma sleep (não alocadora) e é
        // joinada (`await`) antes do 2º spawn.
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, """
                Int lenta() { time.sleep(400); return 9 }
                Int rapida() { return 42 }
                main() {
                    val r = spawn lenta()
                    try {
                        awaitTimeout(r, 50)
                        println("in")
                    } catch (String e) {
                        println("err")
                    }
                    var f = await r
                    println("f=" + f)
                    val q = spawn rapida()
                    var w = awaitTimeout(q, 100)
                    println("q=" + w)
                }
                """);
        CompilationResult r = driver.compile(f, tmp.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), "Native awaitTimeout deve compilar: " + r.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out").resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        assertEquals("err\nf=9\nq=42", output, "lenta estoura (catch); rápida no prazo");
    }

    @Test
    void awaitTimeoutJs(@TempDir Path tmp) throws Exception {
        // JS sequencial: a task sempre está pronta (roda em ordem), então o timeout
        // nunca estoura — awaitTimeout é equivalente ao await (paridade de API).
        runJs(tmp, """
                Int t() { return 9 }
                main() {
                    val r = spawn t()
                    var v = awaitTimeout(r, 50)
                    println(v)
                }
                """, "9");
    }

    @Test
    void pollDoneNative(@TempDir Path tmp) throws Exception {
        // CONC001 residual fechado: done/poll não-bloqueantes sobre o handle
        // nativo (flag done no bloco de 32B: 0=tag(2), 4=done, 16=result)
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, """
                Int lenta() { time.sleep(400); return 7 }
                Int rapida() { return 3 }
                main() {
                    val r = spawn lenta()
                    val f = spawn rapida()
                    time.sleep(50)
                    println("poll_f=" + poll(f))
                    println("done_r=" + done(r))
                    println("poll_r=" + poll(r))
                    println("await_r=" + await r)
                    println("done_r2=" + done(r))
                }
                """);
        CompilationResult r = driver.compile(f, tmp.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), "Native poll/done deve compilar: " + r.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out").resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        // task rápida termina antes da main checar; lenta não
        assertTrue(output.contains("poll_f=3"), "poll da rápida devolve o valor: " + output);
        assertTrue(output.contains("done_r=false"), "lenta ainda não terminou: " + output);
        assertTrue(output.contains("poll_r=0"), "poll da lenta não-pronta devolve 0: " + output);
        assertTrue(output.contains("await_r=7"), "await da lenta devolve o valor: " + output);
        assertTrue(output.contains("done_r2=true"), "done vira true após o await: " + output);
    }

    @Test
    void cancelJsSequential(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
                Int t() { return 9 }
                main() {
                    val r = spawn t()
                    assert(cancel(r) == 0)
                    assert(cancelled() == 0)
                    println(await r)
                }
                """, "9");
    }

    // ── helpers ──
    private String runJvm(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runJs(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(findJsEntry(outDir), buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String output = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit code, output: " + output);
            assertEquals(expected, output, "JS output");
            return output;
        }
    }

    private static Path findJsEntry(Path dir) throws java.io.IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs"))
                    .findFirst().orElseThrow(() -> new java.io.IOException("no .mjs in " + dir));
        }
    }
}
