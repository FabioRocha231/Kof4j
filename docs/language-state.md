# Estado Atual da Linguagem Kof

**Data:** 27 de agosto de 2026
**Versão:** 0.2.6-beta
**Testes:** 658 JUnit (650 kof-compiler +8 kof-script +5 kof-c-compiler, 0 falhas) +1 skip condicional; `NativeE2ETest` 50/50, `JvmE2ETest` 29/29, `KofJsE2ETest` 35/35, `KofCCompilerTest` 5/5, `KofHttpE2ETest` 4/4; inclui JSON, exceptions, web, db/orm, UI, security G9, generics `Box<T>` fix, pattern matching e null safety básica
**Status:** Compilador funcional com backends JVM, Native (x86_64 + riscv64 + aarch64 placeholder), KofJS (alpha, GraalJS), KofScript e KofC; web server, distribuição e tooling oficiais (0.2.6-beta, 27/08)

---

## Novidades 0.1.0 → 0.2.6-beta (27/08)

### 0.2.6-beta — linguagem e plataforma

- **Pattern matching** `switch (x) { case String s: ... }` + record destructuring `Point(x,y)` em JVM/Native/JS (`Parser.java:1`, `SemanticAnalyzer.java:1`, `CompilerDriver.java:1`)
- **Null safety** `String?` básica (`Type?` nullable, `?`-check em compile-time)
- **List `map/filter/reduce`** + `Box<T>` generics estáveis (erasure, `substituteTypeVariable` `CompilerDriver.java:3972`)
- **KofScript** top-level `let` → `KofScriptGlobals` (REPL, `--watch`, Windows SIGPIPE fix)
- **KofCcompiler** (`kof c`) C subset native-only: `while`/`if`/deref `&`/`*(int*)` → ELF x86_64 via `kof_c`
- **Native** free-list (`kof_free_head`) + `kof_gc_collect`; MySQL handshake `kof_db_mysql_scramble`; target separation `native.riscv64`/`native.aarch64` (`Target.java:1`, `NativeBackend.java:1`, riscv64 via `riscv64-linux-gnu-as`, `.option arch,rv64g`, `li a7 214/64/93`)
- **kof.http** JVM+JS (JS via `Java HttpClient` interop no `KofJsRunner`)
- **Bugs**: large-project `import a.b.C` file handling (`CompilerDriver.java:243` `import a.b.C` + `a.b` dir, `largeproj` `a/b/C.kf` OK), `List.get`/`listOf`, `release.yml` single job + JDK 21, `kof_free_head` reuso

### 0.1.0 final (P1 — linguagem, 25/08)

- **Enums** com switch exaustivo (`SEM031`), `values/valueOf/name`,
  comparação por conteúdo e mapeamento String nos descritores
- **Map<K,V> / Set<T>** completos nos 3 targets (Native em asm próprio)
- **spawn/await** com handle tipado `Handle<T>` e unboxing de primitivos;
  gaps CONC001/CONC003/AND001 explícitos; lambda não-void de expressão
  única vira return (fix de VerifyError)

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

## Backends (0.2.6-beta)

