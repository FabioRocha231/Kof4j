# Kof vs Java — Comparação Técnica

**Última atualização:** 21 de agosto de 2026

---

## Visão Geral

| Aspecto | Java | Kof |
|---------|------|-----|
| Tipagem | Forte, estática | Forte, estática |
| OO | Classes, interfaces, records | Classes, interfaces, records |
| Herança | Simples + interfaces | Simples + interfaces |
| GC | Automático | JVM: automático / Native: pendente |
| Compilação | javac → bytecode | Kof → IR → JVM/Native |
| Sintaxe | Verbosa | Concisa |

---

## Classes

### Java

```java
public class User {
    private String name;
    private int age;

    public User(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }
}
```

### Kof (PROPOSTA)

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

**Diferença:** Kof não precisa de getters/setters. Fields são acessíveis diretamente.

---

## Records

### Java

```java
public record Point(int x, int y) {}
```

### Kof

```kof
record Point(Int x, Int y)
```

**Diferença:** Praticamente idênticos. Kof é ligeiramente mais conciso.

---

## Herança

### Java

```java
public class Animal {
    protected String name;
    public Animal(String name) { this.name = name; }
    public String speak() { return name; }
}

public class Dog extends Animal {
    public Dog(String name) { super(name); }
    public String speak() { return "woof"; }
}
```

### Kof

```kof
class Animal {
    String name
    public constructor(String name) {
        this.name = name
    }
    public speak(): String {
        return name
    }
}
class Dog extends Animal {
    public constructor(String name) {
        super(name)
    }
    public speak(): String {
        return "woof"
    }
}
```

**Diferença:** Kof é mais conciso. Sem `private`/`protected` em campos (acesso direto).

---

## Null Safety

### Java

```java
// Null pointer exception em runtime
String s = null;
s.length(); // NPE
```

### Kof (PROPOSTA)

Kof atualmente NÃO tem null safety. Nulls são permitidos.

**Proposta futura:** Tipos não-nulos por padrão, `?` para nullable.

---

## Generics

### Java

```java
List<String> list = new ArrayList<>();
list.add("hello");
String s = list.get(0);
```

### Kof

Não implementado.

---

## Collections

### Java

```java
List<String> list = new ArrayList<>();
Map<String, Integer> map = new HashMap<>();
Set<String> set = new HashSet<>();
```

### Kof

Não implementado.

---

## Exceptions

### Java

```java
try {
    throw new RuntimeException("error");
} catch (RuntimeException e) {
    System.out.println(e.getMessage());
} finally {
    // cleanup
}
```

### Kof

```kof
try {
    throw "error"
} catch (String e) {
    println(e)
} finally {
    // cleanup
}
```

**Diferença:** Kof atualmente trata erros como fatais (kof_panic). Try/catch é parseado mas não funciona completamente no Native.

---

## Concorrência

### Java

```java
ExecutorService executor = Executors.newFixedThreadPool(4);
Future<String> future = executor.submit(() -> "result");
```

### Kof

Não implementado.

---

## Dependency Injection

### Java (Spring)

```java
@Service
public class UserService {
    @Autowired
    private UserRepository repository;
}
```

### Kof (PROPOSTA)

```kof
service UserService {
    inject UserRepository repository
}
```

**Status:** Proposta apenas.

---

## HTTP

### Java (Spring Boot)

```java
@RestController
public class UserController {
    @GetMapping("/users/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.findById(id);
    }
}
```

### Kof (PROPOSTA)

```kof
route GET "/users/{id}" {
    return users.find(id)
}
```

**Status:** Proposta apenas.

---

## Configuração

### Java (Spring Boot)

```properties
# application.properties
server.port=8080
spring.datasource.url=jdbc:mysql://localhost/mydb
```

### Kof (PROPOSTA)

```kof
config {
    port = 8080
    database.url = "jdbc:mysql://localhost/mydb"
}
```

**Status:** Proposta apenas.

---

## Resumo

| Feature | Java | Kof Atual | Kof Futuro |
|---------|------|-----------|------------|
| Classes | ✅ | ✅ | ✅ |
| Records | ✅ | ✅ | ✅ |
| Herança | ✅ | ✅ | ✅ |
| Interfaces | ✅ | ✅ | ✅ |
| Virtual dispatch | ✅ | ✅ | ✅ |
| Null safety | ✅ | ❌ | Proposta |
| Generics | ✅ | ❌ | Fase O |
| Collections | ✅ | ❌ | Standard Library |
| Exceptions | ✅ | Parcial | Fase F.6 |
| Concorrência | ✅ | ❌ | Futuro |
| DI | Framework | ❌ | Proposta |
| HTTP | Framework | ❌ | Proposta |
| Config | Framework | ❌ | Proposta |
| Database | Framework | ❌ | Proposta |
