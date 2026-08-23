# CONCURRENCY.md — Modelo de Concorrência Kof (design)

**Status:** Design — NÃO implementado
**Data:** 22 de agosto de 2026

---

## 1. Princípio

Concorrência é uma capacidade da **linguagem/stdlib**, não uma coleção de
APIs da plataforma.

O programador expressa **intenção**:

```text
tarefas concorrentes
```

e não:

```text
Thread / ExecutorService / CompletableFuture / pthread / epoll / libuv
```

A decisão de como executar (virtual thread, platform thread, event loop,
worker) pertence ao **target/runtime**.

---

## 2. Semântica (o que a linguagem promete)

### 2.1 Tarefas

Uma tarefa é uma unidade de execução concorrente com:

- início explícito (função ou bloco);
- terminação implícita (fim do corpo);
- resultado opcional (valor de retorno observável);
- falha propagável (exceção da tarefa é observável).

Conceitualmente:

```text
task
```

### 2.2 Isolamento

O modelo Kof proposto é **isolamento por valor** (como o modelo de atores,
sem a cerimônia):

- cada tarefa possui seu próprio contexto de execução;
- comunicação ocorre por **valores trocados explicitamente** (parâmetros,
  retornos, filas);
- **sem memória compartilhada mutável** como modelo primário (elimina data
  races por construção);
- o runtime pode escalar tarefas livremente entre OS threads.

Isso NÃO é decidido ainda — é a direção proposta. Alternativa considerada:
memória compartilhada com sincronização explícita (rejeitada como modelo
primário por reproduzir a complexidade de threads).

### 2.3 Comunicação

Troca de valores entre tarefas através de:

- parâmetros e retornos (estilo "join");
- filas (produtor/consumidor) — abstração planejada na stdlib
  (`kof.concurrent.Queue`);
- callbacks estruturados (não como modelo primário).

### 2.4 Sincronização

- Por construção (isolamento);
- por valores (retorno/fila);
- nunca por locks como API primária.

---

## 3. Sintaxe (ainda não escolhida)

O design NÃO fixa sintaxe. Candidatos conceituais:

```kof
async {
    ...
}
```

```kof
spawn task()
```

```kof
var resultado = await tarefa()
```

Decisões pendentes:

- `async` como função vs bloco;
- `await` implícito vs explícito;
- como expressar filas/pub-sub;
- modelo de erro (exceção atravessa a tarefa?).

**Não implementar sintaxe antes da semântica acima ser validada.**

---

## 4. Mapeamento por Target

A mesma semântica Kof utiliza implementações diferentes:

| Target | Implementação |
|--------|---------------|
| JVM 25+ | Virtual Threads (scheduler da JVM) |
| JVM 21 | Platform threads / scheduler Kof sobre Executors |
| Native | OS threads + scheduler Kof |
| KofJS (futuro) | Event loop + Promises + Workers |

O código Kof não muda entre targets.

---

## 5. I/O Concorrente

Código como:

```kof
async fun loadUser(id: UUID): User {
    return database.users.find(id)
}
```

não exige que o usuário saiba se internamente foi usado:

```text
blocking I/O
non-blocking I/O
virtual thread
epoll
event loop
worker
```

Essa decisão pertence ao target/runtime.

---

## 6. Dependências

- Lambdas com captura (planned) — necessário para `spawn { ... }` idiomático;
- filas na stdlib (`kof.concurrent.Queue`);
- modelo de exceção por tarefa;
- scheduler Kof no Native (threads + pilha própria).

## 7. Fases de Implementação

1. Semântica validada (este documento);
2. Primitive `spawn`/`async` no JVM (virtual threads quando disponíveis);
3. Scheduler Native;
4. Filas/pub-sub na stdlib;
5. KofJS depois.

## 8. Não-Fazer

- Expor `Thread`, `ExecutorService`, `pthread`, `epoll` como API da linguagem;
- locks como modelo primário;
- `CompletableFuture`-style APIs vazando para o usuário.