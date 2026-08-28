# Idioms — Collections

**Status:** available · **Introduced:** 0.0.4-alpha · **Updated:** 0.2.0-beta (27 Aug 2026)

## What it is

`List<T>` é a coleção ordenada da linguagem. Criação: `listOf(...)` ou `new List<T>()`.
Disponível em JVM (ArrayList), Native (implementação própria com free-list GC) e JS (Array) com a mesma API.
`Map<K,V>` e `Set<T>` existem desde 0.1.0 nos 3 targets (JVM HashMap/HashSet, Native asm próprio, JS Map/Set).

## API real (verificada no compilador — 0.2.0-beta)

```kof
var l = listOf(1, 2, 3, 4)
l.add(5)
var x = l.get(0)        // bounds check nativo via kof_list_get — sem workaround manual
l.set(0, 9)
l.size                  // propriedade, não método
l.contains(3)
l.isEmpty()
var r = l.remove(1)
l.clear()
var vazio = listOf<Int>()

// Higher-order (0.2.0-beta, 3 targets)
var dobrados = l.map((x: Int) -> x * 2)
var pares = l.filter((x: Int) -> x % 2 == 0)
var soma = l.reduce((a: Int, b: Int) -> a + b, 0)

// Map / Set
var m = mapOf("a", 1)
m.put("b", 2)
var v = m.get("a")
var s = setOf(1, 2, 3)
s.add(4)
s.contains(2)
```

Fix 27/08: `listOf(...).get(n)` e `size` em projetos grandes com `import a.b.C` agora resolvem corretamente (CompilerDriver file-specific imports). Não é necessário workaround manual de índice.

## When to use

Qualquer problema que requer uma sequência de elementos:
coleções, registros, filas simples, agrupamentos, acumuladores.
`Map`/`Set` para associações e conjuntos. `map`/`filter`/`reduce` para transformação sem loop manual.

## When not to use

- Não reimplementar `map`/`filter`/`reduce` com loop quando a higher-order expressa a intenção.
- Não usar `List<record>` com busca linear quando `Map<K,V>` resolve (quando há chave).

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

## GOOD — transformação declarativa (0.2.0-beta)

```kof
var nomes = users.map((u: User) -> u.name)
var adultos = users.filter((u: User) -> u.age >= 18)
var total = nums.reduce((a: Int, b: Int) -> a + b, 0)
```

## GOOD — Box<T> com primitivos (0.1.0 fix)

```kof
class Box<T>(T value) {
    get(): T { return value }
}
var b = Box<Int>(42)
println(b.get())   // 42 — substituteTypeVariable corrige T → Int no Native
```

## WHY

`Node`/`next`/`count` é implementação acidental. O domínio é "uma sequência de entradas".
Kof possui a abstração. Represente o domínio, não a implementação.
Higher-orders e `Box<T>` eliminam loops e wrappers manuais.

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
var boxed: Box<Int> = Box(5)
```

## Anti-patterns relacionados

- Linked list manual → `training/anti-patterns/manual-data-structures.md`
- Array como substituto de coleção dinâmica → usar `List<T>`
- Loop manual para map/filter → usar higher-order
