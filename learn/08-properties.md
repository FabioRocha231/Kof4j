# 08 — Propriedades

> **Status: planejado (pós 0.2.6-beta)**
>
> A sintaxe de propriedades ainda não foi implementada (0.2.6-beta foca em `String?`, pattern matching e coleções). Este capítulo documenta a visão planejada.

## O problema

Em Java, para expor um campo você precisa escrever:

```java
private String name;

public String getName() {
    return name;
}

public void setName(String name) {
    this.name = name;
}
```

Três linhas para uma ideia simples.

## A solução planejada

```kf
class User {
    String name;
    Int age;
}
```

O compilador gera automaticamente:
- campo privado
- método getter
- método setter

## Controle de visibilidade

```kf
class User {
    String name { get }              // somente leitura
    String email { get set }         // leitura e escrita
    String password { get private set }  // setter privado
}
```

## Propriedades computadas

```kf
class Circulo {
    Double raio;

    Double area {
        get = 3.14159 * raio * raio;
    }
}
```

## Status atual

Hoje, campos em classes são declarados normalmente:

```kf
class User {
    String name;
    Int age;
}
```

E métodos getter/setter precisam ser escritos manualmente:

```kf
class User {
    String name;

    String getName() {
        return this.name;
    }

    void setName(String name) {
        this.name = name;
    }
}
```

Isso é o que o Java faz. A propriedade automática está planejada para simplificar isso.

## Próximo passo

[Interfaces →](09-interfaces.md)
