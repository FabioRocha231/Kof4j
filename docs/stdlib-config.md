# stdlib config — Configuração Nativa do Kof

**Última atualização:** 27 de agosto de 2026
**Versão:** 0.2.0-beta (658 testes)
**Status:** implementado (Fase 3 do plano de independência do Spring) — JVM+Native (Native asm próprio `/proc/self/environ` + free-list, 27/08)

---

## 1. Filosofia

> Configuração é uma capacidade da linguagem, não de um framework.

`kof.config` resolve valores de configuração com precedência explícita,
tipagem em compile-time e zero dependência externa (sem Spring
Environment/PropertySource, sem dotenv).

## 2. API

```kof
var port  = config.int("server.port", 8080)         // Int com default
var url   = config.str("database.url", "jdbc:h2:mem") // String com default
var debug = config.bool("app.debug", false)         // Bool com default
var big   = config.long("app.timeoutMillis", 30000) // Long com default

var raw   = config.get("server.port")               // String ou null
var has   = config.has("server.port")               // Bool
var home  = config.env("HOME")                      // variável de ambiente direta
```

`config.int/bool/long/str` nunca falham: valor ausente ou inválido → default.

## 3. Fontes e precedência

1. **Arquivo explícito** — `KOF_CONFIG` aponta para um arquivo
   `chave=valor` (comentários com `#`). Maior precedência.
2. **Variável de ambiente** — `KOF_<KEY>` com `.`/`-` → `_` e maiúsculas:
   `server.port` → `KOF_SERVER_PORT`.
3. **Arquivo de profile** — `kof.<KOF_PROFILE>.config` no diretório de
   trabalho (ex.: `kof.prod.config`).
4. **Arquivo padrão** — `kof.config` no diretório de trabalho.

```bash
KOF_CONFIG=/etc/app/config.properties kof run app.kf
KOF_PROFILE=prod kof run app.kf            # usa kof.prod.config
KOF_SERVER_PORT=9000 kof run app.kf        # env por convenção
```

## 4. Exemplo com a stack web

```kof
main() {
    var port = config.int("server.port", 8080)
    var app = web.app()
    app.get("/") {
        return "listening on " + port
    }
    app.listen(port)
}
```

## 5. Targets (0.2.0-beta)

| Target | Estado | Notas |
|--------|--------|-------|
| JVM | ✅ completo | `KofRuntime` gerado |
| Native x86_64 | ✅ completo (asm próprio, 27/08) | `/proc/self/environ` scan, trim, comentários, free-list `kof_free_head` |
| Native riscv64/aarch64 | ✅/placeholder | riscv64 `li a7` syscalls; aarch64 placeholder |
| JS | CONF001 (gap documentado) | `config.*` reporta `CONF001` em compile-time; `secrets.get` funciona |

## 6. Testes

`KofConfigE2ETest` — 8 testes E2E (27/08, 0.2.0-beta): env por convenção, defaults, arquivo
explícito, profiles, arquivo padrão no diretório de trabalho, `env()`,
precedência completa e CONF001 no JS (Native agora ✅).

## 7. Arquitetura

```
Kof source (.kf) → KofConfig (tabela compile-time)
   → SemanticAnalyzer (tipos) → CompilerDriver (KofCall kof_config_*)
   → dev.kof.runtime.KofRuntime (gerado): lookup com precedência + parsing
```

O compilador conhece cada chamada em compile-time; o runtime nunca é
descoberto por reflection.

## 8. Onde estamos vs. o padrão ouro (Spring/Quarkus) — auditoria honesta

**Última revisão:** 30/08/2026 (0.2.4-beta, auditoria da Fase de Configuração)

