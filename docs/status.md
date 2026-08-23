# Status do Projeto Kof

**Última atualização:** 23 de agosto de 2026
**Versão:** 0.0.5-alpha

---

## Build

```
mvn clean package    → PASSA
mvn test             → 416 testes (JVM + Native + KofJS E2E)
kof build            → PASS (--target jvm|native|js) [--release]
kof run              → PASS (jvm|native|js) [--release]
kof serve            → PASS (KofHttpServer, thread pool, 404/500, JSON)
kof check            → PASS
kof test             → PASS (PASS/FAIL por exit code com assert)
kof bench            → PASS (harness: compile, run, validate, métricas, baseline)
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
- **Estrutura `benchmarks/`**: 33 benchmarks em 16 categorias (micro,
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
| herança, `super`, override, virtual dispatch | ✅ | ✅ | ✅ |
| interfaces | ✅ | ✅ | ✅ |
| generics por erasure | ✅ | ✅ | ✅ |
| lambdas `(x: Int) -> expr` (sem capturas) | ✅ | ✅ | ✅ |
| exceptions reais (try/catch/finally + unwinding) | ✅ | ✅ | ✅ |
| `assert(cond[, msg])` | ✅ | ✅ | ✅ |
| `spawn` (concorrência, join implícito) | ✅ | CONC001 | — |
| strings (concat `+`, `==`, indexOf, trim, split...) | ✅ | ✅ | ✅ |
| arrays | ✅ | ✅ | ✅ |
| `List<T>`, `listOf` | ✅ | ✅ | ✅ |
| JSON encode/decode (objetos/records no JVM) | ✅ | ✅ | ✅ |
| kof.io (File/Path/Directory, readFile, writeFile) | ✅ | ✅ | ✅ |
| kof.time (`now()`) | ✅ | ✅ | ✅ |
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

```kof
handle(String method, String path, String body, String query, String headers): String {
    if (path == "/hello") {
        return "{\"msg\": \"hi\"}"
    }
    return null   // 404
}
```

- KofHttpServer: Content-Length-aware, query string, headers, JSON detection,
  thread pool, graceful shutdown.
- Handlers top-level (static): variantes 5/4/3/0 args + `get()`/`post()`...
- Ver: `docs/http.md`.

### Testes da linguagem

```kof
main() {
    assert(2 + 2 == 4)
    assert("kof" == "kof", "strings iguais")
}
```

- `assert` lança quando falso → exit code 1.
- `kof test <file.kf|dir> [--target jvm|native]` reporta PASS/FAIL.
- Ver: `learn/23-testing.md`.

---

## Testes (416/416 PASS)

| Suíte | Quantidade | Cobertura |
|-------|-----------|-----------|
| CompilerDriverTest | 190 | compilação, semântica, fases, isolamento |
| OptimizerTest | 20 | passes de otimização da IR |
| NativeE2ETest | 50 | execução real de binários nativos |
| KofJsE2ETest | 35 | execução real JS (GraalJS) |
| JvmE2ETest | 29 | execução real de bytecode JVM |
| IoE2ETest | 15 | kof.io multiplatform |
| JsonE2ETest | 14 | JSON JVM + Native |
| BackendParityTest | 10 | paridade JVM/Native/JS |
| ExceptionsE2ETest | 9 | try/catch/finally JVM + Native |
| KofHttpServerTest | 8 | serve engine (sockets reais) |
| AssertE2ETest | 5 | assert JVM + Native |
| FunctionSyntaxTest | 4 | formas de declaração de função |
| LambdaE2ETest | 4 | lambdas + if-expr |
| StdlibE2ETest | 4 | now/readFile/writeFile |
| SpawnE2ETest | 3 | spawn (JVM) + CONC001 |
| IdiomaticE2ETest | 7 | idiomas consolidados (chaining, primary ctor) |
| IdiomaticCoreE2ETest | 6 | field initializers, \\uXXXX, listOf<T>() |
| NativeDebugTest* | 5 | harnesses de debug |
| DebugInfoE2ETest | 2 | SourceFile + LineNumberTable (JVM) |
| **Total** | **416** | |

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
| args CLI (`main(args)`) | planejado |
| módulos multi-arquivo | planejado |
| `Process` API | planejado |

Ver as guidelines completas no todo da sessão.

---

## Kof Debugger (em progresso)

Princípio: o programador depura **código Kof**, nunca o artefato do backend.

| Fase | Estado |
|------|--------|
| 1 — DebugInfo na IR (source location por op) | ✅ |
| 2 — JVM: SourceFile + LineNumberTable + LocalVariableTable | ✅ |
| 3 — `kof-debug` (DAP: launch, breakpoints, stack, stepping) | planejado |
| 4 — Kof Editor (breakpoints, toolbar, variables) | planejado |
| 5 — Native (DWARF) | planejado |
| 6 — JS (source maps) | planejado |

Docs: `debugger-architecture.md`, `debugging.md`, `debug-adapter.md`,
`debugging-jvm.md`, `debugging-native.md`, `debugging-js.md`.

---

## Bugs Restantes (reais)

1. GC no Native (memória devolvida ao SO no exit)
2. `spawn` no Native: CONC001 (gap documentado)
3. JSON de objetos/records no Native: JSN002 (gap documentado)
4. JSON Float/Double: JSN001 (gap documentado)
5. Lambdas sem captura (planned)
6. Resultado de tarefa (`await`/join explícito): planned
7. `kof fmt`: planned
8. Map/Set, Option/null safety, pattern matching: planned

---

## Próximos Passos

- kof.test como stdlib completa (assert existe; evoluir para suite estruturada)
- Resultado observável de tarefas (`await`), filas (`kof.concurrent.Queue`)
- Scheduler nativo para `spawn`
- `kof fmt`, hover/completion no LSP
- Roadmap: `docs/roadmap.md`

---

## Roadmap de Longo Prazo

- `docs/roadmap.md` com a visão completa.
- Próximo grande marco: **aplicação web completa em Kof** (frontend via KofJS,
  backend via kof serve, banco, auth, JSON — lógica de negócio em poucos
  arquivos).