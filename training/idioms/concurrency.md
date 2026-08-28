# Idioms — Concurrency

**Status:** available (JVM + JS) · **Introduced:** 0.0.5-alpha · **Updated:** 0.2.0-beta · **Native:** CONC001 (planned)

## What it is

`spawn` executa uma tarefa concorrentemente sem expor threads:

```kof
void processar(Int id) {
    println("processando " + id)
}
main() {
    spawn processar(1)
    spawn processar(2)
    spawn {
        println("background")
    }
    println("fim")
}

// Com resultado (0.2.0-beta)
main() {
    val r = spawn trabalho()   // Handle<T> tipado
    var v = await r            // bloqueia; T com unboxing de primitivos
    println(v)
}
```

## Semântica real (verificada — 0.2.0-beta, 658 testes)

- a tarefa roda em paralelo (JVM: virtual threads; JS: via KofJsRunner);
- o programa **espera as tarefas antes de sair** (join implícito);
- `val r = spawn f()` devolve `Handle<T>` tipado; `await r` com unboxing;
- exceção na tarefa não derruba o programa;
- **Native ainda não suporta** (diagnostic CONC001) — use JVM ou JS.
- **KofScript** `let` top-level também suporta spawn/await via KofScriptGlobals.

## When to use

- trabalho independente que pode rodar em paralelo (processamento de filas,
  I/O, notificações);
- tarefas de background;
- quando o resultado é necessário — use `val r = spawn f(); await r`.

## When not to use

- quando a ordem importa e não há sincronização.
- Native — CONC001; use JVM/JS.

## BAD — expor plataforma

```kof
// NÃO EXISTE — não há Thread/Executor na linguagem
var t = new Thread(() -> work())
t.start()
```

## GOOD

```kof
spawn work()
val r = spawn compute()
var v = await r
```

## GOOD — kof.time interval como scheduler

```kof
// kof.time + spawn para periódicas (JS/JVM)
var id = kof.time.interval(() -> println("tick"), 1000)
```

## WHY

`spawn` expressa intenção. Thread/Runnable/Executor são mecanismos da
plataforma — a decisão de como executar pertence ao runtime.

## Limitações honestas (0.2.0-beta)

- Native: **CONC001** — use JVM/JS por enquanto;
- filas produtor/consumidor: `kof.mq` / `kof.concurrent.Queue` são alternativas via `kof.mq`;
- lambdas com captura funcionam em spawn (BoxN).

## Anti-patterns relacionados

- `fake-idioms.md` — `async`/`await`/Thread não existem (use `spawn`/`await`)
