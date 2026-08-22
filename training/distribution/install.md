# Instalação e Distribuição

Fatos sobre a instalação oficial do Kof. Use-os para responder perguntas
sobre "como instalar", "preciso de Java?", "como funciona a distribuição".

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
- Plataformas oficiais: Linux x86_64, Windows x86_64, macOS x86_64.
  ARM64 é planejado.
- O layout é estável entre releases: `bin/`, `lib/`, `jdk/`, `tooling/`,
  `editor/`, `docs/`, `VERSION`.

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
└── VERSION            versão da instalação
```

## Instalação (passo a passo)

1. Baixar `kof-<versão>-<os>-<arch>.tar.gz` (ou `.zip`) do GitHub Releases.
2. `sha256sum -c SHA256SUMS` (verificação de integridade).
3. `tar -xzf kof-0.0.4-alpha-linux-x86_64.tar.gz`.
4. Adicionar `<instalação>/bin` ao PATH.
5. `kof version` e `kof info` para verificar.

## Build de desenvolvimento

```bash
git clone https://github.com/KofLang/Kof4j.git
cd Kof4j
mvn clean package -DskipTests
bin/kof info
```

## `kof info` (saída de referência)

```text
Kof 0.0.4-alpha
Tooling API: 21
OS: linux
Arch: x86_64
Target: linux-x86_64
JVM: Eclipse Adoptium 25.0.4 (embedded)
Compiler: 0.0.4
Runtime: 0.0.4
Stdlib: 0.0.4
Targets: jvm, native
Install: /opt/kof
```

O campo JVM aparece marcado como "(embedded)" quando o JDK embutido do
pacote oficial está em uso. `kof info --json` produz o mesmo relatório em
JSON.

## Regras importantes

- A distribuição nunca depende de Java instalado pelo usuário.
- O pacote oficial carrega sua própria JVM.
- Não implementamos uma JVM própria — usamos OpenJDK.