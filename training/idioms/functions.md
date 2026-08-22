# Idioms — Functions

**Status:** available · **Introduced:** 0.0.4-alpha (sem `fun`)

## What it is

Kof não possui a palavra-chave `fun`. Funções são declaradas pelo nome,
com o tipo de retorno antes do nome **ou** após os parâmetros.

## Formas válidas (todas verificadas)

```kof
main() {
    println("entry point")
}
```

```kof
String saudacao() {
    return "oi"
}
```

```kof
despedida(): String {
    return "tchau"
}
```

```kof
void fazIsso() {
    println("void explícito")
}
```

```kof
Bool positivo(Int x) = x > 0      // expression body
```

```kof
int dobro(int x) {
    return x * 2
}
```

## When to use funções top-level

- Lógica sem estado (helpers, validação, transformação).
- Utility classes de Java viram funções top-level.
- Handlers de `kof serve` (`handle(...)`) são funções top-level.

## When not to use

- Dados + comportamento → classe ou record.
- `main()` é a única função sem tipo explícito e sem retorno.

## BAD — utility class

```kof
class StringUtils {
    static String capitalizar(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1)
    }
}
```

## GOOD — função top-level

```kof
String capitalizar(String s) {
    return s.substring(0, 1).toUpperCase() + s.substring(1)
}
```

## WHY

A utility class de Java existe porque Java não tem funções fora de classes.
Kof tem funções top-level. A camada extra de classe é ruído.

## Lambdas

```kof
var f = (x: Int) -> x * 2
println(f(21))          // 42

var g = (a: Int, b: Int) -> a + b
println(g(3, 4))        // 7

var h = () -> 99
println(h())            // 99
```

- Lambdas compilam para classes sintéticas com método `invoke`.
- **Sem captura de variáveis do escopo** (planned).
- Lambdas não podem referenciar variáveis locais externas ainda.

## BAD — lambda que espera captura

```kof
var offset = 10
var f = (x: Int) -> x + offset   // ERROR: captura não suportada
```

## WHY

Captura está planned. Usar lambdas apenas com parâmetros e literais por enquanto.

## Anti-patterns relacionados

- `java-like-code.md` — utility classes
- `unnecessary-abstraction.md` — factory/wrapper