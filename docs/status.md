# Status do Projeto Kof

**Última atualização:** 24 de agosto de 2026
**Versão:** 0.0.14-alpha

---

## Build

```
mvn clean package    → PASSA
mvn test             → ~580 testes (verificar numero exato no CI)
kof build            → PASS (--target jvm|native|js) [--release]
kof run              → PASS (jvm|native|js) [--release]
kof serve            → PASS (web.app() nativo + API legada handle())
kof check            → PASS
kof test             → PASS (suíte estruturada `test "nome" { }` nos 3 targets)
kof bench            → PASS (harness: compile, run, validate, métricas, baseline)
kof debug            → PASS (DAP MVP no target JVM)
kof info             → PASS
kof lsp              → PASS (diagnostics reais do frontend)
kof install          → PASS
tests/run-golden.sh  → 16/16 (8 casos × jvm+native)
tests/run-integration.sh → 9/9 (CLI + serve + kof test)
scripts/package.sh   → PASS (layout dist + tar.gz + SHA256SUMS)
```

---

## Performance & Benchmarks (docs/performance.md)

- **Otimizador de IR** (`Optimizer.java`, sempre ativo): constant folding,
  branch simplification (condições constantes → jumps diretos), dead stack
  effects (push+pop, dup+pop, load/store round trips), unreachable code
  elimination (CFG reachability com regiões try/catch preservadas),
  jump-to-next elimination, identidades aritméticas (x+0, x*1, x/1, ...).
  Debug positions preservadas (ops sobreviventes).
- **Perfis debug/release**: `kof build|run --release` remove metadata de
  debug (SourceFile/LineNumberTable no JVM, source map no JS).
- **`kof bench`**: `kof bench [paths...] [--target jvm|native|js]
  [--iterations N] [--quick] [--baseline <file>] [--update-baseline <file>]
  [--threshold <ratio>] [--json] [--fail-on-regression]`.
  Compila → executa → valida stdout contra `expected.txt` → mede tempo
  (mediana) e RSS (Linux, `/usr/bin/time -v`) → compara baseline →
  sinaliza `PERFORMANCE REGRESSION`.
- **Estrutura `benchmarks/`**: 37 benchmarks em 17 categorias (micro,
  algorithms, collections, strings, math, objects, inheritance, interfaces,
  generics, json, io, concurrency, startup, memory, stress, applications).
- **Baselines**: `benchmarks/baselines/<target>-<version>.json` (33 jvm,
  29 native, 32 js).
- **CI**: `.github/workflows/benchmark.yml` — roda jvm+native com
  `--fail-on-regression --threshold 1.20`.
- `scripts/run-benchmarks.sh` — suite completa + atualização de baselines.
- Regra de features novas: docs/performance.md §40-§41 (Definition of Done
  inclui benchmark, stress, memory, resource e debug metadata).

### Correções de backend descobertas pelos benchmarks

| Bug | Correção |
|-----|----------|
| Chamadas via interface com retorno primitivo geravam descritor `Object` (`()Ljava/lang/Object` + `iadd` = bytecode inválido) | `analyzeInterface` agora define os symbols em `members()` (eram invisíveis ao `resolveInHierarchy`) |
| `l.get(i)`/`l.remove(i)`/`l.size`/`l.contains(...)` como statement não emitiam `KofPop` → stack desbalanceado em merge points (Frame.merge crash / VerifyError) | `hasReturnValue` cobre métodos de List que deixam valor |
| `if (long > long)` / `if (float > f)` / `if (double > d)` geravam `IF_ICMP` sobre não-ints (stack underflow) | `KofConditionalJump` ganhou `operandType`; JVM emite `LCMP`/`FCMPL`/`DCMPL` + jumps de 1 operando |
| `while (longExpr < intLiteral)` gerava `LCMP` sobre [long, int] (stack underflow) | shortcut de comparação faz widening dos operandos (`emitComparisonShortcut`) |
| JS: call com efeito descartada em statement com Pop (ex.: `users.remove(0)` silenciosamente não executava) | handler de `KofPop` no JsBackend preserva `JsCall`/`JsSequence` como statement |

---

## Segurança (kof.security, docs/security.md)

- **`kof.security` implementado** (v1): `passwords`, `crypto`, `jwt`,
  `secrets`, `security`, `auth` — secure by default, gaps de target com
  diagnóstico claro em compile-time (SECN001/002/003).
