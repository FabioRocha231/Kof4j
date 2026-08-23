package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Diagnostic;
import dev.kof.compiler.KofVersion;
import dev.kof.compiler.Target;
import dev.kof.runtime.KofJsRunner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * `kof bench` — benchmark harness (docs/performance.md §19-§25, §34).
 *
 * Each benchmark is a directory containing `Main.kf`, `expected.txt` and an
 * optional `meta.json` ({ "targets": [...], "iterations": N }). The harness:
 *
 *   compile → run → validate output → collect metrics → compare baseline
 *
 * Metrics: median wall time over N runs; peak RSS (Linux, via /usr/bin/time).
 * A baseline file (JSON) can be compared against; regressions above the
 * threshold ratio are reported and can fail the run (CI gate, §26).
 */
public final class Bench {

    private static final double DEFAULT_THRESHOLD = 1.20;

    private Bench() {
    }

    public static int run(String[] args) {
        if (args.length > 0 && "bench".equals(args[0])) {
            args = java.util.Arrays.copyOfRange(args, 1, args.length);
        }
        List<Path> roots = new ArrayList<>();
        Target target = Target.JVM;
        int iterations = 3;
        int warmup = 1;
        Path baselineFile = null;
        Path updateBaseline = null;
        boolean jsonOut = false;
        boolean failOnRegression = false;
        boolean verbose = false;
        double threshold = DEFAULT_THRESHOLD;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--target" -> {
                    if (i + 1 < args.length) {
                        target = parseTarget(args[++i]);
                    }
                }
                case "--iterations" -> {
                    if (i + 1 < args.length) {
                        iterations = Integer.parseInt(args[++i]);
                    }
                }
                case "--warmup" -> {
                    if (i + 1 < args.length) {
                        warmup = Integer.parseInt(args[++i]);
                    }
                }
                case "--quick" -> {
                    iterations = 1;
                    warmup = 0;
                }
                case "--baseline" -> {
                    if (i + 1 < args.length) {
                        baselineFile = Path.of(args[++i]);
                    }
                }
                case "--update-baseline" -> {
                    if (i + 1 < args.length) {
                        updateBaseline = Path.of(args[++i]);
                    }
                }
                case "--threshold" -> {
                    if (i + 1 < args.length) {
                        threshold = Double.parseDouble(args[++i]);
                    }
                }
                case "--json" -> jsonOut = true;
                case "--fail-on-regression" -> failOnRegression = true;
                case "--verbose" -> verbose = true;
                default -> {
                    if (arg.startsWith("--target=")) {
                        target = parseTarget(arg.substring("--target=".length()));
                    } else if (arg.startsWith("--iterations=")) {
                        iterations = Integer.parseInt(arg.substring("--iterations=".length()));
                    } else if (arg.startsWith("--warmup=")) {
                        warmup = Integer.parseInt(arg.substring("--warmup=".length()));
                    } else if (arg.startsWith("--baseline=")) {
                        baselineFile = Path.of(arg.substring("--baseline=".length()));
                    } else if (arg.startsWith("--update-baseline=")) {
                        updateBaseline = Path.of(arg.substring("--update-baseline=".length()));
                    } else if (arg.startsWith("--threshold=")) {
                        threshold = Double.parseDouble(arg.substring("--threshold=".length()));
                    } else {
                        roots.add(Path.of(arg));
                    }
                }
            }
        }
        if (roots.isEmpty()) roots.add(Path.of("benchmarks"));
        if (iterations < 1) iterations = 1;

        List<BenchmarkSpec> specs = discover(roots);
        if (specs.isEmpty()) {
            System.err.println("kof bench: no benchmarks found");
            return 1;
        }

        Map<String, Object> baseline = baselineFile != null ? loadBaseline(baselineFile) : null;
        Map<String, Object> results = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean anyRegression = false;
        boolean anyFailure = false;

        for (BenchmarkSpec spec : specs) {
            if (!spec.supports(target)) {
                if (verbose) System.out.println("skip   " + spec.name + " (not supported on " + target + ")");
                continue;
            }
            if (verbose) System.out.println("run    " + spec.name + " (" + target + ")");
            Map<String, Object> row = benchmark(spec, target, iterations, warmup, verbose);
            if (row == null) {
                anyFailure = true;
                continue;
            }
            if (baseline != null) {
                Object baseMs = ((Map<?, ?>) baseline.getOrDefault("results", Map.of())).get(spec.name);
                if (baseMs instanceof Number baseNum && baseNum.doubleValue() > 0) {
                    double ratio = ((Number) row.get("ms")).doubleValue() / baseNum.doubleValue();
                    row.put("ratio", ratio);
                    row.put("baseline_ms", baseNum.doubleValue());
                    // Relative + absolute guard: small benchmarks are noise-prone,
                    // so a regression must also exceed a 10ms absolute delta.
                    double delta = ((Number) row.get("ms")).doubleValue() - baseNum.doubleValue();
                    if (ratio > threshold && delta > 10.0) {
                        row.put("regression", true);
                        anyRegression = true;
                    }
                }
            }
            results.put(spec.name, row);
            row.put("name", spec.name);
            rows.add(row);
        }

        if (updateBaseline != null) {
            writeBaseline(updateBaseline, target, iterations, results);
        }

        if (jsonOut) {
            System.out.println(Json.stringify(report(target, iterations, results, baselineFile)));
        } else {
            printTable(target, rows);
        }

        if (anyRegression) {
            if (!jsonOut) {
                System.out.println("PERFORMANCE REGRESSION");
            }
            return failOnRegression ? 1 : 0;
        }
        return anyFailure ? 1 : 0;
    }

    private static Map<String, Object> report(Target target, int iterations,
                                              Map<String, Object> results, Path baselineFile) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("tool", "kof bench");
        report.put("version", KofVersion.version());
        report.put("target", target.name().toLowerCase());
        report.put("iterations", iterations);
        report.put("host", hostName());
        report.put("results", results);
        if (baselineFile != null) report.put("baseline", baselineFile.toString());
        return report;
    }

    private static String hostName() {
        String os = System.getProperty("os.name", "?");
        String arch = System.getProperty("os.arch", "?");
        return os + "/" + arch;
    }

    // ── Discovery ────────────────────────────────────────────────────

    private static final class BenchmarkSpec {
        final String name;
        final Path dir;
        final int iterations;
        final List<String> targets;

        BenchmarkSpec(String name, Path dir, int iterations, List<String> targets) {
            this.name = name;
            this.dir = dir;
            this.iterations = iterations;
            this.targets = targets;
        }

        boolean supports(Target t) {
            return targets.isEmpty() || targets.contains(t.name().toLowerCase());
        }
    }

    private static List<BenchmarkSpec> discover(List<Path> roots) {
        List<BenchmarkSpec> specs = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                if (Files.isRegularFile(root) && root.getFileName().toString().equals("Main.kf")) {
                    specs.add(specFromDir(root.getParent()));
                }
                continue;
            }
            try (var stream = Files.walk(root)) {
                stream.filter(p -> p.getFileName() != null && p.getFileName().toString().equals("Main.kf"))
                        .sorted(Comparator.comparing(Path::toString))
                        .forEach(p -> specs.add(specFromDir(p.getParent())));
            } catch (IOException e) {
                System.err.println("kof bench: cannot read " + root + ": " + e.getMessage());
            }
        }
        return specs;
    }

    private static BenchmarkSpec specFromDir(Path dir) {
        String name = dir.getFileName().toString();
        Path parent = dir.getParent();
        if (parent != null && parent.getFileName() != null) {
            name = parent.getFileName() + "/" + name;
        }
        int iterations = 0;
        List<String> targets = List.of();
        Path meta = dir.resolve("meta.json");
        if (Files.isRegularFile(meta)) {
            try {
                Object parsed = Json.parse(Files.readString(meta));
                if (parsed instanceof Map<?, ?> m) {
                    if (m.get("iterations") instanceof Number n) iterations = n.intValue();
                    if (m.get("targets") instanceof List<?> list) {
                        targets = list.stream().map(String::valueOf).map(String::toLowerCase).toList();
                    }
                }
            } catch (Exception e) {
                System.err.println("kof bench: bad meta.json in " + dir + ": " + e.getMessage());
            }
        }
        return new BenchmarkSpec(name, dir, iterations, targets);
    }

    // ── Single benchmark run ─────────────────────────────────────────

    private static Map<String, Object> benchmark(BenchmarkSpec spec, Target target,
                                                 int defaultIterations, int warmup, boolean verbose) {
        int iterations = spec.iterations > 0 ? spec.iterations : defaultIterations;
        Path outDir;
        try {
            outDir = Files.createTempDirectory("kof-bench-");
        } catch (IOException e) {
            System.err.println("kof bench: " + spec.name + ": " + e.getMessage());
            return null;
        }
        try {
            CompilerDriver driver = new CompilerDriver();
            driver.setDebugInfoEnabled(false);
            Path source = spec.dir.resolve("Main.kf");
            long compileStart = System.nanoTime();
            CompilationResult result = driver.compile(source, outDir, target);
            long compileMs = (System.nanoTime() - compileStart) / 1_000_000;
            if (!result.success()) {
                for (Diagnostic d : result.diagnostics().getDiagnostics()) {
                    System.err.println("kof bench: " + spec.name + ": " + d.format());
                }
                return null;
            }

            String expected = expectedOutput(spec.dir);
            if (expected == null) {
                System.err.println("kof bench: " + spec.name + ": expected.txt missing");
                return null;
            }

            // Warmup run: discarded from metrics, but still validated.
            if (warmup > 0) {
                RunResult w = runOnce(target, outDir, spec, verbose);
                if (w == null) {
                    System.err.println("kof bench: " + spec.name + ": warmup run failed");
                    return null;
                }
                if (!w.output.equals(expected)) {
                    System.err.println("kof bench: " + spec.name + ": output mismatch"
                            + "\n  expected: " + quote(expected)
                            + "\n  actual:   " + quote(w.output));
                    return null;
                }
            }

            List<Long> times = new ArrayList<>();
            long rssKb = 0;
            long cpuMicros = 0;
            boolean validated = true;
            for (int i = 0; i < iterations; i++) {
                RunResult rr = runOnce(target, outDir, spec, verbose);
                if (rr == null) {
                    validated = false;
                    break;
                }
                times.add(rr.wallNanos);
                rssKb = Math.max(rssKb, rr.rssKb);
                cpuMicros += rr.userMicros + rr.systemMicros;
                if (!rr.output.equals(expected)) {
                    validated = false;
                    System.err.println("kof bench: " + spec.name + ": output mismatch"
                            + "\n  expected: " + quote(expected)
                            + "\n  actual:   " + quote(rr.output));
                    break;
                }
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ms", median(times));
            if (rssKb > 0) row.put("rss_kb", rssKb);
            if (cpuMicros > 0) row.put("cpu_ms", cpuMicros / 1_000);
            row.put("compile_ms", compileMs);
            row.put("validated", validated);
            if (!validated) row.put("status", "FAILED");
            return row;
        } catch (Exception e) {
            System.err.println("kof bench: " + spec.name + ": " + e);
            return null;
        } finally {
            cleanup(outDir);
        }
    }

    private static final class RunResult {
        final long wallNanos;
        final long rssKb;
        final String output;
        final long userMicros;
        final long systemMicros;

        RunResult(long wallNanos, long rssKb, String output) {
            this(wallNanos, rssKb, output, 0, 0);
        }

        RunResult(long wallNanos, long rssKb, String output, long userMicros, long systemMicros) {
            this.wallNanos = wallNanos;
            this.rssKb = rssKb;
            this.output = output;
            this.userMicros = userMicros;
            this.systemMicros = systemMicros;
        }
    }

    private static RunResult runOnce(Target target, Path outDir, BenchmarkSpec spec, boolean verbose)
            throws IOException, InterruptedException {
        long start = System.nanoTime();
        String output;
        long rssKb = 0;
        long userMicros = 0;
        long systemMicros = 0;
        if (target == Target.JS) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            Path entry = findJsEntry(outDir);
            if (entry == null) {
                System.err.println("kof bench: " + spec.name + ": no JS entry point");
                return null;
            }
            int ec = KofJsRunner.run(entry, buf, InputStream.nullInputStream(), new ByteArrayOutputStream());
            output = buf.toString(StandardCharsets.UTF_8);
            if (ec != 0) {
                System.err.println("kof bench: " + spec.name + ": JS exited with " + ec);
                return null;
            }
        } else {
            List<String> command = commandFor(target, outDir);
            if (command == null) {
                System.err.println("kof bench: " + spec.name + ": cannot build command for " + target);
                return null;
            }
            List<String> effective = command;
            Path timeBin = Path.of("/usr/bin/time");
            boolean canMeasureRss = Files.isExecutable(timeBin)
                    && System.getProperty("os.name", "").toLowerCase().contains("linux");
            if (canMeasureRss) {
                effective = new ArrayList<>(List.of("/usr/bin/time", "-v"));
                effective.addAll(command);
            }
            ProcessBuilder pb = new ProcessBuilder(effective);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            byte[] buf = p.getInputStream().readAllBytes();
            int ec = p.waitFor();
            String text = new String(buf, StandardCharsets.UTF_8);
            if (canMeasureRss) {
                rssKb = parseRss(text);
                userMicros = parseTimeSeconds(text, "User time");
                systemMicros = parseTimeSeconds(text, "System time");
                output = stripTimeOutput(text);
            } else {
                output = text;
            }
            if (ec != 0) {
                System.err.println("kof bench: " + spec.name + ": exited with " + ec
                        + (verbose ? ": " + text : ""));
                return null;
            }
        }
        long wallNanos = System.nanoTime() - start;
        return new RunResult(wallNanos, rssKb, normalize(output), userMicros, systemMicros);
    }

    private static long parseTimeSeconds(String timeOutput, String prefix) {
        for (String line : timeOutput.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(prefix)) {
                String[] parts = trimmed.split(":");
                if (parts.length == 2) {
                    try {
                        return Math.round(Double.parseDouble(parts[1].trim().split(" ")[0]) * 1_000_000);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return 0;
    }

    private static List<String> commandFor(Target target, Path outDir) {
        if (target == Target.JVM) {
            String className = findMainClass(outDir);
            if (className == null) return null;
            List<String> cmd = new ArrayList<>();
            cmd.add(System.getProperty("java.home") + "/bin/java");
            cmd.add("-cp");
            cmd.add(outDir.toString());
            cmd.add(className);
            return cmd;
        }
        if (target == Target.NATIVE) {
            Path bin = outDir.resolve("Default/Main");
            if (!Files.isExecutable(bin)) return null;
            return List.of(bin.toString());
        }
        return null;
    }

    private static String findMainClass(Path dir) {
        try (var s = Files.walk(dir)) {
            List<String> candidates = s.filter(p -> p.toString().endsWith(".class"))
                    .map(p -> dir.relativize(p).toString()
                            .replace(".class", "").replace("/", ".").replace("\\", "."))
                    .toList();
            for (String c : candidates) {
                if (c.endsWith(".Main") || c.equals("Main")) return c;
            }
            return candidates.isEmpty() ? null : candidates.get(candidates.size() - 1);
        } catch (IOException e) {
            return null;
        }
    }

    private static Path findJsEntry(Path dir) {
        Path defaultEntry = dir.resolve("Default.mjs");
        if (Files.exists(defaultEntry)) return defaultEntry;
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs"))
                    .filter(p -> !p.toString().contains("kof-runtime"))
                    .findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static long parseRss(String timeOutput) {
        for (String line : timeOutput.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Maximum resident set size")) {
                String[] parts = trimmed.split(":");
                if (parts.length == 2) {
                    try {
                        return Long.parseLong(parts[1].trim().split(" ")[0]);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return 0;
    }

    private static String stripTimeOutput(String text) {
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (line.contains("Maximum resident set size") || line.contains("Command being timed")
                    || line.startsWith("\t")) {
                continue;
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    // ── Output handling ──────────────────────────────────────────────

    private static String expectedOutput(Path dir) {
        Path expected = dir.resolve("expected.txt");
        if (!Files.isRegularFile(expected)) return null;
        try {
            return normalize(Files.readString(expected));
        } catch (IOException e) {
            return null;
        }
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").stripTrailing();
    }

    private static String quote(String s) {
        return s.isEmpty() ? "(empty)" : "\"" + s + "\"";
    }

    private static long median(List<Long> values) {
        if (values.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 1) return sorted.get(mid);
        return (sorted.get(mid - 1) + sorted.get(mid)) / 2;
    }

    // ── Baseline ─────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadBaseline(Path file) {
        try {
            Object parsed = Json.parse(Files.readString(file));
            if (parsed instanceof Map<?, ?> m) return (Map<String, Object>) m;
        } catch (Exception e) {
            System.err.println("kof bench: cannot read baseline " + file + ": " + e.getMessage());
        }
        return null;
    }

    private static void writeBaseline(Path file, Target target, int iterations,
                                      Map<String, Object> results) {
        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("tool", "kof bench");
        baseline.put("version", KofVersion.version());
        baseline.put("target", target.name().toLowerCase());
        baseline.put("iterations", iterations);
        baseline.put("host", hostName());
        Map<String, Object> ms = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : results.entrySet()) {
            if (e.getValue() instanceof Map<?, ?> row && row.get("validated") == Boolean.TRUE) {
                ms.put(e.getKey(), row.get("ms"));
            }
        }
        baseline.put("results", ms);
        try {
            Files.createDirectories(file.getParent() != null ? file.getParent() : Path.of("."));
            Files.writeString(file, Json.stringify(baseline) + "\n");
            System.out.println("baseline written to " + file);
        } catch (IOException e) {
            System.err.println("kof bench: cannot write baseline: " + e.getMessage());
        }
    }

    // ── Report ───────────────────────────────────────────────────────

    private static void printTable(Target target, List<Map<String, Object>> rows) {
        System.out.println();
        System.out.printf("%-34s %8s %10s %8s %9s %s%n", "benchmark", "ms", "rss_kb", "cpu_ms", "ratio", "status");
        System.out.println("--------------------------------------------------------------------");
        for (Map<String, Object> row : rows) {
            String name = (String) row.get("name");
            Object ms = row.get("ms");
            Object rss = row.get("rss_kb");
            Object cpu = row.get("cpu_ms");
            Object ratio = row.get("ratio");
            String status = row.get("status") != null ? (String) row.get("status") : "ok";
            if (row.get("regression") == Boolean.TRUE) status = "REGRESSION";
            System.out.printf("%-34s %8d %10s %8s %9s %s%n",
                    name, ms instanceof Number n ? n.longValue() : 0,
                    rss instanceof Number n ? n.longValue() : "-",
                    cpu instanceof Number n ? n.longValue() : "-",
                    ratio instanceof Number n ? String.format("%.2f", n.doubleValue()) : "-",
                    status);
        }
        System.out.println("--------------------------------------------------------------------");
        System.out.println("target: " + target.name().toLowerCase() + " | version: " + KofVersion.version());
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

    private static void cleanup(Path dir) {
        try (var s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}