# Empacotamento (Packaging)

Como os artefatos oficiais do Kof são produzidos, nomeados e verificados.

---

## 1. Script

```bash
scripts/package.sh [--jdk] [--output <dir>] [--skip-build]
```

| Opção | Efeito |
|-------|--------|
| `--jdk` | Baixa e embute o OpenJDK (Temurin 21) no pacote |
| `--output <dir>` | Diretório de saída (padrão: `dist/`) |
| `--skip-build` | Usa o jar já compilado sem rebuild |

O script:

1. lê a versão de `VERSION`;
2. compila `kof-cli-<versão>.jar` se necessário (`mvn package -DskipTests`);
3. monta o layout de distribuição;
4. opcionalmente embute o JDK;
5. gera o arquivo (`tar.gz` ou `zip`) e o `SHA256SUMS`.

## 2. Nomenclatura

```text
kof-<version>-<os>-<arch>.tar.gz   # Linux / macOS
kof-<version>-<os>-<arch>.zip      # Windows
```

Exemplos reais:

```text
kof-0.0.5-alpha-linux-x86_64.tar.gz
kof-0.0.5-alpha-windows-x86_64.zip
kof-0.0.5-alpha-macos-x86_64.tar.gz
kof-0.0.5-alpha-linux-aarch64.tar.gz   # planejado
kof-0.0.5-alpha-macos-aarch64.tar.gz   # planejado
```

## 3. Matriz de plataformas

| Plataforma | Estado |
|-----------|--------|
| Linux x86_64 | IMPLEMENTED (backend nativo e JVM testados) |
| Windows x86_64 | PACKAGING READY (CI gera o zip; execução JVM) |
| macOS x86_64 | PACKAGING READY (CI gera o tar.gz) |
| Linux ARM64 | PACKAGING ARCHITECTURE READY (empacotamento suporta; runtime nativo não validado) |
| macOS ARM64 | PACKAGING ARCHITECTURE READY (idem) |
| Windows ARM64 | PLANNED |

A arquitetura de empacotamento não exige mudanças estruturais para adicionar
um novo target: o script mapeia `os`/`arch` e o CI apenas adiciona a linha na
matriz.

## 4. Layout do pacote

```text
kof-<version>-<os>-<arch>/
├── bin/
│   ├── kof            # launcher Unix
│   └── kof.bat        # launcher Windows
├── lib/
│   └── kof.jar        # CLI + compiler + tooling (autocontido)
├── jdk/               # OpenJDK embutido (apenas com --jdk)
├── tooling/
├── editor/
│   └── kof.tmLanguage.json
├── docs/
└── VERSION
```

O `lib/kof.jar` é o jar *shaded* da CLI — contém compiler e tooling. Quando
o JDK embutido está presente, o launcher o usa automaticamente; sem JDK
embutido, o launcher usa `java` do PATH (somente builds de desenvolvimento).

## 5. Checksums

Cada build de pacote gera:

```text
SHA256SUMS
```

Verificação pelo usuário:

```bash
sha256sum -c SHA256SUMS
```

## 6. JDK embutido

`--jdk` baixa o OpenJDK Eclipse Temurin 21 (Tooling API Level) da API de
binários da Adoptium e o coloca em `jdk/`. Não é feito download em builds
locais sem `--jdk` para manter o ciclo rápido; a pipeline de release sempre
empacota com `--jdk`.

## 7. Verificação do artefato (CI)

Antes do release, o CI:

1. extrai o pacote em um diretório limpo;
2. executa `bin/kof version` e `bin/kof info`;
3. verifica a presença do JDK embutido (`jdk/bin/java`).

Nenhum artefato é publicado sem passar nessa validação.

## 8. Referências

- [ARCHITECTURE.md](ARCHITECTURE.md) — estrutura conceitual da distribuição
- [INSTALL.md](INSTALL.md) — instalação pelo usuário
- [RELEASES.md](RELEASES.md) — pipeline de release