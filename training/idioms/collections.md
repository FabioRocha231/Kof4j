# Idioms — Collections

**Status:** available · **Introduced:** 0.0.4-alpha

## What it is

`List<T>` é a coleção ordenada da linguagem. Criação: `listOf(...)` ou `new List<T>()`.
Disponível em JVM (ArrayList) e Native (implementação própria) com a mesma API.

## API real (verificada no compilador)

```kof
var l = listOf(1, 2, 3, 4)
l.add(5)          // ou push / append
var x = l.get(0)  // ou l[0]
l.set(0, 9)
l.size            // ou l.length / l.count / l.size()
l.contains(3)
l.isEmpty()
var r = l.remove(1)
l.clear()
var vazio = listOf<Int>()
```

## When to use

Qualquer problema que requer uma sequência de elementos:
coleções, registros, filas simples, agrupamentos, acumuladores.

## When not to use

- Chaves/associações nome-valor: `Map` **não existe ainda** (planned). Não fingir que existe.
- Conjuntos sem duplicatas: `Set` **não existe ainda** (planned).

## BAD — estrutura manual

```kof
class Node {
    Node next
    Int value
}
class Registry {
    Node root
    Int count
}
```

## GOOD — coleção da linguagem

```kof
class Registry {
    List<LanguageEntry> entries

    constructor() {
        entries = listOf(
            LanguageEntry("Kof", "kf", "kof"),
            LanguageEntry("JSON", "json", "json")
        )
    }
}
```

## WHY

`Node`/`next`/`count` é implementação acidental. O domínio é "uma sequência de entradas".
Kof possui a abstração. Represente o domínio, não a implementação.

## Iteração

```kof
var items = listOf("a", "b", "c")
for (var item in items) {
    println(item)
}
```

`for-in` funciona sobre `List<T>` e arrays (`new Int[5]`).

## Tipos de elementos

```kof
var ids = listOf<Int>()          // lista vazia de Int
var nomes = listOf("Ana", "Mel")
var users = listOf<User>()       // lista de objetos (erasure)
```

## Anti-patterns relacionados

- Linked list manual → `training/anti-patterns/manual-data-structures.md`
- Array como substituto de coleção dinâmica → usar `List<T>`