# 16 — Lambdas

> **Status: implementado (JVM + Native; sem capturas)**
>
> O parser reconhece lambda com sintaxe `{ body }`, mas sem parâmetros e sem geração de bytecode.

## O que são lambdas

Lambdas são funções anônimas — blocos de código que podem ser passados como argumentos.

## Sintaxe em Kof

```kf
var dobro = (x) -> x * 2;
var resultado = dobro(5);  // 10
```

## Usando com collections

```kf
var nomes = ["Ana", "Bob", "Carlos"];

var nomesMaiusculos = nomes.map(nome -> nome.toUpperCase());
// ["ANA", "BOB", "CARLOS"]

var nomesLongos = nomes.filter(nome -> nome.length() > 3);
// ["Carlos"]
```

## Method references

```kf
var nomes = ["Ana", "Bob", "Carlos"];

nomes.forEach(::println);
```

## Closures

Lambdas capturam variáveis do escopo externo:

```kf
var fator = 2;
var dobro = (x) -> x * fator;
```

## Interoperabilidade com Java

Lambdas Kof funcionam com interfaces funcionais Java:

```kf
var lista = new java.util.ArrayList<String>();

// Comparator como lambda
lista.sort((a, b) -> a.compareTo(b));
```

## Próximo passo

[Programação Funcional →](17-functional-programming.md)
