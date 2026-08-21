# Kof

<p align="center">
  <img src="kof.png" alt="Kof Logo" width="200">
</p>

### Uma linguagem. Um compilador. Vários mundos.

**Menos código. Mais intenção. JVM, nativo, script e web. Tudo partindo da mesma linguagem.**

---

> Algumas pessoas olham para um problema e escrevem uma biblioteca.
>
> Outras escrevem um framework.
>
> Algumas criam uma ferramenta.
>
> Eu aparentemente olhei para o ecossistema inteiro e pensei:
>
> **"Tá tudo complicado demais. Vou criar uma linguagem."**
>
> E, aparentemente, uma linguagem só também não era suficiente.

Bem-vinda à **Kof**.

---

# O que é Kof?

Kof é uma linguagem de programação **geral, fortemente tipada e estaticamente tipada**, construída com uma ideia central:

> **Uma única linguagem não deveria obrigar você a escolher um único mundo.**

Kof possui seu próprio compilador, lexer, parser, sistema de tipos, análise semântica e representação intermediária.

A partir dessa representação, diferentes backends podem transformar o mesmo programa em diferentes formas de execução.

A visão da Kof inclui:

```text
                         KOF
                          │
                    Kof Compiler
                          │
                       Kof IR
                          │
          ┌───────────────┼────────────────┐
          │               │                │
       Kof4J          KofNative        KofScript
          │               │                │
          ▼               ▼                ▼
        JVM          Native Binary      Runtime
       .class        Executável        Interativo
          │               │                │
          ▼               ▼                ▼
        JVM             OS/CPU        Kof Runtime
                          │
                          │
                          ▼
                        KofJS
                          │
                          ▼
                         Web
```

E isso não significa criar quatro linguagens.

É uma linguagem.

**Um compilador.**

**Uma semântica.**

**Uma representação intermediária.**

**Múltiplos backends.**

---

# A ideia

O mundo da programação já tem runtimes excelentes.

A JVM é excelente.

O navegador é uma plataforma absurdamente poderosa.

Executáveis nativos continuam sendo fundamentais.

E às vezes você simplesmente quer executar um arquivo agora sem construir uma aplicação inteira.

Então por que uma linguagem deveria obrigar você a escolher um único modelo?

Kof foi pensada para separar duas coisas:

**a linguagem que você escreve**

e

**o lugar onde o programa vai executar.**

Você escreve Kof.

O compilador entende Kof.

Depois você decide para onde aquilo vai.

```text
Kof Source
    │
    ▼
Kof Compiler
    │
    ▼
Kof IR
    │
    ├──────────► Kof4J ───────► JVM
    │
    ├──────────► KofNative ───► Executável nativo
    │
    ├──────────► KofScript ───► Runtime
    │
    └──────────► KofJS ───────► Web
```

**A linguagem não muda.**

O target muda.

---

# E por que diabos outra linguagem?

Porque temos linguagens excelentes.

Java é excelente.

Kotlin é excelente.

Julia é excelente no que se propõe.

Rust é excelente.

Python é excelente.

JavaScript sustenta uma quantidade obscena da internet.

O problema não é falta de linguagem.

O problema é a quantidade de complexidade que foi acumulando ao redor delas.

Hoje, construir uma aplicação pode significar lidar com:

```text
Java
Kotlin
JavaScript
TypeScript
HTML
CSS
SQL
JSON
YAML
XML
Docker
Maven
Gradle
NPM
Webpack
Vite
React
Spring
Hibernate
...
```

E então alguém olha para isso e fala:

> "Como a engenharia de software ficou produtiva."

Calma.

Às vezes a gente só ficou muito bom em criar ferramentas para resolver problemas criados por outras ferramentas.

Kof começa com uma pergunta simples:

> **E se o desenvolvedor pudesse descrever o que quer construir sem precisar escrever toda a burocracia necessária para convencer o computador a fazer aquilo?**

Não queremos esconder complexidade.

Queremos eliminar **complexidade acidental**.

---

# Menos código não significa menos engenharia

Kof não tenta transformar software em brinquedo.

Sistemas distribuídos continuam difíceis.

Concorrência continua difícil.

Banco de dados continua difícil.

Segurança continua difícil.

Arquitetura continua difícil.

Performance continua difícil.

Nenhuma linguagem vai salvar um sistema mal projetado.

O objetivo é outro.

Evitar que o engenheiro gaste energia escrevendo código que existe apenas porque a linguagem exigiu.

Existe uma diferença enorme entre:

```text
complexidade real
```

e:

```text
complexidade acidental
```

Kof quer atacar a segunda.

---

# Veja um exemplo

Uma estrutura simples:

```kf
record Point(int x, int y)
```

Essa declaração já expressa uma quantidade enorme de informação.

No backend JVM, o compilador pode transformar essa definição diretamente em bytecode JVM.

Sem gerar Java.

Sem transpilar para código-fonte intermediário.

Sem pedir para outro compilador interpretar aquilo.

O pipeline é:

```text
Kof Source
    │
    ▼
Lexer
    │
    ▼
Parser
    │
    ▼
AST
    │
    ▼
Semantic Analysis
    │
    ▼
Kof IR
    │
    ▼
Kof4J
    │
    ▼
JVM Bytecode
    │
    ▼
.class
    │
    ▼
JVM
```

O resultado é uma classe JVM real.

Não uma representação aproximada.

Não um arquivo Java esperando ser compilado.

**Bytecode.**

---

# Kof não é um transpiler

Isso é importante.

Kof não funciona assim:

```text
Kof → Java → javac → JVM
```

Funciona assim:

```text
Kof → Kof Compiler → Kof IR → Backend → Target
```

O compilador possui sua própria implementação de:

* lexer
* parser
* AST
* resolução de símbolos
* sistema de tipos
* análise semântica
* IR
* diagnostics
* geração de código

Isso significa que Kof não depende de Java como linguagem intermediária.

A JVM é apenas um dos ambientes para os quais Kof pode gerar código.

---

# Kof não é Kotlin 2

Kotlin fez uma coisa extremamente competente.

Pegou o ecossistema JVM, reduziu uma quantidade brutal da verbosidade do Java e criou uma linguagem moderna e produtiva.

Isso é algo que eu respeito.

Mas Kof não nasceu para ser Kotlin 2.

Kof não é:

```text
Java + açúcar sintático
```

A pergunta é diferente.

Não queremos apenas:

> "Como tornar Java mais agradável?"

Queremos:

> **"Como criar uma linguagem que expresse intenção diretamente e possa executar em diferentes ambientes sem mudar a linguagem?"**

Kof possui:

* sintaxe própria
* semântica própria
* compilador próprio
* IR própria
* backends próprios
* evolução própria

Kotlin resolveu problemas importantes.

Kof está tentando resolver outros.

---

# Kof também não é Julia para a JVM

Julia é uma linguagem extremamente interessante e muito forte em computação científica e numérica.

Mas essa não é a missão da Kof.

Kof é uma linguagem **geral**.

Queremos construir:

* APIs
* backends
* aplicações corporativas
* ferramentas
* serviços distribuídos
* automação
* aplicações desktop
* sistemas
* bibliotecas
* infraestrutura
* scripts
* aplicações web

Uma linguagem.

Muitos problemas.

Muitos targets.

---

# O verdadeiro diferencial: o compilador

Aqui está uma das decisões arquiteturais mais importantes da Kof.

O compilador não é dividido em quatro compiladores independentes.

Existe **um frontend da linguagem**.

```text
                Código Kof
                     │
                     ▼
                  Lexer
                     │
                     ▼
                  Parser
                     │
                     ▼
                    AST
                     │
                     ▼
             Symbol Resolution
                     │
                     ▼
               Type Checking
                     │
                     ▼
             Semantic Analysis
                     │
                     ▼
                  Kof IR
                     │
          ┌──────────┼──────────┐
          │          │          │
          ▼          ▼          ▼
       Kof4J      KofNative   KofScript
       Backend     Backend     Runtime
```

E o KofJS segue a mesma filosofia:

```text
Kof
 ↓
Kof Compiler
 ↓
Kof IR
 ↓
KofJS Backend
 ↓
Web Runtime
 ↓
Browser
```

O frontend não precisa saber se você quer:

```bash
kof build app.kf --target=jvm
```

ou:

```bash
kof build app.kf --target=native
```

ou:

```bash
kof run app.kf
```

ou:

```bash
kof build app.kf --target=js
```

A linguagem é a mesma.

O modelo semântico é o mesmo.

A representação intermediária é a mesma.

A diferença está no backend ou no modelo de execução.

Isso permite que Kof cresça sem transformar o projeto em uma coleção de linguagens diferentes compartilhando um nome.

---

# Kof4J

**Kof4J** é o backend JVM da Kof.

```text
Kof
 ↓
Kof Compiler
 ↓
Kof IR
 ↓
Kof4J Backend
 ↓
.class
 ↓
JVM
```

