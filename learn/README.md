# Aprenda Kof

Bem-vindo à trilha de aprendizado da Kof.

Kof é uma linguagem de programação compilada para múltiplas plataformas, fortemente tipada, orientada a objetos, com suporte a herança, virtual dispatch, interfaces, exceptions, web server nativo e uma plataforma de UI (kof.ui) que renderiza em webview nativo via KofJS.

## O que Kof é hoje

* Compilador completo (Lexer → Parser → AST → Type System → IR → JVM/Native/Native.risc/Native.arm/KofJS/KofC) — `intention->Kof->frontend->IR->backend->runtime`
* Classes, records, interfaces, herança, virtual dispatch
* Strings, arrays, exceptions, JSON, List\<T\> + `map/filter/reduce`, Map/Set, lambdas com capturas, `String?`, pattern `case String s` + `Point(x,y)`
* Web server via `kof serve` + stack web nativa (`web.app()`)
* Runtime nativo x86-64 (free-list GC `kof_free_head`) + riscv64/aarch64 placeholders + SQLite + MySQL via `kof_db`
* Target **KofJS**: ES Modules (GraalJS) + `kof.http` JVM+JS + Target separation (`jvm/native/native.risc/native.arm/js/kofc`)
* **kof.ui**: Window, Label, Button (ações), Input, Column/Row, View+Style —
  renderização em webview nativo (WebKitGTK)
* Distribuição oficial (JDK embutido, tooling, editor support)
* CLI: build, run, serve, check, test, bench, debug, info, lsp, install, script, c, version — `kof script` (`let`→`KofScriptGlobals`, repl, --watch), `kof c` (C subset nativo-only)
* kof.io: File, Path, Directory (JVM + Native) + kof.http (JVM+JS, HTTP002 Native)
* 658 testes

## Para quem é

- **Iniciantes** — comece pelo capítulo 00 e siga a ordem
- **Desenvolvedores Java** — comece pela introdução e vá direto para o que é diferente
- **Contribuidores** — leia Design da Linguagem e Internals do Compilador

## Estrutura

```
00 — Introdução
01 — Instalação (distribuição oficial)
02 — Primeiro Programa
03 — Fundamentos da Linguagem
...
30 — Contribuindo
31 — Distribuição
32 — CLI e Tooling
33 — Versionamento e Releases
34 — Filesystem (kof.io)
35 — kof.ui (cores, widgets, janelas)
36 — Segurança (kof.security)
37 — KofJS (o caminho da Web)
```

## Índice

| # | Capítulo |
|---|----------|
| 00 | [Introdução](00-introduction.md) |
| 01 | [Instalação](01-installation.md) |
| 02 | [Primeiro Programa](02-first-program.md) |
| 03 | [Fundamentos da Linguagem](03-language-basics.md) |
| 04 | [Variáveis e Tipos](04-variables-and-types.md) |
| 05 | [Controle de Fluxo](05-control-flow.md) |
| 06 | [Funções](06-functions.md) |
| 07 | [Classes e Objetos](07-classes-and-objects.md) |
| 08 | [Propriedades](08-properties.md) |
| 09 | [Interfaces](09-interfaces.md) |
| 10 | [Herança](10-inheritance.md) |
| 11 | [Generics](11-generics.md) |
| 12 | [Coleções](12-collections.md) |
| 13 | [Nullability](13-nullability.md) |
| 14 | [Exceções](14-exceptions.md) |
| 15 | [Pattern Matching](15-pattern-matching.md) |
| 16 | [Lambdas](16-lambdas.md) |
| 17 | [Programação Funcional](17-functional-programming.md) |
| 18 | [Concorrência](18-concurrency.md) |
| 19 | [Pacotes e Módulos](19-packages-and-modules.md) |
| 20 | [Annotations](20-annotations.md) |
| 21 | [Interoperabilidade com Java](21-java-interoperability.md) |
| 22 | [JVM](22-jvm.md) |
| 23 | [Testes](23-testing.md) |
| 24 | [Build Tools](24-build-tools.md) |
| 25 | [Spring](25-spring.md) |
| 26 | [Aplicação Real](26-real-world-application.md) |
| 27 | [Melhores Práticas](27-best-practices.md) |
| 28 | [Design da Linguagem](28-language-design.md) |
| 29 | [Internals do Compilador](29-compiler-internals.md) |
| 30 | [Contribuindo](30-contributing.md) |
| 31 | [Distribuição](31-distribution.md) |
| 32 | [CLI e Tooling](32-cli-tooling.md) |
| 33 | [Versionamento e Releases](33-versioning-releases.md) |
| 34 | [Filesystem (kof.io)](34-file-system.md) |
| 35 | [kof.ui — Cores, Widgets e Janelas](35-kof-ui.md) |
| 36 | [Segurança (kof.security)](36-security.md) |
| 37 | [KofJS — o caminho da Web](37-kofjs.md) |

