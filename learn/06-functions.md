# 06 — Funções

> **Status: implementado**
>
> Funções de nível superior, métodos, expression bodies e default parameters funcionam nos três targets (JVM, Native, KofJS).

## Funções de nível superior

```kf
Int soma(Int a, Int b) {
    return a + b
}

main() {
    println(soma(2, 3))
}
```

## Expression body

```kf
Bool positivo(Int x) = x > 0
```

## Parâmetros

```kf
void imprimir(String mensagem, Int vezes) {
    for (var i = 0; i < vezes; i++) {
        print(mensagem);
    }
}
```

## Default parameters

```kf
greet(String name = "world") {
    println("hello " + name)
}

main() {
    greet("Mel")
    greet()          // usa o default — "hello world"
}
```

`Server(8080)` / `Server()` para classes com componentes default seguem a
mesma semântica, resolvida em compile-time.

## Return nu

Em funções `void`, `return` pode aparecer sozinho:

```kf
void maybe(Bool condition) {
    if (condition) {
        return
    }
    println("not-returned")
}
```

`return;` também é aceito por compatibilidade.

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
