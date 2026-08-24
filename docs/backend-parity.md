# Backend Parity — Kof JVM × Native × KofJS

**Última atualização:** 24 de agosto de 2026
**Versão:** 0.0.14-alpha

> Deltas desde 0.0.5: `kof.db` agora tem SQLite **nativo** (link direto da
> `.so`) e MySQL por wire protocol (WIP); `kof.orm`/MongoDB são JVM-only
> (ORM001 fora dele); logging estruturado no JVM; UI renderizada via KofJS.
> A tabela abaixo reflete a base 0.0.5 — sincronizar célula a célula é
> parte do DoD de cada fase do plano (`docs/plan-platform-completion.md`).

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
| Herança, `super`, override | ✅ | ✅ | ✅ | |
| Virtual dispatch | ✅ | ✅ | ✅ | |
| Interfaces | ✅ | ✅ | ✅ | |
| Generics (erasure) | ✅ | ✅ | ✅ | |
| Lambdas `(x: Int) -> expr` | ✅ | ✅ | ✅ | sem capturas |
| Exceptions (throw "msg", try/catch/finally) | ✅ | ✅ | ✅ | Native: unwinding próprio |
| `assert(cond[, msg])` | ✅ | ✅ | ✅ | |
| `spawn` | ✅ | CONC001 | — | gap documentado |
| Strings (`+`, `==`, length, charAt, substring, contains, startsWith, endsWith, indexOf, trim, case, replace, split) | ✅ | ✅ | ✅ | |
| Arrays (`new Int[n]`, `arr[i]`, `.length`) | ✅ | ✅ | ✅ | |
| `List<T>`, `listOf`, for-in | ✅ | ✅ | ✅ | |
| JSON encode/decode (primitivos, List) | ✅ | ✅ | ✅ | |
| JSON objetos/records | ✅ | JSN002 | ✅ | gap no Native documentado |
| kof.io (File, Path, Directory) | ✅ | ✅ | ✅ | |
| kof.time (`now()`) | ✅ | ✅ | ✅ | |
| `readLine`, `readFile`, `writeFile` | ✅ | ✅ | ✅ | |

## Gaps documentados (não mascarados)

| Gap | Diagnostic | Status |
|-----|-----------|--------|
| `spawn` no Native | `CONC001` | planned |
| JSON Float/Double | `JSN001` | planned |
| JSON objetos/records no Native | `JSN002` | planned (JVM/JS ok) |
| decode de arrays | `JSN003` | planned |
| captura em lambdas | — | planned |
| resultado de tarefa (`await`) | — | planned |

## Princípio

A semântica Kof é a mesma em todos os targets. Onde um target não suporta
uma feature ainda, o compilador emite um diagnostic explícito — nunca código
que funciona de forma diferente ou quebra silenciosamente.