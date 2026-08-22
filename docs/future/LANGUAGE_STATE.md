# Estado Atual da Linguagem Kof

**Data:** 22 de agosto de 2026
**Testes:** 204/204 passando (incluindo execução real de binários nativos)
**Status:** Compilador funcional com backends JVM e Native, web server funcional

---

## Sintaxe

### Estrutura básica

```kof
package com.example

import java.util.List

class Animal {
    String name
    public constructor(String name) {
        this.name = name
    }
    public fun speak(): String {
        return name
    }
}

fun main() {
    var a = new Animal("Rex")
    println(a.speak())
}
```

### O que a linguagem suporta atualmente

| Constructo | Sintaxe | Exemplo |
|-----------|---------|---------|
| Package | `package a.b.c` | `package com.example` |
| Import | `import a.b.c` | `import java.util.List` |
| Função | `fun name(args): RetType` | `fun add(Int a, Int b): Int` |
| Classe | `class Name extends Super implements Iface` | `class Dog extends Animal` |
| Record | `record Name(Type field, ...)` | `record Point(Int x, Int y)` |
| Interface | `interface Name extends Iface` | `interface Speaker` |
| Constructor | `constructor(args)` | `constructor(String name)` |
| Campo | `Type name = value` | `String name = "default"` |
| Método | `fun name(args): RetType` | `fun speak(): String` |
| Variável | `var name = value` ou `Type name = value` | `var x = 10` |
| Se | `if (cond) { } else { }` | `if (x > 0) { ... }` |
| Enquanto | `while (cond) { }` | `while (i < 10) { ... }` |
| Do-while | `do { } while (cond)` | `do { ... } while (i < 10)` |
| Para | `for (init; cond; update) { }` | `for (var i = 0; i < 10; i++) { ... }` |
| Try/catch | `try { } catch (Type e) { }` | `try { ... } catch (String e) { ... }` |
| Finally | `finally { }` | `finally { ... }` |
| Throw | `throw expr` | `throw "error"` |
| Return | `return expr` | `return x + 1` |
| New | `new Type(args)` ou `new Type[size]` | `new Dog("Rex")`, `new Int[10]` |
| Array access | `arr[index]` | `a[0]` |
| Array length | `arr.length` | `a.length` |
| String length | `str.length` | `s.length` |
| String concat | `str1 + str2` | `"Hello" + " World"` |
| Herança | `class Sub extends Super` | `class Dog extends Animal` |
| Implementação | `class Name implements Iface` | `class Dog implements Speaker` |
| Super | `super(args)` | `super(name)` |
| Override | implícito (mesmo nome) | `fun speak()` sobrescreve |

### Modificadores suportados

`public`, `private`, `protected`, `static`, `final`, `abstract`, `override`

### Tipos primitivos

`bool`, `byte`, `short`, `int`, `long`, `float`, `double`, `char`, `string`, `void`

### Literais

- Inteiro: `42`, `0xFF`
- Long: `42l`
- Float: `3.14f`
- Double: `3.14`
- String: `"texto"`
- Char: `'c'`
- Boolean: `true`, `false`
- Null: `null`

### Operadores

Aritméticos: `+`, `-`, `*`, `/`, `%`
Comparação: `==`, `!=`, `<`, `>`, `<=`, `>=`
Lógicos: `&&`, `||`, `!`
Atribuição: `=`, `+=`, `-=`, `*=`, `/=`
Bitwise: `&`, `|`, `^`, `~`, `<<`, `>>`, `>>>`

---

## Tipos

### Tipos primitivos

| Tipo | Tamanho | Descrição |
|------|---------|-----------|
| `bool` | 4 bytes | Booleano |
| `byte` | 1 byte | Byte sinalizado |
| `short` | 2 bytes | Short sinalizado |
| `int` | 4 bytes | Inteiro sinalizado |
| `long` | 8 bytes | Long sinalizado |
| `float` | 4 bytes | Ponto flutuante IEEE 754 |
| `double` | 8 bytes | Ponto flutuante IEEE 754 |
| `char` | 4 bytes | Codepoint UTF-32 |
| `string` | referência | String Kof (UTF-8) |
| `void` | — | Sem retorno |

