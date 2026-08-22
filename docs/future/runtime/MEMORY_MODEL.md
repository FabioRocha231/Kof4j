# MEMORY_MODEL.md — Modelo de Memória do Kof

**Data:** 21 de agosto de 2026
**Status:** Implementado — Fase F.7

---

## 1. Visão Geral

O modelo de memória do Kof é baseado em alocação via mmap com reclaim pelo SO no exit do processo.

```
kof_alloc(size)
    ↓
mmap (heap)
    ↓
objeto/array/string
    ↓
(programa termina)
    ↓
SO reivindica memória
```

---

## 2. Estratégia

### Alocação

- `kof_alloc(size)` usa `mmap` com `MAP_PRIVATE | MAP_ANONYMOUS`
- Memória é alinhada em 16 bytes
- Tracking de contagem de alocações para debug

### Deallocation

- `kof_free(ptr)` é no-op (memória não é liberada)
- Programa de curta duração: SO reivindica no exit
- Futuro: GC ou reference counting

### Ownership

- Objetos são alocados via `kof_alloc`
- Referências são ponteiros diretos
- Sem referência counting
- Sem weak references

---

## 3. Lifetime de Objetos

| Tipo | Lifetime | Deallocation |
|------|----------|--------------|
| Objeto | Todo o programa | SO no exit |
| Array | Todo o programa | SO no exit |
| String | Todo o programa | SO no exit |
| Method Table | Todo o programa | SO no exit |

---

## 4. Root References

Roots são:
- Variáveis locais na stack
- Campos estáticos (se existirem)
- Registradores durante execução

Objetos referenciados por roots permanecem válidos durante toda a execução.

---

## 5. Runtime Functions

| Função | Propósito |
|--------|-----------|
| `kof_alloc(size)` | Aloca memória no heap |
| `kof_free(ptr)` | No-op (deallocation futura) |
| `kof_memstats()` | Imprime estatísticas de alocação |

---

## 6. Object Header

```
offset 0:  type_id (4 bytes)
offset 4:  flags (4 bytes)
offset 8:  method_table_ptr (8 bytes)
```

O header não contém informações de memória (sem mark bits, sem forwarding pointer).

---

## 7. Arquivos

| Arquivo | Papel |
|---------|-------|
| NativeRuntime.java | `kof_alloc`, `kof_free`, `kof_memstats` |
| NativeBackend.java | Gera chamadas para funções de runtime |
| ClassLayout.java | Cálculo de tamanho de objetos |

---

## 8. Limitações

1. Sem GC (memória não é liberada durante execução)
2. Sem reference counting
3. Sem weak references
4. Sem finalização de objetos
5. Sem detecção de memory leaks (apenas contagem de alocações)

---

## 9. Futuro

- Fase G: GC tracing (mark-and-sweep)
- Reference counting para objetos sem ciclos
- Weak references
- Finalização de objetos
- Memory compaction
