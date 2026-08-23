package dev.kof.runtime;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * KofJsRunner — executes the JavaScript emitted by the KofJS backend inside
 * the Kof process itself, using the embedded GraalJS engine.
 *
 * The KofJS target has no dependency on Node.js or any external JavaScript
 * runtime: the generated .mjs modules are standard ES2022+ ECMAScript modules,
 * and `kof run --target=js` (and the E2E suite) run them here, in-process.
 *
 * Platform operations (filesystem, stdout, stdin) are exposed to the module
 * through the `kof_platform` global, implemented in Java. The generated module
 * only talks to the platform-neutral kof-runtime.mjs; the platform layer
 * (kof-runtime-io.mjs) delegates to kof_platform.
 */
public final class KofJsRunner {

    private KofJsRunner() {}

    /**
     * Executes an ESM module file and returns the process exit code
     * (0 on success, 1 on runtime error).
     */
    public static int run(Path moduleFile) throws IOException {
        return run(moduleFile, System.out, System.in, System.err);
    }

    public static int run(Path moduleFile, OutputStream out, InputStream in,
                          OutputStream err) throws IOException {
        return run(moduleFile, out, in, err, false);
    }

    /**
     * Executes an ESM module. When {@code openWindow} is true and the program
     * created a kof.ui window (kofUiFlush was triggered), the serialized page
     * is written next to the module and opened in the system webview (the
     * platform default browser).
     */
    public static int run(Path moduleFile, OutputStream out, InputStream in,
                          OutputStream err, boolean openWindow) throws IOException {
        try (Context context = Context.newBuilder("js")
                .allowIO(true)
                .allowAllAccess(true)
                .option("engine.WarnInterpreterOnly", "false")
                .out(out)
                .err(err)
                .in(in)
                .build()) {
            exposePlatform(context, out, in);
            Source source = Source.newBuilder("js", moduleFile.toFile())
                    .mimeType("application/javascript+module")
                    .build();
            context.eval(source);
            if (openWindow) {
                String html = context.getBindings("js").getMember("kof__uiRootHtml").asString();
                if (html != null && !html.isEmpty()) {
                    openInWebview(moduleFile, html);
                }
            }
            return 0;
        } catch (Exception e) {
            try {
                String message = e.getMessage();
                if (message != null && !message.isBlank()) {
                    err.write((message + "\n").getBytes(StandardCharsets.UTF_8));
                    err.flush();
                }
            } catch (IOException ignored) {
            }
            return 1;
        }
    }

