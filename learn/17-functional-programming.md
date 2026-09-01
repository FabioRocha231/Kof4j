# 17 — Programação Funcional

> **Status: implementado — `map/filter/reduce` em `List<T>` (0.2.6-beta) — JVM/Native/JS**
>
> Kof não é uma linguagem funcional, mas `List<T>` já oferece `map/filter/reduce` idiomáticos (ver cap. 12); streams Java continuam interoperáveis.

## Streams

```kf
import java.util.stream.Collectors;

var nomes = ["Ana", "Bob", "Carlos", "Diana"];

var resultado = nomes.stream()
    .filter(nome -> nome.length() > 3)
    .map(nome -> nome.toUpperCase())
    .sorted()
    .collect(Collectors.toList());
// ["CARLOS", "DIANA"]
```

## Pipelines (planejado)

Kof pode adicionar uma sintaxe de pipeline:

```kf
var resultado = nomes
    |> filter(nome -> nome.length() > 3)
    |> map(nome -> nome.toUpperCase())
    |> sorted();
```

Isso é equivalente ao stream acima, mas mais legível.

## Funções puras

Uma função pura não tem efeitos colaterais:

```kf
Int dobro(Int x) = x * 2;
```

## Imutabilidade + `map/filter/reduce` idiomáticos (0.2.0)

```kf
// Kof idiomático — sem Stream Java:
var nums = listOf(1, 2, 3, 4, 5)
var dobrados = nums.map((x: Int) -> x * 2)          // [2,4,6,8,10]
var pares = nums.filter((x: Int) -> x % 2 == 0)     // [2,4]
var soma = nums.reduce(0, (acc: Int, x: Int) -> acc + x) // 15
println(soma) // 15
```

## Imutabilidade

```kf
val lista = listOf(1, 2, 3)
// lista é mutável via métodos — imutabilidade é por convenção/val
var listaMutavel = listOf(1, 2, 3)
listaMutavel.add(4)  // funciona
```

## Próximo passo

[Concorrência →](18-concurrency.md)
