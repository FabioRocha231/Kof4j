# ECOSYSTEM-COVERAGE.md — Matriz de Cobertura do Ecossistema Kof

> Auditoria da stdlib do Kof contra o ecossistema de capacidades de uma
> plataforma moderna (checklist derivado do ecossistema Spring, usado como
> **matriz de capacidades**, não como especificação de API).
>
> **Data:** 25 de agosto de 2026 · **Versão:** 0.1.0
> **Método:** auditoria do repositório (código + testes + docs) — ver §2.
> **Resultado:** nenhuma implementação nova foi feita neste documento —
> apenas inventário, matriz, gaps, prioridade e estratégia.

---

# 1. CLASSIFICAÇÃO

| Status | Significado |
|--------|-------------|
| `DONE` | implementado e testado (pelo menos JVM; ver colunas de target) |
| `PARTIAL` | existe, com limitações conhecidas |
| `PLANNED` | desenhado/documentedo, não implementado |
| `NA` | não se aplica à plataforma (por decisão de design) |
| `EXTERNAL` | fora da stdlib (interoperabilidade ou ferramenta externa) |

Colunas de target: `JVM` / `Native` / `JS` = suporte da capacidade naquele
backend. `Docs` = referência em `docs/`. `Tests` = arquivo(s) de teste em
`kof-compiler/src/test/java/dev/kof/compiler/`.

---

# 2. INVENTÁRIO ATUAL DO KOF (resumo da auditoria)

## 2.1 Mecanismo da stdlib

A stdlib não é uma biblioteca runtime clássica: cada módulo é uma
**tabela de dispatch em compile-time** no compilador.

```text
Código Kof → SemanticAnalyzer (tipos) → CompilerDriver (lowering p/ KofCall "kof_*")
  → JvmRuntime   (gera dev.kof.runtime.KofRuntime.java)
  → NativeRuntime (assembly x86-64, syscalls, sem libc)
  → JsBackend    (kof-runtime.mjs + kof_platform)
```

Gaps de target produzem diagnóstico em compile-time (SECN00x, CONC001,
JSN00x, WEB001) — nunca divergência silenciosa.

## 2.2 Superfície real (módulos → invocações Kof)

| Módulo | Invocações Kof | Arquivo de origem | Tests |
|--------|----------------|-------------------|-------|
| `kof.core`/`kof.collections` | `println/print`, `String` (concat, length, indexOf, split...), `List<T>`, `listOf` | JvmRuntime/NativeRuntime/JsBackend | JvmE2ETest, NativeE2ETest, KofJsE2ETest |
| `kof.io` | `File/Path/Directory` (+métodos), `readFile/writeFile/readLine` | KofIo.java | IoE2ETest (15) |
| `kof.time` | `now()`, `sleep`, `interval`/`cancel` (scheduler) | KofTime.java | KofTimeE2ETest (4) |
| `kof.json` | `json.encode/decode<T>` | JvmRuntime/NativeRuntime/JsBackend | JsonE2ETest (14) |
| `kof.security` | `passwords.*`, `crypto.*`, `jwt.*`, `secrets.*`, `security.*`, `auth.*` | KofSecurity.java | KofSecurityTest (22) |
| `kof.web` | `web.app()`, `app.get/post/.../use/listen/port/close`, `param/query/header/body/method/path` | KofWeb.java + KofHttpServer.java | KofWebE2ETest (9), KofHttpServerTest (8) |
| `kof.http` (client) | `http.get/post/put/delete/patch/options/status/timeout` (headers, JSON) | KofHttp.java | KofHttpE2ETest (3) |
| `kof.mq` | `mq.publish/subscribe/unsubscribe`, `queue/push/pop/size` (em memória) | KofMq.java | KofMqE2ETest (4) |
| `kof.concurrent` | `spawn expr` / `spawn { }` (join implícito) | JvmRuntime | SpawnE2ETest (3) |
| `kof.test` | `assert(cond[, msg])`, `test "nome" { }` (runner sintetizado), `kof test` | CompilerDriver/CLI | AssertE2ETest (5), StructuredTestE2ETest (11) |
| `kof.ui` | `Color/Theme/Palette`, `Window/Label/Button/Input`, `Column/Row/View/Style`, eventos por lambda com capturas, webview nativo | KofUi.java, JsBackend (runtime), kof-webview.c | UiE2ETest (14), WindowE2ETest (3) |
| `kof.config` | `config.get/env/has`, `config.str/int/long/bool(name, fallback)` — arquivo + profiles + env, precedência | KofConfig.java | KofConfigE2ETest (8) |
| `kof.log` | `log.debug/info/warn/error`, níveis (default INFO), `off`, warn→stderr | KofLog.java | KofLogE2ETest (7), NativeLogE2ETest (17) |
| `kof.cli` | `kof build/run/serve/check/test/bench/profile/inspect/debug/info/lsp/install/version` | kof-cli | Bench, KofDebug E2E |

