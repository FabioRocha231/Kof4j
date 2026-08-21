# 09 — Interfaces

## O que é uma interface

Uma interface define um contrato: métodos que uma classe deve implementar.

```kf
interface Comparavel {
    Bool maiorQue(Comparavel outro)
}
```

## Implementando uma interface

```kf
class Produto(String nome, Double preco) implements Comparavel {
    Bool maiorQue(Comparavel outro) {
        if (outro instanceof Produto outroProduto) {
            return this.preco > outroProduto.preco
        }
        return false
    }
}
```

## Interface com herança

```kf
interface Listavel<T> {
    T get(Int indice)
    Int tamanho()
}

interface Editavel<T> extends Listavel<T> {
    void set(Int indice, T valor)
    void adicionar(T valor)
    void remover(Int indice)
}
```

## Status atual

✅ Parser reconhece interfaces
✅ Lowering para interfaces funciona
✅ Gera classe com flag `ACC_INTERFACE`
✅ Métodos com flag `ACC_ABSTRACT`
⚠️ Corpo dos métodos não é gerado
⚠️ Implementação de interface ainda em desenvolvimento

## Multiplatform

Interfaces funcionam tanto no backend JVM quanto no nativo:

**JVM:** gera uma interface JVM padrão
**Nativo:** gera um vtable (tabela de dispatch virtual)

```kf
interface Comparavel {
    Bool maiorQue(Comparavel outro)
}
```

No JVM:
```java
// Gerado: interface Comparavel { boolean maiorQue(Comparavel); }
```

No nativo:
```c
// Gerado: struct Comparavel_vtable { bool (*maiarQue)(void*, void*); }
```

## Como adicionar uma interface

1. Declare a interface no arquivo `.kf`
2. Implemente em uma classe usando `implements`
3. Compile com `kof build`
4. O bytecode gerado terá a interface correta

## Próximo passo

[Herança →](10-inheritance.md)