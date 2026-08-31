# Plano de Ação — Kof como Plataforma Completa de Desenvolvimento

**Data:** 27 de agosto de 2026
**Base:** estado real 0.2.6-beta (658 testes: 650 kof-compiler +8 kof-script +5 kof-c-compiler, 6 targets: jvm stable, native x86_64 stable free-list + kof_gc_collect, native.riscv64 via riscv64-linux-gnu-as, native.aarch64 placeholder, js alpha GraalJS kof.http, kofc native-only; pattern matching + null safety básica; KofScriptGlobals; kof http JVM+JS)
**Versão:** 0.2.6-beta
**Filosofia:** [docs/philosophy.md](philosophy.md) · Auditoria: [docs/ecosystem-coverage.md](ecosystem-coverage.md) · Visão: [docs/roadmap.md](roadmap.md) · Status: [docs/status.md](status.md)

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

## Estado atual (o que já existe — não refazer, 0.2.6-beta)

| Área | Estado |
|------|--------|
| Frontend + IR + 6 targets | ✅ estável (JVM V21, ELF x86_64 free-list + riscv64/aarch64, ES Modules GraalJS, KofC, KofScript) |
| Linguagem | classes, records, herança, interfaces, generics erasure (`Box<T>` via `substituteTypeVariable`), lambdas c/ capturas, exceptions 3 targets, `spawn` JVM, **pattern matching `case String s` + `Point(x,y)`** e **`String?` null safety básica** (27/08) |
| stdlib | `web.app()` (rotas/middleware), `kof.io`, `kof.time`, `kof.config` (JVM+Native free-list), `kof.log` (JVM+Native), `kof.security` v1+G9 (3 targets), `kof.db` (JDBC + SQLite native + MySQL `kof_db_mysql_scramble`), `kof.orm` (entity, CRUD, where com operadores, saveAll, page, migrate, MongoDB), `kof.process`, `kof.ui` (KofJS/webview), `kof.http` (JVM+JS), `List map/filter/reduce` |
| Tooling | build/run/serve/check/test/bench/debug(DAP MVP)/info/lsp/install/script/repl/c, `kof script --watch` (SIGPIPE fix), packaging multiplataforma (`scripts/package.sh` PASS), CI `release.yml` single job JDK 21, golden 16/16, integration 9/9 |
| Corpus | `docs/` (0.2.6-beta), `learn/` (38 capítulos), `training/` |
| Build | `mvn clean package` PASS, `mvn test` 658 (650+8+5), `VERSION` 0.2.6-beta |

---

## P0 — Higiene e fechamento de paridade *(fechado 0.1.0; revisitado 0.2.6-beta)*

Tudo o resto depende disso. Pequeno, alto valor.

| Item | O quê | Aceite |
|------|-------|--------|
| ✅ G7 *(feito 0.0.14 → 0.1.0)* | `jwt.*` com entrada explícita em `supportedOn` — Native reporta SECN004 em compile-time; `supportedOn` revisado função a função | zero erros de link silenciosos no security/web |
| ✅ G6 *(feito 0.0.14 → 0.1.0)* | `kof.test` estruturado: `test "nome" { }`, runner sintetizado em compile-time (desugar → `kof_test_N`), PASS/FAIL por nome nos 3 targets, `--target js` no CLI, `process.exit(code)` nos 3 targets | `StructuredTestE2ETest` 11/11; golden/integração verdes (16/16, 9/9 em 0.2.0) |
| ✅ JSON Native *(25/08)* | JSN003 fechado (arrays `Int/Long/Bool/String[]`); JSN001/JSN002 seguem com FLT001/JSN002 | parity JSON total JVM×Native parcial |
| ✅ Config/Log Native *(LOG001 ✅ 0.0.14; CONFIG001 ✅ 0.1.0)* | `kof.log`/`kof.config` Native completo (asm, free-list 27/08) | LOG001 ✅; CONFIG001 ✅ |
| ✅ 0.2.6-beta target separation | `Target.NATIVE_RISCV64/AARCH64` + `parseTarget native.riscv64/aarch64` | `kof build --target native.riscv64` toolchain `riscv64-linux-gnu-as` + `.option arch,rv64g` + `li a7 214/64/93`; aarch64 placeholder |
| ✅ 0.2.6-beta GC + MySQL + Bugs | free-list `kof_free_head` + `kof_gc_collect` (Native), `kof_db_mysql_scramble` (MySQL handshake), large-project `import a.b.C` (`CompilerDriver.java:243`), `List.get`/`listOf`, `release.yml` single job JDK 21, SIGPIPE Windows | `mvn test` 658, `VERSION` 0.2.6-beta |
| Processo doc | checklist DoD-doc no PR template; `status.md` regenerado por release (27/08 0.2.0) | docs nunca mais defasam 9 versões |

## P1 — Linguagem: coleções e expressividade *(fundação das APIs — fechado 0.2.6-beta)*

Sem isso, os módulos das fases seguintes nascem tortos.

| Item | O quê | Por que agora | Estado 0.2.6-beta |
|------|-------|---------------|-------------------|
| `Map<K,V>` / `Set<T>` | runtime nos 3 targets, API ≤ 12 métodos (get/put/remove/contains/keys/values/count) | validation, cache, sessions e query DSL precisam | ✅ 0.1.0 (3 targets, asm) |
| `enum` | declaração + `values()/valueOf` + switch exaustivo em compile-time | modelagem de domínio (roles, status) sem strings soltas | ✅ 0.1.0 |
| `await`/resultado de `spawn` | `var r = spawn tarefa()` + `await r`; `kof.concurrent.Queue` | concorrência útil de verdade; scheduler depois | ✅ 0.1.0 (JVM) |
| Pattern matching | `switch` com tipos/destructuring sobre records/enums | substitui cadeias de instanceof; fecha G11 parcial | ✅ 0.2.6-beta — `case String s` + `Point(x,y)` JVM/Native/JS |
| Nullability | `Type?` explícito + checagem em compile-time (sem Option no core) | elimina NPE por classe de erro, não por convenção | ✅ 0.2.6-beta — `String?` básica |
| Módulos multi-arquivo + `List map/filter/reduce` | resolução de símbolos entre arquivos + higher-order | `kof build <dir>` e coleções funcionais | ✅ 0.2.6-beta — `import a.b.C` fix (`CompilerDriver.java:243`) + `List map/filter/reduce` (3 targets) |

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
