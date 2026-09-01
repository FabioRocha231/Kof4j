# Idioms — Strings

**Status:** available · **Introduced:** 0.0.4-alpha · **Updated:** 0.2.6-beta

## What it is

String é um tipo primário da linguagem: concatenação com `+`, comparação de
conteúdo com `==`/`!=`, e uma API de métodos direta.

## Operações (verificadas)

```kof
var s = "Hello World"
s.length                    // 11 (método: s.length())
s.charAt(1)                 // 'e' como valor numérico (101)
s.substring(6)              // "World"
s.substring(0, 5)           // "Hello"
s.contains("World")
s.startsWith("Hello")
s.endsWith("orld")
s.indexOf("W")              // 6
s.toUpperCase()
s.toLowerCase()
s.trim()
s.equalsIgnoreCase("hello world")
s.split(" ")                // String[]
var a = "x"
var b = "y"
a == b                      // comparação de CONTEÚDO (não referência)
a + "!"                     // concatenação
```

## BAD — equals de Java

```kof
if (nome.equals("Mel")) {
    ...
}
```

## GOOD

```kof
if (nome == "Mel") {
    ...
}
```

## WHY

Em Kof, `==` em strings compara conteúdo. O `.equals()` de Java existe porque
Java não pode sobrecarregar `==`. Kof não tem essa limitação.
Use `==` — é a intenção.

## BAD — concatenação manual em loop

```kof
var result = ""
for (var item in items) {
    result = result + item + ","
}
```

## GOOD

```kof
var result = ""
for (var item in items) {
    result += item + ","
}
```

Ou, quando a sequência é pequena, `listOf(...).toString()`-like ou concat direto:

```kof
var saudacao = "ola " + nome + "!"
```

## WHY

`+` já é concatenação de strings. Não há necessidade de `StringBuilder` manual —
e **não existe** uma classe StringBuilder na linguagem (não invente uma).

## Nota por target

- No Native, `length` conta **bytes UTF-8** de uma string imutável.
- No JVM, `length` conta unidades UTF-16 (comportamento padrão do `java.lang.String`).

Use `length` para tamanho; não assuma contagem de caracteres quando precisar
de precisão por target (diferença em strings com acentos/emoji).

## Null safety (0.2.6-beta)

```kof
String? s = null
if (s != null) {
    println(s.length)   // narrowing OK
}
// s.length sem check → erro SEM014
```

## Limitações

- `replace` é **somente** `replace(Char, Char)` (códigos numéricos de caractere).
- `split` retorna `String[]`.

## Anti-patterns relacionados

- `sentinel-values.md` — use `String?` em vez de `""` para "não encontrado" (0.2.6-beta)