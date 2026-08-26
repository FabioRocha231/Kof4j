# 04 — Variáveis e Tipos

## O que você vai aprender

Neste capítulo você vai entender como declara variáveis, como o sistema de tipos funciona, e como a inferência de tipos opera.

## Declaração de variáveis

Em Kof existem duas palavras-chave para variáveis:

### `var` — variável mutável

```kf
var nome = "Mel"
nome = "Outro"  // funciona
```

### `val` — Valor constante

```kf
val PI = 3.14
// PI = 2.0  // ERRO: não pode reatribuir
```

## Tipagem explícita

Você pode especificar o tipo explicitamente:

```kf
Int idade = 26
String nome = "Mel"
Bool ativo = true
```

## Inferência de tipos

Quando você usa `var` ou `val`, o compilador infere o tipo:

```kf
var idade = 26        // compilador sabe que é Int
var nome = "Mel"      // compilador sabe que é String
var pi = 3.14         // compilador sabe que é Double
var ativo = true      // compilador sabe que é Bool
```

Isso **não** é tipagem dinâmica. O compilador conhece o tipo em compile-time. É apenas uma forma mais concisa de escrever.

```kf
// Essas duas linhas são equivalentes:
var nome = "Mel"
String nome = "Mel"
```

## Tipos de referência

### Records

```kf
record Point(Int x, Int y)
```

### Classes

```kf
class User(String name)
```

### Arrays

```kf
Int[] numeros = {1, 2, 3}
String[] nomes = {"Ana", "Bob"}
```

### Enums

```kf
enum Color { Red, Green, Blue }
```

Um enum declara um conjunto fechado de constantes. O valor em runtime é o
próprio nome — comparação é por conteúdo (`==` funciona como esperado) e
`println(Color.Red)` imprime `Red`.

API embutida:

| Chamada | Retorna | Descrição |
|---------|---------|-----------|
| `Color.values()` | `List<String>` | todas as constantes, na ordem declarada |
| `Color.valueOf("Red")` | `Color?` | constante pelo nome; `null` se inválida |
| `c.name()` | `String` | o nome da constante |

Constante inexistente é erro de compilação:

```kf
Color.Nope   // SEM030: enum 'Color' não tem constante 'Nope'
```

**Switch exaustivo**: um switch sobre enum precisa cobrir **todas** as
constantes ou ter `default` — senão vira erro `SEM031` listando os casos
faltantes:

```kf
fn nome(Color c) -> String {
    var r = ""
    switch (c) {
        case Color.Red:   { r = "vermelho" }
        case Green:       { r = "verde" }      // não-qualificado também vale
        case Color.Blue:  { r = "azul" }
    }
    return r
}   // sem os três casos e sem default → SEM031
```

## Conversões

### Widening (automática)

O compilador converte automaticamente tipos menores para maiores:

```kf
Int i = 42
Long l = i     // Int → Long (automático)
Double d = i   // Int → Double (automático)
```

### Narrowing (casting)

A conversão de maior para menor precisa de cast explícito:

```kf
Double d = 3.14
Int i = d as Int   // Double → Int (precisa de 'as')
```

## Compatibilidade com tipos Java

Kof usa os mesmos tipos da JVM:

| Kof | Java | JVM |
|-----|------|-----|
| `Bool` | `boolean` | `Z` |
| `Byte` | `byte` | `B` |
| `Short` | `short` | `S` |
| `Int` | `int` | `I` |
| `Long` | `long` | `J` |
| `Float` | `float` | `F` |
| `Double` | `double` | `D` |
| `Char` | `char` | `C` |
| `String` | `String` | `Ljava/lang/String;` |

## Status atual

✅ `var` e `val` funcionam
✅ Inferência de tipos funciona
✅ Records funcionam
✅ Classes com campos funcionam
⚠️ Type checking completo ainda em desenvolvimento
⚠️ Conversões automáticas ainda em desenvolvimento

## Exercício 1

Declare variáveis de todos os tipos primitivos e imprima seus valores usando Java.

## Exercício 2

Crie um record `Produto` com campos `nome String`, `preco Double` e `quantidade Int`. Crie uma instância e acesse seus valores.

## Próximo passo

[Controle de Fluxo →](05-control-flow.md)