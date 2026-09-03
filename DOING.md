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
| **NATIVE002** — validação 13/13 + observability real (riscv64/aarch64) | `EM CURSO` | agente-nativo-val | main | `NativeBackend.java` (`RISCV_RUNTIME_ASM` + `translateRiscvToAarch64`) | ✅ b20aa49 (qemu OK); stubs 79e101a; prepared MySQL fechado (4ce1f25) |

## Concluídos recentemente

| Gap/Item | Estado | Dono | Data | Prova |
|---|---|---|---|---|
| **HTTP002** — `kof.http` no Native | `FEITO` | agente-planning | 03/09 | `NativeHttpRuntime.java` (novo, ≤500): parse URL, IPv4, socket/connect, request/read status/body; `KofHttpE2ETest` 6/6 (get/post/status contra server Kof real) |
| **MySQL Native prepared** — COM_STMT_PREPARE/EXECUTE binário | `FEITO` | agente-nativo-val | 03/09 | `4ce1f25` — `NativeDbPrepared.java`; `KofDbE2ETest` 11/11 |
| **NATIVE002 core** — riscv64 + aarch64 13/13 | `FEITO` | outro agente | 02–03/09 | `3fbc29a`, `ac6c598` — asm puro via `translateRiscvToAarch64` |
| **TIME001** — time.interval/cancel no JS | `FEITO` | agente-planning | 03/09 | `c1db297` — fila cooperativa `kofTimeJobs` bombeada por `kofTimeSleep`; `KofTimeE2ETest` 5/5 |
| **LOG001** — kof.log no JS | `FEITO` | agente-planning | 01/09 | `console.*` + `KOF_LOG_LEVEL` |
| Spans W3C / lifecycle `application{}` / `kof deps` | `FEITO` | agente-planning | 01/09 | `97109c1`, `eb108ec`, `dfce911` |
| **NATIVE002.1/2/3** — validation 13/13 + observability real (riscv64/aarch64) | `FEITO` | agente-nativo-val | 03/09 | `b20aa49` + `79e101a` |

## Abertos (não reclamados — livres para pegar)

| Gap/Item | Prioridade | Escopo | Notas |
|---|---|---|---|
| **GC mark-sweep** Native | alta | `kof_gc_sweep` + auto-collect + E2E | mark já existe; só falta sweep. `status.md` Bugs #8 |
| **HTTP002** parcial restante | média | `delete/put/patch/options` + headers + `timeout/retry/circuit` Native | após get/post/status fecharem |
| **WEB002** — kof.web no Native | média | server HTTP/1.1 listen/accept sobre `kof_net_*` | depois de HTTP002 |
| **CONC003** — JS async real | média | Promises/event-loop GraalJS | design primeiro |
| **MEDIA001/2/3** | baixa | paridade media Native/JS | gaps documentados |
| **SECPQ** | baixa | PQC via liboqs FFI | Tier 9 |
| **MySQL query binário** (resultset de EXECUTE) | média | `kof_db_mysql_read_binary_rows` (parse de lenenc + types) | resultset de COM_STMT_EXECUTE é binário; hoje fica em substituição client-side via `COM_QUERY` — gap documentado |
| Portar stdlib p/ riscv64/aarch64 (web/db/mq/cache/time/log/config/observability) | média | `translateRiscvToAarch64` já existe | NATIVE002 paridade avançada — em curso por agente-nativo-val (validation+observability ✅ `b20aa49`) |
| Debugger DWARF variáveis/expressões + VS Code ext | baixa | `kof.debug` | |
| OpenTelemetry export | baixa | spans já feitos; falta OTLP export | |

## Regras de convivência (já em AGENTS.md)

- **≤500 linhas por classe** (refactor futuro de NativeRuntime: módulo novo por área, ex: `NativeHttpRuntime.java`).
- Nunca duas frentes no mesmo arquivo gigante ao mesmo tempo — se for inevitável, combine no chat antes.
- **Congelamento de comportamento** (AGENTS.md, obrigatório): zero regressão (suíte **840** é gate de merge), features novas **aditivas** (retrocompatibilidade), refactor de 500 linhas preserva semântica (mesma suíte + golden E2E; output mudou = bug do refactor), bugs em `docs/known-bugs.md` são corrigidos **no código** para atingir o comportamento previsto (nunca "documentar em volta"), paridade JVM/Native/JS é regra.

## Frentes de validação/docs (não são gaps de feature — avisar antes de mexer)

| Frente | Estado | Dono | Branch | Arquivos | Notas |
|---|---|---|---|---|---|
| **Bug-hunt + `known-bugs.md`** | `EM CURSO` | agente-idiomatic | idiomatic-fixes | `docs/known-bugs.md`, `docs/status.md` | **13/25 bugs corrigidos 03/09** (1,2,3,4,5,6,7,10,13,14,22,24,25 — todos com teste de regressão). Restantes: 8,9,11,12,15,16,17,18,19,20,21,23. Corrigir bug = reivindicar aqui e fix no código, não no corpus. |
| **Auditoria idiomática de docs/training** | `EM CURSO` | agente-idiomatic | idiomatic-fixes | `learn/`, `training/`, `docs/` | Revisar corpus contra o compilador (fake idioms, casos obsoletos). |
