# 22 — JVM

## Como Kof roda

```
Você escreve:  record Point(Int x, Int y)
                      ↓
Compilador Kof: lexer → parser → AST → IR → bytecode
                      ↓
JVM recebe:    Point.class
                      ↓
Class Loader:  carrega Point.class na memória
                      ↓
Bytecode Verifier: verifica se o bytecode é seguro
                      ↓
JIT Compiler:  converte bytecode para machine code nativo
                      ↓
Execução:      roda como qualquer programa Java
```

## Bytecode

O bytecode é a representação intermediária do programa. É o que o compilador gera e a JVM executa.

Cada instrução bytecode é muito simples:

```
aload_0      → carrega a referência "this"
iload_1      → carrega o inteiro do parâmetro 1
putfield     → armazena um valor em um campo
invokevirtual → chama um método
```

Uma linha de Kof pode gerar várias instruções bytecode.

## Class Loading

Quando a JVM encontra `Point.class`:

1. **Loading**: lê o arquivo `.class` e cria uma representação interna
2. **Linking**: verifica integridade, aloca memória para constantes
3. **Initialization**: executa static initializers (se existirem)

## Bytecode Verification

Antes de executar, a JVM verifica:
- os tipos estão corretos
- as instruções são válidas
- o stack não transborda
- os jumps apontam para posições válidas

Se a verificação falhar, o programa não roda.

## JIT (Just-In-Time) Compiler

A JVM não executa bytecode diretamente. Ela compila para machine code nativo em runtime.

- Métodos que rodam pouco: executam como bytecode
- Métodos que rodam muito (hot): compilados para nativo
- O JIT otimiza baseado em profiling real

Isso significa que código Kof pode ser tão rápido quanto código C++ após warmup.

## Garbage Collection

Kof não precisa de gerenciamento manual de memória. A JVM coleta automaticamente objetos que não são mais referenciados.

## Memory Model

Kof respeita o Java Memory Model:
- `volatile` garante visibilidade entre threads
- `synchronized` garante atomicidade
- Happens-before relationship é preservado

## Próximo passo

[Testes →](23-testing.md)
