# Kof Standard Library — Security + Enterprise Capability Audit

> Documento arquitetural permanente.
>
> Objetivo: mapear as capacidades do ecossistema Spring, auditar o estado
> atual da Standard Library Kof e definir a arquitetura de `kof.security`
> como fundação enterprise da plataforma.
>
> Regra fundamental: **não copiar Spring, não replicar APIs, não criar
> wrappers.** Construir a solução Kof para os mesmos problemas.

---

# 1. PRINCÍPIO

Uma aplicação Kof deve construir uma aplicação enterprise completa usando a
própria plataforma Kof:

```text
HTTP, REST, auth, autorização, validação, serialização, database,
messaging, observabilidade, testing
```

Spring continua válido como alternativa externa — nunca como dependência
arquitetural.

---

# 2. MATRIZ DO ECOSSISTEMA SPRING

Legenda de status para a Standard Library Kof:

```text
EXISTS           → já implementado na plataforma Kof
PARTIAL          → existe, mas com lacunas conhecidas
MISSING          → não existe, prioridade definida
NOT APPLICABLE   → não se aplica à arquitetura Kof
```

## 2.1 Spring Framework

| Capacidade | Spring | Kof | Status | Notas |
|-----------|--------|-----|--------|-------|
| Core (beans, container) | Core/Beans/Context | — | MISSING (baixa prioridade) | Kof não necessita de container; funções/estado global cobrem a maioria dos casos |
| Dependency Injection | Core/Context | — | MISSING (baixa) | Decisão futura deliberada; ver §3 |
| Expression Language | SpEL | — | MISSING (média) | Pode ser resolvido com funções de primeira classe |
| AOP | AspectJ | — | MISSING (média) | Composição de funções + middleware cobre casos comuns |
| Validation | spring-validation | `kof.validation` (planejado) | MISSING (alta) | See security/validation |
| Web | spring-web | `kof.web` (`web.app()`) | EXISTS | Rotas, params, query, headers, body, middleware `app.use` |
| Web MVC | WebMVC | `kof.web` + handlers | PARTIAL | Só JVM hoje; JS embarcado (alpha) |
| WebFlux | WebFlux | `spawn` + virtual threads | PARTIAL | Modelo concorrente próprio |
| WebSocket | WebSocket | — | MISSING (média) | Depende do servidor HTTP |
| Messaging | spring-messaging | `kof.messaging` (planejado) | MISSING (alta) | |
| Transactions | spring-tx | `kof.database` (planejado) | MISSING (alta) | |
| Scheduling | spring-context | `kof.concurrent` (spawn existe) | PARTIAL | scheduler nativo planejado |
| Events | ApplicationEvent | — | MISSING (média) | Pode ser idioma + filas |
| Resources | Resource | `kof.io` | EXISTS | |
| Cache | spring-cache | — | MISSING (média) | |
| Conversion | ConversionService | type system Kof | NOT APPLICABLE | Tipagem estática resolve em compile-time |
| Testing | spring-test | `kof test` + `assert` + JUnit (interno) | PARTIAL | |

## 2.2 Spring Boot

| Capacidade | Spring Boot | Kof | Status | Notas |
|-----------|-------------|-----|--------|-------|
| Lifecycle | run() | `main()` + `web.app().listen()` | EXISTS | |
| Configuração | application.yml | — | MISSING (alta) | `kof.config` planejado (env + arquivo) |
| Profiles | profiles | — | MISSING (média) | |
| Dependency management | starters | — | NOT APPLICABLE | Sem dependências: a stdlib É a plataforma |
| Auto configuration | auto-config | — | NOT APPLICABLE | Compilador sabe o que o programa usa |
| Embedded servers | Tomcat/Jetty | `KofHttpServer` | EXISTS | JVM apenas |
| Actuator | actuator | — | MISSING (alta) | `kof.observability`/health planejado |
| Health checks | health | — | MISSING (alta) | |
| Metrics | micrometer | `kof bench`/`kof profile` (tooling) | PARTIAL | Runtime metrics planejadas |
| Observability | tracing | — | MISSING (média) | |
| Logging | logback | `println`/`System.err` | PARTIAL | `kof.logging` planejado |
| Graceful shutdown | shutdown | `web.close()` + spawn join | PARTIAL | |
| CLI/tooling | spring CLI | `kof` CLI (build/run/serve/test/bench/profile/inspect) | EXISTS | |

## 2.3 Spring Security

