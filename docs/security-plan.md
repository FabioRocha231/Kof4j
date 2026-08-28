# KOF SECURITY — ARQUITETURA + PLANO DE IMPLEMENTAÇÃO

**Última atualização:** 27 de agosto de 2026
**Versão:** 0.2.0-beta (658 testes; `VERSION` 0.2.0-beta)
> Estado baseado em auditoria real do repositório (27/ago/2026, 0.2.0-beta — free-list + riscv64 + pattern matching + `String?` + `kof.http` JS).
> Obrigações do módulo: **não copiar Spring**, **security by default**,
> **zero ceremony**, **multi-target honesto** (JVM/Native/JS) e **nunca
> divergência silenciosa** (gap → diagnóstico em compile-time SECN00x).

---

# 1. ARQUITETURA

`kof.security` é uma **tabela de dispatch em compile-time** (mesmo padrão de
`kof.io`/`kof.web`/`kof.http`): a intenção é escrita em Kof, o
`SemanticAnalyzer` tipa, o `KofSecurity.java` mapeia para `kof_sec_*`, e cada
target fornece a implementação:

```text
Kof (intenção) → SemanticAnalyzer → KofSecurity.dispatch → kof_sec_*
   → JvmRuntime    (dev.kof.runtime.KofRuntime — javax.crypto, SecureRandom)
   → NativeRuntime (asm x86-64 sem libc — getrandom, constant-time, FIPS 180-4)
   → JsBackend     (kof_platform / JS puro)
```

Dois planos ortogonais:

- **Plano 1 — primitivas** (`passwords`, `crypto`, `secrets`, `security`):
  funções puras, sem estado, sem web. Já implementado (JVM + asm + JS), com
  gaps de target pontuais (SECN001/002/003).
- **Plano 2 — web security** (`auth`, `sessions`, `cookies`, `csrf`, `cors`,
  `headers`, `ratelimit`, `middleware`): camada de request/response sobre
  `kof.web` + `kof.http`. Já existe a base (`auth.*`, `csrf/cors/headers`) no
  JVM via ThreadLocal por request; falta o middleware integrado e o estado.

## 1.1 Superfície atual (auditoria — 31 funções, 6 namespaces — 0.2.0-beta 27/08)

| Namespace | Funções | JVM | Native x86_64 (free-list 27/08) | JS |
|-----------|---------|-----|-------------------------------|----|
| `passwords` | `hash/verify/needsRehash` (PBKDF2-HMAC-SHA256 600k) | ✅ | ✅ (asm HMAC + free-list) | ✅ |
| `crypto` | `sha256` `sha512` `hmacSha256` `encryptAesGcm` `decryptAesGcm` `randomHex` `randomInt` | ✅ | ✅ sha256/hmac/sha512/random (SECN002 AES-GCM only) | ✅ |
| `jwt` | `create(claims, secret[, ttl])` `verify(token, secret[, iss, aud])` `secret()` | ✅ | ✅ (asm base64url + HMAC) | ✅ |
| `secrets` | `get(name[, fallback])` (env `KOF_*`) `redact` | ✅ | ✅ `/proc/self/environ` | ✅ |
| `security` | `constantTimeEquals` `randomHex` `randomInt` `redact` `csrfToken` `csrfValid` `corsAllowed` `cspHeader` `hstsHeader` `contentTypeOptionsHeader` `frameHeader` `referrerHeader` | ✅ | ✅ ct/redact/random; ❌ csrf/cors/headers | ✅ ct/redact/random; ❌ csrf/cors/headers |
| `auth` (web) | `secret(token)` `token()` `authenticated()` `claims()` `user()` `hasRole(r)` `hasPermission(p)` | ✅ (Bearer JWT + ThreadLocal) | ❌ | ❌ |

Formatos versionados: `pbkdf2$sha256$<iter>$<saltB64>$<hashB64>`; AES-GCM
`aesgcm$<ivB64>$<ctB64>` (key 32B, IV 12B); JWT RFC 7519 HS256 fixo
(alg nunca aceito do token).

---

# 2. PLANO DE IMPLEMENTAÇÃO POR CAMADAS (18 prioridades)

Cada camada entrega valor sozinha e segue a regra "DONE" da plataforma:
API idiomática + type safety + targets aplicáveis + testes unit/E2E +
adversariais + benchmark + security review + docs.

## Camada A — fundamentos (primitivas)

| # | Capacidade | Estado | Ação |
|---|-----------|--------|------|
| 1 | **secure random** | ✅ `randomHex`/`randomInt` nos 3 targets (SecureRandom / getrandom / platform) | manter; adicionar `randomBytes` e vetores de teste + adversarial |
| 2 | **hashing** | ✅ `sha256`/`sha512` (FIPS 180-4; sha512 ❌ Native SECN003) | implementar `sha512` no asm (FIPS 180-4, alinhado com sha256 já existente) |
| 3 | **password hashing** | ✅ PBKDF2-HMAC-SHA256 600k (JVM/JS); ❌ Native SECN001 | implementar `kof_sec_password_*` no asm (PBKDF2 sobre o HMAC já existente; constante de tempo na verificação) |
| 4 | **HMAC** | ✅ `hmacSha256` (RFC 2104) nos 3 targets | manter; vetores + tamanhos de chave variados |
| 5 | **AES-GCM / ChaCha20-Poly1305** | ✅ AES-GCM JVM; ❌ SECN002 Native/JS | Native: AES-GCM asm (constante de tempo) ou primitiva via `kof_sec_aesgcm_*`; **ChaCha20-Poly1305** como segunda cifra (RFC 8439) — decidir formato `chacha20$...` |
| 6 | **key management** | ✅ parcial: env `KOF_*`, `secrets.get`, `jwt.secret()` | camada `kof.security.keys`: geração, rotação, armazenamento (arquivo com permissão 0600, env, variante `keychain`/platform no JS); `secrets.redact` por padrão em logs |

