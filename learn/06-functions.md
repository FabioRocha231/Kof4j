# 06 — Funções

> **Status: implementado (JVM / Native / JS / KofScript) — 0.2.0-beta**
>
> Funções de nível superior, métodos, expression bodies e default parameters funcionam nos três targets (JVM, Native, KofJS) + KofScript (`let`→`KofScriptGlobals`). Target separation `native.risc/arm` preserva a mesma IR.

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

## Funções de nível superior como valores (0.2.0)

Lambdas com capturas já permitem passar funções como valores — a base para `map/filter/reduce`:

```kf
Int aplicar(Int x, f: (Int) -> Int) {
    return f(x)
}

main() {
    var r = aplicar(5, (x: Int) -> x * 2)  // 10
    println(r)
}
```

No KofScript, `let`/`const` no topo são alias para `var`/`val` e viram `KofScriptGlobals` para estado entre células/REPL.

## Próximo passo

[Classes e Objetos →](07-classes-and-objects.md)
