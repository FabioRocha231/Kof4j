# 32 — CLI e Tooling

> **Kof 0.2.6-beta — 31 ago 2026 — 658 testes — targets jvm/native/native.risc/native.arm/js/kofc**

A CLI é a ferramenta central da plataforma Kof.

## Comandos

| Comando | O que faz |
|---------|-----------|
| `kof build <dir>` | Compila para JVM (padrão) |
| `kof build <dir> --target=native` | Compila para ELF x86-64 |
| `kof build <dir> --target=native.risc` | Compila para ELF riscv64 |
| `kof build <dir> --target=native.arm` | Compila para ELF aarch64 |
| `kof build <dir> --target=js` | Compila para ES Modules |
| `kof run <file.kf> [--target jvm|native|native.risc|native.arm|js]` | Compila e executa |
| `kof script <file.ks|kf> [--watch] [--target ...]` | KofScript direto (`let`→`KofScriptGlobals`) + REPL (`kof script --repl` / `kof repl`) |
| `kof c <file.c> [--run] [--output <bin>]` | KofC C subset → ELF x86-64 nativo-only |
| `kof serve <file.kf>` | Web server HTTP básico |
| `kof check <file.kf\|dir>` | Type-check sem emitir código |
| `kof test <file.kf\|dir> [--target jvm|native|js]` | Roda programas e reporta PASS/FAIL pelo exit code |
| `kof bench [paths...] [--target ...]` | Benchmark harness |
| `kof debug <file.kf> [--target jvm]` | DAP MVP |
| `kof info [--json]` | Relatório do ambiente |
| `kof lsp` | Language Server (stdio, LSP 3.x) |
| `kof version` | Versão da plataforma (`0.2.6-beta`) |

Planejado: `kof fmt` (formatter). Todos os comandos seguem `intention->Kof->frontend->IR->backend->runtime`.

## `kof info`

Diagnóstico oficial do ambiente — para usuários e suporte:

```text
Kof 0.2.6-beta
Tooling API: 21
OS: linux
Arch: x86_64
Target: linux-x86_64
JVM: Eclipse Adoptium 25.0.4 (embedded)
Compiler: 0.2.6-beta
Runtime: 0.2.6-beta
Stdlib: 0.2.6-beta
Targets: jvm, native, native.risc, native.arm, js, kofc
Install: /opt/kof
```

Formato estruturado: `kof info --json`.

## `kof check`

Executa o pipeline completo (Lexer → Parser → Análise Semântica) e reporta
todos os erros, sem emitir código. É a mesma checagem que o LSP publica.

## `kof script` e `kof c` (0.2.0)

```bash
kof script demo.ks                 # let/const no topo → KofScriptGlobals
kof script demo.ks --watch         # re-executa ao salvar
kof script --repl                  # REPL incremental (exit para sair)
kof c hello.c --run                # C subset nativo-only (GAS+LD)
kof c hello.c --output ./bin
```

`KofScript` reaproveita o frontend real (`lexer→parser→AST→IR`) e o backend escolhido; `let x=5; fn foo(){println(x)}` vira `class KofScriptGlobals { static Int x=5 }` + `main(){foo()}`.

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

Nunca duplique o parser em um editor: consuma o tooling do Kof. Target separation (`native.risc`/`native.arm`) já aparece no `kof info` e no `parseTarget`.

## Referências

- [docs/tooling/README.md](../docs/tooling/README.md)
- [docs/tooling/EDITOR_SUPPORT.md](../docs/tooling/EDITOR_SUPPORT.md)
- [docs/tooling/LSP.md](../docs/tooling/LSP.md)
