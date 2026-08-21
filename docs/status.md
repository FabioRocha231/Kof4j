# Status do Projeto Kof

**Ultima atualização:** 21 de agosto de 2026

---

## Build

```
mvn clean package -DskipTests → PASSA
mvn test → 32/32 PASSAM (JUnit)
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

---

## Fase E — Native Backend (EM PROGRESSO)

### O que foi feito

- NativeBackend reescrito como stack machine real
- consome Kof IR diretamente (sem JvmTypeMapper)
- emite x86-64 System V AMD64 ABI
- string literals na seção `.data`
- println/print como syscalls nativos (kof_print, kof_println)
- kof_print_int para impressão de inteiros
- field offsets calculados a partir do IRClass
- NativeTypeMapper independente de JvmTypeMapper

### Bugs corrigidos nesta fase

1. Constructor argument order (NEW → DUP → args → CONSTRUCTOR)
2. Super constructor usando Type UnknownType → corrigido para usar superclass type
3. Default constructor sem resolvedCtor → agora sempre emite chamada
4. ownerTypeFromInternal agora gera ClassType com packageName correto
5. println com integer agora usa kof_print_int

### O que funciona end-to-end (JVM)

| Feature | Status |
|---------|--------|
| println("Hello") | ✅ |
| var x = 10; x + y | ✅ |
| if/else | ✅ |
| while | ✅ |
| for | ✅ |
| record Point(Int x, Int y) | ✅ |
| Point(10, 20) | ✅ |
| p.x() | ✅ |
| class User | ✅ |
| new User("Mel") | ✅ |
| user.getName() | ✅ |
| Field assignment this.name = name | ✅ |
| Expression body methods | ✅ |

### Testes

| Tipo | Quantidade |
|------|-----------|
| JUnit (kof-compiler) | 32 passando |
| Architectural isolation | 7 testes |
| IR representation | 6 testes |
| End-to-end JVM | 19 testes |

---

## Bugs Restantes

1. `var` em campos de classe não suportado
2. herança de classes não testada
3. interfaces não testadas com implementação
4. Native: assembly gerado compilado mas `as` rejeita (erro de ABI)
5. Native: precisa de runtime nativa para execução real

## Próximos Passos

### Fase E (em progresso)
1. 🔴 Corrigir erro de assembly no Native
2. Records com constructor e accessors no Native
3. Classes com fields e methods no Native
4. Documentação do Native backend
5. Backend parity table

### Roadmap de Longo Prazo
- Criado `docs/future/ROADMAP.md` com visão completa
