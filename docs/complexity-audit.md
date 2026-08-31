# Auditoria de Complexidade

**Última atualização:** 27 de agosto de 2026
**Versão:** 0.2.6-beta (658 testes; 6 targets; free-list GC; `VERSION` 0.2.6-beta)

---

## Princípio

> Kof deve ser pequeno por design.

Não queremos acumular features até virar outro Java.

---

## O que Existe Hoje

### Código do Compilador

| Arquivo | Linhas | Função |
|---------|--------|--------|
| Lexer.java | ~420 | Análise léxica (pattern matching `case` + `String?`) |
| Parser.java | ~920 | Análise sintática (pattern matching, `String?`, `let` KofScript) |
| SemanticAnalyzer.java | ~560 | Análise semântica (pattern matching, `String?`, `CompilerDriver.java:243` import fix) |
| CompilerDriver.java | ~900 | Lowering IR (free-list alloc, `substituteTypeVariable` `Box<T>`, `KofScriptGlobals`) |
| JvmBackend.java | ~400 | Backend JVM (V21, LineNumberTable, `Java HttpClient` for JS) |
| NativeBackend.java | ~700 | Backend Native (x86_64 + riscv64 `.option arch,rv64g`, `li a7 214/64/93`) |
| NativeRuntime.java | ~750 | Runtime nativo (free-list `kof_free_head` + `kof_gc_collect`, `kof_db_mysql_scramble`) |
| JsBackend.java | ~500 | Backend JS (GraalJS, pattern matching, `kof.http` interop) |
| KofCcompiler.java | ~200 | C subset (`kof c`) → ELF x86_64 |
| KofScript.java | ~150 | KofScript (`let` → `KofScriptGlobals`, REPL) |
| IRNodes.java | ~170 | Representação intermediária (+ KofDebugInfo) |
| Type.java | ~90 | Sistema de tipos (`Type?` nullable) |
| SymbolTable.java | ~120 | Tabela de símbolos |
| ClassLayout.java | ~140 | Layout de memória |
| Outros | ~600 | Utilidades + Optimizer |

**Total:** ~14.500 linhas de código do compilador (0.2.6-beta, 27/08)

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

O Kof atual tem ~13.000 linhas de compilador. Isso é comparável a:
- Lua: ~20.000 linhas
- Zig: ~150.000 linhas
- Go: ~1.500.000 linhas

Kof é pequeno. O objetivo é manter assim.

**Regra:** antes de adicionar uma feature, perguntar:
1. "Isso resolve um problema real?"
2. "Existe uma forma mais simples?"
3. "Quanto código isso adiciona?"
4. "Isso pode ser resolvido pelo runtime em vez do compilador?"
