# 17 — Programação Funcional

> **Status: planejado**
>
> Kof não é uma linguagem funcional, mas suporta programação funcional usando lambdas e streams do Java.

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

## Imutabilidade

```kf
val lista = [1, 2, 3];
// lista.add(4);  // ERRO: lista é imutável

var listaMutavel = new java.util.ArrayList([1, 2, 3]);
listaMutavel.add(4);  // funciona
```

## Próximo passo

[Concorrência →](18-concurrency.md)
