# Plano — Kof Spring Starter + Independência do Spring

**Última atualização:** 23 de agosto de 2026

> Este documento transforma a especificação "Kof Spring Starter + Independência
> do Spring" em um plano executável por fases, com critérios de aceite e ordem
> de dependências.
>
> Regra absoluta: **Kof não existe para ser uma linguagem melhor para escrever
> Spring. Kof existe para ser uma linguagem completa. Spring é apenas uma das
> muitas coisas que Kof deve conseguir consumir.**

---

## 0. Arquitetura-alvo

```
                 ┌───────────────┐
                 │     Kof       │
                 │    stdlib     │
                 └───────┬───────┘
                         │
              ┌──────────┴──────────┐
              │                     │
              ▼                     ▼
       Kof Application       Spring Application
              │                     │
              ▼                     ▼
        Kof Runtime           Spring Runtime
              │                     │
              └──────────┬──────────┘
                         ▼
                        JVM
```

- O ecossistema Kof/stdlib deve conter solução nativa para toda capacidade
  fundamental (HTTP, JSON, DI, config, database, security, validation,
  logging, testing, concurrency, observability).
- Spring é interoperabilidade opcional, nunca fundação.
- Kof compila direto para bytecode JVM: `Kof → Kof compiler → JVM bytecode →
  Spring`. Nunca `Kof → Java source → javac → Spring`.

---

## 1. Princípios de decisão

Toda implementação futura deve responder:

> "Essa funcionalidade pertence à linguagem/stdlib Kof ou estamos simplesmente
> dependendo de uma abstração que o Spring já possui?"

Se for capacidade fundamental → solução Kof-native. Spring pode fornecer
implementação alternativa ou integração, nunca o requisito.

Stack Kof-native (mapeamento de referência, sem copiar APIs):

| Capacidade | Spring (referência) | Kof-native |
|------------|--------------------|------------|
| HTTP/routing | Spring MVC/WebFlux | `web.app()` + rotas (em andamento) |
| JSON | Jackson | `json.encode/decode` (JVM; nativo JSN00x) |
| DI | Spring Context | compile-time / runtime Kof |
| Config | Spring Configuration | `kof.config` tipado |
| Database | Spring Data/Hibernate | `db.query<T>` + `transaction {}` |
| Security | Spring Security | camada `kof.security` |
| Validation | jakarta.validation | validação nativa |
| Logging | SLF4J/Logback | `log.info()` estruturado |
| Scheduling | `@Scheduled` | `schedule/every/after` |
| Events | Spring Events/Kafka | filas/pub-sub nativos |
| Concurrency | executor/async | `spawn` + structured concurrency |
| Lifecycle | SpringApplication | `application { onStart/onShutdown }` |
| Testing | JUnit/Spring Test | `kof test` + suíte completa |
| Observability | Spring Actuator | métricas/health/tracing nativos |

---

## 2. Fases

### Fase 1 — Web nativa Kof ✅ (concluída em 23/08/2026)

Evoluir `kof serve` para a stack web completa. Critérios de aceite:

- [x] `web.app()` cria uma aplicação; `app.get("/hello") { return "Hello" }`
      registra rota com lambda trailing (bloco).
- [x] Path parameters: `/users/:id` + `param("id")`.
- [x] Query parameters: `query("name")`.
- [x] Headers: `header("x")`; corpo: `body()`.
- [x] JSON tipado nos handlers: `return json.encode(user)` e
      `json.decode<User>(body())`.
- [x] Middleware: `app.use { ... }` (null = continua, String = resposta curta).
- [x] `app.listen(port)`, `app.port()`, `app.close()` (graceful shutdown).
- [x] `kof serve <file.kf>` executa programas `web.app()`; API legada
      `handle(...)` continua funcionando.
- [x] Testes E2E (subprocesso + sockets reais) verdes; `mvn test` verde
      (459/459, incluindo os 9 de `KofWebE2ETest`).
- [x] Docs: `docs/stdlib-web.md`, `docs/status.md`, `docs/http.md`, README.

Implementado em: `KofWeb` (tabela compile-time), `Parser` (lambda trailing),
`SemanticAnalyzer`/`CompilerDriver` (dispatch `web.*` + contexto de request),
`JvmRuntime` (engine HTTP gerado), CLI `kof serve` (detecção de `main()`).

