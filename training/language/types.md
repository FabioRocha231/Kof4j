# Kof Types

**Version:** 0.2.6-beta (30 Aug 2026)

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

Nullable: suffix `?` → `String?`, `Int?`, `Point?` (NullableType, 0.2.6-beta). `if (x != null)` narrows para non-null via `isAssignable`.

## Reference Types

### Classes
```kof
class User(String name, Int age) { }
var u = User("Mel", 30)
```

### Records
```kof
record Point(Int x, Int y)
var p = Point(10, 20)
switch (p) {
    case Point(var x, var y): println(x)
}
```

### Generics + Box<T>

```kof
class Box<T>(T value) {
    get(): T { return value }
}
var b: Box<Int> = Box(42)   // T primitivo OK — substituteTypeVariable fix 25/08
var l: List<Box<Int>> = listOf(Box(1), Box(2))
var dobrados = listOf(1,2,3).map((x: Int) -> x * 2)
```

Erasure com boxing via `parameterTypes` do call-site.

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

```kof
enum Color { Red, Green, Blue }
```

- Runtime representation: the constant name itself (String-backed).
- `==` compares by content; constant name printed directly.
- `Color.values() -> List<String>`; `Color.valueOf("Red") -> Color?`;
  `c.name() -> String`.
- Unknown constant → compile error SEM030.
- Exhaustive switch required (all constants or default) → SEM031.
- Mapped to `java/lang/String` in JVM descriptors on all targets.

### Nullable

```kof
String? s = null
Int? n = 5
if (s != null) {
    println(s.length)   // OK — narrowing
}
String t = s            // erro: String? não atribuível a String sem check
```

`NullableType(inner)` em `Type.java`; `SemanticAnalyzer.isAssignable` trata `Nullable → non-null`.

## Type Inference

```kof
var x = 10          // Int
var s = "Hello"     // String
var p = Point(1, 2) // Point
var b = Box(5)      // Box<Int> inferred
let y = 10          // KofScript → var y: Int = 10 (KofScriptGlobals)
```

## Explicit Types

```kof
Int x = 10
String s = "Hello"
Point p = Point(1, 2)
String? maybe = null
Box<Int> boxed = Box<Int>(5)
```

## Type Compatibility

- Widening: `Int` → `Long` → `Float` → `Double`
- Nullable: `String` assignable to `String?`, not vice-versa without `!= null` check
- String + anything → String (concatenation)
- Comparison operators → Bool
- Logical operators → Bool
- Erasure: `List<Int>` e `List<String>` mesmo runtime, boxing via call-site
