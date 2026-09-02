# Known Bugs — handoff para o próximo agente

> **Data:** 02/09/2026 · **Status:** documentados e verificados no compilador
> (0.2.6-beta). Este arquivo existe para que um agente (ou humano) pegue os
> bugs sem precisar redescobri-los. **Não são características** — são bugs
> reais com reprodução mínima.

---

## 1. `throw <valor não-String>` gera bytecode inválido no JVM

### Sintoma

`throw 42` ou `catch (Int e)` compila, mas o `.class` resultante falha ao
carregar com:

```
Erro: os componentes de runtime do JavaFX não foram encontrados...
ClassFormatError: Illegal class name "" in class file Default/Main
```

(O "JavaFX" é o launcher disfarçando um `ClassFormatError`/`VerifyError`.)
Exceções em Kof são **Strings** (`throw "msg"` / `catch (String e)`) — tudo
mais quebra silenciosamente no JVM.

### Reprodução mínima

```kof
main() {
    try {
        throw 42
    } catch (String e) {
        print("texto")
    }
    println("done")
}
```

Compile `kof build --target jvm`, rode: `ClassFormatError` no launch.

### Causa provável

O backend JVM emite o `throw` de um valor primitivo sem o wrap correto em
`RuntimeException` (o `catch (String e)` espera um `RuntimeException` com
`getMessage()`), gerando constant pool/verificação inválidos.

### O que deveria acontecer

Kof declara que exceções são Strings. O ideal é o **compilador rejeitar
`throw <não-String>` e `catch <não-String>` em compile-time** (SEM0xx), em vez
de gerar bytecode inválido. Alternativa: dar suporte real a `throw 42`/
`catch (Int e)` (wrap+unwrap tipado) — mas isso contradiz a filosofia
"exceptions são Strings" (ver `training/language/exceptions.md`).

### Arquivos prováveis

- `CompilerDriver.java` — lowering de `ThrowStmt` e de `catch` (JVM).
- `JvmBackend.java` — emissão de `athrow`/handler.

### Onde está documentado

- `training/language/exceptions.md` ("Exceptions são Strings ... `throw 42`
  geram bytecode inválido no JVM — 02/09").
- `learn/14-exceptions.md` (nota "verificado 02/09").

---

## 2. Captura mutável no Native: ler variável boxeada DENTRO da lambda após
mutação EXTERNA produz lixo

### Sintoma

No target **Native**, quando uma lambda **lê** uma variável capturada que foi
**mutada fora** da lambda, o valor lido é lixo (ponteiro/offset), não o valor.

### Reprodução mínima

```kof
main() {
    var offset = 10
    var f2 = (x: Int) -> x + offset
    println(f2(5))        // JVM: 15  /  Native: lixo (ex.: 4198674)
    offset = 20
    println(f2(5))        // JVM: 25  /  Native: lixo (ex.: 21)
}
```

### Causa provável

O `CompilerDriver` boxa a variável capturada (`Box0` com campo `value`) e, ao
ler dentro da lambda, emite `KofLoadField(Box0, "value")`. No Native, o
`resolveFieldOffset` para o box dentro do contexto da lambda resolve o offset
contra o layout ERRADO (fallback `HEADER_SIZE`/16), lendo o ponteiro do box em
vez de `.value`. A direção inversa (lambda **escreve** na externa, leitura fora
da lambda) funciona no Native — ver `LambdaE2ETest.mutableCaptureLambdaWritesNative`.

### O que deveria acontecer

`f2(5)` deve retornar `15` depois `25` no Native, igual ao JVM
(`LambdaE2ETest.mutableCaptureOuterMutationJvm`).

### Arquivos prováveis

- `NativeBackend.java` — `resolveFieldOffset` / `getLayoutForType` no contexto
  da lambda (a lambda é classe sintética; o layout do `Box0` precisa ser
  resolvido, não o da lambda).
- `CompilerDriver.java` — `emitLambda` captura dos boxes (verificado JVM).

### Onde está documentado

- `training/idioms/functions.md` e `learn/16-lambdas.md` (nota "Native: a
  direção 'lambda escreve' funciona; 'lê após mutação externa' é bug").

---

## Como pegar estes bugs

1. Reproduza com os snippets acima (`kof run --target=jvm|native`).
2. Fix no código (não no corpus).
3. Adicione teste E2E que falha antes e passa depois.
4. Atualize este arquivo (mova para "resolvidos" com o commit).
5. Remova as notas de "bug/verificado 02/09" do corpus quando o fix entrar.