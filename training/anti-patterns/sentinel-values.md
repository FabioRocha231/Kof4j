# Anti-pattern — Sentinel Values

## Name

Usar um valor de dados para representar ausência/erro.

## Problem

`""`, `-1`, `0`, `"not found"` retornados para significar "não existe".
O consumidor precisa conhecer a convenção; valores legítimos podem colidir
com a sentinela; o erro não carrega informação.

## Bad example

```kof
Int findIndex(String key) {
    for (var i = 0; i < keys.size; i = i + 1) {
        if (keys.get(i) == key) {
            return i
        }
    }
    return -1
}
```

`-1` é a sentinela. O chamador precisa lembrar: `if (findIndex(k) >= 0)`.

## Why it is bad

- Convenção invisível (o tipo `Int` não diz que `-1` é especial).
- Erro e dado são indistinguíveis.
- Não há mensagem de erro.

## Preferred approach (erro real)

```kof
Int findIndex(String key) {
    for (var i = 0; i < keys.size; i = i + 1) {
        if (keys.get(i) == key) {
            return i
        }
    }
    throw "key not found: " + key
}
```

O consumidor trata com `try/catch` e recebe a informação do erro.

## Preferred approach (ausência como dado)

```kof
// Quando ausência é um estado normal do domínio:
record Found(Bool ok, Int index)
// ou, até Option<T> existir:
Bool findIndex(String key): Int  // hmm — veja o aviso abaixo
```

> **WORKAROUND — não idiom:**
> A linguagem **não possui** `Option<T>`/`null safety` ainda (planned).
> Se o domínio exige "ausência como valor" (não como erro), o retorno de
> sentinela pode ser o workaround aceitável NO MOMENTO — mas marque-o
> explicitamente como `WORKAROUND`, não como idiom da linguagem.

## Exceptions

- Convenções de APIs externas (ex.: índices retornam -1 em certos protocolos).