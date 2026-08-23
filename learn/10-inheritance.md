# 10 — Herança

> **Status: implementado**
>
> A herança está parcialmente suportada no parser, mas a resolução de chamadas de métodos herdados não está implementada.

## Extends

```kf
class Animal(String nome) {
    void falar() {
        print(nome + " faz um barulho");
    }
}

class Cachorro(String raca) extends Animal {
    void falar() {
        print(nome + " late");
    }
}
```

## Hierarquia

```
Object
  └── Animal
        ├── Cachorro
        └── Gato
```

## Classes abstratas

```kf
abstract class Forma {
    abstract Double area();
}

class Circulo(Double raio) extends Forma {
    Double area() {
        return 3.14159 * raio * raio;
    }
}

class Retangulo(Double largura, Double altura) extends Forma {
    Double area() {
        return largura * altura;
    }
}
```

## sealed classes (planejado)

```kf
sealed class Resultado<T> permits Sucesso<T>, Erro<T> {}

class Sucesso<T>(T valor) extends Resultado<T> {}
class Erro<T>(String mensagem) extends Resultado<T> {}
```

Isso garante que `Resultado` só pode ser implementado por `Sucesso` e `Erro`. O compilador pode verificar a completude do `switch`.

## Polimorfismo

```kf
void imprimirArea(Forma forma) {
    print(forma.area());
}

var c = new Circulo(5.0);
var r = new Retangulo(3.0, 4.0);

imprimirArea(c);  // 78.53975
imprimirArea(r);  // 12.0
```

## Próximo passo

[Generics →](11-generics.md)
