# Status do Projeto Kof

**Última atualização:** 22 de agosto de 2026
**Versão:** 0.0.4-alpha

---

## Build

```
mvn clean package → PASSA
mvn test → 292/292 PASSAM (JUnit, inclui execução real JVM + Native)
kof run → FUNCIONA
kof build → FUNCIONA (--target jvm|native)
kof serve → FUNCIONA (handlers top-level static, JSON, routing por ==)
kof check → FUNCIONA
kof info → FUNCIONA
kof lsp → FUNCIONA (diagnostics via frontend real)
scripts/package.sh → gera pacote oficial + SHA256SUMS
```

---

## Infraestrutura 0.0.4 (distribuição)

- `VERSION` como fonte única de versão; `<revision>` no Maven; `KofVersion`
  lendo `dev/kof/version.properties` empacotado; `scripts/bump-version.sh`.
- CLI: `kof info [--json]`, `kof check`, `kof version` por fonte única.
- `kof lsp` — Language Server mínimo consumindo o frontend real.
- Launchers `bin/kof` (Unix) e `bin/kof.bat` (Windows) com JDK embutido
  (Temurin 21, Tooling API Level 21).
- `scripts/package.sh` — layout oficial de distribuição, `--jdk` para JDK
  embutido, SHA256SUMS.
- GitHub Actions: `ci.yml` (PR) e `release.yml` (main → teste → bump →
  package 3 plataformas → changelog → GitHub Release).
- Editor support: `editor/kof.tmLanguage.json` (grammar TextMate oficial).
- Maven compilador com `compilerReuseStrategy=alwaysNew` (estabilidade sob JDK 25).

---

## Estado da Linguagem

### Sintaxe de funções (sem `fun`)

```kof
main() { ... }                       // entry point, void implícito
String saudacao() { ... }            // retorno antes do nome
despedida(): String { ... }          // retorno após os parâmetros
void fazIsso() { ... }               // void explícito
Bool positivo(Int x) = x > 0         // expression body
int dobro(int x) { ... }             // primitivos em qualquer caixa
class Calc {
    Int value                        // campo
    void reset() { ... }             // método void
    Int getValue() { ... }           // método com retorno
    constructor(String nome) { ... } // construtor por palavra-chave
}
```

### Features implementadas

| Feature | JVM | Native |
|---------|-----|--------|
| println / print | ✅ | ✅ |
| variáveis, aritmética, bitwise | ✅ | ✅ |
| if/else, while, for, do-while, break/continue | ✅ | ✅ |
| switch | ✅ | ✅ |
| funções (todas as formas de declaração) | ✅ | ✅ |
| classes, campos, métodos, construtores | ✅ | ✅ |
| `constructor` por palavra-chave | ✅ | ✅ |
| records (com toString/equals/hashCode no JVM) | ✅ | ✅ |
| herança, `super`, virtual dispatch, override | ✅ | ✅ |
| interfaces + dispatch por interface | ✅ | ✅ |
| generics por erasure (classes e funções) | ✅ | ✅ |
| instanceof, cast (`as`) | ✅ | ✅ |
| exceptions reais (try/catch/finally + exception table) | ✅ | ⚠️¹ |
| strings (concat `+`, equals `==`, indexOf, trim, case, replace, split) | ✅ | ✅ |
| arrays (`new Int[n]`, acesso, `.length`) | ✅ | ✅ |
| `List<T>` (add, get, set, size, contains, isEmpty, remove, clear, listOf) | ✅ | ✅ |
| JSON encode/decode (int, long, bool, string, list, array) | ✅ | ✅ |
| JSON objetos/records (encode + decode) | ✅ | — |
| literais `Int` estourados viram `Long` automaticamente | ✅ | ✅ |

¹ Native: `throw` encerra o processo (`kof_panic`); try/catch/finally compilam, mas exceções não são recuperáveis (limitado e documentado).

### JSON

```kof
json.encode(42)                      // "42"
json.encode(user)                    // {"name":"Mel","age":30} (objetos/records no JVM)
json.encode(listOf(1, 2, 3))         // [1,2,3]
var u = json.decode<User>("{\"name\": \"Ana\", \"age\": 25}")
var l = json.decode<List<Int>>("[1, 2, 3]")
```

- JVM: parser JSON completo (objetos, arrays, strings com escapes, números), encode/decode via reflection (classes e records).
- Native: implementação em assembly (builder + decode posicional).
- Diagnostics claros para combinações não suportadas:
  - `JSN001` — Float/Double (ambos targets)
  - `JSN002` — objetos no target Native
  - `JSN003` — decode de arrays (use `List<Int>`)

