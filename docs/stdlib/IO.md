# kof.io — Filesystem API

`kof.io` é a API oficial de filesystem do Kof: arquivos, diretórios e
caminhos com uma única semântica nos targets JVM e Native.

## Tipos

`File`, `Path` e `Directory` representam um caminho (a string do path).
Todas as operações de `kof.io` funcionam nos três tipos — o tipo apenas
orienta a intenção.

## Path

| Operação | Exemplo | Resultado (Linux/macOS) |
|----------|---------|--------------------------|
| `resolve` | `Path("data").resolve("users.txt")` | `data/users.txt` |
| `parent` | `Path("data/users.txt").parent()` | `data` |
| `fileName` | `Path("data/users.txt").fileName()` | `users.txt` |
| `extension` | `Path("data/users.txt").extension()` | `txt` |
| `normalize` | `Path("a/./b/../c").normalize()` | `a/c` |
| `isAbsolute` | `Path("/x").isAbsolute()` | `true` |
| `toAbsolute` | `Path("x").toAbsolute()` | caminho absoluto |

No Windows o separador é `\`; o código Kof nunca concatena separadores.

## File

| Operação | Descrição |
|----------|-----------|
| `exists()` | Bool |
| `isFile()` / `isDirectory()` | Bool |
| `readText()` | String UTF-8, `null` se falhar |
| `writeText(s)` / `appendText(s)` | Bool, UTF-8 |
| `readBytes()` | `Int[]` (0-255), `null` se falhar |
| `writeBytes(b)` / `appendBytes(b)` | Bool |
| `size()` | Long, `-1` se não existir |
| `delete()` | Bool (arquivo ou diretório vazio) |
| `name()` / `path()` | String |

Formas estáticas: `File.exists(p)`, `File.readText(p)`,
`File.writeText(p, s)`, `File.appendText(p, s)`, `File.delete(p)`,
`File.size(p)`, `File.name(p)`.

## Directory

| Operação | Descrição |
|----------|-----------|
| `exists()` | Bool |
| `create()` | cria; falha se já existe |
| `createDirectories()` | cria recursivamente |
| `list()` | `List<String>` dos nomes, ordenado |
| `delete()` | remove diretório vazio |

```kof
var dir = Directory("data")
dir.createDirectories()
for (var entry in dir.list()) {
    println(entry.name)
}
```

`entry.name` e `entry.path` retornam o próprio entry.

## Exemplo completo

```kof
var path = Path("data/users.txt")
path.parent().createDirectories()
path.writeText("Mel\nKof\n")
var text = path.readText()
println(text)
println(path.size())
```

## Erros e encoding

- Texto: UTF-8 sempre.
- Leituras com falha: `null` (JVM). Native: `readText` de arquivo
  inexistente encerra com erro — use `exists()` antes.
- Booleanas: `true`/`false`. `size()`: `-1` quando não existe.

## Referência

- [learn/34-file-system.md](../../learn/34-file-system.md)
- Testes: `kof-compiler/src/test/java/dev/kof/compiler/IoE2ETest.java`