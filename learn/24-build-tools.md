# 24 — Build Tools

> **Status: planejado**
>
> A integração com build tools ainda não está implementada. Este capítulo documenta a visão planejada.

## Maven

### Estrutura de projeto

```
meu-projeto/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/          ← código Java
│   │   └── kof/           ← código Kof
│   └── test/
│       ├── java/
│       └── kof/
```

### pom.xml

```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.exemplo</groupId>
    <artifactId>meu-app</artifactId>
    <version>1.0-SNAPSHOT</version>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
            <version>3.2.0</version>
        </dependency>
    </dependencies>
</project>
```

### Compilando

```bash
mvn compile
```

O plugin Kof compila `.kf` antes de `.java`.

## Gradle

### build.gradle.kts

```kotlin
plugins {
    id("dev.kof.kof") version "0.1.0"
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web:3.2.0")
}
```

### Compilando

```bash
./gradlew build
```

## Coexistência com Java

Kof e Java podem coexistir no mesmo projeto:

```
src/
├── main/
│   ├── java/
│   │   └── com/exemplo/
│   │       └── legacy/
│   │           └── OldService.java
│   └── kof/
│       └── com/exemplo/
│           └── new/
│               └── NewService.kf
```

O compilador Kof gera `.class` que o Java pode chamar normalmente.

## Próximo passo

[Spring →](25-spring.md)
