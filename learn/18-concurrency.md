# 18 — Concorrência

> **Status: implementado (JVM; Native CONC001)**
>
> Kof usa as capacidades de concorrência da JVM diretamente.

## Threads

Kof usa `Thread` e `Runnable` do Java:

```kf
var thread = new Thread(() -> {
    print("rodando em outra thread");
});
thread.start();
thread.join();
```

## Virtual Threads (Java 21+)

```kf
var thread = Thread.ofVirtual().start(() -> {
    print("virtual thread");
});
thread.join();
```

## CompletableFuture

```kf
import java.util.concurrent.CompletableFuture;

var futuro = CompletableFuture.supplyAsync(() -> {
    return "resultado";
});

var resultado = futuro.get();  // "resultado"
```

## Synchronized

```kf
class Conta {
    private Double saldo;

    synchronized void depositar(Double valor) {
        this.saldo += valor;
    }

    synchronized Double getSaldo() {
        return this.saldo;
    }
}
```

## Structured Concurrency (planejado)

```kf
import jdk.incubator.concurrent.StructuredTaskScope;

async Dashboard carregarDashboard(UUID id) {
    parallel {
        user = users.find(id);
        posts = posts.findByUser(id);
        notifications = notifications.find(id);
    }
    return new Dashboard(user, posts, notifications);
}
```

## Próximo passo

[Packages e Módulos →](19-packages-and-modules.md)
