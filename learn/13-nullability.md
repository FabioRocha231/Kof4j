# 13 — Nullability

> **Status: planejado**
>
> O sistema de nullability ainda não foi implementado. Este capítulo documenta a visão planejada.

## O problema

`NullPointerException` é a causa mais comum de erros em Java.

```java
String nome = null;
System.out.println(nome.length());  // NullPointerException!
```

## A solução planejada

Kof pode adicionar tipos nullable:

```kf
String nome = "Mel";     // não pode ser null
String? apelido = null;   // pode ser null
```

Se você tentar acessar algo em um tipo nullable:

```kf
String? nome = obterNome();
print(nome.length());  // ERRO: nome pode ser null
```

O compilador exige tratamento:

```kf
String? nome = obterNome();
if (nome != null) {
    print(nome.length());  // seguro
}
```

## Interoperabilidade com Java

Java não tem nullability. Quando você chama código Java:

```kf
// Isso pode retornar null do Java
String? resultado = javaMethod();
```

## Onde estamos hoje

Hoje, Kof não tem sistema de nullability. Null é tratado como qualquer outro valor, como em Java.

A decisão de como implementar nullability será tomada após o type system estar mais maduro.

## Próximo passo

[Exceptions →](14-exceptions.md)
