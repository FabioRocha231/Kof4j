# Status do Projeto Kof

**Última atualização:** 22 de agosto de 2026
**Versão:** 0.0.5-alpha

---

## Build

```
mvn clean package    → PASSA
mvn test             → 394 testes (JVM + Native + KofJS E2E)
kof build            → PASS (--target jvm|native|js)
kof run              → PASS (jvm|native|js)
kof serve            → PASS (KofHttpServer, thread pool, 404/500, JSON)
kof check            → PASS
kof test             → PASS (PASS/FAIL por exit code com assert)
kof info             → PASS
kof lsp              → PASS (diagnostics reais do frontend)
kof install          → PASS
tests/run-golden.sh  → 16/16 (8 casos × jvm+native)
tests/run-integration.sh → 9/9 (CLI + serve + kof test)
scripts/package.sh   → PASS (layout dist + tar.gz + SHA256SUMS)
```

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

## Testes (381/381 PASS)

| Suíte | Quantidade | Cobertura |
|-------|-----------|-----------|
| CompilerDriverTest | 190 | compilação, semântica, fases, isolamento |
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
| NativeDebugTest | 1 | harness de debug |
| **Total** | **394** | |

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