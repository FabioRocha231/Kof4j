package dev.kof.compiler;

/**
 * MySQL prepared statements no Native (x86_64) — COM_STMT_PREPARE +
 * COM_STMT_EXECUTE (protocolo binario) sobre o wire ja existente em
 * NativeRuntime (handshake + scramble + kof_net_*).
 *
 * Gap: docs/status.md "MySQL nativo completo" — binds em MySQL hoje usam
 * substituicao client-side em COM_QUERY; o binario real (PREPARE/EXECUTE)
 * faltava (tentativa 01/09 revertida — packet EXECUTE malformado; relancada
 * 03/09 com MySQL 8.0 real em 127.0.0.1:13306 p/ validar byte-a-byte).
 *
 * Cliente caps = 0x00088209 (sem CLIENT_DEPRECATE_EOF) → PREPARE OK seguido de
 * [num_params coldefs + EOF] e [num_columns coldefs + EOF] quando > 0.
 *
 * Convencao de valores: ate 4 binds em .Ldb_prep_args (8B cada). Valor &lt;
 * 0x1000000 e Int (MYSQL_TYPE_LONG, 4B LE); senao KofString* (VAR_STRING,
 * lenenc) — mesmo teste de kof_db_bind.
 */
final class NativeDbPrepared {

    private NativeDbPrepared() {}

