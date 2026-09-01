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

## 9. Manual List.get handling (fix 27/08 — removido)

```kof
// WRONG (workaround histórico) — bounds check manual antes de get
if (i >= 0 && i < l.size) { var x = l.get(i) }

// RIGHT (0.2.6-beta) — kof_list_get já faz bounds check com mensagem clara
var x = l.get(1)   // ou l[1]
var y = listOf(1,2,3).get(1) // 2
```

## 10. Manual import workarounds (fix 27/08 — removido)

```kof
// WRONG — copiar arquivo C.kf para pasta raiz para evitar import a.b.C falhando
// RIGHT (0.2.6-beta) — CompilerDriver expandKofImports file-specific
import a.b.C
import a.b.*
```

## 11. Ignorar null safety (0.2.6-beta)

```kof
// WRONG — sentinela para ausência
String find(String key) { return "" }

// RIGHT — String? com narrowing
String? find(String key) { if (found) return value; return null }
var r = find("x")
if (r != null) { println(r) }
```

## 12. Loop manual quando higher-order existe (0.2.6-beta)

```kof
// WRONG
var nomes = listOf()
for (var u in users) { nomes.add(u.name) }

// RIGHT
var nomes = users.map((u: User) -> u.name)
```