## 2.3 kof.security — inventário (6 namespaces, dispatch compile-time)

| Namespace | Chamada | JVM | Native | JS |
|-----------|---------|-----|--------|----|
| `passwords` | `hash/verify/needsRehash` (PBKDF2-HMAC-SHA256 600k) | ✅ | ❌ SECN001 | ✅ |
| `crypto` | `sha256`, `sha512`, `hmacSha256`, `encryptAesGcm/decryptAesGcm`, `randomHex`, `randomInt` | ✅ (sha512/AES-GCM) | ✅ sha256/hmac/random; ❌ sha512 SECN003, AES-GCM SECN002 | ✅ |
| `jwt` | `create(claims, secret[, ttl])`, `verify(token, secret[, iss, aud])`, `secret()` (HS256 fixo, iat/exp) | ✅ | ❌ SECN004 (diagnóstico em compile-time — G7 fechado) | ✅ |
| `secrets` | `get(name)`, `get(name, fallback)` (env `KOF_*`), `redact(value)` | ✅ | ✅ (`/proc/self/environ`) | ✅ |
| `security` | `constantTimeEquals`, `csrfToken/csrfValid`, `corsAllowed`, headers (CSP/HSTS/nosniff/Frame/Referrer), `randomHex/randomInt`, `redact` | ✅ | ✅ constant-time/redact; ❌ csrf/cors/headers | ✅ constant-time/redact; ❌ csrf/cors/headers |
| `auth` (web) | `secret(token)`, `token()`, `authenticated()`, `claims()`, `user()`, `hasRole(r)`, `hasPermission(p)` (Bearer JWT + ThreadLocal por request) | ✅ | ❌ | ❌ |

Formato dos hashes: `pbkdf2$sha256$<iter>$<saltB64>$<hashB64>`;
AES-GCM: `aesgcm$<ivB64>$<ctB64>` (key 32B, IV 12B).
Documentação: `docs/security.md`; testes: `KofSecurityTest` (22).

## 2.4 kof.web — inventário

- `web.app()` → rotas `app.get/post/put/delete/patch/options(path) { }`,
  middleware `app.use { }`, `app.listen(port)` (bloqueante, virtual
  threads), `app.port()`, `app.close()`.
- Contexto: `param/query/header/body/method/path` (ThreadLocal por request).
- Path params `:id`; query e headers case-insensitive; Content-Type
  automático (JSON se `{`/`[`); 404/500; middlewares em cadeia.
- Engine: `WebRoute/WebRequest` gerados no KofRuntime; `KofHttpServer`
  (legado `kof serve`, `ReflectiveHandler`).
- Targets: JVM ✅; Native ❌ (sem `kof_web_*` no asm); JS ❌ WEB001.
- Tests: `KofWebE2ETest` (9, sockets reais), `KofHttpServerTest` (8).
- Docs: `docs/stdlib-web.md`.

## 2.5 Runtimes

| Runtime | Local | Conteúdo |
|---------|-------|----------|
| JVM | gerado no compile (`dev.kof.runtime.KofRuntime`) | json, io, time, spawn, web, security, ui |
| Native | `NativeRuntime.java` (asm x86-64, sem libc) | strings, listas, json, io, sec (parcial), net (símbolos), time, print |
| JS | `JsBackend` gera `kof-runtime.mjs` + `kof-runtime-io.mjs`; `kof-runtime` module = `KofJsRunner` (GraalJS embarcado) | linguagem, io via `kof_platform`, sec, ui (DOM/webview) |

## 2.6 Testes (38 arquivos, 590 JUnit) — por módulo

Security (22) · CompilerDriver (190) · Native E2E (50) · KofJS E2E (35) ·
JVM E2E (29) · Optimizer (21) · Io (15) · Json (14) · CoreRegression (12) ·
BackendParity (10) · Exceptions (9) · Web E2E (9) · HttpServer (8) ·
**KofConfig (8)** · **KofLog (7)** · Idiomatic (7+6) · Ui (6) · Assert (5) ·
FunctionSyntax (4) · Lambda (4) · **KofTime (4)** · **KofMq (4)** ·
**KofHttp (3)** · TuringComplete (3) · **KofOrm (16, E2E MariaDB/Postgres)** ·
**KofDb (8)** · Spawn (3) · Window (3) · IRStatistics (2) · DebugInfo (2) ·
NativeDebug (5) · StructuredTest (11) · AndroidInterop (11).
Golden: `tests/golden/` 16 casos × jvm+native (16/16).

