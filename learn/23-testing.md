# 23 — Testes

> **Status: planejado**
>
> Testes em Kof usam JUnit. O compilador gera bytecode compatível com JUnit.

## JUnit 5

```kf
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserTest {
    @Test
    void deveCriarUser() {
        var user = new User("Mel", "mel@example.com");
        assertEquals("Mel", user.name());
        assertEquals("mel@example.com", user.email());
    }

    @Test
    void deveFalharSeNomeNulo() {
        assertThrows(NullPointerException.class, () -> {
            new User(null, "email@test.com");
        });
    }
}
```

## Assertions

```kf
assertEquals(esperado, atual);
assertTrue(condicao);
assertFalse(condicao);
assertNull(valor);
assertNotNull(valor);
assertThrows(TipoExcecao.class, () -> { ... });
```

## Testes parametrizados (planejado)

```kf
@ParameterizedTest
@ValueSource(ints = {1, 2, 3, 4, 5})
void deveSerPositivo(Int numero) {
    assertTrue(numero > 0);
}
```

## Executando testes

```bash
mvn test
```

Ou com a CLI:

```bash
java -jar kof-cli.jar test src/test/
```

## Próximo passo

[Build Tools →](24-build-tools.md)
