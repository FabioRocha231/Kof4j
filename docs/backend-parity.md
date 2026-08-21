# Backend Parity — Kof JVM vs Native

**Última atualização:** 21 de agosto de 2026

---

## Tabela de Paridade

| Feature | JVM | Native | Notas |
|---------|-----|--------|-------|
| **Literals** | | | |
| Int literal | ✅ | ⚠️ | Compila, assembly rejeitado |
| Long literal | ✅ | ⚠️ | Compila, assembly rejeitado |
| Float literal | ✅ | ⚠️ | Compila, assembly rejeitado |
| Double literal | ✅ | ⚠️ | Compila, assembly rejeitado |
| String literal | ✅ | ⚠️ | Compila, assembly rejeitado |
| Boolean literal | ✅ | ⚠️ | Compila, assembly rejeitado |
| Char literal | ✅ | ⚠️ | Compila, assembly rejeitado |
| Null literal | ✅ | ⚠️ | Compila, assembly rejeitado |
| **Variáveis** | | | |
| Local variables | ✅ | ⚠️ | Compila, assembly rejeitado |
| Var declaration | ✅ | ⚠️ | Compila, assembly rejeitado |
| Type inference | ✅ | ⚠️ | Compila, assembly rejeitado |
| Nested scopes | ✅ | ⚠️ | Compila, assembly rejeitado |
| **Arithmetic** | | | |
| Integer addition | ✅ | ⚠️ | Compila, assembly rejeitado |
| Integer subtraction | ✅ | ⚠️ | Compila, assembly rejeitado |
| Integer multiplication | ✅ | ⚠️ | Compila, assembly rejeitado |
| Integer division | ✅ | ⚠️ | Compila, assembly rejeitado |
| Integer modulo | ✅ | ⚠️ | Compila, assembly rejeitado |
| Unary negation | ✅ | ⚠️ | Compila, assembly rejeitado |
| Unary not | ✅ | ⚠️ | Compila, assembly rejeitado |
| **Comparisons** | | | |
| == | ✅ | ⚠️ | Compila, assembly rejeitado |
| != | ✅ | ⚠️ | Compila, assembly rejeitado |
| < | ✅ | ⚠️ | Compila, assembly rejeitado |
| <= | ✅ | ⚠️ | Compila, assembly rejeitado |
| > | ✅ | ⚠️ | Compila, assembly rejeitado |
| >= | ✅ | ⚠️ | Compila, assembly rejeitado |
| **Control Flow** | | | |
| if/else | ✅ | ⚠️ | Compila, assembly rejeitado |
| if (no else) | ✅ | ⚠️ | Compila, assembly rejeitado |
| while | ✅ | ⚠️ | Compila, assembly rejeitado |
| for | ✅ | ⚠️ | Compila, assembly rejeitado |
| Nested if | ✅ | ⚠️ | Compila, assembly rejeitado |
| Nested loops | ✅ | ⚠️ | Compila, assembly rejeitado |
| **Functions** | | | |
| Top-level functions | ✅ | ⚠️ | Compila, assembly rejeitado |
| Zero arguments | ✅ | ⚠️ | Compila, assembly rejeitado |
| One argument | ✅ | ⚠️ | Compila, assembly rejeitado |
| Multiple arguments | ✅ | ⚠️ | Compila, assembly rejeitado |
| Return value | ✅ | ⚠️ | Compila, assembly rejeitado |
| Void return | ✅ | ⚠️ | Compila, assembly rejeitado |
| Recursive functions | ✅ | ⚠️ | Compila, assembly rejeitado |
| **Records** | | | |
| record declaration | ✅ | ⚠️ | Compila, assembly rejeitado |
| Record instantiation | ✅ | ⚠️ | Compila, assembly rejeitado |
| Accessors (x(), y()) | ✅ | ⚠️ | Compila, assembly rejeitado |
| **Classes** | | | |
| Class declaration | ✅ | ⚠️ | Compila, assembly rejeitado |
| Fields | ✅ | ⚠️ | Compila, assembly rejeitado |
| Constructors | ✅ | ⚠️ | Compila, assembly rejeitado |
| Instance methods | ✅ | ⚠️ | Compila, assembly rejeitado |
| new ClassName() | ✅ | ⚠️ | Compila, assembly rejeitado |
| this.name = name | ✅ | ⚠️ | Compila, assembly rejeitado |
| **Strings** | | | |
| println("text") | ✅ | ⚠️ | Compila, assembly rejeitado |
| println(variable int) | ✅ | ⚠️ | Compila, assembly rejeitado |
| println(variable string) | ✅ | ⚠️ | Compila, assembly rejeitado |
| String concatenation | ❌ | ❌ | Não suportado em nenhum backend |
| **IO** | | | |
| println | ✅ | ⚠️ | Compila, assembly rejeitado |
| print | ✅ | ⚠️ | Compila, assembly rejeitado |
| System.out (JVM) | ✅ | N/A | |
| syscalls (Native) | N/A | ⚠️ | Compila, assembly rejeitado |

## Legenda

- ✅ Funcional e testado
- ⚠️ Compila mas assembly rejeitado pelo `as`
- ❌ Não suportado
- N/A Não aplicável ao backend

## Notas

### JVM Backend
- Usa ASM para gerar bytecode
- Suporta todas as features do Kof compiladas até agora
- Executa em qualquer JVM

### Native Backend
- Gera assembly x86-64 System V AMD64 ABI
- Assembly é rejeitado pelo `as` — causa raiz não identificada
- Runtime nativa mínima (kof_print, kof_println, kof_print_int)
- Strings representadas como ponteiros para `.data`
- Field offsets calculados a partir da declaração dos campos no IRClass

### Bloqueio atual

**TODOS** os testes native E2E falham porque o assembly gerado é rejeitado pelo `as` (GNU assembler). O erro é `Error reading source file: Main.s` que na verdade é uma falha no passo de assembly/linking.

### Áreas que precisam de trabalho

1. **[URGENTE] Investigar e corrigir erro de assembly** — o assembly gerado tem instruções ou operandos inválidos
2. **[URGENTE] Criar teste que realmente execute o binário** — atualmente nenhum binário nativo é executado
3. **Native**: Records precisam de layout correto de campos
4. **Native**: Classes com fields precisam de alocação heap
5. **Ambos**: String concatenation não suportada
6. **Ambos**: Herança não testada
7. **Ambos**: Interfaces não testadas
