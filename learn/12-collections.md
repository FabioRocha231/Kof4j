# 12 — Collections

> **Status: implementado**
>
> `List<T>` é a coleção nativa de Kof; o tipo do elemento é preservado pela
> pipeline inteira (inferência, for-in, `get`, resolução de métodos).

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
println(l.size)
println(l.contains(3))
println(l.isEmpty())
var removido = l.remove(1)
l.set(0, 100)
l.clear()
```

## Iterando

```kf
for (String nome : nomes) {
    print(nome);
}
```

## Collections imutáveis (planejado)

```kf
var lista = [1, 2, 3];           // List.of(1, 2, 3)
var mapa = {"a": 1, "b": 2};    // Map.of("a", 1, "b", 2)
var conjunto = #{1, 2, 3};      // Set.of(1, 2, 3)
```

## Próximo passo

[Nullability →](13-nullability.md)
