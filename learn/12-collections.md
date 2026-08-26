# 12 — Collections

> **Status: implementado (JVM / Native / JS)**
>
> `List<T>`, `Map<K,V>` e `Set<T>` são coleções nativas de Kof. O tipo dos
> elementos é preservado pela pipeline inteira (inferência, for-in, `get`,
> resolução de métodos). No Native, Map e Set rodam em assembly próprio
> sobre o mesmo layout de alocação do List.

## List — a forma idiomática

```kf
var tokens = listOf(
    Token("identifier", "hello"),
    Token("string", "world")
)

for (var token in tokens) {
    println(token.kind())     // o tipo do elemento nunca degrada para Object
}

var nomes = listOf<String>()            // lista vazia tipada
nomes.add("Ana")
nomes.add("Bob")
println(nomes.get(0))
```

A inferência é mantida em toda a pipeline:

```kf
var users: List<User> = listOf()        // anotação explícita
users.add(User("Mel", 26))
println(users.get(0).name)
```

`json.decode<List<User>>(...)` também preserva o tipo dos elementos —
cada elemento é vinculado ao record, em JVM e KofJS.

## Operações de List

```kf
var l = listOf(1, 2, 3, 4)

l.size()          // 4
l.get(0)          // 1
l.contains(3)     // true
l.isEmpty()       // false
l.remove(0)       // remove por índice, devolve o elemento
l.set(0, 9)       // substitui in-place
l.clear()         // esvazia
```

## Map — pares chave/valor

`Map<K,V>` guarda pares com chave única. A API espelha a intenção, não o
mecanismo — sem `HashMap` exposto na superfície:

```kf
var idades = mapOf()
idades.put("Ana", 26)
idades.put("Bob", 31)

idades.get("Ana")         // 26
idades.containsKey("Bob") // true
idades.size()             // 2

idades.put("Ana", 27)     // sobrescreve; devolve o valor anterior
idades.remove("Bob")      // devolve o valor removido

idades.keys()             // List<String> das chaves
idades.values()           // List<Int> dos valores
idades.clear()
idades.isEmpty()
```

O tipo do valor é pinado no primeiro `put` — depois disso `get`, `remove`
e comparações têm tipagem concreta:

```kf
var estoque = mapOf()
estoque.put("parafuso", 500)
assert(estoque.get("parafuso") == 500)   // comparação numérica direta
```

## Set — valores únicos

`Set<T>` rejeita duplicatas: `add` devolve `true` só quando o elemento é
novo.

```kf
var vistos = setOf(1, 2, 2, 3)
vistos.size()          // 3 — o segundo 2 foi ignorado

vistos.contains(2)     // true
vistos.add(2)          // false (já existe)
vistos.remove(1)       // true
vistos.clear()
vistos.isEmpty()       // true
```

Strings funcionam igual:

```kf
var tags = setOf("kof", "lang")
tags.add("kof")        // false
println(tags.size())   // 1
```

## Paridade entre targets

| Operação | JVM | Native | JS |
|----------|-----|--------|----|
| List completa | ✅ | ✅ asm | ✅ |
| Map (todas as operações) | ✅ HashMap | ✅ asm próprio | ✅ JS Map |
| Set (todas as operações) | ✅ HashSet | ✅ asm sobre List | ✅ JS Set |

Igualdade em Map/Set usa `equals` no JVM, comparação nativa (com tag de
tipo para strings) no Native e `===`/`Map`/`Set` no JS.

## Próximo passo

**[13 — Nullability](13-nullability.md)**
