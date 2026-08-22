# Status do Projeto Kof

**Ultima atualização:** 21 de agosto de 2026

---

## Build

```
mvn clean package -DskipTests → PASSA
mvn test → 115/115 PASSAM (JUnit)
kof run → FUNCIONA
kof build → FUNCIONA
```

---

## Fases Concluídas

### Fase C — Type System + Symbol Resolution ✅

- Type.java: PrimitiveType, ClassType, UnknownType
- SymbolTable.java: scopes encadeados
- SemanticAnalyzer.java: resolução de métodos, constructors, fields, locals
- JvmTypeMapper.java: isolado no backend JVM
- 16 testes JUnit

### Fase D — Generalização da IR ✅

- IRNodes.java reescrito sem dependências ASM
- Kof IR backend-agnostic: LabelId, KofOperation
- CompilerDriver gera IR semântica
- JVM e Native backends consomem a mesma IR
- NativeTypeMapper independente de JvmTypeMapper

### Fase E — Native Backend ✅

- NativeBackend reescrito como stack machine real
- consome Kof IR diretamente (sem JvmTypeMapper)
- emite x86-64 System V AMD64 ABI
- string literals na seção `.data`
- println/print como syscalls nativos
- Multi-classe em um único .s
- Mangle de nomes para funções top-level
- Parser: suporte a `: ReturnType` em funções top-level

### Fase F — Runtime + Object Model (em progresso)

#### Auditoria concluída
- `docs/future/runtime/CURRENT_STATE.md` — mapeamento completo do estado atual
- Identificados 15 hacks no NativeBackend
- Mapeadas todas as operações IR × Runtime

#### ABI definida
- `docs/future/runtime/RUNTIME_ABI.md` — contrato semântico entre compiler e runtime
- Definidos: allocation, object model, field layout, strings, arrays, errors, calling convention

#### Object Model definido
- `docs/future/runtime/OBJECT_MODEL.md` — layout de objetos, records, strings, arrays
- Header: 8 bytes (type_id + flags)
- Fields: ordenados, 8 bytes cada (padded)

#### Infraestrutura implementada
- `ClassLayout.java` — cálculo centralizado de offset e tamanho de fields
- `FieldLayout.java` — representação de um field no layout
- `NativeRuntime.java` — gera assembly para funções de runtime
- kof-runtime module: estrutura criada (vazio, para runtime futura)

#### NativeBackend refatorado
- `computeFieldOffset` → `ClassLayout.fieldOffset` (centralizado)
- `computeObjectSize` → `ClassLayout.totalSize` (baseado em fields reais)
- `emitBuiltinFunctions` → `NativeRuntime.generateRuntimeAssembly()` (separado)
- `KofNewObject` → usa `kof_alloc` (heap allocation) em vez de stack
- `KofDup` → funcional (duplica ponteiro na stack)
- `emitMethod` → emite construtores (não mais skip de `<init>`)
- `sanitizeName` → remove `<` e `>` de `<init>` (assembly válido)
- Object size: 24 bytes para records com 2 fields (8 header + 2×8)

#### Runtime functions nativas
- `kof_alloc(size)` — heap allocation via mmap
- `kof_free(ptr)` — no-op (futuro: GC)
- `kof_panic(message)` — erro fatal com mensagem
- `kof_null_error()` — null pointer access
- `kof_bounds_error(i, len)` — array index out of bounds
- `kof_print`, `kof_println`, `kof_print_int` — I/O
- `kof_string_from_literal`, `kof_string_length`, `kof_string_concat`, `kof_string_equals` — Strings
- `kof_print_string`, `kof_println_string` — String I/O
- `kof_memcpy` — memória
- `kof_array_alloc`, `kof_array_length`, `kof_array_get`, `kof_array_set` — Arrays

#### Fase F.1 — String Model ✅

- `BuiltinTypes.STRING` — referência centralizada (elimina `ClassType("java.lang", "String")` espalhados)
- KofString layout: type_id(4) + flags(4) + length(4) + padding(4) + UTF-8 data + \0
- NativeRuntime: 7 funções de runtime para strings
- NativeBackend: string literals criam KofString via `kof_string_from_literal`
- NativeBackend: println/print usa `kof_println_string`/`kof_print_string` para KofString
- `STRING_MODEL.md` documentado
- 10 novos testes (tipo, literal, variável, UTF-8, dispatch, layout constants)

#### Fase F.2 — Array Model ✅

- `Type.ArrayType` — tipo array no Type System
- `NewArrayExpr` + `ArrayAccessExpr` — nós AST para criação e acesso
- Parser: `new Type[size]`, `expr[expr]`, `expr.length`
- SemanticAnalyzer: type checking (índice é Int, tipo compatível, length retorna Int)
- CompilerDriver: lowering para `KofNewArray`/`KofArrayLoad`/`KofArrayStore`/`KofArrayLength`
- NativeRuntime: 4 funções de runtime para arrays (alloc, length, get, set)
- NativeBackend: lowering completo das operações de array
- JVM Backend: `NEWARRAY`/`IALOAD`/`IASTORE`/`ARRAYLENGTH`
- `ARRAY_MODEL.md` documentado
- 25 novos testes (criação, acesso, length, long, string, loop, argumento, retorno, vazio, constants, type system)

#### Fase F.3 — Inheritance ✅

