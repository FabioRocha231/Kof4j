# Backend Parity — Kof JVM × Native × KofJS

**Última atualização:** 2 de setembro de 2026
**Versão:** 0.2.6-beta

> Deltas desde 0.1.0: Targets `native.risc` (riscv64) e `native.arm` (aarch64) separados de `native` x86_64 (**em desenvolvimento** — plumbing pronto, codegen stub; ver `docs/native-multiarch.md`); Native free-list (`kof_free_head`) + `kof_gc_collect` (mark-sweep pendente; auto-GC desativado); MySQL wire protocol em progresso (`kof_db_mysql_scramble` + `user:pass@`); pattern matching `switch case String s` + record destructuring `Point(x,y)` em JVM/Native/JS; `String?` null safety básica; `KofScript` top-level `let` → `KofScriptGlobals`; `KofCcompiler` (`kof c`) native-only C subset; `List map/filter/reduce` + `Box<T>`; Windows SIGPIPE fix.
> Deltas 30-31/08: `spawn`/`await` real no Native (pthread + trampoline + join + allocator thread-safe futex — CONC001); FP real em XMM (FLT001); JSON objetos/records + arrays FP no Native (JSN001/JSN002/JSN003); WebSocket/SSE no JVM (`app.ws`/`sse.*`, RFC 6455); `kof.http` retry/circuit JVM+JS (30s window, fail-fast); `kof.cache` 3 targets (fix de clobber de registradores); UI Fase 7 Router (JS real, JVM no-op); SQLite nativo via `.so` direto; `kof fmt` + `kof config gen`.
> Deltas 01/09: **`spawn` com lambda que captura variável externa** (`SpawnStmt` emitia a lambda com zero capturas → `VerifyError`/valor errado; agora coleta via `collectCaptures`); **`&&`/`||` booleanos short-circuitam no JS** (backend emitia `&`/`|` bitwise que avalia os dois lados; agora `&&`/`||` JS para `bool`, bitwise intacto); **`Channel<T>` como parâmetro de função** (tipo saía com package vazio e o `isChannel` exigia `kof.concurrent` → mapeado como builtin + `JvmTypeMapper` → `LinkedBlockingQueue`); **`println`/`print` antes de `spawn` no Native** e o bug pré-existente **`spawn→await→spawn`** (SIGSEGV no `pthread_create` — stack chegava desalinhada ao C call; alinhado com `andq $-16` preservando `r15` + frame); **KofJS no browser real** (`KofJsBrowserE2ETest` — Chrome headless + HTTP + DOM); **`SECN002` fechado — AES-256-GCM no KofJS** (AES-GCM puro JS: FIPS 197 key expansion + GCM CTR/GHASH, formato `aesgcm$iv$ctTag` idêntico ao JVM/Native; tamper detectado por decode base64 estrito `strict=true` — paridade cross-target JVM↔JS testada); **`OBS002` fechado — histogram/metrics no Native** (store asm 32B name+sum+count + export Prometheus via `kof_string_concat` — paridade de conteúdo com o JVM); **`TIME001` (Native) — `time.interval`/`time.cancel`** (mesmo mecanismo do `scheduler.every`/`cancel`, SCHED001: thread por job; alias `kof_time_interval`/`kof_time_cancel` → `jmp kof_scheduler_every`/`cancel`; mutação por referência da captura validada — JS segue gap: event-loop + `sleep` bloqueante incompatíveis); **`MQ001` fechado — kof.mq no Native** (pub/sub + filas in-process em asm: store `.bss` + nodes 40B, invoke-com-arg `rdi`=fn `rsi`=msg, unsubscribe por identidade do objeto fn — paridade de output JVM↔Native↔JS); **`Set<T>`/`Map<K,V>` como campo/retorno de classe no JVM** (o `JvmTypeMapper` mapeava só `List`→`ArrayList`, então `Set`/`Map` viravam `Lkof/Set;`/`Lkof/Map;` → `NoClassDefFoundError`; agora `HashSet`/`HashMap`; + parse de método de classe com retorno genérico `Set<Int> all(` — `KofMapSetTest.setMapAsFieldAndReturn`, 3 targets); **Query DSL tipada do ORM (nível 3, `ORM001`)** — `User.query(db){ where; orderBy; limit }` baixado para `db.query<T>` (SQL preparada em compile-time, valores como binds; `KofOrmE2ETest` 22); **source map V3 do KofJS** (mappings VLQ reais em nível de linha: cada função gerada → linha Kof via `KofDebugInfo` — antes era stub `"mappings":""`; `KofJsSourceMapTest`).
> Tabela reflete 0.2.6-beta (02/09) — Build `mvn test` **792** (775+8+5+4), golden 16/16, integration 9/9. DoD em `docs/plan-platform-completion.md`.

