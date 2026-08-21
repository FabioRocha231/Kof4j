# Aprenda Kof

Bem-vindo à trilha de aprendizado da Kof.

Este é o ponto de partida para aprender a programar em Kof — uma linguagem compilada para múltiplas plataformas, fortemente tipada, orientada a objetos, compatível com Java, mas com muito menos boilerplate.

## Para quem é

- **Iniciantes em programação** — se você nunca programou, comece pelo capítulo 00 e siga a ordem.
- **Desenvolvedores Java** — você já sabe programar. Comece pela introdução e vá direto para o que é diferente.
- **Desenvolvedores de outras linguagens** — a sintaxe é familiar, mas os conceitos da JVM podem ser novos. Recomendo seguir a ordem completa.
- **Contribuidores do compilador** — pule para "Design da Linguagem" e "Internals do Compilador".
- **Curiosos sobre compiladores** — os últimos capítulos explicam como a Kof funciona por dentro.

## Como estudar

 Leia os capítulos em ordem. Cada um constrói sobre o anterior.

Se você já sabe programar, pode pular capítulos, mas leia os exemplos — eles mostram a sintaxe real da Kof.

## Pré-requisitos

- JDK 21 ou superior instalado
- Um editor de texto (VS Code, IntelliJ, ou qualquer um)
- Terminal / linha de comando

## Ordem recomendada

```
Leitura completa (recomendado):

00 → 01 → 02 → 03 → 04 → 05 → 06 → 07 → 08 → 09 → 10
→ 11 → 12 → 13 → 14 → 15 → 16 → 17 → 18 → 19 → 20
→ 21 → 22 → 23 → 24 → 25 → 26 → 27

Trilha para iniciantes:
00 → 01 → 02 → 03 → 04 → 05 → 06 → 07

Trilha para Java developers:
00 → Introdução → 02 → 07 → 09 → 21 → 25

Trilha para contribuidores:
28 → 29 → 30

Trilha multiplatform:
00 → native/README.md → native/architecture.md → native/roadmap.md
```

## Estado atual

Kof está em fase inicial, mas funcional. O compilador já gera código executável para JVM e nativo.

**O que funciona hoje:**
- Lexer completo com 55+ keywords
- Parser recursivo descendente funcional
- Records com campos, construtor, accessors e toString
- Declarações de classe com campos e métodos
- Declarações de interface
- `fun main() = print("Hello")` — programa completo com ponto de entrada
- `println("Hello")` — saída de texto
- `var nome = "Mel"` — inferência de tipos
- Package e import declarations
- Ponto e vírgula opcional
- CLI com comandos `build`, `run`, `version` e flag `--target`
- Backend JVM via ASM — gera `.class` funcionais
- Backend Nativo — gera ELF x86-64 via assembly + as + ld
- Testes golden baseados em shell

**O que está em desenvolvimento:**
- Type checking completo
- Resolução de variáveis
- Controle de fluxo (if/while/for)
- Expressões binárias com tipos corretos
- Geração de bytecode para métodos com corpo real

**O que está planejado:**
- Generics
- Exceptions
- Pattern matching
- Collections
- Java interop
- Annotations
- Concorrência
- Spring integration
- LSP/tooling
- KofScript (runtime interativo)
- KofJS (frontend web)

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