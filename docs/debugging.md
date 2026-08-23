# DEBUGGING.md — Depuração Kof (visão de uso)

**Status:** Fase 1 em implementação (source mapping na IR); JVM/DAP planejado
**Data:** 23 de agosto de 2026

---

## 1. Experiência

Depurar Kof é depurar Kof — em qualquer target:

```text
  40 | User find(Int id) {
  41 |     var user = repository.find(id)
● 42 |     return user
  43 | }
```

Ao parar:

```text
CALL STACK

UserService.find       UserService.kf:42
UserController.get     UserController.kf:18
main                    Application.kf:7
```

```text
VARIABLES

id      Int        42
user    User
  name             "Mel"
  active           true
```

O usuário nunca precisa saber JVM bytecode, assembly ou JavaScript.

## 2. Comandos planejados

```bash
kof debug app.kf
kof debug --target jvm app.kf
kof debug --target native app.kf
kof debug --target js app.kf
kof debug --attach <pid>        # futuro
kof build app.kf --debug        # metadata extra
kof build app.kf --release
```

## 3. Capacidades

- breakpoints em source (`UserService.kf:42`)
- continue, step over/into/out, pause, restart, terminate
- stack traces com nomes e linhas Kof
- scopes, locals, arguments, campos de classe, coleções (tipos Kof)
- exceções (break on throw / uncaught) com stack Kof
- avaliação de expressões: planejada (com respeito ao type system)

## 4. Integração

```text
Kof Editor
    ├── LSP ────► Kof Language Server (diagnostics, symbols, hover)
    └── DAP ────► kof-debug
                      ├── JVM (JDWP)
                      ├── Native (DWARF)
                      └── JS (Node Inspector)
```

LSP e DAP não se misturam: LSP = código; DAP = execução.

## 5. Estado

- Fase 1 (DebugInfo na IR) — em implementação
- Fases 2-7 — planejadas; ver `debugger-architecture.md`