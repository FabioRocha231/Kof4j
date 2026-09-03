package dev.kof.cli;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Package manager MVP (TIER 1.4) — {@code kof deps}.
 *
 * <p>Gerencia o arquivo {@code kofdeps} (uma dependência Maven por linha,
 * {@code group:artifact:version}) e resolve para o cache local
 * {@code ~/.kof/deps}. A resolução é honesta: baixa o jar e o POM direto;
 * dependências transitivas ainda não são resolvidas (reportado, nunca
 * silencioso). O classpath resolvido é consumido por {@code kof build}
 * {@code --deps} e {@code kof run --deps}.</p>
 *
 * <p>Exemplo:</p>
 * <pre>{@code
 * kof deps init
 * kof deps add com.h2database:h2:2.2.224
 * kof deps list
 * kof deps resolve
 * kof run --deps main.kf
 * }</pre>
 */
final class Deps {

    private Deps() {}

    static final String DEPS_FILE = "kofdeps";
    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    static int run(String[] args) {
        if (args.length < 2 || "--help".equals(args[1]) || "-h".equals(args[1])) {
            System.out.println("usage: kof deps <init|add|remove|list|resolve>");
            System.out.println("  init [dir]                 create an empty kofdeps file");
            System.out.println("  add <g:a:v> [dir]          append a dependency (Maven g:a:v)");
            System.out.println("  remove <g:a:v> [dir]       remove a dependency");
            System.out.println("  list [dir]                 list declared dependencies");
            System.out.println("  resolve [dir]              download jars into ~/.kof/deps and print classpath");
            System.out.println();
            System.out.println("dependencies: one per line, group:artifact:version (Maven Central)");
            return 0;
        }
        try {
            return switch (args[1]) {
                case "init" -> init(args);
                case "add" -> add(args);
                case "remove" -> remove(args);
                case "list" -> list(args);
                case "resolve" -> resolve(args);
                default -> {
                    System.err.println("kof deps: unknown subcommand '" + args[1] + "'");
                    yield 1;
                }
            };
        } catch (IOException e) {
            System.err.println("kof deps: " + e.getMessage());
            return 1;
        }
    }

    private static Path depsFile(String[] args) {
        // args = [deps, subcomando, ...]. O diretório é:
        //   init/list/resolve: args[2] se existir, senão "."
        //   add/remove: a dependência é args[2]; o diretório é args[3] se
        //   existir, senão "."
        String dir;
        String sub = args[1];
        if ("add".equals(sub) || "remove".equals(sub)) {
            dir = args.length > 3 ? args[3] : ".";
        } else {
            dir = args.length > 2 ? args[2] : ".";
        }
        return Path.of(dir).resolve(DEPS_FILE);
    }

    private static int init(String[] args) throws IOException {
        Path file = depsFile(args);
        if (Files.exists(file)) {
            System.err.println("deps: " + file + " já existe");
            return 1;
        }
        Files.writeString(file, "");
        System.out.println("criado " + file + " (vazio) — adicione com: kof deps add <g:a:v>");
        return 0;
    }

