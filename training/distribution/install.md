# Instalação e Distribuição

Fatos sobre a instalação oficial do Kof. Use-os para responder perguntas
sobre "como instalar", "preciso de Java?", "como funciona a distribuição".

**Version:** 0.2.0-beta (27 Aug 2026)

## Fatos

- O Kof é distribuído como uma plataforma autocontida, não apenas um JAR.
- O pacote oficial contém: compiler, CLI, runtime, stdlib, tooling, editor
  support, OpenJDK embutido e documentação.
- O usuário NÃO precisa instalar Java, configurar JAVA_HOME ou usar SDKMAN.
- O OpenJDK embutido é Temurin 21 (Tooling API Level 21).
- O launcher é `bin/kof` (Unix) ou `bin/kof.bat` (Windows); ele localiza o
  JDK embutido em `jdk/` e, em builds de desenvolvimento sem JDK embutido,
  usa `java` do PATH.
- Artefatos: `kof-<versão>-<os>-<arch>.tar.gz` (Linux/macOS) ou `.zip`
  (Windows), acompanhados de `SHA256SUMS`.
- Plataformas oficiais: Linux x86_64, Windows x86_64, macOS x86_64, plus
  `native.risc`/`native.arm` placeholder (riscv64/aarch64 via qemu, 0.2.0-beta).
- O layout é estável entre releases: `bin/`, `lib/`, `jdk/`, `tooling/`,
  `editor/`, `docs/`, `VERSION`.
- Release 0.2.0-beta usa **single job** para package+release (fix 27/08, sem artifact loss).

## Estrutura do pacote

```text
kof/
├── bin/kof            launcher (Unix)
├── bin/kof.bat        launcher (Windows)
├── lib/kof.jar        CLI + compiler + tooling (shaded)
├── jdk/               OpenJDK embutido (pacote oficial)
├── tooling/           convenções de consumo por editores
├── editor/            grammar TextMate oficial
├── docs/              documentação compacta
└── VERSION            versão da instalação (0.2.0-beta)
```

## Instalação (passo a passo)

1. Baixar `kof-0.2.0-beta-<os>-<arch>.tar.gz` (ou `.zip`) do GitHub Releases.
2. `sha256sum -c SHA256SUMS` (verificação de integridade).
3. `tar -xzf kof-0.2.0-beta-linux-x86_64.tar.gz`.
4. Adicionar `<instalação>/bin` ao PATH.
5. `kof version` e `kof info` para verificar (deve mostrar `0.2.0-beta`).

## Build de desenvolvimento

```bash
git clone https://github.com/KofLang/Kof4j.git
cd Kof4j
mvn clean package -DskipTests   # 658 testes em mvn test
bin/kof info
bin/kof version                 # 0.2.0-beta
```

## `kof info` (saída de referência — 0.2.0-beta)

```text
Kof 0.2.0-beta
Tooling API: 21
OS: linux
Arch: x86_64
Target: linux-x86_64
JVM: Eclipse Adoptium 21.0.x (embedded)
Compiler: 0.2.0
Runtime: 0.2.0
Stdlib: 0.2.0
Targets: jvm, native, native.risc, native.arm, js, kofc
Install: /opt/kof
```

O campo JVM aparece marcado como "(embedded)" quando o JDK embutido do
pacote oficial está em uso. `kof info --json` produz o mesmo relatório em
JSON.

Targets disponíveis: `jvm`, `native` (x86_64 free-list GC), `native.risc`, `native.arm` (placeholder), `js` (kof.http via Java HttpClient), `kofc` (C subset nativo-only).

## Regras importantes

- A distribuição nunca depende de Java instalado pelo usuário.
- O pacote oficial carrega sua própria JVM.
- Não implementamos uma JVM própria — usamos OpenJDK.
- Single job para package+release desde 27/08.