- **JVM**: PBKDF2-HMAC-SHA256 (600k iterações), SHA-256/512, HMAC, AES-GCM,
  SecureRandom, JWT HS256 (sig/exp/iss/aud), env secrets, constant-time,
  redaction, contexto web `auth.*` (Bearer JWT).
- **Native**: SHA-256 e HMAC em assembly puro (x86-64, sem libc, FIPS 180-4 /
  RFC 2104 — valores idênticos ao JVM), random via `getrandom`, secrets via
  `/proc/self/environ`, constant-time, redaction.
- **JS**: SHA-256/512 e HMAC em JS puro, PBKDF2 com delegação ao platform
  (runner embarcado), JWT, secrets, constant-time.
- **Testes**: `KofSecurityTest` — 22 testes (unit + E2E nos 3 targets +
  adversariais: tamper, expiração, confusão de algoritmo, token malformado,
  chave errada, issuer/audience).
- **Benchmarks**: `benchmarks/security/` (password-hash, jwt, hash-speed,
  aes-gcm).
- **Docs**: `docs/security.md` (auditoria + matriz + arquitetura + estado),
  `docs/stdlib.md`, `learn/36-security.md`, `training/language/security.md`,
  `training/examples/security.kf`.

---

## Database + ORM (kof.db / kof.orm)

### kof.db — persistência como parte da linguagem

```kof
main() {
    var db = db.connect("jdbc:h2:mem:app;DB_CLOSE_DELAY=-1")
    db.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, name VARCHAR)")
    var rows = db.query("SELECT * FROM users WHERE id = ?", 1)
    transaction {
        db.execute("INSERT INTO users VALUES (1, 'Mel')")
        db.execute("UPDATE users SET name = 'Melissa' WHERE id = 1")
    }
    db.close(db)
}
```

- **JVM**: JDBC idiomático (`db.connect`, `db.execute`, `db.query`,
  `query<T>` tipado por record/entity, credentials opcionais,
  `transaction {}` com commit/rollback real).
- **Native**: SQLite via link direto da `.so` (sem driver JDBC) — roundtrip
  E2E real (`nativeSqliteRoundtrip`). MySQL/MariaDB via wire protocol sobre
  sockets nativos em progresso (auth scramble SHA-1 implementado).
- **JS**: reporta `DB001` (gap documentado).
- DSNs: `jdbc:*` (JVM), `sqlite:` (JVM/Native), `mongodb://` (ORM).

### kof.orm — o ORM da própria linguagem

```kof
entity User {
    id: Long generated
    name: String
    email: String unique
    age: Int
}

main() {
    var db = db.connect("jdbc:h2:mem:app;DB_CLOSE_DELAY=-1")
    orm.create<User>(db)                                  // DDL do schema
    orm.save(db, User(0, "Mel", "mel@kof.dev", 30))       // insert/update
    var u = orm.find<User>(db, 1)                         // PK
    var adultos = orm.where<User>(db, "age", 30)          // query por campo
    var veteranos = orm.where<User>(db, "age", ">", 30)   // operadores: > < >= <= != LIKE
    orm.saveAll<User>(db, l)                              // batch (upsert por PK)
    var pg = orm.page<User>(db, 20, 40)                   // paginação (limit, offset)
    println(orm.count<User>(db))
    orm.delete<User>(db, 1)
    orm.migrate(db, "add-phone", "ALTER TABLE user ADD phone VARCHAR")
}
```

- Schema declarado na linguagem (`entity`) — o compilador conhece campos,
  tipos e constraints em compile-time (nunca reflection para descobrir
  schema); `generated`, `unique`, PK não-numérica.
- Backends SQL: H2/SQLite/MySQL/MariaDB/PostgreSQL via JDBC (JVM).
- CRUD completo + consultas: `saveAll` (batch), `where` com operadores
  (`"="`, `">"`, `"<"`, `">="`, `"<="`, `"!="`...), `count` com filtro,
  `page` (limit/offset) e `deleteAll`.
- **MongoDB**: `save/find/all/where/delete/count` sobre o driver oficial via
  reflexão compatível (`Bson`/`Class`, sem ClientSession); teste E2E com
  container real (skip condicional; serviço Mongo no CI).
- Migrations versionadas: tabela `kof_migrations`, cada migração roda uma vez.
- Native/JS reportam `ORM001`.
- Testes: `KofDbE2ETest` (8), `KofOrmE2ETest` (12+; MariaDB/PostgreSQL/MongoDB
  com skip condicional quando o container não está no ar).
