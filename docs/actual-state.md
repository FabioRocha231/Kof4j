# Estado Atual do Projeto Kof

**Última atualização:** 21 de agosto de 2026

---

## Resumo Executivo

Kof é uma linguagem compilada para múltiplos targets (JVM, Native, Web, Script).

O projeto possui um **frontend sólido** (lexer + parser + AST) e um **backend JVM funcional** para o subconjunto de features que são corretamente loweradas.

**Fase C CONCLUÍDA**: Type System + Symbol Resolution criados e integrados.

**Fase D CONCLUÍDA**: IR generalizada para múltiplos backends. Kof IR é backend-agnostic.

**Fase E EM PROGRESSO**: NativeBackend reescrito como stack machine real consumindo Kof IR.

O **backend nativo** compila para x86-64 ELF. O assembly é gerado e montado, mas a execução end-to-end precisa de runtime nativa para binários.

O **kof-runtime** está vazio.

---

## Build Status

| Verificação | Resultado |
|-------------|-----------|
| `mvn clean package -DskipTests` | ✅ PASSA |
| `mvn test` | ✅ PASSA (32/32) |
| `kof run` | ✅ FUNCIONA |
| `kof build` | ✅ FUNCIONA |

---

## O que FUNCIONA de ponta a ponta

### Records para JVM

```kf
record Point(Int x, Int y)
```

Gera: classe `.class` válida, construtor, accessors, `toString()`.

### Funções com println

```kf
fun main() {
    println("Hello, Kof!")
}
```

Compila e executa corretamente.

### Variáveis

```kf
fun main() {
    var nome = "Mel"
    var idade = 26
    println(nome)
    println(idade)
}
```

Resultado: `Mel` / `26`

### Records nativos

```kf
record Point(Int x, Int y)
```

Gera binário ELF x86-64.

### Classes

```kf
public class User {
    String name
    public constructor(String name) { this.name = name }
    public fun getName(): String { return name }
}
```

Compila, gera `.class`, executa na JVM.

### Controle de fluxo

`if/else`, `while`, `for` — todos funcionam.

---

## O que está PARCIALMENTE implementado

### Type System

| Feature | Status |
|---------|--------|
| `Type.java` | ✅ PrimitiveType, ClassType, UnknownType |
| `SymbolTable.java` | ✅ Scopes encadeados |
| `SemanticAnalyzer.java` | ✅ Resolução de métodos, constructors, fields, locals |
| Type checking | ⚠️ Básico |

### IR Lowering

| Feature | Status |
|---------|--------|
| Records | ✅ |
| Classes | ✅ |
| Funções top-level | ✅ |
| Métodos | ✅ |
| Construtores | ✅ |
| `var`/`val` | ✅ |
| `return` | ✅ |
| `if`/`else` | ✅ |
| `while` | ✅ |
| `for` | ✅ |
| Expressões binárias | ✅ |
| `print`/`println` | ✅ |

### Backend Native

| Feature | Status |
|---------|--------|
| Stack machine real | ✅ |
| Kof IR consumption | ✅ |
| x86-64 System V ABI | ✅ |
| String literals em .data | ✅ |
| kof_print/kof_println | ✅ |
| kof_print_int | ✅ |
| Field offsets do IRClass | ✅ |
| println com integer | ✅ |

### CLI

| Feature | Status |
|---------|--------|
| `kof build` | ✅ |
| `kof build --target=jvm` | ✅ |
| `kof build --target=native` | ✅ |
| `kof run` | ✅ |
| `kof version` | ✅ |

---

## O que NÃO está implementado

### Language Features
- `do-while`, `switch`/`case`, `break`/`continue`
- `try`/`catch`/`finally`
- Generics, Enums, Annotations, Arrays
- Pattern matching

### Type System
- Type checking completo
- Overload resolution
- Generic type inference

### Backends
- KofJS (Web) — não iniciado
- KofScript — só compila para JVM e executa

---

## Arquitetura

```text
Source (.kf)
  ↓ Lexer
  ↓ Parser
  ↓ AST
  ↓ Type System
  ↓ Semantic Analysis
  ↓ Kof IR (backend-agnostic)
  ├── JVM Backend (ASM)
  └── Native Backend (x86-64)
```

| Módulo | Estado |
|--------|--------|
| kof-compiler | Funcional |
| kof-cli | Funcional |
| kof-runtime | Vazio |

| Métrica | Valor |
|---------|-------|
| Linhas de código (compiler) | ~3.000 |
| Arquivos de código | 20 |
| Testes JUnit | 32 |
| Features JVM | ~15 |
