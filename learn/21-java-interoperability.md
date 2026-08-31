# 21 — Java Interoperability

> **Status: parcial — bytecode JVM compatível, chamada Java direta funcional (0.2.6-beta)**
>
> O compilador gera bytecode JVM padrão compatível com Java (chamar e ser chamado, libs, annotations). A sintaxe `import java.util.*` + `new ArrayList<String>()` já funciona; `super.metodo()` contra classes do classpath (`.jar`/`.aar`) resolve descritores via `CompilerDriver.setExternalClasspath`.

## A premissa

Kof não precisa reimplementar o ecossistema Java para utilizá-lo.

O bytecode gerado por Kof é bytecode JVM padrão. Isso significa que qualquer biblioteca Java funciona automaticamente.

## Usando Java Collections

```kf
import java.util.ArrayList;
import java.util.HashMap;

var lista = new ArrayList<String>();
lista.add("Kof");
lista.add("é");
lista.add("legal");

var mapa = new HashMap<String, Integer>();
mapa.put("kof", 1);
```

## Usando Java IO

```kf
import java.io.File;
import java.io.FileWriter;

var arquivo = new File("saida.txt");
var writer = new FileWriter(arquivo);
writer.write("olá mundo");
writer.close();
```

## Usando Java Time

```kf
import java.time.LocalDate;
import java.time.LocalDateTime;

var hoje = LocalDate.now();
var agora = LocalDateTime.now();
```

## Usando Java Streams

```kf
import java.util.stream.Collectors;

var numeros = [1, 2, 3, 4, 5];
var pares = numeros.stream()
    .filter(n -> n % 2 == 0)
    .collect(Collectors.toList());
// [2, 4]
```

## Usando JDBC

```kf
import java.sql.Connection;
import java.sql.DriverManager;

var conn = DriverManager.getConnection("jdbc:mysql://localhost/db", "user", "pass");
var stmt = conn.createStatement();
var rs = stmt.executeQuery("SELECT * FROM users");
```

## Usando Spring

```kf
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
class UserService {
    @Autowired
    UserRepository repository;

    User find(UUID id) {
        return repository.findById(id).orElse(null);
    }
}
```

## Regras de interoperabilidade

1. **Tipos Kof → Java**: mapeados diretamente (`Int` → `int`, `String` → `String`)
2. **Java → Kof**: APIs Java são chamadas normalmente
3. **Generics**: funcionam entre as linguagens
4. **Exceptions**: checked exceptions propagam corretamente
5. **Reflection**: enxerga classes Kof normalmente
6. **Annotations**: chegam ao bytecode corretamente

## Próximo passo

[JVM →](22-jvm.md)
