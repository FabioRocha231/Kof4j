# Idioms — Architecture

**Status:** available · **Introduced:** 0.0.4-alpha · **Updated:** 0.2.0-beta

## What it is

A filosofia da linguagem aplicada a decisões de arquitetura.

## Princípio central

> Represente o domínio, não a implementação acidental.

## 1. Complexidade pertence à plataforma

Se a complexidade pode ser absorvida pelo compilador, runtime ou stdlib,
ela deve desaparecer do código do usuário.

## BAD — infraestrutura manual

```kof
class JsonParser {
    // parser JSON manual
}
class Db {
    // conexão manual
}
class Http {
    // servidor manual
}
```

## GOOD — plataforma (0.2.0-beta)

```kof
var j = json.encode(user)
var dados = readFile("config.json")
var html = http.get("https://example.com")   // kof.http JVM+JS (Java HttpClient)
let x = 5                                    // KofScript top-level let → KofScriptGlobals
// kof serve: handle(method, path, body) + web.app() + ws/sse + cache
// kof c: C subset nativo-only para hot paths
```

## WHY

JSON, arquivos e HTTP já existem na plataforma. Reimplementá-los à mão
adiciona complexidade que o programador teria que manter.

## 2. Camadas sem necessidade

## BAD — camadas de cerimônia

```kof
class UserController {
    UserService service
    constructor() {
        service = new UserService()
    }
    listar(): String {
        return service.listar()
    }
}
class UserService {
    UserRepository repo
    constructor() {
        repo = new UserRepository()
    }
    listar(): String {
        return repo.listar()
    }
}
class UserRepository {
    listar(): String {
        return "users"
    }
}
```

## GOOD — o que o problema exige

```kof
String listarUsers() {
    return "users"
}
```

## WHY

Controller/Service/Repository existe em Java por convenções de framework
(Spring, injeção, transações). Kof não possui essas convenções. Adicione uma
camada somente quando ela resolve um problema real.

## 3. Dados → record, comportamento → classe, lógica → função

```kof
record Product(String id, String name, Double price)

class Cart {
    List<Product> items
    constructor() {
        items = listOf()
    }
    add(Product p) {
        items.add(p)
    }
}

Double total(Cart cart) {
    var sum = 0.0
    for (var item in cart.items) {
        sum = sum + item.price
    }
    return sum
}
```

## 4. Módulos (0.2.0-beta)

`package`/`import` existem. `import a.b.C` file-specific fixado 27/08 — projetos grandes com `a/b/C.kf` agora compilam corretamente (CompilerDriver). `import a.b.*` para diretório. Targets: `jvm`, `native`, `native.risc`/`native.arm` (placeholder), `js`, `kofc`, `KofScript` (`.ks` com `let`).

Para programas pequenos, um único arquivo `.kf` é suficiente — o `main()` no topo.

## When not to use

- Não crie "manager", "helper", "context", "handler" genéricos sem responsabilidade clara.
- Não espelhe a estrutura de um framework Java (beans, autowired, config).
- Não planeje microsserviços antes de ter um problema.

## Anti-patterns relacionados

- `unnecessary-abstraction.md`
- `java-like-code.md`