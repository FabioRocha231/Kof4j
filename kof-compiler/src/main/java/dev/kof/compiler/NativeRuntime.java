package dev.kof.compiler;

import java.util.List;


final class NativeRuntime {

    private NativeRuntime() {}


    static String generateRuntimeAssembly() {
        StringBuilder sb = new StringBuilder();
        emitPrint(sb);
        emitPrintln(sb);
        emitPrintInt(sb);
        emitIntToString(sb);
        emitLongToString(sb);
        emitBoolToString(sb);
        emitListFunctions(sb);
        emitJsonFunctions(sb);
        emitAlloc(sb);
        emitFree(sb);
        emitProcessExit(sb);
        emitPanic(sb);
        emitNullError(sb);
        emitBoundsError(sb);
        emitMemcpy(sb);
        emitStringFromLiteral(sb);
        emitStringLength(sb);
        emitStringConcat(sb);
        emitStringEquals(sb);
        emitPrintString(sb);
        emitPrintlnString(sb);
        emitStringCharAt(sb);
        emitStringSubstring(sb);
        emitStringContains(sb);
        emitStringStartsWith(sb);
        emitStringEndsWith(sb);
        emitStringIndexOf(sb);
        emitStringTrim(sb);
        emitStringCase(sb);
        emitStringReplace(sb);
        emitStringEqualsIgnoreCase(sb);
        emitStringSplit(sb);
        emitArrayAlloc(sb);
        emitArrayLength(sb);
        emitArrayGet(sb);
        emitArraySet(sb);
        emitMemstats(sb);
        emitIoTimeFunctions(sb);
        emitLogFunctions(sb);
        emitConfigFunctions(sb);
        emitIoFileFunctions(sb);
        emitUiColorFunctions(sb);
        emitUiWindowFunctions(sb);
        emitNetSocket(sb);
        emitNetBind(sb);
        emitNetListen(sb);
        emitNetAccept(sb);
        emitNetRead(sb);
        emitNetWrite(sb);
        emitNetClose(sb);
        emitInstanceof(sb);
        emitSecurityFunctions(sb);
        return sb.toString();
    }


    static void generateMethodTable(StringBuilder sb, String className, List<String> methodNames) {
        sb.append(".balign 8\n");
        sb.append(".globl ").append(className).append("_vtable\n");
        sb.append(".type ").append(className).append("_vtable, @object\n");
        sb.append(className).append("_vtable:\n");
        for (String methodName : methodNames) {
            sb.append("    .quad ").append(methodName).append("\n");
        }
        sb.append("    .quad 0\n");
    }


    static void emitInitObject(StringBuilder sb) {
        sb.append("""
            .globl kof_init_object
            .type kof_init_object, @function
            kof_init_object:
                movl %esi, 0(%rdi)
                movl $0, 4(%rdi)
                movq %rdx, 8(%rdi)
                ret
            """);
    }


    private static void emitPrint(StringBuilder sb) {
        sb.append("""
            .globl kof_print
            .type kof_print, @function
            kof_print:
                pushq %rbx
                movq %rdi, %rbx
                xorq %rdx, %rdx
            .Lkof_print_len:
                cmpb $0, (%rbx,%rdx)
                je .Lkof_print_do
                incq %rdx
                jmp .Lkof_print_len
            .Lkof_print_do:
                movq $1, %rax
                movq $1, %rdi
                movq %rbx, %rsi
                syscall
                popq %rbx
                ret
            """);
    }


    private static void emitPrintln(StringBuilder sb) {
        sb.append("""
            .globl kof_println
            .type kof_println, @function
            kof_println:
                call kof_print
                pushq %rbx
                leaq .Lnewline(%rip), %rdi
                call kof_print
                popq %rbx
                ret
            """);
    }


