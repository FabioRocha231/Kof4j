# Status do Projeto Kof

**Última atualização:** 31 de agosto de 2026
**Versão:** 0.2.6-beta

---

## Build

```
mvn clean package    → PASSA
mvn test             → 755 testes 742 kof-compiler +8 kof-script +5 kof-c-compiler, 0 falhas)
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
   sockets nativos: **handshake + auth scramble SHA-1 + auth-switch
   (mysql_native_password) + COM_QUERY + parse de resultset (coldefs + rows
   + EOF) + binds `?` (substituição de literal client-side, `nativeMysqlWireProtocol`
   — 31/08)**. Prepared statements via COM_STMT_PREPARE (binário) pendente.
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
- Testes: `KofDbE2ETest` (9), `KofOrmE2ETest` (12+; MariaDB/PostgreSQL/MongoDB
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
  thread-safe (futex) + `done`/`poll`/`cancel`/`cancelled`/`selectAny` — ✅ 31/08 (CONC001 fechado).
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

### Media (`kof.media`) — arquivos, não strings

A linguagem NÃO transporta imagem/áudio como `String` gigante (nem base64
literal no fonte, nem data-URI colado à mão — o padrão que o
Kof-editor-theme-maker era forçado a adotar com `pageCss(): String` e
`kofPngData(): String`). O app trata o ARQUIVO:

```kof
main() {
    var app = web.app()
    app.serveDir("/img", "assets")      // GET /img/logo.png → bytes do disco, image/png
    app.get("/thumb") {
        var img = Image.open("assets/logo.png")   // javax.imageio
        img.saveAs("assets/thumb.jpg", "jpeg")
        return "w=" + img.width() + " h=" + img.height()
    }
    app.get("/rec") {
        var m = Mic.record(2)            // javax.sound.sampled (16kHz mono PCM)
        m.saveWav("assets/gravacao.wav")
        return "ms=" + m.durationMs()
    }
    app.get("/clip") {
        var v = Video.open("assets/clip.mp4")
        return "ms=" + v.durationMs() + " " + v.format()
    }
    app.serveDir("/media", "assets")      // Range 206 p/ <video> no browser
    app.listen(8080)
}
```

- **`Image`** (`ImageData`): `open` (PNG/JPEG/GIF/BMP), `width/height/format`,
  `save`, `saveAs(path, fmt)`, `bytes`/`bytesAs`, `dataUri` (opcional, em
  runtime — nunca literal no fonte), `close`.
- **`Audio`**: `openWav`/`saveWav` (WAV RIFF PCM 16-bit), `sampleRate`,
  `durationMs`, `pcmBytes`.
- **`Mic`**: `record(seconds)` do microfone padrão, `list()`.
- **`Video`**: `open` + metadados do container (`path/size/format/durationMs`,
  MP4/MOV lidos do box `mvhd`; outros containers → 0) + `bytes`/`close`.
  O app NÃO decodifica frames — sem lib externa no JVM (gap honesto); a API
  serve o arquivo (serveDir + Range) para o navegador reproduzir.
- **`app.serveDir(prefix, dir)`** (`web`): fallback de rotas dinâmicas —
  devolve o ARQUIVO em binário com content-type pela extensão (HTML/CSS/JS/
  imagens/áudio/**vídeo**/fontes/PDF...), `Cache-Control`, proteção contra
  path traversal e **Range requests** (`206 Partial Content` + `Content-Range`
  + `Accept-Ranges: bytes`, `416` para range inválido) — necessário para
  `<video>`/`<audio>` navegarem/seekarem no browser. Sem isso, o app só
  tinha `String` por rota → CSS/HTML/imagens viravam strings concatenadas e
  base64 colado no fonte.
- Caminhos relativos resolvem contra a raiz do projeto (`-Dkof.root`,
  definido pelo CLI `run`/`serve` como o diretório do `.kf`).
- **Targets**: JVM (javax.imageio + javax.sound; vídeo como container +
  streaming). **Gaps honestos**: decodificação de frames de vídeo (sem lib
  externa), câmera (MEDIA002), mic sem hardware (MEDIA003), paridade
  Native/JS (MEDIA001 — ART sem javax.imageio; app Android roda no WebView
  KofJS).
- Ver: `KofMediaE2ETest` (12 testes: serving binário byte-a-byte,
  content-type, traversal bloqueado, 404, dimensões reais, conversão
  PNG→JPEG, WAV info/copy, mic sem hardware, metadados de MP4, Range
  206/416/200).

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

## Testes (755 declarados = 742 kof-compiler +8 kof-script +5 kof-c-compiler  medição real 31/08 (grep @Test)

| Suíte | Quantidade | Cobertura |
|-------|-----------|-----------|
| CompilerDriverTest | 190 | compilação, semântica, fases, isolamento |
| NativeE2ETest | 50 | execução real de binários nativos |
| KofJsE2ETest | 35 | execução real JS (GraalJS) |
| JvmE2ETest | 29 | execução real de bytecode JVM |
| KofSecurityTest | 25 | kof.security: senhas, crypto, JWT, secrets, adversariais |
| OptimizerTest | 21 | passes de otimização da IR |
| KofOrmE2ETest | 16 | kof.orm: entity, CRUD, where, migrate, unique, MongoDB |
| IoE2ETest | 15 | kof.io multiplatform |
| ComponentCoreE2ETest | 14 | kof.ui Component: view/onMount/onDispose |
| CoreRegressionE2ETest | 14 | regressões de uso real (BOM, toInt, ARITH001...) |
| JsonE2ETest | 14 | JSON JVM + Native |
| UiE2ETest | 14 | kof.ui: widgets, estilo, bindings, múltiplas janelas |
| AndroidInteropE2ETest | 11 | android: interop Java (external classpath) |
| KofConfigE2ETest | 11 | kof.config: env, arquivo, profiles, precedência, typed, CONF001 |
| KofWebWsE2ETest | 11 | WebSocket RFC 6455: handshake + frame + lifecycle |
| StructuredTestE2ETest | 11 | test "nome" {} nos 3 targets + process.exit |
| BackendParityTest | 10 | paridade JVM/Native/JS |
| KofLogE2ETest | 10 | kof.log JVM: níveis, stderr, off, JSON, correlation |
| KofPatternMatchingTest | 10 | switch case String s / Point(x,y) 3 targets |
| KofWebE2ETest | 10 | stack web nativa (web.app, rotas, JSON, middleware) |
| ExceptionsE2ETest | 9 | try/catch/finally JVM + Native |
| KofDbE2ETest | 9 | kof.db: JDBC, query<T>, transaction, rollback, SQLite nativo, DB001 |
| KofHttpServerTest | 8 | serve engine (sockets reais) |
| KofMediaE2ETest | 8 | kof.media + serveDir: Image/Audio/WAV, conteúdo binário (não base64) |
| NativeConfigE2ETest | 8 | kof.config Native (asm): precedência, typed, comentários |
| IdiomaticE2ETest | 7 | idiomas consolidados (chaining, primary ctor) |
| JsonCompleteE2ETest | 7 | JSON completo: Float/Double, arrays decode (JVM) |
| KofAwaitTest | 7 | spawn/await Handle<T> tipado (JVM) |
| KofWebSseE2ETest | 7 | SSE: sse.send/event/close (sockets reais) |
| KofWsFrameTest | 7 | frame codec RFC 6455: máscara, limites, ping/pong |
| NativeLogE2ETest | 7 | kof.log Native (asm): níveis, stderr, formato civil, off |
| IdiomaticCoreE2ETest | 6 | field initializers, \uXXXX, listOf<T>() |
| AssertE2ETest | 5 | assert JVM + Native |
| FloatingPointGapE2ETest | 5 | FP XMM: encode/decode/arrays (FLT001) |
| KofCacheE2ETest | 5 | suíte E2E/compilação |
| KofConcurrency2Test | 13 | spawn stmt/expr, selectAny, cancel/cancelled, done/poll, awaitTimeout, channel (JVM/Native/JS) |
| KofHigherOrderTest | 5 | funções de ordem superior (map/filter/reduce) |
| KofIntOverflowNativeTest | 5 | aritmética Int 32 bits no Native |
| KofTimeE2ETest | 5 | time now/sleep/interval (JVM/Native/JS) |
| KofWebTlsTest | 5 | TLS/HTTPS: listenSecure + kof.http sobre TLS |
| PackagesE2ETest | 5 | pacotes/módulos multi-arquivo (import a.b.C) |
| FunctionSyntaxTest | 4 | formas de declaração de função |
| KofEnumSwitchTest | 4 | switch exaustivo sobre enum + SEM031 |
| KofEnumTest | 4 | enum: values/valueOf/name, SEM030, mapeamento JVM |
| KofHttpE2ETest | 4 | kof.http client (sockets reais, JVM + JS) |
| KofMqE2ETest | 4 | kof.mq publish/subscribe/queue (JVM+JS, MQ001) |
| KofWebStreamE2ETest | 4 | WebSocket/SSE end-to-end (persistent-conn) |
| LambdaE2ETest | 4 | lambdas + if-expr |
| RouterE2ETest | 4 | kof.ui Router Fase 7: go/replace/back/forward |
| SpawnE2ETest | 4 | spawn (JVM virtual threads) + join implícito |
| StdlibE2ETest | 4 | now/readFile/writeFile |
| ConfigGenTest | 3 | kof config gen: template kof.config do código |
| KofHttpResilienceE2ETest | 3 | kof.http timeout/retry/circuit (JVM + JS paridade) |
| KofMapSetTest | 3 | Map/Set 3 targets (asm próprio no Native) |
| KofObservabilityTest | 3 | health/metrics/requestId (JVM/Native/JS) |
| KofSecurityG9Test | 3 | web security: rateLimit/session/apiKey |
| KofValidationTest | 3 | 13 predicados de validação (3 targets) |
| TetrisEasterEggTest | 3 | registro easter egg oculto |
| TuringCompleteE2ETest | 3 | completude de Turing (loops/while/recursão) |
| WindowE2ETest | 3 | Window: size, close-to-exit |
| DebugInfoE2ETest | 2 | SourceFile + LineNumberTable (JVM) |
| IRStatisticsTest | 2 | observer de IR + estatísticas de otimização |
| NativeDebugTest | 1 | harnesses de debug nativo |
| NativeDebugTest2 | 1 | harnesses de debug nativo (2) |
| NativeDebugTest3 | 1 | harnesses de debug nativo (3) |
| NativeDebugTest4 | 1 | harnesses de debug nativo (4) |
| NativeDebugTest5 | 1 | harnesses de debug nativo (5) |
| **Total kof-compiler** | **742** | |
| kof-script | 8 | KofScriptGlobals / repl / --watch |
| kof-c-compiler | 5 | KofC C subset → ELF |
| **Total** | **755** (+1 skip condicional; conferir total no CI a cada release) | |
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
2. ~~`spawn` no Native: CONC001~~ — ✅ fechado 31/08: pthread_create + trampoline + await/pthread_join + allocator thread-safe (futex) + join implícito + `done`/`poll`/`cancel`/`cancelled`/`selectAny` (cancel cooperativo por TID + selectAny polling 1ms; `SemanticAnalyzer` desambigua `cancel(Handle<T>)→Bool` vs `scheduler.cancel(String)→VOID`)
   - ⚠️ bug pré-existente SEPARADO (reproduz na tree limpa, sem o feature de CONC001): `spawn→await→spawn` corrompe a pilha/frame da main thread — SIGSEGV no 2º `pthread_create` (retorno viciado), mesmo sem cancel/selectAny. **Gatilho = `pthread_join` na main** (reprodutor mínimo: `spawn t1(); await; spawn t2()`; sem o `join` — ex. `spawn; sleep; spawn` — passa). Alinhamento de pilha já auditado (conforme ABI x86_64) e **descartado** como causa. Reprodutível com `BUG`/`G1`/`M2f`/`G3`. Suspeito: interação `pthread_join`+`kof_alloc`/`pthread_create` no main (possível UAF do bloco do handle).
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
18. ~~MySQL/MariaDB no Native: wire protocol~~ — ✅ 31/08: handshake + scramble SHA-1 + auth-switch + COM_QUERY + resultset (coldefs/rows/EOF) + **binds `?`** (substituição de literal client-side no COM_QUERY); restam **prepared statements** binários (COM_STMT_PREPARE/EXECUTE)
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
8. ✅ `Scheduler` `every(ms) { }`/`at(cron) { }`/`cancel(id)` — ✅ JVM (`ScheduledExecutor`, 27/08) + JS (`setInterval`) + **Native SCHED001** (31/08: thread por job — trampoline `usleep` ms→us + `active` flag com futex — `cancel(id)` cooperativo; `KofConcurrency2Test` `schedulerEveryNative/Jvm`)
9. ✅ `kof.http` `timeout`/`retry`/`circuit breaker` — ✅ JVM+JS (30/08; retry repete em exceção+HTTP 5xx, circuito abre após N falhas por 30s com fail-fast, `circuit(0)` recupera; `KofHttpResilienceE2ETest 3/3` JVM+JS) — falta `HTTP/2`

**P3 — Data produção:**
10. Query DSL tipada `User.query { where age > 18 }`
11. Connection pooling + `kof.db`/`kof.orm` fora do JVM (JS via WASM, Native ORM sobre SQLite)
12. ~~MySQL/MariaDB nativo (handshake+query)~~ — ✅ 31/08 (wire protocol: handshake+scramble+auth-switch+COM_QUERY+resultset); restam **prepared statements** + binds `?` no MySQL nativo

**P4 — Observabilidade:**
13. Métricas `histogram` + endpoint `/metrics` (Prometheus)
14. Health `app.health("/health")` + tracing/OpenTelemetry

**P5 — DX:**
15. `kof fmt` (parser real) + `kof init` + `REPL`
16. LSP hover/completion/rename + Debugger Native DWARF/JS source maps + VS Code extension

## Roadmap — Estado por Fase (31/08)

### Concluído — Disponível

- Compiler foundation — Lexer, Parser, AST, Type system foundation, Semantic analysis, Kof IR
- JVM backend; Native backend (x86_64); JS backend (GraalJS)
- classes, records, inheritance, interfaces, constructors (sobrecarga), exceptions, generics, collections, string operations, control flow
- `kof build`, `kof run`, `kof serve`, `kof test`, `kof debug` (MVP JVM, DAP sobre stdio), `kof bench` (37 benchmarks + baselines), `kof fmt` (parser real, idempotente)
- `kof.web` — rotas e middleware (JVM); WebSocket RFC 6455 + SSE nativo (JVM, 0.2.6-beta); TLS/HTTPS `web.listenSecure` (JVM)
- `kof.db` — JDBC + SQLite nativo; `kof.orm` — entity, CRUD, migrate, MongoDB (JVM)
- `kof.log` nativo; `kof.config` (arquivo > env > profile, tipado, `${key}`, 3 targets); `kof.mq` pub/sub (JVM)
- cliente HTTP (JVM) + JS via `Java HttpClient` interop (HTTP002 JS fechado) + retry/circuit (3 targets, 30/08)
- `kof.security` v1 (JVM/Native/JS); web security G9 — rateLimit, sessões, API keys (3 targets)
- `kof.validation` (13 predicados, 3 targets); `kof.observability` (health/métricas/request IDs, 3 targets); `kof.ui` widgets com render KofJS
- `kof.process` execução de processos externos; `process.spawn` stdin/stdout vivos (F10, JVM/JS)
- **Concorrência**: `spawn`/`await` JVM (virtual threads) + **Native (pthread — CONC001 fechado 31/08)** + **Android (platform threads — AND001 fechado 31/08, ART sem virtual threads → fallback)** + JS sequencial; `done`/`poll` não-bloqueantes; `cancel`/`cancelled` cooperativo (JVM + Native por TID); `selectAny` (JVM + Native + JS); `awaitTimeout(r, ms)` — valor no prazo, exceção capturável no estouro (JVM + Native; JS sequencial = paridade); `channel<T>()` com `send`/`receive` (JVM LinkedBlockingQueue + Native FIFO futex + JS array); `scheduler.every/at/cancel` (JVM `ScheduledExecutor` + JS `setInterval` + **Native SCHED001**: thread por job com trampoline `usleep` ms→us + flag `active` futex) — `KofConcurrency2Test` 15/15, `SpawnE2ETest` 4/4
- **`kof.media` (31/08)** — gestão de arquivos multimídia sem base64 literal: `Image.open/save/saveAs/dataUri` (javax.imageio, PNG/JPEG/GIF/BMP), `Audio.openWav/saveWav` (WAV RIFF PCM 16-bit), `Mic.record` (javax.sound.sampled), `Video.open` (metadados do container MP4/MOV + streaming); `web` `app.serveDir(prefix, dir)` serve ARQUIVO do disco com content-type correto + **Range requests (206/416)** p/ vídeo navegável + proteção de path-traversal; raiz do app via `-Dkof.root` (CLI `run`/`serve`). Gaps: frames de vídeo (sem lib externa), câmera (MEDIA002), sem hardware de mic (MEDIA003), paridade Native/JS (MEDIA001) — `KofMediaE2ETest` 12/12
- **KofAndroid Fase 2 (31/08)** — `--apk` standalone (aapt2/d8/zipalign/apksigner direto do CLI) + release signing `--keystore/--storepass/--keypass/--alias` + label/permissões derivados do programa (`detectAppLabel`/`@Permissions`)
- enum nos 3 targets + switch exaustivo (SEM031); Map/Set nos 3 targets (COL001 fechado)
- otimizador de IR sempre ativo; pattern matching (switch com tipos + destructuring, 3 targets); null safety básica (`String?`, 3 targets); higher-order em coleções (map/filter/reduce, 3 targets); módulos multi-arquivo (`import a.b.C`)
- KofScript — top-level let/const (`KofScriptGlobals`, repl, `--watch`); KofC compiler — C subset → ELF x86_64 (`kof c`)
- LSP com hover/completion + diagnostics reais; widening de return
- Native GC — free-list `kof_free_head` (reuso mmap, 27/08); `kof_gc_collect` mark-sweep emitido (auto-GC desligado após hang — memória devolvida só no `munmap` fallback; ver "Bugs Restantes" #1)
- Ponto flutuante real no Native (FLT001 fechado 31/08 — XMM); JSON objetos/records no Native (JSN002 fechado) + arrays FP (JSN001/003)
- releases multiplataforma (2 jobs: `test-and-bump` → `package-and-release`; linux-x86_64 / macos-arm64 / windows-x86_64)

### Em desenvolvimento

- Standard Library (contratos em estabilização)
- Async / Concurrency residual: JS async real sobre Promises/event-loop (CONC003); ~~Android `AND001`~~ — ✅ 31/08 (platform threads no ART, fallback quando `Thread.startVirtualThread` ausente); ⚠️ bug pré-existente `spawn→await→spawn` (SIGSEGV no próximo `pthread_create` — ver "Bugs Restantes" #2)
- ~~KofAndroid Fase 2~~ — ✅ 31/08 (`--apk` standalone + `--keystore` release signing + label/permissões derivados do programa)
- ~~`kof.media` residual (31/08)~~ — ✅ 31/08: **video** (`Video.open` + metadados do container + streaming) e **Range requests** (206/416) fechados; restam câmera (MEDIA002 — sem lib externa no JVM) e paridade Native/JS (MEDIA001 — ART sem javax.imageio; app Android roda no WebView KofJS)
- MySQL/MariaDB nativo — **wire protocol ✅ 31/08** (handshake + scramble SHA-1 + auth-switch + COM_QUERY + resultset; `nativeMysqlWireProtocol`); restam **prepared statements** (bind `?` via COM_STMT_PREPARE)
- `native.risc` (riscv64) toolchain estável + `native.arm` (aarch64) placeholder — ELF via cross-as/ld + qemu (codegen ainda x86_64)
- Debugger — além do MVP JVM (DAP sobre stdio já no JVM; Native DWARF / JS source maps pendentes)
- KofJS — plataforma web no browser (ES Modules via GraalJS já em alpha)

### Planejado

- KofScript — runtime completo de execução direta (hoje só top-level let)
- package manager (`kof init`, `kofdeps`, registry)
- complete language specification; conformance suite
- query DSL tipada para o ORM (`User.query { where age > 18 }`)
- full web platform (frontend declarativo + routing/forms/SSR)
- **gRPC no `kof.web`** (31/08) — comunicação RPC gRPC como primeira classe da plataforma web: `app.grpc { service ... }` (stubs a partir de `.proto`, server streaming + unary sobre HTTP/2 no JVM) + client `grpc.call(endpoint, method, msg)`; codegen `.proto` → IR; parity JVM primeiro (ver `docs/roadmap.md` § web)
- auto-hospedagem (compilador escrito em Kof)

Roadmap completo: `docs/roadmap.md`; execução: `docs/plan-platform-completion.md`