### Exceptions (JVM — reais)

- `try`/`catch`/`finally` com exception table + StackMapTable.
- `throw "mensagem"` é wrapped em `RuntimeException`; `catch (String e)` unwrap de volta.
- `finally` roda nos caminhos normal, capturado e propagado (catch-all + rethrow).
- Try aninhado com propagação para o try externo.
- Native: `throw` → `kof_panic` (documentado como limitação).

### HTTP (`kof serve`)

```kof
handle(String method, String path, String body): String {
    if (path == "/hello") {
        return "{\"msg\": \"hi\"}"
    }
    return "{\"msg\": \"not found\"}"
}
```

- Funções top-level compilam para métodos static — dispatch por reflexão com
  suporte a static (URLClassLoader sobre as classes compiladas).
- Fallbacks: `handle()`, `get()`/`post()`/etc.
- Content-Type automático: `application/json` quando o body começa com `{`/`[`.
- `--port`, `--host`, graceful shutdown.

---

## Testes (292/292 PASS)

| Suíte | Quantidade | Cobertura |
|-------|-----------|-----------|
| CompilerDriverTest | 190 | compilação, semântica, fases F, isolamento arquitetural, IR |
| NativeE2ETest | 49 | execução real de binários nativos |
| JvmE2ETest | 29 | execução real de bytecode JVM |
| JsonE2ETest | 13 | JSON JVM + Native + diagnostics |
| ExceptionsE2ETest | 6 | try/catch/finally executados no JVM |
| FunctionSyntaxTest | 4 | todas as formas de declaração de função (JVM + Native) |
| NativeDebugTest | 1 | harness de debug nativo |

---

## Bugs Restantes (reais)

1. GC não implementado no Native (`kof_free` é no-op; memória devolvida ao SO no exit)
2. Exceptions no Native: `throw` = `kof_panic`, try/catch não recupera (compila, executa sequencialmente)
3. JSON de objetos/records não suportado no Native (JSN002 — diagnostic claro)
4. JSON Float/Double não suportado (JSN001 — diagnostic claro)
5. `json.decode<Int[]>` não suportado (parser não aceita `[]` em type argument; JSN003 como rede)
6. `LambdaExpr`/`IfExpr` parseados mas não lowerados (retornam UnknownType)
7. `kof-runtime` é módulo Maven vazio (destino de runtime futura)
8. `tests/golden` e `tests/integration` são esqueleto (sem casos reais)
9. Script `tests/native/golden/test-hello.sh` desatualizado (referencia jar antigo)

---

## Próximos Passos

### Fase H — HTTP (concluída ✅)
- `KofHttpServer` (engine cru: Content-Length, query, headers, JSON detection, 404/500, thread pool, graceful shutdown)
- `ReflectiveHandler` (funções top-level static: 5/4/3/0 args + get()/post()...; null = 404)
- `kof serve` usando o engine novo (removido fallback "Hello from Kof!" e truncamento de 4096)
- 8 testes E2E in-process com sockets reais

### Fase I — Concurrency (design ✅)
- `docs/future/CONCURRENCY.md`: semântica de tarefas, isolamento por valor, mapeamento por target (JVM virtual threads / Native scheduler / JS event loop)
- Sintaxe ainda não escolhida — semântica primeiro

### Fase J — Tooling (concluída ✅)
- LSP validado (initialize + publishDiagnostics com diagnóstico real SEM011; corrigido bug de URI absoluta)
- Editor grammar: `constructor`, `listOf`, `json` como builtins

### Fase K — Tests (concluída ✅)
- `tests/run-golden.sh`: 5 casos (hello, collections, exceptions, json, strings) × 2 targets = 10 checks
- `tests/run-integration.sh`: CLI E2E (build jvm/native, run, check, serve /ping + 404) = 8 checks
- Ambos no CI

### Fase L — Distribution (validada ✅)
- `scripts/package.sh` validado (layout dist + tar.gz + SHA256SUMS)
- Launcher `bin/kof` do pacote executando programas

### PRIORIDADE 2 — Standard Library (outro agente em progresso)
- `kof.core`, `kof.collections`, `kof.io` (File/Path/Directory), `kof.time` (now), `kof.json`
- Zero imports para operações fundamentais

### kof.test — suite de testes como parte da stdlib
- Testes unitários e de integração da própria linguagem (Kof testando Kof)

---

## Roadmap de Longo Prazo

- `docs/future/ROADMAP.md` com visão completa (fases A–Q).
- Próximo grande marco: **aplicação web completa em Kof** (frontend, backend,
  banco, auth, JSON, validação — lógica de negócio em pouquíssimos arquivos).