# 19 — Packages e Módulos

> **Status: implementado — imports `a.b.C` corrigidos para projetos grandes (0.2.0-beta)**
>
> `package` + `import` funcionam end-to-end. Em 27/08 o `CompilerDriver` passou a tratar `import a.b.C` como import de arquivo **mais** import de diretório `a.b`, corrigindo a perda de imports em projetos com `a/b/C.kf`.

## Package

Cada arquivo Kof pode declarar um package:

```kf
package com.exemplo.users

record User(String name, String email)
```

Isso gera a classe no package `com.exemplo.users` (JVM: internal name `com/exemplo/users/User`).

## Imports

```kf
import java.util.List
import java.util.Map
import java.util.HashMap
import a.b.C          // 0.2.0: resolve tanto o arquivo C.kf quanto o diretório a/b/
```

`kof build` agora compila `largeproj` corretamente:

```text
src/
├── Main.kf          // import a.b.C
└── a/b/C.kf         // package a.b; class C { ... }
→ Main.class + a/b/C.class  (decls=2, ambos emitidos)
```

Antes de 27/08, `import a.b.C` em projetos grandes podia perder a segunda declaração — o driver só registrava o arquivo, não o diretório. Agora (`CompilerDriver.java:243`) registra `file import` + `dir import`, e a cadeia `intention->Kof->frontend->IR->backend->runtime` preserva todos os `CompilationUnit`s até o backend.

### Exemplo runnable (multi-arquivo)

```kf
// a/b/C.kf
package a.b
class C {
    String msg() { return "de C" }
}
```

```kf
// Main.kf
import a.b.C

main() {
    var c = C()
    println(c.msg())  // de C
}
```

```bash
kof build src --target=jvm     # gera Main.class + a/b/C.class
kof build src --target=native  # ELF com ambas as units linkadas
kof build src --target=js      # Default.mjs com import C
```

### Import estático (planejado)

```kf
import static java.lang.Math.PI
import static java.lang.Math.sqrt
```

### Import de módulo (planejado)

```kf
import module java.base
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
    requires java.sql
    requires spring.boot
    exports com.exemplo.api
}
```

## Próximo passo

[Annotations →](20-annotations.md)
