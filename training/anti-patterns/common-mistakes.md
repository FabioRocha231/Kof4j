# Kof Common Mistakes

## 1. Using Java-style getters/setters

```kof
// WRONG
class User {
    private String name
    public getName(): String { return name }
}

// RIGHT
class User {
    String name  // accessible directly
}
```

## 2. Manual memory management

```kof
// WRONG — Kof manages memory automatically
var ptr = alloc(100)
free(ptr)

// RIGHT
var data = new Int[100]
// memory reclaimed automatically
```

## 3. Backend-specific code

```kof
// WRONG — breaks multi-target
if (target == "native") {
    nativeCode()
}

// RIGHT — same code for all targets
var result = compute()
println(result)
```

## 4. Over-engineering

```kof
// WRONG
class ServiceFactory {
    create(): Service {
        return new Service()
    }
}

// RIGHT
var service = new Service()
```

## 5. Ignoring error handling

```kof
// WRONG — unsafe
var data = riskyOperation()

// RIGHT — safe
try {
    var data = riskyOperation()
} catch (String e) {
    println("Error: " + e)
}
```

## 6. Using Object as universal type

```kof
// WRONG
var x: Object = "hello"

// RIGHT
var x: String = "hello"
```

## 7. Unnecessary annotations

Annotations existem no Kof para **interoperação** (frameworks JVM, Android). Para recursos da própria plataforma, use as APIs idiomáticas — annotation+container é vazar mecanismo na intenção.

```kof
// WRONG — HTTP routing é intenção da linguagem, não annotation
@RestController
class UserController {
    // ...
}

// RIGHT
main() {
    var app = web.app()
    app.get("/users") { ... }
}

// RIGHT — annotation como metadado de interop (o framework externo exige)
@Service
class UserService {
    // ...
}
```

## 8. Manual string building

```kof
// WRONG
var result = ""
for (var i = 0; i < items.length; i++) {
    result = result + items[i] + ", "
}

// RIGHT — use concatenation
var result = "Items: " + items.length
```
