# Kof Native — Multi-Arch (RISC-V 64 e ARM64/AArch64)

> **Status:** `EM DESENVOLVIMENTO (parcial)` — **riscv64 com codegen real (02/09)**; aarch64 pendente.
> **Versão:** 0.2.6-beta · **Data:** 2026-09-02
> **Gap:** `NATIVE002` (riscv64 parcial — caminho feliz; aarch64 e ops fora do caminho feliz pendentes).
> **Progresso 02/09:** toolchain cruzada + qemu + **codegen riscv64** (stack machine) —
> `NativeRiscv64E2ETest 4/4` (`qemu-riscv64`): println(String/Int), `var`, `if/else`,
> aritmética/comparações Int. Ver §2.3.
> **Decisão (02/09):** runtime por arch **em assembly puro**, no mesmo estilo do x86_64
> (`NativeRuntime.generateRuntimeAssembly`) — **sem C** ("Kof é Kof"; o `kof-c-compiler`
> é outra ferramenta, não um runtime). O C compilado com gcc cruzado que foi usado em
> 02/09 como validação de ABI foi descartado: o riscv64 passa a emitir runtime asm
> (bump allocator + syscalls/PLT libc) e linka com `ld -dynamic-linker ... -lc`,
> idêntico ao caminho x86_64.
> **Escopo:** expandir o `NativeBackend` (hoje `x86_64` em asm puro) para
> `riscv64` e `aarch64` Linux, preservando `frontend → Kof IR → backend` e
> paridade `JVM/Native/JS`. Este doc vive em `docs/` (não em `docs/future/`)
> porque **já há código em desenvolvimento** — ele documenta o estado real e
> como finalizar.

## 1. Objetivo

Levar o `NativeBackend` de `x86_64` único para multi-arch Linux sem quebrar
`KofPatternMatchingTest` 10/10 (`switch String s` / `instanceof` / `as` /
`checkcast`) e a suíte `NativeE2ETest`.

Não inclui macOS/Windows, GC avançado ou `kof.web` nativo completo (ver
"fora de escopo").

## 2. Estado Real (auditoria 01/09)

> **Regra desta pasta:** `docs/` documenta o que **está em desenvolvimento**;
> `docs/future/` só o que **é plano futuro** (zero código). Este item já tem
> código, por isso está aqui.

### 2.1 O que JÁ ESTÁ FEITO (plumbing)

| Peça | Estado | Onde |
|------|--------|------|
| Enum `Target.NATIVE_RISCV64` / `NATIVE_AARCH64` | ✅ | `Target.java` (valores distintos de `NATIVE`; `NATIVE` continua = `x86_64`) |
| `Target.isNative()` cobre os 3 nativos | ✅ | `Target.java` |
| `Target.nativeArch()` → `x86_64`/`riscv64`/`aarch64` | ✅ | `Target.java` |
| CLI `native.risc`/`native.riscv64`/`native.riscv` → `NATIVE_RISCV64` | ✅ | `Main.java:364` |
| CLI `native.arm`/`native.aarch64`/`native.aarch` → `NATIVE_AARCH64` | ✅ | `Main.java:365` |
| `kof build`/`run` aceitam `native.risc`/`native.arm` | ✅ | `status.md:13-14` |
| Dispatch `emit()` → `emitRiscv`/`emitAarch64` | ✅ | `NativeBackend.java:210-215` |
| Cross toolchain invocado (as/ld + dynamic-linker + `-lc`) | ✅ | `NativeBackend.emitRiscv`/`emitAarch64` |
| Fallback gracioso sem toolchain (`keeping asm`) | ✅ | idem (try/catch `IOException`) |

**Consequência prática:** `kof build --target native.risc` **compila e gera um
binário** (um stub que sai com `0`) — o pipeline de toolchain/cross-as/ld já
funciona de ponta a ponta.

### 2.2 O que AINDA NÃO ESTÁ FEITO (codegen — o gap real `NATIVE002`)