| Capacidade | Spring Security | Kof | Status | Prioridade |
|-----------|----------------|-----|--------|-----------|
| Password hashing | BCrypt | `kof.security.passwords` | **EXISTS (esta etapa)** | CRÍTICA |
| Authentication | AuthenticationManager | `kof.security.auth` | **EXISTS (esta etapa)** | CRÍTICA |
| Authorization (roles) | authorize | `kof.security.auth.hasRole` | **EXISTS (esta etapa)** | CRÍTICA |
| Authorities/permissions | authorities | `kof.security.auth.hasPermission` | **EXISTS (esta etapa)** | ALTA |
| Sessions | session management | `kof.security.sessions` (planejado) | MISSING | ALTA |
| Security context | SecurityContext | `kof.security.auth` (contexto de request) | **EXISTS (esta etapa)** | CRÍTICA |
| CSRF | CsrfFilter | `kof.security.security.csrf*` | **EXISTS (esta etapa)** | ALTA |
| CORS | CorsFilter | `kof.security.security.cors*` | **EXISTS (esta etapa)** | ALTA |
| OAuth2 client | OAuth2Client | `kof.security.oauth` (arquitetura) | MISSING | MÉDIA |
| OIDC | OIDC | idem | MISSING | MÉDIA |
| JWT | Nimbus/JJWT | `kof.security.jwt` | **EXISTS (esta etapa)** | CRÍTICA |
| Resource server | Bearer | `kof.security.jwt` + `auth` | **EXISTS (esta etapa)** | ALTA |
| Method security | @PreAuthorize | `auth.requireRole` (middleware) | **EXISTS (esta etapa)** | ALTA |
| Security headers | headers | `kof.security.security.*` (helpers) | **EXISTS (esta etapa)** | ALTA |
| Request filtering | filter chain | `app.use` (middleware) | **EXISTS** | CRÍTICA |
| Remember-me | remember-me | — | MISSING | BAIXA |
| Logout | logout | — | MISSING | MÉDIA |
| Security events | events | — | MISSING | BAIXA |

## 2.4 Spring Data

| Capacidade | Spring Data | Kof | Status | Prioridade |
|-----------|-------------|-----|--------|-----------|
| Repository abstração | Commons | `kof.database` (planejado) | MISSING | ALTA |
| JPA | JPA | — | MISSING | MÉDIA (decidir: JDBC direto é mais Kof) |
| JDBC | JDBC | `kof.database` (planejado) | MISSING | ALTA |
| R2DBC | R2DBC | — | MISSING | MÉDIA |
| MongoDB | Mongo | — | MISSING | MÉDIA |
| Redis | Redis | — | MISSING | MÉDIA |
| REST exports | Data REST | `kof.rest` (planejado) | MISSING | MÉDIA |
| Migrations | Flyway/Liquibase | — | MISSING | ALTA |

## 2.5 Spring Integration / Cloud / Batch / GraphQL / Session / Kafka / AMQP / Pulsar / WS / HATEOAS / REST Docs / Modulith / Authorization Server

| Capacidade | Spring | Kof | Status | Prioridade |
|-----------|--------|-----|--------|-----------|
| Messaging channels | Integration | `kof.messaging` (planejado) | MISSING | MÉDIA |
| Retry/error handling | Integration | — | MISSING | MÉDIA |
| Service discovery | Cloud | — | MISSING | BAIXA (config manual) |
| Gateway | Cloud Gateway | `kof.web` + proxy | PARTIAL | BAIXA |
| Circuit breakers | Resilience | — | MISSING | BAIXA |
| Distributed tracing | Sleuth | — | MISSING | BAIXA |
| Batch (jobs/steps/retry) | Batch | — | MISSING | MÉDIA |
| GraphQL | GraphQL | — | MISSING | BAIXA (REST primeiro) |
| Distributed sessions | Session | `kof.security.sessions` (planejado) | MISSING | BAIXA |
| Kafka producer/consumer | Kafka | `kof.messaging` (planejado) | MISSING | MÉDIA |
| AMQP queues | AMQP | idem | MISSING | MÉDIA |
| Pulsar | Pulsar | idem | MISSING | BAIXA |
| SOAP/XML | WS | — | NOT APPLICABLE | REST/JSON é o idioma Kof |
| Hypermedia | HATEOAS | — | MISSING | BAIXA |
| API docs | REST Docs | `docs/` + training | PARTIAL | MÉDIA |
| Module boundaries | Modulith | packages + type system | PARTIAL | BAIXA |
| OAuth2 authorization server | Auth Server | `kof.security.oauth` (arquitetura) | MISSING | MÉDIA |

