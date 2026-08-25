# Kof Standard Library — Arquitetura

> A Standard Library do Kof é a plataforma: HTTP, REST, auth, autorização,
> validação, serialização, database, messaging, observabilidade e testing
> devem ser construídos com a própria plataforma — sem dependência
> arquitetural de frameworks externos.

---

# 1. PRINCÍPIO

```text
intenção → Kof → resultado
```

A API pública é simples; a implementação interna é eficiente. O compilador
conhece cada chamada de stdlib em compile-time e a mapeia para o runtime
mais direto de cada target (JVM bytecode, assembly nativo, JS idiomático).

---

# 2. MECANISMO

Cada módulo da stdlib é uma **tabela de dispatch compile-time**:

```text
KofSecurity.java / KofWeb.java / KofIo.java / KofUi.java
        │
        ├── SemanticAnalyzer   → tipos das chamadas (inferência/checagem)
        ├── CompilerDriver     → lowering para KofCall(kof_*)
        ├── JvmRuntime         → KofRuntime gerado (javax.crypto, java.nio...)
        ├── NativeRuntime      → assembly x86-64 (syscalls, sem libc)
        └── JsBackend          → kof-runtime.mjs (JS puro + kof_platform)
```

Gaps de target produzem **diagnósticos claros em compile-time** (SECN00x,
CONC001, JSN00x) — nunca comportamento silenciosamente diferente.

---

# 3. MÓDULOS

| Módulo | Estado | Notas |
|--------|--------|-------|
| `kof.core` | ✅ | println, strings, arrays, aritmética |
| `kof.collections` | ✅ | `List<T>`, `listOf` (boxing de primitivos no JVM é gap conhecido) |
| `kof.io` | ✅ | `File/Path/Directory`, readFile/writeFile — JVM/Native/JS |
| `kof.time` | ✅ | `now()` |
| `kof.json` | ✅ | encode/decode; objetos só JVM (JSN002), Float/Double gap (JSN001) |
| `kof.http` | ✅ | `kof serve` (KofHttpServer, thread pool) — JVM |
| `kof.web` | ✅ | `web.app()`, rotas, middleware `app.use` — JVM |
| `kof.security` | ✅ (v1 + G9) | passwords, crypto, jwt, secrets, auth, security, rateLimit, sessions, apiKeys — ver `docs/security.md` |
| `kof.concurrent` | ✅ | `spawn` (virtual threads) — JVM; Native CONC001 |
| `kof.test` | ✅ | `kof test`, `assert` |
| `kof.cli` | ✅ | `kof build/run/serve/check/test/bench/profile/inspect/debug/info/lsp/install/version` (inclui o debugger DAP) |
| `kof.metrics` | 🟡 | `kof bench`/`kof profile` (tooling) |
| `kof.rest` | ⏳ | planejado |
| `kof.database` | ✅ | `kof.db` (JDBC idiomático + SQLite nativo) + `kof.orm` (entity, create/save/find/all/where/delete/count/migrate) — JVM; ver `future/DATABASE_VISION.md` |
| `kof.messaging` | ⏳ | planejado |
| `kof.validation` | ✅ | `validation.required/notBlank/minLength/maxLength/lengthBetween/isEmail/isUrl/matches/isInt/isLong/inRange/min/max` — JVM/Native/JS (`KofValidationTest` 3/3) |
| `kof.logging` | ✅ | `log.debug/info/warn/error`, níveis, off (JVM; LOG001 outros) — KofLogE2ETest (7) |
| `kof.observability` | ✅ | `observability.health/readiness/liveness`, `counter(name)`/`increment(name, delta)`/`gauge(name, value)`, `requestId()`/`correlationId()` — JVM/Native/JS (`KofObservabilityTest` 3/3) |
| `kof.process` | ⏳ | planejado |
| `kof.config` | ✅ | `config.get/env/has`, `config.str/int/long/bool(name, fallback)`; arquivo > env > profile > default (JVM; CONFIG001 outros) — KofConfigE2ETest (8) |

---

# 4. REGRAS DE DESIGN

1. **Intenção primeiro**: `passwords.verify(pw, hash)` — nunca primitivas
   soltas para a aplicação montar segurança.
2. **Secure by default**: escolhas seguras automáticas (PBKDF2 600k, HS256
   fixo, salt aleatório, comparação constant-time).
3. **Sem cerimônia**: sem injeção de container, sem annotations, sem
   configuração XML/yml obrigatória.
4. **Sem overhead escondido**: cada abstração precisa responder qual é seu
   custo em runtime (docs/performance.md §8).
5. **Diagnósticos claros**: gaps de target nunca silenciosos.
6. **Java/Spring continuam válidos** como interoperabilidade — nunca como
   dependência arquitetural.

---

# 5. AUDITORIA DO ECOSSISTEMA

A matriz completa de cobertura (inventário, gaps, dependências,
arquitetura, prioridade e estratégia) vive em **`docs/ecosystem-coverage.md`**
— resultado da auditoria da stdlib contra as capacidades de uma
plataforma moderna (checklist derivado do ecossistema Spring, usado como
matriz de capacidades, não como especificação de API).

Resumo executivo (0.0.5-alpha):

| Categoria | Estado |
|-----------|--------|
| core/collections/io/time/json | DONE (3 targets) |
| security (crypto, jwt, secrets, auth web) | DONE (JVM/Native/JS core; web auth JVM) |
| web server (`web.app()`) | DONE (JVM) |
| concurrency (`spawn`) | DONE (JVM) |
| test (`assert`, `kof test`) | PARTIAL (suíte estruturada planejada) |
| observability | DONE (kof.observability: health/metrics/request IDs — JVM/Native/JS) |
| messaging, scheduling (interval done), sessions, rate limiting, TLS | PLANNED (gaps P0-P2) |

# 6. PRÓXIMAS ETAPAS

1. G7 — diagnóstico de target completo no security/web (erros de link →
   SECN00x claros).
2. G6 — `kof.test` estruturado (`test "nome" { }` + suites).
3. ~~G3~~ — `kof.config` ✅ (JVM); estender a Native/JS.
4. G2 — `kof.http` client (get/post, headers, JSON, timeout).
5. G1 — `kof.database` (JDBC idiomático: connect/query/transaction,
   prepared statements, pools, migrations).
6. G4 — `kof.validation`.
7. ~~G5~~ — ✅ `kof.observability` (health/readiness/liveness, counter/increment/gauge, requestId/correlationId — JVM/Native/JS).
8. G8 — `kof.time.sleep` + scheduler básico.
9. G10 — security no Native (jwt, passwords, sha512, aesgcm).
10. ~~G9~~ — ✅ rate limiting, sessions, API keys (`security.rateLimit`, `sessionCreate`/`sessionGet`/`sessionDestroy`, `apiKeyGenerate`/`apiKeyValid` — JVM/Native/JS).
11. G12 — TLS/HTTPS no servidor web.

Prioridades e estratégia completas: `docs/ecosystem-coverage.md` §7-§8.