- Docs: `docs/future/DATABASE_VISION.md` (níveis 0-2 e 4 implementados;
  nível 3 = query DSL tipada é o próximo).

---

## Infraestrutura 0.0.5 (distribuição)

- `VERSION` como fonte única; `<revision>` no Maven; `KofVersion` com
  `version.properties`; `scripts/bump-version.sh`.
- CLI: `build, run, serve, check, test, info, lsp, install, version`.
- `kof lsp` — Language Server via stdio (initialize, didOpen/didChange/
  didClose → publishDiagnostics do frontend real).
- Launchers `bin/kof` (Unix) e `bin/kof.bat` (Windows) com JDK embutido
  (Temurin 21, Tooling API Level 21).
- `scripts/package.sh` — layout oficial de distribuição, `--jdk` para JDK
  embutido, SHA256SUMS.
- GitHub Actions: `ci.yml` (PR — testes, golden, integração, multiplatform)
  e `release.yml` (main → testes → bump → package 3 plataformas → changelog
  → GitHub Release).
- Editor support: `editor/kof.tmLanguage.json` (grammar TextMate).

---

## Targets

| Target | Backend | Execução | Status |
|--------|---------|----------|--------|
| `jvm` | `JvmBackend` (ASM) | bytecode V21, exception table, virtual threads | estável |
| `native` | `NativeBackend` | ELF x86-64, syscalls, sem libc obrigatória | estável |
| `js` | `JsBackend` + `KofJsRunner` | ES Modules (ECMAScript 2022+) via GraalJS embutido | alpha |

O mesmo frontend e a mesma Kof IR alimentam os três backends.

---

## Estado da Linguagem

### Sintaxe de funções (sem `fun`)

```kof
main() { ... }                       // entry point, void implícito
String saudacao() { ... }            // retorno antes do nome
despedida(): String { ... }          // retorno após os parâmetros
void fazIsso() { ... }               // void explícito
Bool positivo(Int x) = x > 0         // expression body
```

### Features implementadas

| Feature | JVM | Native | KofJS |
|---------|-----|--------|-------|
| println / print | ✅ | ✅ | ✅ |
| variáveis, aritmética, bitwise, hex literals | ✅ | ✅ | ✅ |
| if/else, if-expr | ✅ | ✅ | ✅ |
| while, for, do-while, for-in, break/continue | ✅ | ✅ | ✅ |
| switch | ✅ | ✅ | ✅ |
| funções (todas as formas) | ✅ | ✅ | ✅ |
| classes, campos, métodos | ✅ | ✅ | ✅ |
| `constructor(...)` e primary `class X(...)` | ✅ | ✅ | ✅ |
| records (toString/equals/hashCode) | ✅ | ✅ | ✅ |
| herança, `super`, override, virtual dispatch | ✅ | ✅* | ✅ | Native: `super.metodo()` = SUP001 |
| interfaces | ✅ | ✅ | ✅ |
| generics por erasure | ✅ | ✅ | ✅ |
| lambdas `(x: Int) -> expr` + capturas | ✅ | ✅ | ✅ |
| exceptions reais (try/catch/finally + unwinding) | ✅ | ✅ | ✅ |
| `assert(cond[, msg])` | ✅ | ✅ | ✅ |
| `spawn` (concorrência, join implícito) | ✅ | CONC001 | — |
| strings (concat `+`, `==`, indexOf, trim, split...) | ✅ | ✅ | ✅ |
| arrays | ✅ | ✅ | ✅ |
| `List<T>`, `listOf` | ✅ | ✅ | ✅ |
| JSON encode/decode (objetos/records no JVM) | ✅ | ✅ | ✅ |
| JSON decode `List<User>` (objetos aninhados) | ✅ | — | ✅ |
| kof.io (File/Path/Directory, readFile, writeFile) | ✅ | ✅ | ✅ |
| kof.time (`now()`) | ✅ | ✅ | ✅ |
| kof.web (`web.app()`, rotas, middleware) | ✅ | — | — |
| kof.config (env, arquivos, profiles, typed) | ✅ | ✅ (asm próprio) | CONF001 |
| kof.log (`log.info/warn/error/debug`) | ✅ | ✅ (asm; UTC, sem JSON) | LOG001 |
| kof.security (passwords, crypto, JWT, secrets) | ✅ | ✅ | ✅ |
| kof.db (JDBC, query<T>, transaction) + SQLite nativo | ✅ | ✅ (SQLite; MySQL WIP) | DB001 |
| kof.orm (entity, CRUD, where, migrate, MongoDB) | ✅ | ORM001 | ORM001 |
| String.toInt/toLong/toDouble/toFloat | ✅ | ✅ | ✅ |
| kof.ui (Color, Palette, Theme, Window) | ✅ | ✅ (JS render) | ✅ |
| default parameters em funções | ✅ | ✅ | ✅ |
| `readLine()` | ✅ | ✅ | ✅ |

