package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Diagnostic;
import dev.kof.compiler.Target;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LspServer — a minimal Language Server Protocol implementation on stdio.
 *
 * The editor-facing diagnostics come from the SAME frontend the compiler
 * uses (Lexer -> Parser -> SemanticAnalyzer -> CompilerDriver). There is no
 * parallel parser: what the editor sees is exactly what the compiler would
 * reject or accept.
 *
 * Supported: initialize, initialized, shutdown, exit,
 * textDocument/didOpen, textDocument/didChange (full sync),
 * textDocument/publishDiagnostics. Enough to give Kof syntax-aware error
 * reporting in any LSP-capable editor.
 */
final class LspServer {

    private final InputStream in;
    private final OutputStream out;
    private final CompilerDriver driver = new CompilerDriver();
    private boolean running = true;

    LspServer(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
    }

    void run() throws IOException {
        while (running) {
            int contentLength = -1;
            while (true) {
                String line = readLine();
                if (line == null) return;
                if (line.isBlank()) break;
                if (line.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
                }
            }
            if (contentLength < 0) continue;
            byte[] body = in.readNBytes(contentLength);
            if (body.length < contentLength) return;
            handleMessage(new String(body, StandardCharsets.UTF_8));
        }
    }

    private String readLine() throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
        }
        if (c == -1 && sb.isEmpty()) return null;
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void handleMessage(String raw) {
        Object parsed = Json.parse(raw);
        if (!(parsed instanceof Map<?, ?> msg)) return;
        Map<String, Object> m = (Map<String, Object>) msg;
        Object id = m.get("id");
        String method = m.get("method") == null ? null : m.get("method").toString();
        Map<String, Object> params = m.get("params") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();

        if (method == null) return; // response — not expected from client

        switch (method) {
            case "initialize" -> {
                Map<String, Object> capabilities = new LinkedHashMap<>();
                Map<String, Object> sync = new LinkedHashMap<>();
                sync.put("change", 1L); // FULL
                sync.put("openClose", Boolean.TRUE);
                capabilities.put("textDocumentSync", sync);
                capabilities.put("positionEncoding", "utf-16");
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("capabilities", capabilities);
                result.put("serverInfo", Map.of("name", "kof-lsp", "version", dev.kof.compiler.KofVersion.version()));
                respond(id, result);
            }
            case "initialized" -> { /* no-op */ }
            case "shutdown" -> respond(id, null);
            case "exit" -> running = false;
            case "textDocument/didOpen" -> publishDiagnostics(params);
            case "textDocument/didChange" -> publishDiagnostics(params);
            default -> { /* unknown method — ignore */ }
        }
    }

    private void respond(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        writeMessage(Json.stringify(response));
    }

    @SuppressWarnings("unchecked")
    private void publishDiagnostics(Map<String, Object> params) {
        Map<String, Object> textDoc = params.get("textDocument") instanceof Map<?, ?> td
                ? (Map<String, Object>) td : Map.of();
        String uri = textDoc.get("uri") == null ? "" : textDoc.get("uri").toString();
        String text = textDoc.get("text") == null ? "" : textDoc.get("text").toString();
        if (params.get("contentChanges") instanceof List<?> changes && !changes.isEmpty()
                && changes.get(0) instanceof Map<?, ?> c) {
            text = String.valueOf(((Map<?, ?>) c).get("text"));
        }

        List<Object> diagnostics = analyze(uri, text);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uri", uri);
        result.put("diagnostics", diagnostics);
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("jsonrpc", "2.0");
        notification.put("method", "textDocument/publishDiagnostics");
        notification.put("params", result);
        writeMessage(Json.stringify(notification));
    }

    private List<Object> analyze(String uri, String text) {
        List<Object> diagnostics = new ArrayList<>();
        Path tmpDir = null;
        Path file = null;
        try {
            tmpDir = Files.createTempDirectory("kof-lsp-");
            String name = "LspMain.kf";
            String path = uri.startsWith("file:") ? uri.substring("file:".length()) : uri;
            if (!path.isEmpty() && !path.endsWith(".kf")) {
                int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
                if (slash >= 0) path = path.substring(slash + 1);
            }
            if (path.endsWith(".kf")) name = path;
            file = tmpDir.resolve(name);
            Files.writeString(file, text);

            CompilationResult result = driver.compile(file, tmpDir.resolve("out"), Target.JVM);
            for (Diagnostic d : result.diagnostics().getDiagnostics()) {
                Map<String, Object> diag = new LinkedHashMap<>();
                Map<String, Object> range = new LinkedHashMap<>();
                Map<String, Object> start = new LinkedHashMap<>();
                Map<String, Object> end = new LinkedHashMap<>();
                start.put("line", Math.max(0, d.line() - 1));
                start.put("character", Math.max(0, d.column() - 1));
                end.put("line", Math.max(0, d.line() - 1));
                end.put("character", Math.max(0, d.column() - 1 + Math.max(0, d.length())));
                range.put("start", start);
                range.put("end", end);
                diag.put("range", range);
                diag.put("severity", d.severity() == Diagnostic.Severity.ERROR ? 1 : 2);
                diag.put("source", "kof");
                diag.put("code", d.code());
                diag.put("message", d.message() + (d.code() != null && !d.code().isEmpty()
                        ? " [" + d.code() + "]" : ""));
                diagnostics.add(diag);
            }
        } catch (IOException e) {
            Map<String, Object> diag = new LinkedHashMap<>();
            Map<String, Object> range = new LinkedHashMap<>();
            range.put("start", Map.of("line", 0L, "character", 0L));
            range.put("end", Map.of("line", 0L, "character", 0L));
            diag.put("range", range);
            diag.put("severity", 1);
            diag.put("source", "kof");
            diag.put("message", "internal error: " + e.getMessage());
            diagnostics.add(diag);
        } finally {
            if (tmpDir != null) {
                try (var s = Files.walk(tmpDir)) {
                    s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
                } catch (IOException ignored) {}
            }
        }
        return diagnostics;
    }

    private void writeMessage(String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        try {
            out.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(body);
            out.flush();
        } catch (IOException e) {
            running = false;
        }
    }
}