package dev.kof.compiler;

import java.util.List;

/**
 * NativeRuntime — generates x86-64 assembly for Kof runtime functions.
 *
 * These functions are the native implementation of the Kof Runtime ABI.
 * They are emitted into the .text section of every native binary.
 */
final class NativeRuntime {

    private NativeRuntime() {}

    /**
     * Returns assembly for all required runtime functions.
     */
    static String generateRuntimeAssembly() {
        StringBuilder sb = new StringBuilder();
        emitPrint(sb);
        emitPrintln(sb);
        emitPrintInt(sb);
        emitIntToString(sb);
        emitBoolToString(sb);
        emitAlloc(sb);
        emitFree(sb);
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
        emitArrayAlloc(sb);
        emitArrayLength(sb);
        emitArrayGet(sb);
        emitArraySet(sb);
        emitMemstats(sb);
        emitNetSocket(sb);
        emitNetBind(sb);
        emitNetListen(sb);
        emitNetAccept(sb);
        emitNetRead(sb);
        emitNetWrite(sb);
        emitNetClose(sb);
        emitInstanceof(sb);
        return sb.toString();
    }

    /**
     * Generates method table entries for a class.
     * Each entry is: .quad method_address
     * methods is a list of mangled method names in order.
     */
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

    /**
     * kof_init_object(obj_ptr, type_id, vtable_ptr)
     * Initializes the object header.
     * %rdi = obj_ptr, %esi = type_id, %rdx = vtable_ptr
     */
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

    /**
     * kof_print(ptr) — prints a null-terminated string to stdout.
     */
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

    /**
     * kof_println(ptr) — prints a null-terminated string followed by newline.
     */
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

    /**
     * kof_int_to_string(value) — converts an int to a KofString.
     * %rdi = int value
     * returns %rax = KofString*
     */
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

    /**
     * kof_bool_to_string(value) — converts a bool to a KofString ("true"/"false").
     * %rdi = int value (0/1)
     * returns %rax = KofString*
     */
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

    /**
     * kof_print_int(value) — prints an integer as decimal to stdout.
     */
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

    /**
     * kof_alloc(size) — allocates size bytes on the heap.
     * Returns pointer in %rax. Uses mmap for simplicity.
     */
    private static void emitAlloc(StringBuilder sb) {
        sb.append("""
            .globl kof_alloc
            .type kof_alloc, @function
            kof_alloc:
                pushq %rbx
                movq %rdi, %rbx
                addq $15, %rbx
                andq $~15, %rbx
                movq $0, %rdi
                movq %rbx, %rsi
                movq $0x22, %rdx
                movq $0x22, %r10
                movq $-1, %r8
                movq $0, %r9
                movq $9, %rax
                syscall
                popq %rbx
                ret
            """);
    }

    /**
     * kof_free(ptr) — no-op for now (no GC).
     * Memory is reclaimed by the OS when the process exits.
     */
    private static void emitFree(StringBuilder sb) {
        sb.append("""
            .globl kof_free
            .type kof_free, @function
            kof_free:
                ret
            """);
    }

