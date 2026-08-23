# 00 — Introdução

## O que é Kof

Kof é uma linguagem de programação compilada para múltiplas plataformas.

Ela existe por uma razão simples: Java é uma das plataformas mais poderosas do mundo, mas exige uma quantidade absurda de código para expressar ideias simples.

Kof mantém o poder da JVM e do ecossistema Java, mas remove a maior parte da ceremony. E agora, essa mesma linguagem pode gerar binários nativos para Linux x86-64.

## A visão multiplatform

Kof não é apenas uma linguagem para a JVM. É uma linguagem que pode compilar para diferentes targets:

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

**A linguagem não muda. O target muda.**

Isso significa que você pode escrever o mesmo código Kof e compilar para:
- **JVM** — bytecode `.class` que roda em qualquer JVM
- **Nativo** — executável ELF x86-64 que roda direto no Linux
- **KofJS** — ES Modules (ECMAScript 2022+) executados na engine JS
  embarcada (sem Node); `kof.ui` renderiza em webview nativo ou browser.
  Ver [capítulo 37](37-kofjs.md).

## A comparação visual

Java:

```java
public final class User {

    private final String name;
    private final String email;

    public User(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public String name() {
        return name;
    }

    public String email() {
        return email;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return Objects.equals(name, other.name)
            && Objects.equals(email, other.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, email);
    }

    @Override
    public String toString() {
        return "User[name=" + name + ", email=" + email + "]";
    }
}
```

Kof:

```kf
record User(String name, String email)
```

O compilador gera exatamente a mesma coisa: uma classe JVM com campos, construtor, accessors, equals, hashCode e toString.

## Filosofia

Kof segue três princípios:

**1. Menos código, mesma capacidade.**

Cada linha que você escreve em Kof precisa ter o mesmo peso semântico que a equivalente em Java. Não removemos funcionalidade — removemos repetição.

**2. Tipo forte, compilação estática.**

O compilador conhece seus tipos. Erros são encontrados antes de o programa rodar. Isso não muda — é uma das grandes forças da JVM.

**3. A JVM é o runtime.**

Kof não inventa garbage collector, scheduler, ou modelo de memória. A JVM já faz tudo isso. Kof usa o que já existe.

## Relação com Java

Kof é **compatível com Java**, não é um substituto.

Código Kof gera bytecode JVM padrão. Esse bytecode pode:
- ser chamado por código Java
- chamar código Java
- usar qualquer biblioteca Java
- rodar em qualquer JVM

Kof não reescreve o ecossistema Java. Kof se conecta a ele.

## Relação com Kotlin

Kotlin resolve o mesmo problema (Java é verboso) de uma forma diferente.

Kotlin adicionou muitas features novas à linguagem: data classes, sealed classes, coroutines, extension functions, null safety, etc.

Kof tenta resolver o mesmo problema de uma forma mais minimalista. Em vez de adicionar muitas features novas, Kof tenta expressar as mesmas ideias do Java com menos código.

Se uma ideia de outra linguagem for melhor, Kof pode adotar a ideia. Não há fanatismo aqui.

## O que Kof NÃO tenta resolver

Kof não tenta ser:
- uma linguagem funcional
- uma linguagem para sistemas distribuídos
- uma linguagem para machine learning

Kof tenta ser a melhor forma de escrever código orientado a objetos para a
JVM, para binários nativos e — via KofJS — para a web (frontend com
`kof.ui` + `kof run --target=js`; ver [capítulo 37](37-kofjs.md)).

## Por que "Kof"

O nome é curto, fácil de digitar, e não conflita com nenhuma biblioteca Java conhecida.

## Como funciona por baixo

```
Você escreve:     record User(String name)
                        ↓
Compilador Kof:   lexer → parser → AST → IR → backend
                        ↓
                  ┌─────┴─────┐
                  │           │
               JVM          Native
                  │           │
                  ▼           ▼
             User.class    ELF x86-64
                  │           │
                  ▼           ▼
              funciona    executável
              como uma    direto no
              classe      Linux
              Java normal
```

Não existe etapa de geração de Java. Não existe interpretador. O compilador gera bytecode ou código nativo diretamente.

## Próximo passo

[Vamos instalar tudo →](01-installation.md)