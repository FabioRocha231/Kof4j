# Java to Kof Migration

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
    
    public String getName() { return name; }
    public int getAge() { return age; }
}
```

### Kof
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

## Records

### Java
```java
public record Point(int x, int y) {}
```

### Kof
```kof
record Point(Int x, Int y)
```

## Inheritance

### Java
```java
public class Animal {
    protected String name;
    public Animal(String name) { this.name = name; }
}
public class Dog extends Animal {
    public Dog(String name) { super(name); }
}
```

### Kof
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

### Java
```java
public interface Speaker {
    String speak();
}
public class Dog implements Speaker {
    public String speak() { return "woof"; }
}
```

### Kof
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

## Collections

### Java
```java
List<String> list = new ArrayList<>();
list.add("hello");
Map<String, Integer> map = new HashMap<>();
```

### Kof
```kof
var list = new String[0]  // arrays (collections coming soon)
```

## HTTP

### Java (Spring)
```java
@RestController
public class UserController {
    @GetMapping("/users/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.findById(id);
    }
}
```

### Kof
```kof
// Web server via kof serve
// Handler pattern (evolving)
```
