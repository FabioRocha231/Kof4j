# DEBUGGING_JVM.md — Debug no target JVM

**Status:** Fase 2 planejada (LineNumberTable em implementação)
**Data:** 23 de agosto de 2026

---

## 1. Fluxo

```text
Kof Debug Info (IR)
    ↓
JVM Debug Metadata (ASM)
    ↓
class file (LineNumberTable, LocalVariableTable, SourceFile)
    ↓
JDWP (java -agentlib:jdwp)
    ↓
kof-debug (DAP)
    ↓
Editor
```

## 2. Metadata gerada

- `SourceFile` — o arquivo .kf;
- `LineNumberTable` — mapeia bytecode → linha Kof (a Fase 1 fornece a
  posição de cada op da IR; o JvmBackend emite `visitLineNumber`);
- `LocalVariableTable` / `LocalVariableTypeTable` — nomes e tipos Kof
  (com assinaturas genéricas);
- métodos e classes com informação de origem.

## 3. Mapeamento

```text
UserService.kf:42
    ↓
método JVM correspondente + offset de bytecode
```

A tradução acontece no backend; o usuário nunca vê o bytecode.

## 4. JDWP

O programa é lançado com `-agentlib:jdwp=transport=dt_socket,...` e o
adaptador conversa com o JDWP para: breakpoints, stepping, stack frames,
locals, exceções.