    private static void openInWebview(Path moduleFile, String html) throws IOException {
        // The webview runs the real application, not a snapshot: the compiled
        // module and the runtimes are copied next to a fresh index.html, so
        // DOM events (button clicks) execute inside the WebKit page itself.
        Path appDir = Files.createTempDirectory("kof-ui-");
        Files.writeString(appDir.resolve("kof-ui.html"), html);
        String entry = moduleFile.getFileName().toString();
        Files.writeString(appDir.resolve(entry), Files.readString(moduleFile));
        Path runtime = moduleFile.resolveSibling("kof-runtime.mjs");
        if (Files.exists(runtime)) {
            Files.copy(runtime, appDir.resolve("kof-runtime.mjs"));
        }
        Path ioRuntime = moduleFile.resolveSibling("kof-runtime-io.mjs");
        if (Files.exists(ioRuntime)) {
            Files.copy(ioRuntime, appDir.resolve("kof-runtime-io.mjs"));
        }
        String page = "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"utf-8\">\n"
                + "  <title>Kof</title>\n  <style>\n"
                + "    body { margin: 0; font-family: system-ui, sans-serif; }\n"
                + "    #kof-root { display: flex; flex-direction: column; gap: 8px;\n"
                + "                 padding: 16px; min-height: 100vh; box-sizing: border-box; }\n"
                + "    .kof-label { font-size: 16px; }\n"
                + "    .kof-button { font-size: 16px; padding: 8px 16px; cursor: pointer; }\n"
                + "  </style>\n</head>\n<body>\n  <div id=\"kof-root\"></div>\n"
                + "  <script type=\"module\" src=\"" + entry + "\"></script>\n</body>\n</html>\n";
        Files.writeString(appDir.resolve("index.html"), page);
        Path pagePath = appDir.resolve("index.html").toAbsolutePath();
        System.err.println("kof: window at " + pagePath);
        Path shim = findWebviewShim();
        if (shim != null) {
            try {
                new ProcessBuilder(shim.toString(), pagePath.toString()).start();
                return;
            } catch (IOException e) {
                System.err.println("kof: native webview failed (" + e.getMessage() + ") — falling back");
            }
        }
        try {
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(pagePath.toUri());
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", pagePath.toString()).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", pagePath.toString()).start();
            } else {
                new ProcessBuilder("xdg-open", pagePath.toString()).start();
            }
        } catch (IOException ignored) {
            System.err.println("kof: open " + pagePath + " to view the window");
        }
    }

    private static Path findWebviewShim() {
        String install = System.getProperty("kof.install.dir", "");
        if (!install.isEmpty()) {
            Path p = Path.of(install, "bin", "kof-webview");
            if (Files.isExecutable(p)) return p;
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("linux")) return null;
        Path p = Path.of("bin", "kof-webview");
        if (Files.isExecutable(p)) return p.toAbsolutePath();
        return null;
    }

    /**
     * Executes the module and returns the serialized kof.ui window HTML
     * (or null when the program created no window). Used by tests.
     */
    public static String runCaptureHtml(Path moduleFile, OutputStream out, InputStream in,
                                        OutputStream err) throws IOException {
        try (Context context = Context.newBuilder("js")
                .allowIO(true)
                .allowAllAccess(true)
                .option("engine.WarnInterpreterOnly", "false")
                .out(out)
                .err(err)
                .in(in)
                .build()) {
            exposePlatform(context, out, in);
            Source source = Source.newBuilder("js", moduleFile.toFile())
                    .mimeType("application/javascript+module")
                    .build();
            context.eval(source);
            Value html = context.getBindings("js").getMember("kof__uiRootHtml");
            return html.isString() && !html.asString().isEmpty() ? html.asString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Exposes the kof_platform object: IO and console primitives implemented
     * in Java. The generated JavaScript never reaches for Node/browser APIs.
     */
    private static void exposePlatform(Context context, OutputStream out, InputStream in) {
        Value bindings = context.getBindings("js");
        java.util.Map<String, Object> platform = new java.util.LinkedHashMap<>();
        platform.put("print", (ProxyExecutable) args -> {
            for (Value arg : args) {
                try {
                    out.write(String.valueOf(arg.isNull() ? "null" : arg).getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    return 0;
                }
            }
            return 0;
        });
        platform.put("processRun", (ProxyExecutable) args -> {
            try {
                String program = args[0].asString();
                java.util.List<String> cmd = new java.util.ArrayList<>();
                cmd.add(program);
                if (args.length > 1 && !args[1].isNull()) {
                    long n = args[1].getArraySize();
                    for (int i = 0; i < n; i++) {
                        cmd.add(args[1].getArrayElement(i).asString());
                    }
                }
                Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
                String out = new String(p.getInputStream().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
                String err = new String(p.getErrorStream().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
                int code = p.waitFor();
                java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
                result.put("stdout", out);
                result.put("stderr", err);
                result.put("exitCode", code);
                return result;
            } catch (Exception e) {
                java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
                result.put("stdout", "");
                result.put("stderr", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                result.put("exitCode", -1);
                return result;
            }
        });
        platform.put("readLine", (ProxyExecutable) args -> readLine(in));
        platform.put("readFile", (ProxyExecutable) args -> {
            try {
                return Files.readString(Path.of(args[0].asString()));
            } catch (IOException e) {
                return null;
            }
        });
        platform.put("writeFile", (ProxyExecutable) args -> {
            try {
                Files.writeString(Path.of(args[0].asString()), args[1].asString());
                return 0;
            } catch (IOException e) {
                return -1;
            }
        });
        platform.put("fileExists", (ProxyExecutable) args -> Files.exists(Path.of(args[0].asString())) ? 1 : 0);
        platform.put("fileIsFile", (ProxyExecutable) args -> Files.isRegularFile(Path.of(args[0].asString())) ? 1 : 0);
        platform.put("fileIsDir", (ProxyExecutable) args -> Files.isDirectory(Path.of(args[0].asString())) ? 1 : 0);
        platform.put("readText", (ProxyExecutable) args -> readFileText(args));
        platform.put("writeText", (ProxyExecutable) args -> writeFileText(args, false));
        platform.put("appendText", (ProxyExecutable) args -> writeFileText(args, true));
        platform.put("readBytes", (ProxyExecutable) args -> readBytes(args));
        platform.put("writeBytes", (ProxyExecutable) args -> writeBytes(args, false));
        platform.put("appendBytes", (ProxyExecutable) args -> writeBytes(args, true));
        platform.put("delete", (ProxyExecutable) args -> {
            try {
                Files.deleteIfExists(Path.of(args[0].asString()));
                return 0;
            } catch (IOException e) {
                return -1;
            }
        });
        platform.put("fileSize", (ProxyExecutable) args -> {
            try {
                return Files.size(Path.of(args[0].asString()));
            } catch (IOException e) {
                return -1L;
            }
        });
        platform.put("fileName", (ProxyExecutable) args -> {
            Path p = Path.of(args[0].asString());
            Path name = p.getFileName();
            return name == null ? args[0].asString() : name.toString();
        });
        platform.put("pathParent", (ProxyExecutable) args -> {
            Path parent = Path.of(args[0].asString()).getParent();
            return parent == null ? null : parent.toString();
        });
        platform.put("pathFileName", (ProxyExecutable) args -> {
            Path name = Path.of(args[0].asString()).getFileName();
            return name == null ? args[0].asString() : name.toString();
        });
        platform.put("pathExtension", (ProxyExecutable) args -> {
            String name = Path.of(args[0].asString()).getFileName().toString();
            int dot = name.lastIndexOf('.');
            return dot <= 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1);
        });
        platform.put("pathNormalize", (ProxyExecutable) args -> Path.of(args[0].asString()).normalize().toString());
        platform.put("pathResolve", (ProxyExecutable) args -> Path.of(args[0].asString()).resolve(args[1].asString()).toString());
        platform.put("pathIsAbsolute", (ProxyExecutable) args -> Path.of(args[0].asString()).isAbsolute() ? 1 : 0);
        platform.put("pathToAbsolute", (ProxyExecutable) args -> Path.of(args[0].asString()).toAbsolutePath().toString());
        platform.put("dirCreate", (ProxyExecutable) args -> dirCreate(args, false));
        platform.put("dirCreateDirs", (ProxyExecutable) args -> dirCreate(args, true));
        platform.put("dirDelete", (ProxyExecutable) args -> {
            try {
                Files.deleteIfExists(Path.of(args[0].asString()));
                return 0;
            } catch (IOException e) {
                return -1;
            }
        });
        platform.put("dirList", (ProxyExecutable) args -> dirList(args));
        // kof.security platform primitives (docs/security.md §5)
        platform.put("getenv", (ProxyExecutable) args ->
                System.getenv(args[0].asString()));
        platform.put("randomBytesHex", (ProxyExecutable) args -> {
            int n = args[0].asInt();
            byte[] buf = new byte[Math.max(0, Math.min(n, 4096))];
            new java.security.SecureRandom().nextBytes(buf);
            StringBuilder sb = new StringBuilder(buf.length * 2);
            for (byte b : buf) sb.append(String.format("%02x", b));
            return sb.toString();
        });
        platform.put("randomInt", (ProxyExecutable) args -> {
            int bound = args[0].asInt();
            return bound <= 0 ? 0 : new java.security.SecureRandom().nextInt(bound);
        });
        platform.put("pbkdf2Hex", (ProxyExecutable) args -> {
            String password = args[0].asString();
            String saltHex = args[1].asString();
            int iterations = args[2].asInt();
            byte[] salt = new byte[saltHex.length() / 2];
            for (int i = 0; i < salt.length; i++) {
                salt[i] = (byte) Integer.parseInt(saltHex.substring(i * 2, i * 2 + 2), 16);
            }
            try {
                byte[] dk = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(new javax.crypto.spec.PBEKeySpec(
                                password.toCharArray(), salt, iterations, 256))
                        .getEncoded();
                StringBuilder sb = new StringBuilder(dk.length * 2);
                for (byte b : dk) sb.append(String.format("%02x", b));
                return sb.toString();
            } catch (Exception e) {
                return null;
            }
        });
        bindings.putMember("kof_platform", ProxyObject.fromMap(platform));
    }

    private static String readLine(InputStream in) {
        StringBuilder sb = new StringBuilder();
        try {
            int c;
            while ((c = in.read()) != -1) {
                if (c == '\n') return sb.toString();
                sb.append((char) c);
            }
        } catch (IOException ignored) {
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static Object readFileText(Value[] args) {
        try {
            return Files.readString(Path.of(args[0].asString()));
        } catch (IOException e) {
            return null;
        }
    }

    private static Object writeFileText(Value[] args, boolean append) {
        try {
            if (append) {
                Files.writeString(Path.of(args[0].asString()), args[1].asString(),
                        StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            } else {
                Files.writeString(Path.of(args[0].asString()), args[1].asString());
            }
            return 0;
        } catch (IOException e) {
            return -1;
        }
    }

    private static Object readBytes(Value[] args) {
        try {
            byte[] bytes = Files.readAllBytes(Path.of(args[0].asString()));
            int[] out = new int[bytes.length];
            for (int i = 0; i < bytes.length; i++) out[i] = bytes[i] & 0xFF;
            return out;
        } catch (IOException e) {
            return null;
        }
    }

    private static Object writeBytes(Value[] args, boolean append) {
        try {
            byte[] bytes = new byte[(int) args[1].getArraySize()];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) (args[1].getArrayElement(i).asInt() & 0xFF);
            }
            Path p = Path.of(args[0].asString());
            if (append) {
                Files.write(p, bytes, java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            } else {
                Files.write(p, bytes);
            }
            return 0;
        } catch (IOException e) {
            return -1;
        }
    }

    private static Object dirCreate(Value[] args, boolean recursive) {
        try {
            if (recursive) {
                Files.createDirectories(Path.of(args[0].asString()));
            } else {
                Files.createDirectory(Path.of(args[0].asString()));
            }
            return 0;
        } catch (IOException e) {
            return -1;
        }
    }

    private static Object dirList(Value[] args) {
        try (var stream = Files.list(Path.of(args[0].asString()))) {
            List<String> names = new ArrayList<>();
            stream.map(p -> p.toString()).sorted().forEach(names::add);
            return names.toArray(new String[0]);
        } catch (IOException e) {
            return null;
        }
    }
}