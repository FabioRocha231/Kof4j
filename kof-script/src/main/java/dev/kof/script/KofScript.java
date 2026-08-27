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
    public static RunResult eval(String code) throws IOException {
        Path tmp = Files.createTempDirectory("kofscript");
        try {
            Path src = tmp.resolve("Main.kf");
            String wrapped = code.contains("main()") ? code : "main() {\n" + code + "\n}";
            Files.writeString(src, wrapped);
            return runFile(src);
        } finally {
            deleteRecursively(tmp);
        }
    }

    public static RunResult runFile(Path sourceFile) throws IOException {
        Path outDir = Files.createTempDirectory("kofscript-out");
        try {
            CompilerDriver driver = new CompilerDriver();
            CompilationResult result = driver.compile(sourceFile, outDir, Target.JVM);
            if (!result.success()) {
                StringBuilder sb = new StringBuilder();
                result.diagnostics().getDiagnostics().forEach(d -> sb.append(d.message()).append(" [").append(d.code()).append("]\n"));
                return new RunResult(1, "", sb.toString(), false);
            }
            String cp = outDir.toString() + File.pathSeparator + "kof-runtime/target/classes";
            // Use the same java that runs this process
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