## 2.7 Benchmarks (33, em 16 categorias)

micro, algorithms, collections, strings, math, objects, inheritance,
interfaces, generics, json, io, concurrency, startup, memory, stress,
applications + `benchmarks/security/` (password-hash, jwt, hash-speed,
aes-gcm). Tooling: `kof bench` (mediana + RSS + baseline), `kof profile`.

---

# 3. MATRIZ DE COBERTURA

Legenda nas colunas de target: `y` = suportado, `~` = parcial, `–` = não.
`Docs`: `security.md` = `docs/security.md`; `stdlib.md` = `docs/stdlib.md`;
`web` = `docs/stdlib-web.md`; `concurrency` = `docs/concurrency.md`.

## 3.1 Core / Application

| Capacidade | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| application lifecycle | `main()`/`args` | y | y (args vazios) | y | UiE2ETest | language-state.md |
| configuration model | `config.get/str/int/long/bool/has` (arquivo + env + profiles) | y | y (CONFIG001 fechado) | – CONF001 | KofConfigE2ETest | stdlib.md |
| dependency injection | `NA` (sem container; resolução direta) | — | — | — | — | philosophy.md |
| events | `PLANNED` (event bus) | — | — | — | — | roadmap.md |
| validation | ✅ `kof.validation` (required/notBlank/minLength/maxLength/lengthBetween/isEmail/isUrl/matches/isInt/isLong/inRange/min/max) — JVM/Native/JS | y | y | y | KofValidationTest | stdlib.md |
| scheduling | ✅ `kof.time.sleep/interval/cancel` (JVM); TIME001 no Native/JS | y | – | – | KofTimeE2ETest | stdlib.md |
| caching | `PLANNED` | — | — | — | — | roadmap.md |
| transactions | ✅ `transaction {}` (JVM; commit/rollback real) | y | – DB001 | – DB001 | KofDbE2ETest | future/DATABASE_VISION.md |
| resource management | `PARTIAL` (try/finally real) | y | y | — | ExceptionsE2ETest | language-state.md |
| profiles/environments | `PARTIAL` (profile file + env; o resto em kof.config) | y | – CONFIG001 | – CONFIG001 | KofConfigE2ETest | — |

## 3.2 Web / HTTP / REST

| Capacidade | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| HTTP server | `web.app()` | y | – | – WEB001 | KofWebE2ETest | web |
| routing (path params, query, headers) | `app.get("/users/:id")` | y | – | – | KofWebE2ETest | web |
| REST verbs | get/post/put/delete/patch/options | y | – | – | KofWebE2ETest | web |
| JSON body | automático (Content-Type) | y | – | – | KofWebE2ETest | web |
| middleware | `app.use` | y | – | – | KofWebE2ETest | web |
| HTTP client | ✅ `kof.http` (get/post/put/delete/patch/options/status/timeout, headers, JSON; HTTP002 Native/JS) | y | – | – | KofHttpE2ETest | http.md |
| typed path/query/body | `PLANNED` (hoje strings) | — | — | — | — | web |
| status codes custom | `PLANNED` | — | — | — | — | web |
| headers de resposta custom | `PLANNED` | — | — | — | — | web |
| cookies | `PLANNED` | — | — | — | — | roadmap.md |
| multipart | `PLANNED` | — | — | — | — | — |
| content negotiation | `PLANNED` | — | — | — | — | — |
| error handling | 404/500 + mensagem | y | – | – | KofWebE2ETest | web |
| WebSocket | `PLANNED` | — | — | — | — | roadmap.md |
| SSE | `PLANNED` | — | — | — | — | — |
| gRPC / GraphQL / SOAP | `EXTERNAL`/`PLANNED` (interop) | — | — | — | — | roadmap.md |
| REST documentation (OpenAPI) | `PLANNED` | — | — | — | — | roadmap.md |
| HATEOAS | `NA` (sem framework pesado) | — | — | — | — | — |

## 3.3 Data / Database