## Camada B — identidade

| # | Capacidade | Estado | Ação |
|---|-----------|--------|------|
| 7 | **JWT/JWS** | ✅ HS256 create/verify (iat/exp/iss/aud) nos 3 targets (SECN004 fechado) | manter; JWS (HS384/HS512) como extensão |
| 8 | **authentication** | ✅ `auth.*` Bearer JWT no JVM (ThreadLocal por request) | expor `auth.authenticate(token)` como função pura (fora de request); integrar ao middleware (`app.use { auth.require() }`) |
| 9 | **authorization** | ✅ `auth.hasRole/hasPermission` (RBAC) JVM | RBAC/ABAC via claims + `auth.requireRole(r)`/`auth.requirePermission(p)` em rotas; modelo de policy declarativo sem framework |

## Camada C — web security (camada de request/response)

| # | Capacidade | Estado | Ação |
|---|-----------|--------|------|
| 10 | **sessions** | ❌ | `kof.security.sessions`: sessão com id seguro (random 32B), armazenamento em memória (JVM) + plugável; assinatura de cookie; expiração; sem estado fixo (token assinado) como opção |
| 11 | **cookies** | ❌ | `kof.security.cookies`: helper de cookie seguro (HttpOnly, Secure, SameSite, Max-Age, domain/path); parse/set no servidor web |
| 12 | **CSRF** | ✅ `csrfToken`/`csrfValid` JVM | integrar ao middleware: validar automaticamente mutações quando sessão por cookie; token por sessão |
| 13 | **CORS** | ✅ `corsAllowed` JVM | middleware `app.cors { origin allow list, methods, headers, credentials }`; pré-flight automático |
| 14 | **security headers** | ✅ helpers JVM (`cspHeader`/`hstsHeader`/...) | middleware `app.securityHeaders()` aplicando por padrão (CSP/HSTS/nosniff/X-Frame/Referrer) — security by default |
| 15 | **rate limiting** | ❌ | `kof.security.ratelimit`: janela fixa/sliding, por IP/rota/token; contadores em memória (JVM); middleware `app.rateLimit(60, "1m")`; resposta 429 + Retry-After |
| 18 | **security middleware** | ❌ | orquestra 10–15 em `app.use`; ordem fixa: rate limit → cors → headers → cookies/session → csrf → auth → authorization → rota |

## Camada D — identidade federada e transporte

| # | Capacidade | Estado | Ação |
|---|-----------|--------|------|
| 16 | **OAuth2 / OIDC** | ❌ | client (authorization code + PKCE), resource server (validação de JWT de terceiros), provider (P2); via `kof.http` client |
| 17 | **TLS / certificates / HTTPS** | ❌ (servidor HTTP plano) | `kof.web` + certs: gerar/ler PEM (PKCS#8/PKCS#12), `app.listenTls(port, cert, key)`; TLS por padrão quando certificado presente |

---

# 3. ORDEM SUGERIDA DE EXECUÇÃO (com dependências)

```text
Camada A: 1,2,4 (já prontos) → 3 (pbkdf2 asm) → 5 (aesgcm asm) → 6 (keys)
Camada B: 7 (jwt asm) → 8 (auth pura) → 9 (authorization)
Camada C: 14 (headers) → 13 (cors) → 12 (csrf) → 11 (cookies) → 10 (sessions)
          → 15 (ratelimit) → 18 (middleware orquestrador)
Camada D: 17 (TLS) → 16 (OAuth2/OIDC client)
```

Sequência de valor: **Native crypto (3,5,7)** destrava o resto no Native;
**middleware (18)** é o que torna 10–15 utilizáveis no mundo real; **TLS (17)**
é o fechamento do ciclo "produção".

---

# 4. REGRAS INVARIANTES

1. **Nunca silencioso**: toda função nova entra em `KofSecurity.supportedOn`
   + `gapCode` no mesmo PR (SECN00x); nenhum stub, nenhum comportamento
   divergente por target.
2. **Security by default**: headers seguros, cookies seguros, timeouts,
   limites de request, redaction de segredos em logs — o default é o seguro;
   o desenvolvedor opta por relaxar explicitamente.
3. **Constante de tempo** em qualquer comparação de segredo (já existe
   `constantTimeEquals`).
4. **Zero ceremony**: sem annotations, sem container, sem config XML; a
   intenção em Kof é a configuração (ex.: `app.rateLimit(60, "1m")`).
5. **Formato versionado** em toda representação serializada (hash/cifra/JWT)
   para permitir evolução sem quebrar dados existentes.
6. **Rotação**: toda chave tem caminho de rotação documentado
   (`keys.rotate`, TTL de sessão, rehash de senha via `needsRehash`).
7. **Multi-target honesto**: primitivas no asm sem libc (getrandom, FIPS,
   constante de tempo); gaps reais são diagnosticados, nunca simulados.