---

# 3. DECISÕES ARQUITETURAIS

## 3.1 DI / container

Kof não possui container de beans. Funções de primeira classe, lambdas e
estado de módulo cobrem a composição. Um contêiner DI seria cerimônia sem
ganho de intenção. **Decisão: NOT APPLICABLE para o núcleo; reavaliar apenas
se um padrão de necessidade surgir (plugins/SPI).**

## 3.2 Database

`kof.database` será SQL-first (JDBC idiomático), sem ORM pesado. Records do
Kof + SQL explícito cobrem a maioria das aplicações com menos camadas.
JPA-style ORM é decisão futura, não pressuposto.

## 3.3 Observabilidade

`kof.metrics`/`kof.observability` seguirão o padrão das métricas já
existentes no tooling (`kof bench`, `kof profile`): coletar no runtime,
expor de forma simples, integrar com JFR/perf/V8 quando existir.

## 3.4 Security

`kof.security` é implementado como **namespaces compilados** (mesmo padrão
de `kof.io`/`kof.web`): a intenção é expressa diretamente, o compilador
resolve a função de runtime e cada target fornece a implementação.

---

# 4. AUDITORIA DA STANDARD LIBRARY KOF (estado real)

| Módulo | Existe? | Idiomático? | Seguro? | Multi-target | Performático? | Testes | Docs |
|--------|---------|-------------|---------|--------------|---------------|--------|------|
| `kof.core` (println, aritmética, strings, arrays) | SIM | SIM | SIM | JVM/Native/JS | SIM | SIM | SIM |
| `kof.collections` (List, listOf) | SIM | SIM | SIM (bounds) | JVM/Native/JS | PARCIAL (boxing List<Int> no JVM) | SIM | SIM |
| `kof.io` (File/Path/Directory, readFile) | SIM | SIM | SIM | JVM/Native/JS | SIM | SIM | SIM |
| `kof.time` (`now()`) | SIM | SIM | SIM | JVM/Native/JS | SIM | SIM | SIM |
| `kof.json` (encode/decode) | SIM | SIM | PARCIAL (objetos só JVM; Float/Double gap) | JVM/Native/JS | SIM | SIM | SIM |
| `kof.http` (serve) | SIM | SIM | PARCIAL (ver abaixo) | JVM | SIM | SIM | SIM |
| `kof.web` (web.app, rotas, middleware) | SIM | SIM | PARCIAL (auth em construção) | JVM | SIM | SIM (novo) | SIM |
| `kof.rest` | NÃO | — | — | — | — | — | — |
| `kof.database` | NÃO | — | — | — | — | — | — |
| `kof.security` | NÃO (esta etapa implementa) | — | — | — | — | — | — |
| `kof.concurrent` (spawn) | SIM | SIM | SIM | JVM (native CONC001) | SIM | SIM | SIM |
| `kof.messaging` | NÃO | — | — | — | — | — | — |
| `kof.validation` | NÃO | — | — | — | — | — | — |
| `kof.serialization` | PARCIAL (json) | SIM | SIM | PARCIAL | SIM | SIM | SIM |
| `kof.logging` | PARCIAL (println) | SIM | SIM | SIM | SIM | SIM | SIM |
| `kof.observability` | NÃO | — | — | — | — | — | — |
| `kof.metrics` | PARCIAL (bench/profile) | SIM | SIM | JVM | SIM | SIM | SIM |
| `kof.test` (`kof test`, assert) | SIM | SIM | SIM | JVM/Native | SIM | SIM | SIM |
| `kof.cli` (CLI kof) | SIM | SIM | SIM | JVM | SIM | SIM | SIM |
| `kof.process` | NÃO | — | — | — | — | — | — |
| `kof.crypto` | NÃO (faz parte de kof.security) | — | — | — | — | — | — |
| `kof.ui` (plataforma de UI) | PARCIAL | PARCIAL | — | JS | — | PARCIAL | PARCIAL |

## 4.1 Estado da segurança na plataforma hoje

| Área | Estado atual |
|------|--------------|
| Hash de senha | INEXISTENTE (programador usaria sha256 — proibido por design) |
| Constante de tempo | INEXISTENTE |
| Random seguro | INEXISTENTE (sem API de random) |
| JWT | INEXISTENTE |
| Headers/CSRF/CORS | INEXISTENTE |
| Segredos (env) | INEXISTENTE |
| Auth em HTTP | PARCIAL: `header("x-auth")` manual no middleware |
| Secrets em logs | SEM PROTEÇÃO |