    private static int add(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("usage: kof deps add <group:artifact:version> [dir]");
            return 1;
        }
        String dep = normalize(args[2]);
        if (dep == null) {
            System.err.println("deps: formato esperado group:artifact:version (ex.: com.h2database:h2:2.2.224)");
            return 1;
        }
        Path file = depsFile(args);
        if (!Files.exists(file)) Files.writeString(file, "");
        List<String> lines = new ArrayList<>(Files.readAllLines(file));
        if (lines.contains(dep)) {
            System.out.println("já declarada: " + dep);
            return 0;
        }
        lines.add(dep);
        Files.write(file, lines);
        System.out.println("adicionada: " + dep);
        return 0;
    }

    private static int remove(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("usage: kof deps remove <group:artifact:version> [dir]");
            return 1;
        }
        Path file = depsFile(args);
        if (!Files.exists(file)) {
            System.err.println("deps: " + file + " não existe");
            return 1;
        }
        List<String> lines = new ArrayList<>(Files.readAllLines(file));
        boolean removed = lines.remove(args[2]);
        Files.write(file, lines);
        if (removed) {
            System.out.println("removida: " + args[2]);
        } else {
            System.err.println("deps: '" + args[2] + "' não declarada");
            return 1;
        }
        return 0;
    }

    private static int list(String[] args) throws IOException {
        Path file = depsFile(args);
        if (!Files.exists(file)) {
            System.err.println("deps: " + file + " não existe (rode: kof deps init)");
            return 1;
        }
        List<String> lines = Files.readAllLines(file);
        if (lines.isEmpty()) {
            System.out.println("(nenhuma dependência declarada em " + file + ")");
        } else {
            for (String l : lines) {
                if (!l.isBlank()) System.out.println(l);
            }
        }
        return 0;
    }

    private static int resolve(String[] args) throws IOException {
        Path file = depsFile(args);
        if (!Files.exists(file)) {
            System.err.println("deps: " + file + " não existe (rode: kof deps init)");
            return 1;
        }
        List<String> lines = Files.readAllLines(file);
        List<String> missing = new ArrayList<>();
        for (String l : lines) {
            if (l.isBlank()) continue;
            String[] ga = l.split(":");
            if (ga.length != 3) {
                missing.add(l + " (formato inválido)");
                continue;
            }
            try {
                download(ga[0], ga[1], ga[2]);
            } catch (Exception e) {
                missing.add(l + " → " + e.getMessage());
            }
        }
        if (!missing.isEmpty()) {
            System.err.println("deps: resolução incompleta:");
            for (String m : missing) System.err.println("  " + m);
            System.err.println("note: dependências transitivas (POM) ainda não são resolvidas.");
            return 1;
        }
        System.out.println(classpath());
        return 0;
    }

    /** Normaliza "g:a:v" ou devolve null se o formato for inválido. */
    static String normalize(String dep) {
        String[] parts = dep.split(":");
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            return null;
        }
        return parts[0] + ":" + parts[1] + ":" + parts[2];
    }

    private static Path cacheDir() {
        String home = System.getProperty("user.home", ".");
        return Path.of(home, ".kof", "deps");
    }

    private static Path jarPath(String group, String artifact, String version) {
        Path dir = cacheDir().resolve(group.replace('.', '/')).resolve(artifact).resolve(version);
        return dir.resolve(artifact + "-" + version + ".jar");
    }

    private static void download(String group, String artifact, String version) throws IOException {
        Path jar = jarPath(group, artifact, version);
        if (Files.exists(jar)) return;
        Files.createDirectories(jar.getParent());
        String url = MAVEN_CENTRAL + group.replace('.', '/') + "/" + artifact + "/"
                + version + "/" + artifact + "-" + version + ".jar";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        try {
            HttpResponse<Path> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofFile(jar));
            if (resp.statusCode() != 200) {
                Files.deleteIfExists(jar);
                throw new IOException("HTTP " + resp.statusCode() + " para " + url);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrompido", e);
        }
        System.out.println("baixado " + group + ":" + artifact + ":" + version);
    }

    /** Classpath resolvido (jars no cache), separado por ':' (ou ';' no Windows). */
    static String classpath() throws IOException {
        return classpath(Path.of("."));
    }

    static String classpath(Path projectDir) throws IOException {
        Path file = projectDir.resolve(DEPS_FILE);
        if (!Files.exists(file)) return "";
        String sep = System.getProperty("os.name", "").toLowerCase().contains("win") ? ";" : ":";
        StringBuilder sb = new StringBuilder();
        for (String l : Files.readAllLines(file)) {
            if (l.isBlank()) continue;
            String[] ga = l.split(":");
            if (ga.length != 3) continue;
            Path jar = jarPath(ga[0], ga[1], ga[2]);
            if (Files.exists(jar)) {
                if (sb.length() > 0) sb.append(sep);
                sb.append(jar);
            }
        }
        return sb.toString();
    }
}