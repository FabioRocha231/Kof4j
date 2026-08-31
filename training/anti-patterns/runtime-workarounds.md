# Anti-pattern — Runtime Workarounds

## Name

Tratar workarounds temporários como regras da linguagem.

## Problem

Quando uma feature ainda não existe, o código precisa de um desvio.
O desvio é legítimo — mas **não é idiom**. O corpus deve marcar
explicitamente `WORKAROUND` e `NOT IDIOMATIC`.

## Workarounds atuais (0.2.6-beta, 30 Aug 2026 — 658 testes)

### 1. Null safety parcial

```kof
// ✅ 0.2.6-beta — String? / Int? implementado com narrowing
String? s = null
if (s != null) {
    println(s.length)   // OK — narrowing via isAssignable
}
// Option<T> genérico ainda é planned — use String? para nulabilidade simples
// WORKAROUND até Option<T>: exceção ou record Found(Bool ok, T value)
```

**Não aprenda Option<T> como idiom — use String?.**

### 2. JSON

```kof
// WORKAROUND — json.encode de objeto/record no Native ainda JSN002
// Use: json.encode(listOf(1, 2, 3))  (primitivos/lists funcionam em ambos)
// JVM e JS: json.encode(Point(3,4)) OK
```

```kof
// WORKAROUND — JSN001
// json.encode(1.5) e Float[]/Double[] no Native não compilam; use Int/String
// JVM e JS: Float/Double OK
```

### 3. Construtor com argumentos — RESOLVIDO (0.2.6-beta)

```kof
// ✅ Primary constructor é a forma idiomática desde 0.0.5
class User(String name, Int age) { }
var u = User("Mel", 30)   // sem new também OK
// Forma verbosa ainda válida mas não idiomática
```

Não use `// WORKAROUND` para construtor — é feature estável.

### 4. Captura em lambdas — RESOLVIDO (0.2.6-beta)

```kof
var offset = 10
var f = (x: Int) -> x + offset   // ✅ OK — captura mutável via box sintético BoxN
println(f(5))   // 15
```

Não marque captura como workaround — é implementado.

### 5. Imports de projeto grande — RESOLVIDO (27/08)

```kof
// ✅ CompilerDriver expandKofImports agora trata import a.b.C (arquivo) + a.b (pasta)
// Projeto largeproj com a/b/C.kf → Main.class + a/b/C.class corretos
import a.b.C
import a.b.*
```

Não é necessário workaround manual de imports.

### 6. List.get / listOf — RESOLVIDO (27/08)

```kof
var l = listOf(1, 2, 3)
var x = l.get(1)   // 2 — kof_list_get com bounds, sem handling manual
```

Não implemente bounds check manual — a stdlib já faz.

### 7. GC nativo — em progresso

```kof
// Native usa free-list first-fit + kof_gc_collect (mark-sweep conservador)
// kof_alloc reutiliza blocos via kof_free_head; kof_gc_tick automático
// Ainda não é GC completo de produção — programas longos devem evitar vazamento
```

## Regra

1. Todo workaround no corpus carrega o rótulo `WORKAROUND`.
2. Todo workaround cita a feature planned que o substituirá.
3. Nunca apresente workaround como exemplo de "código idiomático".

## Quando um workaround deixa de ser workaround

Quando a feature é implementada, o exemplo oficial é atualizado e o rótulo
removido. O histórico conceitual é preservado no CHANGELOG, não no corpus.
Em 0.2.6-beta foram removidos: captura lambda, imports a.b.C, List.get, primary constructor.

## Exceptions

- Nenhuma: workarounds são sempre temporários por definição.
