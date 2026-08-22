# Distribuição do Kof — Instalação

Guia de instalação da plataforma Kof a partir dos artefatos oficiais.

---

## 1. Baixar

Os artefatos são publicados automaticamente no GitHub Releases a cada
commit na `main`:

```text
kof-<versão>-<os>-<arch>.tar.gz    # Linux / macOS
kof-<versão>-<os>-<arch>.zip       # Windows
SHA256SUMS                          # checksums de todos os artefatos
```

Plataformas:

| Plataforma | Artefato |
|-----------|----------|
| Linux x86_64 | `kof-0.0.4-alpha-linux-x86_64.tar.gz` |
| Windows x86_64 | `kof-0.0.4-alpha-windows-x86_64.zip` |
| macOS x86_64 | `kof-0.0.4-alpha-macos-x86_64.tar.gz` |

ARM64 (Linux/macOS/Windows) é planejado para versões futuras.

---

## 2. Verificar integridade

```bash
sha256sum -c SHA256SUMS
```

---

## 3. Instalar

### Linux / macOS

```bash
tar -xzf kof-0.0.4-alpha-linux-x86_64.tar.gz
export PATH="$PWD/kof-0.0.4-alpha-linux-x86_64/bin:$PATH"
kof version
```

### Windows

Extraia o zip e adicione `kof-0.0.4-alpha-windows-x86_64\bin` ao `PATH`.

```bat
kof version
```

---

## 4. Verificar a instalação

```bash
kof info
```

A saída deve mostrar a versão, o Tooling API, o target, a JVM embutida
(marcada como `(embedded)` quando o pacote oficial está em uso) e a
localização da instalação.

---

## 5. O que você recebe

- Compilador (backends JVM e Native)
- CLI (`build`, `run`, `serve`, `check`, `info`, `lsp`, `version`)
- Runtime + Standard Library (embutidas no artefato)
- OpenJDK embutido (pacote oficial)
- Tooling de editores (grammar oficial + LSP)
- Documentação

**Nenhuma instalação externa de Java é necessária.**

---

## 6. Atualizar

Substitua o diretório da instalação pela nova versão. O layout é estável
entre releases Alpha.

---

## 7. Build a partir do código-fonte

```bash
git clone https://github.com/KofLang/Kof4j.git
cd Kof4j
mvn clean package -DskipTests
bin/kof info
```