    static void emitMysqlPrepared(StringBuilder sb) {
        sb.append("""
            # ====== MySQL prepared binario (COM_STMT_PREPARE / EXECUTE) ======
            .section .bss
            .align 8
            .Ldb_prep_args: .space 32            # 4 args x 8B
            .Ldb_prep_execbuf: .space 16384      # packet EXECUTE montado
            .section .text

            # kof_db_mysql_prepare(rdi=fd, rsi=sql KofString*) -> rax = stmt_id|0
            .globl kof_db_mysql_prepare
            .type kof_db_mysql_prepare, @function
            kof_db_mysql_prepare:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx               # fd
                movq %rsi, %r12               # sql
                testq %r12, %r12
                jz .Lprep_fail
                leaq .Ldb_mysql_buf(%rip), %r13
                movb $0x16, 4(%r13)           # COM_STMT_PREPARE
                leaq 24(%r12), %rsi
                movl 16(%r12), %ecx           # sqllen
                movq %rcx, %r14
                movq %rcx, %rdx
                leaq 5(%r13), %rdi
                call kof_memcpy
                leal 1(%r14d), %eax           # len = 1 + sqllen
                movb %al, 0(%r13)
                shrl $8, %eax
                movb %al, 1(%r13)
                shrl $8, %eax
                movb %al, 2(%r13)
                movb $0, 3(%r13)              # seq = 0
                movq %rbx, %rdi
                movq %r13, %rsi
                leaq 5(%r14), %rdx
                call kof_net_write
                movq %rbx, %rdi
                call kof_db_mysql_reset
                call kof_db_mysql_next
                testq %rax, %rax
                jle .Lprep_fail
                cmpb $0x00, (%rsi)            # OK response
                jne .Lprep_fail
                movl 1(%rsi), %r14d           # stmt_id u32 LE
                movzwl 5(%rsi), %r13d         # num_columns
                movzwl 7(%rsi), %ebx          # num_params
                testl %ebx, %ebx
                jz .Lprep_cols
            .Lprep_dp:
                call kof_db_mysql_next
                testq %rax, %rax
                jle .Lprep_fail
                decl %ebx
                jnz .Lprep_dp
                call kof_db_mysql_next        # EOF apos coldefs de params
                testq %rax, %rax
                jle .Lprep_fail
            .Lprep_cols:
                testl %r13d, %r13d
                jz .Lprep_ok
            .Lprep_dc:
                call kof_db_mysql_next
                testq %rax, %rax
                jle .Lprep_fail
                decl %r13d
                jnz .Lprep_dc
                call kof_db_mysql_next        # EOF apos coldefs de colunas
                testq %rax, %rax
                jle .Lprep_fail
            .Lprep_ok:
                movl %r14d, %eax
                jmp .Lprep_ret
            .Lprep_fail:
                xorl %eax, %eax
            .Lprep_ret:
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # kof_db_mysql_exec(rdi=fd, esi=stmt_id, edx=nparams) -> rax: payload len
            # da resposta (0 erro). Monta e envia COM_STMT_EXECUTE com os args de
            # .Ldb_prep_args. Caller faz kof_db_mysql_{reset,next} p/ a resposta.
            .globl kof_db_mysql_exec
            .type kof_db_mysql_exec, @function
            kof_db_mysql_exec:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx               # fd
                movl %esi, %r12d              # stmt_id
                movl %edx, %r13d              # nparams
                leaq .Ldb_prep_execbuf(%rip), %r14
                movb $0x17, 4(%r14)           # COM_STMT_EXECUTE
                movl %r12d, 5(%r14)
                movb $0, 9(%r14)              # flags = NO_CURSOR
                movl $1, 10(%r14)             # iteration-count
                leaq 14(%r14), %r15           # cursor
                testl %r13d, %r13d
                jz .Lexec_nobind
                movb $0, (%r15)               # null bitmap (1 byte, sem nulls)
                incq %r15
                movb $1, (%r15)               # new-params-bound-flag
                incq %r15
                # types (2B por param)
                xorl %ecx, %ecx               # i em ecx — cuidado: nao chamar nada
            .Lexec_tloop:
                cmpl %r13d, %ecx
                jge .Lexec_tdone
                leaq .Ldb_prep_args(%rip), %rax
                movq (%rax,%rcx,8), %rax      # valor
                cmpq $0x1000000, %rax
                jae .Lexec_tstr
                movw $3, (%r15)               # MYSQL_TYPE_LONG
                jmp .Lexec_tnext
            .Lexec_tstr:
                movw $0xFD, (%r15)            # MYSQL_TYPE_VAR_STRING
            .Lexec_tnext:
                addq $2, %r15
                incl %ecx
                jmp .Lexec_tloop
            .Lexec_tdone:
                # values — pode chamar memcpy (relica: nparams em %r13, i em .Lprep
                # counter na pilha)
                xorl %ecx, %ecx
            .Lexec_vloop:
                cmpl %r13d, %ecx
                jge .Lexec_send
                leaq .Ldb_prep_args(%rip), %rax
                movq (%rax,%rcx,8), %rax
                cmpq $0x1000000, %rax
                jae .Lexec_vstr
                movl %eax, (%r15)             # int 4B LE
                addq $4, %r15
                incl %ecx
                jmp .Lexec_vloop
            .Lexec_vstr:
                # salva i / nparams / fd (memcpy clobbera rcx)
                pushq %rcx
                pushq %r13
                pushq %rbx
                # lenenc do tamanho (suporta < 251, depois 0xFC + 2B)
                movl 16(%rax), %r13d          # len
                cmpq $251, %r13
                jae .Lexec_vstr_big
                movb %r13b, (%r15)
                incq %r15
                jmp .Lexec_vstr_copy
            .Lexec_vstr_big:
                movb $0xFC, (%r15)
                movw %r13w, 1(%r15)
                addq $3, %r15
            .Lexec_vstr_copy:
                leaq 24(%rax), %rsi           # bytes
                movq %r15, %rdi               # dst
                movq %r13, %rdx               # len
                call kof_memcpy
                addq %r13, %r15
                popq %rbx
                popq %r13
                popq %rcx
                incl %ecx
                jmp .Lexec_vloop
            .Lexec_nobind:
                # sem params: payload termina aqui (sem nullmap/flag)
            .Lexec_send:
                # payload len = r15 - (buf+4)
                leaq .Ldb_prep_execbuf(%rip), %rax
                leaq 4(%rax), %rcx
                movq %r15, %rdx
                subq %rcx, %rdx               # payload len
                movb %dl, 0(%rax)
                shrl $8, %edx
                movb %dl, 1(%rax)
                shrl $8, %edx
                movb %dl, 2(%rax)
                movb $0, 3(%rax)
                # total = 4 + payload
                movq %r15, %rdx
                subq %rax, %rdx
                leaq -4(%rdx), %rax           # rax = payload len
                # write(fd, buf, 4+payload)
                movq %rbx, %rdi
                leaq .Ldb_prep_execbuf(%rip), %rsi
                leaq 4(%rax), %rdx
                pushq %rax
                call kof_net_write
                popq %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret
            """);
    }
}
