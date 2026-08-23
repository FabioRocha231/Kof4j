# Idioms — Errors

**Status:** available (JVM e Native) · **Introduced:** 0.0.4-alpha

## What it is

Exceções são Strings. `throw "mensagem"`, `catch (String e)`, `finally`.
Funciona em JVM (exception table) e Native (unwinding próprio).

```kof
try {
    throw "boom"
    println("unreachable")
} catch (String e) {
    println("caught: " + e)
} finally {
    println("finally")
}
```

## When to use

- Erro que interrompe o fluxo e precisa ser tratado em outro ponto.
- `finally` para cleanup que deve rodar em todos os caminhos.

## When not to use

- Fluxo normal de controle — use `if`.
- Validação simples — `if` + retorno.
- **Não existe `Option<T>`** (planned). Não invente sentinelas quando uma
  exceção comunica o erro melhor.

## BAD — sentinela

```kof
String find(String key) {
    for (var entry in entries) {
        if (entry.key == key) {
            return entry.value
        }
    }
    return ""
}
```

Quando `""` significa "não encontrado", o consumidor precisa checar por convenção.
Isso é uma sentinela: o dado e o erro são indistinguíveis.

## GOOD — exceção

```kof
String find(String key) {
    for (var entry in entries) {
        if (entry.key == key) {
            return entry.value
        }
    }
    throw "not found: " + key
}
```

Uso:

```kof
try {
    var v = find("x")
} catch (String e) {
    println("falhou: " + e)
}
```

## WHY

A exceção carrega a informação do erro no próprio mecanismo de erros da
linguagem. A sentinela espalha a convenção por todos os consumidores.

> **Limitação honesta:** a linguagem ainda não tem `Option<T>`/`Result<T>`.
> Se o domínio exige ausência como valor (e não como erro), sentinela pode ser
> aceitável — marque como `WORKAROUND`, não como idiom.

## Propagation

```kof
void inner() {
    throw "from-inner"
}
String outer() {
    try {
        inner()
        return "no"
    } catch (String e) {
        return "got: " + e
    }
}
```

A exceção atravessa frames (funções chamadas) em ambos os targets.

## finally

```kof
try {
    trabalho()
} finally {
    limpar()
}
```

`finally` roda no caminho normal, no caminho capturado e na propagação.

## Anti-patterns relacionados

- `sentinel-values.md`
- `runtime-workarounds.md`