| Peça | Estado | Detalhe |
|------|--------|---------|
| **Lowering real riscv64 (caminho feliz)** | ✅ parcial 02/09 | `emitRiscv` emite o IR em asm: stack machine riscv64 (`s11`=fp locais, `s2`=pilha de operandos callee-saved, `ra`/`s2` preservados no frame) + `.macro pop`; `KofLoadLiteral`/`KofBinary`/`KofConditionalJump`/`KofCall(println, String.valueOf)`/`KofLoadLocal`/`KofStoreLocal`/`KofReturn`. `NativeRiscv64E2ETest 4/4` |
| **Lowering real aarch64** | ❌ STUB | `emitAarch64` gera só `_start` + `main: mov x0,#0; ret` — **não** emite o IR (mesma estratégia do riscv64 a portar) |
| Ops fora do caminho feliz riscv64 (coleções/classe/`instanceof`/`switch`/FP) | ❌ diagnóstico `NATIVE002` | ops desconhecidos emitem comentário `# NATIVE002: op fora do caminho feliz` (nunca binário mudo) |
| Os 18 métodos `emit*` reais (x86_64) | ✅ | `emitBinary`/`emitOperation`/`emitMethod`/`emitConditionalJump`/vcall… — o caminho completo continua só em x86_64 |
| Extração de `NativeBase` (layout/`kof_alloc`/mangle comum) | ❌ não existe | `NativeBackend` ainda é monolítico x86_64 |
| Runtime por arch (asm) | ⚠️ riscv64 happy-path | `kof_alloc`(bump)/`kof_memcpy`/`kof_string_from_literal`/`kof_int_to_string`/`kof_println_string` em **asm riscv64** (bump allocator em `.bss` + PLT `fwrite`/`snprintf`); `qemu-riscv64` (ver §2.3). aarch64 pendente |
| Testes E2E `qemu` (aarch64/riscv64) | ❌ não existem | nenhum `NativeAarch64E2ETest`/`NativeRiscv64E2ETest` |
| CI com cross toolchains | ❌ não existe | `aarch64/riscv64` não entram no pipeline |
| `backend-parity.md` colunas por arch | ⚠️ parcial | delta citado, colunas `NATIVE_X86_64/AARCH64/RISCV64` separadas pendentes |

**Consequência prática:** um programa real (com `println`, `instanceof`,
`switch`) em `native.risc`/`native.arm` **não executa a lógica** — sai `0` sem
efeto. O stub existe para validar o *encanamento*, não a codegen.

### 2.3 Runtime em assembly puro por arch (decisão 02/09)

**Não há runtime em C no Kof.** O nativo x86_64 é asm puro de ponta a ponta: o
runtime (`kof_alloc`, `kof_string_*`, `kof_instanceof`, …) é emitido em
assembly por `NativeRuntime.generateRuntimeAssembly()` e linkado com
`ld -dynamic-linker /lib64/ld-linux-x86-64.so.2 -lc` — a libc entra via PLT
(`printf`/`snprintf`), sem C compilado. (O módulo `kof-c-compiler` é outra
ferramenta — reimplementação do sectorC — e **não** é um runtime.)

Decisão para riscv64/aarch64: **mesmo caminho** — runtime emitido em asm
puro por arch + `ld -dynamic-linker /lib/ld-linux-<arch>.so.1 -lc`. Um C
compilado com gcc cruzado foi usado brevemente (02/09) apenas para validar a
ABI/estática no qemu; ele foi descartado da arquitetura.

Toolchain instalada (02/09, via `sudo apt`):
`binutils-riscv64-linux-gnu`, `binutils-aarch64-linux-gnu`, `qemu-user`,
`gcc-riscv64-linux-gnu`/`gcc-aarch64-linux-gnu` (debug),
`libc6-riscv64-cross`, `libc6-arm64-cross`.

Pipeline alvo (`emitRiscv`/`emitAarch64`):
```
Main.s  (programa: kof_main + seções .data/.rodata)
      + runtime asm riscv64/aarch64 (emitido pelo NativeBackend)
   └─ <arch>-as → <arch>-ld -dynamic-linker /lib/ld-linux-<arch>.so.1 -lc
   └─ qemu-<arch> → saída esperada (exit 0)
```

