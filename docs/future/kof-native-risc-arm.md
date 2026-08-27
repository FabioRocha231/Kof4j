# Kof Native — RISC-V 64 e ARM64 (AArch64) — Plano Futuro

> **Status:** `PLANNED` — documento de planejamento, sem código ainda.
> **Versão:** 0.1.2-beta `6ee25e8` · **Data:** 2026-08-27
> **Escopo:** expandir `Target.NATIVE` hoje `x86_64` (`as/ld` `kof_alloc` `kof_instanceof`) para `riscv64` e `aarch64` Linux, preservando `frontend → Kof IR → backend` e paridade `JVM/Native/JS`.

## 1. Objetivo

Levar o `NativeBackend` de `x86_64` único para multi-arch Linux sem quebrar `KofPatternMatchingTest` 10/10 (`switch String s` / `instanceof` / `as` / `checkcast`).

Não inclui macOS/Windows, GC avançado ou `kof.web` nativo completo — esses ficam em `kof-native-macos.md`.

## 2. Estado Atual (x86_64)

```
Kof → CompilerDriver (lower switch pattern: #switch + instanceof + 0 + CJ EQ)
  → JvmBackend (INSTANCEOF/CHECKCAST java/lang/String)
  → NativeBackend x86_64 (emitBinary emitConditionalJump KofCall vtable 8(%rax))
  → JsBackend (typeof === "string")
```

- `NativeBackend.java:728` `emitBinary` `addq/subq/imulq/idivq %rcx,%rax` `pushq`
- `NativeBackend.java:766` `cmpq %rax,%rcx; je/jne/jl`
- `NativeBackend.java:782` `vcall: mov 8(%rax) → %rbx; call *%rbx`
- `NativeRuntime` `kof_alloc(kof_heap)` `kof_instanceof(rdi=obj, esi=typeId)` `kof_string_*` `HEADER 24B`
- `ld -dynamic-linker /lib64/ld-linux-x86-64.so.2 -lc`

Todo `Target.NATIVE` hoje assume `RIP`-relative, `rdi/rsi/rdx/rcx/r8/r9`, `stack 8`.

## 3. Arquitetura Alvo

```
Target enum:
  JVM, JS, NATIVE_X86_64, NATIVE_AARCH64, NATIVE_RISCV64, ANDROID
  (NATIVE = alias NATIVE_X86_64 para compat)

IRModule → Backend.select:
  NATIVE_X86_64 → X64Backend
  NATIVE_AARCH64 → Aarch64Backend
  NATIVE_RISCV64 → Riscv64Backend
  (extrair interface comum NativeBase: ClassLayout, kof_alloc, mangle)
```

`kof build --target native --arch riscv64|arm64|x64` (default `x64`).

## 4. Mapeamento por Arch

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
| **Cmp/Jmp** | `cmpq %rax,%rcx; je L; jmp M` | `cmp x1,x0; b.eq L; b M` | `sub t0,a0,a1; beqz t0,L; j M` (`bne/bge` etc) |
| **String header** | `24B [typeId@0][vtable@8][len@16]` | idem | idem |
| **Syscall exit** | `mov $60,%rax; xor %rdi,%rdi; syscall` | `mov x8,#93; mov x0,#0; svc #0` | `li a7,93; li a0,0; ecall` |

`kof_alloc`/`kof_instanceof`/`kof_string_*` em `native/kof_runtime_{x64,aarch64,riscv64}.s` com `KOF_STRING_TYPE_ID=1` constante.

## 5. Mudanças Necessárias

1. **Enum Target + CLI** `kof-compiler/Target.java` + `kof-cli/Main.java` `--arch`.
2. **Extrair `NativeBase`** `getLayoutForType/sanitize/mangle/resolveFieldOffset` comum; `X64Backend` herda atual; criar `Aarch64Backend`/`Riscv64Backend` com `emit*` para seus `ISA`.
3. **Runtime multi-arch** `native/src/kof_alloc_{arch}.s` + `ld` seleção por arch; `kof build` escolhe `as/ld` via `PATH` ou `KOF_AS`.
4. **Testes** `KofPatternMatchingTest` já cobre `switch String`/`instanceof`/`as`; adicionar `NativeAarch64E2ETest` `qemu-aarch64` e `NativeRiscv64E2ETest` `qemu-riscv64` (skip se `qemu`/`cross as` ausente, como `NativeE2ETest` hoje).
5. **CI** `toolchains: x86_64` sempre, `aarch64/riscv64` `if: cross-available`.

## 6. Ordem de Implementação (incremental, sem quebrar x86_64)

1. `Target` + `CLI --arch` + `NativeBase` extração (nenhum `emit` muda, `NATIVE==X86_64` alias) — validar `190/0` `CompilerDriverTest`.
2. `Aarch64Backend` mínimo: `String`/`println`/`instanceof String` + `switch String s` + `checkcast` no-op — `qemu-aarch64` `hello`.
3. `Riscv64Backend` idem.
4. `List/Map/Set` `kof_list_*` e `kof_instanceof` para classes `Dummy` (usado em `KofPatternMatchingTest` default).
5. Documentar `docs/backend-parity.md` `NATIVE_X86_64 / AARCH64 / RISCV64` colunas.

## 7. Riscos e Mitigação

- **Stack ABI 16-byte** ARM/RISC-V exige `sp` alinhado → usar `str/ld` pareado 16.
- **Reloc `RIP` vs `PC-relative`**: x64 `leaq sym(%rip)` → ARM `adrp`+`add` / RISC-V `auipc`+`ld`.
- **Cross toolchain ausente** → `assume` skip, não falha `mvn test`.
- **QEMU lento** → `NativeE2ETest` só `x64` rápido, `aarch64/riscv64` em `@Tag("slow")`.

## 8. Fora de Escopo (futuro)

- `GC`, `float/double` `Native` (`F2D`), `kof.web` `listen` nativo, `macOS Mach-O`/`Windows PE`.

## 9. Critério de Pronto

`var x:Object="hello"; switch(x){case String s: println(s)}` compila e roda idêntico em `x64` `aarch64 (qemu)` `riscv64 (qemu)` e `JS` `typeof==="string"`; `mvn test -Dtest=KofPatternMatchingTest -Darch=all` `10/10` por arch.

*Documento criado em `docs/future/` para não bloquear `KofScript MVP` a seguir.*
