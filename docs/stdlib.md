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
| `kof.security` | ✅ (v1) | passwords, crypto, jwt, secrets, auth, security — ver `docs/security.md` |
| `kof.concurrent` | ✅ | `spawn` (virtual threads) — JVM; Native CONC001 |
| `kof.test` | ✅ | `kof test`, `assert` |
| `kof.cli` | ✅ | `kof build/run/serve/check/test/bench/profile/inspect/lsp/install` |
| `kof.metrics` | 🟡 | `kof bench`/`kof profile` (tooling) |
| `kof.rest` | ⏳ | planejado |
| `kof.database` | ⏳ | planejado (SQL-first, sem ORM pesado) |
| `kof.messaging` | ⏳ | planejado |
| `kof.validation` | ⏳ | planejado |
| `kof.logging` | ⏳ | planejado (println hoje) |
| `kof.observability` | ⏳ | planejado |
| `kof.process` | ⏳ | planejado |
| `kof.config` | ⏳ | planejado (env hoje via `secrets.get`) |

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

# 5. PRÓXIMAS ETAPAS

1. `kof.config` (env + arquivo + profiles).
2. `kof.database` (JDBC idiomático + migrations).
3. `kof.validation`.
4. `kof.logging` + `kof.observability` (health checks, metrics runtime).
5. `kof.messaging`.
6. JWT/passwords no Native (HMAC asm já existe; falta o binding).
7. SHA-512 no Native.
8. Auditoria contínua: docs/security.md §2 (matriz do ecossistema Spring).