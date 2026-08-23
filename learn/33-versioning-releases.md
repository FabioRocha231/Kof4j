# 33 — Versionamento e Releases

## Formato

```text
MAJOR.MINOR.PATCH
```

- `X` — Major release
- `Y` — Major fix / evolução significativa
- `Z` — Bugfix (**o pontinho da vergonha**)

O PATCH é o *pontinho da vergonha*: bugfixes, correções, regressões e
pequenos ajustes sem mudança arquitetural relevante.

## Estágio Alpha

O Kof está em `0.0.x` (Alpha). Cada release carrega o sufixo:

```text
0.0.5-alpha
```

Nada é chamado de stable. A evolução pretendida: Alpha → Beta → Release
Candidate → Stable.

## Fonte única de verdade

A versão vive em `VERSION` (raiz do repositório). `scripts/bump-version.sh`
sincroniza:

```text
VERSION → pom.xml (<revision>) → dev/kof/version.properties (empacotado)
```

Nunca edite versões espalhadas por arquivos — a pipeline cuida disso.

## Release automático

Cada commit na `main` gera a próxima versão Alpha:

```text
commit → CI → testes → version bump → package → GitHub Release
```

- a `main` nunca aponta para um estado que não compila;
- release quebrada nunca é publicada;
- artefatos: `kof-<versão>-<os>-<arch>.tar.gz`/`.zip` + `SHA256SUMS`;
- changelog gerado por `scripts/changelog.sh` a partir da convenção de
  commits (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `build:`,
  `tooling:`).

## Referências

- [docs/distribution/VERSIONING.md](../docs/distribution/VERSIONING.md)
- [docs/distribution/RELEASES.md](../docs/distribution/RELEASES.md)