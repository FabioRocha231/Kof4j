# CLI e Tooling

Fatos sobre a CLI oficial do Kof. Use para responder perguntas sobre
comandos, tooling e editor support.

## Comandos oficiais

| Comando | Comportamento |
|---------|---------------|
| `kof build <dir> [--target jvm\|native] [--output <dir>]` | Compila |
| `kof run <file.kf> [args...]` | Compila e executa (JVM) |
| `kof serve <file.kf> [--port <port>] [--host <host>]` | Web server HTTP básico |
| `kof check <file.kf\|dir>` | Type-check sem emitir código |
| `kof test <file.kf\|dir> [--target jvm\|native]` | Roda programas; PASS se exit code 0 |
| `kof info [--json]` | Relatório do ambiente |
| `kof lsp` | Language Server (stdio, LSP 3.x) |
| `kof version` | Versão da plataforma |

Planejado: `kof fmt`. Não existe comando `kof doctor` — o
diagnóstico oficial é `kof info`.

## Tooling API Level

- O baseline de API Java do tooling é 21.
- O Kof não exige Java anterior a 21 para seu tooling.
- Versões posteriores (ex.: 25, Virtual Threads) podem ser usadas
  internamente sem virar requisito.
- O pacote oficial carrega sua própria JVM (Temurin 21).

## Editor support

- O suporte de editores viaja com a distribuição.
- Grammar oficial: `editor/kof.tmLanguage.json` (scope `source.kof`),
  consumível por VS Code, IntelliJ (TextMate) e highlighters compatíveis.
- Semântica e diagnostics: `kof lsp` — qualquer editor LSP (VS Code,
  IntelliJ via LSP4IJ, Neovim, Helix, Eglot).
- **Regra: nunca duplicar o parser em um editor.** O editor consome o
  tooling do Kof; o LSP consome o frontend real do compilador.

## LSP

- `kof lsp` implementa LSP 3.x sobre stdio (framing Content-Length, JSON-RPC 2.0).
- Mensagens: initialize, initialized, shutdown, exit, didOpen, didChange,
  publishDiagnostics.
- Diagnostics são produzidos pelo CompilerDriver real — os mesmos códigos e
  mensagens de `kof check`/`kof build`.
- Sync de documentos: completa (change: 1).
- Sem parser paralelo: editor e compilador sempre concordam.

## `kof info`

Informa: versão do Kof, versão do compiler, versão do runtime, versão da
stdlib, tooling API level, target/arquitetura, SO, JVM embutida, versão da
JVM, targets disponíveis (jvm, native) e localização da instalação.
Legível por humanos; `--json` para formato estruturado.

## Regras importantes

- Preservar `kof build`, `kof install` (compatibilidade com a CLI existente),
  `kof run` e `kof serve`.
- Não criar `kof doctor` — o comando de diagnóstico é `kof info`.
- Formatter e test runner serão consumidos do frontend oficial, sem
  implementações paralelas.