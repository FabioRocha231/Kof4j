package dev.kof.cli;

import dev.kof.compiler.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
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
            case "version" -> System.out.println("kof 0.1.0-SNAPSHOT");
            default -> { System.err.println("unknown: " + args[0]); printUsage(); }
        }
    }

    private static void run(String[] args) {
        if (args.length < 2) { System.err.println("usage: kof run <file.kf> [args...]"); return; }
        Path file = Path.of(args[1]);
        if (!Files.exists(file)) { System.err.println("file not found: " + file); System.exit(1); return; }

        Path tempDir;
        try { tempDir = Files.createTempDirectory("kof-run-"); }
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
        List<String> javaArgs = new ArrayList<>();
        javaArgs.add("java");
        javaArgs.add("-cp");
        javaArgs.add(tempDir.toString());
        javaArgs.add(className);
        for (int i = 2; i < args.length; i++) javaArgs.add(args[i]);

        try {
            ProcessBuilder pb = new ProcessBuilder(javaArgs);
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

    private static void build(String[] args) {
        if (args.length < 2) { System.err.println("usage: kof build <source-dir> [--target jvm|native] [--output <dir>]"); return; }
        Path src = Path.of(args[1]);
        Target target = Target.JVM;
        Path out = Path.of("build/classes");
        for (int i = 2; i < args.length; i++) {
            if (args[i].equals("--target") && i + 1 < args.length) {
                target = switch (args[i + 1]) {
                    case "jvm" -> Target.JVM;
                    case "native" -> Target.NATIVE;
                    default -> { System.err.println("unknown target: " + args[i + 1]); System.exit(1); yield Target.JVM; }
                };
                i++;
            } else if (args[i].equals("--output") && i + 1 < args.length) {
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
        System.out.println("  build <dir> [--target jvm|native] [--output <dir>]");
        System.out.println("  run <file.kf> [args...]");
        System.out.println("  serve <file.kf> [--port <port>] [--host <host>]");
        System.out.println("  version");
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

        try (ServerSocket serverSocket = new ServerSocket(port, 50, java.net.InetAddress.getByName(host))) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nkof serve shutting down...");
                try { serverSocket.close(); } catch (IOException ignored) {}
                cleanup(tempDir);
            }));

            System.out.println("listening for connections...");

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setSoTimeout(5000);
                    handleRequest(clientSocket, className, tempDir);
                } catch (IOException e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        System.err.println("connection error: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("server error: " + e.getMessage());
            cleanup(tempDir);
            System.exit(1);
        }
    }

    private static void handleRequest(Socket clientSocket, String className, Path classDir) {
        try {
            InputStream in = clientSocket.getInputStream();
            OutputStream out = clientSocket.getOutputStream();

            StringBuilder request = new StringBuilder();
            byte[] buffer = new byte[4096];
            int bytesRead;
            boolean headersComplete = false;
            while ((bytesRead = in.read(buffer)) != -1) {
                request.append(new String(buffer, 0, bytesRead));
                if (request.toString().contains("\r\n\r\n")) {
                    headersComplete = true;
                    break;
                }
            }

            if (!headersComplete) {
                sendResponse(out, 400, "Bad Request", "Invalid HTTP request");
                clientSocket.close();
                return;
            }

            String firstLine = request.toString().split("\r\n")[0];
            String[] parts = firstLine.split(" ");
            if (parts.length < 2) {
                sendResponse(out, 400, "Bad Request", "Invalid HTTP request");
                clientSocket.close();
                return;
            }

            String method = parts[0];
            String path = parts[1];

            String body = "";
            int headerEnd = request.toString().indexOf("\r\n\r\n");
            if (headerEnd >= 0) {
                body = request.substring(headerEnd + 4);
            }

            List<String> headers = new ArrayList<>();
            String[] headerLines = request.toString().split("\r\n");
            for (int i = 1; i < headerLines.length; i++) {
                if (headerLines[i].isEmpty()) break;
                headers.add(headerLines[i]);
            }

            String response = invokeHandler(className, method, path, body, headers);
            sendRawResponse(out, response);
            clientSocket.close();
        } catch (Exception e) {
            try {
                sendResponse(clientSocket.getOutputStream(), 500, "Internal Server Error", e.getMessage());
                clientSocket.close();
            } catch (IOException ignored) {}
        }
    }

    private static String invokeHandler(String className, String method, String path, String body, List<String> headers) {
        try {
            Class<?> clazz = Class.forName(className);
            Object instance = clazz.getDeclaredConstructor().newInstance();

            // Try handle(method, path, body) first
            for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals("handle") && m.getParameterCount() == 3) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params[0] == String.class && params[1] == String.class && params[2] == String.class) {
                        Object result = m.invoke(instance, method, path, body);
                        if (result instanceof String s) {
                            return buildHttpResponse(200, "OK", s);
                        }
                    }
                }
            }

            // Try handle() with no args
            for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals("handle") && m.getParameterCount() == 0) {
                    Object result = m.invoke(instance);
                    if (result instanceof String s) {
                        return buildHttpResponse(200, "OK", s);
                    }
                }
            }

            // Try method-specific handlers: get(), post(), etc.
            String handlerName = method.toLowerCase();
            for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(handlerName) && m.getParameterCount() == 0) {
                    Object result = m.invoke(instance);
                    if (result instanceof String s) {
                        return buildHttpResponse(200, "OK", s);
                    }
                }
            }

            return buildHttpResponse(200, "OK", "Hello from Kof!");
        } catch (Exception e) {
            return buildHttpResponse(500, "Internal Server Error", "Handler error: " + e.getMessage());
        }
    }

    private static String buildHttpResponse(int status, String statusText, String body) {
        byte[] bodyBytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String contentType = "text/plain; charset=utf-8";
        if (body.startsWith("{") || body.startsWith("[")) {
            contentType = "application/json; charset=utf-8";
        }
        return "HTTP/1.1 " + status + " " + statusText + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + bodyBytes.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n"
                + body;
    }

    private static void sendResponse(OutputStream out, int status, String statusText, String body) throws IOException {
        String response = buildHttpResponse(status, statusText, body);
        out.write(response.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.flush();
    }

    private static void sendRawResponse(OutputStream out, String response) throws IOException {
        out.write(response.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.flush();
    }
}
