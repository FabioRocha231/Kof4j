# 07 — Classes e Objetos

## O que você vai aprender

Neste capítulo você vai entender como definir classes, criar objetos, e como a orientação a objetos funciona em Kof.

## Records

O construto mais simples para definir dados:

```kf
record User(String name, String email)
```

Isso gera:
- classe `User` que estende `java.lang.Record`
- campos `name` e `email` (privados, finais)
- construtor `(String, String)`
- métodos `name()` e `email()`
- método `toString()`

## Classes

Para algo mais complexo:

```kf
class Calculadora {
    Int resultado

    Calculadora() {
        this.resultado = 0
    }

    void somar(Int valor) {
        this.resultado += valor
    }

    Int getResultado() {
        return this.resultado
    }
}
```

## Construtores

### Construtor padrão

Se você não definir nenhum construtor, um padrão vazio é gerado:

```kf
class Config {
    String host
    Int porta
}

// funciona: new Config()
```

### Construtor com parâmetros

```kf
class Connection(String host, Int porta) {
    // campos e lógica de inicialização
}
```

## Campos

```kf
class User {
    String name
    String email
    Bool active = true
}
```

### Modificadores de acesso

```kf
class Conta {
    private Double saldo
    public String titular

    public void depositar(Double valor) {
        this.saldo += valor
    }

    public Double getSaldo() {
        return this.saldo
    }
}
```

## Métodos

```kf
class StringUtils {
    static String repetir(String texto, Int vezes) {
        var resultado = ""
        for (var i = 0; i < vezes; i++) {
            resultado += texto
        }
        return resultado
    }
}
```

## this e super

```kf
class Animal {
    String nome

    Animal(String nome) {
        this.nome = nome
    }
}

class Cachorro extends Animal {
    String raca

    Cachorro(String nome, String raca) {
        super(nome)
        this.raca = raca
    }
}
```

## Status atual

✅ Records funcionam completamente
✅ Declarações de classe com campos funcionam
✅ Métodos são gerados (assinaturas)
⚠️ Corpo dos métodos ainda em desenvolvimento
⚠️ Construtores personalizados ainda em desenvolvimento
❌ Herança ainda não funciona

## Multiplatform

Records funcionam tanto no backend JVM quanto no nativo:

**JVM:** gera uma classe que estende `java.lang.Record`
**Nativo:** gera uma struct C com os campos

```kf
record Point(Int x, Int y)
```

No JVM:
```java
// Gerado: classe Point extends Record
// campos: int x, int y
// construtor: Point(int x, int y)
// accessors: int x(), int y()
```

No nativo:
```c
// Gerado: struct Point { int x; int y; }
```

## Exercício 1

Crie uma classe `ContaBancaria` com:
- campos `titular` e `saldo`
- método `depositar(valor)`
- método `sacar(valor)`
- método `getSaldo()`

## Exercício 2

Crie um record `Retangulo` com `largura` e `altura`. Adicione um método `area()` que retorne a área.

## Próximo passo

[Propriedades →](08-properties.md)