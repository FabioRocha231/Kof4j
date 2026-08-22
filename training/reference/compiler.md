# Kof Compiler Reference

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

## CLI Commands

| Command | Description |
|---------|-------------|
| `kof build <dir>` | Compile all .kf files |
| `kof build --target=jvm` | Compile for JVM |
| `kof build --target=native` | Compile for Native |
| `kof run <file.kf>` | Run a Kof program |
| `kof serve <file.kf>` | Start HTTP server |
| `kof version` | Show version |

## Backend Targets

### JVM
- Uses ASM for bytecode generation
- Targets Java 21
- Uses JVM's GC and memory management

### Native
- Generates x86-64 Linux ELF binaries
- Uses Linux syscalls directly (no libc)
- Runtime functions: kof_alloc, kof_print, kof_string_*, kof_array_*, etc.

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
| KofCall | Method/function call |
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
| KofConditionalJump | Conditional branch |
