# 14 — Exceptions

> **Status: implementado (JVM real; Native unwinding)**
>
> O lexer reconhece `throw`, `try`, `catch`, `finally`. O parser suporta `throw` e `try/catch` parcialmente. A geração de bytecode para exceptions ainda não está implementada.

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
