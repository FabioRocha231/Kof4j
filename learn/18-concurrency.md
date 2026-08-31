# 18 — Concorrência

> **Status: implementado (JVM, virtual threads) — 0.2.6-beta**
>
> Kof não expõe `Thread`, `Runnable` nem `CompletableFuture`: a intenção é
> `spawn` (rode em paralelo) e `await` (espere o resultado). No Native e
> no JS os mesmos programas reportam gap claro em compile-time — nunca
> comportamento silenciosamente diferente. Chain: `intention->Kof->frontend->IR->backend->runtime`.

## spawn — dispare e esqueça

```kf
fn baixar(url: String) {
    // trabalho lento...
}

main() {
    spawn baixar("https://example.com")   // roda em virtual thread
    println("seguindo o fluxo principal")
}
```

O corpo pode ser qualquer expressão — o compilador embrulha numa tarefa
sintética:

```kf
spawn {
    for (var i in 1..100) {
        processar(i)
    }
}
```

## spawn + await — resultado tipado

`spawn <expressão>` devolve um handle tipado `Handle<T>`; `await` bloqueia
a virtual thread chamadora até o valor chegar:

```kf
Int somar(a: Int, b: Int) {
    return a + b
}

main() {
    val r = spawn somar(2, 3)     // Handle<Int>
    // ...trabalho enquanto a soma acontece...
    val total = await r           // Int — unboxing automático
    println(total)                // 5
}
```

Primitivos (`Int`, `Bool`) e referências funcionam igualmente:

```kf
String buscar() { return "dados" }

main() {
    val r = spawn buscar()
    println(await r)              // "dados"
}
```

## poll / done — sem bloquear

```kf
val r = spawn trabalho()
if (done(r)) {
    println("pronto: " + poll(r))
}
```

- `poll(r)` devolve o valor se pronto; **default do tipo** (0/false) para
  primitivos não-prontos, `null` para referências. Use `done()` para
  distinguir "não pronto" de um valor default.
- `done(r)` → `Bool`.
- JS: execução é sequencial, então `poll` sempre tem o valor e `done` é
  `true`. Native reporta CONC001.

## Exceções atravessam await

A exceção lançada dentro da tarefa chega **com a mensagem original** no
ponto do await — o runtime desembrulha o wrapper:

```kf
Int quebra() { throw "boom" }

main() {
    val r = spawn quebra()
    try {
        await r
    } catch (String e) {
        println(e)   // "boom"
    }
}
```

## Cancelamento cooperativo

```kf
Int trabalho() {
    var i = 0
    while (i < 10000 && !cancelled()) {
        time.sleep(1)
        i++
    }
    return i
}

main() {
    val r = spawn trabalho()
    time.sleep(30)
    assert(cancel(r))       // marca a tarefa
    await r                 // a tarefa sai do loop cedo
}
```

- `cancel(r)` marca o handle; **a tarefa decide quando sair** consultando
  `cancelled()` dentro do próprio corpo.
- `cancelled()` fora de uma tarefa devolve `false`.
- No JS é no-op marcado (`cancel` devolve `0`, `cancelled` devolve `false`) —
  execução é sequencial.

## selectAny — primeiro que chegar

```kf
val a = spawn lenta()      // 300ms
val b = spawn rapida()     // imediata
println(selectAny(a, b))   // valor da rapida
```

Bloqueia até **qualquer** handle completar e devolve o valor dele. No JS
(sequencial) devolve o primeiro argumento.

## Semântica

- Cada `spawn` roda numa **virtual thread** (JDK 21+) — barato para milhares de tarefas.
- Exceção dentro da tarefa é re-lançada no ponto do `await`.
- `await` num handle duas vezes devolve o mesmo valor (o resultado é memoizado pelo runtime).

## Gaps por target (nunca silenciosos)

| Construto | JVM | Native | JS |
|-----------|-----|--------|----|
| `spawn stmt` | ✅ | CONC001 | CONC003 |
| `val r = spawn expr` | ✅ | CONC001 | CONC003 |
| `await r` | ✅ | CONC001 | CONC003 |

O diagnóstico vem em compile-time, com código — o programa não compila.
No JS o modelo é single-threaded/event-loop; um port futuro seguirá o
mesmo par spawn/await.

## Target separation (0.2.0)

`Target` enum agora separa `NATIVE` (x86-64) de `NATIVE_RISCV64` e `NATIVE_AARCH64`. `spawn` continua com gap `CONC001` em todos os Natives — a separação vale para codegen/linker (`as`/`ld` por arch), não muda a semântica de concorrência. Native ainda usa free-list `kof_free_head` para reuso de `mmap`, sem `spawn`.

## Próximo passo

**[19 — Pacotes e Módulos](19-packages-and-modules.md)**
