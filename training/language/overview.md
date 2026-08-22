# Kof Overview

Kof is a compiled, statically-typed, object-oriented programming language targeting JVM and Native (x86-64).

## Key Characteristics

- **Compiled** — the compiler emits JVM bytecode (via ASM) or a native ELF binary; there is no interpreter
- **Statically typed** — type errors caught at compile time
- **Multi-target** — same code runs on JVM and Native
- **Minimal boilerplate** — intent over ceremony
- **Memory managed** — no manual allocation/deallocation in user code
- **No `fun` keyword** — functions are declared by name (`main()`, `String f()`, `f(): String`)

## Compilation Pipeline

```
Source (.kf)
    ↓
Lexer → Tokens
    ↓
Parser → AST
    ↓
Semantic Analysis → Typed AST
    ↓
Kof IR (backend-agnostic)
    ↓
┌─────────┬──────────┐
│  JVM    │  Native  │
└─────────┴──────────┘
```

## Current Features (0.0.5-alpha)

| Feature | Status |
|---------|--------|
| Classes, records, interfaces, inheritance, virtual dispatch | ✅ |
| Constructors (`constructor(...)`, default auto) | ✅ |
| Functions (all declaration forms, no `fun`) | ✅ |
| Lambdas `(x: Int) -> expr` (no captures) | ✅ |
| If-expressions `var x = if (c) a else b` | ✅ |
| `List<T>` + `listOf` + `for (var x in coll)` | ✅ |
| Strings (`+`, `==`, indexOf, trim, split, ...) | ✅ |
| Arrays (`new Int[n]`, `arr[i]`, `.length`) | ✅ |
| Exceptions `throw "msg"` / try/catch/finally (JVM + Native) | ✅ |
| Generics (erasure) | ✅ |
| JSON `json.encode` / `json.decode<T>` | ✅ (objetos só JVM) |
| kof.io: `readFile`, `writeFile`, `readLine`, `File/Path/Directory` | ✅ |
| kof.time: `now()` | ✅ |
| switch, instanceof, `as` | ✅ |
| Web server (`kof serve`, `handle(...)`) | ✅ |

## Planned / Unavailable

| Feature | Status |
|---------|--------|
| `Map`, `Set` | Planned |
| `Option<T>` / null safety | Planned |
| Async / concurrency | Planned |
| Higher-order functions (`map`, `filter`) | Planned |
| Lambda captures | Planned |
| Pattern matching | Planned |
| Primary constructors `class X(...)` | Unavailable — use `constructor(...)` |
| Array literals `{1, 2, 3}` | Unavailable — use `new Int[n]` |

## What Kof Is NOT

- Java with different syntax
- A transpiler to Java
- A Spring clone
- A framework
- An interpreter
- A VM