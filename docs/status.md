# Status do Projeto Kof

**Última atualização:** 31 de agosto de 2026
**Versão:** 0.2.6-beta

---

## Build

```
mvn clean package    → PASSA
mvn test             → 658 testes (650 kof-compiler +8 kof-script +5 kof-c-compiler, 0 falhas) + 1 skip condicional
kof build            → PASS (--target jvm|native|js|native.risc|native.arm) [--release]
kof run              → PASS (jvm|native|js|native.risc|native.arm) [--release]
kof serve            → PASS (web.app() nativo + API legada handle())
kof check            → PASS
kof test             → PASS (suíte estruturada `test "nome" { }` nos 3 targets)
kof bench            → PASS (harness: compile, run, validate, métricas, baseline)
kof debug            → PASS (DAP MVP no target JVM)
kof info             → PASS
kof lsp              → PASS (hover/completion + diagnostics reais)
kof install          → PASS
kof c                → PASS (KofCcompiler nativo-only C subset → ELF x86_64 via kof_c)
kof script           → PASS (KofScript top-level let → KofScriptGlobals, repl, --watch)
tests/run-golden.sh  → 16/16 (8 casos × jvm+native)
tests/run-integration.sh → 9/9 (CLI + serve + kof test)
scripts/package.sh   → PASS (layout dist + tar.gz/zip + SHA256SUMS + jars)
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

### Correções de backend descobertas pelos benchmarks e E2E

| Bug | Correção |
|-----|----------|
| Chamadas via interface com retorno primitivo geravam descritor `Object` (`()Ljava/lang/Object` + `iadd` = bytecode inválido) | `analyzeInterface` agora define os symbols em `members()` (eram invisíveis ao `resolveInHierarchy`) |
| `l.get(i)`/`l.remove(i)`/`l.size`/`l.contains(...)` como statement não emitiam `KofPop` → stack desbalanceado em merge points (Frame.merge crash / VerifyError) | `hasReturnValue` cobre métodos de List que deixam valor |
| `if (long > long)` / `if (float > f)` / `if (double > d)` geravam `IF_ICMP` sobre não-ints (stack underflow) | `KofConditionalJump` ganhou `operandType`; JVM emite `LCMP`/`FCMPL`/`DCMPL` + jumps de 1 operando |
| `while (longExpr < intLiteral)` gerava `LCMP` sobre [long, int] (stack underflow) | shortcut de comparação faz widening dos operandos (`emitComparisonShortcut`) |
| JS: call com efeito descartada em statement com Pop (ex.: `users.remove(0)` silenciosamente não executava) | handler de `KofPop` no JsBackend preserva `JsCall`/`JsSequence` como statement |
| `Box<Int>` / `Box<T>` com `b.get()` retornando `T` imprimia `T` como `String` no Native → segfault `0x7` (`NativeE2ETest.execGenericClass`) | `CompilerDriver.inferExprType` substitui `T` via `substituteTypeVariable` (receiver `Box<Int>`); `println` nativo `valueOf(Int)` → `kof_int_to_string` (`CompilerDriver.java:3972,2257`) |
| `record Ponto` `hashCode()` reportava `SEM025` falso-positivo | `SemanticAnalyzer.java:1033` ignora `isObjectMethod(hashCode/equals/toString)` |

---

## Segurança (kof.security, docs/security.md)

- **`kof.security` implementado** (v1): `passwords`, `crypto`, `jwt`,
  `secrets`, `security`, `auth` — secure by default, gaps de target com
  diagnóstico claro em compile-time (SECN001/002/003).
- **JVM**: PBKDF2-HMAC-SHA256 (600k iterações), SHA-256/512, HMAC, AES-GCM,
  SecureRandom, JWT HS256 (sig/exp/iss/aud), env secrets, constant-time,
  redaction, contexto web `auth.*` (Bearer JWT).
- **Native**: SHA-256, SHA-512 e HMAC em assembly puro (x86-64, sem libc,
  FIPS 180-4 / RFC 2104 — valores idênticos ao JVM), PBKDF2-HMAC-SHA256,
  AES-GCM (round-trip E2E `aesGcmNativeRoundTrip`), JWT HS256, random via
  `getrandom`, secrets via `/proc/self/environ`, constant-time, redaction.
- **JS**: SHA-256/512 e HMAC em JS puro, PBKDF2 com delegação ao platform
  (runner embarcado), JWT, secrets, constant-time; AES-GCM no JS = SECN002.
- **Testes**: `KofSecurityTest` — 25 testes (unit + E2E nos 3 targets +
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

## Infraestrutura de distribuição

- `VERSION` como fonte única; `<revision>` no Maven; `KofVersion` com
  `version.properties`; `scripts/bump-version.sh`.
- CLI: `build, run, serve, check, test, script, repl, c, fmt, config gen,
  bench, profile, inspect, debug, info, lsp, install, version, init`.
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
| `native` | `NativeBackend` (x86_64) | ELF x86_64, syscalls, free-list alloc, GC mark pending | estável |
| `native.risc` | `NativeBackend` (riscv64) | ELF riscv64 via `riscv64-linux-gnu-as/ld` + qemu (placeholder, separado de `native`) | em progresso |
| `native.arm` | `NativeBackend` (aarch64) | ELF aarch64 via `aarch64-linux-gnu-as/ld` + qemu (placeholder) | em progresso |
| `js` | `JsBackend` + `KofJsRunner` | ES Modules via GraalJS, `kof.http` via `Java HttpClient` interop | alpha |
| `kofc` | `KofCcompiler` | C subset (`int` globals, `void` funcs, `if`/`while`/`*(int*)`/`&`) → nativo x86_64 | nativo-only |

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
| `spawn` (concorrência, join implícito) | ✅ | ✅ (pthread, 31/08) | ✅ |
| strings (concat `+`, `==`, indexOf, trim, split...) | ✅ | ✅ | ✅ |
| arrays | ✅ | ✅ | ✅ |
| `List<T>`, `listOf`, `map/filter/reduce` | ✅ | ✅ | ✅ |
| `Box<T>` generics com `T` primitivo/Boxed (ex.: `Box<Int>`) | ✅ | ✅ | ✅ | 25/08 fix `substituteTypeVariable` |
| JSON encode/decode (objetos/records no JVM) + arrays nativos | ✅ | ✅ | ✅ |
| JSON decode `List<User>` (objetos aninhados) | ✅ | — | ✅ |
| kof.io (File/Path/Directory, readFile, writeFile) | ✅ | ✅ | ✅ |
| kof.time (now/sleep/interval) | ✅ | ✅ (now/sleep) | ✅ (now/sleep) |
| kof.web (`web.app()`, rotas, middleware) | ✅ | — | — |
| kof.http (`http.get/post/put/delete/status` + `timeout/retry/circuit`) | ✅ | HTTP002 | ✅ (27/08 JS via `Java HttpClient` interop; 30/08 retry/circuit paridade) |
| kof.config (env, arquivos, profiles, typed) | ✅ | ✅ (asm próprio) | ✅ |
| kof.mq (publish/subscribe/queue) | ✅ | MQ001 | ✅ |
| kof.log (`log.info/warn/error/debug`) | ✅ | ✅ (asm; UTC, sem JSON) | LOG001 |
| kof.security (passwords, crypto, JWT, secrets) | ✅ | ✅ | ✅ |
| kof.db (JDBC, query<T>, transaction) + SQLite nativo | ✅ | ✅ (SQLite; MySQL WIP) | DB001 |
| kof.orm (entity, CRUD, where, migrate, MongoDB) | ✅ | ORM001 | ORM001 |
| String.toInt/toLong/toDouble/toFloat | ✅ | ✅ | ✅ |
| kof.ui (Color, Palette, Theme, Window) | ✅ | ✅ (JS render) | ✅ |
| default parameters em funções | ✅ | ✅ | ✅ |
| `readLine()` | ✅ | ✅ | ✅ |
| `KofCcompiler` C subset → nativo | — | ✅ (27/08) | — |
| `KofScript` top-level `let`/`const` → `KofScriptGlobals` | ✅ | ✅ | ✅ |

### Concorrência (`spawn`)

```kof
spawn processarFila()
spawn {
    println("background")
}
```

- JVM: virtual threads; o programa espera as tarefas (join implícito).
- Native: pthread_create + trampoline + `await`/pthread_join + allocator
  thread-safe (futex) — ✅ 31/08 (CONC001 fechado).
- JS: sequencial (spawn statement/expr cobre; async real = CONC003 parcial).
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

## Testes (658 declarados = 650 kof-compiler +8 kof-script +5 kof-c-compiler — 1 skip; `NativeE2ETest` 50/50, `JvmE2ETest` 29/29, `KofJsE2ETest` 35/35, `KofCCompilerTest` 5/5, `KofHttpE2ETest` 4/4 em 27/08)

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
| KofCacheE2ETest | 5 | kof.cache: get/set/ttl/expiry/delete/clear x3 targets |
| KofWebE2ETest | 9 | stack web nativa (web.app, rotas, JSON, middleware) |
| KofDbE2ETest | 8 | kof.db: JDBC, query<T>, transaction, rollback, SQLite nativo, DB001 |
| KofHttpServerTest | 8 | serve engine (sockets reais) |
| KofConfigE2ETest | 11 | kof.config: env, arquivo, profiles, precedência, typed, CONF001 |
| KofSecurityTest | 25 | kof.security: senhas, crypto, JWT, secrets, adversariais (JVM/Native/JS) |
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

1. GC automático no Native — free-list `kof_free_head` implementado 27/08 (reuso `mmap`), GC mark-sweep pendente (memória ainda devolvida só no `munmap` fallback)
2. ~~`spawn` no Native: CONC001~~ — ✅ fechado 31/08: pthread_create + trampoline + await/pthread_join + allocator thread-safe (futex) + join implícito
3. ~~JSON de objetos/records no Native: JSN002~~ — ✅ fechado (composição compile-time)
4. ~~JSON Float/Double: JSN001~~ — ✅ fechado 31/08 (parser FP completo: fração+expoente, arrays Double[])
5. ~~JSON decode de arrays~~ — ✅ JSN003 fechado: Int[]/Long[]/Bool[]/String[]; JSN001 fechou Double[]/Float[] (31/08)
6. ~~Lambdas sem captura~~ — ✅ captura implementada (mutable via box `BoxN`; `Lambda0`/`Box0`)
7. ~~Generics `Box<T>` com println nativo~~ — ✅ 25/08 `Box<Int>`/`T` substituído + `kof_int_to_string`
8. ~~`SEM025` falso-positivo em `hashCode/equals/toString`~~ — ✅ `isObjectMethod` em 25/08
9. ~~`await`/join~~ — ✅ nos 3 targets (JVM virtual threads, JS sequencial, Native pthread)
10. ~~`kof fmt`: planned (P5)~~ — ✅ implementado: `kof fmt` via parser real
    (`KofFormatter`), idempotente (2c3e794)
11. ~~Map/Set~~ — ✅ `List.map/filter/reduce` + `Map/Set` JVM/Native/JS (26/08)
12. Pattern matching: ✅ `switch (x) { case String s: ... }` + `case Point(x,y)` em `Parser/Semantic/CompilerDriver` + `Native rbx→rcx` + `JS typeof` (27/08 `Point(x,y)` `JVM:30 Native:30 JS:30` `KofPatternMatchingTest 10/10` + `KofWebE2ETest 9/9`)
13. Null safety `String?`: ✅ básica `String?` `Int?` `?`-check em compile-time `Type.NullableType` `JvmBackend:110` `SemanticAnalyzer:1637` `isAssignable` `var s:String?=null` `s==null` `t="hello"` `jvm: null/hello native: null/hello js: null/hello` (27/08)
14. ~~Módulos multi-arquivo imports perdidos em projetos grandes~~ — ✅ 27/08 `CompilerDriver.java:243` `import a.b.C` file import `+` `a.b` dir import, `largeproj` `a/b/C.kf` `decls=2` `Main.class+a/b/C.class` ok
15. ~~`List.get` native~~ — ✅ verificado `listOf(1,2,3).get(1) → 2` nativo `kof_list_get` bounds OK (caso `List.of` era `listOf`)
16. Web: status codes/headers customizados por handler: ✅ `kof.web.status(201, body)` + `headerSet("X","y")` em `KofWeb.java:107` + `JvmWebRuntime.java:22` `KOF_WEB_STATUS/HEADERS` + `JvmRuntime.java:489` `kof_web_dispatch` `+wired` `kof_web_build` headers `+wired` `status_text 201 Created 202 Accepted` `JVM: 201/hellox 202/value` `KofWebE2ETest 9/9` (27/08)
17. Web: `kof.web` nativo sem servidor (P2) — `kof.http` ✅ JVM+JS (`Java HttpClient`), Native HTTP002
18. MySQL/MariaDB no Native: wire protocol em progresso (auth scramble SHA-1 feito; falta handshake completo, query e prepared statements) (P3)
19. ~~`kof_sec_secret_get` no Native~~ — ✅ resolvido: reescrito no padrão linear dos demais; segfault e fragmentos errados eliminados.
20. ~~Ponto flutuante no Native~~ — ✅ FLT001 fechado: FP é XMM real (`vcvtsi2sd`, `mulsd`); dtoa via snprintf alinhado; `kof_string_to_double` parse completo (fração+expoente).
21. ~~idem~~
22. `KofCcompiler` riscv64/aarch64 placeholder (target separation feito `Target.NATIVE_RISCV64/AARCH64` + `parseTarget native.risc/arm`, codegen ainda x86_64 placeholder, `qemu` skip)
23. ~~`kof.cache` nativo: segfault em `set_ttl` (index `%rax` clobberado) + `get/ttl` (exp em `%rdi` clobberado) + `println(null)` segfault~~ — ✅ 30/08: registradores preservados (`%r14/%r13/%r15`), branch `jle` de expiração corrigido, `kof_print_string` guarda null, `find_slot` sobrescreve chave existente; `KofCacheE2ETest 5/5 x3 targets`

---

## Próximos Passos (ordem P1→P5)

**P1 — Linguagem (em progresso):**
1. ✅ `Map/Set` + `enum` + `await` + `List.map/filter/reduce` (JVM/Native/JS)
2. ✅ `Pattern matching` — `switch (x) { case String s: ... }` + `case Point(x,y)` `JVM/Native/JS` `30` `10/10`
3. ✅ `Nullability` `String?`/`Int?` + `?`-check `Type.NullableType` `jvm/native/js null/hello` (27/08 básica)
4. `Módulos multi-arquivo` — `kof build <dir>` com resolução unificada (`import a.b.C` file fix done, semântica unificada residual)

**P2 — Web completa (próxima listinha):**
5. ✅ Resposta rica `status(201, body)`/`headerSet("X","y")` `JVM` `201 Created 202 Accepted` `X-Custom/X-Test` `KofWebE2ETest 9/9` (27/08) `Native WEB002` `JS stub`
6. ✅ `kof.cache` `get/set/set(key,v,ttl)/ttl/delete/clear` — ✅ JVM/Native/JS (30/08; fix nativo: clobber de `%rax/%rdi` em `set_ttl/get/ttl` + `println(null)` segfault; `KofCacheE2ETest 5/5 x3 targets`)
7. ✅ `WebSocket` `app.ws("/chat") { }` + `SSE` `sse.send/event/close` — ✅ JVM (30/08; PRs 14-17: persistent-conn/route-kinds, SSE, handshake RFC 6455, frame codec+máscara; `KofWebSseE2ETest 7/7` `KofWebWsE2ETest 11/11` `KofWsFrameTest 7/7`)
8. `Scheduler` `every(30s) { }`/`at("0 3 * * *") { }` sobre virtual threads (JVM `every/at/cancel` `kof_scheduler_every` `ScheduledExecutor` + JS `setInterval` `kofSchedulerEvery` `27/08` `scheduler.every(100) job-1` `JVM:job-1 JS:kofSchedulerEvery` `KofTimeE2ETest 5/0` `Native SCHED001`)
9. ✅ `kof.http` `timeout`/`retry`/`circuit breaker` — ✅ JVM+JS (30/08; retry repete em exceção+HTTP 5xx, circuito abre após N falhas por 30s com fail-fast, `circuit(0)` recupera; `KofHttpResilienceE2ETest 3/3` JVM+JS) — falta `HTTP/2`

**P3 — Data produção:**
10. Query DSL tipada `User.query { where age > 18 }`
11. Connection pooling + `kof.db`/`kof.orm` fora do JVM (JS via WASM, Native ORM sobre SQLite)
12. MySQL/MariaDB nativo completo (handshake+query+prepared)

**P4 — Observabilidade:**
13. Métricas `histogram` + endpoint `/metrics` (Prometheus)
14. Health `app.health("/health")` + tracing/OpenTelemetry

**P5 — DX:**
15. `kof fmt` (parser real) + `kof init` + `REPL`
16. LSP hover/completion/rename + Debugger Native DWARF/JS source maps + VS Code extension

Roadmap: `docs/roadmap.md`; execução: `docs/plan-platform-completion.md`

---

## Roadmap de Longo Prazo

- `docs/roadmap.md` com a visão completa.
- Próximo grande marco: **aplicação web completa em Kof** (frontend via KofJS,
  backend via kof serve, banco, auth, JSON — lógica de negócio em poucos
  arquivos).