# Versionamento e Releases

Fatos sobre o modelo de versionamento e release do Kof. Use para responder
perguntas sobre versões, releases e o processo de publicação.

## Formato de versão

```text
MAJOR.MINOR.PATCH
```

- `X` — Major release.
- `Y` — Major fix / evolução significativa.
- `Z` — Bugfix — o "pontinho da vergonha" (correções, regressões, pequenos
  ajustes sem mudança arquitetural relevante).

## Estágio atual

- O Kof está em `0.0.x` (Alpha).
- O release carrega explicitamente o sufixo: `0.0.5-alpha`.
- Nada é chamado de stable.
- Evolução pretendida: Alpha → Beta → Release Candidate → Stable.
- A versão de componente (compiler/runtime/stdlib) é `0.0.4`; o sufixo
  `-alpha` pertence ao release.

## Fonte única de verdade

- A versão vive no arquivo `VERSION` na raiz do repositório.
- `scripts/bump-version.sh` sincroniza `VERSION` → `pom.xml` (`<revision>`)
  → `kof-compiler/src/main/resources/dev/kof/version.properties`.
- A pipeline atualiza automaticamente: compiler, CLI, runtime, artefatos,
  pacote, GitHub Release, changelog.
- Não editar versões manualmente em vários arquivos.

## Release automático (CI/CD)

Cada commit na `main`:

```text
commit → CI → testes → version bump → package → GitHub Release
```

- A `main` nunca aponta para um estado que não compila.
- O release só acontece se `mvn clean test` e `mvn clean package` passarem.
- Nunca publicar release quebrada.
- Workflow de PR: build + testes + verificações estáticas.
- Workflow de push na main: testes, bump (próxima versão Alpha), empacotamento
  multiplataforma, validação de artefato, changelog, GitHub Release.

## Artefatos

```text
kof-0.0.5-alpha-linux-x86_64.tar.gz
kof-0.0.5-alpha-windows-x86_64.zip
kof-0.0.5-alpha-macos-x86_64.tar.gz
SHA256SUMS
```

Cada pacote contém compiler, CLI, runtime, stdlib, tooling, editor support e
JDK embutido (pacote oficial).

## Changelog

- `CHANGELOG.md` mantido no repositório, com marcador `<!-- NEXT-RELEASE -->`
  onde a pipeline insere a próxima seção.
- `scripts/changelog.sh` agrupa commits desde o último tag pela convenção:
  `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `build:`, `tooling:`.

## Tags

- Tags seguem `kof-<versão>` (ex.: `kof-0.0.5-alpha`).
- O commit de bump usa `[skip ci]` para não re-disparar a pipeline.

## Regras importantes

- Enquanto Alpha: todo commit na main gera a próxima versão Alpha
  (incremento de PATCH).
- Verificação de consistência: CI compara `VERSION`, `pom.xml` e o resource
  empacotado.