    /**
     * kof_memstats() — prints memory allocation statistics.
     * Useful for debugging memory usage.
     */
    static void emitMemstats(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lkof_alloc_count: .quad 0
            .section .text
            .Lkof_memstats_nl: .asciz "\\n"
            .globl kof_memstats
            .type kof_memstats, @function
            kof_memstats:
                pushq %rbx
                movq .Lkof_alloc_count(%rip), %rdi
                call kof_print_int
                leaq .Lkof_memstats_nl(%rip), %rdi
                call kof_print
                popq %rbx
                ret
            """);
    }

    /**
     * kof_panic(message) — prints error message and exits.
     */
    private static void emitPanic(StringBuilder sb) {
        sb.append("""
            .globl kof_panic
            .type kof_panic, @function
            kof_panic:
                call kof_println
                movq $60, %rax
                movq $1, %rdi
                syscall
            """);
    }

    /**
     * kof_null_error() — prints null reference error and exits.
     */
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

    /**
     * kof_bounds_error(index, length) — prints array bounds error and exits.
     * %rdi = index, %rsi = length
     */
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

    // ── KofString Runtime Functions ────────────────────────────────

    /**
     * KofString layout (x86-64):
     *   offset 0:  type_id   (4 bytes, always 1 for String)
     *   offset 4:  flags     (4 bytes)
     *   offset 8:  method_table_ptr (8 bytes, NULL for strings)
     *   offset 16: length    (4 bytes, byte count)
     *   offset 20: padding   (4 bytes, alignment)
     *   offset 24: UTF-8 data + null terminator
     */
    static final int KOF_STRING_TYPE_ID = 1;
    static final int KOF_STRING_HEADER_SIZE = 24;

    /**
     * kof_string_from_literal(data_ptr, byte_length) → str_ptr
     * Creates a KofString from a static literal.
     * %rdi = pointer to UTF-8 data, %esi = byte length
     * Returns pointer to new KofString in %rax.
     */
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

    /**
     * kof_memcpy(dest, src, n) — copies n bytes from src to dest.
     * %rdi = dest, %rsi = src, %edx = n
     */
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

    /**
     * kof_string_length(str_ptr) → int
     * Returns the byte length of a KofString.
     * %rdi = pointer to KofString
     * Returns length in %eax.
     */
    private static void emitStringLength(StringBuilder sb) {
        sb.append("""
            .globl kof_string_length
            .type kof_string_length, @function
            kof_string_length:
                movl 16(%rdi), %eax
                ret
            """);
    }

    /**
     * kof_string_concat(str1, str2) → str3
     * Concatenates two KofStrings into a new KofString.
     * %rdi = str1, %rsi = str2
     * Returns pointer to new KofString in %rax.
     */
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

    /**
     * kof_string_equals(str1, str2) → bool
     * Compares two KofStrings for byte-level equality.
     * %rdi = str1, %rsi = str2
     * Returns 1 if equal, 0 otherwise in %eax.
     */
    private static void emitStringEquals(StringBuilder sb) {
        sb.append("""
            .globl kof_string_equals
            .type kof_string_equals, @function
            kof_string_equals:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%rbx), %eax
                cmpl 16(%r12), %eax
                jne .Lkof_str_eq_no
                xorq %rcx, %rcx
            .Lkof_str_eq_loop:
                cmpl %ecx, %eax
                jg .Lkof_str_eq_cmp
                movl $1, %eax
                popq %r12
                popq %rbx
                ret
            .Lkof_str_eq_cmp:
                movzbl 24(%rbx,%rcx), %edx
                cmpl %edx, 24(%r12,%rcx)
                jne .Lkof_str_eq_no
                incq %rcx
                jmp .Lkof_str_eq_loop
            .Lkof_str_eq_no:
                movl $0, %eax
                popq %r12
                popq %rbx
                ret
            """);
    }

    /**
     * kof_print_string(str_ptr) — prints a KofString to stdout.
     * Uses stored length (no strlen scan).
     * %rdi = pointer to KofString
     */
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

    /**
     * kof_println_string(str_ptr) — prints a KofString + newline.
     * %rdi = pointer to KofString
     */
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

    // ── Additional String Runtime Functions ──────────────────────

    /**
     * kof_string_char_at(str, index) → byte value
     * Returns the byte at the given index.
     */
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

    /**
     * kof_string_substring(str, start, end) → new_str_ptr
     */
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

    /**
     * kof_string_contains(str, sub) → bool
     */
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

    /**
     * kof_string_starts_with(str, prefix) → bool
     */
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

    /**
     * kof_string_ends_with(str, suffix) → bool
     */
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

    // ── KofArray Runtime Functions ─────────────────────────────────

    /**
     * KofArray layout (x86-64):
     *   offset 0:  type_id   (4 bytes, always 2 for Array)
     *   offset 4:  flags     (4 bytes)
     *   offset 8:  method_table_ptr (8 bytes, NULL for arrays)
     *   offset 16: length    (4 bytes, number of elements)
     *   offset 20: elem_size (4 bytes, size of each element)
     *   offset 24: elements  (length * elem_size bytes)
     */
    static final int KOF_ARRAY_TYPE_ID = 2;
    static final int KOF_ARRAY_HEADER_SIZE = 24;

    /**
     * kof_array_alloc(length, element_size) → array_ptr
     * Allocates a new array on the heap.
     * %rdi = length (number of elements), %rsi = element_size (bytes per element)
     * Returns pointer to new KofArray in %rax.
     */
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

    /**
     * kof_array_length(array_ptr) → int
     * Returns the length of a KofArray.
     * %rdi = pointer to KofArray
     * Returns length in %eax.
     */
    private static void emitArrayLength(StringBuilder sb) {
        sb.append("""
            .globl kof_array_length
            .type kof_array_length, @function
            kof_array_length:
                movl 16(%rdi), %eax
                ret
            """);
    }

    /**
     * kof_array_get(array_ptr, index) → element_value
     * Gets an element from a KofArray.
     * %rdi = pointer to KofArray, %rsi = index
     * Returns element value in %rax (for primitives) or pointer (for references).
     * Performs bounds checking.
     */
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
                movq (%rax), %rax
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

    /**
     * kof_array_set(array_ptr, index, value)
     * Sets an element in a KofArray.
     * %rdi = pointer to KofArray, %rsi = index, %rdx = value
     * Performs bounds checking.
     */
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
                movq %r13, (%rax)
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

    // ── Network Runtime Functions ──────────────────────────────────

    /**
     * kof_net_socket(domain, type, protocol) → fd
     * Creates a socket. %rdi=domain, %rsi=type, %rdx=protocol
     * Returns file descriptor in %rax, or -1 on error.
     */
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

    /**
     * kof_net_bind(fd, port, addr) → status
     * Binds socket. %rdi=fd, %esi=port, %rdx=addr_ptr
     * Returns 0 on success, -1 on error.
     */
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

    /**
     * kof_net_listen(fd, backlog) → status
     * Listens on socket. %rdi=fd, %esi=backlog
     */
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

    /**
     * kof_net_accept(fd) → client_fd
     * Accepts a connection. %rdi=fd
     * Returns client fd in %rax.
     */
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

    /**
     * kof_net_read(fd, buf, len) → bytes_read
     * Reads from socket. %rdi=fd, %rsi=buf, %rdx=len
     */
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

    /**
     * kof_net_write(fd, buf, len) → bytes_written
     * Writes to socket. %rdi=fd, %rsi=buf, %rdx=len
     */
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

    /**
     * kof_net_close(fd) → status
     * Closes socket. %rdi=fd
     */
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

    /**
     * kof_instanceof(obj_ptr, target_type_id) → bool
     * Walks the class hierarchy using kof_super_table.
     * %rdi = obj_ptr, %esi = target_type_id
     * Returns 1 if object's type is target or a subtype, 0 otherwise.
     */
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
}
