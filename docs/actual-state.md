# Estado Atual do Projeto Kof

**Última atualização:** 22 de agosto de 2026
**Versão:** 0.0.5-alpha

---

## Resumo Executivo

Kof é uma linguagem compilada para múltiplos targets (JVM, Native, Web, Script).

O projeto possui um **frontend completo** (lexer + parser + AST + symbol table + semantic + type checking), uma **IR backend-agnóstica** e **dois backends funcionais**: JVM (bytecode via ASM) e Native (ELF x86-64, syscalls, sem libc obrigatória).

**Fases C, D, E CONCLUÍDAS**: Type System, IR generalizada, NativeBackend ELF.

**Fase F CONCLUÍDA**: String model, Array model, Inheritance, Virtual Dispatch, Interfaces, Exceptions, Memory (mmap, sem GC).

**Pipeline 0.0.5 CONCLUÍDO**: JSON parity JVM/Native, exceptions reais no JVM, sintaxe de funções sem `fun`, serve/LSP/check/install/info, distribuição oficial.

---

## Build Status

| Verificação | Resultado |
|-------------|-----------|
| `mvn clean package` | ✅ PASSA |
| `mvn test` | ✅ PASSA (490/490) |
| `kof run` | ✅ FUNCIONA |
| `kof build --target jvm` | ✅ FUNCIONA |
| `kof build --target native` | ✅ FUNCIONA |
| `kof serve` | ✅ FUNCIONA |
| `kof check` | ✅ FUNCIONA |
| `kof info` | ✅ FUNCIONA |
| `kof lsp` | ✅ FUNCIONA |
| `kof install` | ✅ FUNCIONA |
| `scripts/package.sh` | ✅ GERA PACOTE + SHA256SUMS |

---

## O que FUNCIONA de ponta a ponta

### Sintaxe de funções (sem `fun`)

```kf
main() { ... }                       // entry point, void implícito
String saudacao() { ... }            // retorno antes do nome
despedida(): String { ... }          // retorno após os parâmetros
void fazIsso() { ... }               // void explícito
Bool positivo(Int x) = x > 0         // expression body
int dobro(int x) { ... }             // primitivos em qualquer caixa
```

### Records

```kf
record Point(Int x, Int y)
main() {
    var p = Point(3, 7)
    println(p)                       // Ponto[x=3, y=7] (toString no JVM)
    println(p.x() == q.x())
}
```

Gera `.class` válido (construtor, accessors, toString/equals/hashCode no JVM) e binário ELF x86-64 no Native.

### Classes

```kf
class User(String name, Int age) {
    greeting(): String {
        return "Hello " + name
    }
}
```

O construtor primário gera campos, construtor e acesso dentro de métodos —
sem `this.name = name`. `User(...)` e `new User(...)` são equivalentes
(`new` é retrocompatível). Inicializadores de campo rodam em todos os
construtores (JVM, Native, KofJS). Herança, virtual dispatch e interfaces
funcionam.

### JSON

```kf
json.encode(42)                      // "42"
json.encode(user)                    // {"name":"Mel","age":30} (JVM: objetos/records)
json.encode(listOf(1, 2, 3))         // [1,2,3]
var u = json.decode<User>("{\"name\": \"Ana\", \"age\": 25}")
var l = json.decode<List<Int>>("[1, 2, 3]")
```

JVM + Native parity para int/long/bool/string/list/array. Objetos/records: JVM (reflection).

### Exceptions (JVM — reais)

```kf
try {
    throw "boom"
} catch (String e) {
    println("caught: " + e)
} finally {
    println("finally")
}
```

Exception table real + StackMapTable. `throw "msg"` wrap em RuntimeException; `catch (String e)` unwrap. `finally` roda em todos os caminhos (normal, capturado, propagado). Native: `throw` = `kof_panic` (limitação documentada).

### HTTP (`kof serve`)

```kf
handle(String method, String path, String body): String {
    if (path == "/hello") {
        return "{\"msg\": \"hi\"}"
    }
    return "{\"msg\": \"not found\"}"
}
```

