# 01 — Instalação

## Instalação oficial (recomendado)

O Kof é distribuído como uma plataforma autocontida. O pacote oficial
inclui compilador, CLI, runtime, stdlib, tooling e um **OpenJDK embutido** —
não é necessário instalar Java separadamente.

1. Baixe o artefato do GitHub Releases (ex.: `kof-0.0.4-alpha-linux-x86_64.tar.gz`).
2. Verifique a integridade:

   ```bash
   sha256sum -c SHA256SUMS
   ```

3. Extraia e adicione ao PATH:

   ```bash
   tar -xzf kof-0.0.4-alpha-linux-x86_64.tar.gz
   export PATH="$PWD/kof-0.0.4-alpha-linux-x86_64/bin:$PATH"
   ```

4. Verifique:

   ```bash
   kof version
   kof info
   ```

O `kof info` mostra a versão, Tooling API (21), target, JVM (embutida, quando
aplicável) e a localização da instalação.

## Build a partir do código-fonte (para desenvolvedores)

```bash
git clone https://github.com/KofLang/Kof4j.git
cd Kof4j
mvn clean package -DskipTests
bin/kof version
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
kof build src/ --target=native
```

## Rodando

```bash
kof run main.kf
```

Resultado:

```
Hello, World!
```

## Comandos da CLI

| Comando | Descrição |
|---------|-----------|
| `kof build <dir>` | Compila para JVM (padrão) |
| `kof build <dir> --target=native` | Compila para nativo |
| `kof run <arquivo>` | Compila e executa |
| `kof serve <arquivo>` | Web server HTTP básico |
| `kof check <arquivo\|dir>` | Type-check sem emitir código |
| `kof info [--json]` | Relatório do ambiente |
| `kof lsp` | Language Server (stdio) |
| `kof version` | Mostra a versão |

## Status atual

> **O compilador está funcional.** A CLI compila arquivos `.kf` que contenham
> records, classes, interfaces e funções. O backend JVM gera `.class`
> funcionais. O backend nativo gera ELF x86-64. A distribuição oficial é
> autocontida (JDK embutido, tooling e editor support).

## Referências

- [docs/distribution/INSTALL.md](../docs/distribution/INSTALL.md)
- [docs/distribution/ARCHITECTURE.md](../docs/distribution/ARCHITECTURE.md)

## Próximo passo

[Primeiro Programa →](02-first-program.md)