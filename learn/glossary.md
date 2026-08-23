# Glossário

## A

**Abstract Syntax Tree (AST)** — representação hierárquica da estrutura sintática do código. Cada nó da árvore representa uma construção da linguagem (classe, método, expressão, etc.).

**Annotation** — metadado que pode ser adicionado a classes, métodos, campos ou parâmetros. Usado por frameworks como Spring e JPA.

**ASM** — biblioteca Java para geração e transformação de bytecode JVM. Usada pelo compilador Kof para gerar `.class`.

**Bytecode** — representação intermediária do programa que a JVM executa. Gerado pelo compilador.

## C

**Cast** — conversão explícita de um tipo para outro. Em Kof: `valor as Tipo`.

**Checked Exception** — exception que deve ser declarada no método ou tratada com try/catch. Exemplo: `IOException`.

**Class Loading** — processo da JVM de carregar um arquivo `.class` e torná-lo disponível para execução.

**Compiler** — programa que traduz código fonte para bytecode. O compilador Kof traduz `.kf` para `.class`.

**Constant Pool** — tabela de constantes dentro de um arquivo `.class` contendo strings, nomes de classes, métodos, etc.

## D

**Desugaring** — transformação de syntactic sugar em construtos mais básicos. Exemplo: `for-each` virando `while`.

**Diagnostic** — mensagem de erro ou aviso gerada pelo compilador.

**Domain Model** — representação dos conceitos do problema no código.

## E

**Erasure** — processo de remover informações de tipos genéricos em runtime. `List<String>` vira `List` no bytecode.

**Expression** — código que produz um valor. Exemplo: `2 + 3`, `"olá"`, `user.name()`.

**Expression Body** — forma concisa de definir uma função: `Int dobro(Int x) = x * 2;`

## G

**Garbage Collection (GC)** — processo automático de liberação de memória não utilizada. A JVM faz isso.

**Generic** — mecanismo de parametrização de tipos. Exemplo: `class Caixa<T>`.

**GraalJS** — engine JavaScript embarcada que o Kof usa para executar o target
KofJS (`kof run --target=js`) sem depender de Node.js.

## K

**Kof IR** — representação intermediária única da Kof: o mesmo frontend gera a
IR e os três backends (JVM, Native, KofJS) a consomem.

**KofJS** — target `js` da Kof: gera ES Modules (ECMAScript 2022+) a partir da
Kof IR. Ver `learn/37-kofjs.md`.

**kof.ui** — plataforma de UI: `Color`, `Theme`, `Palette`, `Window`, `Label`,
`Button`, `Input`, `Column`/`Row`, `View`+`Style`. Renderização é KofJS
(webview nativo `kof-webview` com WebKitGTK ou browser); nos alvos JVM/Native
os handles são no-ops. Ver `learn/35-kof-ui.md`.

**kof-webview** — shell nativo (WebKitGTK embutido, Linux) que abre a
aplicação KofJS interativa; `kof run --target=js` aguarda a janela fechar.

## I

**IIFE (Imediatamente Invoked Function Expression)** — função executada imediatamente após ser definida.

**Imutabilidade** — propriedade de um objeto que não pode ser alterado após criação. Records são imutáveis.

**Inference** — capacidade do compilador deduzir o tipo automaticamente. `var x = 42` infere `Int`.

**Intermediate Representation (IR)** — representação entre a AST e o bytecode. Mais baixa que a AST, mais alta que bytecode.

**Invocation Target** — método que será chamado por uma instrução `invoke*`.

## J

**JIT (Just-In-Time)** — compilador da JVM que converte bytecode para machine code nativo em runtime.

**JVM (Java Virtual Machine)** — máquina virtual que executa bytecode. Kof roda na JVM.

## L

**Lexer** — fase do compilador que converte texto em tokens.

**Lowering** — processo de transformar construtos de alta nível em construtos mais baixos. Exemplo: records viram classes com campos e métodos.

## M

**Metadata** — dados sobre dados. No contexto de bytecode: informações sobre tipos, annotations, linha/coluna, etc.

**Method Descriptor** — string que descreve a assinatura de um método. Exemplo: `(II)V` para `void m(int, int)`.

## P

**Parser** — fase do compilador que converte tokens em AST.

**Pattern Matching** — mecanismo de verificar se um valor corresponde a um padrão e extrair dados dele.

**Primitive Type** — tipo fundamental da JVM: `int`, `long`, `float`, `double`, `boolean`, `byte`, `short`, `char`.

## R

**Record** — classe imutável gerada automaticamente. Em Kof: `record Point(Int x, Int y)`.

**Reference Type** — tipo que referencia um objeto na heap. Exemplo: `String`, `User`, `List`.

**Reflection** — capacidade de inspecionar e modificar estruturas do programa em runtime.

## S

**Sealed Class** — classe que restringe quais classes podem herdar dela.

**Statement** — instrução que realiza uma ação. Exemplo: `return x;`, `if (c) { ... }`.

**Static Type Checking** — verificação de tipos feita pelo compilador antes da execução.

**Symbol Table** — estrutura de dados que armazena informações sobre identificadores (nomes de classes, métodos, variáveis).

## T

**Token** — unidade léxica最小ima. Exemplo: `record`, `Point`, `(`, `Int`.

**Type Erasure** — remoção de informações de tipos genéricos em runtime.

**Type System** — conjunto de regras que definem como tipos interagem.

## V

**Value Type** — tipo imutável que representa dados. Records são value types.

**Virtual Thread** — thread leve gerenciada pela JDK. Disponível desde Java 21.
