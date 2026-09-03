# Anti-pattern: asm-comment-escape (COMENTARIOS em asm-textblocks)

## O que é

Os runtimes de geração asm (`NativeRuntime*.java`, `NativeWebRuntime.java`
etc.) usam Java text blocks (`"""..."""`) com strings `asm` inline. Nessas
cadeias quaisquer caracteres (incluindo `\r`, tabs, aspas, non-ASCII) são
preservados ao gerar o arquivo `.s`. O assembler GNU (`as`) é estrito — um
`\r` num comentário *não-em*`.asciz` quebra a linha e se torna "junk at end
of line".

## Sintomas

- **`Main.s:NNNNF: Error: junk at end of line, first unrecognized character
  is `:'`/`(`**／ ou `invalid character (0xa) in mnemonic`
- **Componente quebra logo após edição de comentário**

## Regras para comentários em asm dentro de text blocks Java

1. **Apenas ASCII básico** (sem ç/á/é — veja bytes 0x80+ no `.s`)
2. **Nunca `\r\n` ou `\n` literal** em comment. Mesmo que o `"""` Java
   interprete, o `\r` fica no arquivo `.s` e quebra
3. Se precisar documentar caractere especial, escreva `CRLF` ou `LF` em
   ASCII puro, não o caractere literal

## Exemplo ruim

```java
        sb.append("""
            movq %rax, %rbx
            # achou \r\n\r\n: body em rsi+4     <-- quebra o assembler
            call handle_body
        """);
```

## Exemplo bom

```java
        sb.append("""
            movq %rax, %rbx
            # achou CRLF CRLF; body em rsi+4
            call handle_body
        """);
```

## Ferramenta de detecção rápida

Antes de commit, compile o asm e busque por linhas com CR sem escape:

```bash
grep -a $'\r' out/Default/Main.s | grep -v 'asciz\|\.quad\|\.long\|\.byte'
```

Se aparecer, ro is an asm-comment escape bug.

## Referência

- Discovered em 03/09 durante WEB002 T2/T3/Т4 (KofWebNativeE2ETest).
- Relacionado: `fake-idioms.md` (o que NÃO existe em Kof); o comentário em
  asm essencialmente **prejudica a build**, não a semântica Kof.
