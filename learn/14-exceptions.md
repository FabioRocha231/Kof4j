# 14 — Exceptions

> **Status: implementado (JVM / Native / JS) — 0.2.6-beta — unwinding real nos 3 targets**
>
> `throw`/`try`/`catch`/`finally` com unwinding real em JVM, Native e KofJS.
> Kof lança **valores** (`throw "mensagem"` / `catch (String e)`), não
> instâncias de classe de exceção. A cadeia
> `intention->Kof->frontend->IR->backend->runtime` mantém a mesma semântica
> nos três runtimes.

## throw

Kof lança um **valor** (a mensagem vai direto para o `catch`), não uma
instância de classe de exceção:

```kf
throw "valor inválido"
```

## try/catch

```kf
try {
    var conexao = abrirConexao()
    // usar conexão
} catch (String e) {
    print("erro: " + e)
} finally {
    // limpar recursos
}
```

Vários `catch` podem filtrar pelo tipo do valor lançado:

```kf
try {
    processar()
} catch (String e) {
    print("erro de texto: " + e)
} catch (Int e) {
    print("código de erro: " + e)
}
```

## Modelo de exceção (valores)

Kof usa um modelo próprio, mais simples que o de classes de exceção do Java:

- lança-se um **valor** (`String`, `Int`, ...) com `throw`;
- `catch (Tipo e)` filtra pelo tipo do valor;
- não há enforcement de "checked/unchecked" em compile-time.

A cláusula `throws` é aceita na sintaxe, mas ainda não é verificada
(planejado). O modelo de classes de exceção do Java (`extends Exception`,
`getMessage()`) serve à interoperabilidade e é a direção planejada.

## Lançando valores contextualizados

Como a exceção é um valor, a "identidade" da falha vem da própria mensagem:

```kf
throw "user not found: " + id
```

## Uso prático

```kf
User findUser(Int id) {
    var user = repository.find(id)
    if (user == null) {
        throw "user not found: " + id
    }
    return user
}
```

## Próximo passo

[Pattern Matching →](15-pattern-matching.md)
