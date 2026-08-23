# DEBUGGER_ARCHITECTURE.md — Arquitetura do Kof Debugger

**Status:** Plano + Fase 1 em implementação (source mapping na IR)
**Data:** 23 de agosto de 2026

---

## 1. Princípio

> **O programador depura o programa Kof, nunca o artefato intermediário
> gerado pelo backend.**

O debugger preserva a identidade semântica Kof sobre qualquer target:

```text
Kof Source
    ↓
Debug Metadata (na IR)
    ↓
Kof Debug Information
    ↓
Debug Adapter (DAP)
    ↓
Editor
```

Cada backend traduz o modelo de debug Kof para o formato da plataforma
(JDWP/DWARF/Source Maps) — mas a abstração pública é sempre Kof.

## 2. Divisão de responsabilidades

| Camada | Responsabilidade |
|--------|------------------|
| Frontend | AST com posições de origem |
| CompilerDriver | **Debug Metadata** — cada op da IR sabe de onde veio |
| IR | KofDebugInfo (source files, functions, locals, scopes, mappings) |
| JvmBackend | LineNumberTable, LocalVariableTable, SourceFile |
| NativeBackend | símbolos, line tables (DWARF futuramente) |
| JsBackend | source maps Kof → JS |
| kof-debug | Debug Adapter (DAP) |
| Kof Editor | breakpoints, stack, variables (DAP) |

## 3. Debug Metadata na IR

Toda operação relevante da Kof IR carrega a relação:

```text
IR instruction
 ├── source = src/UserService.kf
 ├── line = 42
 ├── column = 17
 ├── function = UserService.find
 └── scope = method
```

O modelo interno (backend-agnóstico):

```text
KofDebugInfo
 ├── SourceFile[]
 ├── Function[]
 ├── Variable[]
 ├── Scope[]
 ├── Type[]
 ├── SourceLocation[]
 └── InstructionMapping[]
```

Não criar metadata específica para JVM — a informação de origem existe
**antes** do backend.

## 4. Mapeamento por target

| Target | Formato nativo | Exposição ao usuário |
|--------|----------------|----------------------|
| JVM | LineNumberTable, LocalVariableTable, SourceFile + JDWP | funções e linhas Kof |
| Native | símbolos + line tables (DWARF futuro) | funções e linhas Kof |
| KofJS | source maps Kof → JS + Node Inspector/Chrome | funções e linhas Kof |

## 5. Regras

- Nunca: `Kof → Java → debugger`
- Nunca: mostrar JVM slots, assembly ou JS gerado como experiência primária
- Código sintético (construtores gerados, lambdas, accessors) deve ser
  **escondido** — o usuário vê a abstração Kof original
- Build `--debug` preserva metadata extra; `--release` não destrói metadata
  essencial sem necessidade

## 6. Fases

```text
Fase 1  DebugInfo na IR (source location por op, function/variable/scope)
Fase 2  JVM: LineNumberTable + LocalVariableTable (via ASM)
Fase 3  kof-debug: Debug Adapter DAP (launch, attach, breakpoints, stack)
Fase 4  Kof Editor: breakpoints, toolbar, call stack, variables, stepping
Fase 5  Native: DWARF
Fase 6  JS: source maps + Node Inspector
Fase 7  Avançado: conditional/exception breakpoints, avaliação, async
```

## 7. Documentação relacionada

- `debugging.md` — visão de uso
- `debug-adapter.md` — o componente kof-debug
- `debugging-jvm.md`, `debugging-native.md`, `debugging-js.md` — por target