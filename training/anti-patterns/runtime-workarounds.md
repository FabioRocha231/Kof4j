# Anti-pattern — Runtime Workarounds

## Name

Tratar workarounds temporários como regras da linguagem.

## Problem

Quando uma feature ainda não existe, o código precisa de um desvio.
O desvio é legítimo — mas **não é idiom**. O corpus deve marcar
explicitamente `WORKAROUND` e `NOT IDIOMATIC`.

## Workarounds atuais conhecidos (0.0.5)

### 1. Ausência como valor (Option)

```kof
// WORKAROUND — Option<T> não existe (planned)
// null não é suportado como valor retornável seguro; use:
//  - exceção para erro;
//  - sentinela documentada para ausência esperada.
```

**Não aprenda isto como idiom oficial.**

### 2. JSON de objetos no Native

```kof
// WORKAROUND — json.encode(de objeto) no Native não é suportado (JSN002)
// Use: json.encode(listOf(1, 2, 3))  (primitivos/lists funcionam em ambos)
```

### 3. JSON Float/Double

```kof
// WORKAROUND — JSN001
// json.encode(1.5) não compila; converta para String ou use int.
```

### 4. Captura em lambdas

```kof
// WORKAROUND — captura de variáveis do escopo em lambdas não existe (planned)
var f = (x: Int) -> x * 2   // OK
// var f = (x: Int) -> x + offset  // NÃO compila (captura)
```

### 5. Construtor com argumentos

```kof
// A classe precisa declarar `constructor(...)` para `new User(args)`.
// Construtor automático por campos: planned.
class User {
    String name
    public constructor(String name) {
        this.name = name
    }
}
```

## Regra

1. Todo workaround no corpus carrega o rótulo `WORKAROUND`.
2. Todo workaround cita a feature planned que o substituirá.
3. Nunca apresente workaround como exemplo de "código idiomático".

## Quando um workaround deixa de ser workaround

Quando a feature é implementada, o exemplo oficial é atualizado e o rótulo
removido. O histórico conceitual é preservado no CHANGELOG, não no corpus.

## Exceptions

- Nenhuma: workarounds são sempre temporários por definição.