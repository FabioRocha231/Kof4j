# stdlib web — Stack Web Nativa do Kof

**Última atualização:** 30 de agosto de 2026
**Versão:** 0.2.6-beta
**Status:** implementado no JVM — HTTP + TLS + SSE + WebSocket; JS/Native reportam `WEB001`-`WEB004`

---

## 1. Filosofia

> Uma aplicação web Kof não precisa de Spring. HTTP, rotas, JSON, contexto de
> request e middleware são parte do ecossistema Kof.

Nenhuma dependência externa: o servidor HTTP é gerado dentro do runtime JVM do
próprio programa compilado (`dev.kof.runtime.KofRuntime`). Sem servlet
container, sem Spring MVC, sem annotations.

## 2. Exemplo completo

```kof
main() {
    var app = web.app()

    app.get("/hello") {
        return "Hello from Kof"
    }

    app.sse("/events/:room") {
        var r = param("room")
        sse("joined:" + r)
        sse("tick")
    }

    app.ws("/chat") {
        var m = wsMessage()
        if (m == "bye") {
            return
        }
        wsSend("echo: " + m)
    }

    app.listen(8080)
}
```

```bash
kof serve app.kf              # compila e executa (a app chama app.listen)
kof run app.kf                # idem — o programa inicia o próprio servidor
```

## Suporte por target

| Target | HTTP + TLS | SSE | WebSocket |
|--------|-----------|-----|-----------|
| JVM | ✅ | ✅ | ✅ |
| JS | ❌ `WEB001`/`WEB002` | ❌ `WEB003` | ❌ `WEB004` |
| Native | ❌ `WEB001`/`WEB002` | ❌ `WEB003` | ❌ `WEB004` |

## 3. API

### `web.app()`

Cria uma aplicação. O valor retornado (`kof.web.App`) é um handle; em runtime
é um identificador de registro interno.

### Rotas

| Chamada | Tipo |
|---------|------|
| `app.get(path) { ... }` | HTTP GET |
| `app.post(path) { ... }` | HTTP POST |
| `app.put(path) { ... }` | HTTP PUT |
| `app.delete(path) { ... }` | HTTP DELETE |
| `app.patch(path) { ... }` | HTTP PATCH |
| `app.options(path) { ... }` | HTTP OPTIONS |
| `app.sse(path, handler)` | SSE (JVM) |
| `app.ws(path, handler)` | WebSocket (JVM) |

O corpo `{ ... }` é um lambda trailing — o handler da rota. Um handler pode
também ser passado explicitamente: `app.get("/x", handler)`.

- `path` suporta segmentos com parâmetro: `/users/:id` (prefixo `:`).
- HTTP: o handler retorna `String` (corpo da resposta, 200) ou `null` (404);
  a resposta detecta JSON automaticamente quando o corpo começa com `{` ou `[`.
- SSE: o handler é chamado uma vez por conexão; `sse("...")` envia um evento
  `data: ...` e a conexão fica aberta até o handler retornar ou o cliente fechar.
- WebSocket: o handler é chamado por mensagem de texto; `wsMessage()` expõe a
  mensagem atual e `wsSend("...")` envia um frame de texto. A conexão fica
  aberta até CLOSE ou idle timeout.

### Middleware

`app.use { ... }` registra um middleware executado antes do roteamento.
Retorno `null` → continua; retorno `String` → resposta imediata (200).

### Servidor

| Chamada | Descrição |
|---------|-----------|
| `app.listen(port)` | Inicia o servidor (bloqueante) em `0.0.0.0` |
| `app.listenSecure(port)` | Inicia servidor HTTPS (JVM, certificado self-signed) |
| `app.port()` | Porta efetivamente vinculada (útil com `listen(0)`) |
| `app.close()` | Encerra o servidor (graceful shutdown) |

`app.listen(0)` vincula uma porta efêmera; `app.port()` revela a porta real.

### Contexto de request (dentro de handlers/middleware)

| Função | Retorna |
|--------|---------|
| `param("id")` | Path parameter |
| `query("name")` | Query parameter |
| `header("x-auth")` | Header (case-insensitive) |
| `body()` | Corpo cru da request |
| `method()` | Método HTTP ("GET", "POST", ...) |
| `path()` | Caminho da request |
| `status(code, body)` | Define o status HTTP e retorna o body |
| `headerSet(name, value)` / `setHeader(name, value)` | Define header de resposta |
| `sse(data)` | Dentro de `app.sse`, envia um evento SSE |
| `wsMessage()` | Dentro de `app.ws`, retorna a mensagem de texto atual |
| `wsSend(data)` | Dentro de `app.ws`, envia um frame de texto |

O contexto é por-request (ThreadLocal em runtime) — handlers podem ser
concorrentes sem estado compartilhado.

## 4. Concorrência

Cada conexão/rota é tratada em virtual threads (JVM). O programador escreve
handlers síncronos; o runtime decide a estratégia.

## 5. Limitações atuais

- SSE/WebSocket existem somente no JVM. JS e Native não têm backend web e
  emitem `WEB003` (SSE) / `WEB004` (WebSocket); HTTP/TLS já emitem
  `WEB001`/`WEB002`.
- Sem cap de conexões concorrentes.
- Sem limite configurável de frame/message no WebSocket.
- Sem deadline por evento no SSE.
- Hardening (limites configuráveis, backpressure, deadlines) é PR futuro.
- `kof.http` client — ✅ JVM+JS (27/08), Native `HTTP002` pendente.
- Middleware/rotas de outros métodos HTTP além dos listados: futuramente.

## 6. Testes

`KofWebE2ETest` 9 + `KofWebSseE2ETest` + `KofWebWsE2ETest` +
`KofWsFrameTest` + `KofWebStreamE2ETest` + `KofWebTlsTest` 5 + `KofHttpServerTest`
8 + `KofHttpE2ETest` 4 (JVM+JS, 27/08) — cada teste compila um programa Kof,
executa o bytecode/JS como subprocesso e exercita o servidor/cliente com
sockets reais (routing, path params, query, headers, body, JSON round-trip,
middleware, 404, múltiplas rotas, SSE, WebSocket, `http.get/post/put/delete`
+ TLS).

## 7. Arquitetura

```
Kof source (.kf)
   ↓ CompilerDriver
Kof IR (KofCall kof_web_*)
   ↓ JvmBackend
bytecode JVM
   ↓
dev.kof.runtime.KofRuntime (gerado)  ← engine HTTP/SSE/WS embutido no programa
   ├── KOF_WEB_APPS (registro de apps)
   ├── WebRoute (method, segments, params, handler, kind: HTTP/SSE/WS)
   ├── WebRequest (method, path, query, headers, body)
   ├── SseConnection / WsConnection / WsFrame
   └── accept loop (virtual threads) + dispatch
```

As chamadas `kof_web_*` são resolvidas em compile-time pela tabela `KofWeb`
(dança análoga a `KofIo`): o programador nunca vê threads, sockets ou parsing
HTTP.

## 8. Referências

- Plano: `docs/plan-spring-independence.md` (Fase 1)
- Status: `docs/status.md`
- Roadmap: `docs/roadmap.md` (Fase 3 — Web Platform)
