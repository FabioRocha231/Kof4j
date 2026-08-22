# 32 — CLI e Tooling

A CLI é a ferramenta central da plataforma Kof.

## Comandos

| Comando | O que faz |
|---------|-----------|
| `kof build <dir>` | Compila para JVM (padrão) |
| `kof build <dir> --target=native` | Compila para binário nativo |
| `kof run <file.kf>` | Compila e executa |
| `kof serve <file.kf>` | Web server HTTP básico |
| `kof check <file.kf\|dir>` | Type-check sem emitir código |
| `kof info [--json]` | Relatório do ambiente |
| `kof lsp` | Language Server (stdio, LSP 3.x) |
| `kof version` | Versão da plataforma |

Planejados: `kof test` (test runner), `kof fmt` (formatter).

## `kof info`

Diagnóstico oficial do ambiente — para usuários e suporte:

```text
Kof 0.0.4-alpha
Tooling API: 21
OS: linux
Arch: x86_64
Target: linux-x86_64
JVM: Eclipse Adoptium 25.0.4 (embedded)
Compiler: 0.0.4
Runtime: 0.0.4
Stdlib: 0.0.4
Targets: jvm, native
Install: /opt/kof
```

Formato estruturado planejado/disponível: `kof info --json`.

## `kof check`

Executa o pipeline completo (Lexer → Parser → Análise Semântica) e reporta
todos os erros, sem emitir código. É a mesma checagem que o LSP publica.

## `kof lsp`

Language Server que consome o **frontend real do compilador**. Os
diagnósticos do editor são exatamente os do compilador — não existe parser
paralelo.

```bash
kof lsp   # lê stdin, escreve stdout (LSP)
```

## Editor support

O tooling de editores viaja com a distribuição:

- `editor/kof.tmLanguage.json` — grammar TextMate oficial (scope `source.kof`);
- `kof lsp` — semântica e diagnostics em qualquer editor LSP (VS Code,
  IntelliJ via LSP4IJ, Neovim, Helix, Eglot, etc.).

Nunca duplique o parser em um editor: consuma o tooling do Kof.

## Referências

- [docs/tooling/README.md](../docs/tooling/README.md)
- [docs/tooling/EDITOR_SUPPORT.md](../docs/tooling/EDITOR_SUPPORT.md)
- [docs/tooling/LSP.md](../docs/tooling/LSP.md)