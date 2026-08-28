# Architecture Decision Record

## Project: Kof

## Status: Accepted

**Última atualização:** 27 de agosto de 2026
**Versão:** 0.2.0-beta

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
  ↓ Kof IR (backend-agnostic, com KofDebugInfo)
  ↓ Optimizer (constant folding, DCE, branch simplification, etc.)
  ↓
  ├── Kof4J Backend (ASM, bytecode V21)
  │   ↓ .class files
  │   ↓ JVM (virtual threads, KofRuntime gerado)
  │
  ├── KofNative Backend (x86_64)
  │   ↓ Assembly x86-64
  │   ↓ as + ld
  │   ↓ ELF x86_64 (syscalls, free-list + kof_gc_collect)
  │   ↓ OS
  │
  ├── KofNative riscv64
  │   ↓ Assembly riscv64 (.option arch,rv64g, li a7 214/64/93)
  │   ↓ riscv64-linux-gnu-as + ld
  │   ↓ ELF riscv64
  │
  ├── KofNative aarch64
  │   ↓ placeholder (Target.NATIVE_AARCH64)
  │   ↓ aarch64-linux-gnu-as + ld
  │   ↓ ELF aarch64
  │
  ├── KofJS Backend (GraalJS)
  │   ↓ ES Modules (ECMAScript 2022+)
  │   ↓ kof-runtime.mjs + KofJsRunner (embedded GraalJS)
  │   ↓ Node/Browser via kof_platform
  │
  ├── KofC Backend (KofCcompiler)
  │   ↓ C subset → Native x86_64 via kof_c (while/if/deref &/*(int*))
  │   ↓ ELF x86_64
  │
  └── KofScript Runtime
       ↓ top-level let → KofScriptGlobals (REPL, --watch)
       ↓ JVM execution (compila para bytecode em temp dir)
       ↓ Interactive
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
- `JvmBackend` - generates `.class` files via ASM (`kof-compiler/src/main/java/dev/kof/compiler/JvmBackend.java:1`)
- `NativeBackend` - generates ELF via assembly + `as` + `ld` (x86_64 stable, riscv64/aarch64 via cross toolchain)
- `JsBackend` - generates ES Modules (ECMAScript 2022+), executed by the embedded GraalJS engine (`KofJsRunner`)
- `KofCcompiler` - C subset (`kof c`) → ELF x86_64 native-only (`kof-compiler/src/main/java/dev/kof/compiler/KofCcompiler.java:1`)

### Target Enum

```java
public enum Target {
    JVM,
    NATIVE,          // x86_64 stable (free-list + kof_gc_collect)
    NATIVE_RISCV64,  // riscv64 via riscv64-linux-gnu-as, .option arch,rv64g, li a7 214/64/93
    NATIVE_AARCH64,  // aarch64 placeholder
    JS,              // alpha (GraalJS)
    KOF_C            // kofc native-only
}
```

CLI: `kof build/run --target jvm|native|native.riscv64|native.aarch64|js` (`CompilerDriver.java:1`, `Target.java:1`). `kof run`/`kof build --target js` executa JS sem Node.js (runtime embarcado). `kof c` usa `KofCcompiler` apenas para `native`.

## Type System

The type system supports (0.2.0-beta, 27/08/2026):

- Primitive types: `bool`, `byte`, `short`, `int`, `long`, `float`, `double`, `char`
- Reference types: classes, interfaces, enums (with `values()/valueOf` + exhaustiveness), records
- Generic types: `List<T>`, `Map<K,V>`, `Set<T>`, `Box<T>` (erasure, `Box<Int>` works via `substituteTypeVariable` `CompilerDriver.java:3972`)
- Type parameters: `<T>` (implemented, erasure); bounds (future)
- Wildcards: `?`, `? extends T`, `? super T` (future)
- Arrays: `int[]`, `String[]`
- Null safety: `String?` basic (`Type?` nullable, compile-time `?`-check) — 0.2.0-beta
- Pattern matching: `switch` with `case String s` + record destructuring `Point(x,y)` — JVM/Native/JS (0.2.0-beta)
- Void type
- Function types: `FunctionType` (lambdas with captures via `BoxN`, implemented)

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

The native backend generates ELF binaries (0.2.0-beta).

```text
Kof IR
  ↓
Assembly generation (x86-64 / riscv64 / aarch64)
  ↓
as (GNU assembler: as / riscv64-linux-gnu-as / aarch64-linux-gnu-as)
  ↓
.o (object file)
  ↓
ld (linker)
  ↓
ELF binary
```

Targets (0.2.0-beta):
- `native` (x86_64) **stable**: ELF x86_64, syscalls, free-list allocator (`kof_free_head`) + `kof_gc_collect` (mark-sweep pending, `munmap` fallback), strings/lists/JSON, exceptions with unwinding, `kof_db_mysql_scramble` for MySQL handshake
- `native.riscv64` **stable toolchain**: `.option arch,rv64g`, syscalls via `li a7 214/64/93` (riscv64 Linux ABI), `riscv64-linux-gnu-as/ld` + qemu, codegen riscv64 implemented
- `native.aarch64` **placeholder**: `Target.NATIVE_AARCH64` + `aarch64-linux-gnu-as/ld` + qemu skip, codegen still x86_64 placeholder (target separation done)

Current capabilities (x86_64):
- Record structs with fields, constructors, accessors, inheritance 3 levels, virtual dispatch via vtable
- Integer arithmetic, bitwise, control flow (if/else, while/for/do-while/break/continue, switch with pattern matching)
- Function calls (all forms), lambdas with captures (`BoxN`), exceptions (unwinding)
- Strings, arrays, `List<T>` with `map/filter/reduce`, `Map/K,V`, `Set<T>`, `Box<T>` (`kof_int_to_string`)
- `kof.io`, `kof.time` (now/sleep), `kof.config` (asm próprio, `/proc/self/environ`), `kof.log` (asm), `kof.security` (SHA-256/HMAC asm), `kof.db` SQLite + MySQL scramble

Runtime functions (x86-64, `NativeRuntime.java:1`):
- `kof_alloc` / `kof_free_head` free-list / `kof_gc_collect`
- `kof_print` / `kof_println` / `kof_print_int` / `kof_int_to_string`
- `kof_string_*`, `kof_array_*`, `kof_list_*`, `kof_map_*`, `kof_db_mysql_scramble`
- `kof_panic`, `kof_null_error`, `kof_bounds_error`

## JsBackend

- Generates ES Modules, executed by embedded GraalJS (`KofJsRunner`) — no Node.js required
- Supports pattern matching (`case String s` + `Point(x,y)` via `typeof` + destructuring), `String?` basic, `kof.http` via `Java HttpClient` interop, `List map/filter/reduce`, `Box<T>` via `substituteTypeVariable`
- Status alpha (0.2.0-beta)

## KofCcompiler

- C subset compiler (`kof c` — native-only): `int` globals, `void` funcs, `if`/`while`/`*(int*)`/`&`, → ELF x86_64 via `kof_c` (`KofCcompiler.java:1`)

## KofScript Runtime

KofScript enables direct execution of Kof programs (0.2.0-beta: top-level `let` → `KofScriptGlobals`).

```bash
kof run program.kf
kof script app.kf [--watch]
kof repl
```

Implementation:
- Compiles to JVM bytecode in temp directory (shared frontend + IR)
- `let`/`const` at top-level desugars to `KofScriptGlobals` fields
- Executes via `java -cp` with `KofScript` harness
- Cleans up temp files; `--watch` re-executes on change; SIGPIPE handled on Windows

## Standard Library (compile-time dispatch)

A Standard Library do Kof é implementada como **tabelas de dispatch
compile-time** (docs/stdlib.md): cada módulo é um descriptor no compilador
(`KofIo.java`, `KofWeb.java`, `KofSecurity.java`, `KofUi.java`) que mapeia a
intenção do programador para funções de runtime `kof_*`:

```text
Kof source
  ↓
SemanticAnalyzer   → tipos das chamadas
CompilerDriver     → lowering para KofCall(kof_*)
  ├── JvmRuntime   → KofRuntime.java gerado (javax.crypto, java.nio..., HttpClient for kof.http JS)
  ├── NativeRuntime→ assembly x86-64 / riscv64 (syscalls, sem libc, free-list + kof_gc_collect)
  └── JsBackend    → kof-runtime.mjs (JS puro + kof_platform, GraalJS)
```

Gaps de target produzem **diagnósticos claros em compile-time** (SECN00x,
CONC001, JSN00x, DB001, CONF001, LOG001) — nunca comportamento silenciosamente diferente.

Módulos (0.2.0-beta, 27/08/2026): `kof.core`, `kof.collections` (`List map/filter/reduce`, `Map/Set`, `Box<T>`), `kof.io`, `kof.time`, `kof.json` (pattern-aware), `kof.http` (JVM+JS via HttpClient), `kof.web`, `kof.security`, `kof.concurrent` (`spawn`), `kof.test`, `kof.cli` (`build/run/serve/check/test/bench/debug/info/lsp/install/script/repl/c`), `kof.db`/`kof.orm` (SQLite native + MySQL scramble), `kof.config`/`kof.log`. Estado completo em docs/stdlib.md e docs/status.md:12-26 (658 testes, 16/16 golden, 9/9 integration).

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