Handlers top-level (static), Content-Type automático, `--port`/`--host`, graceful shutdown.

### HTTP moderno (`web.app()` — stack web nativa)

```kf
var app = web.app()
app.get("/users/:id") {
    var user = User(param("id"))
    json.encode(user)
}
app.listen(8080)
```

Rotas com path params (`:id`), query, headers, middleware `app.use { }`,
Content-Type automático (JSON), 404/500, concorrência com virtual threads.
`kof serve <file.kf>` detecta `main()` e executa apps `web.app()`.
9 testes E2E com sockets reais (`KofWebE2ETest`). Ver `docs/stdlib-web.md`.

### kof.config e kof.log

```kf
config.str("database.url", "jdbc:h2:mem:test")   // arquivo > env > profile > default
log.info("servindo na porta 8080")               // debug/info/warn/error, níveis
```

`kof.config` (typed: str/int/long/bool, env/has) e `kof.log` (níveis, off,
warn→stderr) — JVM; Native/JS reportam CONFIG001/LOG001. Testes:
`KofConfigE2ETest` (8), `KofLogE2ETest` (7).

### kof.ui (fundação)

`Color` (RGBA 32-bit), `Theme` (light/dark), `Window/Label/Button`,
`Palette.*` — webview embarcado no JS runner (`--openWindow`) e shell
nativo `kof-webview` (WebKitGTK).

---

## O que está implementado

### Type System

| Feature | Status |
|---------|--------|
| `Type.java` | ✅ PrimitiveType, ClassType, TypeVariable, ArrayType, WildcardType |
| `SymbolTable.java` | ✅ Scopes encadeados, resolução em hierarquia |
| `SemanticAnalyzer.java` | ✅ Métodos, constructors, fields, locals, generics por erasure |
| Type checking | ✅ Assignability, larguras primitivas, arg types |

### IR Lowering

| Feature | Status |
|---------|--------|
| Records, classes, interfaces, herança | ✅ |
| Funções top-level (todas as formas) | ✅ |
| Métodos, construtores, `super` | ✅ |
| `var`/`val`, `return` | ✅ |
| `if`/`else`, `while`, `for`, `do-while`, `switch`, `break`/`continue` | ✅ |
| `try`/`catch`/`finally` + `throw` | ✅ (JVM real; Native panic) |
| Expressões binárias, unárias, bitwise | ✅ |
| Arrays, List\<T\>, generics | ✅ |
| JSON, strings (API completa), `instanceof`/`as` | ✅ |

### Segurança (kof.security — docs/security.md)

| Feature | Status |
|---------|--------|
| `passwords.hash/verify/needsRehash` | ✅ JVM/JS (PBKDF2-HMAC-SHA256 600k); Native SECN001 |
| `crypto.sha256/sha512/hmacSha256` | ✅ JVM/Native (asm)/JS — valores idênticos |
| `crypto.aesGcm` | ✅ JVM; outros SECN002 |
| `crypto.randomHex/randomInt` | ✅ JVM (SecureRandom)/Native (getrandom)/JS |
| `jwt.create/verify` (HS256, exp/iss/aud) | ✅ JVM/JS; Native pendente |
| `secrets.get/redact` | ✅ JVM/Native (/proc/self/environ)/JS |
| `security.constantTimeEquals` | ✅ 3 targets |
| `security.csrf*/corsAllowed/headers` | ✅ JVM |
| `auth.*` (contexto web Bearer JWT) | ✅ JVM |
| Gaps por target | ✅ Diagnostics claros SECN001/002/003 |
| `KofSecurityTest` | ✅ 22 testes (unit + E2E + adversariais) |
| `benchmarks/security/` | ✅ password-hash, jwt, hash-speed, aes-gcm |

### Backend JVM (ASM)

| Feature | Status |
|---------|--------|
| Bytecode V21 direto, COMPUTE_FRAMES | ✅ |
| Exception table + StackMapTable | ✅ |
| Records com atributo Record + toString/equals/hashCode | ✅ |
| Virtual dispatch, interfaces | ✅ |
| Erasure boxing (`kof_box`/`kof_unbox`) | ✅ |
| JSON helper `dev.kof.runtime.KofJson` (gerado via javac) | ✅ |
| List = java.util.ArrayList | ✅ |

