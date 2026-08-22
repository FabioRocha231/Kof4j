# Kof Overview

Kof is a compiled, statically-typed, object-oriented programming language targeting JVM and Native (x86-64).

## Key Characteristics

- **Compiled** — no interpreter, no JVM bytecode generation layer
- **Statically typed** — type errors caught at compile time
- **Multi-target** — same code runs on JVM and Native
- **Minimal boilerplate** — intent over ceremony
- **Memory managed** — no manual allocation/deallocation

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

## Current Features

| Feature | Status |
|---------|--------|
| Classes | ✅ |
| Records | ✅ |
| Interfaces | ✅ |
| Inheritance | ✅ |
| Virtual Dispatch | ✅ |
| Strings | ✅ |
| Arrays | ✅ |
| Exceptions | ✅ |
| do-while | ✅ |
| String concat | ✅ |
| Web server | ✅ |

## What Kof Is NOT

- Java with different syntax
- A transpiler to Java
- A Spring clone
- A framework
- An interpreter
