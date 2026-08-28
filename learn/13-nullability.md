# 13 — Nullability

> **Status: básico implementado (0.2.0-beta) — `String?` + verificação via `kof check`**
>
> `String?` (e `Tipo?` em geral) já é reconhecido pelo parser/type system (`NullableType`, `parseTypeRef` com `?`); o compilador exige tratamento antes de dereferenciar e `kof check` valida. Interop com Java mapeia retornos que podem ser `null` para `String?`. No runtime, `String?` é erasure para `String` (mesma carga), a guarda é do type system — `intention->Kof->frontend->IR->backend->runtime`.

## O problema

`NullPointerException` é a causa mais comum de erros em Java.

```java
String nome = null;
System.out.println(nome.length());  // NullPointerException!
```

## A solução (0.2.0-beta)

Kof adiciona tipos nullable com `?`:

```kf
String nome = "Mel"           // não pode ser null
var apelido: String? = null   // pode ser null (nullable básico 0.2.0)
```

Verificação com `kof check` (compile-time):

```bash
kof check null.kf   # valida String? e exige guard
```

Exemplo que passa no `kof check`:

```kf
main() {
    var nome: String? = null
    if (nome == null) {
        println("sem nome")
    }
    var comNome: String? = "Mel"
    if (comNome != null) {
        println(comNome)  // seguro: guard libera o acesso
    }
}
```

```bash
kof check null.kf          # ✅ sem erros
kof run null.kf --target=js # ✅ JS (GraalJS) executa com erasure para String
# kof run --target=jvm ainda usa erasure; guard é só verificação — runtime é String normal
```

Se você tentar acessar sem verificar, o compilador reporta:

```kf
var nome: String? = obterNome()
println(nome.length())  // ERRO: nome pode ser null — exige `if (nome != null)` antes
```

O padrão idiomático (compile-time, runnable via `kof check` + execução):

```kf
var nome: String? = obterNome()
if (nome != null) {
    println(nome)  // seguro — guard libera o acesso (erasure para String no runtime)
} else {
    println("vazio")
}
```

## Interoperabilidade com Java

Java não tem nullability. Quando você chama código Java que pode retornar `null`:

```kf
var resultado: String? = javaMethod()   // força o tipo nullable
if (resultado != null) {
    println(resultado)
}
```

## Onde estamos (0.2.0)

- ✅ `String?`, `Int?` e `Tipo?` no parser (`parseTypeRef` consome `?`) e `Type.of` (`NullableType`)
- ✅ `kof check` valida `String?` e exige `if (x != null)` antes de dereferenciar
- ✅ `var x: String? = null` / `var x: String? = "Mel"` como forma runnable (var + `:`)
- 🚧 Flow analysis mais profundo e operadores `?.` / `?:` ainda planejados
- 🚧 Codegen JVM/JS é erasure (nullable vira String no bytecode); `length()` após guard ainda passa pelo type checker mas runtime é String normal

Antes de 0.2.0, `null` era tratado como qualquer valor Java. Agora `?` é a forma oficial de documentar e checar nulabilidade (658 testes).

## Próximo passo

[Exceptions →](14-exceptions.md)
