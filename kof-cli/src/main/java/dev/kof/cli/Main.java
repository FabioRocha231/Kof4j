package dev.kof.cli;

import dev.kof.compiler.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Main {
    public static void main(String[] args) {
        if (args.length == 0) { printUsage(); return; }
        switch (args[0]) {
            case "build" -> build(args);
            case "run" -> run(args);
            case "serve" -> serve(args);
            case "check" -> check(args);
            case "info" -> info(args);
            case "lsp" -> lsp();
            case "install" -> install(args);
            case "version" -> System.out.println("kof " + KofVersion.version());
            default -> { System.err.println("unknown: " + args[0]); printUsage(); }
        }
    }

    private static void run(String[] args) {
        if (args.length < 2) { System.err.println("usage: kof run <file.kf> [--target jvm|native|js] [args...]"); return; }
        Path file = Path.of(args[1]);
        if (!Files.exists(file)) { System.err.println("file not found: " + file); System.exit(1); return; }

        Target target = Target.JVM;
        int argStart = 2;
        for (int i = 2; i < args.length; i++) {
            if (args[i].startsWith("--target=")) {
                target = parseTarget(args[i].substring("--target=".length()));
                argStart = i + 1;
            } else if (args[i].equals("--target") && i + 1 < args.length) {
                target = parseTarget(args[i + 1]);
                argStart = i + 2;
                i++;
            }
        }

        Path tempDir;
        try { tempDir = Files.createTempDirectory("kof-run-"); }
        catch (IOException e) { System.err.println("failed to create temp dir: " + e.getMessage()); System.exit(1); return; }

        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(file, tempDir, target);
        for (Diagnostic d : result.diagnostics().getDiagnostics()) System.err.println(d.format());
        if (!result.success()) { cleanup(tempDir); System.exit(1); return; }

        if (target == Target.JS) {
            if (!nodeAvailable()) {
                System.err.println("kof run: Node.js not found in PATH (required for --target=js)");
                cleanup(tempDir);
                System.exit(1);
                return;
            }
            String entry = findJsEntry(tempDir);
            if (entry == null) {
                System.err.println("no JS entry point found");
                cleanup(tempDir);
                System.exit(1);
                return;
            }
            List<String> nodeArgs = new ArrayList<>();
            nodeArgs.add("node");
            nodeArgs.add(entry);
            for (int i = argStart; i < args.length; i++) nodeArgs.add(args[i]);
            executeProcess(nodeArgs, tempDir);
            return;
        }

        String className = findMainClass(tempDir);
        if (className == null) {
            System.err.println("no main class found");
            cleanup(tempDir);
            System.exit(1);
            return;
        }
        List<String> javaArgs = new ArrayList<>();
        javaArgs.add(javaExecutable());
        javaArgs.add("-cp");
        javaArgs.add(tempDir.toString());
        javaArgs.add(className);
        for (int i = argStart; i < args.length; i++) javaArgs.add(args[i]);
        executeProcess(javaArgs, tempDir);
    }

    private static void executeProcess(List<String> command, Path tempDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.inheritIO();
            Process p = pb.start();
            int exitCode = p.waitFor();
            cleanup(tempDir);
            System.exit(exitCode);
        } catch (IOException e) {
            System.err.println("failed to execute: " + e.getMessage());
            cleanup(tempDir);
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cleanup(tempDir);
            System.exit(1);
        }
    }

    private static boolean nodeAvailable() {
        try {
            Process p = new ProcessBuilder("node", "--version").redirectErrorStream(true).start();
            return p.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private static String findJsEntry(Path dir) {
        Path defaultEntry = dir.resolve("Default.mjs");
        if (Files.exists(defaultEntry)) return defaultEntry.toString();
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs"))
                    .filter(p -> !p.toString().contains("kof-runtime"))
                    .findFirst()
                    .map(p -> p.toString())
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static void build(String[] args) {
        if (args.length < 2) { System.err.println("usage: kof build <source-dir> [--target jvm|native] [--output <dir>]"); return; }
        Path src = Path.of(args[1]);
        Target target = Target.JVM;
        Path out = Path.of("build/classes");
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--target=")) {
                target = parseTarget(arg.substring("--target=".length()));
            } else if (arg.startsWith("--output=")) {
                out = Path.of(arg.substring("--output=".length()));
            } else if (arg.equals("--target") && i + 1 < args.length) {
                target = parseTarget(args[i + 1]);
                i++;
            } else if (arg.equals("--output") && i + 1 < args.length) {
                out = Path.of(args[i + 1]);
                i++;
            }
        }
        CompilerDriver driver = new CompilerDriver();
        List<Path> files = collect(src);
        if (files.isEmpty()) { System.out.println("no .kf files found"); return; }
        boolean ok = true;
        for (Path f : files) {
            CompilationResult r = driver.compile(f, out, target);
            for (Diagnostic d : r.diagnostics().getDiagnostics()) System.out.println(d.format());
            if (!r.success()) ok = false;
        }
        if (!ok) System.exit(1);
    }

    private static Target parseTarget(String value) {
        return switch (value) {
            case "jvm" -> Target.JVM;
            case "native" -> Target.NATIVE;
            case "js" -> Target.JS;
            default -> {
                System.err.println("unknown target: " + value);
                System.exit(1);
                yield Target.JVM;
            }
        };
    }

    private static List<Path> collect(Path dir) {
        List<Path> files = new ArrayList<>();
        try (var s = Files.walk(dir)) { s.filter(p -> p.toString().endsWith(".kf")).forEach(files::add); }
        catch (IOException e) { System.err.println("error: " + e.getMessage()); }
        return files;
    }

    private static void cleanup(Path dir) {
        try (var s = Files.walk(dir)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }

    private static String javaExecutable() {
        String install = System.getProperty("kof.install.dir", "");
        if (!install.isEmpty()) {
            String exe = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
            Path jdk = Path.of(install, "jdk", "bin", exe);
            if (Files.isExecutable(jdk)) return jdk.toString();
        }
        return "java";
    }

    private static String findMainClass(Path dir) {
        try (var s = Files.walk(dir)) {
            List<String> candidates = s.filter(p -> p.toString().endsWith(".class"))
                    .map(p -> dir.relativize(p).toString()
                            .replace(".class", "")
                            .replace("/", ".")
                            .replace("\\", "."))
                    .toList();
            for (String c : candidates) {
                if (c.endsWith(".Main") || c.equals("Main")) return c;
            }
            return candidates.isEmpty() ? null : candidates.get(candidates.size() - 1);
        } catch (IOException e) {
            return null;
        }
    }

    private static void printUsage() {
        System.out.println("usage: kof <command>");
        System.out.println("  build <dir> [--target jvm|native|js] [--output <dir>]");
        System.out.println("  run <file.kf> [--target jvm|native|js] [args...]");
        System.out.println("  serve <file.kf> [--port <port>] [--host <host>]");
        System.out.println("  check <file.kf|dir>          type-check without emitting output");
        System.out.println("  info [--json]                environment and platform report");
        System.out.println("  lsp                          Language Server (stdio, LSP protocol)");
        System.out.println("  install <dir>                install this build as a distribution");
        System.out.println("  version");
        System.out.println();
        System.out.println("note: the js target is in development (alpha); node is required to run");
    }

    private static void install(String[] args) {
        if (args.length < 2) { System.err.println("usage: kof install <dir>"); System.exit(1); return; }
        Path prefix = Path.of(args[1]);
        try {
            Path jar = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isDirectory(jar)) {
                System.err.println("install: run from the packaged kof.jar (bin/kof install <dir>)");
                System.exit(1);
                return;
            }
            Files.createDirectories(prefix.resolve("bin"));
            Files.createDirectories(prefix.resolve("lib"));
            Files.copy(jar, prefix.resolve("lib/kof.jar"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Path launcher = prefix.resolve("bin/kof");
            Files.writeString(launcher, """
                    #!/usr/bin/env bash
                    set -euo pipefail
                    DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
                    HOME_DIR="$(dirname "$DIR")"
                    EMBEDDED=false
                    JAVA="$(command -v java || true)"
                    if [ -x "$HOME_DIR/jdk/bin/java" ]; then
                        JAVA="$HOME_DIR/jdk/bin/java"
                        EMBEDDED=true
                    fi
                    if [ -z "$JAVA" ]; then echo "kof: no java found" >&2; exit 1; fi
                    exec "$JAVA" -Dkof.install.dir="$HOME_DIR" -Dkof.embedded.jdk="$EMBEDDED" \\
                        -jar "$HOME_DIR/lib/kof.jar" "$@"
                    """);
            launcher.toFile().setExecutable(true);
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                Files.writeString(prefix.resolve("bin/kof.bat"),
                        "@echo off\r\nset KOF_HOME=%~dp0..\r\njava -Dkof.install.dir=\"%KOF_HOME%\" -jar \"%KOF_HOME%\\lib\\kof.jar\" %*\r\n");
            }
            Files.writeString(prefix.resolve("VERSION"), KofVersion.version() + "\n");
            String installDir = System.getProperty("kof.install.dir", "");
            Path src = installDir.isEmpty() ? Path.of("").toAbsolutePath() : Path.of(installDir);
            if (Files.exists(src.resolve("editor"))) copyTree(src.resolve("editor"), prefix.resolve("editor"));
            if (Files.exists(src.resolve("tooling"))) copyTree(src.resolve("tooling"), prefix.resolve("tooling"));
            System.out.println("kof installed at " + prefix.toAbsolutePath());
            System.out.println("add " + prefix.resolve("bin") + " to your PATH and run: kof info");
        } catch (Exception e) {
            System.err.println("install: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (var s = Files.walk(from)) {
            for (Path p : s.toList()) {
                Path rel = from.relativize(p);
                Path target = to.resolve(rel.toString());
                if (Files.isDirectory(p)) Files.createDirectories(target);
                else Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void lsp() {
        LspServer server = new LspServer(System.in, System.out);
        try {
            server.run();
        } catch (IOException e) {
            System.err.println("lsp: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void check(String[] args) {
        if (args.length < 2) { System.err.println("usage: kof check <file.kf|dir>"); System.exit(1); return; }
        Path src = Path.of(args[1]);
        if (!Files.exists(src)) { System.err.println("not found: " + src); System.exit(1); return; }
        List<Path> files = Files.isDirectory(src) ? collect(src) : List.of(src);
        if (files.isEmpty()) { System.out.println("no .kf files found"); return; }
        CompilerDriver driver = new CompilerDriver();
        boolean ok = true;
        int count = 0;
        for (Path f : files) {
            Path tmp;
            try { tmp = Files.createTempDirectory("kof-check-"); }
            catch (IOException e) { System.err.println("failed to create temp dir: " + e.getMessage()); System.exit(1); return; }
            CompilationResult r = driver.compile(f, tmp, Target.JVM);
            for (Diagnostic d : r.diagnostics().getDiagnostics()) System.out.println(d.format());
            cleanup(tmp);
            if (!r.success()) ok = false;
            count++;
        }
        if (!ok) System.exit(1);
        System.out.println("checked " + count + " file(s) — no errors");
    }

    private static void info(String[] args) {
        boolean json = args.length > 1 && "--json".equals(args[1]);
        String installDir = System.getProperty("kof.install.dir", "");
        if (installDir.isEmpty()) {
            try {
                Path jar = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                installDir = jar.toString();
            } catch (Exception e) {
                installDir = Path.of("").toAbsolutePath().toString();
            }
        }
        boolean embedded = Boolean.parseBoolean(System.getProperty("kof.embedded.jdk", "false"));
        String jvm = System.getProperty("java.vendor", "unknown") + " " + System.getProperty("java.version", "unknown");
        String channel = releaseChannel(KofVersion.version());

        if (json) {
            System.out.println("{\"kof\":\"" + jsonEscape(KofVersion.version())
                    + "\",\"releaseChannel\":\"" + channel
                    + "\",\"toolingApi\":" + KofVersion.toolingApi()
                    + ",\"os\":\"" + KofVersion.os()
                    + "\",\"arch\":\"" + KofVersion.arch()
                    + "\",\"target\":\"" + KofVersion.target()
                    + "\",\"jvm\":\"" + jsonEscape(jvm)
                    + "\",\"embeddedJdk\":" + embedded
                    + ",\"compiler\":\"" + KofVersion.compiler()
                    + "\",\"runtime\":\"" + KofVersion.runtime()
                    + "\",\"stdlib\":\"" + KofVersion.stdlib()
                    + "\",\"targets\":["jvm","native","js"]"
                    + ",\"lsp\":true"
                    + ",\"editorSupport\":true"
                    + ",\"install\":\"" + jsonEscape(installDir) + "\"}");
            return;
        }

        System.out.println("Kof " + KofVersion.version());
        System.out.println("Release channel: " + channel);
        System.out.println("Tooling API: " + KofVersion.toolingApi());
        System.out.println("OS: " + KofVersion.os());
        System.out.println("Arch: " + KofVersion.arch());
        System.out.println("Target: " + KofVersion.target());
        System.out.println("JVM: " + jvm + (embedded ? " (embedded)" : ""));
        System.out.println("Compiler: " + KofVersion.compiler());
        System.out.println("Runtime: " + KofVersion.runtime());
        System.out.println("Stdlib: " + KofVersion.stdlib());
        System.out.println("Targets: jvm, native, js (alpha)");
        System.out.println("LSP: available");
        System.out.println("Editor support: available");
        System.out.println("Install: " + installDir);
    }

    private static String releaseChannel(String version) {
        if (version.endsWith("-alpha")) return "alpha";
        if (version.endsWith("-beta")) return "beta";
        if (version.endsWith("-rc")) return "release-candidate";
        return "stable";
    }

    private static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private static void serve(String[] args) {
        if (args.length < 2) { System.err.println("usage: kof serve <file.kf> [--port <port>] [--host <host>]"); return; }
        Path file = Path.of(args[1]);
        if (!Files.exists(file)) { System.err.println("file not found: " + file); System.exit(1); return; }

        int port = 8080;
        String host = "0.0.0.0";
        for (int i = 2; i < args.length; i++) {
            if (args[i].equals("--port") && i + 1 < args.length) {
                port = Integer.parseInt(args[i + 1]);
                i++;
            } else if (args[i].equals("--host") && i + 1 < args.length) {
                host = args[i + 1];
                i++;
            }
        }

        Path tempDir;
        try { tempDir = Files.createTempDirectory("kof-serve-"); }
        catch (IOException e) { System.err.println("failed to create temp dir: " + e.getMessage()); System.exit(1); return; }

        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(file, tempDir, Target.JVM);
        for (Diagnostic d : result.diagnostics().getDiagnostics()) System.err.println(d.format());
        if (!result.success()) { cleanup(tempDir); System.exit(1); return; }

        String className = findMainClass(tempDir);
        if (className == null) {
            System.err.println("no main class found");
            cleanup(tempDir);
            System.exit(1);
            return;
        }

        System.out.println("kof serve starting on " + host + ":" + port);
        System.out.println("compiling " + file + " ...");
        System.out.println("server ready at http://" + host + ":" + port);

        URLClassLoader handlerLoader;
        try {
            handlerLoader = new URLClassLoader(
                    new java.net.URL[]{tempDir.toUri().toURL()}, Main.class.getClassLoader());
        } catch (java.net.MalformedURLException e) {
            System.err.println("failed to load compiled classes: " + e.getMessage());
            cleanup(tempDir);
            System.exit(1);
            return;
        }

        try {
            Class<?> handlerClass = Class.forName(className, true, handlerLoader);
            dev.kof.compiler.KofHttpServer server = new dev.kof.compiler.KofHttpServer(
                    dev.kof.compiler.ReflectiveHandler.forClass(handlerClass));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nkof serve shutting down...");
                server.close();
                try { handlerLoader.close(); } catch (IOException ignored) {}
                cleanup(tempDir);
            }));

            System.out.println("listening for connections...");
            server.serve(host, port);
        } catch (ClassNotFoundException e) {
            System.err.println("handler class not found: " + e.getMessage());
            cleanup(tempDir);
            System.exit(1);
        } catch (IOException e) {
            System.err.println("server error: " + e.getMessage());
            cleanup(tempDir);
            System.exit(1);
        }
    }
}
