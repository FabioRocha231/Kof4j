# stdlib web — Stack Web Nativa do Kof

**Última atualização:** 27 de agosto de 2026
**Versão:** 0.2.0-beta (658 testes; `kof.http` JVM+JS)
**Status:** implementado (Fase 1 do plano de independência do Spring) — `kof serve` + `kof.http` JVM+JS

---

## 1. Filosofia

> Uma aplicação web Kof não precisa de Spring. HTTP, rotas, JSON, contexto de
> request e middleware são parte do ecossistema Kof.

Nenhuma dependência externa: o servidor HTTP é gerado dentro do runtime JVM do
próprio programa compilado (`dev.kof.runtime.KofRuntime`). Sem servlet
container, sem Spring MVC, sem annotations.

## 2. Exemplo completo

```kof
record User(String name, Int age)

main() {
    var app = web.app()

    // Middleware: retorna null para continuar; String para responder direto
    app.use {
        if (header("x-auth") == "secret") {
            return null
        }
        return "{\"error\": \"unauthorized\"}"
    }

    app.get("/hello") {
        return "Hello from Kof"
    }

    // Path parameter + query string
    app.get("/users/:id") {
        return "user " + param("id") + " q=" + query("name")
    }

    app.get("/agent") {
        return "agent=" + header("user-agent")
    }

    app.get("/me") {
        return method() + " " + path()
    }

    // Corpo da request
    app.post("/echo") {
        return "got:" + body()
    }

    // JSON tipado de ponta a ponta
    app.post("/user") {
        var user = json.decode<User>(body())
        return json.encode(user)
    }

    app.listen(8080)
}
```

```bash
kof serve app.kf              # compila e executa (a app chama app.listen)
kof run app.kf                # idem — o programa inicia o próprio servidor
```

## 3. API

### `web.app()`

Cria uma aplicação. O valor retornado (`kof.web.App`) é um handle; em runtime
é um identificador de registro interno.

### Rotas

| Chamada | Método HTTP |
|---------|-------------|
| `app.get(path) { ... }` | GET |
| `app.post(path) { ... }` | POST |
| `app.put(path) { ... }` | PUT |
| `app.delete(path) { ... }` | DELETE |
| `app.patch(path) { ... }` | PATCH |
| `app.options(path) { ... }` | OPTIONS |

O corpo `{ ... }` é um lambda trailing — o handler da rota. Um handler pode
também ser passado explicitamente: `app.get("/x", handler)`.

- `path` suporta segmentos com parâmetro: `/users/:id` (prefixo `:`).
- O handler retorna `String` (corpo da resposta, 200) ou `null` (404).
- A resposta detecta JSON automaticamente quando o corpo começa com `{` ou `[`
  (`Content-Type: application/json`).

### Middleware

`app.use { ... }` registra um middleware executado antes do roteamento.
Retorno `null` → continua; retorno `String` → resposta imediata (200).

### Servidor

| Chamada | Descrição |
|---------|-----------|
| `app.listen(port)` | Inicia o servidor (bloqueante) em `0.0.0.0` |
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

O contexto é por-request (ThreadLocal em runtime) — handlers podem ser
concorrentes sem estado compartilhado.

## 4. Concorrência

Cada conexão é tratada em uma virtual thread (JVM). O programador escreve
handlers síncronos; o runtime decide a estratégia.

## 5. Limitações atuais (Fase 1, 0.2.0-beta)

- Status codes customizados ainda não (200/404/500 automáticos).
- Headers de resposta customizados ainda não.
- O target `js` reporta `WEB001` (gap documentado, `kof.http` já funciona no JS via `Java HttpClient`).
- O target `native` (`x86_64`/`riscv64`/`aarch64`) não possui servidor web ainda (`WEB002` TLS também).
- `kof.http` client — ✅ JVM+JS (27/08), Native `HTTP002` pendente.
- Middleware/rotas de outros métodos HTTP além dos listados: futuramente.

## 6. Testes (0.2.0-beta)

`KofWebE2ETest` 9 + `KofHttpServerTest` 8 + `KofHttpE2ETest` 4 (JVM+JS, 27/08) + `KofWebTlsTest` 5 — cada teste compila um programa Kof, executa o
bytecode/JS como subprocesso e exercita o servidor/cliente com sockets reais
(routing, path params, query, headers, body, JSON round-trip, middleware,
404, múltiplas rotas com lambda trailing, `http.get/post/put/delete` + TLS).

## 7. Arquitetura

```
Kof source (.kf)
   ↓ CompilerDriver
Kof IR (KofCall kof_web_*)
   ↓ JvmBackend
bytecode JVM
   ↓
dev.kof.runtime.KofRuntime (gerado)  ← engine HTTP embutido no programa
   ├── KOF_WEB_APPS (registro de apps)
   ├── WebRoute (method, segments, params, handler)
   ├── WebRequest (method, path, query, headers, body)
   └── accept loop (virtual threads) + dispatch
```

As chamadas `kof_web_*` são resolvidas em compile-time pela tabela `KofWeb`
(dança análoga a `KofIo`): o programador nunca vê threads, sockets ou parsing
HTTP.

## 8. Referências

- Plano: `docs/plan-spring-independence.md` (Fase 1)
- Status: `docs/status.md`
- Roadmap: `docs/roadmap.md` (Fase 3 — Web Platform)