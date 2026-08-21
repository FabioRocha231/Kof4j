# 12 — Collections

> **Status: planejado**
>
> Kof não reimplementa collections. Kof usa as collections do Java diretamente.

## Collections do Java

Kof é compatível com `java.util`:

```kf
import java.util.List;
import java.util.Map;
import java.util.Set;
```

## List

```kf
var nomes = new java.util.ArrayList<String>();
nomes.add("Ana");
nomes.add("Bob");
nomes.add("Carlos");

String primeiro = nomes.get(0);  // "Ana"
Int tamanho = nomes.size();       // 3
```

## Map

```kf
var notas = new java.util.HashMap<String, Double>();
notas.put("Prova 1", 8.5);
notas.put("Prova 2", 9.0);

Double nota = notas.get("Prova 1");  // 8.5
```

## Set

```kf
var ids = new java.util.HashSet<String>();
ids.add("001");
ids.add("002");
ids.add("001");  // duplicata ignorada

Int tamanho = ids.size();  // 2
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