| Capacidade | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| SQL / JDBC | ✅ `kof.db` (SQL-first) + SQLite nativo via `.so` | y | y (SQLite; MySQL WIP) | – DB001 | KofDbE2ETest | future/DATABASE_VISION.md |
| `db.connect/query/transaction` | ✅ (+ `query<T>` tipado) | y | y | – DB001 | KofDbE2ETest | future/DATABASE_VISION.md |
| prepared statements | ✅ (binds `?`) | y | y | – DB001 | KofDbE2ETest | — |
| connection pools | `PLANNED` | — | — | — | — | — |
| migrations | ✅ `orm.migrate` versionado (`kof_migrations`) | y | – ORM001 | – ORM001 | KofOrmE2ETest | future/DATABASE_VISION.md |
| repositories/ORM | ✅ `kof.orm`: `entity` + create/save/find/all/where/delete/count | y | – ORM001 | – ORM001 | KofOrmE2ETest | future/DATABASE_VISION.md |
| NoSQL (MongoDB) | ✅ driver oficial via reflexão compatível | y | — | — | KofOrmE2ETest (E2E, skip condicional) | future/DATABASE_VISION.md |
| mapping | ✅ entity → linha/documento por schema de compile-time | y | – | y | JsonE2ETest, KofOrmE2ETest | — |
| query DSL tipada (`User.query { where ... }`) | `PLANNED` (nível 3 da visão) | — | — | — | — | future/DATABASE_VISION.md |
| pagination | ✅ `orm.page(page, size[, where])` | y | – | – | KofOrmE2ETest | future/DATABASE_VISION.md |
| PostgreSQL / MySQL / SQLite / MongoDB / Redis | `PLANNED` (adapters) | — | — | — | — | — |
| transactions | `PLANNED` | — | — | — | — | — |
| optimistic/pessimistic locking | `PLANNED` | — | — | — | — | — |

## 3.4 Messaging

| Capacidade | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| event bus / pub-sub | ✅ `kof.mq` (publish/subscribe/unsubscribe + queue/push/pop) — JVM; MQ001 no Native/JS | KofMq | KofMqE2ETest (4) | — | — | concurrency |
| queues (`kof.concurrent.Queue`) | `PLANNED` | — | — | — | — | concurrency |
| Kafka / AMQP / Pulsar | `PLANNED` (adapters externos) | — | — | — | — | roadmap.md |
| retry / dead-letter / backpressure | `PLANNED` | — | — | — | — | — |
| consumer groups | `PLANNED` | — | — | — | — | — |

## 3.5 Security (kof.security)

| Capacidade | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| password hashing (PBKDF2 600k) | `DONE` | y | – SECN001 | y | KofSecurityTest | security.md |
| SHA-256 / SHA-512 / HMAC | `DONE` | y | y sha256/hmac; – sha512 SECN003 | y | KofSecurityTest | security.md |
| AES-GCM | `DONE` (JVM) | y | – SECN002 | – SECN002 | KofSecurityTest | security.md |
| SecureRandom | `DONE` | y | y (getrandom) | y | KofSecurityTest | security.md |
| JWT (HS256, exp/iss/aud) | `DONE` (JVM/JS) | y | – SECN004 (compile-time) | y | KofSecurityTest | security.md |
| secrets (`secrets.get`, env) | `DONE` | y | y | y | KofSecurityTest | security.md |
| constant-time comparison | `DONE` | y | y | y | KofSecurityTest | security.md |
| redaction | `DONE` | y | y | y | KofSecurityTest | security.md |
| CSRF | `DONE` (JVM) | y | – | – | — | security.md |
| CORS | `DONE` (JVM) | y | – | – | — | security.md |
| security headers (CSP/HSTS/nosniff/Frame/Referrer) | `DONE` (JVM) | y | – | – | — | security.md |
| auth web (Bearer JWT + roles/permissions) | `DONE` (JVM) | y | – | – | — | security.md |
| RBAC / ABAC | `PARTIAL` (auth.hasRole/hasPermission JVM) | y | – | – | — | security.md |
| API keys | `PLANNED` | — | — | — | — | security.md |
| rate limiting | ✅ `security.rateLimit(key, limit, window)` — JVM/Native/JS | y | y | y | KofSecurityG9Test | security.md |
| sessions | ✅ `security.sessionCreate/sessionGet/sessionDestroy` — JVM/Native/JS | y | y | y | KofSecurityG9Test | security.md |
| API keys | ✅ `security.apiKeyGenerate/apiKeyValid` — JVM/Native/JS | y | y | y | KofSecurityG9Test | security.md |
| OAuth2 / OIDC (client, resource server, provider) | `PLANNED` | — | — | — | — | security.md |
| TLS / certificates / HTTPS | ✅ `web.listenSecure(port)` + `kof.http` HTTPS — JVM (self-signed via keytool) | y | — WEB002 | — WEB002 | KofWebTlsTest | http.md |
| secure cookies | `PLANNED` | — | — | — | — | — |
| token rotation / replay protection | `PLANNED` | — | — | — | — | — |
| audit logging | `PLANNED` | — | — | — | — | — |
| request signing | `PLANNED` | — | — | — | — | — |
| service-to-service auth | `PLANNED` | — | — | — | — | — |
| key management | `PLANNED` (hoje: env `KOF_*`) | y | y | y | — | security.md |

