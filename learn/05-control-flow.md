# 05 — Controle de Fluxo

> **Status: implementado (JVM / Native / JS) — 0.2.6-beta**
>
> `if/else`, `while`, `for`, `for-in`, `switch`, `break/continue` funcionam nos três targets. Pattern matching (`case String s`, `Point(x,y)`) ver capítulo 15.

## Condicional

### if / else

```kf
if (idade >= 18) {
    print("maior de idade");
} else {
    print("menor de idade");
}
```

### if como expressão

```kf
String mensagem = if (ativo) "sim" else "não";
```

## Loops

### while

```kf
var i = 0;
while (i < 10) {
    print(i);
    i++;
}
```

### for

```kf
for (var i = 0; i < 10; i++) {
    print(i);
}
```

### for-in

```kf
for (var nome in nomes) {
    print(nome)
}
```

Funciona sobre `List<T>` e arrays.

## switch

```kf
switch (dia) {
    case 1:
        println("segunda")
        break
    case 5:
        println("sexta")
        break
    default:
        println("meio")
}
```

> Nota: switch usa `case N:` com `break` (estilo C). If-expr é a forma
> preferida para valores condicionais: `var x = if (c) a else b`.

## break e continue

```kf
for (var i = 0; i < 100; i++) {
    if (i == 50) break;
    if (i % 2 == 0) continue;
    print(i);
}
```

## Próximo passo

[Funções →](06-functions.md)