---

# 5. ARQUITETURA `kof.security` (definição)

Namespaces de intenção (compilados, mesmo padrão de `kof.io`/`kof.web`):

```text
kof.security
├── passwords        → hash/verify/needsRehash (PBKDF2-HMAC-SHA256, secure by default)
├── crypto           → sha256/sha512, hmacSha256, aesGcm (encrypt/decrypt), randomHex/randomInt
├── jwt              → create/verify (HS256, exp/iss/aud, sem confusão de algoritmo)
├── secrets          → get (env), redact
├── security         → constantTimeEquals, randomHex, redact, csrfToken/csrfValid, corsAllowed, headers helpers
└── auth             → contexto web: secret, token, authenticated, claims, user, hasRole, hasPermission
```

Suporte por target (definição):

| Função | JVM | Native | JS |
|--------|-----|--------|----|
| `passwords.hash/verify/needsRehash` | SIM (javax.crypto PBKDF2) | SIM (asm PBKDF2-HMAC-SHA256) | SIM (PBKDF2 em JS) |
| `crypto.sha256/sha512` | SIM | SIM (asm) | SIM (JS) |
| `crypto.hmacSha256` | SIM | SIM (asm) | SIM (JS) |
| `crypto.aesGcm` encrypt/decrypt | SIM | NÃO (SECN002) | NÃO (SECN002) |
| `crypto.randomHex/randomInt` | SIM (SecureRandom) | SIM (getrandom) | SIM (kof_platform) |
| `jwt.create/verify` | SIM | NÃO (depende de PBKDF2? não — HMAC; depende de sha256/hmac asm) | SIM |
| `secrets.get` | SIM (env) | SIM (getenv asm — se viável) | SIM (kof_platform) |
| `security.constantTimeEquals` | SIM | SIM (asm) | SIM (JS) |
| `security.redact` | SIM | SIM | SIM |
| `security.csrfToken/csrfValid` | SIM | — | — |
| `security.corsAllowed` | SIM | — | — |
| `security.cspHeader/hstsHeader/...` | SIM | — | — |
| `auth.*` (contexto web) | SIM | — | — |

**Regra**: qualquer gap emite diagnóstico claro em compile-time (ex.
`SECN001: passwords.hash não está disponível no target Native ainda`).
Nunca comportamento silenciosamente diferente (§16 do doc de performance).

## 5.1 Formatos (versionados, sem ambigüidade)

```text
passwords:   pbkdf2$sha256$<iterations>$<salt-b64>$<hash-b64>
crypto:      aesgcm$<iv-b64>$<ciphertext+tag-b64>
jwt:         RFC 7519 HS256 (alg fixado, nunca aceito do token)
```

---

# 6. PRÓXIMAS ETAPAS

1. Implementar `kof.security` (KofSecurity.java + runtimes JVM/Native/JS).
2. Testes unitários + E2E + adversariais (`KofSecurityTest`).
3. Benchmarks (`benchmarks/security/`).
4. Documentação (`docs/security.md`, `learn/`, `training/`).
5. Auditoria contínua: `kof.config`, `kof.database`, `kof.messaging`,
   `kof.validation`, `kof.logging`, `kof.observability` (próximas etapas).

---

# 7. ESTADO DA IMPLEMENTAÇÃO (0.0.5)

## 7.1 Implementado

