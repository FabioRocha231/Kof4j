# 01 — Instalação

## O que você precisa

- **JDK 21 ou superior** — Kof é uma linguagem para a JVM. Você precisa de um JDK.
- **Maven** — o compilador Kof é construído com Maven.
- **Um editor** — qualquer um serve. VS Code, IntelliJ, vim, nano.

## Verificando o JDK

Abra o terminal e execute:

```bash
java -version
```

Você deve ver algo como:

```
openjdk version "21.0.x" ...
```

Se não tiver JDK instalado, baixe em [adoptium.net](https://adoptium.net/).

## Verificando o Maven

```bash
mvn -version
```

Se não tiver Maven, baixe em [maven.apache.org](https://maven.apache.org/download.cgi).

## Instalando o compilador Kof

Clone o repositório e construa:

```bash
git clone https://github.com/seu-usuario/kof.git
cd kof
mvn clean package -DskipTests
```

O compilador estará em:

```
kof-cli/target/kof-cli-0.1.0-SNAPSHOT.jar
```

## Criando seu primeiro projeto

Crie a estrutura:

```
meu-projeto/
├── src/
│   └── main.kf
```

No arquivo `main.kf`:

```kf
record Point(Int x, Int y)
```

## Compilando

```bash
java -jar kof-cli/target/kof-cli-0.1.0-SNAPSHOT.jar build src/
```

Isso gera um arquivo `Point.class` na pasta de saída.

## Verificando

```bash
javap -v Point.class
```

Você verá uma classe JVM válida que estende `java.lang.Record`.

## Rodando

### Com a CLI (recomendado)

Crie um arquivo `main.kf`:

```kf
fun main() = print("Hello, World!")
```

Execute:

```bash
java -jar kof-cli/target/kof-cli-0.1.0-SNAPSHOT.jar run main.kf
```

Resultado:

```
Hello, World!
```

### Chamando de Java

Crie um arquivo `Test.java`:

```java
import java.lang.reflect.Method;

public class Test {
    public static void main(String[] args) throws Exception {
        Class<?> cls = Class.forName("Point");
        Object point = cls.getConstructor(int.class, int.class).newInstance(3, 7);
        Method x = cls.getMethod("x");
        Method y = cls.getMethod("y");
        System.out.println("x = " + x.invoke(point));
        System.out.println("y = " + y.invoke(point));
    }
}
```

Compile e execute:

```bash
javac Test.java
java -cp .: Point Test
```

Resultado:

```
x = 3
y = 7
```

Seu primeiro programa Kof está rodando na JVM.

## Compilando para nativo

Kof também pode gerar binários nativos para Linux x86-64:

```bash
java -jar kof-cli/target/kof-cli-0.1.0-SNAPSHOT.jar build main.kf --target=native
```

Isso gera um executável ELF que pode rodar sem JVM:

```bash
./main
```

## Comandos da CLI

| Comando | Descrição |
|---------|-----------|
| `kof build <arquivo>` | Compila para JVM (padrão) |
| `kof build <arquivo> --target=native` | Compila para nativo |
| `kof run <arquivo>` | Compila e executa |
| `kof version` | Mostra a versão |

## Status atual

> **O compilador está funcional.** A CLI compila arquivos `.kf` que contenham records, classes, interfaces e funções. O backend JVM gera `.class` funcionais. O backend nativo gera ELF x86-64.

## Próximo passo

[Primeiro Programa →](02-first-program.md)