| Capacidade | kof.config hoje | Spring Boot | Status |
|------------|-----------------|-------------|--------|
| Arquivo de config | `kof.config` (key=value) | `application.properties` | ✅ equivalente |
| Profiles | `KOF_PROFILE` → `kof.prod.config` | `spring.profiles.active` | ✅ equivalente |
| Env por convenção | `server.port` → `KOF_SERVER_PORT` | `SERVER_PORT` (relaxed binding) | ✅ equivalente |
| Typed com default | `config.int/str/bool/long` | `@Value` / `@ConfigurationProperties` | ✅ equivalente |
| Falhar cedo (required) | ❌ `null` silencioso | falha no boot | ❌ **gaps** |
| Config declarativa tipada | ❌ | ❌ (reflection em runtime) | 🎯 vantagem planejada |
| Interpolação | ❌ | `${key}` | ❌ gap |
| Descoberta de chaves | ❌ | Actuator `/env` | ❌ gap (mas há alternativa melhor: o compilador já conhece as chaves) |
| Secrets | separados (`kof.security.secrets.get`, env-only) | `Environment` mistura tudo | ✅ Kof é mais seguro |

### 8.1 Decisões de projeto (firmes)

1. **O arquivo se chama `kof.config`** — não `application.properties` nem
   `application.kof`. Consistência com `kof.log`, `kof.cache`, `kof.db`:
   tudo do Kof vive no namespace `kof.*`. O "application.kof" da discussão
   inicial já está atendido pelo nome certo.
2. **Secret NUNCA vai no arquivo.** `kof.config` é comittável no git;
   secrets vivem em env (`secrets.get`) — separação config/secret é
   segurança, não conveniência. Padrão 12-factor; melhor que a prática
   comum de misturar no mesmo arquivo.
3. **Nunca reflection.** A precedência é implementada direto (JVM gerado,
   asm nativo, `kof_platform` no JS). Sem PropertySource, sem relfection.

### 8.2 Roadmap (na ordem de valor)

**P1 — `config.required(key)` — falhar cedo.**
```kof
var url = config.required("database.url")   // erro de startup claro se ausente
```
Elimina a classe inteira de bugs de deploy (" rodou na minha máquina").
Runtime: se ausente → `KofPanic` com a chave e a precedência consultada.
Os 3 targets; pequeno.

**P2 — Interpolação no arquivo.**
```text
# kof.config
db.host = localhost
db.url  = jdbc:h2://${db.host}/mem
```
Lookup recursivo com detecção de ciclo. Baixo custo, alto valor de DX.

**P3 — `kof config gen` — template a partir do código.**
O compilador JÁ conhece todas as chaves (compile-time dispatch). Um subcomando
lista as chaves usadas + defaults e gera um `kof.config` de exemplo para
deploy. Nenhum concorrente faz isso sem rodar a aplicação.

**P3 — Config declarativa tipada (a visão do KOF_VS_SPRING §2).**
```kof
config App {
    port    = 8080
    db.url  = "jdbc:h2:mem"
    debug   = false
}
```
Um bloco na linguagem; o compilador valida chaves/tipos em compile-time,
emite a classe `AppConfig` e sabe TODAS as chaves. Erro de digitação em
config vira erro de compilação — nada no mercado faz isso (Spring resolve
em runtime por reflection; Quarkus usa anotações + APT).
Depende: parser de blocos nomeados, codegen. Fase própria, grande.

**P4 — JS:** fechar o gap CONF001 (via `kof_platform` — o host já expõe
`getenv`).

### 8.3 O que NÃO faremos

- Recarga automática de config (hot reload): complexidade de runtime alto,
  valor baixo em ambientes containerizados (o pod reinicia).
- Secrets em arquivo (mesmo cifrado): a env já é o contrato universal
  (Kubernetes, systemd, CI). Nada de inventar formato de cofre.
- YAML/TOML: o formato `key=value` com `#` cobre 100% dos casos reais de
  config de app; YAML traz dependência e superfície de erro (indentação)
  sem benefício. Se um dia precisar de estrutura, o P3 (bloco declarativo)
  resolve com tipagem, não com indentação.
