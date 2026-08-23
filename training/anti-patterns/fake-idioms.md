# Anti-pattern — Fake Idioms

## Name

Ensinar ou usar como idiomático algo que não existe na linguagem.

## Problem

O modelo pode inventar `users.map(...)`, `Option<T>`, `async/await`,
`for user in users` (sem `var`), primary constructors, pattern matching —
porque existem em outras linguagens. Código assim **não compila** ou
**compila por acidente** com semântica errada.

## Status real (verificar sempre no compilador)

| Feature | Status |
|---|---|
| `List<T>` (add/get/set/size/contains/isEmpty/remove/clear/listOf) | Implemented |
| `for (var x in coll)` | Implemented |
| Lambdas `(x: Int) -> expr` (sem capturas) | Implemented |
| If-expr `if (c) a else b` | Implemented |
| `json.encode` / `json.decode<T>` | Implemented |
| `throw "msg"` / `try/catch/finally` | Implemented |
| `Map` / `Set` | Planned |
| `Option<T>` / null safety | Planned |
| `async` / `await` / resultado de tarefa | Planned |
| `spawn` (tarefas concorrentes) | Implemented (JVM; Native CONC001) |
| `Thread` / `Executor` (APIs de plataforma) | Unavailable — nunca use |
| Pattern matching | Planned |
| `users.map(...)` / funções higher-order | Planned |
| Primary constructor `class X(...)` | Unavailable |
| Array literals `{1, 2, 3}` | Unavailable |
| `instanceof` com binding | Unavailable |
| `for user in users` (sem var) | Unavailable |

## Bad example

```kof
// NÃO COMPILA — map não existe
var nomes = users.map(u -> u.name)

// NÃO COMPILA — array literal não existe
var nums = [1, 2, 3]

// NÃO COMPILA — primary constructor não existe
class User(String name)

// NÃO COMPILA — Option não existe
var maybe = Option.of(x)
```

## Good example (o que existe)

```kof
var nomes = listOf()
for (var u in users) {
    nomes.add(u.name)
}

var nums = new Int[3]
nums[0] = 1

class User {
    String name
    public constructor(String name) {
        this.name = name
    }
}

try {
    // tratar ausência como erro
} catch (String e) {
    // WORKAROUND até Option<T> existir
}
```

## Why it is bad

Um modelo que "aprende" features inexistentes produz código que o compilador
rejeita — ou pior, código que compila com outra semântica. O corpus deve
ensinar a fronteira exata do que existe.

## Regra

Antes de usar uma feature, verifique a tabela de status.
Quando a feature não existe: use a alternativa real OU marque `WORKAROUND`.

## Exceptions

- Nenhuma — fake idioms nunca são aceitáveis no corpus.