| API | JVM | Native | JS | Formato |
|-----|-----|--------|----|---------|
| `passwords.hash(password)` | ✅ PBKDF2-HMAC-SHA256 600k | ✅ (asm: HMAC interno + b64 + getrandom) | ✅ PBKDF2 (platform-delegated) | `pbkdf2$sha256$600000$salt$hash` |
| `passwords.verify(password, hash)` | ✅ constant-time | ✅ (asm, constant-time) | ✅ | |
| `passwords.needsRehash(hash)` | ✅ | ✅ | ✅ | |
| `crypto.sha256(data)` | ✅ | ✅ (asm FIPS 180-4) | ✅ (JS puro) | hex |
| `crypto.sha512(data)` | ✅ | ✅ (asm FIPS 180-4, vetores FIPS testados) | ✅ (JS puro) | hex |
| `crypto.hmacSha256(key, data)` | ✅ | ✅ (asm) | ✅ (JS puro) | hex |
| `crypto.encryptAesGcm(plain, keyHex)` | ✅ AES/GCM/NoPadding | ❌ SECN002 | ❌ SECN002 | `aesgcm$iv$ct` |
| `crypto.decryptAesGcm(ct, keyHex)` | ✅ (falha em tamper) | ❌ SECN002 | ❌ SECN002 | |
| `crypto.randomHex(n)` | ✅ SecureRandom | ✅ getrandom | ✅ platform | hex |
| `crypto.randomInt(bound)` | ✅ | ✅ getrandom + rejection | ✅ platform | |
| `jwt.create(claims, secret[, ttl])` | ✅ HS256 + iat/exp | ✅ (asm: base64url + HMAC + kof_now) | ✅ | RFC 7519 HS256 |
| `jwt.verify(token, secret[, iss, aud])` | ✅ (sig, exp, iss, aud) | ✅ (asm, constant-time + exp/iss/aud) | ✅ | alg fixado HS256 |
| `jwt.secret()` | ✅ env `KOF_JWT_SECRET` ou gerado | ✅ | ✅ | 32 bytes hex |
| `secrets.get(name[, fallback])` | ✅ env | ✅ `/proc/self/environ` | ✅ platform | |
| `secrets.redact(value)` | ✅ | ✅ (asm) | ✅ | `abcd********wxyz` |
| `security.constantTimeEquals(a, b)` | ✅ `MessageDigest.isEqual` | ✅ (asm) | ✅ | |
| `security.randomHex` / `randomInt` | ✅ | ✅ | ✅ | |
| `security.csrfToken/csrfValid` | ✅ (session-scoped) | ❌ | ❌ | |
| `security.corsAllowed(origin, allowed)` | ✅ | ❌ | ❌ | |
| `security.cspHeader/hstsHeader/contentTypeOptionsHeader/frameHeader/referrerHeader` | ✅ (valores prontos) | ❌ | ❌ | |
| `auth.secret/token/authenticated/claims/user/hasRole/hasPermission` | ✅ (contexto web, Bearer JWT) | ❌ | ❌ | |

## 7.2 Verificação cruzada

Os valores de SHA-256/HMAC são idênticos entre JVM, Native e JS e batem com
vetores de referência (FIPS 180-4, RFC 2104) — verificado por
`KofSecurityTest` nos três targets.

## 7.3 Testes

`KofSecurityTest` (22 testes): hashing/verificação, senha errada, rehash,
SHA-256/512 vetores, HMAC, constant-time, random, JWT (assinatura, expiração,
issuer/audience, token malformado, confusão de algoritmo, claims não-objeto),
AES-GCM (round trip, tamper, chave errada), secrets/redact, e diagnostics
de target gap (SECN001/002/003). Casos adversariais incluídos (§18).

## 7.4 Benchmarks

`benchmarks/security/`: `password-hash`, `jwt`, `hash-speed`, `aes-gcm`
(jvm/js conforme suporte).

## 7.5 Gaps documentados

- ~~JWT no Native~~ — ✅ fechado: `kof_sec_jwt_*` em asm (base64url + HMAC
  + iat/exp + exp/iss/aud + constant-time + exceções via try/catch).
- `passwords.*` no Native: PBKDF2 asm planejado.
- SHA-512 asm: planejado (SECN003 hoje).
- AES-GCM fora do JVM: primitiva com requisitos de constante de tempo;
  planejado para Native via primitivas específicas.
- `== null` com String no Native: `kof_string_equals` não trata null
  (limitação pré-existente do backend).
- **Diagnósticos de target incompletos**: `jwt.*`, `auth.*`, `csrf`,
  `cors` e os headers de segurança não têm entrada explícita em
  `KofSecurity.supportedOn` para Native/JS (default `true`) → hoje um
  programa que os usa no Native falha no link com símbolo indefinido em
  vez de um diagnóstico SECN00x claro. Item **G7** da auditoria
  (`docs/ecosystem-coverage.md` §4) — prioridade P0.

## 7.6 Correções de bugs descobertas durante a implementação

| Bug | Correção |
|-----|----------|
| `while (longExpr < intLiteral)` gerava `LCMP` sobre [long, int] (stack underflow → Frame.merge crash) | shortcut de comparação agora faz widening dos operandos (`emitComparisonShortcut`) |
| `crypto.randomInt` nativo retornava o quociente da divisão em vez do resto | `movl %edx, %eax` após `divl` |