Gaps documentados: status codes/headers customizados (fase posterior);
target `js` reporta `WEB001`; target `native` sem servidor web ainda.

### Fase 2 — JSON nativo completo

- [ ] JVM: Float/Double em `json.encode`/`json.decode` (remove JSN001 no JVM).
- [ ] Decode de `List<User>` / arrays de objetos no JVM (JSN003).
- [ ] Native: encode/decode de objetos e records (JSN002) ou gap documentado
      e priorizado.
- [ ] Native: Float/Double.
- [ ] Testes de paridade JVM/Native.
- [ ] Jackson continua funcionando via interop (teste de interoperabilidade).

### Fase 3 — Configuração nativa (`kof.config`)

- [ ] Ambiente, arquivos de config, profiles, secrets, tipagem.
- [ ] `config.server.port`, `config.database.url` com validação em compile-time.
- [ ] Suporte a environment variables.
- [ ] Testes + docs `docs/stdlib-config.md`.

### Fase 4 — Logging + Observabilidade

- [ ] `log.info/warn/error/debug` com níveis, contexto, correlation ID,
      timestamps, structured logging (JSON).
- [ ] Métricas, health checks, tracing hooks; OpenTelemetry/JFR/JMX como
      integração opcional (nunca requisito).
- [ ] Testes + docs.

### Fase 5 — Database + Transactions

- [ ] `db.query<T>("select ... where id = ?", id)` sobre JDBC (interop JVM).
- [ ] `transaction { ... }` com commit/rollback automáticos.
- [ ] Connection pooling e configuração tipada.
- [ ] Hibernate suportado como backend opcional via interop (teste).
- [ ] Testes + docs `docs/stdlib-database.md`.

### Fase 6 — Concurrency completa

- [ ] `await`, filas (`kof.concurrent.Queue`), canais, cancellation, timeouts.
- [ ] Structured concurrency e supervision sem expor Thread/Executor.
- [ ] Scheduler nativo para `spawn` no Native (CONC001).
- [ ] Testes + docs `docs/stdlib-concurrency.md`.

### Fase 7 — Security nativa

- [ ] Password hashing, JWT, sessions, cookies, CSRF, CORS, security headers,
      rate limiting, TLS.
- [ ] Auth/authorization declarativa.
- [ ] Spring Security como alternativa de interop (teste), nunca requisito.
- [ ] Testes + docs `docs/stdlib-security.md`.

### Fase 8 — Validation + Scheduling + Events

- [ ] Validação nativa sem jakarta.validation.
- [ ] `schedule/every/after` sem `@Scheduled`.
- [ ] Eventos/filas/pub-sub com backends Kafka/RabbitMQ/JMS/NATS opcionais.
- [ ] Testes + docs.

### Fase 9 — DI nativa + Application lifecycle

- [ ] Resolução de dependências em compile-time quando possível
      (`service UserService(UserRepository repository)` ou equivalente).
- [ ] `application { onStart/onShutdown }` sem SpringApplication.
- [ ] Testes + docs.

### Fase 10 — Testing nativo completo

- [ ] `kof test` evoluído: unit, integration, HTTP tests, database tests,
      mocks/fakes, fixtures, property tests, benchmarks, stress.
- [ ] JUnit continua interoperável (teste).
- [ ] Testes + docs.

### Fase 11 — CLI completa

- [ ] `kof run/build/test/serve` consolidados; ferramentas futuras:
      database, migration, configuration, deployment, observability.
- [ ] `kofdeps` / `kof init` / `kof install` (dependency management).

### Fase 12 — Aplicação web completa sem Spring (teste obrigatório)

- [ ] Aplicação Kof com HTTP + routing + JSON + database + transactions +
      auth + authorization + validation + logging + metrics + tracing +
      concurrency + testing, sem nenhuma dependência Spring.
- [ ] Repositório de exemplo oficial (ex.: `examples/web-app`).

### Fase 13 — Spring Starter (`kof spring starter`)

Somente depois da Fase 12 (ou em paralelo sem criar dependência
arquitetural). Critérios de aceite:

