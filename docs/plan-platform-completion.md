# Plano de Ação — Kof como Plataforma Completa de Desenvolvimento

**Data:** 24 de agosto de 2026
**Base:** estado real 0.0.14-alpha (527 testes, 3 backends, kof.web/db/orm/security/ui)
**Filosofia:** [docs/philosophy.md](philosophy.md) · Auditoria: [docs/ecosystem-coverage.md](ecosystem-coverage.md) · Visão: [docs/roadmap.md](roadmap.md)

---

## Princípios que governam este plano

1. **Intenção → Kof → compilador → backend.** Nenhuma feature nova expõe
   mecanismo (`Thread`, `HttpClient`, driver de banco) na linguagem.
2. **Nunca silencioso.** O que um target não realiza vira diagnostic em
   compile-time com código (`CONC001`, `DB001`...) — nunca erro de link.
3. **Compile-time > runtime magic.** Schema, validação, rotas e config são
   conhecidos do compilador; reflection é interop, não fundação.
4. **JVM primeiro, port depois.** Módulos pesados nascem no JVM, ganham
   parity Native/JS com gaps documentados até completarem.
5. **API pequena.** Cada namespace nasce com ≤ 10 funções úteis.

---

## Definition of Done (toda feature, sem exceção)

Seguir `docs/performance.md` §40-§41. Resumo operacional:

- [ ] Compila nos 3 targets **ou** diagnostico de gap com código e entrada
      em `supportedOn` (G7 nunca reabre).
- [ ] Testes E2E reais por target afetado + caso no `BackendParityTest`.
- [ ] Benchmark/stress quando impacto de performance for plausível;
      baseline atualizado (`kof bench --update-baseline`).
- [ ] Docs sincronizadas no mesmo PR: `status.md`, `backend-parity.md`,
      `ecosystem-coverage.md`, capítulo `learn/` se visível ao usuário,
      entrada em `training/` (LLM corpus é estratégia oficial).
- [ ] `mvn test`, golden e integração verdes.

---

## Estado atual (o que já existe — não refazer)

| Área | Estado |
|------|--------|
| Frontend + IR + 3 backends | ✅ estável (JVM V21, ELF x86-64, ES Modules) |
| Linguagem | classes, records, herança, interfaces, generics erasure, lambdas c/ capturas, exceptions 3 targets, `spawn` JVM |
| stdlib | `web.app()` (rotas/middleware), `kof.io`, `kof.time`, `kof.config` (JVM), `kof.log` (JVM, JSON+correlation), `kof.security` v1 (3 targets), `kof.db` (JDBC + SQLite nativo; MySQL WIP), `kof.orm` (entity, CRUD, where, migrate, MongoDB), `kof.process`, `kof.ui` (render KofJS/webview) |
| Tooling | build/run/serve/check/test/bench/debug(DAP MVP)/info/lsp/install, packaging multiplataforma, CI + releases automáticas |
| Corpus | `docs/`, `learn/` (38 capítulos), `training/` |

---

## P0 — Higiene e fechamento de paridade *(base; ~1 semana)*

Tudo o resto depende disso. Pequeno, alto valor.

| Item | O quê | Aceite |
|------|-------|--------|
| ✅ G7 *(feito 0.0.14)* | `jwt.*` com entrada explícita em `supportedOn` — Native reporta SECN004 em compile-time; `supportedOn` revisado função a função | zero erros de link silenciosos no security/web |
| ✅ G6 *(feito 0.0.14)* | `kof.test` estruturado: `test "nome" { }`, runner sintetizado em compile-time (desugar → `kof_test_N`), PASS/FAIL por nome nos 3 targets, `--target js` no CLI, `process.exit(code)` nos 3 targets | `StructuredTestE2ETest` 11/11; golden/integração verdes |
| JSON Native | fechar JSN001 (Float/Double), JSN002 (objetos), JSN003 (arrays) | parity JSON total JVM×Native no BackendParityTest |
| 🟡 Config/Log Native *(LOG001 ✅ feito 0.0.14)* | `kof.log` Native completo (asm: data civil, env scan próprio, stderr); falta `kof.config` no Native (env + arquivo) e `KOF_LOG_JSON` nativo | LOG001 ✅; CONFIG001 pendente |
| Processo doc | checklist DoD-doc no PR template; `status.md` regenerado por release | docs nunca mais defasam 9 versões |

## P1 — Linguagem: coleções e expressividade *(fundação das APIs)*

Sem isso, os módulos das fases seguintes nascem tortos.

| Item | O quê | Por que agora |
|------|-------|---------------|
| `Map<K,V>` / `Set<T>` | runtime nos 3 targets, API ≤ 12 métodos (get/put/remove/contains/keys/values/count) | validation, cache, sessions e query DSL precisam |
| `enum` | declaração + `values()/valueOf` + switch exaustivo em compile-time | modelagem de domínio (roles, status) sem strings soltas |
| `await`/resultado de `spawn` | `var r = spawn tarefa()` + `await r`; `kof.concurrent.Queue` | concorrência útil de verdade; scheduler depois |
| Pattern matching | `switch` com tipos/destructuring sobre records/enums | substitui cadeias de instanceof; fecha G11 parcial |
| Nullability | `Type?` explícito + checagem em compile-time (sem Option no core) | elimina NPE por classe de erro, não por convenção |
| Módulos multi-arquivo | resolução de símbolos entre arquivos do mesmo projeto | `kof build <dir>` já compila múltiplos arquivos; falta semântica unificada |