---

## Tabela de Paridade

| Feature | JVM | Native | KofJS | Notas |
|---------|-----|--------|-------|-------|
| Literals (int, long, float, double, string, bool, char, null, hex) | ✅ | ✅ | ✅ | hex: `0x...` |
| Variáveis, `var`/`val`, inferência | ✅ | ✅ | ✅ | |
| Aritmética, bitwise, unário | ✅ | ✅ | ✅ | |
| if/else | ✅ | ✅ | ✅ | |
| if-expr (`var x = if (c) a else b`) | ✅ | ✅ | ✅ | |
| while, for, do-while, break/continue | ✅ | ✅ | ✅ | |
| for-in (`for (var x in coll)`) | ✅ | ✅ | ✅ | |
| switch (`case N:`) | ✅ | ✅ | ✅ | |
| Funções (todas as formas, sem `fun`) | ✅ | ✅ | ✅ | |
| Records (`record Point(Int x, Int y)`) | ✅ | ✅ | ✅ | toString/equals/hashCode |
| Classes, campos, métodos | ✅ | ✅ | ✅ | |
| `constructor(...)` e primary `class X(...)` | ✅ | ✅ | ✅ | |
| Herança, `super`, override | ✅ | ✅* | ✅ | Native: `super.metodo()` = SUP001 |
| Virtual dispatch | ✅ | ✅ | ✅ | |
| Interfaces | ✅ | ✅ | ✅ | |
| Generics (erasure) — `Box<T>` `T` primitivo | ✅ | ✅ | ✅ | 25/08 `substituteTypeVariable` + `kof_int_to_string` |
| Lambdas `(x: Int) -> expr` | ✅ | ✅ | ✅ | com capturas (box `BoxN`) |
| Exceptions (throw "msg", try/catch/finally) | ✅ | ✅ | ✅ | Native: unwinding próprio |
| `assert(cond[, msg])` | ✅ | ✅ | ✅ | |
| `spawn` stmt / `spawn f()` / `await` / `poll` / `done` / `cancel`+`cancelled` / `selectAny` / `awaitTimeout` / `channel<T>` send/receive / `scheduler.every`+`at`+`cancel` (Handle<T>, unbox, exceção limpa) | ✅ (virtual threads; canal = LinkedBlockingQueue; scheduler = ScheduledExecutor) | ✅ 31/08 (pthread_create + trampoline + pthread_join, allocator futex — CONC001; awaitTimeout = polling 1ms; canal = FIFO futex; **scheduler SCHED001** = thread por job + `usleep` ms→us + flag `active`) | ✅ sequencial (async real = CONC003; canal = array; scheduler = setInterval) | **01/09:** lambda c/ captura (`collectCaptures`), `Channel<T>` como parâmetro, `println`/`spawn→await→spawn` sem SIGSEGV (alinhamento de stack no `pthread_create`). `KofAwaitTest` 7/7 · `KofConcurrency2Test` 18/18 · `SpawnE2ETest` 8/8 |
| Strings (`+`, `==`, length, charAt, substring, contains, startsWith, endsWith, indexOf, trim, case, replace, split) | ✅ | ✅ | ✅ | |
| Arrays (`new Int[n]`, `arr[i]`, `.length`) | ✅ | ✅ | ✅ | |
| `List<T>`, `listOf`, for-in | ✅ | ✅ | ✅ | |
| `Map<K,V>` + `mapOf` (put/get/remove/contains/size/keys/values/clear/isEmpty) | ✅ HashMap | ✅ asm próprio | ✅ JS Map | `KofMapSetTest` — **campo/retorno de classe 01/09** (mapper `Map`→`HashMap`; parse de método c/ retorno genérico) |
| `Set<T>` + `setOf` (add/contains/remove/size/clear/isEmpty) | ✅ HashSet | ✅ asm sobre List | ✅ JS Set | tag de tipo no Native; **campo/retorno de classe 01/09** (mapper `Set`→`HashSet`) |
| `enum` + values/valueOf/name + switch exaustivo (`SEM031`) | ✅ | ✅ | ✅ | enum→String nos descritores; `KofEnumSwitchTest` |
| JSON encode/decode (primitivos, List) | ✅ | ✅ | ✅ | arrays `JSN003` fechado; `Double[]`/`Float[]` no Native (JSN001, 31/08) |
| JSON objetos/records (JSN002) | ✅ | ✅ 31/08 | ✅ | composição em compile-time no Native |
| kof.io (File, Path, Directory) | ✅ | ✅ | ✅ | |
| kof.time (`now()`, `sleep`, `interval`) | ✅ | ✅ (now/sleep/**interval**) | ✅ (now/sleep) | `interval`/`every` JVM (ScheduledExecutor) + **Native (01/09, `time.interval` reusa o scheduler — SCHED001; mutação por referência validada)** + JS `every` (`setInterval`, 27/08); JS `time.interval` ainda gap `TIME001` (event-loop + `sleep` bloqueante incompatíveis) |
| `readLine`, `readFile`, `writeFile` | ✅ | ✅ | ✅ | |
| `kof.validation` (13 preds) | ✅ | ✅ | ✅ | `KofValidationTest` |
| `kof.observability` (health/metrics/requestId) | ✅ | ✅ | ✅ | `KofObservabilityTest` |
| `kof.http` client | ✅ | HTTP002 | ✅ (GraalJS via `Java HttpClient` interop + fetch fallback) | `KofHttpE2ETest` 4/4 (JVM+JS); retry/circuit paridade 30/08 (`KofHttpResilienceE2ETest` 3/3) |
| `kof.cache` (get/set/ttl/delete/clear) | ✅ | ✅ 30/08 (fix clobber `%rax`/`%rdi`) | ✅ | `KofCacheE2ETest` 5/5 x3 targets |
| `kof.mq` (pub/sub + queue) | ✅ | ✅ (01/09, pub/sub + filas in-process, asm) | ✅ | `KofMqE2ETest` 4/4 (JVM+Native+JS) |
| `kof.config` (typed) | ✅ | ✅ (asm próprio) | CONF001 | precedência total Native (`KOF_CONFIG` > env > profile > `kof.config`); `NativeConfigE2ETest` 8 |
| `kof.log` | ✅ (JSON + correlation ID) | ✅ (asm; UTC, sem JSON) | ✅ 01/09 (console.* + nível) | `KofLogE2ETest` 11 (incl. JS) + `NativeLogE2ETest` 7 |
| `kof.security` (passwords/crypto/jwt/secrets + G9) | ✅ | ✅ | ✅ | PBKDF2/SHA512/JWT/AES-GCM (asm no Native, JS puro no KofJS — `SECN002` fechado 01/09) |
| `kof.db`/`kof.orm` | ✅ | ✅ (SQLite `.so` direto; **`transaction {}` commit/rollback 01/09** (EH asm + BEGIN/COMMIT/ROLLBACK); **MySQL wire protocol** — handshake+scramble+auth-switch+COM_QUERY+resultset 31/08)/ORM001 | DB001/ORM001 | **Query DSL `User.query(db){ where; orderBy; limit }` (nível 3) 01/09** — baixa p/ `db.query` (JVM E2E H2); MySQL native `nativeMysqlWireProtocol`; `nativeTransaction{Commits,RollsBack}`; prepared statements pendentes |
| `web.app()` + TLS `listenSecure` | ✅ | WEB002 | WEB001 | `KofWebTlsTest` |
| `web.app()` WebSocket `app.ws` + SSE `sse.*` | ✅ 30/08 (RFC 6455 + frame codec/máscara) | WEB002 | WEB001 | `KofWebWsE2ETest` 11/11 · `KofWebSseE2ETest` 7/7 · `KofWsFrameTest` 7/7 |
| `status(code, body)` / `headerSet` | ✅ 27/08 | WEB002 | WEB001 | `KofWebE2ETest` 9/9 |
| UI Fase 7 Router (`go/replace/back/forward/param/current/depth`) | no-op | — | ✅ 31/08 | `RouterE2ETest` |
| `switch` pattern matching `case String s` | ✅ | ✅ | ✅ | 0.2.6-beta |
| `switch` record destructuring `Point(x,y)` | ✅ | ✅ | ✅ | 0.2.6-beta |
| `String?` null safety básica | ✅ | ✅ | ✅ | 0.2.6-beta (`Type?`) |
| `List map/filter/reduce` | ✅ | ✅ | ✅ | 0.2.6-beta |
| `Box<T>` generic | ✅ | ✅ | ✅ | `substituteTypeVariable` |
| `KofScript` top-level `let` → `KofScriptGlobals` | ✅ | ✅ | ✅ | `KofScript` 0.2.0 |
| `KofCcompiler` (`kof c`) C subset | — | ✅ x86_64 native-only | — | `kof_c`, while/if/deref &/* |
| `native.risc` (riscv64) / `native.arm` (aarch64) | — | **em desenvolvimento**: plumbing pronto / codegen stub — `docs/native-multiarch.md` | — | target separation 0.2.0 |
| `kof fmt` (formatter parser real, idempotente) | ✅ 31/08 | ✅ | ✅ | `KofFormatter` (2c3e794) |
| **KofJS no browser real** (`kof.ui` renderizando DOM via ES Modules) | — | — | ✅ 01/09 (`KofJsBrowserE2ETest` — Chrome headless + HTTP + captura de DOM; pula se Chrome ausente) | ESM via HTTP local (módulos não carregam via `file://`); `KofJsRunner` serve `appDir` em `127.0.0.1` |
| Android (Fase 1: `kof build --target android` → projeto Maven + APK, host Activity em Kof) | ✅ (bytecode JVM) | — | — | gaps `AND00x` em compile-time |

## Gaps documentados (não mascarados)

| Gap | Diagnostic | Status |
|-----|-----------|--------|
| spawn/await no Native | ✅ 31/08 (CONC001 fechado — pthread_create + trampoline + pthread_join + allocator thread-safe futex; join implícito) | |
| spawn/await no JS | ✅ sequencial (stmt + spawn-expr + await/poll/cancel/selectAny) | event-loop async real é `CONC003` (evolução futura) |
| web no Native/JS (server, TLS, ws/sse) | `WEB002` / `WEB001` | planned |
| kof.http no Native | `HTTP002` | planned (JVM+JS ✅, retry/circuit 30/08) |
| kof.db/orm no JS | `DB001` / `ORM001` | planned |
| JSON Float/Double | ✅ 31/08 (JSN001 fechado — XMM + parser fração/expoente) | |
| JSON objetos/records no Native | ✅ 31/08 (JSN002 fechado — composição em compile-time) | |
| GC mark-sweep no Native | — | pendente; auto-GC desativado após hang (memória devolvida só no `munmap` fallback) |
| MySQL nativo completo | — | WIP: **wire protocol ✅** (handshake + scramble SHA-1 + auth-switch `mysql_native_password` + COM_QUERY + resultset coldefs/rows/EOF + binds `?` client-side — `nativeMysqlWireProtocol`, 31/08); falta **prepared statements** binários (COM_STMT_PREPARE/EXECUTE — tentativa de 01/09 revertida; binds `?` já cobrem o uso funcional) |
| Native riscv64/aarch64 codegen | `NATIVE002` | **riscv64 parcial (02/09)**: `NativeRiscv64E2ETest 4/4` — `kof_main` + runtime em **asm puro** (raw syscalls, **sem C**; `as`+`ld` estático) + qemu: println String/Int, var, if/else, aritmética/comparações Int. aarch64 ainda stub; coleções/classe/`instanceof`/`switch` cross pendentes. **Ver `docs/native-multiarch.md`** (estado real + como finalizar) |

<<<<<<< HEAD
Fechados em 0.2.6-beta (01/09): `spawn` com lambda que captura variável externa (JVM+Native), `&&`/`||` booleanos com short-circuit no JS (bitwise intacto), `Channel<T>` como parâmetro de função (JVM/Native/JS), `println`/`print` antes de `spawn` e `spawn→await→spawn` no Native sem SIGSEGV (alinhamento de stack no `pthread_create`), KofJS `kof.ui` renderizando em browser real (Chrome headless E2E), LSP `references`+`rename`, tracing W3C `traceId`/`spanId` (3 targets), `moduleRoot` por LCA (P1-4), validação tipada de coluna no ORM (P3-10, `ORM003`), **AES-256-GCM no KofJS (`SECN002`)** — AES-GCM puro JS com paridade cross-target JVM↔JS e tamper detection por base64 estrito, **histogram/metrics no Native (`OBS002`)** — store asm + export Prometheus em paridade de conteúdo com o JVM, **`time.interval`/`time.cancel` no Native (`TIME001`)** — reusa o scheduler (SCHED001) via alias `jmp`, mutação por referência validada, **`transaction {}` no Native** — `kof_db_transaction` em asm: BEGIN/COMMIT/ROLLBACK via `kf_db_execute`, lambda via vtable (`rdi`=this p/ capturas) e ROLLBACK+re-throw no EH (`kf_throw_string` chega no handler com `%rdi` e a chain p/ o try externo), conexão default = última aberta — `nativeTransactionCommits`/`nativeTransactionRollsBackOnFailure`, **`kof.mq` no Native (`MQ001`)** — pub/sub + filas in-process em asm (store `.bss` + nodes 40B, invoke-com-arg, paridade de output com JVM/JS), **`Set<T>`/`Map<K,V>` como campo/retorno de classe no JVM** — mapper `Set`→`HashSet`/`Map`→`HashMap` + parse de método de classe c/ retorno genérico (`KofMapSetTest.setMapAsFieldAndReturn`, 3 targets), **source map V3 do KofJS** — mappings VLQ em nível de linha (função gerada → linha Kof; `KofJsSourceMapTest`).
=======
Fechados em 0.2.6-beta (01/09): `kof.log` no JS (LOG001 — console.* + `KOF_LOG_LEVEL`), bloco Vulkan no runtime JVM condicional ao uso de `kof.vk` (capability/link-por-uso, R2), `--enable-preview` só para programas Vulkan (FFM preview API no JDK 21).
>>>>>>> 0d45a90 (feat(js): close LOG001 (kof.log on JS) + runtime fixes)
Fechados em 0.2.6-beta (30-31/08): `spawn` Native (CONC001 — pthread), FP XMM (FLT001), JSON completo no Native (JSN001/JSN002/JSN003 — objetos/records + arrays incl. Double/Float), WebSocket/SSE JVM (RFC 6455), `kof.http` retry/circuit JVM+JS (30s window, fail-fast), `kof.cache` 3 targets (fix de clobber de registradores), SQLite nativo `.so` direto, UI Fase 7 Router (JS), `kof fmt` + `kof config gen`.
Fechados em 0.2.6-beta (27/08): pattern matching `switch case String s` + record `Point(x,y)` (JVM/Native/JS), `String?` null safety básica, `kof.http` no JS via `Java HttpClient`, `List map/filter/reduce`, large-project `import a.b.C` (`CompilerDriver.java:243`), `List.get`/`listOf`, free-list GC (`kof_free_head`), Windows SIGPIPE.
Fechados em 0.1.0: Map/Set nativo (era COL001), await com unboxing, captura em lambdas (BoxN), resultado de tarefa (`await`).

## Princípio

A semântica Kof é a mesma em todos os targets. Onde um target não suporta
uma feature ainda, o compilador emite um diagnostic explícito — nunca código
que funciona de forma diferente ou quebra silenciosamente.

---

## Convenção de gaps por domínio (visão universal)

Todo gap de capacidade tem um **código de diagnóstico** documentado aqui e
emitido em compile-time. Domínios novos seguem o mesmo padrão dos existentes
(`SECN00x`, `CONC003`, `DB001`, `HTTP002`, ...):

| Prefixo | Domínio | Exemplos |
|---------|---------|----------|
| `HTTP`/`WEB`/`DB`/`ORM`/`MQ`/`SCHED`/`TIME`/`CONC`/`SECN` | Sistemas atuais | `HTTP002`, `WEB001`, `DB001`, `MQ001`, `SCHED001`, `CONC003`, `SECN002` |
| `AND` | Android | `AND001..004` |
| `NATIVE` | codegen multiarch | `NATIVE002` |
| `INFRA` | infraestrutura / IaC | `INFRA00x` |
| `DATA` | data engineering / dataframe / ML | `DATA00x` |
| `SCI` | scientific computing / HPC | `SCI00x` |
| `BIO` | bioinformática | `BIO00x` |
| `SECPQ` | criptografia pós-quântica | `SECPQ` (gap de target, nunca stub) |

Regra (R6): gap de domínio sempre tem **código + entrada nesta matriz** —
nunca stub silencioso, nunca fallback fraco, nunca "paridade parcial" sem
diagnóstico.

## Tiers de estabilidade (R5)

Cada namespace/pacote carrega um tier:

- **`stable`** — garantia de compatibilidade; promove somente com DoD completo
  (3 targets ou gap diagnosticado, E2E por target, benchmark quando plausível,
  docs + `training/` sincronizadas, suíte verde).
- **`experimental`** — pode mudar; a camada de **pacotes oficiais** nasce
  `experimental`.

Domínios pesados (`ml`, `bio`, `hpc`, `infra-<cloud>`) são **pacotes
oficiais** (camada 4), **nunca** stdlib base (R1).