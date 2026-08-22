# 30 — Contribuindo

## Estrutura do repositório

```
kof/
├── kof-compiler/       ← compilador core
├── kof-cli/            ← linha de comando
├── kof-runtime/        ← runtime (futuro)
├── docs/               ← documentação interna
├── learn/              ← este material
├── tests/              ← testes golden
├── pom.xml             ← build Maven
└── README.md
```

## Buildando

```bash
mvn clean package -DskipTests
```

## Rodando testes

```bash
mvn test
```

## Estrutura do compilador

```
kof-compiler/src/main/java/dev/kof/compiler/
├── Lexer.java          ← lexer hand-written
├── Parser.java         ← parser recursivo descendente
├── AstNodes.java       ← nós da AST
├── Type.java           ← sistema de tipos
├── SymbolTable.java    ← tabela de símbolos
├── IRNodes.java        ← operações IR
├── CompilerDriver.java ← orquestrador
├── Backend.java        ← interface de backend
├── Target.java         ← enum de targets
├── JvmBackend.java     ← geração de bytecode JVM
├── NativeBackend.java  ← geração de código nativo
├── Diagnostic.java     ← diagnósticos
├── DiagnosticCollector.java
├── CompilationResult.java
├── Token.java          ← representação de token
├── TokenType.java      ← enum de tipos de token
└── SourcePosition.java ← posição no código
```

## Como adicionar uma feature

### 1. Lexer

Se a feature precisa de uma nova keyword ou operador:

- Adicione o token em `TokenType.java`
- Adicione o reconhecimento em `Lexer.java`

### 2. Parser

Se a feature precisa de nova sintaxe:

- Adicione o nó AST em `AstNodes.java`
- Adicione o parsing em `Parser.java`

### 3. Lowering

Se a feature precisa gerar IR:

- Adicione operações IR em `IRNodes.java` (se necessário)
- Adicione o lowering em `CompilerDriver.java`

### 4. Backend

Se a feature precisa de novas instruções:

- Para JVM: adicione o emission em `JvmBackend.java`
- Para nativo: adicione o emission em `NativeBackend.java`

### 5. Testes

- Crie um arquivo `.kf` em `tests/golden/`
- Crie um teste shell que valide o output

## Como alterar o type checker

O type checker está em desenvolvimento. Quando estiver pronto:

1. Adicione regras em uma nova classe `TypeChecker.java`
2. O type checker roda entre o parser e o lowering
3. Erros de tipo são reportados via `DiagnosticCollector`

## Como alterar o backend JVM

O backend usa ASM. Para adicionar uma nova instrução:

1. Defina a operação IR em `IRNodes.java`
2. Adicione o emission em `JvmBackend.emitOperation()`
3. Atualize `computeStack()` e `computeLocals()`

## Como alterar o backend nativo

O backend gera assembly x86-64. Para adicionar uma nova instrução:

1. Defina a operação IR em `IRNodes.java`
2. Adicione a geração de assembly em `NativeBackend.emitOperation()`
3. Considere a calling convention System V AMD64

## Como criar testes golden

1. Crie um arquivo `.kf` em `tests/golden/`
2. O compilador deve gerar um `.class` (JVM) ou executável (nativo)
3. Verifique com `javap -v` que o bytecode está correto (JVM)
4. Teste que a classe carrega e executa na JVM
5. Para nativo, teste que o executável roda e produz o output esperado

## Como atualizar documentação

Sempre que uma feature mudar:

1. Verifique se `/learn` precisa ser atualizado
2. Verifique se `docs/` precisa ser atualizado
3. Mantenha a documentação sincronizada com o código

## Regras para pull requests

1. Uma feature por PR
2. Testes para cada feature
3. Documentação atualizada
4. Sem comentários no código
5. Código que compila sem warnings

## Estado atual do projeto

O projeto está em fase inicial, mas funcional:

**Funciona hoje:**
- Lexer completo com 55+ keywords
- Parser recursivo descendente funcional
- Records, classes e interfaces
- Funções com `main()`
- CLI com build, run, version
- Backend JVM via ASM — gera `.class` funcionais
- Backend Nativo — gera ELF x86-64
- Testes golden baseados em shell

**Em desenvolvimento:**
- Type checking completo
- Resolução de variáveis
- Controle de fluxo
- Expressões complexas

**Planejado:**
- Generics
- Exceptions
- Pattern matching
- Collections
- Java interop
- KofScript
- KofJS

## Próximo passo

[Glossário →](glossary.md)