### Concorrência (`spawn`)

```kof
spawn processarFila()
spawn {
    println("background")
}
```

- JVM: virtual threads; o programa espera as tarefas (join implícito).
- Native: `CONC001` (gap documentado — planned).
- Zero API de plataforma exposta (Thread/Runnable são internos do runtime).
- Ver: `docs/concurrency.md`.

### HTTP (`kof serve`)

API legada (handler top-level):

```kof
handle(String method, String path, String body, String query, String headers): String {
    if (path == "/hello") {
        return "{\"msg\": \"hi\"}"
    }
    return null   // 404
}
```

Stack web nativa (Fase 1 — independência do Spring):

```kof
record User(String name, Int age)

main() {
    var app = web.app()
    app.use {
        if (header("x-auth") == "secret") {
            return null
        }
        return "{\"error\": \"unauthorized\"}"
    }
    app.get("/hello") {
        return "Hello from Kof"
    }
    app.get("/users/:id") {
        return "user " + param("id") + " q=" + query("name")
    }
    app.post("/user") {
        var user = json.decode<User>(body())
        return json.encode(user)
    }
    app.listen(8080)
}
```

- `web.app()` + rotas com lambda trailing; path params (`:id`), query,
  headers, body, `method()`, `path()`; middleware `app.use { ... }`.
- Engine HTTP gerado dentro do runtime do programa (sem servlet container,
  sem Spring); cada conexão em virtual thread.
- `kof serve <file.kf>` detecta `main()` e executa apps `web.app()`;
  a API legada `handle(...)` continua funcionando.
- Ver: `docs/stdlib-web.md` e `KofWebE2ETest` (9 testes E2E com sockets reais).

### Configuração nativa (`kof.config`)

```kof
main() {
    var port = config.int("server.port", 8080)
    var url = config.str("database.url", "jdbc:h2:mem")
    var debug = config.bool("app.debug", false)
    var home = config.env("HOME")
    if (config.has("database.url")) { ... }
}
```

- Precedência: arquivo explícito (`KOF_CONFIG`) > env `KOF_<KEY>` >
  profile (`kof.<KOF_PROFILE>.config`) > arquivo padrão (`kof.config`).
- Tipagem em compile-time; valores ausentes/inválidos → default.
- Native: implementação asm própria completa — precedência total
  (KOF_CONFIG > env KOF_<KEY> > perfil > kof.config), typed com default
  em valor inválido, trim e comentários (`NativeConfigE2ETest`, 8 testes).
  JS reporta `CONF001`. Docs: `docs/stdlib-config.md`
  (`KofConfigE2ETest`, 8 E2E).

### Logging nativo (`kof.log`)

```kof
log.debug("detail")
log.info("request started")
log.warn("slow response")
log.error("failed: " + message)
```

- Formato `timestamp LEVEL mensagem`; info/debug → stdout, warn/error →
  stderr; nível via `KOF_LOG_LEVEL` (debug < info < warn < error < off).
- Funciona dentro de handlers web. **Native**: implementação asm própria
  (data civil Hinnant, env scan próprio) — timestamp UTC e `KOF_LOG_JSON`
  sem efeito por enquanto; JS reporta `LOG001`. Docs: `docs/stdlib-logging.md`
  (`KofLogE2ETest` 10 JVM + `NativeLogE2ETest` 7).

### Testes da linguagem (G6 — suíte estruturada)

```kof
test "soma simples" {
    assert(2 + 2 == 4)
}

test "string igual" {
    assert("kof" == "kof", "strings iguais")
}

main() { /* ignorado pelo kof test */ }
```

- `test "nome" { }` vira função em compile-time (desugar → `kof_test_N`);
  o runner é sintetizado pelo compilador — zero reflection.
- `kof test <file.kf|dir> [--target jvm|native|js]` reporta
  `PASS nome` / `FAIL nome: mensagem` + resumo; exit code ≠ 0 se houver
  falha. Cada teste roda isolado (try/catch por teste).
