# DEBUG-ADAPTER.md — kof-debug (Debug Adapter DAP)

**Status:** Planejado — não implementado
**Data:** 23 de agosto de 2026

---

## 1. Objetivo

Componente `kof-debug` que expõe a execução Kof via **DAP** (Debug Adapter
Protocol) — o mesmo protocolo dos editores modernos (VS Code, Neovim,
IntelliJ, Kof Editor).

Não criar protocolo proprietário.

## 2. Responsabilidades

- iniciar programas (`launch`);
- anexar a processos (`attach` — futuro);
- controle de execução: continue, pause, step over/into/out, restart, terminate;
- breakpoints (source; depois conditional, hit count, exception);
- stack traces, scopes, locals, arguments, campos;
- eventos de exceção;
- inspeção de variáveis com tipos Kof;
- avaliação de expressões (futuro — com type system, nunca Java/JS cru).

## 3. Interface

```text
kof-debug (DAP over stdio)
    ↓
JVM: launch java -agentlib:jdwp + JDWP client
Native: launch binary + DWARF/frame info
JS: launch node --inspect + Inspector protocol
```

O CLI (`kof debug`) é apenas uma interface — a lógica vive no adaptador.

## 4. Tipos de runtime

O adaptador traduz representações de backend para tipos Kof:

| Kof | JVM | Native | JS |
|-----|-----|--------|-----|
| `List<User>` | ArrayList | kof list | Array |
| `User` | User.class | struct | object |
| `String` | java.lang.String | KofString | string |

O usuário sempre vê o tipo Kof.

## 5. Fases

- Fase 3: `kof-debug` com launch JVM + breakpoints + stack + locals + stepping
- Depois: attach, exceptions, evaluation, Native, JS