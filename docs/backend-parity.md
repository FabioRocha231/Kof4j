# Backend Parity — Kof JVM × Native × KofJS

**Última atualização:** 27 de agosto de 2026
**Versão:** 0.2.6-beta

> Deltas desde 0.1.0: Targets `native.riscv64` (riscv64 via `riscv64-linux-gnu-as`, `.option arch,rv64g`, `li a7 214/64/93`) e `native.aarch64` (placeholder) separados de `native` x86_64; Native free-list (`kof_free_head`) + `kof_gc_collect`; MySQL handshake via `kof_db_mysql_scramble`; pattern matching `switch case String s` + record destructuring `Point(x,y)` em JVM/Native/JS; `String?` null safety básica; `KofScript` top-level `let` → `KofScriptGlobals`; `KofCcompiler` (`kof c`) native-only C subset; `kof.http` JVM+JS (GraalJS via `Java HttpClient` interop); `List map/filter/reduce` + `Box<T>`; bugs: large-project `import a.b.C` file handling (`CompilerDriver.java:243`), `List.get`/`listOf`, release.yml single job + JDK 21, Windows SIGPIPE.
> Tabela reflete 0.2.6-beta (27/08) — Build `mvn test` 658 (650+8+5), golden 16/16, integration 9/9. DoD em `docs/plan-platform-completion.md`.

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
| `spawn` stmt / `spawn f()` / `await` / `poll` / `done` / `cancel`+`cancelled` / `selectAny` (Handle<T>, unbox, exceção limpa) | ✅ | ✅ 31/08 (pthread) | ✅ sequencial | `KofAwaitTest` 7/7 · `KofConcurrency2Test` 5/5 · `SpawnE2ETest` 4/4 | `KofAwaitTest` 7/7 · `KofConcurrency2Test` 5/5 |
| Strings (`+`, `==`, length, charAt, substring, contains, startsWith, endsWith, indexOf, trim, case, replace, split) | ✅ | ✅ | ✅ | |
| Arrays (`new Int[n]`, `arr[i]`, `.length`) | ✅ | ✅ | ✅ | |
| `List<T>`, `listOf`, for-in | ✅ | ✅ | ✅ | |
| `Map<K,V>` + `mapOf` (put/get/remove/contains/size/keys/values/clear/isEmpty) | ✅ HashMap | ✅ asm próprio | ✅ JS Map | `KofMapSetTest` |
| `Set<T>` + `setOf` (add/contains/remove/size/clear/isEmpty) | ✅ HashSet | ✅ asm sobre List | ✅ JS Set | tag de tipo no Native |
| `enum` + values/valueOf/name + switch exaustivo (`SEM031`) | ✅ | ✅ | ✅ | enum→String nos descritores; `KofEnumSwitchTest` |
| JSON encode/decode (primitivos, List) | ✅ | ✅ | ✅ | arrays `JSN003` fechado |
| JSON objetos/records (JSN002) | ✅ | ✅ | ✅ | composição em compile-time no Native |
| kof.io (File, Path, Directory) | ✅ | ✅ | ✅ | |
| kof.time (`now()`, `sleep`, `interval`) | ✅ | ✅ | ✅ | `interval` JVM; TIME001 Native/JS |
| `readLine`, `readFile`, `writeFile` | ✅ | ✅ | ✅ | |
| `kof.validation` (13 preds) | ✅ | ✅ | ✅ | `KofValidationTest` |
| `kof.observability` (health/metrics/requestId) | ✅ | ✅ | ✅ | `KofObservabilityTest` |
| `kof.http` client | ✅ | HTTP002 | ✅ (GraalJS via `Java HttpClient` interop) | `KofHttpE2ETest` 4/4 (JVM+JS) |
| `kof.mq` (pub/sub + queue) | ✅ | MQ001 | MQ001 | `KofMqE2ETest` |
| `kof.config` (typed) | ✅ | ✅ | CONF001 | asm Native (free-list) |
| `kof.log` | ✅ | ✅ | LOG001 | asm Native |
| `kof.security` (passwords/crypto/jwt/secrets + G9) | ✅ | ✅ | ✅ | PBKDF2/SHA512/JWT/AES-GCM asm |
| `kof.db`/`kof.orm` | ✅ | ✅/ORM001 | DB001/ORM001 | SQLite nativo; MySQL `kof_db_mysql_scramble` |
| `web.app()` + TLS `listenSecure` | ✅ | WEB002 | WEB001 | `KofWebTlsTest` |
| `switch` pattern matching `case String s` | ✅ | ✅ | ✅ | 0.2.6-beta |
| `switch` record destructuring `Point(x,y)` | ✅ | ✅ | ✅ | 0.2.6-beta |
| `String?` null safety básica | ✅ | ✅ | ✅ | 0.2.6-beta (`Type?`) |
| `List map/filter/reduce` | ✅ | ✅ | ✅ | 0.2.6-beta |
| `Box<T>` generic | ✅ | ✅ | ✅ | `substituteTypeVariable` |
| `KofScript` top-level `let` → `KofScriptGlobals` | ✅ | ✅ | ✅ | `KofScript` 0.2.0 |
| `KofCcompiler` (`kof c`) C subset | — | ✅ x86_64 native-only | — | `kof_c`, while/if/deref &/* |
| `native.riscv64` / `native.aarch64` | — | riscv64 stable / aarch64 placeholder | — | 0.2.0 target separation |

## Gaps documentados (não mascarados)

| Gap | Diagnostic | Status |
|-----|-----------|--------|
| spawn/await no Native | ✅ 31/08 (CONC001 fechado — pthread + allocator thread-safe; join implícito) | |
| spawn/await no JS | ✅ sequencial (stmt + spawn-expr + await/poll/cancel/selectAny) | event-loop async real é evolução futura |
| web TLS no Native/JS | `WEB002` | planned |
| kof.http no Native | `HTTP002` | planned (JS now ✅) |
| JSON Float/Double | ✅ 31/08 (JSN001 fechado) | |
| Native aarch64 codegen | `NATIVE002` | placeholder (target separation done) |

Fechados em 0.2.6-beta: pattern matching `switch case String s` + record `Point(x,y)` (JVM/Native/JS), `String?` null safety básica, `kof.http` no JS via `Java HttpClient`, `List map/filter/reduce`, large-project `import a.b.C` (`CompilerDriver.java:243`), `List.get`/`listOf`, free-list GC (`kof_free_head` + `kof_gc_collect`), release.yml single job + JDK 21, Windows SIGPIPE.
Fechados em 0.1.0: Map/Set nativo (era COL001), await com unboxing, JSN002 (objetos no Native), captura em lambdas (BoxN), resultado de tarefa (`await`).

## Princípio

A semântica Kof é a mesma em todos os targets. Onde um target não suporta
uma feature ainda, o compilador emite um diagnostic explícito — nunca código
que funciona de forma diferente ou quebra silenciosamente.