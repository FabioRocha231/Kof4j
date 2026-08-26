# Estado Atual do Projeto Kof

**Última atualização:** 25 de agosto de 2026
**Versão:** 0.1.0

---

## Resumo Executivo

Kof é uma linguagem compilada para múltiplos targets (JVM, Native, Web, Script).

O projeto possui um **frontend completo** (lexer + parser + AST + symbol table + semantic + type checking), uma **IR backend-agnóstica** e **três backends funcionais**: JVM (bytecode via ASM), Native (ELF x86-64, syscalls, sem libc obrigatória) e KofJS (ES Modules na engine GraalJS embarcada).

**Fases C, D, E CONCLUÍDAS**: Type System, IR generalizada, NativeBackend ELF.

**Fase F CONCLUÍDA**: String model, Array model, Inheritance, Virtual Dispatch, Interfaces, Exceptions, Memory (mmap, sem GC).

**Pipeline 0.0.5 CONCLUÍDO**: JSON parity JVM/Native, exceptions reais no JVM, sintaxe de funções sem `fun`, serve/LSP/check/install/info, distribuição oficial.

**Plataforma 0.0.7-0.1.0 (25/08)**: kof.ui (widgets + webview nativo via KofJS), kof.db (JDBC idiomático JVM + SQLite nativo via .so + MySQL wire protocol WIP), kof.orm (`entity` declarativo + CRUD/where/migrate + MongoDB), logging estruturado (JSON, correlation ID), JSON completo (Float/Double, arrays), conversões String→numérico, ARITH001, BOM UTF-8, generics `Box<T>` com `T` primitivo fixo (`NativeE2ETest` 50/50; `substituteTypeVariable`), `SEM025` sem falso-positivo em `hashCode/equals/toString`.

---

## Build Status

| Verificação | Resultado |
|-------------|-----------|
| `mvn clean package` | ✅ PASSA |
| `mvn test` | ✅ PASSA (590 JUnit / 581 declarados; `NativeE2ETest` 50/50, `JvmE2ETest` 29/29, `KofJsE2ETest` 35/35 em 25/08; +1 skip condicional) |
| `kof run` | ✅ FUNCIONA |
| `kof build --target jvm` | ✅ FUNCIONA |
| `kof build --target native` | ✅ FUNCIONA |
| `kof build --target js` | ✅ FUNCIONA |
| `kof serve` | ✅ FUNCIONA |
| `kof check` | ✅ FUNCIONA |
| `kof test` | ✅ FUNCIONA |
| `kof bench` | ✅ FUNCIONA (37 benchmarks, baseline+regressão) |
| `kof debug` | ✅ FUNCIONA (DAP MVP, target JVM) |
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
warn→stderr) — JVM; Native/JS reportam CONFIG001/LOG001. Logging
estruturado em JSON com correlation ID no JVM. Testes:
`KofConfigE2ETest` (8), `KofLogE2ETest` (10).

### kof.db e kof.orm — persistência nativa

```kf
entity User {
    id: Long generated
    name: String
    email: String unique
}

main() {
    var db = db.connect("jdbc:h2:mem:app;DB_CLOSE_DELAY=-1")
    orm.create<User>(db)
    orm.save(db, User(0, "Mel", "mel@kof.dev"))
    var u = orm.find<User>(db, 1)
}
```

- `kof.db`: JDBC idiomático (connect/execute/query/query<T>/transaction) no
  JVM; **SQLite nativo** via link direto da `.so`; MySQL/MariaDB por wire
  protocol sobre sockets nativos (WIP); JS reporta DB001.
- `kof.orm`: schema na linguagem (`entity`, compile-time), CRUD completo
  (`create/save/find/all/where/delete/count`), migrations versionadas
  (`orm.migrate`), constraints (`generated`, `unique`), PK não-numérica.
  **MongoDB** suportado (driver oficial via reflexão compatível). Native/JS
  reportam ORM001.
- Testes: `KofDbE2ETest` (8), `KofOrmE2ETest` (10, incluindo MongoDB E2E).
- Ver `docs/future/DATABASE_VISION.md`.

### Feedback de uso real (kof-calculator-lab)

- UTF-8 BOM inicial tolerado pelo Lexer (editores Windows).
- `String.toInt()/toLong()/toDouble()/toFloat()` como funções do runtime.
- ARITH001: divisão/resto por zero **constante** rejeitada em compile-time
  (inteiros; float/double produzem Infinity/NaN e não são diagnosticados).
- `--help` nos subcomandos `kof run/build/serve/check`.

### kof.ui (plataforma de UI)

