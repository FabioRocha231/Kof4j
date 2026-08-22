# Kof Common Patterns

## CRUD Entity

```kof
record User(String name, String email)
```

## Service Pattern

```kof
class UserService {
    User[] users = new User[0]
    
    fun find(Int id): User {
        // implementation
    }
    
    fun create(String name, String email): User {
        // implementation
    }
}
```

## Web Handler

```kof
fun handle(String method, String path, String body): String {
    if (path == "/users") {
        return "{\"users\": []}"
    }
    return "Not found"
}
```

## Error Handling

```kof
try {
    var result = riskyOperation()
    println(result)
} catch (String e) {
    println("Error: " + e)
} finally {
    println("Cleanup")
}
```

## Array Processing

```kof
var items = new Int[5]
for (var i = 0; i < items.length; i++) {
    items[i] = i * 2
}
for (var i = 0; i < items.length; i++) {
    println(items[i])
}
```

## String Processing

```kof
var s = "Hello World"
println(s.length)           // 11
println(s.charAt(0))        // 72 (H)
println(s.substring(0, 5))  // "Hello"
println(s.contains("World")) // true
println(s.startsWith("Hello")) // true
println(s.endsWith("World"))   // true
```

## Inheritance + Interface

```kof
interface Serializable {
    fun serialize(): String
}
class Entity implements Serializable {
    String id
    public constructor(String id) {
        this.id = id
    }
    public fun serialize(): String {
        return "{\"id\": \"" + id + "\"}"
    }
}
```
