# Estado Atual da Linguagem Kof

**Data:** 25 de agosto de 2026
**Versão:** 0.1.0-beta
**Testes:** 590 JUnit (581 declarados) passando (+1 skip condicional; `NativeE2ETest` 50/50, `JvmE2ETest` 29/29, `KofJsE2ETest` 35/35; inclui JSON, exceptions, web, db/orm, UI, security G9 e generics `Box<T>` fix em 25/08)
**Status:** Compilador funcional com backends JVM, Native e KofJS; web server, distribuição e tooling oficiais (0.1.0-beta; generics `Box<T>` + `SEM025` fix 25/08)

---

## Novidades 0.0.5 → 0.1.0-beta (25/08)

- **Interop Android/JVM**: `super.metodo()` com INVOKESPECIAL (owner é a
  superclasse direta; assinaturas externas resolvidas via classpath
  `.jar`/`.aar` — `CompilerDriver.setExternalClasspath`) e annotations
  `@Name`/`@Name(valor | key = valor, ...)` emitidas no bytecode
  (RuntimeVisible/Invisible) em classes, campos, métodos e parâmetros.
  `super.metodo()` no Native reporta `SUP001`.
- `entity Name { field: Type constraint }` — schema declarativo (compile-time)
  para o `kof.orm` (`generated`, `unique`, PK não-numérica).
- Namespaces da stdlib: `kof.db`, `kof.orm`, `kof.process`, `kof.ui`
  (Window/Label/Button/Input/Column/Row/View/Style), além de `kof.web`,
  `kof.io`, `kof.time`, `kof.config`, `kof.log`, `kof.security`, `kof.validation`, `kof.observability`, `kof.http`, `kof.mq`.
- Conversões `String.toInt()/toLong()/toDouble()/toFloat()` (runtime).
- ARITH001: divisão/resto por zero **constante** rejeitada em compile-time
  (apenas inteiros — float/double produzem Infinity/NaN).
- Lexer tolera UTF-8 BOM inicial.
- Lambdas com capturas em todos os targets (box `BoxN`); múltiplas janelas no kof.ui.
- **25/08:** generics `Box<T>` com `T` primitivo (`Box<Int>`) fix — `substituteTypeVariable` + `kof_int_to_string` nativo; `SEM025` sem falso-positivo em `hashCode/equals/toString`.

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
    public speak(): String {
        return name
    }
}

main() {
    var a = new Animal("Rex")
    println(a.speak())
}
```

### O que a linguagem suporta atualmente

| Constructo | Sintaxe | Exemplo |
|-----------|---------|---------|
| Package | `package a.b.c` | `package com.example` |
| Import | `import a.b.c` | `import java.util.List` |
| Função | `name(args): RetType` | `add(Int a, Int b): Int` |
| Classe | `class Name extends Super implements Iface` | `class Dog extends Animal` |
| Record | `record Name(Type field, ...)` | `record Point(Int x, Int y)` |
| Interface | `interface Name extends Iface` | `interface Speaker` |
| Constructor | `constructor(args)` | `constructor(String name)` |
| Campo | `Type name = value` | `String name = "default"` |
| Método | `name(args): RetType` | `speak(): String` |
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
| Override | implícito (mesmo nome) | `speak()` sobrescreve |

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
    public getName(): String {
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
    speak(): String
}
class Dog implements Speaker {
    public speak(): String {
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

## O que NÃO existe (planejado — P1 em progresso por outro agente)

- Map, Set (P1)
- `await`/resultado de tarefa, filas (`kof.concurrent.Queue`) (P1)
- `spawn` no Native (CONC001)
- JSON objetos/records no Native (JSN002)
- Reflection, Macros; annotations de enum/Classe em valores (`ANNOT001`)
- REPL
- Formatter (`kof fmt` planejado)
- Database nível 1 (query DSL tipada) — `kof.db` nível 0 e `kof.orm` nível 2/4 já DONE

## O que existe desde 0.0.5

- Generics (com erasure) — 25/08 `Box<T>` `T` primitivo fixo (`Box<Int>` + `println` nativo)
- `List<T>` (JVM + Native + JS), `listOf`, for-in
- Lambdas `(x: Int) -> expr` + if-expr + capturas (box `BoxN`) em 3 targets
- JSON encode/decode (JVM + Native + JS; objetos/records no JVM/JS; arrays nativos `JSN003`)
- Exceptions reais (JVM table + Native unwinding)
- `assert` + `kof test` estruturado (`test "nome" {}`) + `process.exit`
- `spawn` (concorrência — JVM, virtual threads)
- kof.io (File/Path/Directory, readFile/writeFile), kof.time (`now()`, `sleep`, `interval`)
- HTTP (`kof serve` — KofHttpServer com thread pool), `kof.http` client, `kof.mq`
- `kof.validation`, `kof.observability` (health/metrics), `kof.security` (PBKDF2/SHA/JWT/AES-GCM + G9 rateLimit/session/apiKey em 3 targets), `kof.db`/`kof.orm` + `kof.config`/`kof.log` (asm Native)
- KofJS (target `js` — GraalJS embutido), TLS `web.listenSecure` (JVM)
- Language Server (`kof lsp` — frontend real do compilador)
- `kof check`, `kof info`, `kof install`, `kof bench`/`profile`/`inspect`/`debug`
- Distribuição oficial com JDK embutido, versionamento e releases automáticas (0.1.0-beta)
