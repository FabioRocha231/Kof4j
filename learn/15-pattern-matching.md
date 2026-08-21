# 15 — Pattern Matching

> **Status: planejado**
>
> O parser suporta `instanceof` básico, mas pattern matching com tipos e destructuring ainda não foi implementado.

## instanceof com padrão

```kf
if (obj instanceof String s) {
    print("é uma string: " + s);
}
```

Em vez de:

```kf
if (obj instanceof String) {
    String s = (String) obj;
    print("é uma string: " + s);
}
```

## switch com padrões

```kf
return switch (forma) {
    case Circulo c -> "círculo com raio " + c.raio();
    case Retangulo r -> "retângulo " + r.largura() + "x" + r.altura();
    default -> "forma desconhecida";
};
```

## Padrões em sealed hierarchies

```kf
sealed class Resultado<T> permits Sucesso<T>, Erro<T> {}

String mensagem(Resultado<String> r) {
    return switch (r) {
        case Sucesso<String> s -> "ok: " + s.valor();
        case Erro<String> e -> "falha: " + e.mensagem();
    };
}
```

O compilador verifica se todos os casos foram cobertos (exhaustiveness check).

## Padrões com guards

```kf
switch (nota) {
    case Int n when n >= 9 -> "excelente";
    case Int n when n >= 7 -> "bom";
    case Int n when n >= 5 -> "regular";
    default -> "reprovado";
}
```

## Próximo passo

[Lambdas →](16-lambdas.md)
