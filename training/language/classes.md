# Kof Classes

## Basic Class

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

## Records (Immutable Data)

```kof
record Point(Int x, Int y)
// Auto-generates: constructor, accessors x(), y(), toString()
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
    fun speak(): String
}
class Dog implements Speaker {
    public fun speak(): String {
        return "woof"
    }
}
```

## Virtual Dispatch

```kof
class Animal {
    public fun speak(): String { return "animal" }
}
class Dog extends Animal {
    public fun speak(): String { return "dog" }
}
fun main() {
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