O objetivo é gerar **bytecode JVM diretamente**.

Não existe:

```text
Kof → Java → javac → JVM
```

Existe:

```text
Kof → Kof Compiler → JVM Bytecode → JVM
```

Isso permite aproveitar toda a infraestrutura da JVM sem transformar Kof em uma variante de Java.

E, principalmente, permite interoperabilidade com o ecossistema existente.

Spring.

Hibernate.

Maven.

Gradle.

Bibliotecas Java.

Ferramentas JVM.

Décadas de código.

Tudo isso continua sendo útil.

---

# Por que JVM?

Porque a JVM é uma puta plataforma.

Madura.

Portável.

Otimizada.

Extremamente estudada.

Com JIT.

Garbage collectors sofisticados.

Class loading.

Profilers.

Debuggers.

Ferramentas.

Bibliotecas.

E um ecossistema gigantesco.

Kof não precisa criar outro runtime para provar que consegue.

A JVM já está aí.

**Vamos usar.**

---

# KofNative

Mas às vezes você não quer JVM.

Você quer um executável.

Um arquivo.

Um processo.

Rodar.

Acabou.

É para isso que existe o **KofNative**.

```text
Kof
 ↓
Kof Compiler
 ↓
Kof IR
 ↓
KofNative Backend
 ↓
Native Binary
 ↓
Operating System
```

A ideia é permitir que Kof gere **binários nativos diretamente**, sem exigir uma JVM instalada para executar o programa.

Isso abre espaço para:

* ferramentas CLI
* utilitários
* aplicações enxutas
* containers
* sistemas
* ferramentas de infraestrutura
* aplicações que precisam de distribuição simples
* workloads onde um executável standalone faz mais sentido

O mesmo código Kof.

Outro backend.

Outro modelo de execução.

---

# KofScript

E às vezes você não quer compilar para distribuir.

Você só quer executar.

Agora.

Sem cerimônia.

Sem build.

Sem empacotamento.

Sem criar um projeto de 14 arquivos para imprimir uma mensagem.

É para isso que existe o **KofScript**.

```bash
kof run script.kf
```

O KofScript fornece um modelo de execução dinâmica e interativa para Kof.

Ideal para:

* scripts
* automação
* prototipação
* experimentos
* REPL
* tarefas rápidas
* desenvolvimento interativo
* ferramentas auxiliares

A mesma linguagem.

A mesma sintaxe.

O mesmo sistema conceitual.

Outro modo de execução.

É basicamente:

> "Eu quero usar Kof agora."

Então usa.

---

# KofJS

E aí chegamos ao navegador.

Porque aparentemente criar backend, runtime nativo e execução por script ainda não era suficiente.

Kof também possui uma visão para frontend através do **KofJS**.

A ideia é simples:

> **Você não deveria precisar escrever HTML e CSS manualmente só para desenhar uma interface.**

Hoje, construir uma interface pode envolver:

```text
HTML
CSS
JavaScript
TypeScript
DOM
Framework
Componentes
Bundler
Build system
Configuração
```

E você só queria colocar um botão na tela.

No KofJS, a proposta é trabalhar em uma camada mais próxima daquilo que você realmente quer construir.

Algo conceitualmente semelhante a:

```kf
screen UserScreen {

    title "Users"

    button "Create User" {

        onClick createUser()

    }

    list users {

        item user {

            text user.name
            text user.email

        }

    }

}
```

A sintaxe ainda está em evolução.

A ideia não.

**Você descreve a interface.**

**KofJS cuida da maquinaria web.**

HTML continua existindo.

CSS continua existindo.

JavaScript continua existindo.

Mas você não precisa necessariamente escrever tudo isso diretamente.

---

# Uma linguagem. Vários mundos.

Essa é a visão completa:

```text
                              KOF
                               │
                         Kof Compiler
                               │
                            Kof IR
                               │
          ┌────────────────────┼────────────────────┐
          │                    │                    │
          ▼                    ▼                    ▼
       Kof4J                KofNative           KofScript
          │                    │                    │
          ▼                    ▼                    ▼
         JVM              Native Binary          Runtime
          │                    │                    │
          └────────────────────┼────────────────────┘
                               │
                               ▼
                             KofJS
                               │
                               ▼
                              Web
```

Uma linguagem.

Um compilador.

Uma semântica.

Uma IR.

Vários backends.

Vários modelos de execução.

---

# O que isso significa na prática?

Você pode imaginar algo como:

```bash
kof build application.kf --target=jvm
```

Para JVM.

```bash
kof build application.kf --target=native
```

Para binário nativo.

```bash
kof run application.kf
```

Para execução através do KofScript.

E eventualmente:

```bash
kof build application.kf --target=js
```

Para Web.

O código não precisa ser reescrito porque o target mudou.

**O target é uma decisão do compilador.**

Não uma decisão da linguagem.

---

# Interoperabilidade

No mundo JVM, Kof não quer reinventar o ecossistema.

Se existe uma biblioteca Java, ela deve ser utilizável.

Isso inclui:

```text
Spring
Hibernate
JPA
Jackson
Netty
JUnit
Mockito
Kafka
Redis clients
Database drivers
...
```

A lista é praticamente infinita.

O objetivo é simples:

> **Seu código Java existente não deveria se tornar inútil só porque você decidiu experimentar Kof.**

---

# Tipagem forte e estática

Kof é construída ao redor de um sistema de tipos em tempo de compilação.

O compilador deve encontrar problemas antes que eles cheguem à execução sempre que possível.

Tipos não são sugestões.

O sistema de tipos existe para:

* detectar erros
* facilitar refatorações
* tornar APIs mais claras
* melhorar ferramentas
* permitir otimizações
* tornar grandes codebases mais previsíveis

E, principalmente:

**o desenvolvedor não deveria precisar repetir aquilo que o compilador já consegue entender.**

---

# O compilador é parte da linguagem

Um compilador não deveria responder apenas:

```text
error
```

e mandar você passar a tarde procurando o problema.

Ele deve explicar:

* onde está o erro
* o que aconteceu
* por que aconteceu
* o que era esperado
* o que foi encontrado
* como corrigir quando for possível

Diagnostics são parte da linguagem.

Tooling é parte da linguagem.

O compilador não é apenas um tradutor.

Ele é uma das principais ferramentas de desenvolvimento.

---

# Arquitetura

```text
                         Kof Source
                             │
                             ▼
                           Lexer
                             │
                             ▼
                           Parser
                             │
                             ▼
                            AST
                             │
                             ▼
                    Symbol Resolution
                             │
                             ▼
                       Type Checking
                             │
                             ▼
                     Semantic Analysis
                             │
                             ▼
                          Kof IR
                             │
            ┌────────────────┼────────────────┐
            │                │                │
            ▼                ▼                ▼
       JVM Backend      Native Backend   Script Runtime
            │                │                │
            ▼                ▼                ▼
          .class         Executable        Runtime
            │                │                │
            ▼                ▼                ▼
           JVM              OS            Kof Runtime
                             │
                             ▼
                        KofJS Backend
                             │
                             ▼
                           Web
```

O frontend da linguagem é compartilhado.

A IR funciona como a fronteira entre a linguagem e os targets.

Isso permite adicionar novos backends sem criar uma nova linguagem.

---

# Estrutura do projeto

```text
Kof/
│
├── kof-compiler/
│   └── Núcleo do compilador
│       ├── Lexer
│       ├── Parser
│       ├── AST
│       ├── Type System
│       ├── Semantic Analysis
│       ├── IR
│       ├── Diagnostics
│       └── Backends
│
├── kof-cli/
│   └── Interface de linha de comando
│
├── kof-runtime/
│   └── Runtime mínimo
│
├── docs/
│   └── Documentação e decisões arquiteturais
│
├── tests/
│   └── Golden tests e integration tests
│
└── README.md
```

---

# Estado atual

Kof está em desenvolvimento.

O compilador já possui uma fundação funcional.

Atualmente, a implementação consegue:

* processar arquivos `.kf`
* utilizar lexer próprio
* utilizar parser recursive descent próprio
* construir AST
* fazer lowering da AST para IR
* realizar geração de bytecode JVM
* utilizar ASM como backend de geração de bytecode
* compilar `record`
* gerar construtor
* gerar accessors
* gerar `toString`
* produzir arquivos `.class`
* carregar os `.class` gerados pela JVM
* executar o resultado

Por exemplo:

```kf
record Point(int x, int y)
```

já pode ser compilado para bytecode JVM válido.

O resultado pode ser carregado pela JVM e produzir corretamente:

```text
x() = 3
y() = 7
```

Isso é importante porque Kof não está apenas descrevendo uma arquitetura no papel.

**O compilador já está produzindo código executável.**

Os demais targets fazem parte da arquitetura e do desenvolvimento da linguagem.

---

# Construindo

O projeto atualmente utiliza Maven:

