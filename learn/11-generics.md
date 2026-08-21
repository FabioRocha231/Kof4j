# 11 — Generics

> **Status: planejado**
>
> O type system define `TypeVariable` e `WildcardType`, mas o parser e o lowering não suportam generics ainda.

## O problema

Sem generics, você precisa de casts:

```java
List lista = new ArrayList();
lista.add("texto");
String texto = (String) lista.get(0);  // cast manual
```

Com generics, o compilador sabe o tipo:

```java
List<String> lista = new ArrayList<String>();
lista.add("texto");
String texto = lista.get(0);  // sem cast
```

## Generics em Kof

### Classes genéricas

```kf
class Caixa<T>(T valor) {
    T get() {
        return this.valor;
    }
}
```

Uso:

```kf
var caixaTexto = new Caixa<String>("olá");
var caixaNumero = new Caixa<Int>(42);
```

### Métodos genéricos

```kf
<T> T primeiro(List<T> lista) {
    return lista.get(0);
}
```

### Bounds

```kf
<T extends Comparable<T>> T maximo(T a, T b) {
    return a.maiorQue(b) ? a : b;
}
```

## Variância (planejado)

```kf
void copiar(List<? extends Animal> origem, List<? super Animal> destino) {
    for (var animal : origem) {
        destino.add(animal);
    }
}
```

## Interoperabilidade com generics Java

```kf
// Kof usando generics Java
var lista = new java.util.ArrayList<String>();
lista.add("hello");
String item = lista.get(0);
```

## Próximo passo

[Collections →](12-collections.md)
