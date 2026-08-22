# Anti-pattern — Manual Data Structures

## Name

Implementar estruturas de dados que a linguagem já fornece.

## Problem

Linked lists manuais (`Node` + `next`), arrays dinâmicos manuais, hashmaps
manuais, string builders manuais — quando `List<T>` (e, no futuro, `Map`/`Set`)
já resolvem o problema.

## Bad example

```kof
class Node {
    String value
    Node next
}
class Registry {
    Node root
    Int count
    Bool hasNext() {
        return root != null && root.next != null
    }
}
```

## Why it is bad

A implementação manual carrega: alocação, encadeamento, contagem, bounds,
iteração — tudo que o programador teria que manter e testar. O domínio é
"uma coleção", não "nós encadeados".

## Preferred approach

```kof
class Registry {
    List<String> entries

    constructor() {
        entries = listOf()
    }
}
```

## Estruturas manuais comuns → alternativa

| Manual | Alternativa Kof |
|---|---|
| Linked list (`Node.next`) | `List<T>` |
| Dynamic array + `count` | `List<T>` (size) |
| Hashmap manual | **Unavailable** — `Map` é planned; use `List<record>` com busca linear |
| String builder | `+` concatenação |
| Collection wrapper | A coleção diretamente |
| Registry com `get`/`has` | `List<T>` + `contains` / loop |

## Exceptions

- Implementar uma estrutura para APRENDER a linguagem (didático).
- Estrutura com requisitos de performance comprovados que a stdlib não cobre.