```bash
mvn clean install
```

Ou:

```bash
mvn clean install -DskipTests
```

---

# Executando

A CLI atualmente pode ser executada através do JAR gerado:

```bash
java -jar kof-cli/target/kof-cli-0.1.0-SNAPSHOT.jar build <source-dir>
```

A interface da CLI ainda está evoluindo junto com o compilador.

---

# Princípios

## 1. Menos código, mesma capacidade

Se uma informação já está presente no código, não obrigue o desenvolvedor a escrevê-la novamente.

---

## 2. Tipagem forte

O compilador deve trabalhar junto com você.

---

## 3. Intenção acima de cerimônia

O código deve expressar a ideia, não a burocracia.

---

## 4. Um frontend, múltiplos backends

A linguagem não deve mudar porque o target mudou.

---

## 5. Direto para o target

JVM significa bytecode.

Native significa binário.

Script significa execução pelo runtime.

Web significa código para o navegador.

Cada backend deve trabalhar diretamente com seu ambiente.

---

## 6. Interoperabilidade

Não jogue fora o mundo existente.

Use-o.

---

## 7. Sem mágica desnecessária

Abstração deve reduzir trabalho, não esconder o programa.

---

## 8. Ferramentas importam

Formatter.

Linter.

LSP.

Debugger.

REPL.

Package manager.

Tudo isso faz parte da experiência.

---

# Roadmap

## Fundação da linguagem

* [x] Lexer
* [x] Parser
* [x] AST
* [x] IR inicial
* [x] Records
* [ ] Sistema de tipos completo
* [ ] Classes
* [ ] Interfaces
* [ ] Generics
* [ ] Nullability
* [ ] Pattern matching
* [ ] Collections
* [ ] Exceptions
* [ ] Concurrency
* [ ] Functional features

## Kof4J

* [x] JVM backend inicial
* [x] Geração de `.class`
* [x] Geração de records
* [ ] Java interoperability
* [ ] Generics
* [ ] Annotations
* [ ] Maven integration
* [ ] Gradle integration
* [ ] Spring compatibility
* [ ] JVM debugging

## KofNative

* [ ] Native backend
* [ ] Native runtime
* [ ] Executáveis standalone
* [ ] Cross-platform builds
* [ ] Native libraries interoperability

## KofScript

* [ ] Runtime
* [ ] `kof run`
* [ ] REPL
* [ ] Interactive evaluation
* [ ] Script imports
* [ ] Standard scripting library

## KofJS

* [ ] UI model
* [ ] Component system
* [ ] Reactive state
* [ ] JavaScript backend
* [ ] Browser runtime
* [ ] HTML abstraction
* [ ] CSS abstraction
* [ ] Browser tooling

## Developer Experience

* [ ] Formatter
* [ ] Linter
* [ ] LSP
* [ ] VS Code
* [ ] IntelliJ IDEA
* [ ] Debugger
* [ ] Package manager

---

# O que Kof NÃO é

Kof não é:

* Java com outra sintaxe.
* Kotlin 2.
* Julia para JVM.
* Um transpiler.
* Um gerador de Java.
* Um interpretador fantasiado de compilador.
* Um framework web que decidiu se chamar linguagem.
* Um substituto da JVM.
* Uma promessa de eliminar toda complexidade da engenharia.
* Mais um projeto que chama `Hello World` de "revolutionary".

Kof é uma linguagem.

Um compilador.

Uma IR.

Vários backends.

E uma tentativa séria de construir uma experiência de desenvolvimento mais coerente.

---

# Por que tudo isso?

Porque talvez a coisa mais importante sobre Kof não seja nenhuma feature isolada.

Não é JVM.

Não é Native.

Não é Script.

Não é Web.

É a ideia de que **o desenvolvedor não deveria precisar trocar de linguagem toda vez que muda a camada da aplicação**.

Você não deveria precisar pensar:

> "Agora estou fazendo backend, então sou um tipo de programador."

> "Agora estou fazendo frontend, então preciso aprender outra linguagem."

> "Agora preciso de um script, então preciso de outra ferramenta."

> "Agora preciso de um executável, então preciso de outro ecossistema."

A visão da Kof é:

```text
Eu estou construindo software.

Kof é a linguagem.

O compilador decide onde ele vai viver.
```

---

# Estado do projeto

> **Kof está em desenvolvimento ativo e é experimental.**

Não use Kof para migrar sua empresa inteira amanhã.

Sério.

Não faça isso.

A linguagem pode quebrar.