- Arquivos sem blocos `test` mantêm o contrato antigo (PASS/FAIL por
  exit code do programa inteiro).
- **process.exit(code)**: primitivo novo nos 3 targets (JVM System.exit,
  Native syscall, JS sentinel no KofJsRunner) — sem stack trace.
- G7 fechado: `jwt.*` tem entrada explícita na matriz de targets — Native
  reporta `SECN004` em compile-time (antes: erro de link silencioso).
- Ver: `learn/23-testing.md`, `StructuredTestE2ETest` (11 testes).

---

## Testes (527/528 — 1 skip condicional)

| Suíte | Quantidade | Cobertura |
|-------|-----------|-----------|
| CompilerDriverTest | 190 | compilação, semântica, fases, isolamento |
| OptimizerTest | 21 | passes de otimização da IR |
| NativeE2ETest | 50 | execução real de binários nativos |
| KofJsE2ETest | 35 | execução real JS (GraalJS) |
| JvmE2ETest | 29 | execução real de bytecode JVM |
| IoE2ETest | 15 | kof.io multiplatform |
| UiE2ETest | 14 | kof.ui: widgets, estilo, bindings, múltiplas janelas |
| JsonE2ETest | 14 | JSON JVM + Native |
| CoreRegressionE2ETest | 14 | regressões de feedback de uso real (BOM, toInt, ARITH001...) |
| BackendParityTest | 10 | paridade JVM/Native/JS |
| KofOrmE2ETest | 10 | kof.orm: entity, CRUD, where, migrate, unique, PK não-numérica, MongoDB E2E, ORM001/ORM002 |
| KofLogE2ETest | 10 | kof.log JVM: níveis, stderr, off, JSON estruturado, correlation ID |
| NativeLogE2ETest | 7 | kof.log Native (asm): níveis, stderr, formato civil, off |
| ExceptionsE2ETest | 9 | try/catch/finally JVM + Native |
| KofWebE2ETest | 9 | stack web nativa (web.app, rotas, JSON, middleware) |
| KofDbE2ETest | 8 | kof.db: JDBC, query<T>, transaction, rollback, SQLite nativo, DB001 |
| KofHttpServerTest | 8 | serve engine (sockets reais) |
| KofConfigE2ETest | 8 | kof.config: env, arquivo, profiles, precedência, typed, CONF001 |
| KofSecurityTest | 22 | kof.security: senhas, crypto, JWT, secrets, adversariais (JVM/Native/JS) |
| JsonCompleteE2ETest | 7 | JSON completo: Float/Double, arrays decode (JVM) |
| IdiomaticE2ETest | 7 | idiomas consolidados (chaining, primary ctor) |
| IdiomaticCoreE2ETest | 6 | field initializers, \\uXXXX, listOf<T>() |
| AssertE2ETest | 5 | assert JVM + Native |
| StructuredTestE2ETest | 11 | test "nome" {} nos 3 targets + process.exit + descoberta |
| FunctionSyntaxTest | 4 | formas de declaração de função |
| LambdaE2ETest | 4 | lambdas + if-expr |
| StdlibE2ETest | 4 | now/readFile/writeFile |
| WindowE2ETest | 3 | Window: size, close-to-exit |
| SpawnE2ETest | 3 | spawn (JVM) + CONC001 |
| TetrisEasterEggTest | 3 | registro easter egg oculto |
| IRStatisticsTest | 2 | observer de IR + estatísticas de otimização |
| DebugInfoE2ETest | 2 | SourceFile + LineNumberTable (JVM) |
| NativeDebugTest* | 5 | harnesses de debug |
| **Total** | **581 declarados** (+1 skip condicional; conferir total no CI a cada release) | |

---

## Consolidação idiomática (guidelines 0.0.5)

Princípio: `intenção → Kof → compiler → backend` — nunca detalhes da
plataforma vazando para a linguagem.

| Guideline | Estado |
|-----------|--------|
| `User(...)` sem `new` (retrocompatível) | ✅ |
| Primary constructor `class User(String name)` | ✅ |
| `this` não obrigatório | ✅ |
| Field initializers aplicados no construtor | ✅ (0.0.5) |
| Resolução de métodos independente da ordem textual | ✅ |
| Escapes `\n` `\t` `\r` `\uXXXX` | ✅ (0.0.5) |
| `listOf<T>()` vazio preserva o tipo | ✅ (0.0.5) |
| `List<User>` + for-in tipado | ✅ |
| `++`/`--` em campos | ✅ |
| `return` nu em void | ✅ |
| lambdas com capturas | ✅ (sem testes dedicados ainda) |
| args CLI (`main(args)`) | ✅ |
| default parameters | ✅ |
| módulos multi-arquivo | planejado |
| `Process` API | ✅ (`kof.process` + `kof_process_run`) |