- `SemanticAnalyzer.resolveInHierarchy()` — caminha a cadeia de superclasses para resolver members
- `ClassLayout.buildWithSuper()` — inclui fields herdados no layout com offsets corretos
- `NativeBackend.allClassesMap` — armazena todas as IRClasses para resolver superclasses
- `CompilerDriver.findSuperClass()` — encontra a superclass de uma classe
- `CompilerDriver.lowerConstructor()` — suporta `super(args)` com argumentos
- Constructor chaining: `super(args)` é a primeira instrução do construtor
- Acesso a fields e métodos herdados funciona em ambos os backends
- Herança de 3 níveis suportada
- `INHERITANCE_MODEL.md` documentado
- 20 novos testes (subclasse, fields herdados, methods herdados, constructor chaining, 3 níveis, object size)

### Bugs corrigidos nesta fase

1. Constructor argument order (NEW → DUP → args → CONSTRUCTOR)
2. Super constructor usando Type UnknownType → corrigido para usar superclass type
3. Default constructor sem resolvedCtor → agora sempre emite chamada
4. ownerTypeFromInternal agora gera ClassType com packageName correto
5. println com integer agora usa kof_print_int
6. Native: multi-classe emitida em um único .s
7. Native: mangle de funções top-level
8. Parser: `: ReturnType` suportado em funções top-level
9. Native: object size hardcoded 64 → ClassLayout calcula tamanho real
10. Native: field offset hash-based → ClassLayout com offset centralizado
11. Native: KofNewObject stack allocation → kof_alloc heap allocation
12. Native: KofDup no-op → funcional
13. Native: constructors nunca emitidos → agora emitidos
14. Native: `<init>` com `<` inválido em assembly → sanitizado
15. String type: `ClassType("java.lang", "String")` espalhado → `BuiltinTypes.STRING`
16. Native: string literals como ponteiros crus → KofString objects
17. Native: println strlen-based → kof_println_string com length armazenado

### O que funciona end-to-end

| Feature | JVM | Native |
|---------|-----|--------|
| println("Hello") | ✅ | ✅ |
| var x = 10; x + y | ✅ | ✅ |
| if/else | ✅ | ✅ |
| while | ✅ | ✅ |
| for | ✅ | ✅ |
| record Point(Int x, Int y) | ✅ | ✅ |
| Point(10, 20) | ✅ | ✅ |
| p.x() | ✅ | ✅ |
| class User | ✅ | ✅ |
| new User("Mel") | ✅ | — |
| user.getName() | ✅ | — |
| Field assignment this.name = name | ✅ | — |
| Expression body methods | ✅ | — |
| Funções com retorno | ✅ | ✅ |
| println(variable int) | ✅ | ✅ |
| Heap allocation (kof_alloc) | N/A | ✅ |
| Constructors emitidos | N/A | ✅ |
| KofDup funcional | N/A | ✅ |
| KofString objects | N/A | ✅ |
| String literals (KofString) | ✅ | ✅ |
| println("UTF-8") | ✅ | ✅ |
| String variable | ✅ | ✅ |

### Testes

| Tipo | Quantidade |
|------|-----------|
| JUnit (kof-compiler) | 115 passando |
| Architectural isolation | 7 testes |
| IR representation | 6 testes |
| End-to-end JVM | 19 testes |
| End-to-end Native | 7 testes |
| Native debug | 1 teste |
| Phase F (runtime + object model) | 9 testes |
| Phase F.1 (string model) | 10 testes |
| Phase F.2 (array model) | 25 testes |
| Phase F.3 (inheritance) | 20 testes |
| Phase F.4 (virtual dispatch) | 11 testes |

---

## Arquivos Modificados/Criados (Fase F)

### Criados
- `kof-runtime/pom.xml` — módulo Maven para runtime
- `kof-compiler/.../ClassLayout.java` — cálculo centralizado de field layout
- `kof-compiler/.../FieldLayout.java` — representação de field no layout
- `kof-compiler/.../NativeRuntime.java` — gera assembly para runtime functions
- `docs/future/runtime/CURRENT_STATE.md` — auditoria completa
- `docs/future/runtime/RUNTIME_ABI.md` — definição da ABI
- `docs/future/runtime/OBJECT_MODEL.md` — modelo de objetos

### Modificados
- `kof-compiler/pom.xml` — dependência em kof-runtime
- `kof-compiler/.../NativeBackend.java` — refatorado para usar ClassLayout, NativeRuntime, constructors, KofDup
- `kof-compiler/src/test/.../CompilerDriverTest.java` — 9 novos testes Phase F

---

## Bugs Restantes

1. `var` em campos de classe não suportado
2. herança de classes não implementada
3. interfaces não testadas com implementação
4. Arrays não implementados no native
5. Virtual dispatch não implementado
6. GC não implementado
7. Exception handling não implementado
8. String concat via `+` não integrada no CompilerDriver
9. String equals via `==` não integrada no CompilerDriver

## Próximos Passos

### Fase F (próximas etapas)
1. String model (KofString com header, length, UTF-8)
2. Array model (KofArray com header, length, elements)
3. Inheritance básica (class Dog : Animal)
4. JVM adapters para runtime ABI
5. Runtime errors em execução real

### Roadmap de Longo Prazo
- Criado `docs/future/ROADMAP.md` com visão completa