## 3.6 Identity

| Capacidade | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| OAuth2 client / resource server / authorization server | `PLANNED` | — | — | — | — | security.md |
| OIDC provider | `PLANNED` | — | — | — | — | — |
| session management | `PLANNED` | — | — | — | — | — |
| LDAP / Kerberos | `EXTERNAL` | — | — | — | — | — |
| machine-to-machine auth | `PLANNED` | — | — | — | — | — |

## 3.7 Integration / Resilience

| Capacidade | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| HTTP integrations | `PLANNED` (client) | — | — | — | — | — |
| file adapters | `DONE` (kof.io) | y | y | y | IoE2ETest | stdlib/IO.md |
| retry / timeout | `PLANNED` | — | — | — | — | — |
| circuit breaker / bulkhead | `PLANNED` | — | — | — | — | — |
| idempotency | `PLANNED` | — | — | — | — | — |

## 3.8 Batch

| Capacidade | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| jobs/steps/pipelines/checkpoints | `PLANNED` | — | — | — | — | — |
| retries / resumability / parallel | `PLANNED` | — | — | — | — | — |
| scheduling | `PLANNED` | — | — | — | — | — |

## 3.9 Observability

| Capacidade | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| metrics (runtime API) | ✅ `kof.observability.counter/increment/gauge` — JVM/Native/JS | y | y | y | KofObservabilityTest | observability.md |
| health checks / readiness / liveness | ✅ `kof.observability.health/readiness/liveness` — JVM/Native/JS | y | y | y | KofObservabilityTest | observability.md |
| tracing / OpenTelemetry | `PLANNED` | — | — | — | — | — |
| structured logging | `log.debug/info/warn/error` (níveis, stderr) | y | y (asm, UTC) | – LOG001 | KofLogE2ETest, NativeLogE2ETest | — |
| correlation IDs / request IDs | ✅ `kof.observability.requestId/correlationId` — JVM/Native/JS | y | y | y | KofObservabilityTest | observability.md |
| request IDs | ✅ `kof.observability.requestId` — JVM/Native/JS | y | y | y | KofObservabilityTest | observability.md |
| profiling / runtime diagnostics | `PARTIAL` (`kof profile`) | y | y | – | — | performance.md |
| resource monitoring | `PARTIAL` (memstats nativo, RSS no bench) | – | y | – | Bench | performance.md |

## 3.10 Configuration

| Capacidade | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| environment variables | `DONE` (`secrets.get`, `KOF_*`, `config.env`) | y | y | y | KofSecurityTest, KofConfigE2ETest | security.md |
| command-line arguments | `DONE` (`main(args)`) | y | y (vazio) | y (vazio) | UiE2ETest | language-state.md |
| config files / profiles / precedence | `DONE` (JVM): arquivo explícito > env > profile > default | y | – CONFIG001 | – CONFIG001 | KofConfigE2ETest | stdlib.md |
| typed configuration | `DONE` (`config.str/int/long/bool`) | y | – | – | KofConfigE2ETest | — |
| hot reload | `PLANNED`/`NA` | — | — | — | — | — |

## 3.11 Testing

| Capacidade | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| `assert(cond[, msg])` | `DONE` | y | y | — | AssertE2ETest | language-state.md |
| `kof test` (per-file, exit code) | `DONE` | y | y | — | — | roadmap.md |
| suíte estruturada `test "nome" { }` | `PLANNED` | — | — | — | — | roadmap.md |
| HTTP testing | `DONE` (E2E com sockets) | y | — | — | KofWebE2ETest | web |
| mocks / fixtures | `PLANNED` | — | — | — | — | — |
| property testing / stress | `PARTIAL` (benchmarks stress) | y | y | – | Bench | performance.md |
| test containers | `NA`/`EXTERNAL` | — | — | — | — | — |
| golden tests | `DONE` | y | y | — | tests/golden | — |

## 3.12 CLI / Shell

