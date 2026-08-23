# 23 — Testes

> **Status: implementado — `kof test` + `assert`**
>
> Testar Kof é escrever Kof: `assert(cond[, "msg"])` lança quando a condição
> é falsa (exit code 1); `kof test` compila e executa cada arquivo e reporta
> PASS/FAIL.

## assert

```kf
main() {
    assert(2 + 2 == 4)
    assert("kof" == "kof", "strings iguais")
    assert(listOf(1, 2).size == 2)
}
```

## kof test

```bash
kof test src/tests/            # diretório — um programa por arquivo
kof test math.kf               # arquivo único
kof test src/tests --target native
```

Saída:

```text
PASS src/tests/math.kf
FAIL src/tests/broken.kf
1 passed, 1 failed
```

O teste falha quando: o programa não compila, o processo sai com código ≠ 0
(ex.: um `assert` falso) ou o main não é encontrado.

## Convenção

Cada arquivo `.kf` de teste é um programa executável independente (tem
`main()`). O `assert` é a primitive — não há framework nem annotations.

## Escrevendo uma suite

```kf
// math.kf — um arquivo por área
Int soma(Int a, Int b) {
    return a + b
}
main() {
    assert(soma(2, 3) == 5)
    assert(soma(-1, 1) == 0, "negativos")
    println("math ok")
}
```

## JUnit (não usar)

O ecossistema Java/JUnit **não** faz parte da linguagem — sem annotations,
sem framework. O teste Kof é a linguagem.
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
