package dev.kof.compiler;

/**
 * kof.web server para o target Nativo (WEB002).
 *
 * Estratégia de fechamento (incremental):
 *   - T1 (este arquivo): accept loop bloqueante que responde 200 "hello" a
 *     qualquer requisição — prova o caminho: listen→accept→read→write→close.
 *   - T2: parse METHOD/PATH + match de rotas literais (kof_web_route).
 *   - T3: dispatch real (chamando o handler lambda registrado).
 *   - T4: contexto de request (body, param, header, query).
 *
 * Módulo separado por regra ≤500 linhas/classe.
 */
final class NativeWebRuntime {

    private NativeWebRuntime() {}

    static void emitWebFunctions(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lweb_nroutes:   .quad 0
            .Lweb_routes:    .space 8192                     # 256 entradas de 32B
            .Lweb_okres:     .asciz "HTTP/1.1 200 OK\\r\\nContent-Length: 5\\r\\nConnection: close\\r\\n\\r\\nhello"
            .Lweb_reqlen:    .quad 0
            .section .bss
            .Lweb_reqbuf:    .space 16384

            .section .text

            # ---------- util ----------
            # strlen: rdi=cstr → rax=len
            kof_web_cstrlen:
                xorq %rax, %rax
            .Lwcs:
                cmpb $0, (%rdi,%rax)
                je .Lwcsd
                incq %rax
                jmp .Lwcs
            .Lwcsd:
                ret

            # ---------- kof_web_app_new() → sentinel ----------
            .globl kof_web_app_new
            .type kof_web_app_new, @function
            kof_web_app_new:
                movq $1, %rax
                ret

            # kof_web_route(rsi=method, rdi=path, rdx=handler)
            # (assinatura no codegen: receiver string + method string + handler)
            .globl kof_web_route
            .type kof_web_route, @function
            kof_web_route:
                movq .Lweb_nroutes(%rip), %r8
                imulq $32, %r8, %r9
                leaq .Lweb_routes(%rip), %r10
                addq %r9, %r10
                movq %rsi, 0(%r10)
                movq %rdi, 8(%r10)
                movq %rdx, 16(%r10)
                movq $0, 24(%r10)
                incq %r8
                movq %r8, .Lweb_nroutes(%rip)
                ret

            # kof_web_listen(app_ignored: rdi, port: rsi)
            .globl kof_web_listen
            .type kof_web_listen, @function
            kof_web_listen:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rsi, %r13                  # port
                # socket(2, 1, 0)
                movl $2, %edi
                movl $1, %esi
                xorl %edx, %edx
                movl $41, %eax                   # SYS_socket
                syscall
                testq %rax, %rax
                js .Lwlf
                movq %rax, %rbx                  # server fd
                # bind
                subq $16, %rsp
                movw $2, (%rsp)                  # AF_INET
                movq %r13, %rax
                movzx %ax, %eax
                xchgb %al, %ah                   # htons
                movw %ax, 2(%rsp)
                movl $0, 4(%rsp)                 # 0.0.0.0
                movq $0, 8(%rsp)                 # pad
                movq %rbx, %rdi
                movq %rsp, %rsi
                movl $16, %edx
                movl $49, %eax                   # SYS_bind
                syscall
                testq %rax, %rax
                js .Lwlbf
                addq $16, %rsp
                # listen( serverfd, 64 )
                movq %rbx, %rdi
                movl $64, %esi
                movl $50, %eax                   # SYS_listen
                syscall
            .Lwla:                                # accept loop
                movq %rbx, %rdi
                xorq %rsi, %rsi
                xorq %rdx, %rdx
                movl $43, %eax                   # SYS_accept
                syscall
                testq %rax, %rax
                js .Lwla
                movq %rax, %r12                  # client fd
                movq %r12, %rdi
                call kof_web_handle_client
                movq %r12, %rdi
                movl $3, %eax                    # SYS_close
                syscall
                jmp .Lwla
            .Lwlbf:
                addq $16, %rsp
            .Lwlf:
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_web_handle_client: rdi=client_fd → lê request e responde
            .globl kof_web_handle_client
            .type kof_web_handle_client, @function
            kof_web_handle_client:
                pushq %rbx
                movq %rdi, %rbx
                # read(fd, reqbuf, 16384)
                movq %rdi, %rdi
                leaq .Lweb_reqbuf(%rip), %rsi
                movl $16384, %edx
                xorl %eax, %eax                  # SYS_read
                syscall
                testq %rax, %rax
                jle .Lwhc_done
                # write(client_fd, .Lweb_okres, len(.Lweb_okres))
                leaq .Lweb_okres(%rip), %rdi
                call kof_web_cstrlen
                movq %rax, %rdx
                movq %rbx, %rdi
                leaq .Lweb_okres(%rip), %rsi
                movl $1, %eax                    # SYS_write
                syscall
            .Lwhc_done:
                popq %rbx
                ret
            """);
    }
}
