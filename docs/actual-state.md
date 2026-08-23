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
| `mvn test` | ✅ PASSA (381/381) |
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
class User {
    String name
    public constructor(String name) { this.name = name }
    public getName(): String { return name }
}
```

Compila, gera `.class`, executa na JVM e no Native (herança, virtual dispatch, interfaces).

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

---

## O que NÃO está implementado

### Language Features
- Enums, Annotations, Pattern matching
- Lambdas/closures (parseados, não lowerados)
- Map, Set
- Async/await, concorrência
- Reflection

### Type System
- Overload resolution completo
- Variance
- Sealed types

### Backends
- KofJS — alpha funcional (GraalJS embutido)
- KofScript — hoje = compilar para JVM e executar (`kof run`)

### Runtime
- GC no Native (`kof_free` é no-op)
- Exceptions recuperáveis no Native (`throw` = `kof_panic`)
- JSON de objetos no Native (JSN002 — diagnostic claro)

### Tooling
- `kof test` (PASS/FAIL por exit code com `assert`)
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
| Testes JUnit | 381 (todos passando) |
| E2E JVM | 29 |
| E2E Native | 50 |
| E2E JS (KofJS) | 35 |
| E2E JSON | 14 |
| E2E Exceptions | 9 |
| E2E HTTP | 8 |
| E2E kof.io | 15 |
| E2E Spawn/Assert/Lambda | 12 |