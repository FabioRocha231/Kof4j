# Anti-pattern — Premature Optimization

## Name

Otimizar antes de medir, trocando simplicidade por micro-performance.

## Problem

Implementar arrays manuais, caching manual, estruturas de dados complexas ou
truques de baixo nível — quando o programa ainda nem funciona direito.

## Bad example

```kof
// BAD: micro-otimização sem necessidade
var buffer = new Char[1024]
var len = 0
for (var i = 0; i < input.length; i = i + 1) {
    buffer[len] = input.charAt(i)
    len = len + 1
}
var result = ...
```

## Good example

```kof
var result = ""
for (var i = 0; i < input.length; i = i + 1) {
    result += input.charAt(i)
}
```

Ou, quando a semântica permite:

```kof
var result = input
```

## Why it is bad

O custo real raramente está onde o programador adivinha. A versão simples é
mais legível, mais correta e mais fácil de evoluir. A versão otimizada só é
justificável com medição.

## Regra

1. Escreva a versão idiomática.
2. Meça (se houver requisito de performance).
3. Otimize apenas o ponto medido, com comentário explicando por quê.

## Performance real conhecida (0.0.5)

- Native: sem GC — a memória é devolvida ao SO no exit (mmap por alocação).
  Programas de longa duração com alocação contínua devem ter isso em mente.
- Native: strings são UTF-8 bytes; concatenação aloca nova string.
- JVM: tudo delega para a plataforma (ArrayList, String, GC).

## Exceptions

- Algoritmos cuja complexidade é do domínio (ex.: ordenação, busca indexada).