## P2 — Plataforma web completa *(o coração "sem Spring")*

| Item | Gap | Escopo mínimo idiomático |
|------|-----|--------------------------|
| `kof.http` client | G2 | `http.get/post(url)` + headers/body/timeout; JSON automático; roda dentro de `spawn` |
| Resposta rica | status list | `return status(201, body)` / `header("X", "y")` no handler |
| Validation | G4 | `entity`/record com constraints (`required`, `min`, `pattern`) checadas em compile-time; runtime valida input web |
| Sessions/rate limit/API keys | G9 | `session` no contexto web; `app.rate("/login", "10/minute")`; tudo server-side sem container |
| TLS/HTTPS | G12 | certconfig no `app.listen(port, cert: ...)`; Native segue syscalls existentes |
| WebSocket/SSE | roadmap §3 | `app.ws("/chat") { ... }` — eventos como lambdas |
| Scheduler | G8 | `every(30s) { ... }`, `at("0 3 * * *") { ... }` sobre virtual threads |
| Cache | roadmap §3 | `cache.get/set/ttl` in-process (Map + TTL — depende de P1) |
| **App E2E obrigatória** | Fase 12 | blog/API completa: rotas + entity + auth JWT + validation + UI KofJS; roda em jvm e native sem mudar uma linha |

## P3 — Data nível produção

| Item | Referência |
|------|-----------|
| Query DSL tipada (nível 3) | `User.query(db) { where age > 18; orderBy name; limit 10 }` — lowering para SQL prepared; sem strings |
| Connection pooling | pool in-process configurável por DSN |
| MySQL/MariaDB nativo completo | handshake/auth (scramble SHA-1 feito), query, prepared statements, transactions |
| kof.db/kof.orm fora do JVM | JS: SQLite WASM ou DB001 definitivo documentado; Native: ORM sobre o SQLite nativo |
| Pagination/offset padrão | `page(n, size)` no query DSL |

## P4 — Operação e observabilidade

| Item | Escopo |
|------|--------|
| Health checks | `app.health("/health")` default readiness/liveness |
| Métricas | counter/gauge/histogram in-process + endpoint `/metrics` (formato Prometheus) |
| Request ID/tracing | correlation ID do kof.log propagado por web/spawn/db; spans básicos |
| OpenTelemetry | export opcional (interop JVM primeiro) |
| Lifecycle | `application { onStart { }; onShutdown { } }` — DI leve por convenção (construtores + registro), sem container |

## P5 — Developer Experience

| Item | Notas |
|------|-------|
| `kof fmt` | formatter baseado no parser real (mesmo frontend do LSP); estilo do training corpus |
| LSP completo | hover, completion, go-to-definition, rename, semantic tokens — frontend único já alimenta diagnostics |
| REPL/KofScript | avaliação incremental sobre a IR (decisão JIT/interpret após benchmarks — roadmap §1) |
| `kof init` + `kofdeps` | projeto scaffold; dependências Maven resolvidas via Tooling API sem pom manual (roadmap §6) |
| Debugger fases 4-7 | integração editor (breakpoints UI), locals/stepping/watch, Native DWARF, JS source maps |
| Extensão VS Code oficial | grammar + LSP + debug adapter empacotados |

## P6 — Longo prazo (não bloqueia nada acima)

- **Frontend declarativo gerado**: `page/column/button` → HTML/CSS/JS otimizados, SSR, shared models front/back (roadmap §8-9).
- **Monólito → serviços**: particionamento de projeto sem reescrita (roadmap §11).
- **Auto-hospedagem**: componentes do compilador reescritos em Kof quando P1-P3 estabilizarem (roadmap §18).

---

## Ordem de execução e paralelismo

```text
P0 ──► P1 ──► P2 ──► P4 ──► app E2E final
 │             └──► P3 ──┘        (web+data juntos)
 └─────────────► P5 (fmt/LSP podem avançar em paralelo desde P0)
```

- P0 é serial e vem antes de tudo (fecha a filosofia "nunca silencioso").
- P1 precede P2/P3: Map/Set/enum/nullability são matéria-prima das APIs.
- P2 (web) e P3 (data) paralelizáveis entre pessoas/branches após P1.
- A **aplicação exemplo completa** (P2/P3 junta) é o marco de aceite da
  plataforma: um único projeto Kof com backend, banco, auth e UI.

## Métricas de sucesso

1. Blog/API de referência roda idêntico em `--target jvm` e `--target native`
   (golden diff vazio), com benchmark baseline registrado.
2. Zero gaps silenciosos: todo "não suportado" tem código de diagnostic e
   entrada em `backend-parity.md`.
3. Onboarding medido: `kof init && kof run` < 60s do clone ao hello-world
   web com banco.
4. Corpus LLM cobre 100% das APIs públicas (`training/` sincronizado).

## Não fazer (guardrails)

- Nenhum framework interno gigante; namespaces pequenos e composíveis.
- Nenhuma annotation-driven magic; constraints e rotas são sintaxe da
  linguagem ou lambdas.
- Não portar módulo para target novo sem o DoD completo (evita dívida
  "quase funciona" em três lugares).
- Não começar P6 (frontend gerado/auto-hospedagem) antes de P0-P3 fechados.
