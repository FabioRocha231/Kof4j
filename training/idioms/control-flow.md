# Idioms — Control Flow

**Status:** available · **Introduced:** 0.0.4-alpha · **Updated:** 0.2.6-beta

## What it is

Controle de fluxo sem cerimônia: `if`/`else`, `while`, `do-while`, `for`,
`for-in`, `switch`, `break`/`continue` e **if como expressão**.

## if / else

```kof
if (x > 5) {
    println("maior")
} else {
    println("menor")
}
```

## if como expressão

```kof
var status = if (ativo) "online" else "offline"
```

O if-expr produz um valor; os dois branches devem produzir valores compatíveis.

## BAD — if-expr ignorado

```kof
var status = ""
if (ativo) {
    status = "online"
} else {
    status = "offline"
}
```

## GOOD

```kof
var status = if (ativo) "online" else "offline"
```

## WHY

Declarar e depois atribuir em branches é mutação desnecessária.
A expressão-if expressa a intenção e elimina o estado intermediário.

## Loops

```kof
var i = 0
while (i < 5) {
    println(i)
    i = i + 1
}
```

```kof
for (var j = 0; j < 3; j = j + 1) {
    println(j)
}
```

```kof
do {
    println("pelo menos uma vez")
} while (falso())
```

## for-in (coleções e arrays)

```kof
var items = listOf("a", "b", "c")
for (var item in items) {
    println(item)
}

var nums = new Int[3]
nums[0] = 5
for (var n in nums) {
    println(n)
}
```

## switch (0.2.6-beta: pattern matching)

```kof
switch (x) {
    case 1:
        println("um")
        break
    case 2:
        println("dois")
        break
    default:
        println("outro")
}

// pattern matching com type + record destructuring
switch (obj) {
    case String s:
        println(s)
        break
    case Point(var x, var y):
        println(x + "," + y)
        break
    default:
        println("outro")
}
```

## When not to use

- Substituir um `for-in` por `for` com índice manual quando a ordem não importa.
- `switch` para dois casos — `if/else` é mais direto.

## Anti-patterns relacionados

- `premature-optimization.md` — loops manuais sem necessidade