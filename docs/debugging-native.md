# DEBUGGING_NATIVE.md — Debug no target Native

**Status:** Planejado — a Fase 1 da IR (source locations) já alimenta o caminho; DWARF futuro
**Data:** 27 de agosto de 2026
**Versão:** 0.2.6-beta (746 testes; 7 targets; free-list + pthread spawn + FP XMM)

---

## 1. Fluxo

```text
Kof Debug Info (IR)
    ↓
símbolos + line tables (DWARF futuro)
    ↓
ELF x86-64
    ↓
debug adapter (frame info Kof)
    ↓
DAP
    ↓
Editor
```

## 2. Fase inicial

- símbolos de função (já existem: `ClassName_methodName`);
- source locations por instrução (Fase 1 da IR);
- line tables;
- locals com tipos Kof;
- scopes;
- stack frames.

## 3. Depois

- DWARF completo;
- localizações otimizadas de variáveis;
- inspeção de memória nativa.

## 4. Regra

Nunca mostrar assembly como experiência primária — o mapeamento
`assembly → linha Kof` é interno.