| Capacidade | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| `kof` CLI completo | `DONE` | y | y | y | — | tooling/ |
| command parsing (em Kof) | `PLANNED` (`kof.cli` como lib) | — | — | — | — | roadmap.md |
| interactive CLI / prompts / progress | `PLANNED` | — | — | — | — | — |

## 3.13 Modular Architecture

| Capacidade | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| módulos multi-arquivo | `PLANNED` | — | — | — | — | roadmap.md |
| módulos de domínio / boundaries | `PLANNED` | — | — | — | — | — |
| módulos como construção nativa (`service UserService { }`) | `PLANNED` | — | — | — | — | — |
| architecture tests | `PLANNED` | — | — | — | — | — |

## 3.14 AI (investigação)

| Capacidade | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| model clients / embeddings / RAG / tool calling | `PLANNED` (módulo externo ou stdlib futura — decisão pendente) | — | — | — | — | — |

## 3.15 Interoperabilidade

| Capacidade | Kof | JVM | Native | JS | Tests | Docs |
|-----------|-----|-----|--------|----|-------|------|
| chamar Java | `DONE` (interop direta) | y | – | – | CompilerDriverTest | architecture.md |
| Spring | `EXTERNAL` (`kof spring starter` planejado — start.spring.io) | — | — | — | — | plan-spring-independence.md |
| JS (Node/browser) | `PARTIAL` (GraalJS embarcado, `kof_platform`) | — | — | y | KofJsE2ETest | targets/KOFJS.md |
| libc | `NA` (native sem libc) | — | — | — | — | architecture.md |

---

# 4. GAPS CRÍTICOS (prioridade P0)

| # | Gap | Impacto | Local proposto |
|---|-----|---------|----------------|
| G1 | ~~**Database/SQL** inexistente~~ — ✅ **nível 0 implementado**: `kof.db` (JDBC JVM, SQLite nativo, MySQL WIP) + `kof.orm` (entity, CRUD, where, migrate, MongoDB) | apps reais com persistência no JVM/Native-SQLite | próximo: query DSL tipada, pools, kof.db fora do JVM |
| G2 | ~~**HTTP client** inexistente~~ — ✅ **implementado**: `kof.http` client (get/post/put/delete/patch/options/status/timeout, headers; HTTP002 no Native/JS) | integrações, testes, frontend | ✅ fechado — `KofHttpE2ETest` (3) |
| G3 | ~~Configuration~~ — ✅ `kof.config` implementado (arquivo > env > profile > default, typed `str/int/long/bool`); **CONFIG001 nativo fechado** (asm `/proc/self/environ`); JS reporta CONF001 | — | JS (P1) |
| G4 | ~~**Validation** inexistente~~ — ✅ **implementado**: `kof.validation` (13 predicados nos 3 targets) | — | `KofValidationTest` (3/3) |
| G5 | ~~**Observabilidade runtime parcial**~~ — ✅ **implementado**: `kof.observability` (health/readiness/liveness, counter/increment/gauge, requestId/correlationId — JVM/Native/JS; `KofObservabilityTest` 3/3) | — | `KofObservabilityTest` |
| G6 | ~~**kof.test estruturado** inexistente~~ — ✅ **implementado**: `test "nome" { }` nos 3 targets; runner sintetizado em compile-time; PASS/FAIL por nome + exit code (`StructuredTestE2ETest`) | testes como cidadãos de primeira classe | próximo: suites nomeadas por diretório, timeouts, fixtures |
| G7 | ~~**Diagnósticos de target incompletos no security/web**~~ — ✅ **fechado**: `jwt.*` com entrada explícita (SECN004 no Native); `csrf/cors/auth/headers` já cobertos; WEB001 emitido para web.app() e métodos de app fora do JVM | viola "nunca silencioso" | manter: toda função nova entra em `supportedOn` no mesmo PR |
| G8 | ~~**Scheduling** inexistente~~ — ✅ `kof.time.sleep` + `interval`/`cancel` (scheduler JVM; `KofTimeE2ETest` 4/4); Native/JS reportam TIME001 | jobs periódicos | próximos: scheduler nativo, cron (P1) |
| G9 | ~~**Rate limiting / sessions / API keys** inexistentes~~ — ✅ **implementado**: `security.rateLimit`/`sessionCreate`/`sessionGet`/`sessionDestroy`/`apiKeyGenerate`/`apiKeyValid` — JVM/Native/JS (`KofSecurityG9Test` 3/3) | — | `KofSecurityG9Test` |
| G10 | ~~kof.security no Native~~ — ✅ **fechado**: PBKDF2, SHA-512, JWT HS256 e AES-GCM em asm (SECN001-004) | — | manter os vetores de teste (FIPS 197, NIST SP 800-38D, RFC 7519) |
| G11 | ~~**Lambdas com captura**~~ — ✅ **captura implementada** (mutable via box sintético `BoxN` + captura por valor; `Lambda0`/`Box0` gerados); **Map/Set** e **await/join** seguem em aberto | expressividade | compilador (documentado em backend-parity.md) |
| G12 | ~~**TLS/HTTPS** no servidor web~~ — ✅ **implementado**: `web.listenSecure(port)` (JVM, `SSLServerSocket` + `keytool` self-signed, `SAN=IP:127.0.0.1,DNS:localhost`; `kof.http` trust-all) — Native/JS reportam `WEB002` | — | `KofWebTlsTest` (5) |

