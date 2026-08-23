# stdlib log — Logging Nativo do Kof

**Última atualização:** 23 de agosto de 2026
**Status:** implementado (Fase 4 do plano de independência do Spring)

---

## 1. Filosofia

> Logging é parte da plataforma Kof, não de um framework (SLF4J/Logback são
> interoperabilidade, nunca requisito).

## 2. API

```kof
log.debug("detail")
log.info("request started")
log.warn("slow response: " + ms)
log.error("failed: " + message)
```

Cada chamada aceita uma `String` (concatene com `+`). Formato da linha:

```
2026-08-23 12:09:22.715 INFO hello from kof
```

- `info`/`debug` → stdout
- `warn`/`error` → stderr

## 3. Níveis

Controlados pela variável de ambiente `KOF_LOG_LEVEL`
(`debug < info < warn < error < off`; default `info`).

| Nível | Mensagens exibidas |
|-------|--------------------|
| `debug` | debug, info, warn, error |
| `info` (default) | info, warn, error |
| `warn` | warn, error |
| `error` | error |
| `off` | nenhuma |

```bash
KOF_LOG_LEVEL=debug kof run app.kf
KOF_LOG_LEVEL=off kof run app.kf
```

## 4. Contexto web

Funciona dentro de handlers da stack web (mesmo runtime gerado):

```kof
app.get("/users") {
    log.info("users listed")
    return "[]"
}
```

## 5. Targets

| Target | Estado |
|--------|--------|
| JVM | ✅ completo |
| Native | LOG001 (gap documentado em compile-time) |
| JS | LOG001 (gap documentado em compile-time) |

## 6. Testes

`KofLogE2ETest` — 7 testes E2E: nível default, debug visível com
`KOF_LOG_LEVEL=debug`, supressão em `error`, `off` silencioso, warn no
stderr, log dentro de handler web e LOG001 nos targets native/js.

## 7. Arquitetura

```
Kof source (.kf) → KofLog (tabela compile-time)
   → SemanticAnalyzer (tipos) → CompilerDriver (KofCall kof_log_*)
   → dev.kof.runtime.KofRuntime (gerado): nível + timestamp + stream
```

Evolução planejada (Fase 4 completa): structured logging (JSON),
correlation ID por request, contexto por tarefa.