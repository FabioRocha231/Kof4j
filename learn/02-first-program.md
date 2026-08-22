# 02 — Primeiro Programa

## O construto mais básico

Em Kof, o construto mais simples que o compilador gera bytecode válido é o **record**:

```kf
record Ponto(Int x, Int y)
```

Isso cria uma classe JVM com:
- dois campos privados e finais (`x`, `y`)
- um construtor público que aceita `Int` e `Int`
- dois métodos públicos `x()` e `y()` que retornam os valores
- um método `toString()`

## Entendendo cada parte

```
record      → palavra-chave: define um record
Ponto       → nome da classe
(           → início dos componentes
Int x       → primeiro componente: tipo Int, nome x
,           → separador
Int y       → segundo componente: tipo Int, nome y
)           → fim dos componentes
```

## Criando instâncias

Em Java, para criar uma instância você escreve:

```java
new User("Mel", "mel@example.com")
```

Em Kof, a sintaxe para records é a mesma do Java, mas o compilador gera tudo automaticamente:

```java
new Ponto(3, 7)
```

## Acessando valores

Os métodos de acesso são gerados automaticamente:

```java
Ponto p = new Ponto(3, 7);
p.x()  // retorna 3
p.y()  // retorna 7
```

## Um programa completo

Agora Kof suporta `main()`. Você pode criar um programa completo:

Arquivo `main.kf`:

```kf
main() = print("Olá, mundo!")
```

Compilando e executando:

```bash
kof run main.kf
```

Resultado:

```
Olá, mundo!
```

## Um programa com records

Arquivo `ponto.kf`:

```kf
record Ponto(Int x, Int y)

main() {
    var p = Ponto(3, 7)
    print(p)
}
```

Executando:

```bash
kof run ponto.kf
```

Resultado:

```
Ponto[x=3, y=7]
```

## Variáveis e inferência

Kof suporta inferência de tipos:

```kf
var nome = "Mel"
var idade = 26
var pi = 3.14
```

O compilador entende os tipos automaticamente.

## Exercício 1

1. Crie um arquivo `coordenada.kf`
2. Defina um record com dois campos: `lat Double` e `lon Double`
3. Compile com a CLI
4. Verifique com `javap -v Coordenada.class`

## Exercício 2

1. Crie um record `Pessoa` com campos `nome String` e `idade Int`
2. Crie uma função main que crie uma pessoa e imprima seus dados
3. Execute com `kof run`

## Próximo passo

[Fundamentos da Linguagem →](03-language-basics.md)