---

# 5. DEPENDÊNCIAS ENTRE MÓDULOS

```text
kof.config ──────────────► kof.database (DSN, credentials)
   │                        │
   ├──► kof.validation ────┤  (schema, payloads)
   │                        │
   ├──► kof.security ──────┘  (secrets, encrypt de campos)
   │
   ├──► kof.observability     (config de logging/metrics)
   │
   └──► kof.web               (profiles, secrets)

kof.http (client) ──► kof.web (server) ──► kof.security (auth web)
      │                    │
      │                    └──► kof.observability (request IDs, metrics)
      │
      └──► kof.test (E2E HTTP)

kof.database ──► kof.concurrent (pools, async) ──► kof.time (timeouts)

kof.security ──► kof.io (files/certs) ──► kof.json (JWT claims, config)
```

Regra: módulos de baixo nível (`kof.core`, `kof.io`, `kof.json`,
`kof.time`) nunca dependem de módulos de alto nível. `kof.security` é
infraestrutura crítica: nada depende dele para existir, mas tudo que é
exposto ao mundo depende dele para ser seguro.

---

# 6. ARQUITETURA PROPOSTA

```text
kof.core / kof.collections / kof.io / kof.time / kof.json
      │
      ├── kof.security          (crypto, secrets, auth, web security)
      ├── kof.config            (env + files + profiles, typed)
      ├── kof.concurrent        (spawn, queues, async, await)
      │
      ├── kof.http              (server + client, REST, middleware)
      ├── kof.validation
      ├── kof.database          (SQL-first, transactions, migrations)
      │
      ├── kof.observability     (logging, metrics, health, tracing)
      ├── kof.test              (suites estruturadas)
      ├── kof.messaging         (event bus, queues, adapters)
      └── kof.cli               (arg parsing em Kof)
```

Princípios mantidos:

1. **Intenção → Kof → stdlib → runtime/backend → plataforma** — nunca
   "Java API disfarçada", nunca "framework + annotations + reflection +
   container" quando o compilador resolve.
2. **Sem ceremony**: sem `@Service/@Repository/@Autowired` como paradigma;
   construções nativas (`service UserService { }` planejado).
3. **Secure by default**: TLS quando aplicável, cookies seguros,
   constant-time, redaction, erros sem vazamento, timeouts, limites.
4. **Multiplatform honesta**: JVM/Native/JS; quando uma capacidade não
   existe num target: diagnóstico claro (SECN00x/CONC001/JSN00x), nunca
   divergência silenciosa.
5. **Performance**: Kof → IR → código direto; sem reflexão/indireção
   desnecessária em runtime.
6. **`new` não é obrigatório**: `User(...)` é a forma idiomática (já
   vigente nas guidelines — `User("Mel")`); docs/learn/exemplos preferem
   a forma sem `new`.
7. **Spring = interoperabilidade opcional**: `kof spring starter`
   (planejado) consulta start.spring.io e gera projeto compatível — a
   stdlib não copia o modelo do Spring.

---

# 7. PRIORIDADE

## P0 — plataforma base (agora)

1. ~~G7~~ — ✅ diagnóstico de target completo no security/web.
2. ~~G6~~ — ✅ `kof.test` estruturado (`test "nome" { }` nos 3 targets,
   runner sintetizado em compile-time, `process.exit(code)`).
3. ~~G3~~ — ✅ `kof.config` (JVM + **Native**); JS reporta CONF001 (P1).
4. ~~G2~~ — ✅ `kof.http` client.
5. ~~G1~~ — ✅ `kof.db` + `kof.orm` nível 0 completo (JDBC idiomático, SQLite
   nativo, transactions, entity, migrations, **where com operadores**,
   **saveAll batch**, **page/count/deleteAll**, **MariaDB/PostgreSQL reais**,
   MongoDB); próximo: query DSL tipada, pools, portabilidade Native/JS.