## Ordem recomendada

```
00 → 01 → 02 → 03 → 04 → 05 → 06 → 07 → 08 → 09 → 10
→ 11 → 12 → 13 → 14 → 15 → 16 → 17 → 18 → 19 → 20
→ 21 → 22 → 23 → 24 → 25 → 26 → 27
```

## Para LLMs

Consulte também `training/` para corpus estruturado de conhecimento Kof.

## Estado atual

| Capítulo | Tópico | Status |
|----------|--------|--------|
| 00 | Introdução | ✅ |
| 01 | Instalação | ✅ |
| 02 | Primeiro Programa | ✅ |
| 03 | Fundamentos | ✅ |
| 04 | Variáveis e Tipos | ✅ |
| 05 | Controle de Fluxo | ✅ |
| 06 | Funções | ✅ |
| 07 | Classes e Objetos | ✅ |
| 08 | Propriedades | ✅ (planejado) |
| 09 | Interfaces | ✅ |
| 10 | Herança | ✅ |
| 11 | Generics (erasure) | ✅ |
| 12 | Collections (List/Map/Set + map/filter/reduce) | ✅ |
| 13 | Nullability | ✅ Básico (`String?`) |
| 14 | Exceptions | ✅ (JVM + Native unwinding) |
| 15 | Pattern Matching | ✅ (`case String s` + `Point(x,y)`) |
| 16 | Lambdas | ✅ (com capturas) |
| 17 | Programação Funcional | ✅ (`map/filter/reduce`) |
| 18 | Concorrência (spawn) | ✅ (JVM; Native CONC001) |
| 19 | Packages e Módulos | ✅ (`a.b.C` fix) |
| 20 | Annotations | Implementado (JVM/KofJS) |
| 21 | Java Interop | Planejado |
| 22 | JVM | ✅ |
| 23 | Testes (kof test + assert) | ✅ |
| 24 | Build Tools | ✅ |
| 25 | Spring | Planejado |
| 26 | Aplicação Real | Planejado |
| 27 | Boas Práticas | ✅ |
| 28 | Design da Linguagem | ✅ |
| 29 | Internals do Compilador | ✅ |
| 30 | Contribuindo | ✅ |
| Glossário | Glossário | ✅ |
| Multiplatform | Native | ✅ |
| 36 | Segurança (kof.security) | ✅ (JVM/Native/JS; gaps SECN00x) |
| 35 | kof.ui (widgets, janelas, webview) | ✅ (JS render; JVM/Native no-ops) |
| 37 | KofJS (caminho da Web) | ✅ (alpha) |

Kof está em fase de consolidação. O compilador é funcional com backends JVM,
Native (x86-64 free-list), Native.risc, Native.arm, KofJS e KofC (0.2.0-beta, 658 testes).

**Testes:** 658

