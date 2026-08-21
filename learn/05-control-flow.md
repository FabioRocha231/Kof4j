# 05 — Controle de Fluxo

> **Status: planejado**
>
> O parser já reconhece `if`, `else`, `while`, `for`, `switch`, mas a geração de bytecode para controle de fluxo ainda não está implementada. Este capítulo documenta a sintaxe planejada.

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

### for-each (planejado)

```kf
for (String nome : nomes) {
    print(nome);
}
```

## switch

```kf
return switch (dia) {
    case "segunda" -> "início";
    case "sexta" -> "fim";
    default -> "meio";
};
```

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
