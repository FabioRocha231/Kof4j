# 18 — Concorrência

> **Status: implementado (JVM, virtual threads)**
>
> Kof não expõe `Thread`, `Runnable` nem `CompletableFuture`: a intenção é
> `spawn` (rode em paralelo) e `await` (espere o resultado). No Native e
> no JS os mesmos programas reportam gap claro em compile-time — nunca
> comportamento silenciosamente diferente.

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

## Próximo passo

**[19 — Pacotes e Módulos](19-packages-and-modules.md)**
