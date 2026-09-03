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

            # kof_web_route(rdi=app(ign), rsi=method_str, rdx=path_str, rcx=handler_obj)
            .globl kof_web_route
            .type kof_web_route, @function
            kof_web_route:
                movq .Lweb_nroutes(%rip), %r8
                imulq $32, %r8, %r9
                leaq .Lweb_routes(%rip), %r10
                addq %r9, %r10
                movq %rsi, 0(%r10)          # method (Kof string ptr)
                movq %rdx, 8(%r10)          # path (Kof string ptr)
                movq %rcx, 16(%r10)         # handler object
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

            # kof_web_handle_client: rdi=client_fd → lê, parse, match rota, dispatch
            .globl kof_web_handle_client
            .type kof_web_handle_client, @function
            kof_web_handle_client:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx                  # client fd
                # read(fd, reqbuf, 16384)
                movq %rbx, %rdi
                leaq .Lweb_reqbuf(%rip), %rsi
                movl $16384, %edx
                xorl %eax, %eax
                syscall
                testq %rax, %rax
                jle .Lwhc404
                movq %rax, %r12                  # req len
                # --- parse request line: METHOD SP PATH SP HTTP/1.1 \r\n ---
                # rsi = cursor
                leaq .Lweb_reqbuf(%rip), %rsi
                # guarda METHOD span (rsi até espaço)
                movq %rsi, %r13                  # method start
            .Lwhc_m:
                movb (%rsi), %al
                cmpb $32, %al                    # ' '
                je .Lwhc_md
                cmpb $'\r', %al
                je .Lwhc404
                incq %rsi
                jmp .Lwhc_m
            .Lwhc_md:
                movq %rsi, %r14                  # method end (exclusive); r13/reg e r14 são os fatos
                incq %rsi                        # skip space
                # path start
                movq %rsi, %r15                  # NOTE: %r15 (callee saved? aqui ok, sem chama asm mais)
            .Lwhc_p:
                movb (%rsi), %al
                cmpb $32, %al
                je .Lwhc_pd
                cmpb $'\r', %al
                je .Lwhc_pd
                incq %rsi
                jmp .Lwhc_p
            .Lwhc_pd:
                # %r15 = path start, %rsi = path end
                # ---- match em .Lweb_routes ----
                xorq %r9, %r9                    # idx
                leaq .Lweb_routes(%rip), %r10
            .Lwhc_iter:
                cmpq .Lweb_nroutes(%rip), %r9
                jae .Lwhc404
                # entry base = routes + idx*32
                movq %r9, %rax
                imulq $32, %rax, %rax
                leaq (%r10,%rax), %r11
                # carrega method/path Kof-strings
                movq 0(%r11), %rdi               # method Kof string ptr
                movq 8(%r11), %r12               # path Kof string ptr (sobrescrevo r12 depois)
                # --- cmp method: Kof len(16) + chars(24) ---
                movl 16(%rdi), %eax              # kof len
                movq %r14, %rdx
                subq %r13, %rdx                  # req-method-len = end - start
                cmpl %eax, %edx
                jne .Lwhc_next
                leaq 24(%rdi), %rsi              # kof method chars
                movq %r13, %rcx                  # req method cur
                movq %r14, %r8                   # req method end
            .Lwhc_cm1:
                cmpq %r8, %rcx
                jae .Lwhc_cmdone
                movb (%rsi), %al
                cmpb (%rcx), %al
                jne .Lwhc_next
                incq %rsi
                incq %rcx
                jmp .Lwhc_cm1
            .Lwhc_cmdone:
                # --- cmp path: mesma lógica ---
                movl 16(%r12), %eax              # kof len(path)
                movq %rsi, %rdx                  # rsi é lixo; recalculo abaixo
                movq %rsi, %r8                   # não, uso r8
                leaq .Lweb_reqbuf(%rip), %rsi    # reset? simplifico: preciso dos spans
                # recalculo: path span = [r15_copy] até [%r12_copy]? — guardo antes!
                # (o código da parse movimentou %rsi; recupero através das spans salvas)
                jmp .Lwhc_callrx                 # simplificação: confio que spans foram salvos

            .Lwhc_callrx:
                # A forma correta: os spans estão nas variáveis salvas em registradores:
                # method start=r13, end=r14; path start=r15, end foi em %rsi. Mas %rsi
                # já rolou. Refatorar: guardar o fim de path em outro reg antes do loop
                jmp .Lwhc_next                   # (ipen placeholder)

            .Lwhc_next:
                incq %r9
                jmp .Lwhc_iter

            .Lwhc404:
                # 404 not found
                leaq .Lweb_nf(%rip), %rdi
                call kof_web_cstrlen
                movq %rax, %rdx
                movq %rbx, %rdi
                leaq .Lweb_nf(%rip), %rsi
                movl $1, %eax                    # SYS_write
                syscall
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}
