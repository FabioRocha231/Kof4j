# Pipeline de Release

Cada commit na `main` representa um estado publicável. A pipeline garante
que a `main` nunca aponte para um estado que não compila.

```text
commit na main
      ↓
CI
      ↓
testes (mvn clean test — gate obrigatório)
      ↓
version bump (scripts/bump-version.sh)
      ↓
package multiplataforma (scripts/package.sh --jdk)
      ↓
artifact validation
      ↓
GitHub Release + changelog + SHA256SUMS
```

---

## Workflows

### `.github/workflows/ci.yml` — Pull Requests e branches

Executa:

1. Verificação de que `VERSION` e `pom.xml` concordam
   (`scripts/bump-version.sh` + `git diff --exit-code`);
2. `mvn clean test` (todos os testes E2E JVM + Native);
3. `mvn clean package`.

### `.github/workflows/release.yml` — push na `main`

1. **test-and-bump** (Ubuntu):
   - `mvn clean test` — **gate**: nenhuma release é publicada com teste quebrado;
   - lê `VERSION` (ex.: `0.0.5-alpha`), calcula a próxima (`0.0.5-alpha`),
     roda `scripts/bump-version.sh`;
   - insere a seção do changelog no `CHANGELOG.md`;
   - commita e faz push do bump (`[skip ci]` para não re-disparar a pipeline).
2. **package** (matrix Linux x86_64 / Windows x86_64 / macOS x86_64):
   - `mvn clean package`;
   - `scripts/package.sh --jdk` (embute Temurin 21 — Tooling API baseline);
   - valida o artefato: extrai, roda `bin/kof version`, `bin/kof info` e
     verifica o JDK embutido;
   - publica os artefatos + `SHA256SUMS`.
3. **release** (Ubuntu):
   - gera as notas a partir do changelog;
   - combina todos os checksums em um `SHA256SUMS`;
   - cria o GitHub Release `kof-<versão>` com artefatos e checksums.

---

## Regras

- O release **só** acontece se `mvn clean test` e `mvn clean package`
  passarem.
- Nunca publicar uma release quebrada.
- O bump é commitado com `[skip ci]` para evitar loop de releases.
- Tags seguem `kof-<versão>` (ex.: `kof-0.0.5-alpha`).

---

## Artefatos

```text
kof-0.0.5-alpha-linux-x86_64.tar.gz
kof-0.0.5-alpha-windows-x86_64.zip
kof-0.0.5-alpha-macos-x86_64.tar.gz
SHA256SUMS
```

Cada pacote contém: compiler, CLI, runtime, stdlib, tooling, editor support
e JDK embutido.

## Changelog

`CHANGELOG.md` é atualizado pela pipeline via `scripts/changelog.sh`, que
agrupa os commits desde o último tag pela convenção:

```text
feat:  fix:  docs:  refactor:  test:  build:  tooling:
```

## Execução manual

```bash
# Local
scripts/bump-version.sh            # sincroniza VERSION → pom/properties
mvn clean test
scripts/package.sh                 # pacote local sem JDK
scripts/package.sh --jdk           # pacote oficial com JDK embutido
scripts/changelog.sh               # seção do changelog no stdout

# GitHub
# Release manual: GitHub → Actions → Release → Run workflow
```