A sintaxe pode mudar.

APIs podem desaparecer.

O backend pode ser reescrito.

A IR pode mudar.

Uma decisão arquitetural pode se provar completamente errada.

E provavelmente algumas vão.

É assim que se constrói uma linguagem.

Se você quer estabilidade absoluta hoje, use Java.

Se você quer assistir uma linguagem nascer, mexer no compilador, discutir semântica, escrever backend e ajudar a decidir onde isso tudo vai chegar...

**Bem-vinda.**

---

# Contribuindo

Você pode contribuir com:

* lexer
* parser
* AST
* type system
* semantic analysis
* IR
* JVM backend
* native backend
* script runtime
* JS backend
* compiler diagnostics
* tooling
* IDE
* LSP
* testes
* documentação
* benchmarks
* exemplos
* design da linguagem

E principalmente:

**questionando as decisões.**

Não precisa concordar com tudo.

Uma linguagem melhora quando suas premissas são atacadas.

---

# Filosofia

Kof nasceu de uma ideia simples:

> **Uma linguagem de programação deveria sair do caminho do desenvolvedor sem ficar entre o desenvolvedor e a máquina.**

Não queremos magia.

Não queremos burocracia.

Não queremos abstração por abstração.

Não queremos jogar fora décadas de tecnologia porque alguma coisa nova parece mais bonita.

Queremos uma linguagem:

**simples o suficiente para aprender.**

**poderosa o suficiente para construir software sério.**

**estrita o suficiente para evitar erros.**

**flexível o suficiente para conversar com diferentes runtimes.**

**próxima o suficiente da máquina para ser compreensível.**

**abstrata o suficiente para que você possa se concentrar no problema.**

---

# O começo

Toda linguagem começa em algum lugar.

Algumas começam em universidades.

Algumas começam em empresas.

Algumas começam em comitês.

Algumas começam em documentos de especificação com 800 páginas.

Kof começou de um jeito um pouco diferente.

Começou com uma ideia.

Depois virou código.

Depois virou parser.

Depois virou compilador.

Depois virou IR.

Depois virou bytecode.

E agora existe uma arquitetura para fazer essa mesma linguagem viver em mundos diferentes.

JVM.

Native.

Script.

Web.

Tudo através do mesmo compilador.

Talvez Kof se torne uma linguagem enorme.

Talvez continue sendo uma pequena linguagem construída por gente suficientemente maluca para achar que dava para fazer diferente.

Talvez falhe.

Talvez funcione.

Ninguém sabe.

E essa é justamente a parte mais interessante.

Porque o futuro ainda não foi escrito.

Só a primeira linha foi.

```text
                              KOF
                               │
                         Kof Compiler
                               │
                            Kof IR
                               │
        ┌──────────────────────┼──────────────────────┐
        │                      │                      │
        ▼                      ▼                      ▼
      Kof4J                KofNative              KofScript
        │                      │                      │
        ▼                      ▼                      ▼
       JVM                Native Binary             Runtime
        │                      │                      │
        └──────────────────────┬──────────────────────┘
                               │
                               ▼
                             KofJS
                               │
                               ▼
                              Web
```

Uma linguagem.

Um compilador.

Uma representação.

Vários mundos.

Menos cerimônia.

Mais intenção.

Código livre.

Construída do zero.

Construída em público.

Construída porque alguém olhou para o estado atual do desenvolvimento de software e pensou:

> **"Isso poderia ser muito mais simples."**

E decidiu fazer alguma coisa a respeito.

Não é o produto final.

Não é a revolução pronta.

Não é uma promessa de substituir tudo.

É o primeiro capítulo.

O compilador está sendo construído.

A IR está tomando forma.

O bytecode está sendo gerado.

O backend nativo está no horizonte.

O KofScript está esperando para executar.

O KofJS está esperando para chegar ao navegador.

E em algum lugar entre uma ideia completamente absurda e uma linha de código que finalmente compilou...

**Kof nasceu.**

---

# Bem-vinda à Kof.

### Uma linguagem. Um compilador. Vários mundos.

### Menos cerimônia. Mais intenção.

### JVM. Native. Script. Web.

### Do código ao mundo onde ele precisa existir.

**Que comece a porra da compilação.**

---

## Licença

Kof é software livre distribuído sob a licença **GNU General Public License v3.0**.

Consulte `LICENSE` para o texto completo da licença.

---

**Kof**

*Uma linguagem. Um compilador. Vários mundos.*

*Menos cerimônia. Mais intenção.*
