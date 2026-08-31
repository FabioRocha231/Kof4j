# 06 — Funções

> **Status: implementado (JVM / Native / JS / KofScript) — 0.2.6-beta**
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
void greet(String name = "world") {
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

## Funções como valores (0.2.0)

Lambdas são valores de primeira classe — se guardam em variável e se passam
como argumento (é assim que `map/filter/reduce` funcionam, ver cap. 12):

```kf
main() {
    var dobro = (x: Int) -> x * 2
    println(dobro(5))                                  // 10

    var nums = listOf(1, 2, 3)
    var dobrados = nums.map((x: Int) -> x * 2)         // [2, 4, 6]
    println(dobrados.get(0))                           // 2
}
```

Não existe tipo de função anotado como parâmetro de uma função declarada —
a função chega como lambda anônimo no ponto de chamada.

No KofScript, `let`/`const` no topo são alias para `var`/`val` e viram `KofScriptGlobals` para estado entre células/REPL.

## Próximo passo

[Classes e Objetos →](07-classes-and-objects.md)
