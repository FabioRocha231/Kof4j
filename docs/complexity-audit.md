# Auditoria de Complexidade

**Última atualização:** 2 de setembro de 2026
**Versão:** 0.2.6-beta (790 testes; 7 targets: jvm, native x86_64, native.risc/arm, js, kofc, android; free-list GC; `VERSION` 0.2.6-beta)

---

## Princípio

> Kof deve ser pequeno por design.

Não queremos acumular features até virar outro Java.

---

## O que Existe Hoje

### Código do Compilador

| Arquivo | Linhas (31/08) | Função |
|---------|--------|--------|
| Lexer.java | ~480 | Análise léxica (pattern matching `case` + `String?`) |
| Parser.java | ~1.720 | Análise sintática (pattern matching, `String?`, `let` KofScript, lambda trailing) |
| SemanticAnalyzer.java | ~1.870 | Análise semântica (pattern matching, `String?`, `CompilerDriver.java:243` import fix) |
| CompilerDriver.java | ~7.520 | Lowering IR (free-list alloc, `substituteTypeVariable` `Box<T>`, `KofScriptGlobals`, stdlib dispatch) |
| JvmBackend.java | ~1.320 | Backend JVM (V21, LineNumberTable, web ws/sse, `Java HttpClient` for JS) |
| NativeBackend.java | ~1.800 | Backend Native (x86_64; toolchain riscv64/aarch64) |
| NativeRuntime.java | ~15.340 | Runtime nativo — maior parte é assembly embutido (free-list `kof_free_head`, spawn pthread 31/08, FP XMM, JSON objetos/arrays, config/log/security/cache asm, `kof_db_mysql_scramble`) |
| JsBackend.java | ~5.280 | Backend JS (GraalJS, CORE_RUNTIME DOM/UI, `kof.http` interop) |
| KofCCompiler + Lexer/Parser/AST/Emitter | ~710 | C subset (`kof c`) → ELF x86_64 |
| KofScript.java | ~610 | KofScript (`let` → `KofScriptGlobals`, REPL) |
| IRNodes.java | ~250 | Representação intermediária (+ KofDebugInfo) |
| Type.java | ~140 | Sistema de tipos (`Type?` nullable) |
| SymbolTable.java | ~210 | Tabela de símbolos |
| ClassLayout.java | ~110 | Layout de memória |
| Optimizer.java | ~610 | Otimizador de IR (sempre ativo) |
| Outros | ~10.000 | Geração de runtime JVM (`JvmRuntime`/`JvmWebRuntime`...), descritores stdlib (`KofWeb`, `KofSecurity`, `KofUi`, `KofDb`...), utilidades |

**Total:** ~46.600 linhas no `kof-compiler` (main; ~51.800 somando `kof-cli`/`kof-script`/`kof-c-compiler`) (0.2.6-beta, 31/08) — o peso vem de assembly nativa embutida e do runtime JVM gerado, não de lógica Java.

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

1. **Parser**: 1.720 linhas — cresceu com pattern matching, lambda trailing, `let` KofScript
2. **SemanticAnalyzer**: 1.874 linhas — razoável para análise semântica (inclui `supportedOn`/diagnostics de gap)
3. **CompilerDriver**: 7.521 linhas — lowering + dispatch da stdlib inteira; candidato prioritário a extração de helpers
4. **NativeBackend**: 1.803 linhas — complexo mas necessário (x86_64 + toolchains)

### No Runtime

1. **NativeRuntime**: 15.341 linhas — assembly embutido é verboso mas necessário (free-list, spawn pthread, FP XMM, JSON, config/log/security/cache/db em asm)
2. **Funções de runtime** — subconjunto amplo mas cada função cobre um módulo da stdlib

---

## O que Poderia Ser Simplificado

1. **Remover NativeTypeMapper** — não é usado
2. **Remover JvmTypeMapper.BUILTIN_TYPES** — não é usado
3. **Remover FieldLayout.naturalSize()** — não é usado
4. **Remover ClassLayout.clearCache()** — no-op
5. **Simplificar CompilerDriver** — extrair helpers

---

## Conclusão

O Kof atual tem ~46.600 linhas no `kof-compiler` (31/08), das quais ~15.300 são
assembly nativa embutida em `NativeRuntime.java` e ~10.000 são templates de
runtime JVM gerado — a lógica Java "pura" do frontend/backends permanece na
escala de dezenas de milhares, comparável a:
- Lua: ~20.000 linhas
- Zig: ~150.000 linhas
- Go: ~1.500.000 linhas

Kof é pequeno. O objetivo é manter assim — e o crescimento recente veio da
implementação nativa (asm), não de camadas Java extras.

**Regra:** antes de adicionar uma feature, perguntar:
1. "Isso resolve um problema real?"
2. "Existe uma forma mais simples?"
3. "Quanto código isso adiciona?"
4. "Isso pode ser resolvido pelo runtime em vez do compilador?"
