package dev.kof.script;

import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.Target;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/**
 * KofScript — Fase 6 MVP.
 * <p>
 * Direct execution for Kof: compiles Kof source to JVM bytecode in a
 * temporary directory and runs it via {@code java -cp}. This reuses the
 * shared frontend (Lexer → Parser → AST → SemanticAnalyzer → Kof IR) and
 * the existing JvmBackend — no second compiler.
 * <p>
 * The MVP is intentionally thin: it is the foundation for the future
 * REPL / incremental execution / JIT. The public API is stable:
 * {@link #eval(String)}, {@link #runFile(Path)}.
 */
public final class KofScript {

    private KofScript() {}

    public record RunResult(int exitCode, String stdout, String stderr, boolean success) {}

    /**
     * Evaluates a Kof snippet as a program (wraps in main if needed).
     * The snippet may be a full program (with main) or just statements.
     */
    // Simple incremental cache: last source -> last successful outDir content hash
    private static String lastSource = null;
    private static Path lastOutDir = null;

    public static RunResult eval(String code) throws IOException {
        return eval(code, Target.JVM);
    }

    public static RunResult eval(String code, Target target) throws IOException {
        String pre = preprocess(code);
        Path tmp = Files.createTempDirectory("kofscript");
        try {
            Path src = tmp.resolve("Main.kf");
            String wrapped = pre.contains("main()") ? pre : "main() {\n" + pre + "\n}";
            Files.writeString(src, wrapped);
            return runFile(src, target);
        } finally {
            deleteRecursively(tmp);
        }
    }

    /**
     * Preprocess KofScript syntactic sugar:
     * - `async fn` → `fn` (async is just spawn+await, Kof already has spawn/await via Handle<T>)
     * - `let`/`const` → `var` (Kof's var)
     * Future: full async→Handle<T> transform will be done in the frontend.
     */
    static String preprocess(String code) {
        // Preserve string literals while replacing
        String r = code.replaceAll("\\basync\\s+fn\\b", "fn");
        r = r.replaceAll("\\blet\\b", "var");
        r = r.replaceAll("\\bconst\\b", "val");
        return r;
    }

    public static RunResult runFile(Path sourceFile) throws IOException {
        return runFile(sourceFile, Target.JVM);
    }

    public static RunResult runFile(Path sourceFile, Target target) throws IOException {
        Path outDir = Files.createTempDirectory("kofscript-out");
        try {
            CompilerDriver driver = new CompilerDriver();
            CompilationResult result = driver.compile(sourceFile, outDir, target);
            if (!result.success()) {
                StringBuilder sb = new StringBuilder();
                result.diagnostics().getDiagnostics().forEach(d -> sb.append(d.format()).append("\n"));
                return new RunResult(1, "", sb.toString(), false);
            }
            if (target == Target.JS) {
                // JS: run with embedded GraalJS (no Node required) — capture stdout
                String entry = findJsEntry(outDir);
                if (entry == null) return new RunResult(1, "", "no JS entry", false);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(baos);
                ByteArrayOutputStream beos = new ByteArrayOutputStream();
                PrintStream pe = new PrintStream(beos);
                int ec = dev.kof.runtime.KofJsRunner.run(Path.of(entry), ps, System.in, pe, false, new String[0]);
                return new RunResult(ec, baos.toString(), beos.toString(), ec == 0);
            }
            if (target == Target.NATIVE) {
                Path bin = outDir.resolve("Default/Main");
                if (!Files.exists(bin)) return new RunResult(1, "", "no native binary", false);
                ProcessBuilder pb = new ProcessBuilder(bin.toString());
                pb.redirectErrorStream(false);
                Process p = pb.start();
                String stdout = new String(p.getInputStream().readAllBytes());
                String stderr = new String(p.getErrorStream().readAllBytes());
                boolean finished = false;
                try { finished = p.waitFor(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); p.destroyForcibly(); }
                if (!finished) { p.destroyForcibly(); return new RunResult(124, stdout, "timeout", false); }
                return new RunResult(p.exitValue(), stdout, stderr, p.exitValue() == 0);
            }
            String runtimeCp = runtimeClasspath();
            String cp = outDir.toString() + (runtimeCp != null ? File.pathSeparator + runtimeCp : "");
            String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
            ProcessBuilder pb = new ProcessBuilder(javaBin, "-cp", cp, "Default.Main");
            pb.redirectErrorStream(false);
            Process p = pb.start();
            String stdout = new String(p.getInputStream().readAllBytes());
            String stderr = new String(p.getErrorStream().readAllBytes());
            boolean finished = false;
            try {
                finished = p.waitFor(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                p.destroyForcibly();
            }
            if (!finished) {
                p.destroyForcibly();
                return new RunResult(124, stdout, "timeout", false);
            }
            int ec = p.exitValue();
            return new RunResult(ec, stdout, stderr, ec == 0);
        } finally {
            deleteRecursively(outDir);
        }
    }

    private static String runtimeClasspath() {
        try {
            // When running from mvn test, kof-runtime/target/classes exists; when installed, kof.jar contains runtime
            Path candidate = Path.of("kof-runtime/target/classes");
            if (Files.exists(candidate)) return candidate.toString();
            // Try to locate via protection domain of a runtime class (KofJsRunner is always present)
            var loc = dev.kof.runtime.KofJsRunner.class.getProtectionDomain().getCodeSource().getLocation();
            if (loc != null) {
                Path p = Path.of(loc.toURI());
                if (Files.exists(p)) return p.toString();
            }
        } catch (Exception ignore) {}
        return null;
    }

    private static String findJsEntry(Path dir) {
        Path e = dir.resolve("Default.mjs");
        if (Files.exists(e)) return e.toString();
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs")).findFirst().map(Path::toString).orElse(null);
        } catch (IOException ex) { return null; }
    }

    /**
     * Simple REPL: reads lines from stdin, evals until "exit".
     * Incremental: each line is appended to the history and re-evaluated
     * as a whole program (MVP — future will be incremental IR).
     */
    public static void repl(InputStream in, PrintStream out) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder history = new StringBuilder();
        out.println("KofScript REPL 0.1.2-beta — type 'exit' to quit");
        while (true) {
            out.print("kof> ");
            out.flush();
            String line = reader.readLine();
            if (line == null || "exit".equals(line.trim())) break;
            if (line.isBlank()) continue;
            history.append(line).append("\n");
            RunResult r = eval(history.toString());
            if (!r.success()) {
                out.println("error: " + r.stderr().trim());
                // rollback last line on error
                int lastNl = history.lastIndexOf(line);
                if (lastNl >= 0) history.setLength(lastNl);
            } else {
                out.print(r.stdout());
            }
        }
    }

    private static void deleteRecursively(Path dir) {
        try {
            if (!Files.exists(dir)) return;
            Files.walk(dir).sorted((a,b) -> b.compareTo(a)).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignore) {}
            });
        } catch (IOException ignore) {}
    }
}
