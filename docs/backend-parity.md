# Backend Parity — Kof JVM × Native × KofJS

**Última atualização:** 31 de agosto de 2026
**Versão:** 0.2.6-beta

> Deltas desde 0.1.0: Targets `native.risc` (riscv64) e `native.arm` (aarch64) separados de `native` x86_64 (toolchain + qemu; codegen ainda x86_64 placeholder); Native free-list (`kof_free_head`) + `kof_gc_collect` (mark-sweep pendente; auto-GC desativado); MySQL wire protocol em progresso (`kof_db_mysql_scramble` + `user:pass@`); pattern matching `switch case String s` + record destructuring `Point(x,y)` em JVM/Native/JS; `String?` null safety básica; `KofScript` top-level `let` → `KofScriptGlobals`; `KofCcompiler` (`kof c`) native-only C subset; `List map/filter/reduce` + `Box<T>`; Windows SIGPIPE fix.
> Deltas 30-31/08: `spawn`/`await` real no Native (pthread + trampoline + join + allocator thread-safe futex — CONC001); FP real em XMM (FLT001); JSON objetos/records + arrays FP no Native (JSN001/JSN002/JSN003); WebSocket/SSE no JVM (`app.ws`/`sse.*`, RFC 6455); `kof.http` retry/circuit JVM+JS (30s window, fail-fast); `kof.cache` 3 targets (fix de clobber de registradores); UI Fase 7 Router (JS real, JVM no-op); SQLite nativo via `.so` direto; `kof fmt` + `kof config gen`.
> Tabela reflete 0.2.6-beta (31/08) — Build `mvn test` 658 (650+8+5), golden 16/16, integration 9/9. DoD em `docs/plan-platform-completion.md`.

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
| `spawn` stmt / `spawn f()` / `await` / `poll` / `done` / `cancel`+`cancelled` / `selectAny` (Handle<T>, unbox, exceção limpa) | ✅ (virtual threads) | ✅ 31/08 (pthread_create + trampoline + pthread_join, allocator futex — CONC001) | ✅ sequencial (async real = CONC003) | `KofAwaitTest` 7/7 · `KofConcurrency2Test` 5/5 · `SpawnE2ETest` 3/3 |
| Strings (`+`, `==`, length, charAt, substring, contains, startsWith, endsWith, indexOf, trim, case, replace, split) | ✅ | ✅ | ✅ | |
| Arrays (`new Int[n]`, `arr[i]`, `.length`) | ✅ | ✅ | ✅ | |
| `List<T>`, `listOf`, for-in | ✅ | ✅ | ✅ | |
| `Map<K,V>` + `mapOf` (put/get/remove/contains/size/keys/values/clear/isEmpty) | ✅ HashMap | ✅ asm próprio | ✅ JS Map | `KofMapSetTest` |
| `Set<T>` + `setOf` (add/contains/remove/size/clear/isEmpty) | ✅ HashSet | ✅ asm sobre List | ✅ JS Set | tag de tipo no Native |
| `enum` + values/valueOf/name + switch exaustivo (`SEM031`) | ✅ | ✅ | ✅ | enum→String nos descritores; `KofEnumSwitchTest` |
| JSON encode/decode (primitivos, List) | ✅ | ✅ | ✅ | arrays `JSN003` fechado; `Double[]`/`Float[]` no Native (JSN001, 31/08) |
| JSON objetos/records (JSN002) | ✅ | ✅ 31/08 | ✅ | composição em compile-time no Native |
| kof.io (File, Path, Directory) | ✅ | ✅ | ✅ | |
| kof.time (`now()`, `sleep`, `interval`) | ✅ | ✅ (now/sleep) | ✅ (now/sleep) | `interval`/`every` JVM (ScheduledExecutor) + JS (`setInterval`, 27/08); Native SCHED001 |
| `readLine`, `readFile`, `writeFile` | ✅ | ✅ | ✅ | |
| `kof.validation` (13 preds) | ✅ | ✅ | ✅ | `KofValidationTest` |
| `kof.observability` (health/metrics/requestId) | ✅ | ✅ | ✅ | `KofObservabilityTest` |
| `kof.http` client | ✅ | HTTP002 | ✅ (GraalJS via `Java HttpClient` interop + fetch fallback) | `KofHttpE2ETest` 4/4 (JVM+JS); retry/circuit paridade 30/08 (`KofHttpResilienceE2ETest` 3/3) |
| `kof.cache` (get/set/ttl/delete/clear) | ✅ | ✅ 30/08 (fix clobber `%rax`/`%rdi`) | ✅ | `KofCacheE2ETest` 5/5 x3 targets |
| `kof.mq` (pub/sub + queue) | ✅ | MQ001 | ✅ | `KofMqE2ETest` |
| `kof.config` (typed) | ✅ | ✅ (asm próprio) | CONF001 | precedência total Native (`KOF_CONFIG` > env > profile > `kof.config`); `NativeConfigE2ETest` 8 |
| `kof.log` | ✅ (JSON + correlation ID) | ✅ (asm; UTC, sem JSON) | LOG001 | `KofLogE2ETest` 10 + `NativeLogE2ETest` 7 |
| `kof.security` (passwords/crypto/jwt/secrets + G9) | ✅ | ✅ | ✅ | PBKDF2/SHA512/JWT/AES-GCM asm |
| `kof.db`/`kof.orm` | ✅ | ✅ (SQLite `.so` direto)/ORM001 | DB001/ORM001 | MySQL wire protocol WIP (scramble SHA-1 + `user:pass@`, 31/08) |
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
| `native.risc` (riscv64) / `native.arm` (aarch64) | — | toolchain + qemu / codegen x86_64 placeholder | — | target separation 0.2.0 |
| `kof fmt` (formatter parser real, idempotente) | ✅ 31/08 | ✅ | ✅ | `KofFormatter` (2c3e794) |
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
| MySQL nativo completo | — | WIP: auth scramble SHA-1 + parse `user:pass@`; falta handshake completo, query e prepared |
| Native riscv64/aarch64 codegen | `NATIVE002` | toolchain + qemu prontos; codegen ainda x86_64 placeholder |

Fechados em 0.2.6-beta (30-31/08): `spawn` Native (CONC001 — pthread), FP XMM (FLT001), JSON completo no Native (JSN001/JSN002/JSN003 — objetos/records + arrays incl. Double/Float), WebSocket/SSE JVM (RFC 6455), `kof.http` retry/circuit JVM+JS (30s window, fail-fast), `kof.cache` 3 targets (fix de clobber de registradores), SQLite nativo `.so` direto, UI Fase 7 Router (JS), `kof fmt` + `kof config gen`.
Fechados em 0.2.6-beta (27/08): pattern matching `switch case String s` + record `Point(x,y)` (JVM/Native/JS), `String?` null safety básica, `kof.http` no JS via `Java HttpClient`, `List map/filter/reduce`, large-project `import a.b.C` (`CompilerDriver.java:243`), `List.get`/`listOf`, free-list GC (`kof_free_head`), Windows SIGPIPE.
Fechados em 0.1.0: Map/Set nativo (era COL001), await com unboxing, captura em lambdas (BoxN), resultado de tarefa (`await`).

## Princípio

A semântica Kof é a mesma em todos os targets. Onde um target não suporta
uma feature ainda, o compilador emite um diagnostic explícito — nunca código
que funciona de forma diferente ou quebra silenciosamente.