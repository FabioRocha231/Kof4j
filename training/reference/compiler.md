# Kof Compiler Reference

**Version:** 0.2.6-beta (30 Aug 2026) — 658 tests

## Compilation Pipeline

```
Source (.kf / .ks / .c)
    ↓
Lexer → Tokens
    ↓
Parser → AST (PatternExpr fieldVars, NullableType)
    ↓
Semantic Analysis → Typed AST (isAssignable com null narrowing, record destructuring)
    ↓
Kof IR (backend-agnostic) → Optimizer (constant folding, branch simplification)
    ↓
┌─────────┬──────────┬──────┬──────────┬──────┐
│  JVM    │ Native   │ JS   │ KofScript│ KofC │
│  (ASM)  │ x86_64/  │ ES   │ JIT      │ C→ELF│
│         │ risc/arm*│      │          │      │
└─────────┴──────────┴──────┴──────────┴──────┘
```

* native.risc/arm placeholder

## CLI Commands

| Command | Description |
|---------|-------------|
| `kof build <dir> [--target jvm|native|native.risc|native.arm|js]` | Compile all .kf files |
| `kof run <file.kf>` | Run a Kof program (JVM/Native/JS) |
| `kof script <file.ks>` | KofScript top-level let → KofScriptGlobals + JIT |
| `kof c <file.c>` | KofC C subset → ELF x86_64 |
| `kof serve <file.kf>` | Start HTTP server |
| `kof check <file>` | Type-check only |
| `kof test <dir> --target ...` | Structured tests |
| `kof version` | Show version (0.2.6-beta) |

Fixes 27/08:
- `CompilerDriver.expandKofImports` trata `import a.b.C` (arquivo) além de `a.b.*` (pasta) — projetos grandes com `a/b/C.kf` agora geram ambos os `.class`.
- `NativeRuntime` free-list GC (`kof_free_head`, `kof_gc_collect` mark-sweep, `kof_gc_tick`).

## Backend Targets

### JVM
- Uses ASM for bytecode generation
- Targets Java 21
- Uses JVM's GC and memory management
- `kof.http` via `java.net.http.HttpClient`

### Native
- Generates x86_64 Linux ELF binaries
- Uses Linux syscalls directly (no libc)
- Free-list allocator + mark-sweep GC (conservador)
- MySQL handshake com SHA-1 scramble
- Runtime functions: kof_alloc, kof_free, kof_gc_*, kof_print, kof_string_*, kof_array_*, kof_list_*, kof_net_*, kof_db_mysql_scramble, etc.

### Native RISC-V / ARM
- Placeholder ELF via `riscv64-linux-gnu-as/ld` + qemu, `Target.NATIVE_RISCV64/AARCH64`

### JS
- ES Modules 2022+ via GraalJS
- `kof.http` via Java HttpClient interop
- `kof.cache`, `Map/Set`, `String?`, pattern record destructuring

### KofScript
- JIT in-memory, top-level `let`/`const` → `var`/`val` preprocess + `KofScriptGlobals`, evalCache 64 LRU

### KofC
- C subset (`int` globals, `void` funcs, `if`/`while`/`*(int*)`/`&`) → x86_64 via `as`/`ld`

## IR Operations

| Operation | Description |
|-----------|-------------|
| KofLoadLiteral | Load constant value |
| KofLoadLocal | Load local variable |
| KofStoreLocal | Store local variable |
| KofLoadField | Load object field |
| KofStoreField | Store object field |
| KofBinary | Binary arithmetic |
| KofUnary | Unary operation |
| KofCall | Method/function call (incl. map/filter/reduce, http) |
| KofNewObject | Create object |
| KofNewArray | Create array |
| KofArrayLoad | Array element read |
| KofArrayStore | Array element write |
| KofArrayLength | Array length |
| KofReturn | Return value |
| KofReturnVoid | Return void |
| KofThrow | Throw exception |
| KofLabel | Label marker |
| KofJump | Unconditional jump |
| KofConditionalJump | Conditional branch (pattern + null narrowing) |
