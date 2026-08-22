# Idioms — Classes

**Status:** available · **Introduced:** 0.0.4-alpha

## What it is

Classe com campos, métodos, construtor, herança e interfaces.
Campos são declarados **sem** `var`/`val` e sem `;` obrigatório.

```kof
class User {
    String name
    Int age
}
```

## Construtores

O construtor é declarado com a palavra-chave `constructor` (sem `fun`, sem nome da classe).

```kof
class User {
    String name
    Int age

    public constructor(String name, Int age) {
        this.name = name
        this.age = age
    }
}
```

Uso: `new User("Mel", 30)`.

## When to use

- Entidades com comportamento (métodos que operam sobre o estado).
- Estado mutável.
- Herança e polimorfismo.

## When not to use

- Dados imutáveis sem comportamento → **record** (veja `records.md`).
- Apenas agrupamento de valores → record.

## BAD — cerimônia de getter

```kof
class User {
    private String name
    public getName(): String {
        return name
    }
    public setName(String name) {
        this.name = name
    }
}
```

## GOOD — campo direto

```kof
class User {
    String name
}
```

Uso: `u.name = "Mel"` e `println(u.name)`.

## WHY

Getter/setter de Java existe por convenções de encapsulamento (JavaBeans, frameworks).
Kof não possui essas convenções. Campo público é a forma idiomática até que exista
uma razão real para encapsulamento. Não reproduza ceremony sem semântica.

## BAD — factory trivial

```kof
createUser(String name): User {
    return User(name)
}
```

## GOOD

```kof
User(name)
```

## WHY

Uma factory que apenas delega ao construtor não adiciona informação. Chame o construtor.

## Herança

```kof
class Animal {
    String name
    public constructor(String name) {
        this.name = name
    }
    speak(): String = "animal"
}
class Dog extends Animal {
    public constructor(String name) {
        super(name)
    }
    speak(): String = "dog"
}
```

- `super(args)` é a primeira instrução do construtor da subclasse.
- Override é implícito (mesmo nome de método).
- Dispatch é virtual em ambos os targets.

## Anti-patterns relacionados

- Utility class de métodos estáticos → funções top-level (`functions.md`)
- Service layer sem estado → funções top-level
- Factory trivial → chamar o construtor