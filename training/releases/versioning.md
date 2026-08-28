# Versionamento e Releases

Fatos sobre o modelo de versionamento e release do Kof. Use para responder
perguntas sobre versões, releases e o processo de publicação.

**Version:** 0.2.0-beta (27 Aug 2026) — 658 tests

## Formato de versão

```text
MAJOR.MINOR.PATCH[-suffix]
```

- `X` — Major release.
- `Y` — Major fix / evolução significativa.
- `Z` — Bugfix — o "pontinho da vergonha" (correções, regressões, pequenos
  ajustes sem mudança arquitetural relevante).
- `-beta` / `-alpha` / `-rc` — estágio.

## Estágio atual

- O Kof está em `0.2.0-beta` (27 Aug 2026, commit `b4339c8`).
- Evolução: `0.0.5-alpha` → `0.1.0` → `0.2.0-beta` → Beta → Release Candidate → Stable.
- A versão de componente (compiler/runtime/stdlib) é `0.2.0`; o sufixo
  `-beta` pertence ao release.
- Targets: `jvm` / `native` / `native.risc` / `native.arm` / `js` / `kofc` + `KofScript`.

## Fonte única de verdade

- A versão vive no arquivo `VERSION` na raiz do repositório (`0.2.0-beta`).
- `scripts/bump-version.sh` sincroniza `VERSION` → `pom.xml` (`<revision>`)
  → `kof-compiler/src/main/resources/dev/kof/version.properties` (`kof.version=0.2.0-beta`).
- A pipeline atualiza automaticamente: compiler, CLI, runtime, artefatos,
  pacote, GitHub Release, changelog.
- Não editar versões manualmente em vários arquivos.

## Release automático (CI/CD) — single job (fix 27/08)

Cada commit na `main`:

```text
commit → CI (mvn test 658) → package+release em single job (sem perda de artifact) → GitHub Release
```

- Fix 27/08: `package+release` agora em **single job** (evita artifact loss entre jobs separados).
- A `main` nunca aponta para um estado que não compila.
- O release só acontece se `mvn clean test` e `mvn clean package` passarem.
- Workflow de PR: build + testes + verificações estáticas.
- Workflow de push na main: testes, bump (próxima versão Beta), empacotamento
  multiplataforma (linux-x86_64, windows-x86_64, macos-x86_64 + risc/arm placeholder), validação de artefato, changelog, GitHub Release com JDK 21 no release job.

## Artefatos

```text
kof-0.2.0-beta-linux-x86_64.tar.gz
kof-0.2.0-beta-windows-x86_64.zip
kof-0.2.0-beta-macos-x86_64.tar.gz
kof-0.2.0-beta-linux-riscv64.tar.gz   # placeholder
kof-0.2.0-beta-linux-aarch64.tar.gz   # placeholder
SHA256SUMS
lib/kof.jar + kof-cli jars
```

Cada pacote contém compiler, CLI, runtime, stdlib, tooling, editor support e
JDK embutido (Temurin 21, Tooling API Level 21).

## Changelog

- `CHANGELOG.md` mantido no repositório, com marcador `<!-- NEXT-RELEASE -->`
  onde a pipeline insere a próxima seção.
- `scripts/changelog.sh` agrupa commits desde o último tag pela convenção:
  `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `build:`, `tooling:`.

## Tags

- Tags seguem `kof-<versão>` (ex.: `kof-0.2.0-beta`).
- O commit de bump usa `[skip ci]` para não re-disparar a pipeline.

## Regras importantes

- Em Beta: todo commit na main gera a próxima versão Beta.
- Verificação de consistência: CI compara `VERSION`, `pom.xml` e o resource
  empacotado (`mvn package` valida).
- Single job para package+release — não separar em jobs com upload/download artifacts.
