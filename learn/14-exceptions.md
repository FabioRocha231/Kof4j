# 14 — Exceptions

> **Status: implementado (JVM + Native unwinding) — 0.2.6-beta**
>
> `throw`/`try`/`catch`/`finally` com unwinding real em JVM e Native; a cadeia `intention->Kof->frontend->IR->backend->runtime` mantém a mesma semântica nos dois runtimes.

## throw

```kf
throw new IllegalArgumentException("valor inválido");
```

## try/catch

```kf
try {
    var conexao = abrirConexao();
    // usar conexão
} catch (IOException e) {
    print("erro: " + e.getMessage());
} finally {
    // limpar recursos
}
```

## Exceptions checked vs unchecked

Kof segue o modelo Java:

- **Unchecked** (extends `RuntimeException`): não precisa declarar
- **Checked** (extends `Exception`): precisa declarar no método

```kf
void lerArquivo(String caminho) throws IOException {
    // código que pode lançar IOException
}
```

## Exceptions personalizadas

```kf
class UserNotFound extends Exception {
    UserNotFound(String id) {
        super("user not found: " + id);
    }
}
```

## Uso prático

```kf
User findUser(UUID id) {
    var user = repository.find(id);
    if (user == null) {
        throw new UserNotFound(id.toString());
    }
    return user;
}
```

## Próximo passo

[Pattern Matching →](15-pattern-matching.md)