### Backend Native (x86-64)

| Feature | Status |
|---------|--------|
| Stack machine real sobre a IR | ✅ |
| System V AMD64 ABI, ELF via `as`+`ld` | ✅ |
| Heap via mmap (`kof_alloc`) | ✅ |
| Vtables, dispatch virtual e de interface | ✅ |
| Strings, arrays, lists, JSON em assembly | ✅ |
| Syscalls de rede (`kof_net_*`) emitidos (API futura) | ✅ |

### CLI

| Feature | Status |
|---------|--------|
| `kof build` (jvm/native), `kof run` | ✅ |
| `kof serve`, `kof check`, `kof info [--json]` | ✅ |
| `kof lsp` (LSP mínimo com frontend real) | ✅ |
| `kof install`, `kof version` | ✅ |
| `kof bench` (33 benchmarks, mediana+RSS+baseline), `kof profile` | ✅ |
| `kof inspect` (IR) | ✅ |
| `kof debug <file.kf>` (DAP + JDWP cru; breakpoints por linha Kof, stack) | ✅ |

---

## O que NÃO está implementado

### Language Features
- Enums, Annotations, Pattern matching
- Map, Set
- Async/await, concorrência (spawn: JVM)
- Reflection

### Type System
- Overload resolution completo
- Variance
- Sealed types

### Backends
- KofJS — funcional (while(true), try/finally, switch, incrementos, listOf, decode de objetos — com parity JVM/Native/JS nos testes E2E)
- KofScript — hoje = compilar para JVM e executar (`kof run`)

### Runtime
- GC no Native (`kof_free` é no-op)
- Exceptions recuperáveis no Native (`throw` = `kof_panic`)
- JSON de objetos no Native (JSN002 — diagnostic claro)

### Security (kof.security — docs/security.md)
- v1 implementado (ver seção "Segurança" acima).
- Pendente: OAuth2/OIDC client, sessions, rate limiting, audit logging,
  JWT/passwords no Native (SECN001 — HMAC asm já existe), sha512 no Native
  (SECN003), AES-GCM fora do JVM (SECN002), e diagnósticos de target
  completos para jwt/auth/csrf/cors/headers (gap G7 da auditoria).

### Plataforma (gaps da auditoria — docs/ecosystem-coverage.md §4)
- Database/SQL (G1), HTTP client (G2), validation (G4), health/metrics
  (G5), suíte estruturada de testes (G6), scheduling (G8), rate
  limiting/sessions/API keys (G9), TLS/HTTPS (G12).
- `kof.config`/`kof.log` fora do JVM: CONFIG001/LOG001.

### Tooling
- `kof test` (PASS/FAIL por exit code com `assert`; suíte estruturada
  `test "nome" { }` planejada)
- REPL
- `kof fmt` (planejado)

---

## Arquitetura

```text
Source (.kf)
  ↓ Lexer
  ↓ Parser
  ↓ AST
  ↓ Symbol Resolution
  ↓ Semantic Analysis
  ↓ Type Checking
  ↓ Kof IR (backend-agnostic)
  ├── JVM Backend (ASM)
  └── Native Backend (x86-64)
```

| Módulo | Estado |
|--------|--------|
| kof-compiler | Funcional (~10k LOC) |
| kof-cli | Funcional (build, run, serve, check, info, lsp, install) |
| kof-runtime | Estrutura criada (runtime nativa embutida no NativeBackend; KofJson no JVM) |

| Métrica | Valor |
|---------|-------|
| Testes JUnit | 490 (todos passando) |
| E2E JVM | 29 |
| E2E Native | 50 |
| E2E JS (KofJS) | 35 |
| E2E JSON | 14 |
| E2E Exceptions | 9 |
| E2E HTTP | 8 |
| E2E kof.io | 15 |
| E2E Spawn/Assert/Lambda | 12 |