Detalhes do runtime riscv64 (inc-0, 02/09):
- alocação: **bump allocator** em `.bss` (sem `mmap` — evita problemas de
  qemu estático; o x86_64 usa `mmap`+free-list, e o riscv64 segue o modelo
  com bump até a paridade de GC).
- strings: layout **idêntico ao x86_64** — `[typeId@0 i32][super@4 i32]
  [vtable@8 ptr][len@16 i32][data@24 …]` (`KOF_STRING_TYPE_ID=1`).
- saída: PLT `fwrite`/`snprintf` (libc dinâmica), como no x86_64.
- validação: `NativeRiscv64E2ETest 4/4` via `qemu-riscv64`.

O que **restou** para os próximos incrementos:
- runtime riscv64 completo (objects/arrays/instanceof/strings completas/
  coleções/JSON/DB/HTTP/concorrência — paridade total com o x86_64).
- runtime + lowering completos do aarch64 (hoje stub).
- ops fora do caminho feliz riscv64 (coleções, classe, `instanceof`,
  `switch`) — mesmo escopo do §2.2.

## 3. Arquitetura (alvo)

> **Nota:** a implementação real divergiu do esboço original (que propunha
> renomear para `NATIVE_X86_64` + flag `--arch`). A decisão adotada foi **valores
> de enum distintos** (`NATIVE` = x86_64, `NATIVE_RISCV64`, `NATIVE_AARCH64`) +
> **nome de target no CLI** (`native.risc`/`native.arm`) — sem flag `--arch` e
> sem renomear `NATIVE` (mantém compat). Segue a decisão real.

```
Target enum:
  JVM, NATIVE (=x86_64), NATIVE_RISCV64, NATIVE_AARCH64, JS, ANDROID

IRModule → NativeBackend.emit (select por target):
  NATIVE          → lowering x86_64 (completo, 18 emit*)   [FEITO]
  NATIVE_RISCV64  → emitRiscv   (STUB — falta lowering)     [PENDENTE]
  NATIVE_AARCH64  → emitAarch64 (STUB — falta lowering)     [PENDENTE]
  → (meta) extrair NativeBase: ClassLayout, kof_alloc, mangle, resolveFieldOffset
```

`kof build --target native.risc|native.arm` (já funciona no dispatch).

## 4. Mapeamento por Arch (referência para o lowering)

| Aspecto | x86_64 (atual) | AArch64 | RISC-V 64 |
|---------|----------------|---------|-----------|
| **Assembler** | `as` GNU | `aarch64-linux-gnu-as` | `riscv64-linux-gnu-as` |
| **Linker** | `ld -dynamic-linker /lib64/ld-linux-x86-64.so.2 -lc` | `aarch64-linux-gnu-ld -dynamic-linker /lib/ld-linux-aarch64.so.1 -lc` | `riscv64-linux-gnu-ld -dynamic-linker /lib/ld-linux-riscv64-lp64d.so.1 -lc` |
| **Regs args** | `rdi rsi rdx rcx r8 r9` | `x0 x1 x2 x3 x4 x5` | `a0 a1 a2 a3 a4 a5` |
| **Regs temp** | `rax rcx rbx r10` | `x9 x10 x11 x12` | `t0 t1 t2 t3` |
| **Ret** | `rax` | `x0` | `a0` |
| **Stack** | `pushq %rax` | `str x0,[sp,#-16]!` | `addi sp,-16; sd a0,0(sp)` |
| **Call** | `call sym` | `bl sym` | `call sym`/`jal` |
| **Vcall** | `mov 8(%rax),%rbx; add $idx*8,%rbx; mov (%rbx),%rbx; call *%rbx` | `ldr x9,[x0,#8]; add x9,x9,#idx*8; ldr x9,[x9]; blr x9` | `ld t0,8(a0); addi t0,idx*8; ld t0,0(t0); jalr t0` |
| **Cmp/Jmp** | `cmpq %rax,%rcx; je L; jmp M` | `cmp x1,x0; b.eq L; b M` | `sub t0,a0,a1; beqz t0,L; j M` |
| **String header** | `24B [typeId@0][vtable@8][len@16]` | idem | idem |
| **Syscall exit** | `mov $60,%rax; xor %rdi,%rdi; syscall` | `mov x8,#93; mov x0,#0; svc #0` | `li a7,93; li a0,0; ecall` |

