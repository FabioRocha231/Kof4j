# DOING.md — coordenação multi-agente (quem faz o quê)

> **Regra obrigatória para agentes (IA ou humano):**
> 1. **Antes** de começar qualquer trabalho de feature/gap: leia este arquivo.
> 2. Se o item que você quer atacar já tem **dono + estado `EM CURSO`**, não toque —
>    escolha outro ou pergunte. Nunca dois agentes no mesmo gap.
> 3. Ao **reivindicar** um item: edite este arquivo **no mesmo commit** que começa
>    o trabalho (dono, branch, arquivos que vai tocar).
> 4. A **cada commit**, atualize sua linha (estado, progresso, o que falta).
> 5. Ao **concluir**: mude para `FEITO` com data + commit + teste que prova, e
>    marque o gap no docs (`status.md`/`backend-parity.md`).
> 6. Itens abertos ficam em `EM CURSO` por no máx. uma sessão; ao abandonar,
>    volte para `ABERTO` com nota do que já funciona e o que falta.

Estados: `ABERTO` · `EM CURSO` · `FEITO` · `BLOQUEADO`.

---

## Em curso agora

| Gap/Item | Estado | Dono | Branch | Arquivos principais | Notas |
|---|---|---|---|---|---|
| **HTTP002** — `kof.http` no Native | `EM CURSO` | agente-planning | planning-future | `NativeHttpRuntime.java` (novo), `KofHttp.java`, `CompilerDriver.java`, `KofHttpE2ETest.java` | Cliente HTTP/1.1 sem TLS: parse URL, socket (reusa `kof_net_*`), request/response, status; segue padrão MySQL wire. **Escreve em módulo NOVO** (regra ≤500 linhas/classe — NativeRuntime já viola). http:// só (https = diagnóstico, sem TLS em asm). |

## Concluídos recentemente

| Gap/Item | Estado | Dono | Data | Prova |
|---|---|---|---|---|
| **TIME001** — time.interval/cancel no JS | `FEITO` | agente-planning | 03/09 | `c1db297` — fila cooperativa `kofTimeJobs` bombeada por `kofTimeSleep` (GraalJS sem `setInterval`); scheduler JS delega. `KofTimeE2ETest` 5/5 |
| **NATIVE002 core** — riscv64 + aarch64 13/13 | `FEITO` | outro agente | 02–03/09 | `3fbc29a`, `ac6c598` — asm puro via `translateRiscvToAarch64` |
| **LOG001** — kof.log no JS | `FEITO` | agente-planning | 01/09 | `console.*` + `KOF_LOG_LEVEL` |
| Spans W3C / lifecycle `application{}` / `kof deps` | `FEITO` | agente-planning | 01/09 | `97109c1`, `eb108ec`, `dfce911` |

## Abertos (não reclamados — livres para pegar)

| Gap/Item | Prioridade | Escopo | Notas |
|---|---|---|---|
| **GC mark-sweep** Native | alta | `kof_gc_sweep` (hoje stub `ret`) + auto-collect + E2E | mark já existe; só falta sweep. Doc: `status.md` Bugs #8 |
| **HTTP002** parcial restante | média | `delete`/`put`/`patch`/`options` + headers/body-idempotentes + `timeout/retry/circuit` Nativo | após get/post/status fecharem |
| **WEB002** — kof.web no Native | média | server HTTP/1.1 listen/accept sobre `kof_net_*` | depois de HTTP002 (mesmas primitivas) |
| **CONC003** — JS async real | média | Promises/event-loop GraalJS | design primeiro |
| **MEDIA001/2/3** | baixa | paridade media Native/JS | gaps documentados |
| **SECPQ** | baixa | PQC via liboqs FFI | Tier 9 |
| Portar stdlib p/ riscv64/aarch64 (web/db/mq/cache/time/log/config/observability) | média | `translateRiscvToAarch64` já existe | NATIVE002 paridade avançada — confirmar dono com outro agente antes |
| Debugger DWARF variáveis/expressões + VS Code ext | baixa | `kof.debug` | |
| OpenTelemetry export | baixa | spans já feitos; falta OTLP export | |

## Regras de convivência (já em AGENTS.md)

- **≤500 linhas por classe** (refactor futuro de NativeRuntime: módulo novo por área, ex: `NativeHttpRuntime.java`).
- Nunca duas frentes no mesmo arquivo gigante ao mesmo tempo — se for inevitável, combine no chat antes.
