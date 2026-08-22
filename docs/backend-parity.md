# Backend Parity — Kof JVM vs Native

**Última atualização:** 21 de agosto de 2026

---

## Tabela de Paridade

| Feature | JVM | Native | Notas |
|---------|-----|--------|-------|
| **Literals** | | | |
| Int literal | ✅ | ✅ | |
| Long literal | ✅ | ✅ | |
| Float literal | ✅ | ✅ | |
| Double literal | ✅ | ✅ | |
| String literal | ✅ | ✅ | |
| Boolean literal | ✅ | ✅ | |
| Char literal | ✅ | ✅ | |
| Null literal | ✅ | ✅ | |
| **Variáveis** | | | |
| Local variables | ✅ | ✅ | |
| Var declaration | ✅ | ✅ | |
| Type inference | ✅ | ✅ | |
| Nested scopes | ✅ | ✅ | |
| **Arithmetic** | | | |
| Integer addition | ✅ | ✅ | |
| Integer subtraction | ✅ | ✅ | |
| Integer multiplication | ✅ | ✅ | |
| Integer division | ✅ | ✅ | |
| Integer modulo | ✅ | ✅ | |
| Unary negation | ✅ | ✅ | |
| Unary not | ✅ | ✅ | |
| **Comparisons** | | | |
| == | ✅ | ✅ | |
| != | ✅ | ✅ | |
| < | ✅ | ✅ | |
| <= | ✅ | ✅ | |
| > | ✅ | ✅ | |
| >= | ✅ | ✅ | |
| **Control Flow** | | | |
| if/else | ✅ | ✅ | |
| if (no else) | ✅ | ✅ | |
| while | ✅ | ✅ | |
| for | ✅ | ✅ | |
| Nested if | ✅ | ✅ | |
| Nested loops | ✅ | ✅ | |
| **Functions** | | | |
| Top-level functions | ✅ | ✅ | |
| Zero arguments | ✅ | ✅ | |
| One argument | ✅ | ✅ | |
| Multiple arguments | ✅ | ✅ | |
| Return value | ✅ | ✅ | |
| Void return | ✅ | ✅ | |
| Recursive functions | ✅ | ✅ | |
| **Records** | | | |
| record declaration | ✅ | ✅ | |
| Record instantiation | ✅ | ✅ | |
| Accessors (x(), y()) | ✅ | ✅ | |
| **Classes** | | | |
| Class declaration | ✅ | ✅ | Native: compila, constructors emitidos |
| Fields | ✅ | ✅ | Native: ClassLayout calcula offsets |
| Constructors | ✅ | ✅ | Native: emitidos como funções |
| Instance methods | ✅ | — | Native: precisa de virtual dispatch |
| new ClassName() | ✅ | ✅ | Native: usa kof_alloc |
| this.name = name | ✅ | ✅ | Native: ClassLayout offset |
| **Strings** | | | |
| println("text") | ✅ | ✅ | |
| println(variable int) | ✅ | ✅ | |
| println(variable string) | ✅ | ✅ | |
| String concatenation | ❌ | ❌ | Não suportado em nenhum backend |
| **Arrays** | | | |
| new Int[10] | ✅ | ✅ | Native: kof_array_alloc |
| a[i] (read) | ✅ | ✅ | Native: kof_array_get com bounds check |
| a[i] = v (write) | ✅ | ✅ | Native: kof_array_set com bounds check |
| a.length | ✅ | ✅ | Native: kof_array_length |
| Array<Int> | ✅ | ✅ | |
| Array<Long> | ✅ | ✅ | |
| Array<String> | ✅ | ✅ | |
| Array como argumento | ✅ | ✅ | |
| Array como retorno | ✅ | ✅ | |
| Array vazio | ✅ | ✅ | |
| **IO** | | | |
| println | ✅ | ✅ | |
| print | ✅ | ✅ | |
| System.out (JVM) | ✅ | N/A | |
| syscalls (Native) | N/A | ✅ | |

## Legenda

- ✅ Funcional e testado
- — Não suportado (precisa de trabalho adicional)
- ❌ Não suportado
- N/A Não aplicável ao backend

## Notas

### JVM Backend
- Usa ASM para gerar bytecode
- Suporta todas as features do Kof compiladas até agora
- Executa em qualquer JVM

### Native Backend
- Gera assembly x86-64 System V AMD64 ABI
- Assembly é aceito pelo `as` e linkado pelo `ld`
- Todos os 7 testes E2E passam
- Runtime nativa: kof_alloc, kof_free, kof_panic, kof_null_error, kof_bounds_error, kof_print, kof_println, kof_print_int
- Runtime strings: kof_string_from_literal, kof_string_length, kof_string_concat, kof_string_equals, kof_print_string, kof_println_string, kof_memcpy
- Runtime arrays: kof_array_alloc, kof_array_length, kof_array_get, kof_array_set
- Strings representadas como KofString objects (type_id + flags + length + data)
- Arrays representados como KofArray objects (type_id + flags + length + elem_size + data)
- Field offsets calculados por ClassLayout (centralizado)
- Object size calculado por ClassLayout (baseado em fields reais)
- Multi-classe em um único .s (resolve cross-class references)
- Funções top-level mangleadas (Default_Main_add)
- Constructors emitidos como funções nativas
- KofNewObject usa kof_alloc (heap allocation)
- KofDup funcional (duplica ponteiro)

### Áreas que precisam de trabalho

1. **Native**: Herança não implementada
2. **Native**: Virtual dispatch não implementado
3. **Ambos**: String concatenação via `+` não integrada
4. **Ambos**: Interfaces não testadas com implementação
