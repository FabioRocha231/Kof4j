# Aprenda Kof

Bem-vindo à trilha de aprendizado da Kof.

Kof é uma linguagem de programação compilada para múltiplas plataformas, fortemente tipada, orientada a objetos, com suporte a herança, virtual dispatch, interfaces, exceptions e web server nativo.

## O que Kof é hoje

* Compilador completo (Lexer → Parser → AST → Type System → IR → JVM/Native)
* Classes, records, interfaces, herança, virtual dispatch
* Strings, arrays, exceptions
* Web server via `kof serve`
* Runtime nativo x86-64
* 176+ testes passando

## Para quem é

- **Iniciantes** — comece pelo capítulo 00 e siga a ordem
- **Desenvolvedores Java** — comece pela introdução e vá direto para o que é diferente
- **Contribuidores** — leia Design da Linguagem e Internals do Compilador

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
| 08 | Propriedades | ✅ |
| 09 | Interfaces | ✅ |
| 10 | Herança | ✅ |
| 11 | Generics | Planejado |
| 12 | Collections | Planejado |
| 13 | Nullability | Planejado |
| 14 | Exceptions | ✅ Parcial |
| 15 | Pattern Matching | Planejado |
| 16 | Lambdas | Planejado |
| 17 | Programação Funcional | Planejado |
| 18 | Concorrência | Planejado |
| 19 | Packages e Módulos | ✅ Parcial |
| 20 | Annotations | Planejado |
| 21 | Java Interop | ✅ Parcial |
| 22 | JVM | ✅ |
| 23 | Testes | ✅ Parcial |
| 24 | Build Tools | ✅ |
| 25 | Spring | Planejado |
| 26 | Aplicação Real | Planejado |
| 27 | Boas Práticas | ✅ |
| 28 | Design da Linguagem | ✅ |
| 29 | Internals do Compilador | ✅ |
| 30 | Contribuindo | ✅ |
| Glossário | Glossário | ✅ |
| Multiplatform | Native | ✅ |

Kof está em fase de consolidação. O compilador é funcional com backends JVM e Native.

**Testes:** 176/176 passando

**O que funciona hoje:**
- Lexer completo com 55+ keywords
- Parser recursivo descendente funcional
- Records com campos, construtor, accessors e toString
- Declarações de classe com campos e métodos
- Declarações de interface
- Herança simples (extends)
- Virtual dispatch (override)
- Interfaces básicas
- try/catch/finally
- do-while
- String concatenação
- Array creation, access, length
- Field initialization
- Package e import declarations
- CLI com comandos `build`, `run`, `version` e flag `--target`
- Backend JVM via ASM — gera `.class` funcionais
- Backend Nativo — gera ELF x86-64 via assembly + as + ld
- Runtime nativo completo (allocation, strings, arrays, errors)

**O que está em desenvolvimento:**
- String methods (charAt, substring, contains)
- Instanceof / type casting
- Switch statements
- Null safety
- Standard library
- Diagnostics detalhados

**O que está planejado:**
- Generics
- Collections
- Concorrência
- HTTP / Database
- KofJS (frontend web)
- Tooling (LSP, formatter, etc.)

## Arquivos

| Capítulo | Arquivo | Status |
|----------|---------|--------|
| Introdução | `00-introduction.md` | ✅ |
| Instalação | `01-installation.md` | ✅ |
| Primeiro Programa | `02-first-program.md` | ✅ |
| Fundamentos | `03-language-basics.md` | ✅ |
| Variáveis e Tipos | `04-variables-and-types.md` | ✅ |
| Controle de Fluxo | `05-control-flow.md` | Planejado |
| Funções | `06-functions.md` | ✅ Parcial |
| Classes e Objetos | `07-classes-and-objects.md` | ✅ Parcial |
| Propriedades | `08-properties.md` | Planejado |
| Interfaces | `09-interfaces.md` | ✅ Parcial |
| Herança | `10-inheritance.md` | Planejado |
| Generics | `11-generics.md` | Planejado |
| Collections | `12-collections.md` | Planejado |
| Nullability | `13-nullability.md` | Planejado |
| Exceptions | `14-exceptions.md` | Planejado |
| Pattern Matching | `15-pattern-matching.md` | Planejado |
| Lambdas | `16-lambdas.md` | Planejado |
| Programação Funcional | `17-functional-programming.md` | Planejado |
| Concorrência | `18-concurrency.md` | Planejado |
| Packages e Módulos | `19-packages-and-modules.md` | ✅ Parcial |
| Annotations | `20-annotations.md` | Planejado |
| Java Interop | `21-java-interoperability.md` | Planejado |
| JVM | `22-jvm.md` | Planejado |
| Testes | `23-testing.md` | ✅ Parcial |
| Build Tools | `24-build-tools.md` | Planejado |
| Spring | `25-spring.md` | Planejado |
| Aplicação Real | `26-real-world-application.md` | Planejado |
| Boas Práticas | `27-best-practices.md` | Planejado |
| Design da Linguagem | `28-language-design.md` | ✅ |
| Internals do Compilador | `29-compiler-internals.md` | ✅ |
| Contribuindo | `30-contributing.md` | ✅ |
| Glossário | `glossary.md` | ✅ |
| Multiplatform | `native/README.md` | ✅ |