### Tipos de referência

| Tipo | Descrição |
|------|-----------|
| `ClassType` | Classe ou record |
| `ArrayType` | Array de tipo |
| `InterfaceType` | Interface |

### Tipos compound

- **Records**: `record Point(Int x, Int y)` — imutáveis, campos definidos pelo usuário
- **Classes**: `class User { ... }` — mutáveis, campos + métodos
- **Interfaces**: `interface Speaker { ... }` — contratos

---

## Orientação a Objetos

### Classes

```kof
class User {
    String name
    Int age
    public constructor(String name, Int age) {
        this.name = name
        this.age = age
    }
    public fun getName(): String {
        return name
    }
}
```

### Herança

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

### Interfaces

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

### Virtual Dispatch

- Métodos são resolvidos pelo tipo real do objeto em runtime
- `Animal a = new Dog()` → `a.speak()` chama `Dog.speak()`
- Implementado via vtable no Native backend
- JVM usa `INVOKEVIRTUAL` nativo

### Records

```kof
record Point(Int x, Int y)
// Gera: classe, construtor, accessors x(), y(), toString()
```

---

## Runtime

### JVM

- Delega para facilities da JVM
- GC: usa GC da JVM
- Memória: gerenciada pela JVM
- Strings: `java.lang.String`
- Arrays: arrays nativos da JVM

### Native

- Assembly x86-64 System V AMD64 ABI
- Sem dependência de libc
- Alocação via mmap
- Sem GC (memória reclaim pelo SO no exit)
- Strings: KofString (header + UTF-8)
- Arrays: KofArray (header + elementos)
- Objetos, herança, virtual dispatch e instanceof com hierarquia:
  execução real validada por testes E2E (compile → assemble → link → run)
- String methods nativos: length, charAt, substring, contains, startsWith,
  endsWith, concat
- valueOf (int/char/bool → KofString) implementado no runtime

### Object Model

```
Header (16 bytes):
  offset 0:  type_id (4 bytes)
  offset 4:  flags (4 bytes)
  offset 8:  method_table_ptr (8 bytes)

Fields:
  offset 16: field_0
  offset 24: field_1
  ...
```

---

## Backends

| Feature | JVM | Native |
|---------|-----|--------|
| Target | .class / .jar | ELF x86-64 |
| Runtime | JVM | Assembly embutido |
| GC | JVM GC | Nenhum (reclaim pelo SO) |
| Strings | java.lang.String | KofString |
| Arrays | arrays nativos | KofArray |
| Virtual dispatch | INVOKEVIRTUAL | vtable |
| Interfaces | INVOKEINTERFACE | vtable |
| Exceptions | Exceções JVM | kof_panic (fatal) |
| print/println | System.out | Syscalls Linux |

---

## Segurança de Tipos

- Tipagem estática e forte
- Verificação em compile-time
- Coerção implícita limitada (widening primitivo)
- String + anything → String (concatenação)
- Operações inválidas rejeitadas pelo compilador

---

## Erros

### Compile-time

- Variável inexistente
- Método inexistente
- Tipo incompatível
- Argumento incompatível
- Quantidade errada de argumentos

### Runtime

- Null pointer → `kof_null_error` (fatal)
- Array bounds → `kof_bounds_error` (fatal)
- Allocation failure → retorna null
- Panic → `kof_panic` (fatal)

---

## Performance

### Gargalos arquiteturais conhecidos

1. **kof_alloc** usa mmap (lento para alocações pequenas)
2. **kof_string_concat** copia byte a byte
3. **kof_memcpy** copia byte a byte
4. **kof_print_int** usa divisão em loop
5. **Sem GC** — memória nunca é liberada durante execução
6. **Sem otimizações** — constant folding, dead code elimination

---

## O que NÃO existe

- Generics
- Collections (List, Map, Set)
- Async/await
- Concorrência
- Reflection
- Annotations
- Macros
- Módulos/pacotes reais
- Standard library completa
- HTTP (em desenvolvimento — `kof serve`)
- Database
- Serialization
- JSON
- Testes
- REPL
- Language server
