# Kof Classes

**Version:** 0.2.6-beta (02 Sep 2026)

## Basic Class (idiomático: primary constructor)

```kof
class User(String name, Int age) { }
var u = User("Mel", 30)

// Verboso ainda válido
class User2 {
    String name
    Int age
    public constructor(String name, Int age) {
        this.name = name
        this.age = age
    }
}
```

## Records (Immutable Data)

```kof
record Point(Int x, Int y)
// Auto-generates: constructor, accessors x(), y(), toString()
switch (p) {
    case Point(var x, var y): println(x + "," + y) // 0.2.6-beta destructuring
}
```

## Inheritance

```kof
class Animal {
    String name
    public constructor(String name) {
        this.name = name
    }
}
class Dog extends Animal {
    public constructor(String name) {
        super(name)
    }
}
```

## Interfaces

```kof
interface Speaker {
    speak(): String
}
class Dog implements Speaker {
    public speak(): String {
        return "woof"
    }
}
```

## Virtual Dispatch

```kof
class Animal {
    public speak(): String { return "animal" }
}
class Dog extends Animal {
    public speak(): String { return "dog" }
}
main() {
    Animal a = new Dog()
    println(a.speak())  // prints "dog" (virtual dispatch)
}
```

## Field Initialization

```kof
class Config {
    String host = "localhost"
    Int port = 8080
    public constructor() {
    }
}
```

## Access Modifiers

- `public` — accessible everywhere
- `private` — accessible within class
- `protected` — accessible in subclass
- `static` — class-level
- `final` — immutable
