# Kof String Reference

## Creation

```kof
var s = "Hello"           // string literal
var s = ""                // empty string
```

## Operations

### Length
```kof
var s = "Hello"
println(s.length)  // 5
```

### Character Access
```kof
var s = "Hello"
println(s.charAt(0))  // 72 (H)
```

### Substring
```kof
var s = "Hello"
println(s.substring(1, 4))  // "ell"
```

### Concatenation
```kof
var a = "Hello"
var b = " World"
println(a + b)  // "Hello World"
```

### Contains
```kof
var s = "Hello World"
println(s.contains("World"))  // true
println(s.contains("xyz"))    // false
```

### Starts With / Ends With
```kof
var s = "Hello"
println(s.startsWith("He"))  // true
println(s.endsWith("llo"))    // true
```

### Equality
```kof
var a = "Hello"
var b = "Hello"
println(a == b)  // true (byte-level comparison)
```

## Immutability

Strings are immutable. Operations like `concat` create new strings.

## UTF-8

Strings use UTF-8 encoding. Length returns byte count, not character count.

```kof
println("Olá".length)  // 4 (UTF-8 bytes)
```
