# 31 — Distribuição

## Kof é uma plataforma, não apenas um JAR

A partir do 0.0.4, o Kof se comporta como uma linguagem distribuível:

```text
Kof 0.0.4-alpha
        ├── Compiler
        ├── CLI
        ├── Runtime
        ├── Standard Library
        ├── Tooling
        ├── Language Server / editor support
        ├── Embedded OpenJDK
        └── documentação
```

O usuário instala o Kof e recebe tudo o que precisa — **sem instalar Java
separadamente**.

## Estrutura do pacote

```text
kof/
├── bin/
│   ├── kof          # launcher (Unix)
│   └── kof.bat      # launcher (Windows)
├── lib/
│   └── kof.jar      # CLI + compiler + tooling (autocontido)
├── jdk/             # OpenJDK embutido (pacote oficial)
├── tooling/         # definições consumidas por editores
├── editor/          # grammar TextMate oficial
├── docs/
└── VERSION
```

## JDK embutido

O Kof distribui seu próprio OpenJDK (Temurin 21 — alinhado ao Tooling API
Level). O launcher `bin/kof`:

1. localiza o JDK embutido em `jdk/`;
2. se existir, usa-o (sem depender de `JAVA_HOME`/`PATH`);
3. em builds de desenvolvimento, cai para `java` do sistema.

Verificação:

```bash
kof info
# JVM: Eclipse Temurin 21.0.x (embedded)
```

## Tooling API Level

O baseline de API Java do tooling é **21**:

- versões posteriores do OpenJDK podem ser usadas internamente quando
  apropriado (ex.: Virtual Threads com Java 25), sem virar requisito;
- o pacote oficial carrega sua própria JVM.

## Multi-target preservado

A distribuição não muda a arquitetura da linguagem:

```text
Kof Source → Frontend → Kof IR → JVM | Native | Script | KofJS
```

A linguagem é a mesma; o backend muda. Para nativo, o programador nunca
escreve `malloc`, `free` ou gerencia memória manualmente — o compilador/
runtime absorvem isso.

## Instalação

```bash
tar -xzf kof-0.0.4-alpha-linux-x86_64.tar.gz
export PATH="$PWD/kof-0.0.4-alpha-linux-x86_64/bin:$PATH"
kof info
```

Verificar integridade: `sha256sum -c SHA256SUMS`.

## Referências

- [docs/distribution/ARCHITECTURE.md](../docs/distribution/ARCHITECTURE.md)
- [docs/distribution/INSTALL.md](../docs/distribution/INSTALL.md)