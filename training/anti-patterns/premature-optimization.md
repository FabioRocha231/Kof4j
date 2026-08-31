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

## Performance real conhecida (0.2.6-beta — 30 Aug 2026)

- Native: free-list `kof_free_head` first-fit + `kof_gc_collect` mark-sweep conservador (27/08). `kof_alloc` tenta GC antes de `mmap` se free-list vazia; `kof_free` push sem syscall. GC ainda não é completo — programas muito longos devem evitar vazamento.
- Native strings: UTF-8 bytes; concatenação aloca nova string.
- JVM: ArrayList, String, GC, virtual threads.
- JS: GraalJS ES Modules; `kof.http` via Java HttpClient interop.

## Exceptions

- Algoritmos cuja complexidade é do domínio (ex.: ordenação, busca indexada).