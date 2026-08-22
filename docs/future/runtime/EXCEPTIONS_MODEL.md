# EXCEPTIONS_MODEL.md — Modelo de Exceções do Kof

**Data:** 21 de agosto de 2026
**Status:** Implementado — Fase F.6

---

## 1. Visão Geral

Kof suporta `throw` e `try/catch/finally`. Exceções são tratadas de forma diferente nos dois backends:

- **JVM**: Exceções são propagadas naturalmente pela JVM via `athrow`
- **Native**: `throw` termina o processo via `kof_panic`

---

## 2. Sintaxe

### throw

```kof
throw "error message"
```

### try/catch

```kof
try {
    throw "error"
} catch (String e) {
    println(e)
}
```

### try/finally

```kof
try {
    throw "error"
} finally {
    println("cleanup")
}
```

### try/catch/finally

```kof
try {
    throw "error"
} catch (String e) {
    println(e)
} finally {
    println("cleanup")
}
```

### Múltiplos catch

```kof
try {
    throw "error"
} catch (String e) {
    println(e)
} catch (Int e) {
    println(e)
}
```

---

## 3. Semântica

### throw

1. Avalia a expressão
2. No JVM: emite `athrow` (exceção propagada pela JVM)
3. No Native: chama `kof_panic` com a mensagem (processo terminado)

### try/catch

1. Executa o bloco try
2. Se uma exceção é lançada e há um catch compatível, executa o bloco catch
3. No JVM: mecanismo nativo de exception handling
4. No Native: try/catch é tratado como bloco sequencial (exceções são fatais)

### finally

1. Executa independentemente de exceção ser lançada ou não
2. No JVM: código finally é emendado em cada ponto de saída
3. No Native: executado sequencialmente

---

## 4. Runtime Errors

Erros de runtime são fatais em ambos os backends:

| Erro | Função Nativa | Comportamento |
|------|---------------|---------------|
| Null pointer | `kof_null_error()` | Termina com mensagem |
| Array bounds | `kof_bounds_error(i, len)` | Termina com mensagem |
| Runtime panic | `kof_panic(msg)` | Termina com mensagem |

---

## 5. Arquivos

| Arquivo | Papel |
|---------|-------|
| AstNodes.java | `ThrowStmt`, `TryStmt`, `CatchClause` |
| Parser.java | Parsing de `throw`, `try/catch/finally` |
| IRNodes.java | `KofThrow` |
| CompilerDriver.java | Lowering de `throw` e `try/catch/finally` |
| JvmBackend.java | `ATHROW` para throw |
| NativeBackend.java | `kof_panic` para throw |
| NativeRuntime.java | `kof_panic`, `kof_null_error`, `kof_bounds_error` |

---

## 6. Limitações

1. try/catch no Native é sequencial (não há propagação real de exceções)
2. Sem stack unwinding no Native
3. Sem exception object model completo
4. Sem checked exceptions
5. Sem stack traces
