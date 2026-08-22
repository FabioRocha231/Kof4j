# Kof Syntax Reference

## Declarations

### Package
```kof
package com.example
```

### Import
```kof
import java.util.List
import java.util.*
```

### Function
```kof
add(Int a, Int b): Int {
    return a + b
}
```

### Class
```kof
class User {
    String name
    public constructor(String name) {
        this.name = name
    }
    public getName(): String {
        return name
    }
}
```

### Record
```kof
record Point(Int x, Int y)
```

### Interface
```kof
interface Speaker {
    speak(): String
}
```

### Variable
```kof
var x = 10
Type name = value
```

## Statements

### If/Else
```kof
if (x > 0) {
    println("positive")
} else {
    println("non-positive")
}
```

### While
```kof
while (i < 10) {
    println(i)
    i = i + 1
}
```

### Do-While
```kof
do {
    println(i)
    i = i + 1
} while (i < 10)
```

### For
```kof
for (var i = 0; i < 10; i++) {
    println(i)
}
```

### Try/Catch
```kof
try {
    throw "error"
} catch (String e) {
    println(e)
} finally {
    println("done")
}
```

## Expressions

### Arithmetic
```kof
a + b
a - b
a * b
a / b
a % b
```

### Comparison
```kof
a == b
a != b
a < b
a > b
a <= b
a >= b
```

### Logical
```kof
a && b
a || b
!a
```

### String Concatenation
```kof
"Hello" + " World"  // "Hello World"
```

### Array Access
```kof
var arr = new Int[5]
arr[0] = 10
println(arr[0])
println(arr.length)
```

### Method Call
```kof
var s = "Hello"
println(s.length)
println(s.charAt(0))
println(s.substring(1, 3))
```