- [ ] `kof spring starter` consulta metadata do Initializr oficial
      (https://start.spring.io/).
- [ ] Escolhe versões compatíveis e gera o projeto Spring.
- [ ] Integra o compilador Kof: source sets, classes Kof compiladas a
      bytecode (nunca Java source como backend).
- [ ] Mantém interoperabilidade JVM (controller/service/entity/DTO/security
      Kof consumidos pelo Spring).
- [ ] Testes de interoperabilidade: Kof Controller → Spring MVC → HTTP;
      Kof Service → Spring DI; Kof Entity → Hibernate; Kof DTO → Jackson;
      Kof security → Spring Security.
- [ ] Docs: `docs/spring.md`.

### Fase 14 — Documentação e Training

- [ ] README, docs/, learn/, training/ atualizados.
- [ ] `docs/spring.md`, `docs/stdlib-web.md`, `docs/stdlib-database.md`,
      `docs/stdlib-security.md`, `docs/stdlib-concurrency.md`.
- [ ] `training/` ensina primeiro Kof-native, depois Kof + Spring.
- [ ] Declaração explícita: "Spring é suportado pelo Kof, mas não é
      necessário para desenvolver aplicações Kof."

---

## 3. Regras estruturais permanentes

1. Nenhum componente da stdlib Kof pode depender de Spring.
2. Nenhum backend do compilador pode gerar Java source como passo
   intermediário obrigatório.
3. Capacidades fundamentais têm API Kof-native; Spring é alternativa.
4. O teste de independência (aplicação Kof sem Spring) é tão importante
   quanto o teste de interoperabilidade (Kof consumindo Spring).
5. Cada fase termina com testes verdes (`mvn test`) e documentação.
6. O roadmap existente (`docs/roadmap.md`) permanece a visão de longo prazo;
   este documento é o plano de execução da independência + starter.

---

## 4. Nota de operação: múltiplas sessões na mesma branch

Durante a execução da Fase 1 (23/08/2026), sessões paralelas trabalharam na
mesma branch (`feat/kofjs`) ao mesmo tempo. Isso causou conflitos reais que
foram resolvidos e devem ser esperados em sessões futuras:

- **Builds Maven concorrentes sobre os mesmos `target/`**: dois `mvn`
  simultâneos produzem erros transitórios (stale classpath, `cannot find
  symbol`, `surefire` silencioso). Mitigação: aguardar builds alheios
  terminarem ou testar em um clone isolado
  (`git clone` + `-Dmaven.repo.local=<dir>` dedicado).
- **`${revision}` no parent pom**: instalar a partir de um diretório
  sandbox sem a propriedade `<revision>` polui `~/.m2` com um descriptor
  quebrado (`kof-parent:pom:${revision}`). Limpar `~/.m2/repository/dev/kof`
  e reinstalar a partir da raiz do repositório.
- **Arquivos mid-edit**: `NativeRuntime.java` (faltava `}`), `JsBackend.java`
  (`writeHtmlEntry` referenciado antes de definido), `JvmRuntime.java`
  (template com escapes `\"` dentro de text blocks — em text block, para
  gerar `\"` no código-fonte é preciso escrever `\\"`). Todos foram
  corrigidos mecanicamente e documentados aqui.
- **Pegadinha de text block**: código de runtime embutido em
  `JvmRuntime.java`/`JsBackend.java` é gerado a partir de text blocks Java.
  Escapes de string (`\"`, `\n`) sofrem DUPLA interpretação. Escrever
  `\\"`/`\\n` no text block para produzir `\"`/`\n` no código gerado.
- **`pkill -f` suicida**: padrões que casam com a própria linha de comando
  do shell matam a sessão. Usar padrões distintos ou `kill` por PID.
- **Commit intercalado**: sessões podem commitar o trabalho umas das outras
  (`git add -A`). Sempre verificar `git status`/`git log` antes de assumir o
  estado da árvore; nunca rebasear sobre trabalho alheio em andamento.

Resultado: a Fase 1 foi validada em um clone isolado com repositório local
Maven dedicado, e o estado final da árvore de trabalho contém o trabalho das
duas frentes (web nativa + debugger/UI/security da sessão paralela).