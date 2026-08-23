# stdlib config — Configuração Nativa do Kof

**Última atualização:** 23 de agosto de 2026
**Status:** implementado (Fase 3 do plano de independência do Spring)

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

## 5. Targets

| Target | Estado |
|--------|--------|
| JVM | ✅ completo |
| Native | CONF001 (gap documentado em compile-time) |
| JS | CONF001 (gap documentado em compile-time) |

## 6. Testes

`KofConfigE2ETest` — 8 testes E2E: env por convenção, defaults, arquivo
explícito, profiles, arquivo padrão no diretório de trabalho, `env()`,
precedência completa e CONF001 nos targets native/js.

## 7. Arquitetura

```
Kof source (.kf) → KofConfig (tabela compile-time)
   → SemanticAnalyzer (tipos) → CompilerDriver (KofCall kof_config_*)
   → dev.kof.runtime.KofRuntime (gerado): lookup com precedência + parsing
```

O compilador conhece cada chamada em compile-time; o runtime nunca é
descoberto por reflection.