`Color` (RGBA 32-bit), `Theme` (light/dark), `Palette.*`, widgets
`Window`/`Label`/`Button`/`Input`/`Column`/`Row`/`View`/`Style` — renderização
**KofJS**: `kof run --target=js` abre o app interativo no webview nativo
(`bin/kof-webview`, WebKitGTK embutido; módulos ES sobre `file://` habilitados
via `webkit_settings_set_allow_file_access_from_file_urls`). Ações de botão
por lambdas com capturas; fechar a janela encerra o programa. JVM/Native:
handles no-ops.

---

## O que está implementado

### Type System

| Feature | Status |
|---------|--------|
| `Type.java` | ✅ PrimitiveType, ClassType, TypeVariable, ArrayType, WildcardType |
| `SymbolTable.java` | ✅ Scopes encadeados, resolução em hierarquia |
| `SemanticAnalyzer.java` | ✅ Métodos, constructors, fields, locals, generics por erasure; 25/08 `SEM025` ignora `hashCode/equals/toString` |
| Type checking | ✅ Assignability, larguras primitivas, arg types; 25/08 `Box<T>` `T→Int` via `substituteTypeVariable` |

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
| Arrays, List\<T\>, generics | ✅ (25/08 `Box<T>` `T` primitivo/Boxed + `println` nativo `kof_int_to_string`) |
| JSON, strings (API completa), `instanceof`/`as` | ✅ |

### Segurança (kof.security — docs/security.md)

| Feature | Status |
|---------|--------|
| `passwords.hash/verify/needsRehash` | ✅ JVM/JS (PBKDF2-HMAC-SHA256 600k); Native ✅ PBKDF2/SHA-512/JWT/AES-GCM asm (G10 fechado 25/08) |
| `crypto.sha256/sha512/hmacSha256` | ✅ JVM/Native (asm)/JS — valores idênticos |
| `crypto.aesGcm` | ✅ JVM/Native (asm) |
| `crypto.randomHex/randomInt` | ✅ JVM (SecureRandom)/Native (getrandom)/JS |
| `jwt.create/verify` (HS256, exp/iss/aud) | ✅ JVM/Native (asm)/JS |
| `secrets.get/redact` | ✅ JVM/Native (/proc/self/environ)/JS |
| `security.constantTimeEquals` | ✅ 3 targets |
| `security.csrf*/corsAllowed/headers` | ✅ JVM |
| `auth.*` (contexto web Bearer JWT) | ✅ JVM |
| `security.rateLimit/session/apiKey` | ✅ 3 targets (G9 `KofSecurityG9Test` 3/3) |
| Gaps por target | ✅ Diagnostics claros SECN001/002/003/004 |
| `KofSecurityTest` | ✅ 22 testes (unit + E2E + adversariais) + `KofSecurityG9Test` 3/3 |
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
- KofJS — funcional (while(true), try/finally, switch, incrementos, listOf, decode de objetos — com parity JVM/Native/JS nos testes E2E); UI via webview nativo
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

### Database (docs/future/DATABASE_VISION.md)
- Nível 0 (conexão + SQL), 2 (ORM básico) e 4 (migrations) implementados.
- Pendente: nível 1 (query DSL tipada `User.query { where ... }`), connection
  pooling, MySQL nativo completo (wire protocol WIP), kof.db/kof.orm fora do
  JVM (DB001/ORM001).

### Plataforma (gaps da auditoria — docs/ecosystem-coverage.md §4)
- HTTP client (G2), validation (G4), health/metrics (G5), suíte estruturada
  de testes (G6), scheduling (G8) — todos ✅ em 0.1.0 (25/08).
- Rate limiting/sessions/API keys (G9), TLS/HTTPS (G12), kof.security Native (G10) — ✅ 25/08.
- ~~Database/SQL (G1)~~ — ✅ nível 0 do kof.db/kof.orm implementado.
- `kof.config`/`kof.log` fora do JVM: CONFIG001/LOG001 (paridade JVM/Native asm; JS CONF001/LOG001).

### Tooling
- Suíte estruturada `test "nome" { }` (hoje: PASS/FAIL por exit code com `assert`)
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
| Testes JUnit | 590 (581 declarados em 25/08; +1 skip condicional) |
| E2E JVM | 29 |
| E2E Native | 50 |
| E2E JS (KofJS) | 35 |
| E2E JSON | 14 + 7 (completo) |
| E2E Exceptions | 9 |
| E2E HTTP/Web | 8 + 9 (TLS 5) |
| E2E kof.io | 15 |
| E2E UI | 14 + 3 (Window) |
| E2E kof.db / kof.orm | 8 + 16 |
| E2E kof.security + G9 | 22 + 3 |
| Benchmarks | 37 em 17 categorias |