| Feature | JVM | Native x86_64 | Native riscv64 | Native aarch64 | JS (GraalJS) | KofC | KofScript |
|---------|-----|---------------|----------------|----------------|--------------|------|-----------|
| Target | .class / .jar | ELF x86_64 | ELF riscv64 | ELF aarch64 (placeholder) | ES Modules (.mjs) | ELF x86_64 (C subset) | JVM via KofScriptGlobals |
| Runtime | JVM (virtual threads) | Assembly x86-64 (free-list + kof_gc_collect) | Assembly riscv64 (`.option arch,rv64g`, `li a7 214/64/93`) | placeholder | GraalJS embedded + `Java HttpClient` interop | Native only (`kof_c`) | JVM temp dir |
| GC | JVM GC | free-list `kof_free_head` + `kof_gc_collect` (mark-sweep pending) | same | placeholder | GC JS | none | JVM GC |
| Strings | java.lang.String | KofString | KofString riscv64 | — | JS string | C char* | KofString |
| Arrays | arrays nativos | KofArray | KofArray riscv64 | — | JS Array | C array | arrays nativos |
| Virtual dispatch | INVOKEVIRTUAL | vtable | vtable riscv64 | — | prototype | — | INVOKEVIRTUAL |
| Interfaces | INVOKEINTERFACE | vtable | vtable riscv64 | — | — | — | INVOKEINTERFACE |
| Exceptions | Exceções JVM | unwinding próprio | unwinding riscv64 | — | JS throw | — | Exceções JVM |
| print/println | System.out | Syscalls Linux (`write` 1) | Syscalls riscv64 (`li a7 64`) | — | `kof_platform` | `write` | System.out |
| Pattern matching | ✅ `case String s` + `Point(x,y)` | ✅ | ✅ | placeholder | ✅ (`typeof`) | — | ✅ |

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

## O que NÃO existe (residual 0.2.6-beta)

- Reflection, Macros; annotations de enum/Classe em valores (`ANNOT001`) — planned
- Formatter (`kof fmt` planned, P5)
- Database nível 3 (query DSL tipada `User.query { where ... }`) — `kof.db` nível 0 e `kof.orm` nível 2/4 já DONE; MySQL handshake `kof_db_mysql_scramble` feito
- Native aarch64 codegen completo (placeholder, target separation done)
- `spawn` no Native (CONC001) — gap documentado
- GC mark-sweep completo (free-list + `kof_gc_collect` done, sweep pending)

## O que existe desde 0.0.5 → 0.2.6-beta

- Generics (erasure) — 25/08 `Box<T>` `T` primitivo fixo (`Box<Int>` + `println` nativo `kof_int_to_string` `CompilerDriver.java:2257`)
- `List<T>` (JVM + Native + JS), `listOf` + `map/filter/reduce` (0.2.0), for-in
- Pattern matching `switch case String s` + record destructuring `Point(x,y)` (JVM/Native/JS, 27/08)
- Null safety `String?` básica (`Type?` nullable, 27/08)
- Lambdas `(x: Int) -> expr` + if-expr + capturas (box `BoxN`) em 3 targets
- JSON encode/decode (JVM + Native + JS; objetos/records no JVM/JS; arrays nativos `JSN003`)
- Exceptions reais (JVM table + Native unwinding)
- `assert` + `kof test` estruturado (`test "nome" {}`) + `process.exit`
- `spawn` (concorrência — JVM, virtual threads; JS sequencial)
- kof.io (File/Path/Directory, readFile/writeFile), kof.time (`now()`, `sleep`, `interval`)
- HTTP (`kof serve` — KofHttpServer com thread pool), `kof.http` client (JVM+JS via `Java HttpClient`), `kof.mq`
- `kof.validation`, `kof.observability` (health/metrics), `kof.security` (PBKDF2/SHA/JWT/AES-GCM + G9 rateLimit/session/apiKey em 3 targets), `kof.db`/`kof.orm` + `kof.config`/`kof.log` (asm Native, free-list)
- KofJS (target `js` — GraalJS embutido, `kof.http` JS), TLS `web.listenSecure` (JVM), `KofScript` (`let` → `KofScriptGlobals`), `KofCcompiler` (`kof c`)
- Native riscv64/aarch64 targets (`Target.NATIVE_RISCV64/AARCH64`)
- Language Server (`kof lsp` — frontend real do compilador, hover/completion)
- `kof check`, `kof info`, `kof install`, `kof bench`/`profile`/`inspect`/`debug`, `kof script`/`repl`/`c`
- Distribuição oficial com JDK 21 embutido, versionamento `VERSION` 0.2.6-beta e releases single-job (`release.yml`) — `scripts/package.sh` PASS