    private static void emitIntToString(StringBuilder sb) {
        sb.append("""
            .globl kof_int_to_string
            .type kof_int_to_string, @function
            kof_int_to_string:
                pushq %rbx
                pushq %r12
                pushq %r13
                movl %edi, %eax
                movq $0, %r12
                testl %eax, %eax
                jns .Lkof_int_to_str_pos
                movq $1, %r12
                negl %eax
            .Lkof_int_to_str_pos:
                movl %eax, %r13d
                movq $0, %rbx
                movl $10, %ecx
            .Lkof_int_to_str_count:
                xorl %edx, %edx
                divl %ecx
                incq %rbx
                testl %eax, %eax
                jnz .Lkof_int_to_str_count
                testq %r12, %r12
                jz .Lkof_int_to_str_count_done
                incq %rbx
            .Lkof_int_to_str_count_done:
                leaq 25(%rbx), %rdi
                call kof_alloc
                pushq %rax
                leaq 23(%rax), %rsi
                addq %rbx, %rsi
                movl %r13d, %eax
                movl $10, %ecx
            .Lkof_int_to_str_loop:
                xorl %edx, %edx
                divl %ecx
                addb $48, %dl
                movb %dl, (%rsi)
                decq %rsi
                testl %eax, %eax
                jnz .Lkof_int_to_str_loop
                testq %r12, %r12
                jz .Lkof_int_to_str_negdone
                movb $45, (%rsi)
            .Lkof_int_to_str_negdone:
                testq %r12, %r12
                jnz .Lkof_int_to_str_ready
                incq %rsi
            .Lkof_int_to_str_ready:
                popq %r13
                movl $1, 0(%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl %ebx, 16(%r13)
                movl $0, 20(%r13)
                movq %r13, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }


    private static void emitLongToString(StringBuilder sb) {
        sb.append("""
            .globl kof_long_to_string
            .type kof_long_to_string, @function
            kof_long_to_string:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rax
                movq $0, %r12
                testq %rax, %rax
                jns .Lkof_long_to_str_pos
                movq $1, %r12
                negq %rax
            .Lkof_long_to_str_pos:
                movq %rax, %r13
                movq $0, %rbx
                movq $10, %rcx
            .Lkof_long_to_str_count:
                xorq %rdx, %rdx
                divq %rcx
                incq %rbx
                testq %rax, %rax
                jnz .Lkof_long_to_str_count
                testq %r12, %r12
                jz .Lkof_long_to_str_count_done
                incq %rbx
            .Lkof_long_to_str_count_done:
                leaq 25(%rbx), %rdi
                call kof_alloc
                pushq %rax
                leaq 23(%rax), %rsi
                addq %rbx, %rsi
                movq %r13, %rax
                movq $10, %rcx
            .Lkof_long_to_str_loop:
                xorq %rdx, %rdx
                divq %rcx
                addb $48, %dl
                movb %dl, (%rsi)
                decq %rsi
                testq %rax, %rax
                jnz .Lkof_long_to_str_loop
                testq %r12, %r12
                jz .Lkof_long_to_str_negdone
                movb $45, (%rsi)
            .Lkof_long_to_str_negdone:
                testq %r12, %r12
                jnz .Lkof_long_to_str_ready
                incq %rsi
            .Lkof_long_to_str_ready:
                popq %r13
                movl $1, 0(%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl %ebx, 16(%r13)
                movl $0, 20(%r13)
                movq %r13, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }


    private static void emitBoolToString(StringBuilder sb) {
        sb.append("""
            .globl kof_bool_to_string
            .type kof_bool_to_string, @function
            kof_bool_to_string:
                testl %edi, %edi
                jz .Lkof_bool_to_str_false
                leaq .Lkof_str_true(%rip), %rdi
                movl $4, %esi
                jmp .Lkof_bool_to_str_make
            .Lkof_bool_to_str_false:
                leaq .Lkof_str_false(%rip), %rdi
                movl $5, %esi
            .Lkof_bool_to_str_make:
                jmp kof_string_from_literal
            """);
    }


    private static void emitPrintInt(StringBuilder sb) {
        sb.append("""
            .globl kof_print_int
            .type kof_print_int, @function
            kof_print_int:
                pushq %rbx
                pushq %r12
                pushq %r13
                movl %edi, %eax
                movq $0, %r12
                testl %eax, %eax
                jns .Lkof_print_int_pos
                movq $1, %r12
                negl %eax
            .Lkof_print_int_pos:
                movl %eax, %r13d
                movq $0, %rbx
                movl $10, %ecx
            .Lkof_print_int_count:
                xorl %edx, %edx
                divl %ecx
                incq %rbx
                testl %eax, %eax
                jnz .Lkof_print_int_count
                testq %r12, %r12
                jz .Lkof_print_int_count_done
                incq %rbx
            .Lkof_print_int_count_done:
                leaq -48(%rsp), %rsi
                addq %rbx, %rsi
                movl %r13d, %eax
                movq $0, %r13
                movl $10, %ecx
            .Lkof_print_int_loop:
                xorl %edx, %edx
                divl %ecx
                addb $48, %dl
                movb %dl, (%rsi)
                decq %rsi
                incq %r13
                testl %eax, %eax
                jnz .Lkof_print_int_loop
                testq %r12, %r12
                jz .Lkof_print_int_negdone
                movb $45, (%rsi)
                incq %r13
            .Lkof_print_int_negdone:
                testq %r12, %r12
                jnz .Lkof_print_int_ready
                incq %rsi
            .Lkof_print_int_ready:
                movq %r13, %rdx
                movq $1, %rax
                movq $1, %rdi
                syscall
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }


    private static void emitAlloc(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lstr_alloc_fail: .asciz "Runtime error: out of memory"
            .section .text
            .globl kof_alloc
            .type kof_alloc, @function
            kof_alloc:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                addq $15, %rbx
                andq $~15, %rbx
                addq $16, %rbx
                movq %rbx, %r12
                movq $0, %rdi
                movq %r12, %rsi
                movq $0x22, %rdx
                movq $0x22, %r10
                movq $-1, %r8
                movq $0, %r9
                movq $9, %rax
                syscall
                testq %rax, %rax
                js .Lkof_alloc_fail
                movq %r12, (%rax)
                addq $16, %rax
                incq .Lkof_alloc_count(%rip)
                addq %r12, .Lkof_alloc_bytes(%rip)
                popq %r12
                popq %rbx
                ret
            .Lkof_alloc_fail:
                leaq .Lstr_alloc_fail(%rip), %rdi
                call kof_panic
            """);
    }


    private static void emitFree(StringBuilder sb) {
        sb.append("""
            .globl kof_free
            .type kof_free, @function
            kof_free:
                testq %rdi, %rdi
                jz .Lkof_free_done
                movq -16(%rdi), %rsi
                leaq -16(%rdi), %rdi
                movq $11, %rax
                syscall
                incq .Lkof_free_count(%rip)
                addq %rsi, .Lkof_free_bytes(%rip)
            .Lkof_free_done:
                ret
            """);
    }


    static void emitMemstats(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lkof_alloc_count: .quad 0
            .Lkof_free_count: .quad 0
            .Lkof_alloc_bytes: .quad 0
            .Lkof_free_bytes: .quad 0
            .Lkof_memstats_lbl_alloc: .asciz "allocs: "
            .Lkof_memstats_lbl_free: .asciz "frees: "
            .Lkof_memstats_lbl_live: .asciz "live bytes: "
            .Lkof_memstats_nl: .asciz "\\n"
            .section .text
            .globl kof_memstats
            .type kof_memstats, @function
            kof_memstats:
                pushq %rbx
                leaq .Lkof_memstats_lbl_alloc(%rip), %rdi
                call kof_print
                movq .Lkof_alloc_count(%rip), %rdi
                call kof_long_to_string
                movq %rax, %rdi
                call kof_print_string
                leaq .Lkof_memstats_nl(%rip), %rdi
                call kof_print
                leaq .Lkof_memstats_lbl_free(%rip), %rdi
                call kof_print
                movq .Lkof_free_count(%rip), %rdi
                call kof_long_to_string
                movq %rax, %rdi
                call kof_print_string
                leaq .Lkof_memstats_nl(%rip), %rdi
                call kof_print
                leaq .Lkof_memstats_lbl_live(%rip), %rdi
                call kof_print
                movq .Lkof_alloc_bytes(%rip), %rbx
                subq .Lkof_free_bytes(%rip), %rbx
                movq %rbx, %rdi
                call kof_long_to_string
                movq %rax, %rdi
                call kof_print_string
                leaq .Lkof_memstats_nl(%rip), %rdi
                call kof_print
                popq %rbx
                ret
            """);
    }


    /** process.exit(code): syscall exit — termina o processo na hora. */
    private static void emitProcessExit(StringBuilder sb) {
        sb.append("""
            .section .text
            .globl kof_process_exit
            .type kof_process_exit, @function
            kof_process_exit:
                movq %rdi, %rdi
                movq $60, %rax
                syscall
            """);
    }

    private static void emitPanic(StringBuilder sb) {
        sb.append("""
            .section .data
            kof_exc_chain: .quad 0
            .section .text
            .globl kof_panic
            .type kof_panic, @function
            kof_panic:
                call kof_println
                movq $60, %rax
                movq $1, %rdi
                syscall
            """);
        sb.append("""
            .globl kof_throw_string
            .type kof_throw_string, @function
            kof_throw_string:
                movq %rdi, %rsi
                movq kof_exc_chain(%rip), %rax
                testq %rax, %rax
                jz .Lkof_throw_panic
                movq 8(%rax), %rsp
                movq 16(%rax), %rbp
                movq 24(%rax), %rcx
                movq %rcx, kof_exc_chain(%rip)
                movq 0(%rax), %rcx
                testq %rcx, %rcx
                jz .Lkof_throw_panic
                jmp *%rcx
            .Lkof_throw_panic:
                movq %rsi, %rdi
                call kof_println_string
                movq $60, %rax
                movq $1, %rdi
                syscall
            """);
    }


    private static void emitNullError(StringBuilder sb) {
        sb.append(".Lstr_null_err: .asciz \"Runtime error: null pointer access\"\n");
        sb.append("""
            .globl kof_null_error
            .type kof_null_error, @function
            kof_null_error:
                leaq .Lstr_null_err(%rip), %rdi
                call kof_panic
            """);
    }


    private static void emitBoundsError(StringBuilder sb) {
        sb.append(".Lstr_bounds_err: .asciz \"Runtime error: array index out of bounds\"\n");
        sb.append("""
            .globl kof_bounds_error
            .type kof_bounds_error, @function
            kof_bounds_error:
                leaq .Lstr_bounds_err(%rip), %rdi
                call kof_panic
            """);
    }




    static final int KOF_STRING_TYPE_ID = 1;
    static final int KOF_STRING_HEADER_SIZE = 24;


    private static void emitStringFromLiteral(StringBuilder sb) {
        sb.append("""
            .globl kof_string_from_literal
            .type kof_string_from_literal, @function
            kof_string_from_literal:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movl %esi, %r12d
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, 0(%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl %r12d, 16(%r13)
                movl $0, 20(%r13)
                movq %r13, %rdi
                addq $24, %rdi
                movq %rbx, %rsi
                movl %r12d, %edx
                call kof_memcpy
                movb $0, 24(%r13,%r12)
                movq %r13, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }


    static void emitMemcpy(StringBuilder sb) {
        sb.append("""
            .globl kof_memcpy
            .type kof_memcpy, @function
            kof_memcpy:
                xorq %rcx, %rcx
            .Lkof_memcpy_loop:
                cmpl %ecx, %edx
                jle .Lkof_memcpy_done
                movb (%rsi,%rcx), %al
                movb %al, (%rdi,%rcx)
                incq %rcx
                jmp .Lkof_memcpy_loop
            .Lkof_memcpy_done:
                ret
            """);
    }


    private static void emitStringLength(StringBuilder sb) {
        sb.append("""
            .globl kof_string_length
            .type kof_string_length, @function
            kof_string_length:
                movl 16(%rdi), %eax
                ret
            """);
    }


    private static void emitStringConcat(StringBuilder sb) {
        sb.append("""
            .globl kof_string_concat
            .type kof_string_concat, @function
            kof_string_concat:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%rbx), %r13d
                addl 16(%r12), %r13d
                leal 25(%r13), %edi
                call kof_alloc
                movq %rax, %r14
                movl $1, 0(%r14)
                movl $0, 4(%r14)
                movq $0, 8(%r14)
                movl %r13d, 16(%r14)
                movl $0, 20(%r14)
                movq %r14, %rdi
                addq $24, %rdi
                leaq 24(%rbx), %rsi
                movl 16(%rbx), %edx
                call kof_memcpy
                movl 16(%rbx), %eax
                movq %r14, %rdi
                addq $24, %rdi
                addq %rax, %rdi
                leaq 24(%r12), %rsi
                movl 16(%r12), %edx
                call kof_memcpy
                movb $0, 24(%r14,%r13)
                movq %r14, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }


    private static void emitPrintString(StringBuilder sb) {
        sb.append("""
            .globl kof_print_string
            .type kof_print_string, @function
            kof_print_string:
                movq %rdi, %rsi
                addq $24, %rsi
                movl 16(%rdi), %edx
                movq $1, %rax
                movq $1, %rdi
                syscall
                ret
            """);
    }


    private static void emitPrintlnString(StringBuilder sb) {
        sb.append("""
            .globl kof_println_string
            .type kof_println_string, @function
            kof_println_string:
                pushq %rbx
                movq %rdi, %rbx
                movq %rbx, %rdi
                call kof_print_string
                leaq .Lnewline(%rip), %rdi
                call kof_print
                popq %rbx
                ret
            """);
    }


    private static void emitListFunctions(StringBuilder sb) {
        sb.append("""
            .globl kof_list_new
            .type kof_list_new, @function
            kof_list_new:
                pushq %rbx
                movq $64, %rdi
                call kof_alloc
                movq %rax, %rbx
                movl $100, 0(%rbx)
                movl $0, 4(%rbx)
                movq $0, 8(%rbx)
                movl $0, 16(%rbx)
                movl $2, 20(%rbx)
                movq $16, %rdi
                call kof_alloc
                movq %rax, 24(%rbx)
                movq %rbx, %rax
                popq %rbx
                ret

            .globl kof_list_grow
            .type kof_list_grow, @function
            kof_list_grow:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movl 20(%rbx), %r12d
                movl %r12d, %r13d
                shll $1, %r13d
                movl %r13d, 20(%rbx)
                movslq %r13d, %rdi
                shlq $3, %rdi
                addq $24, %rdi
                call kof_alloc
                movq %rax, %rcx
                movq 24(%rbx), %rsi
                movl 16(%rbx), %r13d
                movslq %r13d, %r13
                xorq %rdx, %rdx
            .Lkof_list_grow_copy:
                cmpq %r13, %rdx
                jge .Lkof_list_grow_done
                movq (%rsi,%rdx,8), %rax
                movq %rax, (%rcx,%rdx,8)
                incq %rdx
                jmp .Lkof_list_grow_copy
            .Lkof_list_grow_done:
                movq %rcx, 24(%rbx)
                movq %rbx, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_list_add
            .type kof_list_add, @function
            kof_list_add:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%rbx), %eax
                cmpl 20(%rbx), %eax
                jl .Lkof_list_add_ok
                movq %rbx, %rdi
                call kof_list_grow
            .Lkof_list_add_ok:
                movl 16(%rbx), %eax
                movslq %eax, %rcx
                movq 24(%rbx), %rdx
                movq %r12, (%rdx,%rcx,8)
                addl $1, 16(%rbx)
                popq %r12
                popq %rbx
                ret

            .globl kof_list_get
            .type kof_list_get, @function
            kof_list_get:
                pushq %rbx
                movq %rdi, %rbx
                movl 16(%rbx), %eax
                cmpl %eax, %esi
                jge .Lkof_list_get_bounds
                testl %esi, %esi
                jl .Lkof_list_get_bounds
                movslq %esi, %rcx
                movq 24(%rbx), %rax
                movq (%rax,%rcx,8), %rax
                popq %rbx
                ret
            .Lkof_list_get_bounds:
                movl %esi, %edi
                movl 16(%rbx), %esi
                call kof_bounds_error

            .globl kof_list_set
            .type kof_list_set, @function
            kof_list_set:
                pushq %rbx
                movq %rdi, %rbx
                movl 16(%rbx), %eax
                cmpl %eax, %esi
                jge .Lkof_list_set_bounds
                testl %esi, %esi
                jl .Lkof_list_set_bounds
                movslq %esi, %rcx
                movq 24(%rbx), %rax
                movq %rdx, (%rax,%rcx,8)
                popq %rbx
                ret
            .Lkof_list_set_bounds:
                movl %esi, %edi
                movl 16(%rbx), %esi
                call kof_bounds_error

            .globl kof_list_size
            .type kof_list_size, @function
            kof_list_size:
                movslq 16(%rdi), %rax
                ret

            .globl kof_list_contains
            .type kof_list_contains, @function
            kof_list_contains:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movq %rsi, %r12
                movl %edx, %r13d
                movl 16(%rbx), %r14d
                xorl %r15d, %r15d
            .Lkof_list_contains_loop:
                cmpl %r14d, %r15d
                jge .Lkof_list_contains_no
                movq 24(%rbx), %rax
                movq (%rax,%r15,8), %rax
                cmpl $1, %r13d
                je .Lkof_list_contains_str
                cmpq %r12, %rax
                je .Lkof_list_contains_yes
                jmp .Lkof_list_contains_next
            .Lkof_list_contains_str:
                movq %rax, %rdi
                movq %r12, %rsi
                call kof_string_equals
                testl %eax, %eax
                jnz .Lkof_list_contains_yes
            .Lkof_list_contains_next:
                incl %r15d
                jmp .Lkof_list_contains_loop
            .Lkof_list_contains_yes:
                movl $1, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_list_contains_no:
                xorl %eax, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_list_is_empty
            .type kof_list_is_empty, @function
            kof_list_is_empty:
                cmpl $0, 16(%rdi)
                sete %al
                movzbl %al, %eax
                ret

            .globl kof_list_remove
            .type kof_list_remove, @function
            kof_list_remove:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movl 16(%rbx), %eax
                cmpl %eax, %esi
                jge .Lkof_list_remove_bounds
                testl %esi, %esi
                jl .Lkof_list_remove_bounds
                movslq %esi, %rcx
                movq 24(%rbx), %rax
                movq (%rax,%rcx,8), %r12
            .Lkof_list_remove_shift:
                movl 16(%rbx), %eax
                decl %eax
                cmpl %eax, %ecx
                jge .Lkof_list_remove_done
                movq 24(%rbx), %rax
                movq 8(%rax,%rcx,8), %rdx
                movq 24(%rbx), %rax
                movq %rdx, (%rax,%rcx,8)
                incq %rcx
                jmp .Lkof_list_remove_shift
            .Lkof_list_remove_done:
                movl 16(%rbx), %eax
                decl %eax
                movl %eax, 16(%rbx)
                movq %r12, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_list_remove_bounds:
                movl %esi, %edi
                movl 16(%rbx), %esi
                call kof_bounds_error

            .globl kof_list_clear
            .type kof_list_clear, @function
            kof_list_clear:
                movl $0, 16(%rdi)
                ret
            """);
    }


    private static void emitJsonFunctions(StringBuilder sb) {
        sb.append("""
            .globl kof_json_builder_new
            .type kof_json_builder_new, @function
            kof_json_builder_new:
                pushq %rbx
                movq $32, %rdi
                call kof_alloc
                movq %rax, %rbx
                movl $101, 0(%rbx)
                movl $0, 4(%rbx)
                movq $0, 8(%rbx)
                movl $0, 16(%rbx)
                movl $64, 20(%rbx)
                movq $64, %rdi
                call kof_alloc
                movq %rax, 24(%rbx)
                movq %rbx, %rax
                popq %rbx
                ret

            .globl kof_json_builder_grow
            .type kof_json_builder_grow, @function
            kof_json_builder_grow:
                pushq %rbx
                pushq %r13
                movq %rdi, %rbx
                movl 20(%rbx), %eax
                shll $1, %eax
                movl %eax, 20(%rbx)
                movslq %eax, %rdi
                call kof_alloc
                movq %rax, %rcx
                movq 24(%rbx), %rsi
                movl 16(%rbx), %r13d
                movslq %r13d, %r13
                xorq %rdx, %rdx
            .Lkof_json_bgr_copy:
                cmpq %r13, %rdx
                jge .Lkof_json_bgr_done
                movb (%rsi,%rdx), %al
                movb %al, (%rcx,%rdx)
                incq %rdx
                jmp .Lkof_json_bgr_copy
            .Lkof_json_bgr_done:
                movq %rcx, 24(%rbx)
                movq %rbx, %rax
                popq %r13
                popq %rbx
                ret

            .globl kof_json_builder_char
            .type kof_json_builder_char, @function
            kof_json_builder_char:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movl %esi, %r12d
                movl 16(%rbx), %eax
                cmpl 20(%rbx), %eax
                jl .Lkof_json_bch_ok
                movq %rbx, %rdi
                call kof_json_builder_grow
            .Lkof_json_bch_ok:
                movl 16(%rbx), %eax
                movslq %eax, %rcx
                movq 24(%rbx), %rdx
                movb %r12b, (%rdx,%rcx)
                addl $1, 16(%rbx)
                popq %r12
                popq %rbx
                ret

            .globl kof_json_builder_str
            .type kof_json_builder_str, @function
            kof_json_builder_str:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%r12), %r13d
            .Lkof_json_bst_grow_loop:
                movl 16(%rbx), %eax
                addl %r13d, %eax
                cmpl 20(%rbx), %eax
                jle .Lkof_json_bst_ok
                movq %rbx, %rdi
                call kof_json_builder_grow
                jmp .Lkof_json_bst_grow_loop
            .Lkof_json_bst_ok:
                movl 16(%rbx), %eax
                movslq %eax, %rcx
                movq 24(%rbx), %rdx
                leaq 24(%r12), %rsi
                xorq %r8, %r8
            .Lkof_json_bst_copy:
                cmpq %r13, %r8
                jge .Lkof_json_bst_done
                movb (%rsi,%r8), %al
                movb %al, (%rdx,%rcx)
                incq %r8
                incq %rcx
                jmp .Lkof_json_bst_copy
            .Lkof_json_bst_done:
                movl 16(%rbx), %eax
                addl %r13d, %eax
                movl %eax, 16(%rbx)
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_json_builder_result
            .type kof_json_builder_result, @function
            kof_json_builder_result:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movl 16(%rbx), %r12d
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, 0(%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl %r12d, 16(%r13)
                movl $0, 20(%r13)
                movq 24(%rbx), %rsi
                leaq 24(%r13), %rdi
                movslq %r12d, %rdx
                call kof_memcpy
                movb $0, 24(%r13,%r12)
                movq %r13, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_json_encode_int
            .type kof_json_encode_int, @function
            kof_json_encode_int:
                jmp kof_int_to_string

            .globl kof_json_encode_long
            .type kof_json_encode_long, @function
            kof_json_encode_long:
                jmp kof_long_to_string

            .globl kof_json_encode_bool
            .type kof_json_encode_bool, @function
            kof_json_encode_bool:
                jmp kof_bool_to_string

            .globl kof_json_encode_string
            .type kof_json_encode_string, @function
            kof_json_encode_string:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                call kof_json_builder_new
                movq %rax, %r12
                movq %r12, %rdi
                movl $34, %esi
                call kof_json_builder_char
                movl 16(%rbx), %r13d
                xorq %r14, %r14
            .Lkof_json_esc_loop:
                cmpl %r13d, %r14d
                jge .Lkof_json_esc_done
                leaq 24(%rbx), %rax
                movzbl (%rax,%r14), %eax
                cmpb $34, %al
                je .Lkof_json_esc_quote
                cmpb $92, %al
                je .Lkof_json_esc_backslash
                movq %r12, %rdi
                movl %eax, %esi
                call kof_json_builder_char
                incq %r14
                jmp .Lkof_json_esc_loop
            .Lkof_json_esc_quote:
                movq %r12, %rdi
                movl $92, %esi
                call kof_json_builder_char
                movq %r12, %rdi
                movl $34, %esi
                call kof_json_builder_char
                incq %r14
                jmp .Lkof_json_esc_loop
            .Lkof_json_esc_backslash:
                movq %r12, %rdi
                movl $92, %esi
                call kof_json_builder_char
                movq %r12, %rdi
                movl $92, %esi
                call kof_json_builder_char
                incq %r14
                jmp .Lkof_json_esc_loop
            .Lkof_json_esc_done:
                movq %r12, %rdi
                movl $34, %esi
                call kof_json_builder_char
                movq %r12, %rdi
                call kof_json_builder_result
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_json_encode_list
            .type kof_json_encode_list, @function
            kof_json_encode_list:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movl %esi, %r15d
                call kof_json_builder_new
                movq %rax, %r12
                movq %r12, %rdi
                movl $91, %esi
                call kof_json_builder_char
                movl 16(%rbx), %r13d
                xorq %r14, %r14
            .Lkof_json_el_loop:
                cmpl %r13d, %r14d
                jge .Lkof_json_el_done
                testq %r14, %r14
                jz .Lkof_json_el_no_comma
                movq %r12, %rdi
                movl $44, %esi
                call kof_json_builder_char
            .Lkof_json_el_no_comma:
                movq 24(%rbx), %rax
                movq (%rax,%r14,8), %rdi
                cmpl $1, %r15d
                je .Lkof_json_el_string
                cmpl $2, %r15d
                je .Lkof_json_el_bool
                call kof_json_encode_int
                jmp .Lkof_json_el_appended
            .Lkof_json_el_string:
                call kof_json_encode_string
                jmp .Lkof_json_el_appended
            .Lkof_json_el_bool:
                call kof_json_encode_bool
            .Lkof_json_el_appended:
                movq %r12, %rdi
                movq %rax, %rsi
                call kof_json_builder_str
                incq %r14
                jmp .Lkof_json_el_loop
            .Lkof_json_el_done:
                movq %r12, %rdi
                movl $93, %esi
                call kof_json_builder_char
                movq %r12, %rdi
                call kof_json_builder_result
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_json_encode_array
            .type kof_json_encode_array, @function
            kof_json_encode_array:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                call kof_json_builder_new
                movq %rax, %r12
                movq %r12, %rdi
                movl $91, %esi
                call kof_json_builder_char
                movl 16(%rbx), %r13d
                movl 20(%rbx), %r15d
                xorq %r14, %r14
            .Lkof_json_ea_loop:
                cmpl %r13d, %r14d
                jge .Lkof_json_ea_done
                testq %r14, %r14
                jz .Lkof_json_ea_no_comma
                movq %r12, %rdi
                movl $44, %esi
                call kof_json_builder_char
            .Lkof_json_ea_no_comma:
                leaq 24(%rbx), %rax
                cmpl $4, %r15d
                je .Lkof_json_ea_int
                movq (%rax,%r14,8), %rdi
                call kof_json_encode_string
                jmp .Lkof_json_ea_appended
            .Lkof_json_ea_int:
                movl (%rax,%r14,4), %edi
                call kof_json_encode_int
            .Lkof_json_ea_appended:
                movq %r12, %rdi
                movq %rax, %rsi
                call kof_json_builder_str
                incq %r14
                jmp .Lkof_json_ea_loop
            .Lkof_json_ea_done:
                movq %r12, %rdi
                movl $93, %esi
                call kof_json_builder_char
                movq %r12, %rdi
                call kof_json_builder_result
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_json_decode_int
            .type kof_json_decode_int, @function
            kof_json_decode_int:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movl 16(%rbx), %ecx
                xorq %rdx, %rdx
                jmp .Lkof_json_di_skip

            .globl kof_json_decode_int_at
            .type kof_json_decode_int_at, @function
            kof_json_decode_int_at:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movl 16(%rbx), %ecx
            .Lkof_json_di_skip:
                cmpl %ecx, %edx
                jge .Lkof_json_di_err
                leaq 24(%rbx), %rax
                movzbl (%rax,%rdx), %eax
                cmpb $32, %al
                je .Lkof_json_di_skip_inc
                cmpb $10, %al
                je .Lkof_json_di_skip_inc
                cmpb $13, %al
                je .Lkof_json_di_skip_inc
                cmpb $9, %al
                je .Lkof_json_di_skip_inc
                jmp .Lkof_json_di_sign
            .Lkof_json_di_skip_inc:
                incq %rdx
                jmp .Lkof_json_di_skip
            .Lkof_json_di_sign:
                movq $1, %r12
                cmpb $45, %al
                jne .Lkof_json_di_digits
                movq $-1, %r12
                incq %rdx
            .Lkof_json_di_digits:
                xorq %r8, %r8
            .Lkof_json_di_loop:
                cmpl %ecx, %edx
                jge .Lkof_json_di_done
                leaq 24(%rbx), %rax
                movzbl (%rax,%rdx), %eax
                cmpb $48, %al
                jl .Lkof_json_di_done
                cmpb $57, %al
                jg .Lkof_json_di_done
                imulq $10, %r8
                subl $48, %eax
                movslq %eax, %rax
                addq %rax, %r8
                incq %rdx
                jmp .Lkof_json_di_loop
            .Lkof_json_di_done:
                imulq %r12, %r8
                movq %r8, %rax
                popq %r12
                popq %rbx
                ret
            .Lkof_json_di_err:
                xorl %eax, %eax
                popq %r12
                popq %rbx
                ret

            .globl kof_json_decode_long
            .type kof_json_decode_long, @function
            kof_json_decode_long:
                jmp kof_json_decode_int

            .globl kof_json_decode_list
            .type kof_json_decode_list, @function
            kof_json_decode_list:
                jmp kof_json_decode_int_list

            .globl kof_json_decode_bool
            .type kof_json_decode_bool, @function
            kof_json_decode_bool:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movl 16(%rbx), %ecx
                xorq %rdx, %rdx
            .Lkof_json_db_skip:
                cmpl %ecx, %edx
                jge .Lkof_json_db_false
                leaq 24(%rbx), %rax
                movzbl (%rax,%rdx), %eax
                cmpb $32, %al
                je .Lkof_json_db_skip_inc
                cmpb $10, %al
                je .Lkof_json_db_skip_inc
                cmpb $13, %al
                je .Lkof_json_db_skip_inc
                cmpb $9, %al
                je .Lkof_json_db_skip_inc
                jmp .Lkof_json_db_check
            .Lkof_json_db_skip_inc:
                incq %rdx
                jmp .Lkof_json_db_skip
            .Lkof_json_db_check:
                leaq .Lkof_json_true(%rip), %rsi
                movl $4, %r8d
                call kof_json_starts_with
                testl %eax, %eax
                jz .Lkof_json_db_false
                movl $1, %eax
                popq %r12
                popq %rbx
                ret
            .Lkof_json_db_false:
                xorl %eax, %eax
                popq %r12
                popq %rbx
                ret

            .globl kof_json_starts_with
            .type kof_json_starts_with, @function
            kof_json_starts_with:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movl %edx, %r12d
                movq %rsi, %rdi
                movslq %edx, %rdx
                movl 16(%rbx), %ecx
                cmpl %ecx, %edx
                jg .Lkof_json_sw_no
                xorq %rcx, %rcx
            .Lkof_json_sw_loop:
                cmpl %r12d, %ecx
                jge .Lkof_json_sw_yes
                leaq 24(%rbx), %rax
                movzbl (%rax,%rcx), %eax
                movzbl (%rdi,%rcx), %r8d
                cmpb %r8b, %al
                jne .Lkof_json_sw_no
                incq %rcx
                jmp .Lkof_json_sw_loop
            .Lkof_json_sw_yes:
                movl $1, %eax
                popq %r12
                popq %rbx
                ret
            .Lkof_json_sw_no:
                xorl %eax, %eax
                popq %r12
                popq %rbx
                ret

            .globl kof_json_decode_string
            .type kof_json_decode_string, @function
            kof_json_decode_string:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                call kof_json_builder_new
                movq %rax, %r12
                movl 16(%rbx), %r14d
                xorq %r13, %r13
                jmp .Lkof_json_ds_skip

            .globl kof_json_decode_string_at
            .type kof_json_decode_string_at, @function
            kof_json_decode_string_at:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rdx, %r13
                call kof_json_builder_new
                movq %rax, %r12
                movl 16(%rbx), %r14d
            .Lkof_json_ds_skip:
                cmpl %r14d, %r13d
                jge .Lkof_json_ds_done
                leaq 24(%rbx), %rax
                movzbl (%rax,%r13), %eax
                cmpb $32, %al
                je .Lkof_json_ds_skip_inc
                cmpb $10, %al
                je .Lkof_json_ds_skip_inc
                cmpb $13, %al
                je .Lkof_json_ds_skip_inc
                cmpb $9, %al
                je .Lkof_json_ds_skip_inc
                jmp .Lkof_json_ds_open
            .Lkof_json_ds_skip_inc:
                incq %r13
                jmp .Lkof_json_ds_skip
            .Lkof_json_ds_open:
                cmpb $34, %al
                jne .Lkof_json_ds_done
                incq %r13
            .Lkof_json_ds_loop:
                cmpl %r14d, %r13d
                jge .Lkof_json_ds_done
                leaq 24(%rbx), %rax
                movzbl (%rax,%r13), %eax
                cmpb $34, %al
                je .Lkof_json_ds_close
                cmpb $92, %al
                jne .Lkof_json_ds_plain
                incq %r13
                cmpl %r14d, %r13d
                jge .Lkof_json_ds_done
                leaq 24(%rbx), %rax
                movzbl (%rax,%r13), %eax
                cmpb $110, %al
                je .Lkof_json_ds_newline
                cmpb $116, %al
                je .Lkof_json_ds_tab
                jmp .Lkof_json_ds_plain
            .Lkof_json_ds_newline:
                movl $10, %eax
                jmp .Lkof_json_ds_plain
            .Lkof_json_ds_tab:
                movl $9, %eax
            .Lkof_json_ds_plain:
                movq %r12, %rdi
                movl %eax, %esi
                call kof_json_builder_char
                incq %r13
                jmp .Lkof_json_ds_loop
            .Lkof_json_ds_close:
                incq %r13
            .Lkof_json_ds_done:
                movq %r12, %rdi
                call kof_json_builder_result
                movq %r13, %rdx
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_json_decode_int_list
            .type kof_json_decode_int_list, @function
            kof_json_decode_int_list:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                call kof_list_new
                movq %rax, %r12
                movl 16(%rbx), %r15d
                xorq %r13, %r13
            .Lkof_json_dil_skip:
                cmpl %r15d, %r13d
                jge .Lkof_json_dil_done
                leaq 24(%rbx), %rax
                movzbl (%rax,%r13), %eax
                cmpb $32, %al
                je .Lkof_json_dil_skip_inc
                cmpb $10, %al
                je .Lkof_json_dil_skip_inc
                cmpb $13, %al
                je .Lkof_json_dil_skip_inc
                cmpb $9, %al
                je .Lkof_json_dil_skip_inc
                jmp .Lkof_json_dil_open
            .Lkof_json_dil_skip_inc:
                incq %r13
                jmp .Lkof_json_dil_skip
            .Lkof_json_dil_open:
                cmpb $91, %al
                jne .Lkof_json_dil_done
                incq %r13
            .Lkof_json_dil_loop:
                cmpl %r15d, %r13d
                jge .Lkof_json_dil_done
                leaq 24(%rbx), %rax
                movzbl (%rax,%r13), %eax
                cmpb $93, %al
                je .Lkof_json_dil_done
                cmpb $44, %al
                je .Lkof_json_dil_comma
                cmpb $32, %al
                je .Lkof_json_dil_comma
                cmpb $10, %al
                je .Lkof_json_dil_comma
                cmpb $9, %al
                je .Lkof_json_dil_comma
                movq %rbx, %rdi
                movq %r13, %rdx
                call kof_json_decode_int_at
                movq %rdx, %r13
                movq %r12, %rdi
                movq %rax, %rsi
                call kof_list_add
                jmp .Lkof_json_dil_loop
            .Lkof_json_dil_comma:
                incq %r13
                jmp .Lkof_json_dil_loop
            .Lkof_json_dil_done:
                movq %r12, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_json_decode_string_list
            .type kof_json_decode_string_list, @function
            kof_json_decode_string_list:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                call kof_list_new
                movq %rax, %r12
                movl 16(%rbx), %r15d
                xorq %r13, %r13
            .Lkof_json_dsl_skip:
                cmpl %r15d, %r13d
                jge .Lkof_json_dsl_done
                leaq 24(%rbx), %rax
                movzbl (%rax,%r13), %eax
                cmpb $32, %al
                je .Lkof_json_dsl_skip_inc
                cmpb $10, %al
                je .Lkof_json_dsl_skip_inc
                cmpb $13, %al
                je .Lkof_json_dsl_skip_inc
                cmpb $9, %al
                je .Lkof_json_dsl_skip_inc
                jmp .Lkof_json_dsl_open
            .Lkof_json_dsl_skip_inc:
                incq %r13
                jmp .Lkof_json_dsl_skip
            .Lkof_json_dsl_open:
                cmpb $91, %al
                jne .Lkof_json_dsl_done
                incq %r13
            .Lkof_json_dsl_loop:
                cmpl %r15d, %r13d
                jge .Lkof_json_dsl_done
                leaq 24(%rbx), %rax
                movzbl (%rax,%r13), %eax
                cmpb $93, %al
                je .Lkof_json_dsl_done
                cmpb $44, %al
                je .Lkof_json_dsl_comma
                cmpb $32, %al
                je .Lkof_json_dsl_comma
                cmpb $10, %al
                je .Lkof_json_dsl_comma
                cmpb $9, %al
                je .Lkof_json_dsl_comma
                movq %rbx, %rdi
                movq %r13, %rdx
                call kof_json_decode_string_at
                movq %rdx, %r13
                movq %r12, %rdi
                movq %rax, %rsi
                call kof_list_add
                jmp .Lkof_json_dsl_loop
            .Lkof_json_dsl_comma:
                incq %r13
                jmp .Lkof_json_dsl_loop
            .Lkof_json_dsl_done:
                movq %r12, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
        sb.append(".Lkof_json_true: .asciz \"true\"\n");
    }


    private static void emitStringEquals(StringBuilder sb) {
        sb.append("""
            .globl kof_string_equals
            .type kof_string_equals, @function
            kof_string_equals:
                # null-safe: comparar String com null compara ponteiros
                testq %rdi, %rdi
                jz .Lkof_streq_nulla
                testq %rsi, %rsi
                jnz .Lkof_streq_body
                xorl %eax, %eax          # a != null, b == null
                ret
            .Lkof_streq_nulla:
                testq %rsi, %rsi
                jnz .Lkof_streq_nullb
                movl $1, %eax            # ambas nulas
                ret
            .Lkof_streq_nullb:
                xorl %eax, %eax          # a == null, b != null
                ret
            .Lkof_streq_body:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%rbx), %r13d
                cmpl %r13d, 16(%r12)
                jne .Lkof_strequals_no
                xorq %rcx, %rcx
            .Lkof_strequals_loop:
                cmpl %r13d, %ecx
                jge .Lkof_strequals_yes
                movzbl 24(%rbx,%rcx), %eax
                cmpb %al, 24(%r12,%rcx)
                jne .Lkof_strequals_no
                incq %rcx
                jmp .Lkof_strequals_loop
            .Lkof_strequals_yes:
                movl $1, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_strequals_no:
                xorl %eax, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }




    private static void emitStringCharAt(StringBuilder sb) {
        sb.append("""
            .globl kof_string_char_at
            .type kof_string_char_at, @function
            kof_string_char_at:
                movl 16(%rdi), %edx
                cmpl %edx, %esi
                jge .Lkof_strcharAt_bounds
                testl %esi, %esi
                jl .Lkof_strcharAt_bounds
                movzbl 24(%rdi,%rsi), %eax
                ret
            .Lkof_strcharAt_bounds:
                movl %esi, %edi
                movl 16(%rdi), %esi
                call kof_bounds_error
            """);
    }


    private static void emitStringSubstring(StringBuilder sb) {
        sb.append("""
            .globl kof_string_substring
            .type kof_string_substring, @function
            kof_string_substring:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movl %esi, %r12d
                movl %edx, %r13d
                movl 16(%rbx), %ecx
                cmpl $0, %r13d
                jne .Lkof_substr_end_ok
                movl %ecx, %r13d
            .Lkof_substr_end_ok:
                cmpl %ecx, %r13d
                jg .Lkof_substr_bounds
                testl %r12d, %r12d
                jl .Lkof_substr_bounds
                cmpl %r13d, %r12d
                jg .Lkof_substr_bounds
                movl %r13d, %edi
                subl %r12d, %edi
                movl %edi, %r14d
                leal 25(%r14), %edi
                call kof_alloc
                movq %rax, %r15
                movl $1, (%r15)
                movl $0, 4(%r15)
                movq $0, 8(%r15)
                movl %r14d, 16(%r15)
                movl $0, 20(%r15)
                movq %r15, %rdi
                addq $24, %rdi
                movq %rbx, %rsi
                addq $24, %rsi
                movl %r12d, %eax
                addq %rax, %rsi
                movl %r14d, %edx
                call kof_memcpy
                movb $0, 24(%r15,%r14)
                movq %r15, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_substr_bounds:
                movl %r12d, %edi
                movl %r13d, %esi
                call kof_bounds_error
            """);
    }


    private static void emitStringContains(StringBuilder sb) {
        sb.append("""
            .globl kof_string_contains
            .type kof_string_contains, @function
            kof_string_contains:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%r12), %r13d
                testl %r13d, %r13d
                jz .Lkof_strcontains_found
                movl 16(%rbx), %r14d
                cmpl %r14d, %r13d
                jg .Lkof_strcontains_no
                xorq %rcx, %rcx
            .Lkof_strcontains_outer:
                cmpl %r14d, %ecx
                jge .Lkof_strcontains_no
                leaq 24(%rbx,%rcx), %rax
                xorq %rdx, %rdx
            .Lkof_strcontains_inner:
                cmpl %r13d, %edx
                jge .Lkof_strcontains_found
                movzbl (%rax,%rdx), %r8d
                movzbl 24(%r12,%rdx), %r9d
                cmpl %r9d, %r8d
                jne .Lkof_strcontains_next
                incq %rdx
                jmp .Lkof_strcontains_inner
            .Lkof_strcontains_next:
                incq %rcx
                jmp .Lkof_strcontains_outer
            .Lkof_strcontains_found:
                movl $1, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_strcontains_no:
                xorl %eax, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }


    private static void emitStringStartsWith(StringBuilder sb) {
        sb.append("""
            .globl kof_string_starts_with
            .type kof_string_starts_with, @function
            kof_string_starts_with:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%r12), %r13d
                movl 16(%rbx), %ecx
                cmpl %ecx, %r13d
                jg .Lkof_strstarts_no
                xorq %rcx, %rcx
            .Lkof_strstarts_loop:
                cmpl %r13d, %ecx
                jge .Lkof_strstarts_found
                movzbl 24(%rbx,%rcx), %eax
                cmpb %al, 24(%r12,%rcx)
                jne .Lkof_strstarts_no
                incq %rcx
                jmp .Lkof_strstarts_loop
            .Lkof_strstarts_found:
                movl $1, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_strstarts_no:
                xorl %eax, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }


    private static void emitStringEndsWith(StringBuilder sb) {
        sb.append("""
            .globl kof_string_ends_with
            .type kof_string_ends_with, @function
            kof_string_ends_with:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%r12), %r13d
                movl 16(%rbx), %r14d
                cmpl %r14d, %r13d
                jg .Lkof_strends_no
                movl %r14d, %ecx
                subl %r13d, %ecx
            .Lkof_strends_loop:
                cmpl %r14d, %ecx
                jge .Lkof_strends_found
                movzbl 24(%rbx,%rcx), %eax
                movl %ecx, %edx
                addl %r13d, %edx
                subl %r14d, %edx
                movzbl 24(%r12,%rdx), %edx
                cmpl %edx, %eax
                jne .Lkof_strends_no
                incq %rcx
                jmp .Lkof_strends_loop
            .Lkof_strends_found:
                movl $1, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_strends_no:
                xorl %eax, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }


    private static void emitStringIndexOf(StringBuilder sb) {
        sb.append("""
            .globl kof_string_index_of
            .type kof_string_index_of, @function
            kof_string_index_of:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%rbx), %r13d
                movl 16(%r12), %r14d
                testl %r14d, %r14d
                jz .Lkof_idx_found0
                cmpl %r13d, %r14d
                jg .Lkof_idx_notfound
                xorl %r15d, %r15d
            .Lkof_idx_outer:
                movl %r13d, %eax
                subl %r14d, %eax
                cmpl %eax, %r15d
                jg .Lkof_idx_notfound
                xorl %ecx, %ecx
            .Lkof_idx_inner:
                cmpl %r14d, %ecx
                jge .Lkof_idx_found
                movl %r15d, %eax
                addl %ecx, %eax
                movzbl 24(%rbx,%rax), %eax
                movzbl 24(%r12,%rcx), %edx
                cmpl %edx, %eax
                jne .Lkof_idx_next
                incq %rcx
                jmp .Lkof_idx_inner
            .Lkof_idx_next:
                incl %r15d
                jmp .Lkof_idx_outer
            .Lkof_idx_found0:
                xorl %r15d, %r15d
            .Lkof_idx_found:
                movl %r15d, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_idx_notfound:
                movl $-1, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }


    private static void emitStringTrim(StringBuilder sb) {
        sb.append("""
            .globl kof_string_trim
            .type kof_string_trim, @function
            kof_string_trim:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movl 16(%rbx), %r12d
                xorl %r13d, %r13d
            .Lkof_trim_lead:
                cmpl %r12d, %r13d
                jge .Lkof_trim_done
                movzbl 24(%rbx,%r13), %eax
                cmpb $32, %al
                je .Lkof_trim_skip
                cmpb $9, %al
                je .Lkof_trim_skip
                cmpb $10, %al
                je .Lkof_trim_skip
                cmpb $13, %al
                je .Lkof_trim_skip
                jmp .Lkof_trim_trail
            .Lkof_trim_skip:
                incl %r13d
                jmp .Lkof_trim_lead
            .Lkof_trim_trail:
                movl %r12d, %r14d
            .Lkof_trim_trail_loop:
                cmpl %r13d, %r14d
                jle .Lkof_trim_done
                decl %r14d
                movzbl 24(%rbx,%r14), %eax
                cmpb $32, %al
                je .Lkof_trim_trail_loop
                cmpb $9, %al
                je .Lkof_trim_trail_loop
                cmpb $10, %al
                je .Lkof_trim_trail_loop
                cmpb $13, %al
                je .Lkof_trim_trail_loop
                incl %r14d
            .Lkof_trim_done:
                movl %r14d, %eax
                subl %r13d, %eax
                movl %eax, %r12d
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r15
                movl $1, (%r15)
                movl $0, 4(%r15)
                movq $0, 8(%r15)
                movl %r12d, 16(%r15)
                movl $0, 20(%r15)
                leaq 24(%r15), %rdi
                leaq 24(%rbx), %rsi
                addq %r13, %rsi
                movl %r12d, %edx
                call kof_memcpy
                movb $0, 24(%r15,%r12)
                movq %r15, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }


    private static void emitStringCase(StringBuilder sb) {
        sb.append("""
            .globl kof_string_to_upper
            .type kof_string_to_upper, @function
            kof_string_to_upper:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movl 16(%rbx), %r12d
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, (%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl %r12d, 16(%r13)
                movl $0, 20(%r13)
                xorl %ecx, %ecx
            .Lkof_upper_loop:
                cmpl %r12d, %ecx
                jge .Lkof_upper_done
                movzbl 24(%rbx,%rcx), %eax
                cmpb $97, %al
                jb .Lkof_upper_store
                cmpb $122, %al
                ja .Lkof_upper_store
                subl $32, %eax
            .Lkof_upper_store:
                movb %al, 24(%r13,%rcx)
                incq %rcx
                jmp .Lkof_upper_loop
            .Lkof_upper_done:
                movb $0, 24(%r13,%r12)
                movq %r13, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .globl kof_string_to_lower
            .type kof_string_to_lower, @function
            kof_string_to_lower:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movl 16(%rbx), %r12d
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, (%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl %r12d, 16(%r13)
                movl $0, 20(%r13)
                xorl %ecx, %ecx
            .Lkof_lower_loop:
                cmpl %r12d, %ecx
                jge .Lkof_lower_done
                movzbl 24(%rbx,%rcx), %eax
                cmpb $65, %al
                jb .Lkof_lower_store
                cmpb $90, %al
                ja .Lkof_lower_store
                addl $32, %eax
            .Lkof_lower_store:
                movb %al, 24(%r13,%rcx)
                incq %rcx
                jmp .Lkof_lower_loop
            .Lkof_lower_done:
                movb $0, 24(%r13,%r12)
                movq %r13, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }


    private static void emitStringReplace(StringBuilder sb) {
        sb.append("""
            .globl kof_string_replace_char
            .type kof_string_replace_char, @function
            kof_string_replace_char:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movl %esi, %r12d
                movl %edx, %r13d
                movl 16(%rbx), %r14d
                leal 25(%r14), %edi
                call kof_alloc
                movq %rax, %r15
                movl $1, (%r15)
                movl $0, 4(%r15)
                movq $0, 8(%r15)
                movl %r14d, 16(%r15)
                movl $0, 20(%r15)
                xorl %ecx, %ecx
            .Lkof_replace_char_loop:
                cmpl %r14d, %ecx
                jge .Lkof_replace_char_done
                movzbl 24(%rbx,%rcx), %eax
                cmpl %r12d, %eax
                jne .Lkof_replace_char_store
                movl %r13d, %eax
            .Lkof_replace_char_store:
                movb %al, 24(%r15,%rcx)
                incq %rcx
                jmp .Lkof_replace_char_loop
            .Lkof_replace_char_done:
                movb $0, 24(%r15,%r14)
                movq %r15, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_string_replace
            .type kof_string_replace, @function
            kof_string_replace:
                pushq %rbp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx          # str
                movq %rsi, %r12          # from (substring, not a single char)
                movq %rdx, %r13          # to
                movl 16(%rbx), %r14d     # str_len
                movl 16(%r12), %r15d     # from_len
                testl %r15d, %r15d
                jnz .Lkof_replace_count
                # empty `from`: return a copy of str unchanged
                leal 25(%r14), %edi
                call kof_alloc
                movq %rax, %r15
                movl $1, (%r15)
                movl $0, 4(%r15)
                movq $0, 8(%r15)
                movl %r14d, 16(%r15)
                movl $0, 20(%r15)
                xorl %ecx, %ecx
            .Lkof_replace_copy_empty:
                cmpl %r14d, %ecx
                jge .Lkof_replace_done_empty
                movzbl 24(%rbx,%rcx), %eax
                movb %al, 24(%r15,%rcx)
                incq %rcx
                jmp .Lkof_replace_copy_empty
            .Lkof_replace_done_empty:
                movb $0, 24(%r15,%r14)
                movq %r15, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                popq %rbp
                ret
            .Lkof_replace_count:
                movl 16(%r13), %eax
                movl %eax, %r8d         # to_len
                xorl %ebp, %ebp         # occurrence count (callee-saved)
                xorl %ecx, %ecx         # scan position i
            .Lkof_replace_scan:
                cmpl %r14d, %ecx
                jge .Lkof_replace_alloc
                xorl %r10d, %r10d       # j
            .Lkof_replace_cmp:
                cmpl %r15d, %r10d
                jge .Lkof_replace_match
                leaq 24(%rbx,%rcx), %rax
                movzbl (%rax,%r10), %eax
                movzbl 24(%r12,%r10), %edx
                cmpb %dl, %al
                jne .Lkof_replace_no_match
                incq %r10
                jmp .Lkof_replace_cmp
            .Lkof_replace_match:
                incl %ebp
                addl %r15d, %ecx
                jmp .Lkof_replace_scan
            .Lkof_replace_no_match:
                incq %rcx
                jmp .Lkof_replace_scan
            .Lkof_replace_alloc:
                # result_len = str_len + count * (to_len - from_len)
                movl %r8d, %eax
                subl %r15d, %eax
                imull %ebp, %eax
                addl %r14d, %eax
                leal 25(%rax), %edi
                call kof_alloc
                movq %rax, %r9          # out
                movl $1, (%r9)
                movl $0, 4(%r9)
                movq $0, 8(%r9)
                movl $0, 16(%r9)        # length fixed at the end
                movl $0, 20(%r9)
                movl 16(%r13), %r8d     # to_len (restored after alloc)
                xorl %ecx, %ecx         # i (scan pos)
                xorl %r11d, %r11d       # k (out pos)
            .Lkof_replace_build:
                cmpl %r14d, %ecx
                jge .Lkof_replace_done
                xorl %r10d, %r10d       # j
            .Lkof_replace_bcmp:
                cmpl %r15d, %r10d
                jge .Lkof_replace_bmatch
                leaq 24(%rbx,%rcx), %rax
                movzbl (%rax,%r10), %eax
                movzbl 24(%r12,%r10), %edx
                cmpb %dl, %al
                jne .Lkof_replace_bcopy
                incq %r10
                jmp .Lkof_replace_bcmp
            .Lkof_replace_bmatch:
                xorl %r10d, %r10d
            .Lkof_replace_bto:
                cmpl %r8d, %r10d
                jge .Lkof_replace_bskip
                movzbl 24(%r13,%r10), %eax
                movb %al, 24(%r9,%r11)
                incq %r10
                incq %r11
                jmp .Lkof_replace_bto
            .Lkof_replace_bskip:
                addl %r15d, %ecx
                jmp .Lkof_replace_build
            .Lkof_replace_bcopy:
                movzbl 24(%rbx,%rcx), %eax
                movb %al, 24(%r9,%r11)
                incq %rcx
                incq %r11
                jmp .Lkof_replace_build
            .Lkof_replace_done:
                movb $0, 24(%r9,%r11)
                movl %r11d, 16(%r9)
                movq %r9, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                popq %rbp
                ret
            """);
    }


    private static void emitStringEqualsIgnoreCase(StringBuilder sb) {
        sb.append("""
            .globl kof_string_equals_ignore_case
            .type kof_string_equals_ignore_case, @function
            kof_string_equals_ignore_case:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%rbx), %r13d
                movl 16(%r12), %r14d
                cmpl %r14d, %r13d
                jne .Lkof_eqic_no
                xorl %ecx, %ecx
            .Lkof_eqic_loop:
                cmpl %r13d, %ecx
                jge .Lkof_eqic_yes
                movzbl 24(%rbx,%rcx), %eax
                movzbl 24(%r12,%rcx), %edx
                cmpl %edx, %eax
                je .Lkof_eqic_next
                cmpb $65, %al
                jb .Lkof_eqic_no
                cmpb $90, %al
                ja .Lkof_eqic_try_up
                addl $32, %eax
                cmpl %edx, %eax
                je .Lkof_eqic_next
                jmp .Lkof_eqic_no
            .Lkof_eqic_try_up:
                cmpb $97, %al
                jb .Lkof_eqic_no
                cmpb $122, %al
                ja .Lkof_eqic_no
                subl $32, %eax
                cmpl %edx, %eax
                je .Lkof_eqic_next
                jmp .Lkof_eqic_no
            .Lkof_eqic_next:
                incq %rcx
                jmp .Lkof_eqic_loop
            .Lkof_eqic_yes:
                movl $1, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_eqic_no:
                xorl %eax, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }


    private static void emitStringSplit(StringBuilder sb) {
        sb.append("""
            .globl kof_string_split
            .type kof_string_split, @function
            kof_string_split:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                pushq %r8
                pushq %r9
                pushq %r10
                pushq %r11
                movq %rdi, %rbx
                movl %esi, %r12d
                movl 16(%rbx), %r13d
                movl $1, %r14d
                xorl %ecx, %ecx
            .Lkof_split_count:
                cmpl %r13d, %ecx
                jge .Lkof_split_alloc
                movzbl 24(%rbx,%rcx), %eax
                cmpl %r12d, %eax
                jne .Lkof_split_count_next
                incl %r14d
            .Lkof_split_count_next:
                incq %rcx
                jmp .Lkof_split_count
            .Lkof_split_alloc:
                movl %r14d, %edi
                movq $8, %rsi
                call kof_array_alloc
                movq %rax, %r15
                xorl %r8d, %r8d
                xorl %ecx, %ecx
                xorl %r9d, %r9d
            .Lkof_split_outer:
                cmpl %r13d, %ecx
                jge .Lkof_split_lastpiece
                movzbl 24(%rbx,%rcx), %eax
                cmpl %r12d, %eax
                jne .Lkof_split_outer_next
            .Lkof_split_piece:
                movl %ecx, %eax
                subl %r9d, %eax
                movl %eax, %r10d
                movq %rcx, 0(%rsp)
                movq %r9, 8(%rsp)
                movq %r10, 16(%rsp)
                movq %r8, 24(%rsp)
                leal 25(%r10), %edi
                call kof_alloc
                movq %rax, %r11
                movq 24(%rsp), %r8
                movq 16(%rsp), %r10
                movq 8(%rsp), %r9
                movl $1, (%r11)
                movl $0, 4(%r11)
                movq $0, 8(%r11)
                movl %r10d, 16(%r11)
                movl $0, 20(%r11)
                leaq 24(%r11), %rdi
                leaq 24(%rbx), %rsi
                addq %r9, %rsi
                movl %r10d, %edx
                call kof_memcpy
                movb $0, 24(%r11,%r10)
                movq %r11, %rax
                movq %r8, %rcx
                shlq $3, %rcx
                movq %rax, 24(%r15,%rcx)
                movq 0(%rsp), %rcx
                incl %r8d
                cmpl %r13d, %ecx
                jge .Lkof_split_done
                incl %ecx
                movq %rcx, %r9
                jmp .Lkof_split_outer
            .Lkof_split_outer_next:
                incq %rcx
                jmp .Lkof_split_outer
            .Lkof_split_lastpiece:
                cmpl %r13d, %r9d
                jge .Lkof_split_done
                movl %r13d, %ecx
                jmp .Lkof_split_piece
            .Lkof_split_done:
                movq %r15, %rax
                popq %r11
                popq %r10
                popq %r9
                popq %r8
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }




    static final int KOF_ARRAY_TYPE_ID = 2;
    static final int KOF_ARRAY_HEADER_SIZE = 24;


    private static void emitArrayAlloc(StringBuilder sb) {
        sb.append("""
            .globl kof_array_alloc
            .type kof_array_alloc, @function
            kof_array_alloc:
                pushq %rbx
                pushq %r12
                movl %edi, %ebx
                movl %esi, %r12d
                movq %rbx, %rax
                imulq %r12, %rax
                addq $24, %rax
                movq %rax, %rdi
                call kof_alloc
                movq %rax, %rcx
                movl $2, 0(%rcx)
                movl $0, 4(%rcx)
                movq $0, 8(%rcx)
                movl %ebx, 16(%rcx)
                movl %r12d, 20(%rcx)
                movq %rcx, %rax
                popq %r12
                popq %rbx
                ret
            """);
    }


    private static void emitArrayLength(StringBuilder sb) {
        sb.append("""
            .globl kof_array_length
            .type kof_array_length, @function
            kof_array_length:
                movl 16(%rdi), %eax
                ret
            """);
    }


    private static void emitArrayGet(StringBuilder sb) {
        sb.append("""
            .globl kof_array_get
            .type kof_array_get, @function
            kof_array_get:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movl %esi, %r12d
                testq %rbx, %rbx
                jz .Lkof_array_get_null
                movl 16(%rbx), %ecx
                cmpl %ecx, %r12d
                jge .Lkof_array_get_bounds
                cmpl $0, %r12d
                jl .Lkof_array_get_bounds
                movl 20(%rbx), %edx
                movq %r12, %rax
                imulq %rdx, %rax
                addq $24, %rax
                addq %rbx, %rax
                cmpl $8, %edx
                je .Lkof_array_get_q
                cmpl $4, %edx
                je .Lkof_array_get_d
                cmpl $2, %edx
                je .Lkof_array_get_w
                movzbl (%rax), %eax
                jmp .Lkof_array_get_done
            .Lkof_array_get_w:
                movzwl (%rax), %eax
                jmp .Lkof_array_get_done
            .Lkof_array_get_d:
                movl (%rax), %eax
                jmp .Lkof_array_get_done
            .Lkof_array_get_q:
                movq (%rax), %rax
            .Lkof_array_get_done:
                popq %r12
                popq %rbx
                ret
            .Lkof_array_get_null:
                call kof_null_error
            .Lkof_array_get_bounds:
                movl %r12d, %edi
                movl 16(%rbx), %esi
                call kof_bounds_error
            """);
    }


    private static void emitArraySet(StringBuilder sb) {
        sb.append("""
            .globl kof_array_set
            .type kof_array_set, @function
            kof_array_set:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movl %esi, %r12d
                movq %rdx, %r13
                testq %rbx, %rbx
                jz .Lkof_array_set_null
                movl 16(%rbx), %ecx
                cmpl %ecx, %r12d
                jge .Lkof_array_set_bounds
                cmpl $0, %r12d
                jl .Lkof_array_set_bounds
                movl 20(%rbx), %edx
                movq %r12, %rax
                imulq %rdx, %rax
                addq $24, %rax
                addq %rbx, %rax
                cmpl $8, %edx
                je .Lkof_array_set_q
                cmpl $4, %edx
                je .Lkof_array_set_d
                cmpl $2, %edx
                je .Lkof_array_set_w
                movb %r13b, (%rax)
                jmp .Lkof_array_set_done
            .Lkof_array_set_w:
                movw %r13w, (%rax)
                jmp .Lkof_array_set_done
            .Lkof_array_set_d:
                movl %r13d, (%rax)
                jmp .Lkof_array_set_done
            .Lkof_array_set_q:
                movq %r13, (%rax)
            .Lkof_array_set_done:
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_array_set_null:
                call kof_null_error
            .Lkof_array_set_bounds:
                movl %r12d, %edi
                movl 16(%rbx), %esi
                call kof_bounds_error
            """);
    }




    /**
     * kof.log no Native — mesmo contrato do JVM testado por KofLogE2ETest:
     * "yyyy-MM-dd HH:mm:ss.SSS LEVEL msg"; KOF_LOG_LEVEL filtra
     * (debug&lt;info&lt;warn&lt;error&lt;off, default info); warn/error vão
     * para stderr. Data civil via clock_gettime + conversão de dias da
     * época (algoritmo de Hinnant) em aritmética inteira pura, sem libc.
     * Delta documentado: horário é UTC (JVM usa fuso local) e KOF_LOG_JSON
     * ainda não tem efeito no Native.
     */
    private static void emitLogFunctions(StringBuilder sb) {
        sb.append("""
            .section .data
            kof_log_threshold: .quad -1
            # KofString "KOF_LOG_LEVEL" (layout: len em +16, data em +24)
            .Llog_env_kstr:
                .quad 0
                .quad 0
                .long 13
                .long 0
                .ascii "KOF_LOG_LEVEL"
            .Llog_env_name:    .asciz "KOF_LOG_LEVEL="
            .Llog_w_debug:     .asciz "debug"
            .Llog_w_info:      .asciz "info"
            .Llog_w_warn:      .asciz "warn"
            .Llog_w_warning:   .asciz "warning"
            .Llog_w_error:     .asciz "error"
            .Llog_w_off:       .asciz "off"
            .Llog_lbl_debug:   .asciz "DEBUG"
            .Llog_lbl_info:    .asciz "INFO"
            .Llog_lbl_warn:    .asciz "WARN"
            .Llog_lbl_error:   .asciz "ERROR"
            .Llog_nullmsg:     .asciz "null"
            .section .text
            .globl kof_log_debug
            .type kof_log_debug, @function
            kof_log_debug:
                # convenção nativa: 1º argumento (msg) chega em rdi
                movq %rdi, %rdx
                movq $0, %rdi
                leaq .Llog_lbl_debug(%rip), %rsi
                jmp kof_log_write

            .globl kof_log_info
            .type kof_log_info, @function
            kof_log_info:
                # convenção nativa: 1º argumento (msg) chega em rdi
                movq %rdi, %rdx
                movq $1, %rdi
                leaq .Llog_lbl_info(%rip), %rsi
                jmp kof_log_write

            .globl kof_log_warn
            .type kof_log_warn, @function
            kof_log_warn:
                # convenção nativa: 1º argumento (msg) chega em rdi
                movq %rdi, %rdx
                movq $2, %rdi
                leaq .Llog_lbl_warn(%rip), %rsi
                jmp kof_log_write

            .globl kof_log_error
            .type kof_log_error, @function
            kof_log_error:
                # convenção nativa: 1º argumento (msg) chega em rdi
                movq %rdi, %rdx
                movq $3, %rdi
                leaq .Llog_lbl_error(%rip), %rsi
                jmp kof_log_write

            # .Llog_ci_eq(rdi=candidato, rsi=bytes, edx=len) -> eax=1 se igual (case-insensitive)
            .Llog_ci_eq:
                xorl %eax, %eax
                testl %edx, %edx
                jle .Llog_ci_no
            .Llog_ci_loop:
                movzbl (%rdi), %r8d
                movzbl (%rsi), %r9d
                orb $0x20, %r8b
                orb $0x20, %r9b
                cmpl %r9d, %r8d
                jne .Llog_ci_no
                incq %rdi
                incq %rsi
                decl %edx
                jnz .Llog_ci_loop
                movl $1, %eax
            .Llog_ci_no:
                ret

            # .Llog_parse_level -> rax = threshold (lazy, uma vez por processo).
            # Autocontido: abre /proc/self/environ e procura "KOF_LOG_LEVEL="
            # (o kof_sec_secret_get espera KofString e não serve aqui).
            .Llog_parse_level:
                pushq %rbx
                pushq %r12
                pushq %r13
                subq $16384, %rsp
                leaq .Lsec_environ_path(%rip), %rdi
                xorq %rsi, %rsi
                xorq %rdx, %rdx
                movq $2, %rax               # SYS_open
                syscall
                testq %rax, %rax
                js .Llog_pl_default
                movq %rax, %r12             # fd
                movq %r12, %rdi
                leaq 0(%rsp), %rsi
                movq $16384, %rdx
                xorq %rax, %rax             # SYS_read
                syscall
                movq %rax, %r13             # bytes lidos
                movq %r12, %rdi
                movq $3, %rax               # close
                syscall
                cmpq $15, %r13
                jl .Llog_pl_default
                xorq %rbx, %rbx             # índice no buffer
            .Llog_scan:
                movq %rsp, %r8
                addq %rbx, %r8
                leaq .Llog_env_name(%rip), %r10
                xorq %r9, %r9
            .Llog_pcmp:
                cmpq $14, %r9
                je .Llog_pfound
                leaq (%rbx,%r9), %rdx
                cmpq %r13, %rdx
                jge .Llog_pl_default
                movzbl (%r8,%r9), %eax
                movzbl (%r10,%r9), %ecx
                cmpl %ecx, %eax
                jne .Llog_padvance
                incq %r9
                jmp .Llog_pcmp
            .Llog_padvance:
                incq %rbx
                jmp .Llog_scan
            .Llog_pfound:
                leaq 14(%r8), %rsi          # valor
                xorl %edx, %edx             # len até NUL
            .Llog_vlen:
                movq %rbx, %rax
                addq $14, %rax               # salta o prefixo "KOF_LOG_LEVEL="
                addq %rdx, %rax
                cmpq %r13, %rax
                jge .Llog_vdone
                cmpb $0, (%rsp,%rax)
                je .Llog_vdone
                incq %rdx
                jmp .Llog_vlen
            .Llog_vdone:
                call .Llog_ci_word
                # dispatch pelo comprimento (debug5 info4 warn4 warning7 error5 off3)
                movl %edx, %ebx
                cmpq $7, %rdx
                jne .Llog_pl_5
                leaq .Llog_w_warning(%rip), %rdi
                call .Llog_ci_eq2
                testl %eax, %eax
                jnz .Llog_pl_warn
                jmp .Llog_pl_default
            .Llog_pl_5:
                cmpq $5, %rdx
                jne .Llog_pl_4
                movl %ebx, %edx
                leaq .Llog_w_debug(%rip), %rdi
                call .Llog_ci_eq2
                testl %eax, %eax
                jnz .Llog_pl_debug
                movl %ebx, %edx
                leaq .Llog_w_error(%rip), %rdi
                call .Llog_ci_eq2
                testl %eax, %eax
                jnz .Llog_pl_error
                jmp .Llog_pl_default
            .Llog_pl_4:
                cmpq $4, %rdx
                jne .Llog_pl_3
                movl %ebx, %edx
                leaq .Llog_w_info(%rip), %rdi
                call .Llog_ci_eq2
                testl %eax, %eax
                jnz .Llog_pl_default
                movl %ebx, %edx
                leaq .Llog_w_warn(%rip), %rdi
                call .Llog_ci_eq2
                testl %eax, %eax
                jnz .Llog_pl_warn
                jmp .Llog_pl_default
            .Llog_pl_3:
                cmpq $3, %rdx
                jne .Llog_pl_default
                movl %ebx, %edx
                leaq .Llog_w_off(%rip), %rdi
                call .Llog_ci_eq2
                testl %eax, %eax
                jnz .Llog_pl_off
                jmp .Llog_pl_default
            .Llog_pl_debug:
                movq $0, %rax
                jmp .Llog_pl_exit
            .Llog_pl_warn:
                movq $2, %rax
                jmp .Llog_pl_exit
            .Llog_pl_error:
                movq $3, %rax
                jmp .Llog_pl_exit
            .Llog_pl_off:
                movq $4, %rax
                jmp .Llog_pl_exit
            .Llog_pl_default:
                movq $1, %rax
            .Llog_pl_exit:
                addq $16384, %rsp
                popq %r13
                popq %r12
                popq %rbx
                ret

            # .Llog_ci_word(rsi=valor, edx=len) -> eax=1 se é uma das palavras válidas
            .Llog_ci_word:
                cmpq $7, %rdx
                je .Llog_ciw_yes
                cmpq $5, %rdx
                je .Llog_ciw_yes
                cmpq $4, %rdx
                je .Llog_ciw_yes
                cmpq $3, %rdx
                je .Llog_ciw_yes
                xorl %eax, %eax
                ret
            .Llog_ciw_yes:
                movl $1, %eax
                ret

            # .Llog_ci_eq2(rdi=candidato lowercase, rsi=bytes, edx=len) -> eax=1 se igual
            .Llog_ci_eq2:
                pushq %rbx
                movl %edx, %ebx
                xorl %eax, %eax
                testl %ebx, %ebx
                jle .Llog_ci2_no
            .Llog_ci2_loop:
                movzbl (%rdi), %r8d
                movzbl (%rsi), %r9d
                orb $0x20, %r8b
                orb $0x20, %r9b
                cmpl %r9d, %r8d
                jne .Llog_ci2_no
                incq %rdi
                incq %rsi
                decl %ebx
                jnz .Llog_ci2_loop
                movl $1, %eax
            .Llog_ci2_no:
                popq %rbx
                ret

            # kof_log_write(rdi=level, rsi=label cstr, rdx=msg KofString|0)
            # slots locais: 0..15 timespec | 16 hh | 20 mi | 24 ss | 28 ms
            #               32 year | 36 mon | 40 day | 44 tempA | 48 tempB
            #               52 doe  | 56 era | 60 epochsec
            kof_log_write:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $64, %rsp
                movq %rdi, %r12
                movq %rsi, %r13
                movq %rdx, %r14
                movq $-1, 44(%rsp)
                # threshold lazy
                movq kof_log_threshold(%rip), %rax
                cmpq $-1, %rax
                jne .Llog_have_thresh
                call .Llog_parse_level
                movq %rax, kof_log_threshold(%rip)
                movq kof_log_threshold(%rip), %rax
            .Llog_have_thresh:
                cmpq %rax, %r12
                jl .Llog_suppressed
                # clock_gettime(CLOCK_REALTIME)
                leaq 0(%rsp), %rdi
                xorq %rsi, %rsi
                movq $96, %rax
                syscall
                movq 8(%rsp), %rax           # nsec
                xorq %rdx, %rdx
                movq $1000000, %rcx
                divq %rcx
                movl %eax, 28(%rsp)          # ms
                movq 0(%rsp), %rax           # epoch sec (timespec já é em segundos)
                movq %rax, 60(%rsp)
                # hora do dia
                movq 60(%rsp), %rax
                xorq %rdx, %rdx
                movq $86400, %rcx
                divq %rcx                    # rax=dias, rdx=secs do dia
                movq %rdx, %rax
                xorq %rdx, %rdx
                movq $3600, %rcx
                divq %rcx
                movl %eax, 16(%rsp)          # hh
                movq %rdx, %rax
                xorq %rdx, %rdx
                movq $60, %rcx
                divq %rcx
                movl %eax, 20(%rsp)          # mi
                movl %edx, 24(%rsp)          # ss
                # data civil (Hinnant) — só registradores, sem slots:
                # r8=era, r9=doe, r10/r11/rdi temporários
                movq 60(%rsp), %rax
                xorq %rdx, %rdx
                movq $86400, %rcx
                divq %rcx                     # rax = dias
                addq $719468, %rax            # z
                movq $146097, %rcx
                xorq %rdx, %rdx
                divq %rcx                     # rax=era, rdx=doe
                movq %rax, %r8
                movq %rdx, %r9
                # N = doe - doe/1460 + doe/36524 - doe/146096
                movq %r9, %rax
                xorq %rdx, %rdx
                movq $1460, %rcx
                divq %rcx
                movq %rax, %r10               # doe/1460
                movq %r9, %rax
                xorq %rdx, %rdx
                movq $36524, %rcx
                divq %rcx
                movq %rax, %r11               # doe/36524
                movq %r9, %rax
                xorq %rdx, %rdx
                movq $146096, %rcx
                divq %rcx                     # rax = doe/146096
                movq %r9, %rdi
                subq %r10, %rdi
                addq %r11, %rdi
                subq %rax, %rdi               # N
                movq $365, %rcx
                xorq %rdx, %rdx
                movq %rdi, %rax
                divq %rcx                     # rax = yoe
                movq %rax, %r11               # r11 = yoe
                # ano = yoe + era*400
                movq %r11, %rax
                imulq $400, %r8
                addq %r8, %rax
                movq %rax, %r10               # r10 = year (provisório)
                # doy = doe(r9) - (365*yoe + yoe/4 - yoe/100)
                movq %r11, %rax
                imulq $365, %rax
                movq %rax, %rdi               # rdi = 365*yoe
                movq %r11, %rax
                shrq $2, %rax
                addq %rax, %rdi               # + yoe/4
                movq %r11, %rax
                xorq %rdx, %rdx
                movq $100, %rcx
                divq %rcx
                subq %rax, %rdi               # - yoe/100
                movq %r9, %rax
                subq %rdi, %rax               # doy
                movq %rax, %r9                # r9 = doy
                # mp = (5*doy + 2)/153
                imulq $5, %rax
                addq $2, %rax
                xorq %rdx, %rdx
                movq $153, %rcx
                divq %rcx                     # rax = mp
                movq %rax, %r11               # r11 = mp (yoe livre agora)
                # day = doy - (153*mp+2)/5 + 1
                imulq $153, %rax
                addq $2, %rax
                xorq %rdx, %rdx
                movq $5, %rcx
                divq %rcx                     # rax = correção
                movq %r9, %rdi                # doy
                subq %rax, %rdi
                incq %rdi                     # day
                # month = mp + 3 - 12*(mp/10)
                movq %r11, %rax
                xorq %rdx, %rdx
                movq $10, %rcx
                divq %rcx
                imulq $12, %rax
                movq %rax, %r8                # r8 = 12*(mp/10) (era livre)
                movq %r11, %rax
                addq $3, %rax
                subq %r8, %rax                # month
                movl %r10d, 32(%rsp)          # year
                movl %eax, 36(%rsp)           # month
                movl %edi, 40(%rsp)           # day
                cmpl $2, 36(%rsp)
                jg .Llog_year_ok
                incl 32(%rsp)
            .Llog_year_ok:
                # buffer = msglen + 80
                movq $4, %rdi
                testq %r14, %r14
                jz .Llog_alloc
                movl 16(%r14), %edi
                addq $4, %rdi
            .Llog_alloc:
                addq $80, %rdi
                call kof_alloc
                movq %rax, %r15
                movq %rax, %rbx
                # ano (4 dígitos)
                movl 32(%rsp), %eax
                xorl %edx, %edx
                movl $1000, %r8d
                divl %r8d
                addb $48, %al
                movb %al, (%rbx)
                # resto em edx: centena
                movl %edx, %eax
                xorl %edx, %edx
                movl $100, %r8d
                divl %r8d
                addb $48, %al
                movb %al, 1(%rbx)
                movl %edx, %eax
                xorl %edx, %edx
                movl $10, %r8d
                divl %r8d
                addb $48, %al
                movb %al, 2(%rbx)
                addb $48, %dl
                movb %dl, 3(%rbx)
                addq $4, %rbx
                movb $45, (%rbx)
                incq %rbx
                movl 36(%rsp), %eax
                call .Llog_put2_at_bx
                movb $45, (%rbx)
                incq %rbx
                movl 40(%rsp), %eax
                call .Llog_put2_at_bx
                movb $32, (%rbx)
                incq %rbx
                movl 16(%rsp), %eax
                call .Llog_put2_at_bx
                movb $58, (%rbx)
                incq %rbx
                movl 20(%rsp), %eax
                call .Llog_put2_at_bx
                movb $58, (%rbx)
                incq %rbx
                movl 24(%rsp), %eax
                call .Llog_put2_at_bx
                movb $46, (%rbx)
                incq %rbx
                movl 28(%rsp), %eax
                call .Llog_put3_at_bx
                movb $32, (%rbx)
                incq %rbx
                # label
                movq %r13, %rax
            .Llog_copy_label:
                movzbl (%rax), %ecx
                testl %ecx, %ecx
                jz .Llog_label_done
                movb %cl, (%rbx)
                incq %rax
                incq %rbx
                jmp .Llog_copy_label
            .Llog_label_done:
                movb $32, (%rbx)
                incq %rbx
                # mensagem
                testq %r14, %r14
                jnz .Llog_copy_msg
                leaq .Llog_nullmsg(%rip), %rax
            .Llog_copy_loop:
                movzbl (%rax), %ecx
                testl %ecx, %ecx
                jz .Llog_msg_done
                movb %cl, (%rbx)
                incq %rax
                incq %rbx
                jmp .Llog_copy_loop
            .Llog_copy_msg:
                movl 16(%r14), %ecx
                testl %ecx, %ecx
                jle .Llog_msg_done
                leaq 24(%r14), %rax
                movl %ecx, %edx
            .Llog_copy_bytes:
                movzbl (%rax), %ecx
                movb %cl, (%rbx)
                incq %rax
                incq %rbx
                decl %edx
                jnz .Llog_copy_bytes
            .Llog_msg_done:
                movb $10, (%rbx)
                incq %rbx
                # write(fd, buf, len)
                movq %rbx, %rdx
                subq %r15, %rdx
                movq %r15, %rsi
                movq $1, %rax
                cmpq $2, %r12
                jl .Llog_fd_stdout
                movq $2, %rdi
                jmp .Llog_do_write
            .Llog_fd_stdout:
                movq $1, %rdi
            .Llog_do_write:
                syscall
                movq %r15, %rdi
                call kof_free
            .Llog_suppressed:
                addq $64, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # helpers que usam %rbx como cursor (dentro de kof_log_write)
            .Llog_put2_at_bx:
                pushq %rax
                movl %eax, %ecx
                xorl %edx, %edx
                movl $10, %r8d
                divl %r8d
                addb $48, %al
                movb %al, (%rbx)
                addb $48, %dl
                movb %dl, 1(%rbx)
                addq $2, %rbx
                popq %rax
                ret
            .Llog_put3_at_bx:
                pushq %rax
                movl %eax, %ecx
                xorl %edx, %edx
                movl $100, %r8d
                divl %r8d
                addb $48, %al
                movb %al, (%rbx)
                incq %rbx                    # centena gravada; dezena/unidade via put2
                movl %edx, %eax
                call .Llog_put2_at_bx
                popq %rax
                ret
            """);
    }


    /**
     * kof.config no Native — mesma semântica do JVM (KofConfigE2ETest):
     * precedência KOF_CONFIG > env KOF_&lt;KEY&gt; (pontos/traços viram
     * underscore, maiúsculas) > kof.&lt;profile&gt;.config / kof.config no
     * diretório de trabalho. Arquivo: linhas "chave = valor", "#" comenta,
     * bordas aparadas. Conversores tipados com default em valor inválido.
     * Tudo em asm puro sobre syscalls, sem libc.
     */
    private static void emitConfigFunctions(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lcfg_s_kofconfig:  .asciz "KOF_CONFIG"
            .Lcfg_s_kofprofile: .asciz "KOF_PROFILE"
            .Lcfg_s_default:    .asciz "kof.config"
            .Lcfg_w_true:  .asciz "true"
            .Lcfg_w_yes:   .asciz "yes"
            .Lcfg_w_one:   .asciz "1"
            .Lcfg_w_false: .asciz "false"
            .Lcfg_w_no:    .asciz "no"
            .Lcfg_w_zero:  .asciz "0"
            .section .text

            # kof_env_getc(rdi=nome C-string) -> rax = KofString*|0
            # busca linear simples: acha "NAME=" como substring do environ;
            # valor = ate o NUL da entrada. Estado: r10=nlen, r13=len,
            # r14=i (posicao), r9=j (casamento), r8=ptr corrente.
            kof_env_getc:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $16384, %rsp
                movq %rdi, %rbx              # nome
                xorq %r10, %r10              # strlen(nome)
            .Lceg_nlen:
                cmpb $0, (%rbx,%r10)
                je .Lceg_nlen_done
                incq %r10
                jmp .Lceg_nlen
            .Lceg_nlen_done:
                leaq .Lsec_environ_path(%rip), %rdi
                xorq %rsi, %rsi
                xorq %rdx, %rdx
                movq $2, %rax                # SYS_open
                syscall
                testq %rax, %rax
                js .Lceg_fail
                movq %rax, %r12              # fd
                movq %r12, %rdi
                leaq 0(%rsp), %rsi
                movq $16384, %rdx
                xorq %rax, %rax              # SYS_read
                syscall
                movq %rax, %r13              # bytes lidos
                movq %r12, %rdi
                movq $3, %rax                # close
                testq %r10, %r10             # nome vazio -> falha
                jle .Lceg_fail
                cmpq %r10, %r13
                jle .Lceg_fail               # buffer menor que o nome
                xorq %r14, %r14              # i = 0
            .Lceg_scan:
                cmpq %r13, %r14
                jge .Lceg_fail
                movq %rsp, %r8
                addq %r14, %r8               # r8 = buf + i
                xorq %r9, %r9                # j = 0
            .Lceg_pcmp:
                cmpq %r10, %r9
                je .Lceg_pmatched            # casou o nome inteiro
                movq %r14, %rax
                addq %r9, %rax
                cmpq %r13, %rax
                jge .Lceg_fail
                movzbl (%rbx,%r9), %eax      # name[j]
                movzbl (%r8,%r9), %ecx       # buf[i+j]
                cmpl %ecx, %eax
                jne .Lceg_advance
                incq %r9
                jmp .Lceg_pcmp
            .Lceg_pmatched:
                # exige '=' imediatamente apos o nome
                cmpb $61, (%r8,%r10)
                jne .Lceg_advance
                leaq 1(%r8,%r10), %rdi       # valor = buf + i + nlen + 1
                movq %r14, %rsi
                addq %r10, %rsi
                incq %rsi                    # inicio do valor (offset)
                movq %r13, %rdx
                subq %rsi, %rdx              # limite restante
                movq %rdx, %r15              # r15 = len maximo
                xorq %rcx, %rcx              # comprimento do valor ate NUL
            .Lceg_vscan:
                cmpq %r15, %rcx
                jge .Lceg_vdone
                cmpb $0, (%rdi,%rcx)
                je .Lceg_vdone
                incq %rcx
                jmp .Lceg_vscan
            .Lceg_vdone:
                movq %rcx, %rsi              # vallen
                call kof_string_from_literal
                jmp .Lceg_exit
            .Lceg_advance:
                # avanca ate passar do proximo NUL (fim da entrada)
                cmpq %r13, %r14
                jge .Lceg_fail
                cmpb $0, (%r8)
                je .Lceg_adv_null
                incq %r8
                incq %r14
                jmp .Lceg_advance
            .Lceg_adv_null:
                incq %r14                    # pula o proprio NUL
                jmp .Lceg_scan
            .Lceg_fail:
                xorl %eax, %eax
            .Lceg_exit:
                addq $16384, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # .Lcfg_file_find(rdi=path C-string, rsi=key KofString*) -> KofString*|0
            .Lcfg_file_find:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $16384, %rsp
                movq %rsi, %r12              # key
                movq %rdi, %rbx              # path cstr
                movq %rbx, %rdi
                xorq %rsi, %rsi
                xorq %rdx, %rdx
                movq $2, %rax                # SYS_open (ausente -> null)
                syscall
                testq %rax, %rax
                js .Lcff_fail
                movq %rax, %r13              # fd
                movq %r13, %rdi
                leaq 0(%rsp), %rsi
                movq $16384, %rdx
                xorq %rax, %rax              # SYS_read
                syscall
                movq %rax, %r14              # len
                movq %r13, %rdi
                movq $3, %rax                # close
                syscall
                testq %r14, %r14
                jle .Lcff_fail
                leaq 24(%r12), %r13          # r13 = key data (fd ja fechado)
                movl 16(%r12), %r15d         # r15d = key len
                xorq %rbx, %rbx              # pos
            .Lcff_line:
                cmpq %r14, %rbx
                jge .Lcff_fail
                movq %rbx, %rcx              # fim da linha
            .Lcff_findeol:
                cmpq %r14, %rcx
                jge .Lcff_haveeol
                cmpb $10, (%rsp,%rcx)
                je .Lcff_haveeol
                incq %rcx
                jmp .Lcff_findeol
            .Lcff_haveeol:
                movq %rcx, %r8               # eol exclusivo
                movq %rbx, %r9               # trim esquerdo
            .Lcff_tls:
                cmpq %r8, %r9
                jge .Lcff_blank
                movzbl (%rsp,%r9), %eax
                cmpb $32, %al
                je .Lcff_tls1
                cmpb $9, %al
                je .Lcff_tls1
                jmp .Lcff_tle
            .Lcff_tls1:
                incq %r9
                jmp .Lcff_tls
            .Lcff_tle:
                movq %r8, %r10               # trim direito (' ', tab, CR)
            .Lcff_tle_loop:
                cmpq %r9, %r10
                jle .Lcff_blank
                movzbl -1(%rsp,%r10), %eax
                cmpb $32, %al
                je .Lcff_tle1
                cmpb $9, %al
                je .Lcff_tle1
                cmpb $13, %al
                je .Lcff_tle1
                jmp .Lcff_hash
            .Lcff_tle1:
                decq %r10
                jmp .Lcff_tle_loop
            .Lcff_blank:
                movq %r8, %rbx
                incq %rbx
                jmp .Lcff_line
            .Lcff_hash:
                cmpb $35, (%rsp,%r9)         # '#'
                je .Lcff_blank
                movq %r9, %rcx               # '=' dentro de [r9,r10)
            .Lcff_eq:
                cmpq %r10, %rcx
                jge .Lcff_blank
                cmpb $61, (%rsp,%rcx)
                je .Lcff_keytrim
                incq %rcx
                jmp .Lcff_eq
            .Lcff_keytrim:
                movq %rcx, %r11              # chave direita-aparada [r9,r11)
            .Lcff_keyt:
                cmpq %r9, %r11
                jle .Lcff_keycmp
                movzbl -1(%rsp,%r11), %eax
                cmpb $32, %al
                je .Lcff_keyt1
                cmpb $9, %al
                jne .Lcff_keycmp
            .Lcff_keyt1:
                decq %r11
                jmp .Lcff_keyt
            .Lcff_keycmp:
                movq %r11, %rdx
                subq %r9, %rdx
                cmpq %r15, %rdx
                jne .Lcff_valskip
                xorq %rdx, %rdx
            .Lcff_cmpline:
                cmpq %r15, %rdx
                jge .Lcff_foundkey
                movzbl (%rsp,%r9), %eax
                movzbl (%r13,%rdx), %ecx
                cmpl %ecx, %eax
                jne .Lcff_valskip
                incq %rdx
                incq %r9
                jmp .Lcff_cmpline
            .Lcff_foundkey:
                leaq 1(%rcx), %rsi           # vs = '=' + 1
            .Lcff_vtls:
                cmpq %r10, %rsi
                jge .Lcff_vmk
                movzbl (%rsp,%rsi), %eax
                cmpb $32, %al
                je .Lcff_vtls1
                cmpb $9, %al
                jne .Lcff_vmk
            .Lcff_vtls1:
                incq %rsi
                jmp .Lcff_vtls
            .Lcff_vmk:
                movq %r10, %rdx
                subq %rsi, %rdx              # vallen = fim da linha - inicio do valor
                leaq (%rsp,%rsi), %rdi       # endereco do valor (rsi e OFFSET!)
                call kof_string_from_literal
                jmp .Lcff_exit
            .Lcff_valskip:
                movq %r8, %rbx
                incq %rbx
                jmp .Lcff_line
            .Lcff_fail:
                xorl %eax, %eax
            .Lcff_exit:
                addq $16384, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # .Lcfg_envname(rdi=key KofString*, rsi=dest) -> escreve "KOF_<KEY>" C-string
            .Lcfg_envname:
                movl 16(%rdi), %ecx          # keylen
                leaq 24(%rdi), %rdx          # data
                movb $75, 0(%rsi)            # 'K'
                movb $79, 1(%rsi)            # 'O'
                movb $70, 2(%rsi)            # 'F'
                movb $95, 3(%rsi)            # '_'
                movq $4, %rax
                xorq %r9, %r9
            .Lce_loop:
                cmpq %rcx, %r9
                jge .Lce_done
                movzbl (%rdx,%r9), %edi
                cmpb $46, %dil               # '.'
                je .Lce_us
                cmpb $45, %dil               # '-'
                je .Lce_us
                cmpb $97, %dil               # 'a'
                jb .Lce_store
                cmpb $122, %dil              # 'z'
                ja .Lce_store
                subb $32, %dil               # maiuscula
                jmp .Lce_store
            .Lce_us:
                movb $95, %dil               # '_'
            .Lce_store:
                movb %dil, (%rsi,%rax)
                incq %rax
                incq %r9
                jmp .Lce_loop
            .Lce_done:
                movb $0, (%rsi,%rax)
                ret

            # kof_config_lookup(rdi=key KofString*) -> KofString*|0
            kof_config_lookup:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $512, %rsp
                movq %rdi, %rbx              # key
                # 1) arquivo explicito via KOF_CONFIG
                leaq .Lcfg_s_kofconfig(%rip), %rdi
                call kof_env_getc
                testq %rax, %rax
                jz .Lcl_envkey
                leaq 24(%rax), %rdi          # path cstr (data e NUL-terminada)
                movq %rbx, %rsi
                call .Lcfg_file_find
                testq %rax, %rax
                jnz .Lcl_exit
            .Lcl_envkey:
                # 2) env KOF_<CHAVE>
                movq %rbx, %rdi
                movq %rsp, %rsi
                call .Lcfg_envname
                movq %rsp, %rdi
                call kof_env_getc
                testq %rax, %rax
                jnz .Lcl_exit
                # 3) kof.<profile>.config ou kof.config
                leaq .Lcfg_s_kofprofile(%rip), %rdi
                call kof_env_getc
                testq %rax, %rax
                jz .Lcl_defaultfile
                # monta "kof.<profile>.config" no buffer do frame
                movq %rsp, %r8
                movl $1699939949, %eax       # "kof."
                movl %eax, 0(%r8)
                movq %rax, %r12              # profile KofString
                movq %rsp, %r8
                movl $1699939949, %eax       # "kof." little-endian
                movl %eax, 0(%r8)
                movq $4, %rax
                movl 16(%r12), %ecx          # profile len
                leaq 24(%r12), %rdx          # profile data
                xorq %r9, %r9
            .Lcl_pcopy:
                cmpq %rcx, %r9
                jge .Lcl_pdone
                movzbl (%rdx,%r9), %edi
                movb %dil, (%r8,%rax)
                incq %rax
                incq %r9
                jmp .Lcl_pcopy
            .Lcl_pdone:
                movb $46, 0(%r8,%rax)        # ".config"
                movb $99, 1(%r8,%rax)
                movb $111, 2(%r8,%rax)
                movb $110, 3(%r8,%rax)
                movb $102, 4(%r8,%rax)
                movb $105, 5(%r8,%rax)
                movb $103, 6(%r8,%rax)
                addq $7, %rax
                movb $0, (%r8,%rax)
                leaq 0(%rsp), %rdi
                jmp .Lcl_ffcall
            .Lcl_defaultfile:
                leaq .Lcfg_s_default(%rip), %rdi
            .Lcl_ffcall:
                movq %rbx, %rsi
                call .Lcfg_file_find
            .Lcl_exit:
                addq $512, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # ---- wrappers publicos ----

            kof_config_get:
                jmp kof_config_lookup

            kof_config_env:
                leaq 24(%rdi), %rdi          # nome KofString -> C-string (data NUL-terminada)
                jmp kof_env_getc

            kof_config_has:
                call kof_config_lookup
                testq %rax, %rax
                setne %al
                movzbq %al, %rax
                ret

            kof_config_str:
                pushq %rbx
                movq %rsi, %rbx              # default (lookup preserva rbx)
                call kof_config_lookup
                testq %rax, %rax
                cmovzq %rbx, %rax
                popq %rbx
                ret

            kof_config_int:
                pushq %rbx
                movl %esi, %ebx              # default
                call kof_config_lookup
                testq %rax, %rax
                jz .Lci_def
                movq %rax, %rdi
                call .Lcfg_parse_i64
                testl %edx, %edx
                jz .Lci_def
                # range int32
                cmpq $2147483647, %rax
                jg .Lci_def
                cmpq $-2147483648, %rax
                jl .Lci_def
                popq %rbx
                ret
            .Lci_def:
                movl %ebx, %eax
                popq %rbx
                ret

            kof_config_long:
                pushq %rbx
                movq %rsi, %rbx              # default long
                call kof_config_lookup
                testq %rax, %rax
                jz .Lcl_def
                movq %rax, %rdi
                call .Lcfg_parse_i64
                testl %edx, %edx
                jz .Lcl_def
                popq %rbx
                ret
            .Lcl_def:
                movq %rbx, %rax
                popq %rbx
                ret

            kof_config_bool:
                pushq %rbx
                movl %esi, %ebx              # default
                call kof_config_lookup
                testq %rax, %rax
                jz .Lcb_def
                movl 16(%rax), %ecx          # len
                leaq 24(%rax), %rdx          # data
                # trim rapido nas bordas
                xorq %r9, %r9
            .Lcb_tls:
                cmpq %rcx, %r9
                jge .Lcb_def
                movzbl (%rdx,%r9), %eax
                cmpb $32, %al
                je .Lcb_tls1
                cmpb $9, %al
                je .Lcb_tls1
                jmp .Lcb_tle
            .Lcb_tls1:
                incq %r9
                jmp .Lcb_tls
            .Lcb_tle:
                movq %rcx, %r10
            .Lcb_tle_loop:
                cmpq %r9, %r10
                jle .Lcb_def
                movzbl -1(%rdx,%r10), %eax
                cmpb $32, %al
                je .Lcb_tle1
                cmpb $9, %al
                je .Lcb_tle1
                jmp .Lcb_dispatch
            .Lcb_tle1:
                decq %r10
                jmp .Lcb_tle_loop
            .Lcb_dispatch:
                subq %r9, %r10               # len aparado
                # true / yes / 1 -> 1
                cmpq $4, %r10
                jne .Lcb_chk3
                leaq .Lcfg_w_true(%rip), %rdi
                jmp .Lcb_cmp_true
            .Lcb_chk3:
                cmpq $3, %r10
                jne .Lcb_chk1
                leaq .Lcfg_w_yes(%rip), %rdi
                jmp .Lcb_cmp_true
            .Lcb_chk1:
                cmpq $1, %r10
                jne .Lcb_chk5
                leaq .Lcfg_w_one(%rip), %rdi
                jmp .Lcb_cmp_true
            .Lcb_chk5:
                cmpq $5, %r10
                jne .Lcb_chk2
                leaq .Lcfg_w_false(%rip), %rdi
                jmp .Lcb_cmp_false
            .Lcb_chk2:
                cmpq $2, %r10
                jne .Lcb_def
                leaq .Lcfg_w_no(%rip), %rdi
                jmp .Lcb_cmp_false
            .Lcb_cmp_true:
                call .Lcb_ci_match
                testl %eax, %eax
                jz .Lcb_def
                movl $1, %eax
                jmp .Lcb_ret1
            .Lcb_cmp_false:
                call .Lcb_ci_match
                testl %eax, %eax
                jz .Lcb_def
                xorl %eax, %eax
                jmp .Lcb_ret1
            .Lcb_def:
                movl %ebx, %eax
            .Lcb_ret1:
                popq %rbx
                ret

            # .Lcb_ci_match(rdi=candidato, rsi=data, r10=len aparado) -> eax=1 se igual
            .Lcb_ci_match:
                pushq %rbx
                movl %r10d, %ebx
                xorl %eax, %eax
                testl %ebx, %ebx
                jle .Lcbm_no
                xorq %rcx, %rcx
            .Lcbm_loop:
                movzbl (%rdi,%rcx), %r8d
                movzbl (%rsi,%rcx), %r9d
                orb $0x20, %r8b
                orb $0x20, %r9b
                cmpl %r9d, %r8d
                jne .Lcbm_no
                incq %rcx
                cmpl %ebx, %ecx
                jl .Lcbm_loop
                movl $1, %eax
            .Lcbm_no:
                popq %rbx
                ret

            # .Lcfg_parse_i64(rdi=KofString*) -> rax=valor, edx=1 ok | edx=0 invalido
            .Lcfg_parse_i64:
                movl 16(%rdi), %ecx          # len
                leaq 24(%rdi), %r8           # data
                xorq %r9, %r9
            .Lpi_tls:
                cmpq %rcx, %r9
                jge .Lpi_bad
                movzbl (%r8,%r9), %eax
                cmpb $32, %al
                je .Lpi_tls1
                cmpb $9, %al
                je .Lpi_tls1
                jmp .Lpi_tle
            .Lpi_tls1:
                incq %r9
                jmp .Lpi_tls
            .Lpi_tle:
                movq %rcx, %r10              # fim exclusivo
            .Lpi_tle_l:
                cmpq %r9, %r10
                jle .Lpi_bad                 # vazio apos trim
                movzbl -1(%r8,%r10), %eax
                cmpb $32, %al
                je .Lpi_tle1
                cmpb $9, %al
                je .Lpi_tle1
                jmp .Lpi_sign
            .Lpi_tle1:
                decq %r10
                jmp .Lpi_tle_l
            .Lpi_sign:
                xorq %r11, %r11              # acc
                xorl %esi, %esi              # neg
                cmpq %r9, %r10
                jle .Lpi_bad
                movzbl (%r8,%r9), %eax
                cmpb $45, %al                # '-'
                je .Lpi_negset
                cmpb $43, %al                # '+'
                je .Lpi_posskip
                jmp .Lpi_dcheck
            .Lpi_negset:
                movl $1, %esi
            .Lpi_posskip:
                incq %r9
            .Lpi_dcheck:
                cmpq %r9, %r10
                jle .Lpi_bad                 # sinal sem digitos
            .Lpi_digit:
                movzbl (%r8,%r9), %eax
                subb $48, %al
                cmpb $9, %al
                ja .Lpi_bad
                imulq $10, %r11
                movzbl %al, %eax
                addq %rax, %r11
                incq %r9
                cmpq %r9, %r10
                jg .Lpi_digit
                movq %r11, %rax
                testl %esi, %esi
                jz .Lpi_ok
                negq %rax
            .Lpi_ok:
                movl $1, %edx
                ret
            .Lpi_bad:
                xorl %edx, %edx
                xorl %eax, %eax
                ret
            """);
    }
    private static void emitIoTimeFunctions(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lstr_read_err: .asciz "Runtime error: cannot read"
            .section .text
            .globl kof_now
            .type kof_now, @function
            kof_now:
                subq $16, %rsp
                movq %rsp, %rdi
                xorq %rsi, %rsi
                movq $96, %rax
                syscall
                movq 8(%rsp), %rax
                xorq %rdx, %rdx
                movq $1000, %r8
                divq %r8
                movq 0(%rsp), %rcx
                imulq $1000, %rcx
                addq %rcx, %rax
                addq $16, %rsp
                ret

            .globl kof_read_line
            .type kof_read_line, @function
            kof_read_line:
                pushq %rbx
                pushq %r12
                subq $512, %rsp
                movq %rsp, %rbx
                xorq %r12, %r12
            .Lkof_read_line_loop:
                cmpq $511, %r12
                jge .Lkof_read_line_done
                movq $0, %rax
                movq $0, %rdi
                movq $1, %rsi
                movq %rbx, %rdx
                addq %r12, %rdx
                syscall
                testq %rax, %rax
                jle .Lkof_read_line_done
                movq %rbx, %rcx
                addq %r12, %rcx
                cmpb $10, (%rcx)
                je .Lkof_read_line_done
                incq %r12
                jmp .Lkof_read_line_loop
            .Lkof_read_line_done:
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %rcx
                movl $1, 0(%rcx)
                movl $0, 4(%rcx)
                movq $0, 8(%rcx)
                movl %r12d, 16(%rcx)
                movl $0, 20(%rcx)
                movq %rcx, %rdi
                addq $24, %rdi
                movq %rbx, %rsi
                movl %r12d, %edx
                call kof_memcpy
                movq %rcx, %r13
                movb $0, 24(%r13,%r12)
                movq %r13, %rax
                addq $512, %rsp
                popq %r12
                popq %rbx
                ret

            .globl kof_read_file
            .type kof_read_file, @function
            kof_read_file:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq $0, %rdx
                movq $257, %rax
                syscall
                testq %rax, %rax
                js .Lkof_read_file_err
                movq %rax, %r12
                subq $144, %rsp
                movq %r12, %rdi
                movq %rsp, %rsi
                movq $5, %rax
                syscall
                movq 48(%rsp), %r13
                addq $144, %rsp
                leal 25(%r13), %edi
                call kof_alloc
                movq %rax, %r14
                movl $1, 0(%r14)
                movl $0, 4(%r14)
                movq $0, 8(%r14)
                movl %r13d, 16(%r14)
                movl $0, 20(%r14)
                movq %r12, %rdi
                movq %r14, %rsi
                addq $24, %rsi
                movl %r13d, %edx
                movq $0, %rax
                syscall
                movq %r12, %rdi
                movq $3, %rax
                syscall
                movq %r14, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_read_file_err:
                leaq .Lstr_read_err(%rip), %rdi
                call kof_panic

            .globl kof_write_file
            .type kof_write_file, @function
            kof_write_file:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movq %rsi, %r12
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq $577, %rdx
                movq $420, %r10
                movq $257, %rax
                syscall
                testq %rax, %rax
                js .Lkof_write_file_fail
                movq %rax, %rdi
                leaq 24(%r12), %rsi
                movl 16(%r12), %edx
                movq $1, %rax
                syscall
                movq %rdi, %rdi
                movq $3, %rax
                syscall
                xorl %eax, %eax
                popq %r12
                popq %rbx
                ret
            .Lkof_write_file_fail:
                movq $-1, %rax
                popq %r12
                popq %rbx
                ret
            """);
    }

    private static void emitIoFileFunctions(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lio_slash: .asciz "/"
            .Lio_dot: .asciz "."
            .Lio_dotdot: .asciz ".."
            .Lio_read_err: .asciz "Runtime error: cannot read file"
            .section .text

            // kof_io_make_string(data_ptr, len) → new KofString
            .globl kof_io_make_string
            .type kof_io_make_string, @function
            kof_io_make_string:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movq %rsi, %r12
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, 0(%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl %r12d, 16(%r13)
                movl $0, 20(%r13)
                movq %r13, %rdi
                addq $24, %rdi
                movq %rbx, %rsi
                movq %r12, %rdx
                call kof_memcpy
                movb $0, 24(%r13,%r12)
                movq %r13, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret

            // kof_io_strlen(rdi) → byte length of a NUL-terminated string
            .globl kof_io_strlen
            .type kof_io_strlen, @function
            kof_io_strlen:
                xorq %rax, %rax
            .Lio_strlen_loop:
                cmpb $0, (%rdi,%rax)
                je .Lio_strlen_done
                incq %rax
                jmp .Lio_strlen_loop
            .Lio_strlen_done:
                ret

            // kof_io_last_slash(str) → index of last '/' or -1
            .globl kof_io_last_slash
            .type kof_io_last_slash, @function
            kof_io_last_slash:
                movl 16(%rdi), %eax
                decl %eax
            .Lio_last_slash_loop:
                cmpl $0, %eax
                jl .Lio_last_slash_done
                leaq 24(%rdi), %rcx
                cmpb $47, (%rcx,%rax)
                je .Lio_last_slash_done
                decl %eax
                jmp .Lio_last_slash_loop
            .Lio_last_slash_done:
                ret

            // kof_io_strip_trailing(str) → effective length (trailing '/' removed, root kept)
            .globl kof_io_strip_trailing
            .type kof_io_strip_trailing, @function
            kof_io_strip_trailing:
                movl 16(%rdi), %eax
            .Lio_strip_loop:
                cmpl $1, %eax
                jle .Lio_strip_done
                leaq 24(%rdi), %rcx
                cmpb $47, -1(%rcx,%rax)
                jne .Lio_strip_done
                decl %eax
                jmp .Lio_strip_loop
            .Lio_strip_done:
                ret

            // kof_io_stat_mode(str) → st_mode or -1
            .globl kof_io_stat_mode
            .type kof_io_stat_mode, @function
            kof_io_stat_mode:
                pushq %rbx
                subq $144, %rsp
                movq %rdi, %rbx
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq %rsp, %rdx
                xorq %r10, %r10
                movq $262, %rax
                syscall
                testq %rax, %rax
                js .Lio_stat_err
                movl 24(%rsp), %eax
                addq $144, %rsp
                popq %rbx
                ret
            .Lio_stat_err:
                movq $-1, %rax
                addq $144, %rsp
                popq %rbx
                ret

            // ── Path ──────────────────────────────────────────────

            .globl kof_io_file_name
            .type kof_io_file_name, @function
            kof_io_file_name:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                call kof_io_strip_trailing
                movl %eax, %r13d
                movl %eax, %r12d
                decl %r12d
            .Lio_name_find:
                cmpl $0, %r12d
                jl .Lio_name_no_slash
                leaq 24(%rbx), %rcx
                cmpb $47, (%rcx,%r12)
                je .Lio_name_found
                decl %r12d
                jmp .Lio_name_find
            .Lio_name_found:
                leal 1(%r12), %eax
                movl %r13d, %ecx
                subl %eax, %ecx
                jg .Lio_name_sub
                movq %rbx, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_name_sub:
                leaq 24(%rbx), %rdi
                addq %r12, %rdi
                incq %rdi
                movslq %ecx, %rsi
                call kof_io_make_string
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_name_no_slash:
                movslq %r13d, %rsi
                leaq 24(%rbx), %rdi
                call kof_io_make_string
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_io_path_file_name
            .type kof_io_path_file_name, @function
            kof_io_path_file_name:
                jmp kof_io_file_name

            .globl kof_io_path_parent
            .type kof_io_path_parent, @function
            kof_io_path_parent:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                call kof_io_strip_trailing
                movl %eax, %r13d
                movl %eax, %r12d
                decl %r12d
            .Lio_parent_find:
                cmpl $0, %r12d
                jl .Lio_parent_none
                leaq 24(%rbx), %rcx
                cmpb $47, (%rcx,%r12)
                je .Lio_parent_found
                decl %r12d
                jmp .Lio_parent_find
            .Lio_parent_found:
                testl %r12d, %r12d
                jne .Lio_parent_prefix
                leaq .Lio_slash(%rip), %rdi
                movq $1, %rsi
                call kof_io_make_string
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_parent_prefix:
                leaq 24(%rbx), %rdi
                movslq %r12d, %rsi
                call kof_io_make_string
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_parent_none:
                xorl %eax, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_io_path_extension
            .type kof_io_path_extension, @function
            kof_io_path_extension:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                call kof_io_strip_trailing
                movl %eax, %r13d
                movl %eax, %r14d
                decl %r14d
            .Lio_ext_find_slash:
                cmpl $0, %r14d
                jl .Lio_ext_dot_from
                leaq 24(%rbx), %rcx
                cmpb $47, (%rcx,%r14)
                je .Lio_ext_dot_from
                decl %r14d
                jmp .Lio_ext_find_slash
            .Lio_ext_dot_from:
                movl %r13d, %r12d
                decl %r12d
            .Lio_ext_find_dot:
                cmpl %r14d, %r12d
                jle .Lio_ext_empty
                leaq 24(%rbx), %rcx
                cmpb $46, (%rcx,%r12)
                je .Lio_ext_found
                decl %r12d
                jmp .Lio_ext_find_dot
            .Lio_ext_found:
                movl %r13d, %ecx
                subl %r12d, %ecx
                decl %ecx
                jg .Lio_ext_sub
            .Lio_ext_empty:
                xorl %esi, %esi
                leaq .Lio_dot(%rip), %rdi
                call kof_io_make_string
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_ext_sub:
                leaq 24(%rbx), %rdi
                addq %r12, %rdi
                incq %rdi
                movslq %ecx, %rsi
                call kof_io_make_string
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_io_path_resolve
            .type kof_io_path_resolve, @function
            kof_io_path_resolve:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rsi, %r12
                cmpb $47, 24(%r12)
                je .Lio_resolve_b
                cmpl $0, 16(%rbx)
                je .Lio_resolve_b
                movl 16(%rbx), %eax
                leaq 24(%rbx), %rcx
                cmpb $47, -1(%rcx,%rax)
                je .Lio_resolve_concat
                leaq .Lio_slash(%rip), %rdi
                movq $1, %rsi
                call kof_string_from_literal
                movq %rax, %r13
                movq %rbx, %rdi
                movq %r13, %rsi
                call kof_string_concat
                movq %rax, %r14
                movq %r14, %rdi
                movq %r12, %rsi
                call kof_string_concat
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_resolve_concat:
                movq %rbx, %rdi
                movq %r12, %rsi
                call kof_string_concat
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_resolve_b:
                movq %r12, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_io_path_is_absolute
            .type kof_io_path_is_absolute, @function
            kof_io_path_is_absolute:
                cmpl $0, 16(%rdi)
                jle .Lio_abs_no
                cmpb $47, 24(%rdi)
                jne .Lio_abs_no
                movq $1, %rax
                ret
            .Lio_abs_no:
                xorl %eax, %eax
                ret

            .globl kof_io_path_to_absolute
            .type kof_io_path_to_absolute, @function
            kof_io_path_to_absolute:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                cmpb $47, 24(%rbx)
                je .Lio_toabs_ret
                subq $4096, %rsp
                movq %rsp, %rdi
                movq $4096, %rsi
                movq $79, %rax
                syscall
                testq %rax, %rax
                js .Lio_toabs_err
                movq %rsp, %rdi
                call kof_io_strlen
                movq %rax, %rsi
                movq %rsp, %rdi
                call kof_io_make_string
                movq %rax, %rdi
                movq %rbx, %rsi
                call kof_io_path_resolve
                movq %rax, %r12
                addq $4096, %rsp
                movq %r12, %rax
                popq %r12
                popq %rbx
                ret
            .Lio_toabs_err:
                addq $4096, %rsp
            .Lio_toabs_ret:
                movq %rbx, %rax
                popq %r12
                popq %rbx
                ret

            .globl kof_io_path_normalize
            .type kof_io_path_normalize, @function
            kof_io_path_normalize:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $576, %rsp
                movq %rdi, %rbx
                movq $0, 512(%rsp)
                cmpl $0, 16(%rbx)
                je .Lio_norm_scan
                cmpb $47, 24(%rbx)
                jne .Lio_norm_scan
                movq $1, 512(%rsp)
            .Lio_norm_scan:
                xorl %r12d, %r12d
                xorl %r13d, %r13d
                xorl %r14d, %r14d
            .Lio_norm_collect:
                movl 16(%rbx), %ecx
                cmpl %ecx, %r13d
                jge .Lio_norm_final
                leaq 24(%rbx), %rax
                cmpb $47, (%rax,%r13)
                jne .Lio_norm_advance
                movl %r13d, %r9d
                subl %r14d, %r9d
                jz .Lio_norm_seg_done
                cmpl $1, %r9d
                jne .Lio_norm_check_dotdot
                leaq 24(%rbx), %rax
                cmpb $46, (%rax,%r14)
                je .Lio_norm_seg_done
            .Lio_norm_check_dotdot:
                cmpl $2, %r9d
                jne .Lio_norm_push
                leaq 24(%rbx), %rax
                cmpb $46, (%rax,%r14)
                jne .Lio_norm_push
                cmpb $46, 1(%rax,%r14)
                jne .Lio_norm_push
                testl %r12d, %r12d
                jle .Lio_norm_dotdot_empty
                decl %r12d
                jmp .Lio_norm_seg_done
            .Lio_norm_dotdot_empty:
                cmpq $0, 512(%rsp)
                jne .Lio_norm_seg_done
                movl %r14d, (%rsp,%r12,8)
                movl %r9d, 4(%rsp,%r12,8)
                incl %r12d
                jmp .Lio_norm_seg_done
            .Lio_norm_push:
                movl %r14d, (%rsp,%r12,8)
                movl %r9d, 4(%rsp,%r12,8)
                incl %r12d
            .Lio_norm_seg_done:
                incl %r13d
                movl %r13d, %r14d
                jmp .Lio_norm_collect
            .Lio_norm_advance:
                incl %r13d
                jmp .Lio_norm_collect
            .Lio_norm_final:
                movl %r13d, %r9d
                subl %r14d, %r9d
                jz .Lio_norm_build
                cmpl $1, %r9d
                jne .Lio_norm_final_dotdot
                leaq 24(%rbx), %rax
                cmpb $46, (%rax,%r14)
                je .Lio_norm_build
            .Lio_norm_final_dotdot:
                cmpl $2, %r9d
                jne .Lio_norm_final_push
                leaq 24(%rbx), %rax
                cmpb $46, (%rax,%r14)
                jne .Lio_norm_final_push
                cmpb $46, 1(%rax,%r14)
                jne .Lio_norm_final_push
                testl %r12d, %r12d
                jle .Lio_norm_final_dotdot_empty
                decl %r12d
                jmp .Lio_norm_build
            .Lio_norm_final_dotdot_empty:
                cmpq $0, 512(%rsp)
                jne .Lio_norm_build
                movl %r14d, (%rsp,%r12,8)
                movl %r9d, 4(%rsp,%r12,8)
                incl %r12d
                jmp .Lio_norm_build
            .Lio_norm_final_push:
                movl %r14d, (%rsp,%r12,8)
                movl %r9d, 4(%rsp,%r12,8)
                incl %r12d
            .Lio_norm_build:
                xorl %ecx, %ecx
                cmpq $0, 512(%rsp)
                je .Lio_norm_total_segs
                incl %ecx
            .Lio_norm_total_segs:
                testl %r12d, %r12d
                jle .Lio_norm_total_done
                xorl %r9d, %r9d
            .Lio_norm_total_loop:
                cmpl %r12d, %r9d
                jge .Lio_norm_total_done
                movl 4(%rsp,%r9,8), %eax
                addl %eax, %ecx
                testl %r9d, %r9d
                je .Lio_norm_total_next
                incl %ecx
            .Lio_norm_total_next:
                incl %r9d
                jmp .Lio_norm_total_loop
            .Lio_norm_total_done:
                cmpq $0, 512(%rsp)
                jne .Lio_norm_alloc
                testl %r12d, %r12d
                jg .Lio_norm_alloc
                movl $1, %ecx
                movq $-1, 520(%rsp)
                jmp .Lio_norm_alloc2
            .Lio_norm_alloc:
                movq $0, 520(%rsp)
            .Lio_norm_alloc2:
                movslq %ecx, %rdi
                call kof_alloc
                movq %rax, %r15
                xorl %r9d, %r9d
                cmpq $0, 512(%rsp)
                je .Lio_norm_build_rel
                movb $47, (%r15)
                incl %r9d
            .Lio_norm_build_rel:
                cmpq $-1, 520(%rsp)
                jne .Lio_norm_copy_segs
                movb $46, (%r15)
                incl %r9d
                jmp .Lio_norm_done
            .Lio_norm_copy_segs:
                xorl %r10d, %r10d
            .Lio_norm_seg_loop:
                cmpl %r12d, %r10d
                jge .Lio_norm_done
                testl %r10d, %r10d
                je .Lio_norm_seg_no_sep
                movb $47, (%r15,%r9)
                incl %r9d
            .Lio_norm_seg_no_sep:
                movl (%rsp,%r10,8), %r11d
                movl 4(%rsp,%r10,8), %eax
                movl %eax, %ecx
            .Lio_norm_seg_inner:
                testl %ecx, %ecx
                jle .Lio_norm_seg_next
                leaq 24(%rbx), %rdi
                movb (%rdi,%r11), %dl
                movb %dl, (%r15,%r9)
                incl %r9d
                incq %r11
                decl %ecx
                jmp .Lio_norm_seg_inner
            .Lio_norm_seg_next:
                incl %r10d
                jmp .Lio_norm_seg_loop
            .Lio_norm_done:
                movq %r15, %rdi
                movslq %r9d, %rsi
                call kof_io_make_string
                addq $576, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            // ── File ─────────────────────────────────────────────

            .globl kof_io_file_exists
            .type kof_io_file_exists, @function
            kof_io_file_exists:
                pushq %rbx
                movq %rdi, %rbx
                call kof_io_stat_mode
                testq %rax, %rax
                js .Lio_exists_no
                movq $1, %rax
                popq %rbx
                ret
            .Lio_exists_no:
                xorl %eax, %eax
                popq %rbx
                ret

            .globl kof_io_file_is_file
            .type kof_io_file_is_file, @function
            kof_io_file_is_file:
                pushq %rbx
                movq %rdi, %rbx
                call kof_io_stat_mode
                testq %rax, %rax
                js .Lio_is_file_no
                andq $61440, %rax
                cmpq $32768, %rax
                jne .Lio_is_file_no
                movq $1, %rax
                popq %rbx
                ret
            .Lio_is_file_no:
                xorl %eax, %eax
                popq %rbx
                ret

            .globl kof_io_file_is_dir
            .type kof_io_file_is_dir, @function
            kof_io_file_is_dir:
                pushq %rbx
                movq %rdi, %rbx
                call kof_io_stat_mode
                testq %rax, %rax
                js .Lio_is_dir_no
                andq $61440, %rax
                cmpq $16384, %rax
                jne .Lio_is_dir_no
                movq $1, %rax
                popq %rbx
                ret
            .Lio_is_dir_no:
                xorl %eax, %eax
                popq %rbx
                ret

            .globl kof_io_read_text
            .type kof_io_read_text, @function
            kof_io_read_text:
                jmp kof_read_file

            .globl kof_io_write_text
            .type kof_io_write_text, @function
            kof_io_write_text:
                call kof_write_file
                testq %rax, %rax
                je .Lio_ok1
                xorl %eax, %eax
                ret
            .Lio_ok1:
                movq $1, %rax
                ret

            .globl kof_io_append_text
            .type kof_io_append_text, @function
            kof_io_append_text:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movq %rsi, %r12
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq $1089, %rdx
                movq $420, %r10
                movq $257, %rax
                syscall
                testq %rax, %rax
                js .Lio_append_fail
                movq %rax, %rdi
                leaq 24(%r12), %rsi
                movl 16(%r12), %edx
                movq $1, %rax
                syscall
                movq %rdi, %rdi
                movq $3, %rax
                syscall
                movq $1, %rax
                popq %r12
                popq %rbx
                ret
            .Lio_append_fail:
                xorl %eax, %eax
                popq %r12
                popq %rbx
                ret

            .globl kof_io_delete
            .type kof_io_delete, @function
            kof_io_delete:
                pushq %rbx
                movq %rdi, %rbx
                call kof_io_stat_mode
                testq %rax, %rax
                js .Lio_del_fail
                andq $61440, %rax
                cmpq $16384, %rax
                je .Lio_del_rmdir
                leaq 24(%rbx), %rdi
                movq $87, %rax
                syscall
                testq %rax, %rax
                je .Lio_del_ok
            .Lio_del_fail:
                xorl %eax, %eax
                popq %rbx
                ret
            .Lio_del_rmdir:
                leaq 24(%rbx), %rdi
                movq $84, %rax
                syscall
                testq %rax, %rax
                jne .Lio_del_fail
            .Lio_del_ok:
                movq $1, %rax
                popq %rbx
                ret

            .globl kof_io_file_size
            .type kof_io_file_size, @function
            kof_io_file_size:
                pushq %rbx
                subq $144, %rsp
                movq %rdi, %rbx
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq %rsp, %rdx
                xorq %r10, %r10
                movq $262, %rax
                syscall
                testq %rax, %rax
                js .Lio_size_err
                movq 48(%rsp), %rax
                addq $144, %rsp
                popq %rbx
                ret
            .Lio_size_err:
                movq $-1, %rax
                addq $144, %rsp
                popq %rbx
                ret

            // ── Bytes ────────────────────────────────────────────

            .globl kof_io_read_bytes
            .type kof_io_read_bytes, @function
            kof_io_read_bytes:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq $0, %rdx
                movq $257, %rax
                syscall
                testq %rax, %rax
                js .Lio_bytes_err
                movq %rax, %rbx
                subq $144, %rsp
                movq %rbx, %rdi
                movq %rsp, %rsi
                movq $5, %rax
                syscall
                movq 48(%rsp), %r12
                addq $144, %rsp
                movq %r12, %rdi
                call kof_alloc
                movq %rax, %r15
                movq %rbx, %rdi
                movq %r15, %rsi
                movq %r12, %rdx
                movq $0, %rax
                syscall
                movq %rbx, %rdi
                movq $3, %rax
                syscall
                movl %r12d, %edi
                movq $4, %rsi
                call kof_array_alloc
                movq %rax, %r13
                xorq %rcx, %rcx
            .Lio_bytes_spread:
                cmpq %r12, %rcx
                jge .Lio_bytes_done
                movb (%r15,%rcx), %al
                movb %al, 24(%r13,%rcx,4)
                incq %rcx
                jmp .Lio_bytes_spread
            .Lio_bytes_done:
                movq %r13, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_bytes_err:
                xorl %eax, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_io_write_bytes
            .type kof_io_write_bytes, @function
            kof_io_write_bytes:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rsi, %r12
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq $577, %rdx
                movq $420, %r10
                movq $257, %rax
                syscall
                testq %rax, %rax
                js .Lio_wb_err
                movq %rax, %rbx
                movl 16(%r12), %r14d
                leal 16(%r14), %edi
                call kof_alloc
                movq %rax, %r13
                xorq %rcx, %rcx
            .Lio_wb_copy:
                cmpq %r14, %rcx
                jge .Lio_wb_write
                movl 24(%r12,%rcx,4), %eax
                movb %al, (%r13,%rcx)
                incq %rcx
                jmp .Lio_wb_copy
            .Lio_wb_write:
                movq %rbx, %rdi
                movq %r13, %rsi
                movq %r14, %rdx
                movq $1, %rax
                syscall
                movq %rbx, %rdi
                movq $3, %rax
                syscall
                movq $1, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_wb_err:
                xorl %eax, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_io_append_bytes
            .type kof_io_append_bytes, @function
            kof_io_append_bytes:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rsi, %r12
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq $1089, %rdx
                movq $420, %r10
                movq $257, %rax
                syscall
                testq %rax, %rax
                js .Lio_ab_err
                movq %rax, %rbx
                movl 16(%r12), %r14d
                leal 16(%r14), %edi
                call kof_alloc
                movq %rax, %r13
                xorq %rcx, %rcx
            .Lio_ab_copy:
                cmpq %r14, %rcx
                jge .Lio_ab_write
                movl 24(%r12,%rcx,4), %eax
                movb %al, (%r13,%rcx)
                incq %rcx
                jmp .Lio_ab_copy
            .Lio_ab_write:
                movq %rbx, %rdi
                movq %r13, %rsi
                movq %r14, %rdx
                movq $1, %rax
                syscall
                movq %rbx, %rdi
                movq $3, %rax
                syscall
                movq $1, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_ab_err:
                xorl %eax, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            // ── Directory ────────────────────────────────────────

            .globl kof_io_dir_create
            .type kof_io_dir_create, @function
            kof_io_dir_create:
                pushq %rbx
                movq %rdi, %rbx
                leaq 24(%rbx), %rdi
                movq $493, %rsi
                movq $83, %rax
                syscall
                testq %rax, %rax
                je .Lio_mkdir_ok
                xorl %eax, %eax
                popq %rbx
                ret
            .Lio_mkdir_ok:
                movq $1, %rax
                popq %rbx
                ret

            .globl kof_io_dir_create_dirs
            .type kof_io_dir_create_dirs, @function
            kof_io_dir_create_dirs:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                subq $520, %rsp
                movq %rdi, %rbx
                movl 16(%rbx), %r13d
                movq $1, %r12
            .Lio_dirs_scan:
                cmpq %r13, %r12
                jg .Lio_dirs_final
                leaq 24(%rbx), %rax
                cmpb $47, (%rax,%r12)
                jne .Lio_dirs_advance
                call .Lio_dirs_mkdir_prefix
                testq %rax, %rax
                je .Lio_dirs_advance
                jmp .Lio_dirs_err
            .Lio_dirs_advance:
                incq %r12
                jmp .Lio_dirs_scan
            .Lio_dirs_final:
                leaq 24(%rbx), %rdi
                movq $493, %rsi
                movq $83, %rax
                syscall
                testq %rax, %rax
                je .Lio_dirs_ok
                cmpq $-17, %rax
                jne .Lio_dirs_err
            .Lio_dirs_ok:
                movq $1, %rax
                addq $520, %rsp
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_dirs_err:
                xorl %eax, %eax
                addq $520, %rsp
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_dirs_mkdir_prefix:
                leaq 8(%rsp), %r8
                movq %r12, %rcx
                xorq %r9, %r9
                leaq 24(%rbx), %rsi
                movq %r8, %rdi
            .Lio_dirs_copy:
                cmpq %rcx, %r9
                jge .Lio_dirs_copy_done
                movb (%rsi,%r9), %al
                movb %al, (%rdi,%r9)
                incq %r9
                jmp .Lio_dirs_copy
            .Lio_dirs_copy_done:
                movb $0, (%rdi,%r9)
                cmpq $1, %rcx
                jle .Lio_dirs_prefix_skip
                movq %r8, %rdi
                movq $493, %rsi
                movq $83, %rax
                syscall
                testq %rax, %rax
                je .Lio_dirs_prefix_skip
                cmpq $-17, %rax
                je .Lio_dirs_prefix_skip
                movq $-1, %rax
                ret
            .Lio_dirs_prefix_skip:
                xorl %eax, %eax
                ret

            .globl kof_io_dir_delete
            .type kof_io_dir_delete, @function
            kof_io_dir_delete:
                pushq %rbx
                movq %rdi, %rbx
                leaq 24(%rbx), %rdi
                movq $84, %rax
                syscall
                testq %rax, %rax
                je .Lio_rmdir_ok
                movq $-1, %rax
                popq %rbx
                ret
            .Lio_rmdir_ok:
                movq $1, %rax
                popq %rbx
                ret

            .globl kof_io_dir_list
            .type kof_io_dir_list, @function
            kof_io_dir_list:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $32768, %rsp
                movq %rdi, %rbx
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq $65536, %rdx
                movq $257, %rax
                syscall
                testq %rax, %rax
                js .Lio_list_err
                movq %rax, %rbx
                movq %rsp, %r13
                call kof_list_new
                movq %rax, %r12
            .Lio_list_loop:
                movq %rbx, %rdi
                movq %r13, %rsi
                movq $32768, %rdx
                movq $217, %rax
                syscall
                testq %rax, %rax
                jle .Lio_list_done
                movq %rax, %r14
                movq %r13, %r15
            .Lio_list_entry:
                movzwq 16(%r15), %rdx
                testq %rdx, %rdx
                je .Lio_list_next_buf
                cmpb $46, 19(%r15)
                jne .Lio_list_add
                cmpb $0, 20(%r15)
                je .Lio_list_skip
                cmpb $46, 20(%r15)
                jne .Lio_list_add
                cmpb $0, 21(%r15)
                je .Lio_list_skip
            .Lio_list_add:
                leaq 19(%r15), %rdi
                call kof_io_strlen
                movq %rax, %rsi
                leaq 19(%r15), %rdi
                call kof_io_make_string
                movq %rax, %rsi
                movq %r12, %rdi
                call kof_list_add
            .Lio_list_skip:
                movzwq 16(%r15), %rdx
                addq %rdx, %r15
                leaq (%r13,%r14), %rax
                cmpq %rax, %r15
                jb .Lio_list_entry
                jmp .Lio_list_loop
            .Lio_list_next_buf:
                jmp .Lio_list_done
            .Lio_list_done:
                movq %rbx, %rdi
                movq $3, %rax
                syscall
                call .Lio_list_sort
                movq %r12, %rax
                addq $32768, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_list_sort:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r11
                movq %r12, %rbx
                movl 16(%rbx), %r12d
                movq 24(%rbx), %r11
                movq $1, %r13
            .Lio_sort_i:
                cmpl %r12d, %r13d
                jge .Lio_sort_done
                movq (%r11,%r13,8), %r15
                movl %r13d, %r14d
            .Lio_sort_j:
                testl %r14d, %r14d
                jle .Lio_sort_place
                movq -8(%r11,%r14,8), %rdi
                movq %r15, %rsi
                call .Lio_str_less
                testq %rax, %rax
                jne .Lio_sort_place
                movq -8(%r11,%r14,8), %rax
                movq %rax, (%r11,%r14,8)
                decl %r14d
                jmp .Lio_sort_j
            .Lio_sort_place:
                movq %r15, (%r11,%r14,8)
                incq %r13
                jmp .Lio_sort_i
            .Lio_sort_done:
                popq %r11
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lio_str_less:
                movl 16(%rdi), %ecx
                movl 16(%rsi), %r8d
                xorl %r9d, %r9d
            .Lio_less_loop:
                cmpl %ecx, %r9d
                jge .Lio_less_left_done
                cmpl %r8d, %r9d
                jge .Lio_less_longer
                movzbl 24(%rdi,%r9), %eax
                movzbl 24(%rsi,%r9), %r10d
                cmpl %r10d, %eax
                jl .Lio_less_true
                jg .Lio_less_false
                incl %r9d
                jmp .Lio_less_loop
            .Lio_less_left_done:
                cmpl %r8d, %r9d
                jl .Lio_less_true
            .Lio_less_false:
                xorl %eax, %eax
                ret
            .Lio_less_longer:
                xorl %eax, %eax
                ret
            .Lio_less_true:
                movq $1, %rax
                ret
            .Lio_list_err:
                xorl %eax, %eax
                addq $32768, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            """);
    }

    private static void emitUiColorFunctions(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lui_rgb: .asciz "rgb("
            .Lui_rgba: .asciz "rgba("
            .Lui_comma: .asciz ", "
            .Lui_close_str: .asciz ")"
            .section .text

            kof_ui_color_to_css:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movl %edi, %ebx
                movl %ebx, %r12d
                andl $255, %r12d
                xorl %r14d, %r14d
                cmpl $255, %r12d
                je .Lui_rgb_prefix
                leaq .Lui_rgba(%rip), %rdi
                movq $5, %rsi
                call kof_string_from_literal
                movq %rax, %r15
                movq $1, %r14
                jmp .Lui_red
            .Lui_rgb_prefix:
                leaq .Lui_rgb(%rip), %rdi
                movq $4, %rsi
                call kof_string_from_literal
                movq %rax, %r15
            .Lui_red:
                movl %ebx, %r12d
                shrl $24, %r12d
                andl $255, %r12d
                movl %r12d, %edi
                call kof_int_to_string
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %r15
                leaq .Lui_comma(%rip), %rdi
                movq $2, %rsi
                call kof_string_from_literal
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %r15
                movl %ebx, %r12d
                shrl $16, %r12d
                andl $255, %r12d
                movl %r12d, %edi
                call kof_int_to_string
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %r15
                leaq .Lui_comma(%rip), %rdi
                movq $2, %rsi
                call kof_string_from_literal
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %r15
                movl %ebx, %r12d
                shrl $8, %r12d
                andl $255, %r12d
                movl %r12d, %edi
                call kof_int_to_string
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %r15
                testq %r14, %r14
                je .Lui_close
                leaq .Lui_comma(%rip), %rdi
                movq $2, %rsi
                call kof_string_from_literal
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %r15
                movl %ebx, %r12d
                andl $255, %r12d
                movl %r12d, %edi
                call kof_int_to_string
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %r15
            .Lui_close:
                leaq .Lui_close_str(%rip), %rdi
                movq $1, %rsi
                call kof_string_from_literal
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %r15
                movq %r15, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            """);
    }

    /**
     * kof.db no target Native — link direto da client library (libsqlite3)
     * no passo do ld (padrão kof-webview: sem JDBC driver, sem headers).
     *
     * A convenção segue o System V usado pelo NativeBackend: args em
     * rdi/rsi/rdx/rcx/r8/r9, retorno em rax. O handle de conexão é um
     * KofString "db<N>" indexando o registry abaixo. Binds chegam crus
     * (kof_box é no-op no native): valores &lt; 0x1000000 são Ints, senão
     * ponteiros de KofString — heurística documentada (um bind Int maior
     * que 16MB seria interpretado como String).
     */
    static void emitDbSqlite(StringBuilder sb) {
        sb.append("""
            .section .data
            .Ldb_null: .asciz "null"
            .section .bss
            .Ldb_slots: .zero 512
            .Ldb_types: .zero 64
            .Ldb_count: .quad 0
            .Ldb_mysql_buf: .zero 65536
            .Ldb_mysql_names: .zero 1024
            .Ldb_mysql_seq: .zero 1
            .section .data
            .Ldb_mysql_plugin: .asciz "mysql_native_password"
            .Ldb_mysql_empty: .asciz ""
            .Ldb_mysql_nullstr: .asciz "null"
            .section .text

            # ── SHA-1 (para o auth scramble do MySQL) ────────────────
            # kof_sec_sha1_block: (rdi=h[5] em LE na stack, rsi=bloco 64B)
            .globl kof_sec_sha1_block
            .type kof_sec_sha1_block, @function
            kof_sec_sha1_block:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $352, %rsp           # w[80]=320 + h[5]=20
                movq %rdi, %r12
                movq %rsi, %r13
                movl 0(%r12), %eax
                movl %eax, 320(%rsp)
                movl 4(%r12), %eax
                movl %eax, 324(%rsp)
                movl 8(%r12), %eax
                movl %eax, 328(%rsp)
                movl 12(%r12), %eax
                movl %eax, 332(%rsp)
                movl 16(%r12), %eax
                movl %eax, 336(%rsp)
                # w[0..15] = bloco em BE
                xorl %ecx, %ecx
            .Lsha1_load:
                cmpl $16, %ecx
                jge .Lsha1_load_done
                movl (%r13,%rcx,4), %eax
                bswapl %eax
                movl %eax, (%rsp,%rcx,4)
                incq %rcx
                jmp .Lsha1_load
            .Lsha1_load_done:
                # w[16..79]
                movl $16, %ecx
            .Lsha1_w:
                cmpl $80, %ecx
                jge .Lsha1_w_done
                movl -12(%rsp,%rcx,4), %eax
                xorl -32(%rsp,%rcx,4), %eax
                xorl -56(%rsp,%rcx,4), %eax
                xorl -64(%rsp,%rcx,4), %eax
                roll $1, %eax
                movl %eax, (%rsp,%rcx,4)
                incq %rcx
                jmp .Lsha1_w
            .Lsha1_w_done:
                # a..e = h[0..4]
                movl 320(%rsp), %r8d
                movl 324(%rsp), %r9d
                movl 328(%rsp), %r10d
                movl 332(%rsp), %r11d
                movl 336(%rsp), %ebx
                xorl %r15d, %r15d
            .Lsha1_round:
                cmpl $80, %r15d
                jge .Lsha1_round_done
                # f/g/K por fase
                cmpl $20, %r15d
                jge .Lsha1_phase2
                movl %r9d, %eax
                andl %r10d, %eax
                movl %r9d, %edx
                notl %edx
                andl %r11d, %edx
                orl %edx, %eax
                movl $0x5A827999, %r14d
                jmp .Lsha1_f_done
            .Lsha1_phase2:
                cmpl $40, %r15d
                jge .Lsha1_phase3
                movl %r9d, %eax
                xorl %r10d, %eax
                xorl %r11d, %eax
                movl $0x6ED9EBA1, %r14d
                jmp .Lsha1_f_done
            .Lsha1_phase3:
                cmpl $60, %r15d
                jge .Lsha1_phase4
                movl %r9d, %eax
                andl %r10d, %eax
                movl %r9d, %edx
                andl %r11d, %edx
                orl %edx, %eax
                movl %r10d, %edx
                andl %r11d, %edx
                orl %edx, %eax
                movl $0x8F1BBCDC, %r14d
                jmp .Lsha1_f_done
            .Lsha1_phase4:
                movl %r9d, %eax
                xorl %r10d, %eax
                xorl %r11d, %eax
                movl $0xCA62C1D6, %r14d
            .Lsha1_f_done:
                # temp = ROTL5(a) + f + e + K + W[i]
                movl %r8d, %edx
                roll $5, %edx
                addl %eax, %edx
                addl %ebx, %edx
                addl %r14d, %edx
                addl (%rsp,%r15,4), %edx
                movl %r9d, %eax
                movl %r11d, %ebx
                movl %r10d, %r11d
                roll $30, %eax
                movl %eax, %r10d
                movl %r8d, %r9d
                movl %edx, %r8d
                incq %r15
                jmp .Lsha1_round
            .Lsha1_round_done:
                addl %r8d, 320(%rsp)
                addl %r9d, 324(%rsp)
                addl %r10d, 328(%rsp)
                addl %r11d, 332(%rsp)
                addl %ebx, 336(%rsp)
                movl 320(%rsp), %eax
                movl %eax, 0(%r12)
                movl 324(%rsp), %eax
                movl %eax, 4(%r12)
                movl 328(%rsp), %eax
                movl %eax, 8(%r12)
                movl 332(%rsp), %eax
                movl %eax, 12(%r12)
                movl 336(%rsp), %eax
                movl %eax, 16(%r12)
                addq $352, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_sha1_internal(rdi=out20, rsi=src, rdx=len)
            .globl kof_sec_sha1_internal
            .type kof_sec_sha1_internal, @function
            kof_sec_sha1_internal:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $168, %rsp          # h[5]=20 + bloco 64 + pad 128
                movq %rdi, %r12
                movq %rsi, %r13
                movq %rdx, %r14
                movl $0x67452301, 0(%rsp)
                movl $0xEFCDAB89, 4(%rsp)
                movl $0x98BADCFE, 8(%rsp)
                movl $0x10325476, 12(%rsp)
                movl $0xC3D2E1F0, 16(%rsp)
                xorq %r15, %r15
            .Lsha1_full:
                movq %r14, %rax
                subq %r15, %rax
                cmpq $64, %rax
                jl .Lsha1_final
                movq %rsp, %rdi
                leaq (%r13,%r15), %rsi
                call kof_sec_sha1_block
                addq $64, %r15
                jmp .Lsha1_full
            .Lsha1_final:
                movq %r14, %rax
                subq %r15, %rax
                movq %rax, %rcx
                leaq 20(%rsp), %rdi
                xorq %rdx, %rdx
            .Lsha1_copy:
                cmpq %rcx, %rdx
                jge .Lsha1_copy_done
                leaq (%r13,%r15), %rsi
                movb (%rsi,%rdx), %al
                movb %al, (%rdi,%rdx)
                incq %rdx
                jmp .Lsha1_copy
            .Lsha1_copy_done:
                movb $0x80, (%rdi,%rcx)
                movq %rcx, %r15
                movq %rcx, %rdx
                incq %rdx
            .Lsha1_pad:
                cmpq $128, %rdx
                jge .Lsha1_pad_done
                movb $0, (%rdi,%rdx)
                incq %rdx
                jmp .Lsha1_pad
            .Lsha1_pad_done:
                movq %r14, %rax
                shlq $3, %rax
                bswapq %rax
                movq %rax, 56(%rdi)
                # primeiro bloco do pad (sempre o final: 1 bloco p/ len<56)
                movq %rsp, %rdi
                leaq 20(%rsp), %rsi
                call kof_sec_sha1_block
                movq %r12, %rdi
                movq %rsp, %rsi
                # escreve o digest (BE) direto no out
                movl 0(%rsp), %eax
                bswapl %eax
                movl %eax, 0(%r12)
                movl 4(%rsp), %eax
                bswapl %eax
                movl %eax, 4(%r12)
                movl 8(%rsp), %eax
                bswapl %eax
                movl %eax, 8(%r12)
                movl 12(%rsp), %eax
                bswapl %eax
                movl %eax, 12(%r12)
                movl 16(%rsp), %eax
                bswapl %eax
                movl %eax, 16(%r12)
                addq $168, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_db_mysql_scramble(rdi=out20, rsi=seed, rdx=seedlen, rcx=pass KofString)
            .globl kof_db_mysql_scramble
            .type kof_db_mysql_scramble, @function
            kof_db_mysql_scramble:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $128, %rsp
                movq %rdi, %r12          # out
                movq %rsi, %r13          # seed
                movq %rdx, %r14          # seedlen
                movq %rcx, %r15          # pass
                # stage1 = SHA1(pass)
                leaq 96(%rsp), %rdi
                leaq 24(%r15), %rsi
                movslq 16(%r15), %rdx
                call kof_sec_sha1_internal
                # stage2 = SHA1(stage1)
                leaq 76(%rsp), %rdi
                leaq 96(%rsp), %rsi
                movl $20, %edx
                call kof_sec_sha1_internal
                # stage3 = SHA1(seed + stage2) → 56(%rsp)
                leaq 56(%rsp), %rdi
                leaq 36(%rsp), %rsi
                # copia seed para 36(%rsp)
                xorq %rcx, %rcx
            .Lscr_copy_seed:
                cmpq %r14, %rcx
                jge .Lscr_copy_seed_done
                movb (%r13,%rcx), %al
                movb %al, 36(%rsp,%rcx)
                incq %rcx
                jmp .Lscr_copy_seed
            .Lscr_copy_seed_done:
                movq %r14, %r8
                xorl %ecx, %ecx
            .Lscr_copy_st2:
                cmpl $20, %ecx
                jge .Lscr_copy_st2_done
                movb 76(%rsp,%rcx), %al
                movb %al, 36(%rsp,%r8)
                incq %rcx
                incq %r8
                jmp .Lscr_copy_st2
            .Lscr_copy_st2_done:
                movq %r8, %rdx
                leaq 36(%rsp), %rsi
                call kof_sec_sha1_internal
                # result = stage1 XOR stage3
                xorl %ecx, %ecx
            .Lscr_xor:
                cmpl $20, %ecx
                jge .Lscr_xor_done
                movb 96(%rsp,%rcx), %al
                xorb 56(%rsp,%rcx), %al
                movb %al, (%r12,%rcx)
                incq %rcx
                jmp .Lscr_xor
            .Lscr_xor_done:
                addq $128, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_db_mysql_lenenc: rsi=buf → eax=valor, rsi=proxima pos
            kof_db_mysql_lenenc:
                movzbl (%rsi), %eax
                cmpb $0xFC, %al
                je .Ldb_len_2
                cmpb $0xFD, %al
                je .Ldb_len_3
                incq %rsi
                ret
            .Ldb_len_2:
                movzwl 1(%rsi), %eax
                addq $3, %rsi
                ret
            .Ldb_len_3:
                movzbl 1(%rsi), %eax
                movzbl 2(%rsi), %edx
                shll $8, %edx
                orl %edx, %eax
                movzbl 3(%rsi), %edx
                shll $16, %edx
                orl %edx, %eax
                addq $4, %rsi
                ret

            # resolve "db<N>" → sqlite3* em rax (leaf: clobbera rsi/rdx/rax/rcx)
            kof_db_resolve:
                leaq 26(%rdi), %rsi
                xorl %ecx, %ecx
            .Ldb_res_parse:
                movzbl (%rsi), %edx
                testb %dl, %dl
                je .Ldb_res_done
                subl $'0', %edx
                imull $10, %ecx, %ecx
                addl %edx, %ecx
                incq %rsi
                jmp .Ldb_res_parse
            .Ldb_res_done:
                decl %ecx
                movq .Ldb_slots(,%rcx,8), %rax
                ret

            # kof_db_type(id) → eax: 1=sqlite 2=mysql 3=oracle 4=mongo
            kof_db_type:
                leaq 26(%rdi), %rsi
                xorl %ecx, %ecx
            .Ldb_typ_parse:
                movzbl (%rsi), %edx
                testb %dl, %dl
                je .Ldb_typ_done
                subl $'0', %edx
                imull $10, %ecx, %ecx
                addl %edx, %ecx
                incq %rsi
                jmp .Ldb_typ_parse
            .Ldb_typ_done:
                decl %ecx
                movzbl .Ldb_types(,%rcx,1), %eax
                ret

            # kof_db_connect(url) — sqlite: ou mysql:// (user/pass = NULL)
            .globl kof_db_connect
            .type kof_db_connect, @function
            kof_db_connect:
                xorq %r14, %r14
                xorq %r15, %r15
                jmp kof_db_connect_inner

            # kof_db_connect2(url, user, pass)
            .globl kof_db_connect2
            .type kof_db_connect2, @function
            kof_db_connect2:
                movq %rsi, %r14
                movq %rdx, %r15

            kof_db_connect_inner:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                cmpb $'s', 24(%rbx)
                jne .Ldb_conn_maybe_mysql
                cmpb $'q', 25(%rbx)
                jne .Ldb_connect_bad
                cmpb $'l', 26(%rbx)
                jne .Ldb_connect_bad
                cmpb $'i', 27(%rbx)
                jne .Ldb_connect_bad
                cmpb $'t', 28(%rbx)
                jne .Ldb_connect_bad
                cmpb $'e', 29(%rbx)
                jne .Ldb_connect_bad
                cmpb $':', 30(%rbx)
                jne .Ldb_connect_bad
                leaq 31(%rbx), %rdi
                subq $40, %rsp
                movq %rsp, %rsi
                call sqlite3_open
                testl %eax, %eax
                jne .Ldb_connect_fail
                movq (%rsp), %r12
                addq $40, %rsp
                movl $1, %eax
                jmp .Ldb_connect_register
            .Ldb_conn_maybe_mysql:
                cmpb $'m', 24(%rbx)
                jne .Ldb_connect_bad
                cmpb $'y', 25(%rbx)
                jne .Ldb_connect_bad
                cmpb $'s', 26(%rbx)
                jne .Ldb_connect_bad
                cmpb $'q', 27(%rbx)
                jne .Ldb_connect_bad
                cmpb $'l', 28(%rbx)
                jne .Ldb_connect_bad
                cmpb $':', 29(%rbx)
                jne .Ldb_connect_bad
                cmpb $'/', 30(%rbx)
                jne .Ldb_connect_bad
                cmpb $'/', 31(%rbx)
                jne .Ldb_connect_bad
                # mysql:// — parse host[:port][/db]
                leaq 24(%rbx), %r12
                leaq 8(%r12), %rsi
                xorl %r10d, %r10d
                xorl %r13d, %r13d
            .Ldb_mysql_host:
                movzbl (%rsi), %eax
                testb %al, %al
                jz .Ldb_mysql_open
                cmpb $':', %al
                je .Ldb_mysql_colon
                cmpb $'/', %al
                je .Ldb_mysql_slash
                incq %rsi
                jmp .Ldb_mysql_host
            .Ldb_mysql_colon:
                movb $0, (%rsi)
                incq %rsi
            .Ldb_mysql_port:
                movzbl (%rsi), %eax
                testb %al, %al
                jz .Ldb_mysql_open
                cmpb $'/', %al
                je .Ldb_mysql_slash
                subl $'0', %eax
                imull $10, %r10d, %r10d
                addl %eax, %r10d
                incq %rsi
                jmp .Ldb_mysql_port
            .Ldb_mysql_slash:
                movb $0, (%rsi)
                incq %rsi
                movq %rsi, %r13
            .Ldb_mysql_open:
                # Protocolo MySQL/MariaDB na mão sobre sockets nativos
                # (zero libs — a filosofia do Kof). M1: auth com senha
                # vazia (scramble SHA1 fica documentado como follow-up).
                testl %r10d, %r10d
                jnz .Ldb_mysql_port_ok
                movl $3306, %r10d
            .Ldb_mysql_port_ok:
                movl $2, %edi
                movl $1, %esi
                xorl %edx, %edx
                movq $41, %rax
                syscall
                testq %rax, %rax
                js .Ldb_connect_bad
                movq %rax, %rbx
                leaq -32(%rsp), %r8
                movw $2, (%r8)
                movl %r10d, %eax
                xchgb %al, %ah
                movw %ax, 2(%r8)
                leaq 8(%r12), %rsi
                xorl %r9d, %r9d
                xorl %ecx, %ecx
            .Ldb_ip:
                movzbl (%rsi), %eax
                testb %al, %al
                jz .Ldb_ip_last
                cmpb $'.', %al
                je .Ldb_ip_store
                subl $'0', %eax
                imull $10, %ecx, %ecx
                addl %eax, %ecx
                incq %rsi
                jmp .Ldb_ip
            .Ldb_ip_store:
                movb %cl, 4(%r8,%r9,1)
                incq %r9
                xorl %ecx, %ecx
                incq %rsi
                jmp .Ldb_ip
            .Ldb_ip_last:
                movb %cl, 4(%r8,%r9,1)
                movq %rbx, %rdi
                movq %r8, %rsi
                movl $16, %edx
                movq $42, %rax
                syscall
                testq %rax, %rax
                js .Ldb_connect_bad
                addq $32, %rsp
                # handshake
                leaq .Ldb_mysql_buf(%rip), %r12
                movq %rbx, %rdi
                movq %r12, %rsi
                movl $4096, %edx
                call kof_net_read
                testq %rax, %rax
                jle .Ldb_connect_bad
                cmpb $0x0A, 4(%r12)
                jne .Ldb_connect_bad
                # auth packet no buf: cap(4)+max(4)+charset(1)+23zeros
                leaq .Ldb_mysql_buf(%rip), %r8
                movl $0x00088201, 4(%r8)
                movl $0x01000000, 8(%r8)
                movb $0x21, 12(%r8)
                leaq 13(%r8), %rdi
                xorl %ecx, %ecx
            .Ldb_auth_zero:
                cmpl $23, %ecx
                jge .Ldb_auth_zero_done
                movb $0, (%rdi,%rcx)
                incq %rcx
                jmp .Ldb_auth_zero
            .Ldb_auth_zero_done:
                leaq 36(%r8), %rdi
                testq %r14, %r14
                jz .Ldb_auth_user_empty
                leaq 24(%r14), %rsi
                movl 16(%r14), %ecx
                movq %rcx, %rdx
                call kof_memcpy
                leaq 36(%r8), %rdi
                addq %rcx, %rdi
                jmp .Ldb_auth_user_end
            .Ldb_auth_user_empty:
                movq %rdi, %rsi
                movq %rsi, %rdi
            .Ldb_auth_user_end:
                movb $0, (%rdi)
                incq %rdi
                movb $0, (%rdi)
                incq %rdi
                testq %r13, %r13
                jz .Ldb_auth_db_empty
                movq %r13, %rax
                jmp .Ldb_auth_db_copy
            .Ldb_auth_db_empty:
                leaq .Ldb_mysql_empty(%rip), %rax
            .Ldb_auth_db_copy:
            .Ldb_auth_db_loop:
                movzbl (%rax), %ecx
                movb %cl, (%rdi)
                testb %cl, %cl
                je .Ldb_auth_db_done
                incq %rax
                incq %rdi
                jmp .Ldb_auth_db_loop
            .Ldb_auth_db_done:
                incq %rdi
                leaq .Ldb_mysql_plugin(%rip), %rsi
            .Ldb_auth_plug_loop:
                movzbl (%rsi), %ecx
                movb %cl, (%rdi)
                testb %cl, %cl
                je .Ldb_auth_plug_done
                incq %rsi
                incq %rdi
                jmp .Ldb_auth_plug_loop
            .Ldb_auth_plug_done:
                incq %rdi
                # header: len = rdi - (buf+4), seq 1
                leaq .Ldb_mysql_buf(%rip), %r12
                subq %r12, %rdi
                subq $4, %rdi
                movl %edi, %eax
                movb %al, 0(%r12)
                shrl $8, %eax
                movb %al, 1(%r12)
                shrl $8, %eax
                movb %al, 2(%r12)
                movb $1, 3(%r12)
                leaq 4(%rdi), %rdx
                movq %rbx, %rdi
                movq %r12, %rsi
                call kof_net_write
                movq %rbx, %rdi
                movq %r12, %rsi
                movl $4096, %edx
                call kof_net_read
                testq %rax, %rax
                jle .Ldb_connect_bad
                cmpb $0xFE, 4(%r12)
                jne .Ldb_auth_done
                # AuthSwitchRequest: [0xFE][plugin NUL][seed...]
                # acha o seed após o plugin; seedlen = len - offset
                leaq 5(%r12), %rsi
            .Ldb_switch_find_plugin_end:
                movzbl (%rsi), %eax
                testb %al, %al
                je .Ldb_switch_plugin_end
                incq %rsi
                jmp .Ldb_switch_find_plugin_end
            .Ldb_switch_plugin_end:
                incq %rsi
                movq %rsi, %r13          # seed
                # len do pacote (3 bytes LE) = header len
                movzbl 0(%r12), %eax
                movzbl 1(%r12), %ecx
                shll $8, %ecx
                orl %ecx, %eax
                movzbl 2(%r12), %ecx
                shll $16, %ecx
                orl %ecx, %eax
                subq %r12, %rsi
                subq $4, %rsi
                subl %esi, %eax          # seedlen = pacote - offset
                movl %eax, %edx
                movq %r13, %rsi
                # out do scramble em 8(%rsp)... usar o stack livre
                leaq .Ldb_mysql_names(%rip), %rdi   # área temporária
                movq %r15, %rcx
                call kof_db_mysql_scramble
                # resposta: plugin NUL + 20 bytes do scramble
                leaq .Ldb_mysql_buf(%rip), %r8
                leaq .Ldb_mysql_plugin(%rip), %rsi
                leaq 4(%r8), %rdi
                xorl %ecx, %ecx
            .Ldb_switch_loop:
                movzbl (%rsi,%rcx), %eax
                movb %al, (%rdi,%rcx)
                testb %al, %al
                je .Ldb_switch_done
                incq %rcx
                jmp .Ldb_switch_loop
            .Ldb_switch_done:
                leaq 4(%r8), %rax
                addq %rcx, %rax
                movq %rax, %r13
                incq %rax
                leaq .Ldb_mysql_names(%rip), %rsi
                xorl %ecx, %ecx
            .Ldb_switch_copy_scramble:
                cmpl $20, %ecx
                jge .Ldb_switch_copy_done
                movb (%rsi,%rcx), %al
                movb %al, (%r13,%rcx)
                incq %rcx
                jmp .Ldb_switch_copy_scramble
            .Ldb_switch_copy_done:
                movq %r13, %rax
                addq $20, %rax
                movq %rax, %r13
                subq %r8, %rax
                subq $4, %rax
                movb %al, 0(%r8)
                shrl $8, %eax
                movb %al, 1(%r8)
                shrl $8, %eax
                movb %al, 2(%r8)
                movb $3, 3(%r8)
                movq %rbx, %rdi
                movq %r8, %rsi
                movq %r13, %rdx
                subq %r8, %rdx
                call kof_net_write
                movq %rbx, %rdi
                movq %r12, %rsi
                movl $4096, %edx
                call kof_net_read
                testq %rax, %rax
                jle .Ldb_connect_bad
            .Ldb_auth_done:
                cmpb $0xFF, 4(%r12)
                je .Ldb_connect_bad
                movb $4, .Ldb_mysql_seq(%rip)
                movq %rbx, %r12
                movl $2, %eax
            .Ldb_connect_register:
                movq .Ldb_count(%rip), %r13
                cmpq $63, %r13
                jge .Ldb_connect_bad
                movq %r12, .Ldb_slots(,%r13,8)
                movb %al, .Ldb_types(,%r13,1)
                incq %r13
                movq %r13, .Ldb_count(%rip)
                # handle = "db" + decimal(r13) em buffer de 48 bytes
                leaq -96(%rsp), %r14
                movq %r13, %rax
                leaq 47(%r14), %rcx
                movb $0, (%rcx)
                decq %rcx
                movq $10, %rbx
            .Ldb_itoa:
                xorl %edx, %edx
                divq %rbx
                addb $'0', %dl
                movb %dl, (%rcx)
                testq %rax, %rax
                je .Ldb_itoa_done
                decq %rcx
                jmp .Ldb_itoa
            .Ldb_itoa_done:
                decq %rcx
                movb $'b', (%rcx)
                decq %rcx
                movb $'d', (%rcx)
                # handle KofString na mao: alloc + header + copia inline
                movq %rcx, %rbx
                leaq 48(%r14), %rdx
                subq %rcx, %rdx
                decq %rdx
                movq %rdx, %rsi
                movq %rsi, %r13
                leal 25(%rsi), %edi
                call kof_alloc
                movq %rax, %r12
                movl $1, 0(%r12)
                movl $0, 4(%r12)
                movq $0, 8(%r12)
                movl %r13d, 16(%r12)
                movl $0, 20(%r12)
                xorl %ecx, %ecx
            .Ldb_handle_copy:
                cmpl %r13d, %ecx
                jge .Ldb_handle_copy_done
                movzbl (%rbx,%rcx), %eax
                movb %al, 24(%r12,%rcx)
                incq %rcx
                jmp .Ldb_handle_copy
            .Ldb_handle_copy_done:
                movb $0, 24(%r12,%r13)
                movq %r12, %rax
                addq $96, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret
            .Ldb_connect_fail:
                addq $40, %rsp
            .Ldb_connect_bad:
                xorl %eax, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # kof_db_close(id: KofString)
            .globl kof_db_close
            .type kof_db_close, @function
            kof_db_close:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                call kof_db_resolve
                testq %rax, %rax
                je .Ldb_close_ret
                movq %rax, %rdi
                call sqlite3_close
            .Ldb_close_ret:
                movq %rbp, %rsp
                popq %rbp
                ret

            # kof_db_execute(id, sql) → sqlite3_exec
            .globl kof_db_execute
            .type kof_db_execute, @function
            kof_db_execute:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rsi, %rbx
                movq %rdi, %r12
                call kof_db_type
                cmpl $1, %eax
                je .Ldb_exec0_sqlite
                cmpl $2, %eax
                je .Ldb_exec0_mysql
                jmp .Ldb_exec0_bad
            .Ldb_exec0_sqlite:
                movq %r12, %rdi
                call kof_db_resolve
                testq %rax, %rax
                je .Ldb_exec0_bad
                movq %rax, %r12
                movq %r12, %rdi
                leaq 24(%rbx), %rsi
                xorl %edx, %edx
                xorl %ecx, %ecx
                xorl %r8d, %r8d
                subq $8, %rsp
                call sqlite3_exec
                addq $8, %rsp
                movl %eax, %eax
                jmp .Ldb_exec0_done
            .Ldb_exec0_mysql:
                movq %r12, %rdi
                call kof_db_resolve
                movq %rax, %r12
                # COM_QUERY no fd
                leaq .Ldb_mysql_buf(%rip), %r13
                movb $0x03, 4(%r13)
                leaq 24(%rbx), %rsi
                movl 16(%rbx), %ecx
                movq %rcx, %rdx
                leaq 5(%r13), %rdi
                call kof_memcpy
                leal 1(%ecx), %eax
                movb %al, 0(%r13)
                shrl $8, %eax
                movb %al, 1(%r13)
                shrl $8, %eax
                movb %al, 2(%r13)
                movb .Ldb_mysql_seq(%rip), %al
                movb %al, 3(%r13)
                incb .Ldb_mysql_seq(%rip)
                movq %r12, %rdi
                movq %r13, %rsi
                leaq 5(%rcx), %rdx
                call kof_net_write
                movq %r12, %rdi
                movq %r13, %rsi
                movl $4096, %edx
                call kof_net_read
                testq %rax, %rax
                jle .Ldb_exec0_bad
                cmpb $0xFF, 4(%r13)
                je .Ldb_exec0_bad
                cmpb $0x00, 4(%r13)
                jne .Ldb_exec0_bad
                movzbl 5(%r13), %eax
                cmpb $0xFC, %al
                je .Ldb_exec0_afc
                cmpb $0xFD, %al
                je .Ldb_exec0_afd
                jmp .Ldb_exec0_done
            .Ldb_exec0_afc:
                movzwl 6(%r13), %eax
                jmp .Ldb_exec0_done
            .Ldb_exec0_afd:
                movzbl 6(%r13), %eax
                movzbl 7(%r13), %edx
                shll $8, %edx
                orl %edx, %eax
                movzbl 8(%r13), %edx
                shll $16, %edx
                orl %edx, %eax
            .Ldb_exec0_done:
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret
            .Ldb_exec0_bad:
                xorl %eax, %eax
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # bind helper: rdi=stmt, esi=index, rdx=valor cru (Int ou KofString*)
            kof_db_bind:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                cmpq $0x1000000, %rdx
                jae .Ldb_bind_str
                pushq %rbx
                movl %edx, %ebx
                subq $8, %rsp
                call sqlite3_bind_int
                addq $8, %rsp
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret
            .Ldb_bind_str:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdx, %rbx
                movl %esi, %r12d
                movq %rdi, %r13
                movq %r13, %rdi
                movl %r12d, %esi
                leaq 24(%rbx), %rdx
                movq $-1, %rcx
                movq $-1, %r8
                subq $8, %rsp
                call sqlite3_bind_text
                addq $8, %rsp
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # Execute/query com binds: layout de pilha uniforme —
            #   6 pushes (48 bytes: rbx,r12,r13,r14,r15,rbp)
            #   +16 para &stmt (sempre), +8 extra para b4 (n>=4)
            #   rbx=id→, rbp=db, r12=sql, r13..r15=b1..b3, 16(%rsp)=b4
            .macro KOF_DB_EXEC_N n
            .globl kof_db_execute\\n
            .type kof_db_execute\\n, @function
            kof_db_execute\\n:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $40, %rsp
                movq %rdi, %rbx
                movq %rsi, %r12
                .if \\n >= 1
                movq %rdx, %r13
                .endif
                .if \\n >= 2
                movq %rcx, %r14
                .endif
                .if \\n >= 3
                movq %r8, %r15
                .endif
                .if \\n >= 4
                movq %r9, 16(%rsp)
                .endif
                call kof_db_resolve
                movq %rax, 32(%rsp)
                movq %rbx, %rdi
                call kof_db_type
                cmpl $1, %eax
                je .Ldb_exec_sqlite\\n
                cmpl $2, %eax
                je .Ldb_exec_mysql\\n
                jmp .Ldb_exec_bad\\n
            .Ldb_exec_sqlite\\n:
                leaq 24(%r12), %rsi
                movq 32(%rsp), %rdi
                movq $-1, %rdx
                movq %rsp, %rcx
                xorl %r8d, %r8d
                call sqlite3_prepare_v2
                movq (%rsp), %r12
                .if \\n >= 1
                movq %r12, %rdi
                movl $1, %esi
                movq %r13, %rdx
                call kof_db_bind
                .endif
                .if \\n >= 2
                movq %r12, %rdi
                movl $2, %esi
                movq %r14, %rdx
                call kof_db_bind
                .endif
                .if \\n >= 3
                movq %r12, %rdi
                movl $3, %esi
                movq %r15, %rdx
                call kof_db_bind
                .endif
                .if \\n >= 4
                movq %r12, %rdi
                movl $4, %esi
                movq 16(%rsp), %rdx
                call kof_db_bind
                .endif
                movq %r12, %rdi
                call sqlite3_step
                movq %r12, %rdi
                call sqlite3_finalize
                movq 32(%rsp), %rdi
                call sqlite3_changes
                jmp .Ldb_exec_done\\n
            .Ldb_exec_mysql\\n:
                # COM_QUERY: [len 3][seq 0][0x03][sql]
                leaq .Ldb_mysql_buf(%rip), %r13
                movb $0x03, 4(%r13)
                leaq 24(%r12), %rsi
                movl 16(%r12), %ecx
                movq %rcx, %rdx
                leaq 5(%r13), %rdi
                call kof_memcpy
                leal 1(%ecx), %eax
                movb %al, 0(%r13)
                shrl $8, %eax
                movb %al, 1(%r13)
                shrl $8, %eax
                movb %al, 2(%r13)
                movb .Ldb_mysql_seq(%rip), %al
                movb %al, 3(%r13)
                incb .Ldb_mysql_seq(%rip)
                movq 32(%rsp), %rdi
                movq %r13, %rsi
                leaq 5(%rcx), %rdx
                call kof_net_write
                movq 32(%rsp), %rdi
                movq %r13, %rsi
                movl $4096, %edx
                call kof_net_read
                testq %rax, %rax
                jle .Ldb_exec_bad\\n
                cmpb $0xFF, 4(%r13)
                je .Ldb_exec_bad\\n
                cmpb $0x00, 4(%r13)
                jne .Ldb_exec_bad\\n
                movzbl 5(%r13), %eax
                cmpb $0xFC, %al
                je .Ldb_exec_afc\\n
                cmpb $0xFD, %al
                je .Ldb_exec_afd\\n
                jmp .Ldb_exec_done\\n
            .Ldb_exec_afc\\n:
                movzwl 6(%r13), %eax
                jmp .Ldb_exec_done\\n
            .Ldb_exec_afd\\n:
                movzbl 6(%r13), %eax
                movzbl 7(%r13), %edx
                shll $8, %edx
                orl %edx, %eax
                movzbl 8(%r13), %edx
                shll $16, %edx
                orl %edx, %eax
                jmp .Ldb_exec_done\\n
            .Ldb_exec_bad\\n:
                xorl %eax, %eax
            .Ldb_exec_done\\n:
                addq $40, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret
            .endm

            KOF_DB_EXEC_N 1
            KOF_DB_EXEC_N 2
            KOF_DB_EXEC_N 3
            KOF_DB_EXEC_N 4

            .macro KOF_DB_QUERY_N n
            .globl kof_db_query\\n
            .type kof_db_query\\n, @function
            kof_db_query\\n:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $40, %rsp
                movq %rdi, %rbx
                movq %rsi, %r12
                .if \\n >= 1
                movq %rdx, %r13
                .endif
                .if \\n >= 2
                movq %rcx, %r14
                .endif
                .if \\n >= 3
                movq %r8, %r15
                .endif
                .if \\n >= 4
                movq %r9, 16(%rsp)
                .endif
                call kof_db_resolve
                movq %rax, 32(%rsp)
                movq %rbx, %rdi
                call kof_db_type
                cmpl $1, %eax
                je .Ldb_query_sqlite\\n
                cmpl $2, %eax
                je .Ldb_query_mysql\\n
                jmp .Ldb_query_bad\\n
            .Ldb_query_sqlite\\n:
                leaq 24(%r12), %rsi
                movq 32(%rsp), %rdi
                movq $-1, %rdx
                movq %rsp, %rcx
                xorl %r8d, %r8d
                call sqlite3_prepare_v2
                movq (%rsp), %r12
                .if \\n >= 1
                movq %r12, %rdi
                movl $1, %esi
                movq %r13, %rdx
                call kof_db_bind
                .endif
                .if \\n >= 2
                movq %r12, %rdi
                movl $2, %esi
                movq %r14, %rdx
                call kof_db_bind
                .endif
                .if \\n >= 3
                movq %r12, %rdi
                movl $3, %esi
                movq %r15, %rdx
                call kof_db_bind
                .endif
                .if \\n >= 4
                movq %r12, %rdi
                movl $4, %esi
                movq 16(%rsp), %rdx
                call kof_db_bind
                .endif
                call kof_list_new
                movq %rax, %r14
            .Ldb_query_row\\n:
                movq %r12, %rdi
                call sqlite3_step
                cmpl $100, %eax
                jne .Ldb_query_sqlite_done\\n
                call kof_json_builder_new
                movq %rax, %r15
                movq %r15, %rdi
                movl $'{', %esi
                call kof_json_builder_char
                xorl %ebx, %ebx
            .Ldb_query_col\\n:
                movq %r12, %rdi
                call sqlite3_column_count
                cmpl %eax, %ebx
                jge .Ldb_query_end\\n
                testl %ebx, %ebx
                jz .Ldb_query_comma\\n
                movq %r15, %rdi
                movl $44, %esi
                call kof_json_builder_char
            .Ldb_query_comma\\n:
                movq %r12, %rdi
                movl %ebx, %esi
                call sqlite3_column_name
                movq %rax, 24(%rsp)
                movq 24(%rsp), %rdi
                call kof_io_strlen
                movq 24(%rsp), %rdi
                movq %rax, %rsi
                call kof_io_make_string
                movq %rax, %rdi
                call kof_json_encode_string
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_json_builder_str
                movq %r15, %rdi
                movl $58, %esi
                call kof_json_builder_char
                movq %r12, %rdi
                movl %ebx, %esi
                call sqlite3_column_type
                cmpl $1, %eax
                je .Ldb_query_int\\n
                cmpl $3, %eax
                je .Ldb_query_text\\n
                leaq .Ldb_null(%rip), %rdi
                xorl %esi, %esi
                call kof_io_make_string
                jmp .Ldb_query_val\\n
            .Ldb_query_int\\n:
                movq %r12, %rdi
                movl %ebx, %esi
                call sqlite3_column_int
                movl %eax, %edi
                call kof_json_encode_int
                jmp .Ldb_query_val\\n
            .Ldb_query_text\\n:
                movq %r12, %rdi
                movl %ebx, %esi
                call sqlite3_column_text
                movq %rax, 24(%rsp)
                movq 24(%rsp), %rdi
                call kof_io_strlen
                movq 24(%rsp), %rdi
                movq %rax, %rsi
                call kof_io_make_string
                movq %rax, %rdi
                call kof_json_encode_string
            .Ldb_query_val\\n:
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_json_builder_str
                incl %ebx
                jmp .Ldb_query_col\\n
            .Ldb_query_end\\n:
                movq %r15, %rdi
                movl $'}', %esi
                call kof_json_builder_char
                movq %r15, %rdi
                call kof_json_builder_result
                movq %r14, %rdi
                movq %rax, %rsi
                call kof_list_add
                jmp .Ldb_query_row\\n
            .Ldb_query_sqlite_done\\n:
                movq %r12, %rdi
                call sqlite3_finalize
                movq %r14, %rax
                jmp .Ldb_query_done\\n
            .Ldb_query_mysql\\n:
                # COM_QUERY + parse do resultset (M1: um read por pacote)
                leaq .Ldb_mysql_buf(%rip), %r13
                movb $0x03, 4(%r13)
                leaq 24(%r12), %rsi
                movl 16(%r12), %ecx
                movq %rcx, %rdx
                leaq 5(%r13), %rdi
                call kof_memcpy
                leal 1(%ecx), %eax
                movb %al, 0(%r13)
                shrl $8, %eax
                movb %al, 1(%r13)
                shrl $8, %eax
                movb %al, 2(%r13)
                movb .Ldb_mysql_seq(%rip), %al
                movb %al, 3(%r13)
                incb .Ldb_mysql_seq(%rip)
                movq 32(%rsp), %rdi
                movq %r13, %rsi
                leaq 5(%rcx), %rdx
                call kof_net_write
                movq 32(%rsp), %rdi
                movq %r13, %rsi
                movl $65536, %edx
                call kof_net_read
                testq %rax, %rax
                jle .Ldb_query_bad\\n
                cmpb $0xFF, 4(%r13)
                je .Ldb_query_bad\\n
                call kof_list_new
                movq %rax, %r14
                # parse: col count (lenenc) no payload
                leaq 4(%r13), %rsi
                call kof_db_mysql_lenenc
                movl %eax, %r13d
                xorl %ebx, %ebx
                # column definitions: 4 lenenc strings (def/schema/table/org) + name
            .Ldb_mysql_cols\\n:
                cmpl %r13d, %ebx
                jge .Ldb_mysql_cols_done\\n
                call kof_db_mysql_lenenc
                call kof_db_mysql_lenenc
                call kof_db_mysql_lenenc
                call kof_db_mysql_lenenc
                # name = lenenc string: len em eax, dados em rsi
                call kof_db_mysql_lenenc
                movq %rsi, .Ldb_mysql_names(,%rbx,8)
                movl %eax, .Ldb_mysql_names+512(,%rbx,4)
                addq %rax, %rsi
                # pula org_name (lenenc) + bloco fixo (12 bytes)
                call kof_db_mysql_lenenc
                addq $12, %rsi
                incq %rbx
                jmp .Ldb_mysql_cols\\n
            .Ldb_mysql_cols_done\\n:
                # EOF apos colunas: 0xFE
                movzbl (%rsi), %eax
                cmpb $0xFE, %al
                je .Ldb_mysql_eof\\n
                call kof_db_mysql_lenenc
            .Ldb_mysql_eof\\n:
                incq %rsi
                movq %rsi, 40(%rsp)
            .Ldb_mysql_row\\n:
                movq 40(%rsp), %rsi
                movzbl (%rsi), %eax
                cmpb $0xFE, %al
                je .Ldb_query_done\\n
                call kof_json_builder_new
                movq %rax, %r15
                movq %r15, %rdi
                movl $'{', %esi
                call kof_json_builder_char
                xorl %ebx, %ebx
            .Ldb_mysql_col\\n:
                cmpl %r13d, %ebx
                jge .Ldb_mysql_row_end\\n
                testl %ebx, %ebx
                jz .Ldb_mysql_nocomma\\n
                movq %r15, %rdi
                movl $44, %esi
                call kof_json_builder_char
            .Ldb_mysql_nocomma\\n:
                # nome da coluna
                movq .Ldb_mysql_names(,%rbx,8), %rdi
                movl .Ldb_mysql_names+512(,%rbx,4), %esi
                call kof_io_make_string
                movq %rax, %rdi
                call kof_json_encode_string
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_json_builder_str
                movq %r15, %rdi
                movl $58, %esi
                call kof_json_builder_char
                # valor: lenenc; NULL = 0xFB
                movq 40(%rsp), %rsi
                movzbl (%rsi), %eax
                cmpb $0xFB, %al
                je .Ldb_mysql_null\\n
                call kof_db_mysql_lenenc
                movq %rsi, %r12
                addq %rax, %r12
                movq %rax, %rdx
                movq %rsi, %rdi
                movq %r12, 40(%rsp)
                call kof_io_make_string
                movq %rax, %r12
                xorl %r10d, %r10d
            .Ldb_mysql_num\\n:
                cmpl 16(%r12), %r10d
                jge .Ldb_mysql_is_num\\n
                movzbl 24(%r12,%r10), %eax
                cmpb $'0', %al
                jb .Ldb_mysql_is_str\\n
                cmpb $'9', %al
                ja .Ldb_mysql_is_str\\n
                incq %r10
                jmp .Ldb_mysql_num\\n
            .Ldb_mysql_is_num\\n:
                movq %r15, %rdi
                movq %r12, %rsi
                call kof_json_builder_str
                jmp .Ldb_mysql_val\\n
            .Ldb_mysql_is_str\\n:
                movq %r12, %rdi
                call kof_json_encode_string
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_json_builder_str
                jmp .Ldb_mysql_val\\n
            .Ldb_mysql_null\\n:
                leaq .Ldb_mysql_nullstr(%rip), %rdi
                xorl %esi, %esi
                call kof_io_make_string
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_json_builder_str
                movq 40(%rsp), %rsi
                incq %rsi
                movq %rsi, 40(%rsp)
            .Ldb_mysql_val\\n:
                incl %ebx
                jmp .Ldb_mysql_col\\n
            .Ldb_mysql_row_end\\n:
                movq %r15, %rdi
                movl $'}', %esi
                call kof_json_builder_char
                movq %r15, %rdi
                call kof_json_builder_result
                movq %r14, %rdi
                movq %rax, %rsi
                call kof_list_add
                jmp .Ldb_mysql_row\\n
            .Ldb_query_bad\\n:
                xorl %eax, %eax
            .Ldb_query_done\\n:
                addq $40, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret
            .endm

            KOF_DB_QUERY_N 0
            KOF_DB_QUERY_N 1
            KOF_DB_QUERY_N 2
            KOF_DB_QUERY_N 3
            KOF_DB_QUERY_N 4
            """);
    }

    private static void emitUiWindowFunctions(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lui_empty: .asciz ""
            .section .text

            kof_ui_window_new:
                movl $1, %eax
                ret
            kof_ui_window_set_title:
                ret
            kof_ui_window_title:
                leaq .Lui_empty(%rip), %rdi
                xorq %rsi, %rsi
                jmp kof_io_make_string
            kof_ui_window_bind:
                ret
            kof_ui_window_show:
                ret
            kof_ui_window_close:
                ret
            kof_ui_window_set_size:
                ret
            kof_ui_window_set_theme:
                ret
            kof_ui_label_new:
                movl $1, %eax
                ret
            kof_ui_label_set_text:
                ret
            kof_ui_label_text:
                leaq .Lui_empty(%rip), %rdi
                xorq %rsi, %rsi
                jmp kof_io_make_string
            kof_ui_label_set_font_size:
                ret
            kof_ui_label_font_size:
                xorl %eax, %eax
                ret
            kof_ui_label_set_bold:
                ret
            kof_ui_label_bold:
                xorl %eax, %eax
                ret
            kof_ui_label_set_color:
                ret
            kof_ui_label_color:
                xorl %eax, %eax
                ret
            kof_ui_label_remove:
                ret
            kof_ui_button_new:
                movl $1, %eax
                ret
            kof_ui_button_new_action:
                movl $1, %eax
                ret
            kof_ui_button_set_text:
                ret
            kof_ui_button_text:
                leaq .Lui_empty(%rip), %rdi
                xorq %rsi, %rsi
                jmp kof_io_make_string
            kof_ui_button_remove:
                ret
            kof_ui_input_new:
                movl $1, %eax
                ret
            kof_ui_input_set_text:
                ret
            kof_ui_input_text:
                leaq .Lui_empty(%rip), %rdi
                xorq %rsi, %rsi
                jmp kof_io_make_string
            kof_ui_input_remove:
                ret
            kof_ui_column_new:
                movl $1, %eax
                ret
            kof_ui_row_new:
                movl $1, %eax
                ret
            kof_ui_view_new:
                movl $1, %eax
                ret
            kof_ui_style_new:
                movl $1, %eax
                ret
            kof_ui_view_bind:
                ret
            kof_ui_view_remove:
                ret
            """);
    }

    private static void emitNetSocket(StringBuilder sb) {
        sb.append("""
            .globl kof_net_socket
            .type kof_net_socket, @function
            kof_net_socket:
                movq $41, %rax
                syscall
                ret
            """);
    }


    private static void emitNetBind(StringBuilder sb) {
        sb.append("""
            .globl kof_net_bind
            .type kof_net_bind, @function
            kof_net_bind:
                pushq %rbx
                pushq %r12
                pushq %r13
                movl %edi, %ebx
                movl %esi, %r12d
                movq %rdx, %r13
                subq $16, %rsp
                movw $2, (%rsp)
                movl %r12d, %eax
                xchgb %al, %ah
                movw %ax, 2(%rsp)
                movl $0, 4(%rsp)
                movq %r13, %rdx
                testq %rdx, %rdx
                jnz .Lkof_net_bind_custom
                leaq 4(%rsp), %rdx
            .Lkof_net_bind_custom:
                movl %ebx, %edi
                movq %rdx, %rsi
                movq $16, %rdx
                movq $49, %rax
                syscall
                addq $16, %rsp
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }


    private static void emitNetListen(StringBuilder sb) {
        sb.append("""
            .globl kof_net_listen
            .type kof_net_listen, @function
            kof_net_listen:
                movq $50, %rax
                syscall
                ret
            """);
    }


    private static void emitNetAccept(StringBuilder sb) {
        sb.append("""
            .globl kof_net_accept
            .type kof_net_accept, @function
            kof_net_accept:
                subq $16, %rsp
                movq $0, (%rsp)
                movq $0, 8(%rsp)
                movq %rsp, %rsi
                leaq 8(%rsp), %rdx
                movq $43, %rax
                syscall
                addq $16, %rsp
                ret
            """);
    }


    private static void emitNetRead(StringBuilder sb) {
        sb.append("""
            .globl kof_net_read
            .type kof_net_read, @function
            kof_net_read:
                movq $0, %rax
                syscall
                ret
            """);
    }


    private static void emitNetWrite(StringBuilder sb) {
        sb.append("""
            .globl kof_net_write
            .type kof_net_write, @function
            kof_net_write:
                movq $1, %rax
                syscall
                ret
            """);
    }


    private static void emitNetClose(StringBuilder sb) {
        sb.append("""
            .globl kof_net_close
            .type kof_net_close, @function
            kof_net_close:
                movq $3, %rax
                syscall
                ret
            """);
    }



    private static void emitInstanceof(StringBuilder sb) {
        sb.append("""
            .globl kof_instanceof
            .type kof_instanceof, @function
            kof_instanceof:
                testq %rdi, %rdi
                jz .Lkof_instanceof_null
                movl (%rdi), %eax
            .Lkof_instanceof_loop:
                cmpl %esi, %eax
                je .Lkof_instanceof_found
                testl %eax, %eax
                jz .Lkof_instanceof_null
                leaq kof_super_table(%rip), %rcx
            .Lkof_instanceof_search:
                movl (%rcx), %edx
                testl %edx, %edx
                jz .Lkof_instanceof_null
                cmpl %edx, %eax
                je .Lkof_instanceof_got_super
                addq $8, %rcx
                jmp .Lkof_instanceof_search
            .Lkof_instanceof_got_super:
                movl 4(%rcx), %eax
                jmp .Lkof_instanceof_loop
            .Lkof_instanceof_found:
                movl $1, %eax
                ret
            .Lkof_instanceof_null:
                xorl %eax, %eax
                ret
            """);
    }

    /**
     * kof.security for the Native target (docs/security.md §5).
     *
     * Implemented in raw x86-64 assembly (no libc): SHA-256, HMAC-SHA256,
     * secure random via the getrandom syscall, constant-time comparison,
     * redaction, and environment secrets via /proc/self/environ.
     * Features not implemented on Native produce a compile-time diagnostic
     * (SECN00x) — never silent divergence.
     */
    private static void emitSecurityFunctions(StringBuilder sb) {
        sb.append("""
            .section .rodata
            .balign 8
            .Lsec_hex_chars: .ascii "0123456789abcdef"
            .Lsec_sha256_k:
                .long 0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5
                .long 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5
                .long 0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3
                .long 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174
                .long 0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc
                .long 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da
                .long 0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7
                .long 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967
                .long 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13
                .long 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85
                .long 0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3
                .long 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070
                .long 0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5
                .long 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3
                .long 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208
                .long 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
            .text

            # kof_sec_sha256_internal(rdi=out32, rsi=src, rdx=len)
            # SHA-256 over an in-memory buffer; writes 32 big-endian bytes.
            .globl kof_sec_sha256_internal
            .type kof_sec_sha256_internal, @function
            kof_sec_sha256_internal:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $296, %rsp          # w[64]=256 + h[8]=32 + len(8) -> use 272..287 for w? layout below
                movq %rdi, %r12          # out
                movq %rsi, %r13          # src
                movq %rdx, %r14          # len
                movl $0x6a09e667, 0(%rsp)
                movl $0xbb67ae85, 4(%rsp)
                movl $0x3c6ef372, 8(%rsp)
                movl $0xa54ff53a, 12(%rsp)
                movl $0x510e527f, 16(%rsp)
                movl $0x9b05688c, 20(%rsp)
                movl $0x1f83d9ab, 24(%rsp)
                movl $0x5be0cd19, 28(%rsp)
                # w starts at 32(%rsp) — 256 bytes → ends at 288(%rsp)
                # process full 64-byte blocks from src
                xorq %r15, %r15          # offset
            .Lsec_sha256_full_block:
                movq %r14, %rax
                subq %r15, %rax
                cmpq $64, %rax
                jl .Lsec_sha256_final_block
                movq %rsp, %rdi
                leaq (%r13,%r15), %rsi
                movl $64, %edx
                call kof_sec_sha256_block
                addq $64, %r15
                jmp .Lsec_sha256_full_block
            .Lsec_sha256_final_block:
                # rem = len - offset; build the final block(s) on the stack
                movq %r14, %rax
                subq %r15, %rax
                movq %rax, %rcx          # rem
                subq $128, %rsp
                xorq %rdx, %rdx
            .Lsec_sha256_copy_rem:
                cmpq %rcx, %rdx
                jge .Lsec_sha256_copy_done
                leaq (%r13,%r15), %rsi
                movb (%rsi,%rdx), %al
                movb %al, (%rsp,%rdx)
                incq %rdx
                jmp .Lsec_sha256_copy_rem
            .Lsec_sha256_copy_done:
                movb $0x80, (%rsp,%rcx)
                movq %rcx, %r15          # rem (offset no longer needed)
                movq %rcx, %rdx
                incq %rdx
            .Lsec_sha256_zero_pad:
                cmpq $128, %rdx
                jge .Lsec_sha256_zero_done
                movb $0, (%rsp,%rdx)
                incq %rdx
                jmp .Lsec_sha256_zero_pad
            .Lsec_sha256_zero_done:
                movq %r15, %rax
                addq $9, %rax
                cmpq $64, %rax
                jg .Lsec_sha256_len_in_second
                movq %r14, %rax
                shlq $3, %rax
                bswapq %rax
                movq %rax, 56(%rsp)
                leaq 128(%rsp), %rdi
                movq %rsp, %rsi
                movl $64, %edx
                call kof_sec_sha256_block
                jmp .Lsec_sha256_final_done
            .Lsec_sha256_len_in_second:
                movq %r14, %rax
                shlq $3, %rax
                bswapq %rax
                movq %rax, 120(%rsp)
                leaq 128(%rsp), %rdi
                movq %rsp, %rsi
                movl $64, %edx
                call kof_sec_sha256_block
                leaq 64(%rsp), %rsi
                leaq 128(%rsp), %rdi
                movl $64, %edx
                call kof_sec_sha256_block
            .Lsec_sha256_final_done:
                addq $128, %rsp
                jmp .Lsec_sha256_finish
            .Lsec_sha256_finish:
                # write h0..h7 big-endian to out
                xorq %rcx, %rcx
            .Lsec_sha256_out:
                cmpq $8, %rcx
                jge .Lsec_sha256_ret
                movl (%rsp,%rcx,4), %eax
                bswapl %eax
                movl %eax, (%r12,%rcx,4)
                incq %rcx
                jmp .Lsec_sha256_out
            .Lsec_sha256_ret:
                addq $296, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_sha256_block(rdi=h[8] uint32, rsi=block64)
            .globl kof_sec_sha256_block
            .type kof_sec_sha256_block, @function
            kof_sec_sha256_block:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $272, %rsp          # w[64] = 256 + scratch 8 + h 8
                movq %rdi, 264(%rsp)     # h saved on the stack
                movq %rdi, %r12          # h (also used as scratch; reloaded at the end)
                movq %rsi, %r13          # block
                xorq %rcx, %rcx
            .Lsec_w_load:
                cmpq $16, %rcx
                jge .Lsec_w_load_done
                movl (%r13,%rcx,4), %eax
                bswapl %eax
                movl %eax, (%rsp,%rcx,4)
                incq %rcx
                jmp .Lsec_w_load
            .Lsec_w_load_done:
                movq $16, %rcx
            .Lsec_w_extend:
                cmpq $64, %rcx
                jge .Lsec_w_extend_done
                # s0 = ror7(w[i-15]) ^ ror18 ^ shr3
                movl -60(%rsp,%rcx,4), %eax
                movl %eax, %edx
                movl %eax, %ebx
                roll $25, %eax           # ror 7
                roll $14, %edx           # ror 18
                shrl $3, %ebx
                xorl %edx, %eax
                xorl %ebx, %eax
                # s1 = ror17(w[i-2]) ^ ror19 ^ shr10
                movl -8(%rsp,%rcx,4), %edx
                movl %edx, %ebx
                movl %edx, %r14d
                roll $15, %edx           # ror 17
                roll $13, %ebx           # ror 19
                shrl $10, %r14d
                xorl %ebx, %edx
                xorl %r14d, %edx
                movl -64(%rsp,%rcx,4), %r14d   # w[i-16]
                addl %eax, %r14d
                addl -28(%rsp,%rcx,4), %r14d   # + w[i-7]
                addl %edx, %r14d
                movl %r14d, (%rsp,%rcx,4)
                incq %rcx
                jmp .Lsec_w_extend
            .Lsec_w_extend_done:
                movl 0(%r12), %eax       # a
                movl 4(%r12), %ebx       # b
                movl 8(%r12), %ecx       # c
                movl 12(%r12), %edx      # d
                movl 16(%r12), %r8d      # e
                movl 20(%r12), %r9d      # f
                movl 24(%r12), %r10d     # g
                movl 28(%r12), %r11d     # h
                movq $0, %r14            # round index
            .Lsec_round:
                cmpq $64, %r14
                jge .Lsec_round_done
                # S1(e) -> 256(%rsp)
                movl %r8d, %r15d
                movl %r8d, %r12d
                roll $26, %r15d          # ror 6
                roll $21, %r12d          # ror 11
                xorl %r12d, %r15d
                movl %r8d, %r12d
                roll $7, %r12d           # ror 25
                xorl %r12d, %r15d
                movl %r15d, 256(%rsp)
                # ch = (e & f) ^ (~e & g) -> r15d
                movl %r8d, %r15d
                andl %r9d, %r15d
                movl %r8d, %r12d
                notl %r12d
                andl %r10d, %r12d
                xorl %r12d, %r15d
                # t1 = h + S1 + ch + K[i] + w[i] -> r13d
                leaq .Lsec_sha256_k(%rip), %r13
                movl (%r13,%r14,4), %r13d
                addl (%rsp,%r14,4), %r13d
                addl 256(%rsp), %r13d    # + S1
                addl %r15d, %r13d        # + ch
                addl %r11d, %r13d        # + h
                # S0(a) -> 256(%rsp)
                movl %eax, %r15d
                movl %eax, %r12d
                roll $30, %r15d          # ror 2
                roll $19, %r12d          # ror 13
                xorl %r12d, %r15d
                movl %eax, %r12d
                roll $10, %r12d          # ror 22
                xorl %r12d, %r15d
                movl %r15d, 256(%rsp)
                # maj = (a&b)^(a&c)^(b&c) -> r15d
                movl %eax, %r15d
                andl %ebx, %r15d
                movl %eax, %r12d
                andl %ecx, %r12d
                xorl %r12d, %r15d
                movl %ebx, %r12d
                andl %ecx, %r12d
                xorl %r12d, %r15d
                # t2 = S0 + maj -> 256(%rsp)
                addl 256(%rsp), %r15d
                movl %r15d, 256(%rsp)
                # shift: h=g, g=f, f=e, e=d+t1, d=c, c=b, b=a, a=t1+t2
                movl %r10d, %r11d
                movl %r9d, %r10d
                movl %r8d, %r9d
                movl %edx, %r8d
                addl %r13d, %r8d         # e = d + t1
                movl %ecx, %edx
                movl %ebx, %ecx
                movl %eax, %ebx
                movl %r13d, %eax
                addl 256(%rsp), %eax     # a = t1 + t2
                incq %r14
                jmp .Lsec_round
            .Lsec_round_done:
                movq 264(%rsp), %r12
                addl %eax, 0(%r12)
                addl %ebx, 4(%r12)
                addl %ecx, 8(%r12)
                addl %edx, 12(%r12)
                addl %r8d, 16(%r12)
                addl %r9d, 20(%r12)
                addl %r10d, 24(%r12)
                addl %r11d, 28(%r12)
                addq $272, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_sha256(rdi=string) → hex string
            .globl kof_sec_sha256
            .type kof_sec_sha256, @function
            kof_sec_sha256:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movl 16(%rbx), %r12d
                subq $32, %rsp
                movq %rsp, %rdi
                movq %rbx, %rsi
                addq $24, %rsi           # payload
                movslq %r12d, %rdx
                call kof_sec_sha256_internal
                # build hex string: 24 + 64 + 1
                movl $89, %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, 0(%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl $64, 16(%r13)
                movl $0, 20(%r13)
                xorq %rcx, %rcx
            .Lsec_sha256_hex:
                cmpq $32, %rcx
                jge .Lsec_sha256_hex_done
                movzbl (%rsp,%rcx), %eax
                movl %eax, %edx
                shrb $4, %al
                andb $0x0f, %dl
                leaq .Lsec_hex_chars(%rip), %r14
                movb (%r14,%rax), %al
                movb %al, 24(%r13,%rcx,2)
                movb (%r14,%rdx), %al
                movb %al, 25(%r13,%rcx,2)
                incq %rcx
                jmp .Lsec_sha256_hex
            .Lsec_sha256_hex_done:
                movb $0, 88(%r13)
                movq %r13, %rax
                addq $32, %rsp
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_hmac_sha256(rdi=key, rsi=data) → hex string
            # HMAC-SHA256: H((K^opad) || H((K^ipad) || data)) with K padded to 64
            .globl kof_sec_hmac_sha256
            .type kof_sec_hmac_sha256, @function
            kof_sec_hmac_sha256:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx          # key
                movq %rsi, %r12          # data
                subq $576, %rsp          # k64(64) + inner(64+datalen up to 448) + out(32) + scratch
                movq %rbx, %r13
                movl 16(%rbx), %r13d     # key len
                # build k64 in 0..63(%rsp): key bytes (or hash if keylen>64)
                cmpl $64, %r13d
                jg .Lsec_hmac_key_hash
                xorq %rcx, %rcx
            .Lsec_hmac_key_copy:
                cmpl %r13d, %ecx
                jge .Lsec_hmac_key_copy_done
                movb 24(%rbx,%rcx), %al
                movb %al, (%rsp,%rcx)
                incq %rcx
                jmp .Lsec_hmac_key_copy
            .Lsec_hmac_key_copy_done:
                movq %r13, %rcx
            .Lsec_hmac_key_zero:
                cmpq $64, %rcx
                jge .Lsec_hmac_key_done
                movb $0, (%rsp,%rcx)
                incq %rcx
                jmp .Lsec_hmac_key_zero
            .Lsec_hmac_key_done:
                jmp .Lsec_hmac_key_ready
            .Lsec_hmac_key_hash:
                leaq 512(%rsp), %rdi     # out
                movq %rbx, %rsi
                addq $24, %rsi
                movslq %r13d, %rdx
                call kof_sec_sha256_internal
                xorq %rcx, %rcx
            .Lsec_hmac_key_hash_copy:
                cmpq $32, %rcx
                jge .Lsec_hmac_key_hash_done
                movb 512(%rsp,%rcx), %al
                movb %al, (%rsp,%rcx)
                incq %rcx
                jmp .Lsec_hmac_key_hash_copy
            .Lsec_hmac_key_hash_done:
                movq $32, %rcx
            .Lsec_hmac_key_hash_zero:
                cmpq $64, %rcx
                jge .Lsec_hmac_key_ready
                movb $0, (%rsp,%rcx)
                incq %rcx
                jmp .Lsec_hmac_key_hash_zero
            .Lsec_hmac_key_ready:
                # inner input: ipad(64) at 64(%rsp) + data at 128(%rsp)
                movq %r12, %r14
                movl 16(%r12), %r14d     # data len
                movl $63, %ecx
            .Lsec_hmac_ipad:
                movb (%rsp,%rcx), %al
                xorb $0x36, %al
                movb %al, 64(%rsp,%rcx)
                decq %rcx
                jns .Lsec_hmac_ipad
            .Lsec_hmac_ipad_done:
                movq %r14, %rcx
                decq %rcx
            .Lsec_hmac_data_copy:
                testq %rcx, %rcx
                js .Lsec_hmac_data_copy_done
                movb 24(%r12,%rcx), %al
                movb %al, 128(%rsp,%rcx)
                decq %rcx
                jmp .Lsec_hmac_data_copy
            .Lsec_hmac_data_copy_done:
                # inner = sha256(64+data at 64(%rsp)) → 544(%rsp)
                leaq 544(%rsp), %rdi
                leaq 64(%rsp), %rsi
                movq %r14, %rdx
                addq $64, %rdx
                call kof_sec_sha256_internal
                # outer input: opad(64) + inner(32) → 64(%rsp)
                movl $63, %ecx
            .Lsec_hmac_opad:
                movb (%rsp,%rcx), %al
                xorb $0x5c, %al
                movb %al, 64(%rsp,%rcx)
                decq %rcx
                jns .Lsec_hmac_opad
            .Lsec_hmac_opad_done:
                movl $31, %ecx
            .Lsec_hmac_outer_copy:
                movb 544(%rsp,%rcx), %al
                movb %al, 128(%rsp,%rcx)
                decq %rcx
                jns .Lsec_hmac_outer_copy
            .Lsec_hmac_outer_done:
                # mac = sha256(64+32 at 64(%rsp)) → 512(%rsp)
                leaq 512(%rsp), %rdi
                leaq 64(%rsp), %rsi
                movq $96, %rdx
                call kof_sec_sha256_internal
                # build hex string (24 + 64 + 1)
                movl $89, %edi
                call kof_alloc
                movq %rax, %r15
                movl $1, 0(%r15)
                movl $0, 4(%r15)
                movq $0, 8(%r15)
                movl $64, 16(%r15)
                movl $0, 20(%r15)
                xorq %rcx, %rcx
            .Lsec_hmac_hex:
                cmpq $32, %rcx
                jge .Lsec_hmac_hex_done
                movzbl 512(%rsp,%rcx), %eax
                movl %eax, %edx
                shrb $4, %al
                andb $0x0f, %dl
                leaq .Lsec_hex_chars(%rip), %r14
                movb (%r14,%rax), %al
                movb %al, 24(%r15,%rcx,2)
                movb (%r14,%rdx), %al
                movb %al, 25(%r15,%rcx,2)
                incq %rcx
                jmp .Lsec_hmac_hex
            .Lsec_hmac_hex_done:
                movb $0, 88(%r15)
                movq %r15, %rax
                addq $576, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_constant_time_equals(rdi=a, rsi=b) → 1/0
            .globl kof_sec_constant_time_equals
            .type kof_sec_constant_time_equals, @function
            kof_sec_constant_time_equals:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%rbx), %r13d
                movl 16(%r12), %ecx
                cmpl %ecx, %r13d
                je .Lsec_cte_len_ok
                xorl %eax, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lsec_cte_len_ok:
                movl %r13d, %ecx
                xorl %eax, %eax
            .Lsec_cte_loop:
                testl %ecx, %ecx
                jle .Lsec_cte_done
                movzbl 23(%rbx,%rcx), %edx
                movzbl 23(%r12,%rcx), %r15d
                xorl %r15d, %edx
                orl %edx, %eax
                decq %rcx
                jmp .Lsec_cte_loop
            .Lsec_cte_done:
                testl %eax, %eax
                setz %al
                movzbl %al, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_random_hex(rdi=nbytes) → hex string via getrandom
            .globl kof_sec_random_hex
            .type kof_sec_random_hex, @function
            kof_sec_random_hex:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx          # nbytes
                # alloc n + 24 + 1
                leaq 25(%rbx), %rdi
                call kof_alloc
                movq %rax, %r12
                movl $1, 0(%r12)
                movl $0, 4(%r12)
                movq $0, 8(%r12)
                leal (%rbx,%rbx), %eax
                movl %eax, 16(%r12)
                movl $0, 20(%r12)
                # getrandom(buf, nbytes, 0)
                movq %r12, %rdi
                addq $24, %rdi
                movq %rbx, %rsi
                xorq %rdx, %rdx
                movq $318, %rax
                syscall
                testq %rax, %rax
                js .Lsec_random_fail
                # hex encode nbytes at 24(%r12) into 24..24+2n
                movq %rbx, %rcx
                decq %rcx
            .Lsec_random_hex_loop:
                testq %rcx, %rcx
                jl .Lsec_random_hex_done
                movzbl 24(%r12,%rcx), %eax
                movl %eax, %edx
                shrb $4, %al
                andb $0x0f, %dl
                leaq .Lsec_hex_chars(%rip), %r14
                movb (%r14,%rax), %al
                movb %al, 24(%r12,%rcx,2)
                movb (%r14,%rdx), %al
                movb %al, 25(%r12,%rcx,2)
                decq %rcx
                jmp .Lsec_random_hex_loop
            .Lsec_random_hex_done:
                movb $0, 24(%r12,%rbx,2)
                movq %r12, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lsec_random_fail:
                movq $0, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_random_int(rdi=bound) → secure random int in [0, bound)
            .globl kof_sec_random_int
            .type kof_sec_random_int, @function
            kof_sec_random_int:
                pushq %rbx
                movq %rdi, %rbx
                testq %rbx, %rbx
                jg .Lsec_random_int_ok
                xorl %eax, %eax
                popq %rbx
                ret
            .Lsec_random_int_ok:
                # rejection sampling: 32-bit value < bound * (2^32 / bound)
                movl %ebx, %r10d
                xorl %r9d, %r9d
                movl $1, %r11d
                # range = (2^32 / bound) * bound
                movl $0xffffffff, %eax
                xorl %edx, %edx
                divl %ebx              # eax = 2^32/bound
                movl %eax, %r9d
                imull %ebx, %r9d       # range
                subq $4, %rsp
            .Lsec_random_int_retry:
                movq %rsp, %rdi
                movq $4, %rsi
                xorq %rdx, %rdx
                movq $318, %rax
                syscall
                testq %rax, %rax
                js .Lsec_random_int_fail
                movl (%rsp), %eax
                cmpl %r9d, %eax
                jae .Lsec_random_int_retry
                xorl %edx, %edx
                divl %ebx
                movl %edx, %eax
                addq $4, %rsp
                popq %rbx
                ret
            .Lsec_random_int_fail:
                addq $4, %rsp
                xorl %eax, %eax
                popq %rbx
                ret

            # kof_sec_redact(rdi=value) → masked string
            .globl kof_sec_redact
            .type kof_sec_redact, @function
            kof_sec_redact:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movl 16(%rbx), %r12d
                cmpl $8, %r12d
                jg .Lsec_redact_long
                # return "********"
                movl $32, %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, 0(%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl $8, 16(%r13)
                movl $0, 20(%r13)
                movq $0x2a2a2a2a2a2a2a2a, %rax
                movq %rax, 24(%r13)
                movb $0, 32(%r13)
                movq %r13, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lsec_redact_long:
                # first4 + "********" + last4: total 16 chars
                movl $40, %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, 0(%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl $16, 16(%r13)
                movl $0, 20(%r13)
                movq $0x2a2a2a2a2a2a2a2a, %rax
                movq %rax, 28(%r13)
                movb 24(%rbx), %al
                movb %al, 24(%r13)
                movb 25(%rbx), %al
                movb %al, 25(%r13)
                movb 26(%rbx), %al
                movb %al, 26(%r13)
                movb 27(%rbx), %al
                movb %al, 27(%r13)
                movl %r12d, %r14d
                movl %r12d, %eax
                subl $4, %eax
                movl %eax, %r14d
                movb 24(%rbx,%r14), %al
                movb %al, 36(%r13)
                movl %r12d, %r14d
                subl $3, %r14d
                movb 24(%rbx,%r14), %al
                movb %al, 37(%r13)
                movl %r12d, %r14d
                subl $2, %r14d
                movb 24(%rbx,%r14), %al
                movb %al, 38(%r13)
                movl %r12d, %r14d
                subl $1, %r14d
                movb 24(%rbx,%r14), %al
                movb %al, 39(%r13)
                movb $0, 40(%r13)
                movq %r13, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_secret_get(rdi=name) → value or 0 (null)
            # reads /proc/self/environ via syscalls (no libc)
            .globl kof_sec_secret_get
            .type kof_sec_secret_get, @function
            kof_sec_secret_get:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx          # name
                subq $65536, %rsp
                # open("/proc/self/environ", O_RDONLY)
                leaq .Lsec_environ_path(%rip), %rdi
                xorq %rsi, %rsi
                xorq %rdx, %rdx
                movq $2, %rax
                syscall
                testq %rax, %rax
                js .Lsec_secret_open_fail
                movq %rax, %r12          # fd
                movq %r12, %rdi
                movq %rsp, %rsi
                movq $65536, %rdx
                xorq %rax, %rax
                syscall
                movq %rax, %r13          # bytes read
                # close
                movq %r12, %rdi
                movq $3, %rax
                syscall
                testq %r13, %r13
                jle .Lsec_secret_open_fail
                # scan entries: NAME=VALUE NUL-separated
                xorq %r14, %r14          # entry start
            .Lsec_secret_scan:
                cmpq %r13, %r14
                jge .Lsec_secret_not_found
                # find '=' in this entry
                movq %r14, %rcx
            .Lsec_secret_find_eq:
                cmpq %r13, %rcx
                jge .Lsec_secret_next
                movb (%rsp,%rcx), %al
                cmpb $0x3d, %al          # '='
                je .Lsec_secret_eq_found
                cmpb $0, %al
                je .Lsec_secret_next
                incq %rcx
                jmp .Lsec_secret_find_eq
            .Lsec_secret_eq_found:
                # name length = rcx - r14; compare with name
                movq %rcx, %r15
                subq %r14, %r15
                movl 16(%rbx), %r8d
                movslq %r8d, %r8
                cmpq %r15, %r8
                jne .Lsec_secret_next
                # compare bytes
                xorq %rdx, %rdx
            .Lsec_secret_cmp:
                cmpq %r15, %rdx
                jge .Lsec_secret_match
                leaq (%rsp,%r14), %rsi
                movb (%rsi,%rdx), %al
                movb 24(%rbx,%rdx), %cl
                cmpb %cl, %al
                jne .Lsec_secret_next
                incq %rdx
                jmp .Lsec_secret_cmp
            .Lsec_secret_match:
                # value = bytes after '=' until NUL
                movq %rcx, %r15          # '=' position
                incq %r15
                movq %r15, %r14
            .Lsec_secret_val_end:
                cmpq %r13, %r14
                jge .Lsec_secret_val_done
                cmpb $0, (%rsp,%r14)
                je .Lsec_secret_val_done
                incq %r14
                jmp .Lsec_secret_val_end
            .Lsec_secret_val_done:
                movq %r14, %r13
                subq %r15, %r13          # value len
                leaq 25(%r13), %rdi
                call kof_alloc
                movq %rax, %r12
                movl $1, 0(%r12)
                movl $0, 4(%r12)
                movq $0, 8(%r12)
                movl %r13d, 16(%r12)
                movl $0, 20(%r12)
                xorq %rcx, %rcx
            .Lsec_secret_val_copy:
                cmpq %r13, %rcx
                jge .Lsec_secret_val_copy_done
                leaq (%rsp,%r15), %r14
                movb (%r14,%rcx), %al
                movb %al, 24(%r12,%rcx)
                incq %rcx
                jmp .Lsec_secret_val_copy
            .Lsec_secret_val_copy_done:
                movb $0, 24(%r12,%r13)
                movq %r12, %rax
                addq $65536, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lsec_secret_next:
                # advance to next entry (past NUL)
                movq %r14, %rcx
            .Lsec_secret_skip:
                cmpq %r13, %rcx
                jge .Lsec_secret_not_found
                cmpb $0, (%rsp,%rcx)
                je .Lsec_secret_skip_done
                incq %rcx
                jmp .Lsec_secret_skip
            .Lsec_secret_skip_done:
                incq %rcx
                movq %rcx, %r14
                jmp .Lsec_secret_scan
            .Lsec_secret_not_found:
                addq $65536, %rsp
                xorl %eax, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lsec_secret_open_fail:
                addq $65536, %rsp
                xorl %eax, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_secret_get_default(rdi=name, rsi=fallback) → value or fallback
            .globl kof_sec_secret_get_default
            .type kof_sec_secret_get_default, @function
            kof_sec_secret_get_default:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx          # name
                movq %rsi, %r12          # fallback (callee-saved; rsi is clobbered)
                call kof_sec_secret_get
                testq %rax, %rax
                jnz .Lsec_secret_default_done
                movq %r12, %rax
            .Lsec_secret_default_done:
                popq %r12
                popq %rbx
                ret

            .Lsec_environ_path:
                .asciz "/proc/self/environ"
            """);
    }
}