6. ~~G4~~ — ✅ `kof.validation` (13 predicados nos 3 targets; `KofValidationTest` 3/3).
7. ~~G5~~ — ✅ `kof.observability` (health/readiness/liveness, counter/increment/gauge, requestId/correlationId — JVM/Native/JS; `KofObservabilityTest` 3/3).
8. ~~G8~~ — ✅ `kof.time.sleep` + `interval`/`cancel` (JVM); Native/JS TIME001.
9. G10 — security no Native (jwt, passwords, sha512, aesgcm) + config/log.
10. ~~G9~~ — ✅ rate limiting, sessions, API keys (`security.rateLimit`, `sessionCreate`/`sessionGet`/`sessionDestroy`, `apiKeyGenerate`/`apiKeyValid` — JVM/Native/JS; `KofSecurityG9Test` 3/3).
11. ~~G12~~ — ✅ TLS/HTTPS (`web.listenSecure(port)` — JVM, `SSLServerSocket` + self-signed; `kof.http` HTTPS trust-all; `KofWebTlsTest` 5/5; Native/JS `WEB002`).

## P1

messaging (`kof.concurrent.Queue`, event bus, adapters Kafka/AMQP),
caching, resilience (retry/timeout/circuit breaker), WebSocket/SSE,
GraphQL/gRPC (interop), HTTP/2.

## P2

batch, LDAP, OAuth2/OIDC completo, sessions avançadas, OpenAPI,
mail/SMTP, CLI argument parsing em Kof, modular architecture
(`service UserService { }`, módulos multi-arquivo, boundaries).

## P3

AI (model clients, embeddings, RAG, tool calling — decidir stdlib vs
módulo externo), cloud integrations, provider adapters.

---

# 8. ESTRATÉGIA DE IMPLEMENTAÇÃO

1. **Convergir, não duplicar**: toda capacidade nova passa pelo fluxo
   `SEARCH → EXISTE? → AUDIT/TEST → GAP → DESIGN → IMPLEMENT → TEST →
   DOCUMENT` (§17 do enunciado). Nunca criar `KofSecurity2`/`KofWeb2`.
2. **Atingir P0 por camadas** (cada camada entrega valor sozinha):
   a. diagnóstico de target (G7) + suíte estruturada de testes (G6);
   b. config (G3) + http client (G2) — habilitam testes e integrações;
   c. database (G1) — o maior motor de aplicações reais;
   d. validation (G4) + observability (G5);
   e. web security (G9, G12) — fechar o ciclo "produção".
3. **Cada módulo só é "DONE" com**: API idiomática + type safety +
   targets aplicáveis + testes unit/E2E + stress + benchmark + security
   review + docs + learn + training + exemplo real (Definition of Done).
4. **JVM primeiro** para módulos com backend pesado (database, TLS);
   Native e JS seguem com as primitivas já existentes (asm, kof_platform);
   gaps com diagnóstico, nunca stubs silenciosos.
5. **Documentação contínua**: atualizar `docs/stdlib.md` e este documento
   a cada módulo entregue; criar `docs/database.md`, `docs/messaging.md`,
   `docs/observability.md`, `docs/configuration.md`, `docs/testing.md`,
   `docs/platform.md` conforme cada módulo ganha corpo.

---

# 9. DECISÕES REGISTRADAS

- **Sem DI/container**: resolução direta e construções nativas
  (`service`/`component` planejados) — ver `docs/philosophy.md`,
  `docs/security.md` §2.
- **Database SQL-first**: `db.query` + prepared statements como base;
  ORM opcional, nunca obrigatório — `docs/future/DATABASE_VISION.md`.
- **JWT HS256 fixo** no v1 (sem confusão de algoritmo); rotação e
  assinatura flexível ficam para a camada P2 de identity.
- **`new` aceito por retrocompatibilidade**, não recomendado.
- **AI**: decisão stdlib vs módulo externo adiada até a P3.
- **Observabilidade**: tooling (`kof bench/profile`) + `kof.log`
  (níveis, stderr — JVM); health/metrics/request IDs entram em P0-G5.
- **Configuration**: `kof.config` (JVM) segue a precedência
  arquivo explícito > env > profile > default; typed via
  `config.str/int/long/bool`; Native/JS reportam CONFIG001.