**O que funciona hoje (0.2.0-beta — 27 ago 2026 — 658 testes — `jvm/native/native.risc/native.arm/js/kofc`):**
- Frontend completo (lexer, parser, type system, semântica) — `intention->Kof->frontend->IR->backend->runtime`
- Seis targets: JVM (ASM), Native x86-64 (free-list GC), Native.risc, Native.arm, KofJS (GraalJS) e KofC (C subset nativo-only)
- Classes, records, herança, interfaces, virtual dispatch, generics (erasure), imports `a.b.C` fix (largeproj)
- Funções (sem `fun`), lambdas com capturas, if-expr, switch com `case String s` + `Point(x,y)` destructuring, `String?`, for-in
- Exceptions reais (JVM + Native unwinding), `assert`, `spawn` (JVM, CONC001 Native)
- Strings (API completa), arrays, `List<T>` + `map/filter/reduce`, `Map<K,V>`/`Set<T>`, JSON, kof.io, kof.time, `kof.http` (JVM+JS), `kof_db` (SQLite+MySQL WIP)
- `KofScript` (`let`/`const` no topo → `KofScriptGlobals`, `kof script --repl`, `--watch`), `KofC` (`kof c <file.c>` nativo-only)
- CLI: `build, run, serve, check, test, bench, debug, info, lsp, install, script, c, version` + `--target=jvm|native|native.risc|native.arm|js`
- `kof serve` (KofHttpServer com thread pool), `kof test` (PASS/FAIL por `test "nome" {}`), `kof bench`/`kof debug`
- Distribuição oficial (Temurin 21 embutido, package, CI/release) — Target separation (`Target.NATIVE_RISCV64/AARCH64`)


**O que está planejado (pós 0.2.0):**
- `kof fmt`, `spawn` no Native, JSON de objetos/records no Native (JSN002)
- GC mark-sweep completo (hoje free-list), MySQL wire completo, floating-point SSE nativo
- `when` guards em pattern matching, flow analysis profundo para `String?`
- Hover/completion completos no LSP, `kof_db` ORM MongoDB já OK em JVM

## Arquivos

| Capítulo | Arquivo | Status |
|----------|---------|--------|
| Introdução | `00-introduction.md` | ✅ |
| Instalação | `01-installation.md` | ✅ |
| Primeiro Programa | `02-first-program.md` | ✅ |
| Fundamentos | `03-language-basics.md` | ✅ |
| Variáveis e Tipos | `04-variables-and-types.md` | ✅ |
| Controle de Fluxo | `05-control-flow.md` | ✅ |
| Funções | `06-functions.md` | ✅ |
| Classes e Objetos | `07-classes-and-objects.md` | ✅ Parcial |
| Propriedades | `08-properties.md` | Planejado |
| Interfaces | `09-interfaces.md` | ✅ |
| Herança | `10-inheritance.md` | ✅ |
| Generics | `11-generics.md` | ✅ (erasure) |
| Collections | `12-collections.md` | ✅ (List/Map/Set + map/filter/reduce) |
| Nullability | `13-nullability.md` | ✅ Básico (`String?`) |
| Exceptions | `14-exceptions.md` | ✅ (JVM+Native) |
| Pattern Matching | `15-pattern-matching.md` | ✅ (`case String s` + `Point(x,y)`) |
| Lambdas | `16-lambdas.md` | ✅ (com capturas) |
| Programação Funcional | `17-functional-programming.md` | ✅ (`map/filter/reduce`) |
| Concorrência | `18-concurrency.md` | Planejado |
| Packages e Módulos | `19-packages-and-modules.md` | ✅ (`a.b.C` fix) |
| Annotations | `20-annotations.md` | Implementado (JVM/KofJS) |
| Java Interop | `21-java-interoperability.md` | Planejado |
| JVM | `22-jvm.md` | Planejado |
| Testes | `23-testing.md` | ✅ |
| Build Tools | `24-build-tools.md` | ✅ |
| Spring | `25-spring.md` | Planejado |
| Aplicação Real | `26-real-world-application.md` | Planejado |
| Boas Práticas | `27-best-practices.md` | Planejado |
| Design da Linguagem | `28-language-design.md` | ✅ |
| Internals do Compilador | `29-compiler-internals.md` | ✅ |
| Contribuindo | `30-contributing.md` | ✅ |
| Glossário | `glossary.md` | ✅ |
| Multiplatform | `native/README.md` | ✅ |