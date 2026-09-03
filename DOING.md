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
| **NATIVE002 residual** — MySQL query binário (resultset de EXECUTE) | `EM CURSO` | agente-nativo-val | main | `NativeDbPrepared.java` + wire | valid/observ FEITO 03/09; prepared FEITO 4ce1f25; segue com exec binário |
| **WEB002** — `kof.web` server no Native | `EM CURSO` | agente-planning | planning-future | `NativeWebRuntime.java` (novo, ≤500) + `KofWeb.java` + `KofWebE2ETest.java` | server HTTP/1.1 asm: listen/accept, parse METHOD+PATH, rota por literal, handler via trampolim para lambda; MVP: `app.get("/x"){\ return "hi" }` |

## Concluídos recentemente

| Gap/Item | Estado | Dono | Data | Prova |
|---|---|---|---|---|
| **GC mark-sweep** Native | `FEITO` | agente-planning | 03/09 | `461ec3b` — sweep real funciona; auto-collect fica desligado (safe-points fora do escopo) |
| **HTTP002** — `kof.http` no Native | `FEITO` | agente-planning | 03/09 | `71d27f2` — `NativeHttpRuntime.java` (novo, ≤500): parse URL, IPv4, socket/connect, request/read body/status; `KofHttpE2ETest` 6/6 (get/post/status com server Kof real) |
| **MySQL Native prepared** — COM_STMT_PREPARE/EXECUTE binário | `FEITO` | agente-nativo-val | 03/09 | `4ce1f25` — `NativeDbPrepared.java`; `KofDbE2ETest` 11/11 |
| **NATIVE002 core** — riscv64 + aarch64 13/13 | `FEITO` | outro agente | 02–03/09 | `3fbc29a`, `ac6c598` — asm puro via `translateRiscvToAarch64` |
| **TIME001** — time.interval/cancel no JS | `FEITO` | agente-planning | 03/09 | `c1db297` — fila cooperativa `kofTimeJobs` bombeada por `kofTimeSleep`; `KofTimeE2ETest` 5/5 |
| **LOG001** — kof.log no JS | `FEITO` | agente-planning | 01/09 | `console.*` + `KOF_LOG_LEVEL` |
| Spans W3C / lifecycle `application{}` / `kof deps` | `FEITO` | agente-planning | 01/09 | `97109c1`, `eb108ec`, `dfce911` |

## Abertos (livres pra pegar)

| Gap/Item | Prioridade | Escopo | Notas |
|---|---|---|---|
| **GC mark-sweep** Native | ~~alta~~ | ✅ fechado 03/09 | ver Concluídos |
| **HTTP002 restante** | média | `delete/put/patch/options`/`status` + `timeout/retry/circuit` (knobs no Native) | get/post/status fecham a linha zero — resto é cauda |
| **WEB002** — kof.web no Native | alta | server HTTP/1.1 sobre `kof_net_*` + dispatch com trampolim | depois de HTTP002 |
| **CONC003** — JS async real | média | event-loop real sobre Promises no GraalJS | JS sequencial já funciona; evolução futura |
| **MEDIA001/2/3** | baixa | paridade media Native/JS | gap documentado |
| **SECPQ** | baixa | PQC via liboqs FFI | Tier 9 (futuro) |
| **MySQL query binário** (resultset de EXECUTE) | ~~média~~ | `kof_db_mysql_prep_query` | `FEITO` 03/09 — `02b9ddb`: coldefs+tipos capturados no PREPARE; rows binárias (marker 0x00, null-bitmap +2, valores crus por coltype); `KofDbE2ETest.nativeMysqlPreparedBinary`; suíte 822/0. Fallback COM_QUERY substituição se PREPARE falhar |
| Portar stdlib riscv64/aarch64 (web/db/mq/cache/time/log/config/observability) | média | `translateRiscvToAarch64` existe | agente-nativo-val (validation/observability ✅ b20aa49+79e101a) |
| Debugger DWARF variáveis/expressões + VS Code ext | baixa | `kof.debug` | |
| OpenTelemetry export | baixa | spans feitos; falta OTLP export | |

## Regras de convivência (já em AGENTS.md)

- **≤500 linhas por classe** (refactor futuro de NativeRuntime: módulo novo por área, ex: `NativeHttpRuntime.java`).
- Nunca duas frentes no mesmo arquivo gigante ao mesmo tempo — se for inevitável, combine no chat antes.
- **Congelamento de comportamento** (AGENTS.md, obrigatório): zero regressão (suíte **840** é gate de merge), features novas **aditivas** (retrocompatibilidade), refactor de 500 linhas preserva semântica (mesma suíte + golden E2E; output mudou = bug do refactor), bugs em `docs/known-bugs.md` são corrigidos **no código** para atingir o comportamento previsto (nunca "documentar em volta"), paridade JVM/Native/JS é regra.

## Frentes de validação/docs (não são gaps de feature — avisar antes de mexer)

| Frente | Estado | Dono | Branch | Arquivos | Notas |
|---|---|---|---|---|---|
| **Bug-hunt + `known-bugs.md`** | `EM CURSO` | agente-idiomatic | idiomatic-fixes | `docs/known-bugs.md`, `docs/status.md` | **13/25 bugs corrigidos 03/09** (1,2,3,4,5,6,7,10,13,14,22,24,25 — todos com teste de regressão). Restantes: 8,9,11,12,15,16,17,18,19,20,21,23. Corrigir bug = reivindicar aqui e fix no código, não no corpus. |
| **Auditoria idiomática de docs/training** | `EM CURSO` | agente-idiomatic | idiomatic-fixes | `learn/`, `training/`, `docs/` | Revisar corpus contra o compilador (fake idioms, casos obsoletos). |
