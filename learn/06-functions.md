# 06 — Funções

> **Status: implementado**
>
> O parser já suporta declaração de métodos em classes e records, mas funções de nível superior e closures ainda não foram implementadas.

## Métodos em classes

Métodos são declarações dentro de uma classe ou record:

```kf
class Calculadora {
    Int somar(Int a, Int b) {
        return a + b;
    }
}
```

## Expression body (planejado)

Para funções simples, Kof permite uma forma mais concisa:

```kf
Int somar(Int a, Int b) = a + b;
```

Isso é equivalente a:

```kf
Int somar(Int a, Int b) {
    return a + b;
}
```

## Parâmetros

```kf
void imprimir(String mensagem, Int vezes) {
    for (var i = 0; i < vezes; i++) {
        print(mensagem);
    }
}
```

## Retorno

```kf
Int dobro(Int valor) {
    return valor * 2;
}
```

## Funções de nível superior (planejado)

```kf
Int aplicar(Int x, funcao: Int -> Int f) {
    return f(x);
}

var resultado = aplicar(5, x -> x * 2);  // 10
```

## Próximo passo

[Classes e Objetos →](07-classes-and-objects.md)
