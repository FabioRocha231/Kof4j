# 19 — Packages e Módulos

> **Status: parcial**
>
> O parser tem suporte a package e import, mas a resolução de nomes entre packages ainda não está implementada.

## Package

Cada arquivo Kof pode declarar um package:

```kf
package com.exemplo.users

record User(String name, String email)
```

Isso gera a classe no package `com.exemplo.users`.

## Imports

```kf
import java.util.List;
import java.util.Map;
import java.util.HashMap;
```

### Import estático (planejado)

```kf
import static java.lang.Math.PI;
import static java.lang.Math.sqrt;
```

### Import de módulo (planejado)

```kf
import module java.base;
```

## Visibilidade

| Modificador | Mesmo package | Subclasses | Qualquer lugar |
|-------------|:---:|:---:|:---:|
| `public` | ✅ | ✅ | ✅ |
| `protected` | ✅ | ✅ | ❌ |
| (padrão) | ✅ | ❌ | ❌ |
| `private` | ❌ | ❌ | ❌ |

## Módulos JPMS (planejado)

```kf
module com.exemplo.app {
    requires java.sql;
    requires spring.boot;
    exports com.exemplo.api;
}
```

## Próximo passo

[Annotations →](20-annotations.md)
