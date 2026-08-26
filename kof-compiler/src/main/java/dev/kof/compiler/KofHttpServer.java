package dev.kof.compiler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public final class KofHttpServer {

    public interface Handler {

        String handle(String method, String path, String body, String query, String headers);
    }

    public record Request(String method, String path, String query, String rawHeaders, String body) {
    }

    private final Handler handler;
    private final ExecutorService pool;
    private ServerSocket serverSocket;
    private volatile boolean running;

    public KofHttpServer(Handler handler) {
        this(handler, Math.max(4, Runtime.getRuntime().availableProcessors()));
    }

    public KofHttpServer(Handler handler, int threads) {
        this.handler = handler;
        this.pool = Executors.newFixedThreadPool(threads);
    }


    public void serve(String host, int port) throws IOException {
        serverSocket = new ServerSocket(port, 64, InetAddress.getByName(host));
        running = true;
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
        while (running) {
            try {
                Socket client = serverSocket.accept();
                client.setSoTimeout(15000);
                pool.submit(() -> handleConnection(client));
            } catch (IOException e) {
                if (!running) break;
            }
        }
    }


    public int bind(String host, int port) throws IOException {
        serverSocket = new ServerSocket(port, 64, InetAddress.getByName(host));
        return serverSocket.getLocalPort();
    }


    public void acceptLoop() {
        running = true;
        while (running) {
            try {
                Socket client = serverSocket.accept();
                client.setSoTimeout(15000);
                pool.submit(() -> handleConnection(client));
            } catch (IOException e) {
                if (!running) break;
            }
        }
    }

    public void close() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        pool.shutdown();
    }

    private void handleConnection(Socket client) {
        try (client) {
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();
            Request request = readRequest(in);
            String raw = dispatch(request);
            out.write(raw.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (Exception e) {
            System.err.println("kof serve connection error: " + e.getMessage());
        }
    }

    public static Request readRequest(InputStream in) throws IOException {
        StringBuilder head = new StringBuilder();
        byte[] buffer = new byte[8192];
        int headerEnd = -1;
        while (true) {
            int n = in.read(buffer);
            if (n == -1) throw new IOException("connection closed before headers");
            head.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
            headerEnd = head.indexOf("\r\n\r\n");
            if (headerEnd >= 0) break;
            if (head.length() > 65536) throw new IOException("headers too large");
        }

        String requestText = head.toString();
        String headerBlock = requestText.substring(0, headerEnd);
        StringBuilder body = new StringBuilder(requestText.substring(headerEnd + 4));

        int contentLength = 0;
        for (String line : headerBlock.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-length:")) {
                try {
                    contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        while (body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length < contentLength) {
            int n = in.read(buffer);
            if (n == -1) break;
            body.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
        }
        if (body.length() > contentLength) {
            body.setLength(contentLength);
        }

        String[] lines = headerBlock.split("\r\n");
        String[] parts = lines.length > 0 ? lines[0].split(" ") : new String[0];
        String method = parts.length > 0 ? parts[0] : "GET";
        String fullPath = parts.length > 1 ? parts[1] : "/";
        String path = fullPath;
        String query = "";
        int q = fullPath.indexOf('?');
        if (q >= 0) {
            path = fullPath.substring(0, q);
            query = fullPath.substring(q + 1);
        }
        return new Request(method, path, query, headerBlock, body.toString());
    }

    public String dispatch(Request request) {
        try {
            String result = handler.handle(request.method(), request.path(), request.body(),
                    request.query(), request.rawHeaders());
            if (result == null) {
                return buildResponse(404, "Not Found", "{\"error\": \"not found\"}");
            }
            return buildResponse(200, "OK", result);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return buildResponse(500, "Internal Server Error", "{\"error\": \"handler error: " + msg + "\"}");
        }
    }

    public static String buildResponse(int status, String statusText, String body) {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        String contentType = "text/plain; charset=utf-8";
        String trimmed = body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            contentType = "application/json; charset=utf-8";
        } else if (trimmed.startsWith("<!DOCTYPE") || trimmed.startsWith("<html")) {
            contentType = "text/html; charset=utf-8";
        } else if (trimmed.startsWith("<style") || trimmed.startsWith(":root") || trimmed.startsWith("body {")) {
            contentType = "text/css; charset=utf-8";
        } else if (trimmed.startsWith("var ") || trimmed.startsWith("function ")
                || trimmed.startsWith("document.") || trimmed.startsWith("async ")) {
            contentType = "application/javascript; charset=utf-8";
        }
        return "HTTP/1.1 " + status + " " + statusText + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + bodyBytes.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n"
                + body;
    }
}