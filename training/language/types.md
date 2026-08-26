# Kof Types

## Primitive Types

| Type | Size | Description |
|------|------|-------------|
| `bool` | 4 bytes | Boolean |
| `byte` | 1 byte | Signed byte |
| `short` | 2 bytes | Signed short |
| `int` | 4 bytes | Signed integer |
| `long` | 8 bytes | Signed long |
| `float` | 4 bytes | IEEE 754 float |
| `double` | 8 bytes | IEEE 754 double |
| `char` | 4 bytes | UTF-32 codepoint |
| `string` | reference | KofString |
| `void` | — | No return |

## Reference Types

### Classes
```kof
class User {
    String name
    Int age
}
```

### Records
```kof
record Point(Int x, Int y)
```

### Arrays
```kof
var arr = new Int[10]
var strings = new String[5]
```

### Interfaces
```kof
interface Speaker {
    speak(): String
}
```

### Enums

```kf
enum Color { Red, Green, Blue }
```

- Runtime representation: the constant name itself (String-backed).
- `==` compares by content; constant name printed directly.
- `Color.values() -> List<String>`; `Color.valueOf("Red") -> Color?`;
  `c.name() -> String`.
- Unknown constant → compile error SEM030.
- Exhaustive switch required (all constants or default) → SEM031.
- Mapped to `java/lang/String` in JVM descriptors on all targets.

## Type Inference

```kof
var x = 10          // Int
var s = "Hello"     // String
var p = Point(1, 2) // Point
```

## Explicit Types

```kof
Int x = 10
String s = "Hello"
Point p = Point(1, 2)
```

## Type Compatibility

- Widening: `Int` → `Long` → `Float` → `Double`
- String + anything → String (concatenation)
- Comparison operators → Bool
- Logical operators → Bool
