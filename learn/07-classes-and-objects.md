# 07 — Classes e Objetos

> **Kof 0.2.0-beta — `Point(x,y)` destructuring + `String?` + `intention->Kof->frontend->IR->backend->runtime`**

## O que você vai aprender

Neste capítulo você vai entender como definir classes, criar objetos, e como a orientação a objetos funciona em Kof.

## Construtor primário (a forma idiomática)

A forma recomendada para declarar dados em Kof:

```kf
class User(String name, String email)
```

O compilador gera semanticamente:

```text
field name: String
field email: String
constructor(String, String)
```

Sem exigir `this.name = name`:

```kf
class User(
    String name,
    String email
) {
    greeting(): String {
        return name + " <" + email + ">"
    }
}

main() {
    var user = User("Mel", "mel@kof.dev")
    println(user.greeting())
}
```

Os parâmetros do construtor primário são campos reais da classe: métodos
acessam `name` e `email` diretamente, e o programa externo acessa `user.name`.

## Construção: `new` é opcional

Kof permite ambas as formas, com a mesma semântica:

```kf
var user = User("Mel", 26)      // forma idiomática (recomendada)
var old = new User("Mel", 26)   // forma explícita (retrocompatível)
```

`new` continua válido para código legado; o compilador trata ambas como
construção de instância.

## Records

Um `record` é um construto de dados com valor semântico:

```kf
record Token(String kind, String text)
```

Isso gera:
- classe `Token` que estende `java.lang.Record`
- campos `kind` e `text` (privados, finais)
- construtor `(String, String)`
- métodos `kind()` e `text()`
- `toString()`, `equals()`, `hashCode()`

Records podem ter métodos:

```kf
record Token(String kind, String text) {
    label(): String {
        return kind + "(" + text + ")"
    }
}
```

## Classes

Para algo mais complexo, com corpo:

```kf
class Calculadora {
    Int resultado = 0

    void somar(Int valor) {
        resultado += valor
    }

    Int getResultado() {
        return resultado
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

var config = Config()          // funciona
var legacy = new Config()      // também funciona
```

### Construtor primário

```kf
class Connection(String host, Int porta) {
    hostInfo(): String {
        return host + ":" + porta
    }
}
```

### Construtor explícito (forma verbosa, ainda válida)

```kf
class Connection {
    String host
    Int porta

    constructor(String host, Int porta) {
        this.host = host
        this.porta = porta
    }
}
```

A forma explícita existe para compatibilidade, mas não é necessária.

## Campos

```kf
class User {
    String name
    String email
    Bool active = true
}
```

Inicializadores de campo são aplicados em todos os construtores, antes do
corpo — em JVM, Native e KofJS.

### Modificadores de acesso

```kf
class Conta {
    private Double saldo
    public String titular

    public void depositar(Double valor) {
        saldo += valor
    }

    public Double getSaldo() {
        return saldo
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

    constructor(String nome) {
        this.nome = nome
    }
}

class Cachorro extends Animal {
    String raca

    constructor(String nome, String raca) {
        super(nome)
        this.raca = raca
    }
}
```

## Status atual

✅ Construtor primário gera campos + construtor + acesso dentro de métodos
✅ Records com métodos funcionam
✅ `Classe(...)` sem `new` e `new Classe(...)` com a mesma semântica
✅ Inicializadores de campo (JVM, Native, KofJS)
✅ Herança, virtual dispatch, interfaces
✅ Classes com construtor explícito

## Multiplatform

A mesma fonte funciona em JVM, Native e KofJS:

```kf
record Point(Int x, Int y)
```

**JVM:** classe que estende `java.lang.Record` (campos, accessors, construtor)
**Nativo:** struct equivalente com fields e métodos
**KofJS:** classe ES com a mesma semântica observável

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