# Backend Parity — Kof JVM × Native × KofJS

**Última atualização:** 25 de agosto de 2026
**Versão:** 0.1.0

> Deltas desde 0.0.5: `kof.db` SQLite **nativo** + MySQL WIP; `kof.orm`/MongoDB JVM-only (ORM001); `kof.security` Native completo (PBKDF2/SHA512/JWT/AES-GCM asm, G10), G9 rateLimit/session/apiKey 3 targets, `kof.config`/`kof.log` asm Native, TLS `listenSecure` JVM, `kof.validation`/`kof.observability`/`kof.http`/`kof.mq` 3 targets, generics `Box<T>` fix 25/08, `SEM025` Object fix.
> Tabela reflete 0.1.0 (25/08) — DoD em `docs/plan-platform-completion.md`.

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
| `spawn` stmt / `val r = spawn f()` / `await r` (Handle<T> + unbox) | ✅ | CONC001 | CONC003 | `KofAwaitTest` 4/4 |
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
| `kof.http` client | ✅ | HTTP002 | HTTP002 | `KofHttpE2ETest` |
| `kof.mq` (pub/sub + queue) | ✅ | MQ001 | MQ001 | `KofMqE2ETest` |
| `kof.config` (typed) | ✅ | ✅ | CONF001 | asm Native |
| `kof.log` | ✅ | ✅ | LOG001 | asm Native |
| `kof.security` (passwords/crypto/jwt/secrets + G9) | ✅ | ✅ | ✅ | PBKDF2/SHA512/JWT/AES-GCM asm |
| `kof.db`/`kof.orm` | ✅ | ✅/ORM001 | DB001/ORM001 | SQLite nativo; MongoDB JVM |
| `web.app()` + TLS `listenSecure` | ✅ | WEB002 | WEB001 | `KofWebTlsTest` |

## Gaps documentados (não mascarados)

| Gap | Diagnostic | Status |
|-----|-----------|--------|
| spawn/await no Native | `CONC001` | planned (virtual threads é JVM-only) |
| spawn/await no JS | `CONC003` | planned (modelo event-loop) |
| web TLS no Native/JS | `WEB002` | planned |
| kof.http no Native/JS | `HTTP002` | planned |
| JSON Float/Double | `JSN001` | planned |

Fechados em 0.1.0: Map/Set nativo (era COL001), await com unboxing,
JSN002 (objetos no Native), captura em lambdas (BoxN), resultado de
tarefa (`await`).

## Princípio

A semântica Kof é a mesma em todos os targets. Onde um target não suporta
uma feature ainda, o compilador emite um diagnostic explícito — nunca código
que funciona de forma diferente ou quebra silenciosamente.