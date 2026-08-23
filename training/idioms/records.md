# Idioms — Records

**Status:** available · **Introduced:** 0.0.4-alpha

## What it is

Record é a forma de declarar **dados imutáveis com zero cerimônia**.

```kof
record Point(Int x, Int y)
```

O compilador gera: construtor canônico, accessors (`p.x()`), e no JVM também
`toString`/`equals`/`hashCode`.

## When to use

- Dados imutáveis (DTO, valor, chave, resultado).
- Agrupamento de valores sem comportamento.
- Quando em Java você escreveria `class` + construtor + getters + equals + hashCode.

## When not to use

- Estado mutável → classe.
- Comportamento sobre o estado → classe.

## BAD — classe com cerimônia para dados

```kof
class User {
    String name
    Int age
    public constructor(String name, Int age) {
        this.name = name
        this.age = age
    }
    public getName(): String {
        return name
    }
    public getAge(): Int {
        return age
    }
}
```

## GOOD — record

```kof
record User(String name, Int age)
```

Uso:

```kof
var u = User("Mel", 30)
println(u.name())
println(u)
```

No JVM, `println(u)` imprime `User[name=Mel, age=30]` (toString gerado).

## WHY

Record elimina a cerimônia que Java exige para dados. Se o problema é
"representar um objeto de dados", record é a resposta padrão.

## Criação

```kof
var a = Point(10, 20)      // construtor canônico
var b = new Point(3, 4)    // também aceito
```

## JSON

Records são suportados por `json.encode`/`json.decode<T>` no JVM:

```kof
var p = Point(3, 4)
var j = json.encode(p)                 // {"x":3,"y":4}
var d = json.decode<Point>("{\"x\": 10, \"y\": 20}")
```

**Limitação:** JSON de objetos/records no target Native não é suportado (diagnostic JSN002).

## Anti-patterns relacionados

- `java-like-code.md` — record + getters
- `unnecessary-abstraction.md` — wrapper em volta de record sem motivo