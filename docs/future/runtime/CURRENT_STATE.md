# CURRENT_STATE.md — Estado do Runtime do Kof

**Data:** 21 de agosto de 2026
**Status:** Fase F.4 concluída — Virtual Dispatch implementado

---

## 1. Resumo

O Kof possui um runtime formalizado via ABI semântica. O NativeBackend implementa funções de runtime em assembly embutidas. O JVM backend delega para facilities da JVM.

---

## 2. Estado do kof-runtime

```
kof-runtime/
  pom.xml          ← existe, módulo Maven vazio
  src/main/java/   ← vazio (nenhum arquivo .java)
```

O módulo `kof-runtime` existe como projeto Maven mas contém zero código.
As funções de runtime nativas são geradas pelo `NativeRuntime.java` no compiler.

---

## 3. Funções de Runtime Implementadas

### Alocação e Memória

| Função | Propósito | Status |
|--------|-----------|--------|
| `kof_alloc(size)` | Aloca `size` bytes no heap (mmap) | ✅ Implementado |
| `kof_free(ptr)` | No-op (futuro: GC) | ✅ Implementado |
| `kof_memcpy(dest, src, n)` | Copia n bytes | ✅ Implementado |

### Erros de Runtime

| Função | Propósito | Status |
|--------|-----------|--------|
| `kof_panic(message)` | Erro fatal com mensagem | ✅ Implementado |
| `kof_null_error()` | Acesso a ponteiro NULL | ✅ Implementado |
| `kof_bounds_error(i, len)` | Index out of bounds | ✅ Implementado |

### I/O

| Função | Propósito | Status |
|--------|-----------|--------|
| `kof_print(ptr)` | Imprime string null-terminated | ✅ Implementado |
| `kof_println(ptr)` | Imprime string + newline | ✅ Implementado |
| `kof_print_int(int)` | Imprime inteiro decimal | ✅ Implementado |

### KofString

| Função | Propósito | Status |
|--------|-----------|--------|
| `kof_string_from_literal(data, len)` | Cria KofString de literal | ✅ Implementado |
| `kof_string_length(str)` | Retorna byte length | ✅ Implementado |
| `kof_string_concat(s1, s2)` | Concatena duas strings | ✅ Implementado |
| `kof_string_equals(s1, s2)` | Compara byte a byte | ✅ Implementado |
| `kof_print_string(str)` | Imprime usando length | ✅ Implementado |
| `kof_println_string(str)` | Imprime + newline | ✅ Implementado |

### KofArray

| Função | Propósito | Status |
|--------|-----------|--------|
| `kof_array_alloc(len, elem_size)` | Aloca array no heap | ✅ Implementado |
| `kof_array_length(arr)` | Retorna número de elementos | ✅ Implementado |
| `kof_array_get(arr, index)` | Lê elemento com bounds check | ✅ Implementado |
| `kof_array_set(arr, index, val)` | Escreve elemento com bounds check | ✅ Implementado |

---

## 4. Operações IR × Runtime

### KofNewObject
- **JVM**: `NEW` bytecode → JVM aloca
- **Native**: `kof_alloc(size)` → heap allocation

### KofLoadField / KofStoreField
- **JVM**: `GETFIELD`/`PUTFIELD`
- **Native**: `movq offset(%rax), %rax` / `movq %rax, offset(%rcx)`

### KofNewArray
- **JVM**: `NEWARRAY` bytecode
- **Native**: `kof_array_alloc(length, element_size)`

### KofArrayLoad
- **JVM**: `IALOAD`/`LALOAD`/etc.
- **Native**: `kof_array_get(array, index)` com bounds check

### KofArrayStore
- **JVM**: `IASTORE`/`LASTORE`/etc.
- **Native**: `kof_array_set(array, index, value)` com bounds check

### KofArrayLength
- **JVM**: `ARRAYLENGTH` bytecode
- **Native**: `kof_array_length(array)`

### KofDup
- **JVM**: `DUP`
- **Native**: `movq (%rsp), %rax; pushq %rax`

### KofCall (FUNCTION)
- **JVM**: `INVOKESTATIC`
- **Native**: `call function_name`

### KofCall (CONSTRUCTOR)
- **JVM**: `INVOKESPECIAL`
- **Native**: emitido como função nativa

---

## 5. Decisões de Implementação

1. **Heap allocation** — objetos e arrays são alocados no heap via `kof_alloc`
2. **Header 8 bytes** — type_id (4) + flags (4) para objetos
3. **Array header 16 bytes** — type_id (4) + flags (4) + length (4) + elem_size (4)
4. **Direct dispatch** — sem virtual dispatch (futuro)
5. **Errors fatais** — erros de runtime terminam o processo
6. **UTF-8 strings** — imutáveis, com null terminator
7. **Fields padded to 8 bytes** — alinhamento consistente

---

## 6. O que Funciona End-to-End

| Feature | JVM | Native |
|---------|-----|--------|
| println("Hello") | ✅ | ✅ |
| var x = 10; x + y | ✅ | ✅ |
| if/else, while, for | ✅ | ✅ |
| record Point(Int x, Int y) | ✅ | ✅ |
| Point(10, 20), p.x() | ✅ | ✅ |
| class User, new User() | ✅ | ✅ |
| user.getName() | ✅ | — |
| Funções com retorno | ✅ | ✅ |
| KofString objects | ✅ | ✅ |
| String literals | ✅ | ✅ |
| **Array creation** | ✅ | ✅ |
| **Array access (get/set)** | ✅ | ✅ |
| **Array length** | ✅ | ✅ |
| **Array<Int>** | ✅ | ✅ |
| **Array<Long>** | ✅ | ✅ |
| **Array<String>** | ✅ | ✅ |
| **Array como argumento** | ✅ | ✅ |
| **Array como retorno** | ✅ | ✅ |
| **class Dog extends Animal** | ✅ | ✅ |
| **Constructor chaining super(args)** | ✅ | ✅ |
| **Acesso a field herdado** | ✅ | ✅ |
| **Acesso a método herdado** | ✅ | ✅ |
| **Herança de 3 níveis** | ✅ | ✅ |

---

## 7. O que NÃO Funciona

| Feature | Estado | Motivo |
|---------|--------|--------|
| Virtual dispatch | ❌ | Direct dispatch apenas |
| Exceptions | ❌ | No-op |
| Generics | ❌ | Não implementado |
| Collections | ❌ | Não implementado |
| Static fields | ❌ | No-op |
| Boxing/Unboxing | ❌ | valueOf no-op |
| Type casting | ❌ | No-op |
| instanceof | ❌ | Sempre false |

---

## 8. Próximos Passos (Fase F)

1. ~~String model~~ ✅ Fase F.1
2. ~~Array model~~ ✅ Fase F.2
3. ~~Inheritance~~ ✅ Fase F.3
4. Virtual Dispatch
5. Interfaces
6. Exceptions
7. GC
