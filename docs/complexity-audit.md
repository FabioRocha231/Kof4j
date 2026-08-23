# Auditoria de Complexidade

**Última atualização:** 21 de agosto de 2026

---

## Princípio

> Kof deve ser pequeno por design.

Não queremos acumular features até virar outro Java.

---

## O que Existe Hoje

### Código do Compilador

| Arquivo | Linhas | Função |
|---------|--------|--------|
| Lexer.java | 408 | Análise léxica |
| Parser.java | 888 | Análise sintática |
| SemanticAnalyzer.java | 522 | Análise semântica |
| CompilerDriver.java | 838 | Lowering IR |
| JvmBackend.java | 377 | Backend JVM |
| NativeBackend.java | 652 | Backend Native |
| NativeRuntime.java | 654 | Runtime nativo |
| IRNodes.java | 165 | Representação intermediária |
| Type.java | 83 | Sistema de tipos |
| SymbolTable.java | 112 | Tabela de símbolos |
| ClassLayout.java | 138 | Layout de memória |
| Outros | ~500 | Utilidades |

**Total:** ~4.800 linhas de código do compilador

### O que é necessariamente complexo

- Lexer: precisa reconhecer toda a sintaxe
- Parser: precisa lidar com ambiguidades
- SemanticAnalyzer: precisa resolver tipos e membros
- NativeRuntime: precisa implementar funções de baixo nível

### O que pode ser simplificado

1. **NativeTypeMapper** — não é usado pelo NativeBackend (código morto)
2. **JvmTypeMapper.BUILTIN_TYPES** — mapa definido mas não lido
3. **FieldLayout.naturalSize()** — método nunca chamado
4. **ClassLayout.clearCache()** — no-op
5. **JvmBackend.computeLocals/computeStack** — valores descartados pelo ASM

---

## Abstrações Necessárias vs Desnecessárias

### Necessárias

- **IR** — fronteira entre frontend e backends
- **ClassLayout** — cálculo centralizado de offsets
- **BuiltinTypes** — referências centralizadas de tipos
- **NativeRuntime** — funções de runtime nativo

### Potencialmente Desnecessárias

- **NativeTypeMapper** — não é usado
- **JvmTypeMapper.BUILTIN_TYPES** — não é usado
- **FieldLayout.naturalSize()** — não é usado

---

## Complexidade Acidental

### No Compilador

1. **Parser**: 888 linhas — razoável para um parser completo
2. **SemanticAnalyzer**: 522 linhas — razoável para análise semântica
3. **CompilerDriver**: 838 linhas — poderia ser simplificado
4. **NativeBackend**: 652 linhas — complexo mas necessário

### No Runtime

1. **NativeRuntime**: 654 linhas — função assembly é verbosa mas necessária
2. **21 funções de runtime** — razoável para o subconjunto atual

---

## O que Poderia Ser Simplificado

1. **Remover NativeTypeMapper** — não é usado
2. **Remover JvmTypeMapper.BUILTIN_TYPES** — não é usado
3. **Remover FieldLayout.naturalSize()** — não é usado
4. **Remover ClassLayout.clearCache()** — no-op
5. **Simplificar CompilerDriver** — extrair helpers

---

## Conclusão

O Kof atual tem ~4.800 linhas de compilador. Isso é comparável a:
- Lua: ~20.000 linhas
- Zig: ~150.000 linhas
- Go: ~1.500.000 linhas

Kof é pequeno. O objetivo é manter assim.

**Regra:** antes de adicionar uma feature, perguntar:
1. "Isso resolve um problema real?"
2. "Existe uma forma mais simples?"
3. "Quanto código isso adiciona?"
4. "Isso pode ser resolvido pelo runtime em vez do compilador?"
