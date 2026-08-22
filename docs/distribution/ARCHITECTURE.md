# Arquitetura de Distribuição do Kof

**Versão:** 0.0.4-alpha

O Kof não é apenas um compilador — é uma plataforma distribuível. A partir
desta versão, o projeto trata a instalação como parte oficial do produto:

> **Kof deve parecer uma linguagem que você instala, não um projeto Java que você precisa montar.**

---

## 1. Modelo de Distribuição

O pacote oficial é autocontido. A instalação fornece:

```text
kof/
├── bin/
│   ├── kof          # launcher (Unix)
│   └── kof.bat      # launcher (Windows)
├── lib/
│   └── kof.jar      # CLI + compiler + tooling (shaded, self-contained)
├── jdk/             # OpenJDK embutido (apenas no pacote oficial com --jdk)
│   └── bin/java
├── tooling/         # definições e ferramentas consumidas por editores
├── editor/          # grammar TextMate oficial (source.kof)
├── docs/            # documentação compacta que viaja com a distribuição
└── VERSION          # versão da instalação
```

O usuário **não** precisa instalar Java, configurar `JAVA_HOME`, usar SDKMAN
ou ajustar o `PATH` manualmente. O launcher `bin/kof` resolve o JDK embutido
da própria instalação e, apenas em builds de desenvolvimento sem JDK
embutido, cai para um `java` do sistema.

### Estrutura de arquivos por artefato

```text
kof-<versão>-<os>-<arch>.tar.gz   # Linux / macOS
kof-<versão>-<os>-<arch>.zip      # Windows
```

Cada artefato acompanha um `SHA256SUMS` para verificação de integridade.

---

## 2. JDK Embutido (OpenJDK)

O backend JVM do Kof precisa de uma JVM para executar programas compilados.
Em vez de depender do ambiente do usuário, o pacote oficial **embarca um
OpenJDK compatível** (Eclipse Temurin 21, alinhado ao Tooling API Level).

Decisões:

- **Não** implementamos uma JVM própria — usamos OpenJDK.
- O pacote oficial traz o JDK em `jdk/` e o launcher o utiliza
  automaticamente.
- `kof run`, `kof build --target=jvm`, `kof serve` e `kof test` funcionam
  sem nenhuma instalação externa.
- Em pacotes construídos localmente sem `--jdk`, o launcher usa `java` do
  `PATH` (equivalente a um build de desenvolvimento).

O download do JDK é feito pela pipeline de release via
`scripts/package.sh --jdk` (API de binários da Adoptium). A verificação de
que o JDK embutido está sendo usado aparece em `kof info` (campo JVM marcado
como *embedded*).

---

## 3. Tooling API Level: 21

O tooling distribuído pelo Kof assume como baseline a **API Java 21**.

- APIs usadas pelo tooling são compatíveis com Java 21.
- O Kof não exige Java anterior a 21 para seu tooling.
- O pacote oficial carrega sua própria JVM (Temurin 21).
- Versões posteriores do OpenJDK (ex.: 25) podem ser usadas internamente
  quando apropriado, **sem** tornar essa versão um requisito obrigatório.

Esta decisão está documentada em [docs/tooling/README.md](../tooling/README.md)
e é reportada por `kof info` (`Tooling API: 21`).

---

## 4. Isolamento e Portabilidade

O layout de distribuição garante:

| Propriedade | Como |
|-------------|------|
| Isolamento | Nada é instalado fora do diretório do Kof |
| Portabilidade | Caminhos relativos entre `bin/`, `lib/` e `jdk/` |
| Atualização simples | Substituir o diretório de instalação (ou extrair por cima) |
| Versionamento | `VERSION` + arquivos de versão empacotados |
| Reprodução | Build determinístico via Maven + scripts de empacotamento |

---

## 5. Multi-target preservado

A distribuição não muda a arquitetura de compilação:

```text
Kof Source
    │
    ▼
Frontend
    │
    ▼
Kof IR
    ├──────────► JVM
    ├──────────► Native
    ├──────────► Script
    └──────────► KofJS
```

O código Kof não é reescrito quando o target muda — **a linguagem é a mesma,
o backend muda**. Especialmente para o target Native, a complexidade de
memória (`malloc`, `free`, ponteiros, gerenciamento manual) é absorvida pelo
compilador/runtime, nunca exposta ao programador.

---

## 6. Instalação

### Pacote oficial (recomendado)

```bash
# Linux / macOS
tar -xzf kof-0.0.4-alpha-linux-x86_64.tar.gz
export PATH="$PWD/kof-0.0.4-alpha-linux-x86_64/bin:$PATH"

# Windows
# extraia o zip e adicione kof-0.0.4-alpha-windows-x86_64\bin ao PATH
```

### Build de desenvolvimento

```bash
mvn clean package -DskipTests
bin/kof info
```

Verificar a integridade de um download:

```bash
sha256sum -c SHA256SUMS
```

---

## 7. Verificação

Depois de instalar:

```bash
kof version      # kof 0.0.4-alpha
kof info         # ambiente completo (JVM embutida aparece com "(embedded)")
kof run hello.kf
```