Ver as guidelines completas no todo da sessão.

---

## Kof Debugger (em progresso)

Princípio: o programador depura **código Kof**, nunca o artefato do backend.

| Fase | Estado |
|------|--------|
| 1 — DebugInfo na IR (source location por op) | ✅ |
| 2 — JVM: SourceFile + LineNumberTable + LocalVariableTable | ✅ |
| 3 — `kof-debug` MVP (DAP over stdio + JDWP cru): launch, breakpoints por linha Kof, `stopped`, stack trace com funções/linhas Kof, continue, disconnect | ✅ |
| 4 — Kof Editor (breakpoints, toolbar, variables) | planejado |
| 5 — Native (DWARF) | planejado |
| 6 — JS (source maps) | planejado |
| 7 — Avançado: locals por frame, stepping, exception breakpoints, avaliação | planejado |

`kof debug app.kf` já abre uma sessão DAP funcional no target JVM:
a sessão compila com metadata de debug, lança o JVM com JDWP e responde a
`initialize` / `launch` / `setBreakpoints` / `configurationDone` /
`continue` / `threads` / `stackTrace` / `disconnect` — o breakpoint
para na linha Kof e o call stack mostra funções e linhas Kof.

Docs: `debugger-architecture.md`, `debugging.md`, `debug-adapter.md`,
`debugging-jvm.md`, `debugging-native.md`, `debugging-js.md`.

---

## Bugs Restantes (reais)

1. GC no Native (memória devolvida ao SO no exit)
2. `spawn` no Native: CONC001 (gap documentado)
3. JSON de objetos/records no Native: JSN002 (gap documentado)
4. JSON Float/Double: JSN001 (gap documentado)
5. JSON decode de arrays (`Int[]`): JSN003 (gap documentado; `List<T>` e
   `List<User>` já funcionam)
6. Lambdas sem captura (planned)
7. Resultado de tarefa (`await`/join explícito): planned
8. `kof fmt`: planned
9. Map/Set, Option/null safety, pattern matching: planned
10. Web: status codes/headers customizados por handler (planned)
11. Web: target `js` reporta WEB001; target `native` sem servidor web
12. MySQL/MariaDB no Native: wire protocol em progresso (auth scramble SHA-1
    feito; falta handshake completo, query e prepared statements)
13. `kof_sec_secret_get` no Native: segfault quando a env var procurada
    EXISTE (retorna ok/hang quando ausente) — bug pré-existente da infra
    de secrets; o `kof.log` nativo usa scan próprio de environ e não é
    afetado. Investigar e corrigir com teste E2E dedicado.
14. Ponto flutuante no Native: sem aritmética SSE real (bits vivem como
    inteiros na pilha); operações FP viram FLT001/JSN001 em compile-time.
    Fechar exige backend SSE + formatação double→string.

---

## Próximos Passos

- ~~Database + transactions~~ — ✅ kof.db nível 0 (JDBC JVM + SQLite nativo);
  falta query DSL tipada (nível 3 da DATABASE_VISION) e MySQL nativo completo
- Validation + scheduling + events nativos — Fase 8
- DI nativa + lifecycle (`application { onStart/onShutdown }`) — Fase 9
- Aplicação web completa em Kof sem Spring (teste obrigatório) — Fase 12
- Spring Starter (`kof spring starter`) — Fase 13
- Structured logging (JSON), correlation ID; métricas/health/tracing — Fase 4
- Resultado observável de tarefas (`await`), filas (`kof.concurrent.Queue`)
- Scheduler nativo para `spawn`
- `kof fmt`, hover/completion no LSP
- Roadmap: `docs/roadmap.md`; plano de execução: `docs/plan-spring-independence.md`;
  **plano de plataforma completa: `docs/plan-platform-completion.md`**

---

## Roadmap de Longo Prazo

- `docs/roadmap.md` com a visão completa.
- Próximo grande marco: **aplicação web completa em Kof** (frontend via KofJS,
  backend via kof serve, banco, auth, JSON — lógica de negócio em poucos
  arquivos).