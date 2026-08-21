# Multiplatform — Uma Linguagem, Múltiplos Mundos

## A visão

Kof não é apenas uma linguagem para a JVM. É uma linguagem que pode compilar para diferentes targets, mantendo a mesma sintaxe e semântica.

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
```

## Os backends

### Kof4J (JVM)

O backend JVM gera bytecode `.class` que roda em qualquer JVM.

```kf
record Point(Int x, Int y)
```

```bash
kof build point.kf --target=jvm
# Gera: Point.class
```

**Vantagens:**
- Compatibilidade total com ecossistema Java
- Acesso a milhões de bibliotecas
- JIT compilation
- Garbage collection sofisticada
- Portabilidade (qualquer JVM)

### KofNative (Nativo)

O backend nativo gera executáveis ELF x86-64 para Linux.

```kf
fun main() = print("Hello, World!")
```

```bash
kof build main.kf --target=native
# Gera: main (executável ELF)
./main
# Output: Hello, World!
```

**Vantagens:**
- Sem necessidade de JVM instalada
- Executável standalone
- Performance nativa
- Distribuição simples (apenas o binário)
- Ideal para ferramentas CLI e sistemas

### KofScript (Futuro)

O runtime interativo para scripts e prototipação.

```bash
kof run script.kf
# Executa diretamente sem compilar
```

**Vantagens:**
- Sem build step
- Execução imediata
- Ideal para automação e experimentos

### KofJS (Futuro)

O backend web para geração de código JavaScript.

```bash
kof build app.kf --target=js
# Gera: app.js
```

**Vantagens:**
- Mesma linguagem para backend e frontend
- Sem necessidade de aprender JavaScript
- Acesso ao DOM e APIs do navegador

## Como funciona

### Pipeline de compilação

```text
Kof Source (.kf)
    │
    ▼
  Lexer → tokens
    │
    ▼
  Parser → AST
    │
    ▼
  IR (compartilhado)
    │
    ├──────────► JVM Backend → .class
    │
    ├──────────► Native Backend → ELF
    │
    └──────────► Script Backend → Runtime
```

### IR compartilhada

A representação intermediária (IR) é compartilhada entre todos os backends. Isso permite:

1. **Mesma linguagem** — não existem dialetos para diferentes targets
2. **Mesma semântica** — o significado do código não muda
3. **Otimizações compartilhadas** — melhorias na IR beneficiam todos os backends
4. **Fácil adição de novos backends** — basta implementar a tradução IR → target

### Exemplo multiplatform

```kf
record Point(Int x, Int y)

fun main() {
    var p = Point(3, 7)
    println(p)
}
```

**JVM:**
```bash
kof build main.kf --target=jvm
java -cp . main
# Output: Point[x=3, y=7]
```

**Nativo:**
```bash
kof build main.kf --target=native
./main
# Output: Point[x=3, y=7]
```

**Mesmo código. Mesmo output. Targets diferentes.**

## Quando usar cada backend

| Backend | Use quando | Exemplos |
|---------|------------|----------|
| **JVM** | Precisa de ecossistema Java, bibliotecas, frameworks | APIs Spring, microserviços, aplicações corporativas |
| **Nativo** | Precisa de executável standalone, sem JVM | Ferramentas CLI, containers, sistemas, utilitários |
| **Script** | Precisa de execução rápida, sem build | Automação, scripts, prototipação |
| **JS** | Precisa de frontend web | Interfaces web, SPAs, PWAs |

## Status atual

| Backend | Status | Descrição |
|---------|--------|-----------|
| **JVM** | ✅ Funcional | Gera `.class` via ASM |
| **Nativo** | ✅ Funcional | Gera ELF x86-64 via assembly |
| **Script** | ❌ Planejado | Runtime interativo |
| **JS** | ❌ Planejado | Backend JavaScript |

## Arquitetura do compilador

```text
                    Kof Source (.kf)
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
                     Kof IR (compartilhada)
                      /       \
                     /         \
                    ▼           ▼
             JVM Backend    Native Backend
                    │           │
                    ▼           ▼
                .class       ELF .o
                    │           │
                    ▼           ▼
                javac/jar     ld → executável
```

## Documentação

- [Arquitetura do KofNative](architecture.md) — detalhes da arquitetura multiplatform
- [Opções de Backend](backend-options.md) — análise das opções de backend nativo
- [Roadmap](roadmap.md) — plano de desenvolvimento do backend nativo

## Próximo passo

[Arquitetura do KofNative →](architecture.md)