`kof_alloc`/`kof_instanceof`/`kof_string_*` por arch com `KOF_STRING_TYPE_ID=1`
constante.

## 5. Como Finalizar (passo a passo — reflete o plumbing que já existe)

> O encanamento (enum + CLI + dispatch + toolchain) **já está pronto**. O que
> falta é a codegen. Ordem incremental, sem quebrar `x86_64`:

1. **Extrair `NativeBase`** — tirar para uma classe/interface comum:
   `getLayoutForType`/`sanitize`/`mangle`/`resolveFieldOffset`/`collectStrings`
   (hoje em `NativeBackend`). `NativeBackend` (x86_64) herda e continua igual.
   → validar `mvn test -Dtest=CompilerDriverTest` (nenhum `emit` muda).
   *Depende de: nada. Não muda binário x86_64.*

2. **Runtime por arch** — mover `kof_alloc`/`kof_instanceof`/`kof_string_*`
   para asm por arch (hoje inline em `NativeRuntime` x86_64); o `emit` de cada
   target inclui a seção `.s` correta. *Depende de 1.*

3. **`Riscv64Backend` mínimo** — substituir o stub `emitRiscv` por lowering real
   do caminho feliz: `String`/`println`/`instanceof String` + `switch String s`
   + `checkcast` no-op, usando os mapeamentos da tabela §4.
   → `qemu-riscv64` rodando `hello` (teste `assume` se `qemu`/`riscv64-as`
   ausentes, como `NativeE2ETest`). *Depende de 1,2.*

4. **`Aarch64Backend` mínimo** — idem para aarch64 (`qemu-aarch64`). *Depende de 1,2.*

5. **Coleções + classes** — `kof_list_*`/`kof_map_*`/`kof_set_*` e
   `kof_instanceof` para classes de usuário (o `Dummy` usado em
   `KofPatternMatchingTest`). *Depende de 3,4.*

6. **Testes E2E multi-arch** — `NativeRiscv64E2ETest` + `NativeAarch64E2ETest`
   (`@Tag("slow")`, `assume` p/ `qemu`+cross-as ausentes) rodando a mesma fonte
   que `KofPatternMatchingTest` roda em x86_64/JS. *Depende de 5.*

7. **CI** — toolchains `x86_64` sempre; `aarch64`/`riscv64` com
   `if: cross-available` (não quebrar o pipeline quando a toolchain falta).
   *Depende de 6.*

8. **Docs** — `backend-parity.md`: colunas separadas
   `NATIVE_X86_64`/`AARCH64`/`RISCV64`; remover `NATIVE002` quando 6 verde.
   *Depende de 6.*

**Critério de pronto:** `var x:Object="hello"; switch(x){case String s: println(s)}`
compila e roda **idêntico** em `x86_64`, `aarch64 (qemu)`, `riscv64 (qemu)` e
`JS` (`typeof==="string"`); `KofPatternMatchingTest` 10/10 por arch.

## 6. Riscos e Mitigação

- **Stack ABI 16-byte** (ARM/RISC-V exigem `sp` alinhado) → usar pares
  `str/ld` de 16.
- **Reloc RIP vs PC-relative**: x64 `leaq sym(%rip)` → ARM `adrp`+`add` /
  RISC-V `auipc`+`ld`.
- **Cross toolchain ausente** → `assume` skip, não falhar `mvn test`.
- **QEMU lento** → `NativeE2ETest` só x86_64 rápido; aarch64/riscv64 em
  `@Tag("slow")`.
- **Divergência silenciosa**: stub atual "passa" gerando binário → garantir que
  o gap `NATIVE002` seja **diagnóstico claro** (não binário que silencia a
  lógica) até o lowering existir.

## 7. Fora de Escopo (ficam em `docs/future/` / outros docs)

- `GC` mark-sweep avançado, `float/double` no Native (`F2D`), `kof.web`
  `listen` nativo, `macOS` Mach-O / `Windows` PE.
