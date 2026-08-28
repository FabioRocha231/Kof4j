# 01 — Instalação

> **Kof 0.2.0-beta — 27 ago 2026 — 658 testes**

## Instalação oficial (recomendado)

O Kof é distribuído como uma plataforma autocontida. O pacote oficial
inclui compilador, CLI, runtime, stdlib, tooling e um **OpenJDK embutido** —
não é necessário instalar Java separadamente.

1. Baixe o artefato do GitHub Releases (ex.: `kof-0.2.0-beta-linux-x86_64.tar.gz`).
2. Verifique a integridade:

   ```bash
   sha256sum -c SHA256SUMS
   ```

3. Extraia e adicione ao PATH:

   ```bash
   tar -xzf kof-0.2.0-beta-linux-x86_64.tar.gz
   export PATH="$PWD/kof-0.2.0-beta-linux-x86_64/bin:$PATH"
   ```

4. Verifique:

   ```bash
   kof version   # 0.2.0-beta
   kof info
   ```

O `kof info` mostra a versão (`0.2.0-beta`), Tooling API (21), target, JVM (embutida, quando
aplicável) e a localização da instalação. `Targets: jvm, native, native.risc, native.arm, js, kofc`.

## Build a partir do código-fonte (para desenvolvedores)

```bash
git clone https://github.com/KofLang/Kof4j.git
cd Kof4j
mvn clean package -DskipTests
bin/kof version   # 0.2.0-beta
```

Em builds de desenvolvimento, o launcher `bin/kof` usa o `java` do sistema
(JDK 21+). No pacote oficial, o JDK embutido é usado automaticamente.

## Criando seu primeiro projeto

Crie a estrutura:

```
meu-projeto/
├── src/
│   └── main.kf
```

No arquivo `main.kf`:

```kf
main() {
    println("Hello, World!")
}
```

## Compilando

```bash
kof build src/
```

Gera classes JVM na pasta de saída padrão (`build/classes`).

Para nativo (Linux x86-64):

```bash
kof build src/ --target=native        # x86-64
kof build src/ --target=native.risc   # riscv64 (via riscv64-linux-gnu-as/ld + qemu)
kof build src/ --target=native.arm    # aarch64 (via aarch64-linux-gnu-as/ld + qemu)
kof build src/ --target=js            # ES Modules (GraalJS)
```

## Rodando

```bash
kof run main.kf                       # jvm
kof run main.kf --target=native       # nativo x86-64
kof run main.kf --target=js           # js
kof script main.kf                    # KofScript direto (let/const → KofScriptGlobals)
kof script --repl                     # REPL
kof c hello.c --run                   # KofC: C subset → ELF nativo-only
```

Resultado:

```
Hello, World!
```

## Comandos da CLI (0.2.0-beta)

| Comando | Descrição |
|---------|-----------|
| `kof build <dir>` | Compila para JVM (padrão) |
| `kof build <dir> --target=native` | Compila para nativo x86-64 |
| `kof build <dir> --target=native.risc` | Compila para riscv64 |
| `kof build <dir> --target=native.arm` | Compila para aarch64 |
| `kof build <dir> --target=js` | Compila para ES Modules |
| `kof run <arquivo> [--target jvm|native|native.risc|native.arm|js]` | Compila e executa |
| `kof script <file.ks|kf> [--watch] [--target ...]` | KofScript direto + REPL (`--repl`) |
| `kof c <file.c> [--run] [--output <bin>]` | KofC C subset → ELF nativo-only |
| `kof serve <arquivo>` | Web server HTTP básico |
| `kof check <arquivo\|dir>` | Type-check sem emitir código |
| `kof test <arquivo\|dir> [--target ...]` | Testes |
| `kof info [--json]` | Relatório do ambiente |
| `kof lsp` | Language Server (stdio) |
| `kof version` | Mostra a versão (`0.2.0-beta`) |

> A cadeia `intention->Kof->frontend->IR->backend->runtime` vale para todos os targets — `kof build --target=...` só troca o backend.

## Status atual

> **O compilador está funcional — 0.2.0-beta, 658 testes.** A CLI compila `.kf` com
> records, classes, interfaces, funções, pattern matching (`case String s` + `Point(x,y)`), `String?`, `List map/filter/reduce`, `kof.http` (JVM+JS). O backend JVM gera `.class`
> funcionais. O backend nativo gera ELF x86-64/riscv64/aarch64 (free-list GC em x86-64). `KofScript` (`let`→`KofScriptGlobals`) e `KofC` são nativos do pipeline. A distribuição oficial é
> autocontida (JDK embutido, tooling e editor support).

## Referências

- [docs/distribution/INSTALL.md](../docs/distribution/INSTALL.md)
- [docs/distribution/ARCHITECTURE.md](../docs/distribution/ARCHITECTURE.md)

## Próximo passo

[Primeiro Programa →](02-first-program.md)
