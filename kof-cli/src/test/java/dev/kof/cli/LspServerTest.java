package dev.kof.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * LSP references/rename — mock (didOpen + request) sem processo.
 * Verifica capability anunciada e que as ocorrências de um identificador
 * são encontradas em fronteiras de palavra (sem confundir prefixos/sufixos).
 */
class LspServerTest {

    private static final String URI = "file:///tmp/main.kf";

    private static byte[] frame(String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        byte[] header = ("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[header.length + body.length];
        System.arraycopy(header, 0, out, 0, header.length);
        System.arraycopy(body, 0, out, header.length, body.length);
        return out;
    }

    private static byte[] all(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) len += p.length;
        byte[] out = new byte[len];
        int off = 0;
        for (byte[] p : parts) { System.arraycopy(p, 0, out, off, p.length); off += p.length; }
        return out;
    }

    /** Extrai todos os envelopes JSON de uma saída LSP (skip de headers). */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> messages(String raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        int pos = 0;
        while (true) {
            int h = raw.indexOf("Content-Length:", pos);
            if (h < 0) break;
            int end = raw.indexOf("\r\n\r\n", h);
            int len = Integer.parseInt(raw.substring(h + "Content-Length:".length(), end).trim());
            int body = end + 4;
            Object parsed = Json.parse(raw.substring(body, body + len));
            if (parsed instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
            pos = body + len;
        }
        return out;
    }

    private static Map<String, Object> byId(List<Map<String, Object>> msgs, long id) {
        for (Map<String, Object> m : msgs) {
            if (m.get("id") != null && m.get("id").equals(id)) return m;
        }
        throw new AssertionError("sem resposta id=" + id + " em " + msgs);
    }

    @SuppressWarnings("unchecked")
    @Test
    void initializeAnnouncesReferencesAndRenameCapabilities() throws Exception {
        String req = "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":{}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LspServer server = new LspServer(new ByteArrayInputStream(frame(req)), out);
        server.run();
        Map<String, Object> resp = byId(messages(out.toString(StandardCharsets.UTF_8)), 0);
        Map<String, Object> res = (Map<String, Object>) resp.get("result");
        Map<String, Object> caps = (Map<String, Object>) res.get("capabilities");
        assertEquals(Boolean.TRUE, caps.get("referencesProvider"));
        assertEquals(Boolean.TRUE, caps.get("renameProvider"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void referencesReturnsAllWordBoundaries() throws Exception {
        String text = "var count := 0\nprintln(count)\nvar counter := 1\n";
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\",\"text\":\"" + Json.escape(text) + "\"}}}";
        String req = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"textDocument/references\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\"},"
                + "\"position\":{\"line\":0,\"character\":4},"
                + "\"context\":{\"includeDeclaration\":true}}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LspServer server = new LspServer(new ByteArrayInputStream(all(frame(didOpen), frame(req))), out);
        server.run();
        List<Map<String, Object>> msgs = messages(out.toString(StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        List<Object> locs = (List<Object>) byId(msgs, 1).get("result");
        assertEquals(2, locs.size());
        for (Object l : locs) {
            assertEquals(URI, ((Map<?, ?>) l).get("uri"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void renameProducesEditsForEveryOccurrence() throws Exception {
        String text = "var count := 0\nprintln(count)\nprintln(count)\n";
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\",\"text\":\"" + Json.escape(text) + "\"}}}";
        String req = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"textDocument/rename\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\"},"
                + "\"position\":{\"line\":0,\"character\":4},\"newName\":\"total\"}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LspServer server = new LspServer(new ByteArrayInputStream(all(frame(didOpen), frame(req))), out);
        server.run();
        Map<String, Object> resp = byId(messages(out.toString(StandardCharsets.UTF_8)), 2);
        @SuppressWarnings("unchecked")
        Map<String, Object> res = (Map<String, Object>) resp.get("result");
        List<Object> docChanges = (List<Object>) res.get("documentChanges");
        assertEquals(1, docChanges.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> docEdit = (Map<String, Object>) docChanges.get(0);
        assertEquals(URI, ((Map<?, ?>) docEdit.get("textDocument")).get("uri"));
        @SuppressWarnings("unchecked")
        List<Object> edits = (List<Object>) docEdit.get("edits");
        assertEquals(3, edits.size());
        for (Object e : edits) {
            assertEquals("total", ((Map<?, ?>) e).get("newText"));
        }
    }

    @Test
    void renameWithNoWordOrInvalidNameReturnsNullResult() throws Exception {
        String text = "var count := 0\n";
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\",\"text\":\"" + Json.escape(text) + "\"}}}";
        // char 10 = ':' (não-identificador) → palavra vazia → null
        String reqNoWord = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"textDocument/rename\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\"},"
                + "\"position\":{\"line\":0,\"character\":10},\"newName\":\"x\"}}";
        String reqBadName = "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"textDocument/rename\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + URI + "\"},"
                + "\"position\":{\"line\":0,\"character\":4},\"newName\":\"bad name\"}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LspServer server = new LspServer(
                new ByteArrayInputStream(all(frame(didOpen), frame(reqNoWord), frame(reqBadName))), out);
        server.run();
        List<Map<String, Object>> msgs = messages(out.toString(StandardCharsets.UTF_8));
        assertEquals(null, byId(msgs, 3).get("result"), "sem palavra no cursor → result nulo");
        assertEquals(null, byId(msgs, 4).get("result"), "newName inválido → result nulo");
    }
}
