# Idioms — Concurrency

**Status:** available (JVM) · **Introduced:** 0.0.5-alpha · **Native:** CONC001 (planned)

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
```

## Semântica real (verificada)

- a tarefa roda em paralelo (JVM: virtual threads — detalhe interno);
- o programa **espera as tarefas antes de sair** (join implícito);
- o retorno da função é descartado (fire-and-forget);
- exceção na tarefa não derruba o programa;
- **Native ainda não suporta** (diagnostic CONC001) — use o JVM.

## When to use

- trabalho independente que pode rodar em paralelo (processamento de filas,
  I/O, notificações);
- tarefas de background.

## When not to use

- quando o resultado é necessário no fluxo principal (`await` é planned — não existe);
- quando a ordem importa.

## BAD — expor plataforma

```kof
// NÃO EXISTE — não há Thread/Executor na linguagem
var t = new Thread(() -> work())
t.start()
```

## GOOD

```kof
spawn work()
```

## WHY

`spawn` expressa intenção. Thread/Runnable/Executor são mecanismos da
plataforma — a decisão de como executar pertence ao runtime.

## Limitações honestas

- resultado observável de tarefa: **planned** (`await`/join explícito);
- filas produtor/consumidor: **planned** (`kof.concurrent.Queue`);
- Native: **CONC001** — use o target JVM por enquanto;
- lambdas sem captura (mesma limitação das lambdas).

## Anti-patterns relacionados

- `fake-idioms.md` — `async`/`await`/Thread não existem