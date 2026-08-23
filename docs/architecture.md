# Architecture Decision Record

## Project: Kof

## Status: Accepted

## Context

Kof é uma linguagem de programação fortemente tipada, estaticamente tipada e orientada a objetos. O compilador deve gerar código para múltiplos targets a partir de uma única IR.

Uma linguagem. Um compilador. Múltiplos targets.

## Pipeline

```text
Source (.kf)
  ↓ Lexer
  ↓ Token stream
  ↓ Parser
  ↓ AST
  ↓ Symbol resolution
  ↓ Type checking
  ↓ Semantic analysis
  ↓ Kof IR (backend-agnostic)
  ↓
  ├── Kof4J Backend (ASM)
  │   ↓ .class files
  │   ↓ JVM
  │
  ├── KofNative Backend
  │   ↓ Assembly (x86-64)
  │   ↓ as + ld
  │   ↓ ELF binary
  │   ↓ OS
  │
  ├── KofScript Runtime
  │   ↓ JVM execution
  │   ↓ Interactive
  │
  └── KofJS Backend (future)
      ↓ HTML/CSS/JS
      ↓ Browser
```

## Decision: Multiplatform via Shared Frontend + Pluggable Backends

### Rationale

1. **One language, multiple targets.** The same Kof source compiles to JVM, native, script, or web.
2. **Shared frontend.** Lexer, parser, AST, type system, and semantic analysis are shared across all backends.
3. **Pluggable backends.** Each target has its own backend that consumes the same IR.
4. **No transpilation.** Kof generates bytecode directly for JVM, assembly for native.
5. **No Java intermediate.** Kof does not generate Java source code.

### Backend Interface

```java
public interface Backend {
    void emit(IRModule module, Path outputDir) throws IOException;
}
```

Implementations:
- `JvmBackend` - generates `.class` files via ASM
- `NativeBackend` - generates ELF x86-64 via assembly + as + ld
- `JsBackend` - generates ES Modules (ECMAScript 2022+), executed by the
  embedded GraalJS engine (KofJsRunner)

### Target Enum

```java
public enum Target {
    JVM,
    NATIVE,
    JS
}
```

`kof run`/`kof build --target js` executa JS sem Node.js (runtime embarcado).

## Type System

The type system supports:

- Primitive types: `bool`, `byte`, `short`, `int`, `long`, `float`, `double`, `char`
- Reference types: classes, interfaces, enums, records
- Generic types: `List<T>` (implemented, erasure); `Map<K, V>` (future)
- Type parameters: `<T>` (implemented, erasure); bounds (future)
- Wildcards: `?`, `? extends T`, `? super T` (future)
- Arrays: `int[]`, `String[]`
- Null types
- Void type
- Function types: `FunctionType` (lambdas, implemented)

### Type Representation

```text
Type
  ├── PrimitiveType (int, bool, etc.)
  ├── ClassType (User, String, etc.)
  ├── ArrayType (int[], User[])
  ├── TypeVariable (T)
  ├── WildcardType (? extends T)
  └── UnknownType
```

## IR

The IR is a backend-agnostic lowered representation of the AST.

```text
IRModule
  ├── IRClass
  │     ├── IRField*
  │     ├── IRMethod*
  │     │     ├── IRBasicBlock*
  │     │     │     └── KofOperation*
  │     │     └── IRLocalVariable*
  │     └── metadata
  └── imports
```

### KofOperation types

- **Literals**: KofLoadLiteral (int, long, float, double, string, bool, null)
- **Variables**: KofLoadLocal, KofStoreLocal
- **Fields**: KofLoadField, KofStoreField, KofGetStatic, KofPutStatic
- **Arithmetic**: KofBinary (ADD, SUB, MUL, DIV, MOD), KofUnary (NEG, NOT)
- **Comparisons**: KofConditionalJump (EQ, NE, LT, LE, GT, GE)
- **Control flow**: KofLabel, KofJump, KofConditionalJump
- **Calls**: KofCall (INSTANCE, STATIC, CONSTRUCTOR, FUNCTION)
- **Object creation**: KofNewObject
- **Return**: KofReturn, KofReturnVoid
- **Stack**: KofDup, KofPop
- **Type ops**: KofCheckCast, KofInstanceOf
- **Arrays**: KofArrayLoad, KofArrayStore, KofNewArray, KofArrayLength
- **Exception**: KofThrow

### Labels

Labels use `LabelId` (integer-based), not ASM Labels.

```java
record LabelId(int id) { ... }
```

Each backend maps LabelId to its own representation (ASM Label for JVM, assembly labels for native).

## JVM Backend

The JVM backend uses ASM to generate class files.

```text
Kof IR
  ↓
ClassWriter (ASM)
  ↓
.class bytes
```

The backend produces:
- Correct constant pool
- Correct method descriptors
- StackMapTable
- LineNumberTable (debugging)
- LocalVariableTable (debugging)

## Native Backend

The native backend generates ELF x86-64 binaries.

```text
Kof IR
  ↓
Assembly generation (x86-64)
  ↓
as (GNU assembler)
  ↓
.o (object file)
  ↓
ld (linker)
  ↓
ELF binary
```

Current capabilities:
- Record structs with fields
- Constructor generation
- Accessor methods
- Integer arithmetic
- Function calls
- String printing (via syscalls)
- Control flow (if/else, loops)
- Field access/assignment
- Instance methods

Runtime functions:
- `kof_print` - print null-terminated string
- `kof_println` - print string + newline
- `kof_print_int` - print integer value

## KofScript Runtime

KofScript enables direct execution of Kof programs.

```bash
kof run program.kf
```

Implementation:
- Compiles to JVM bytecode in temp directory
- Executes via `java -cp`
- Cleans up temp files

## Diagnostics

All errors point to the original source location.

```text
error: type mismatch
  --> src/main/kf/User.kf:12:5
   |
12 |     name = 42
   |     ^^^^ expected String, found Int
```

## Java Interoperability

The JVM backend must generate bytecode that is fully compatible with Java:

- Correct class file format
- Correct method signatures
- Correct generic erasure
- Standard class loading
- Standard reflection

## Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| ASM version compatibility | High | Pin ASM version, test with target JDK |
| Generic erasure complexity | High | Start simple, add complexity incrementally |
| Debugging metadata | Medium | Generate source mapping from day one |
| Native backend complexity | High | Start with minimal ELF generation |
| KofJS complexity | High | Focus on backend first, UI model later |
| IR design | High | Keep IR simple, evolve incrementally |
