# Known Bugs — handoff para o próximo agente

> **Data:** 02/09/2026 · **Status:** documentados e verificados no compilador
> (0.2.6-beta). Este arquivo existe para que um agente (ou humano) pegue os
> bugs sem precisar redescobri-los. **Não são características** — são bugs
> reais com reprodução mínima.
>
> **Como pegar:** reproduza o snippet (`kof run --target=jvm`), fix no CÓDIGO
> (não no corpus), adicione teste E2E que falha antes/passa depois, atualize
> este arquivo (mova para "resolvidos" com o commit) e remova as notas do
> corpus.

---

## JVM / Native / JS — bugs por alvo

### 1. `throw <valor não-String>` gera bytecode inválido no JVM

- **Sintoma:** `throw 42` ou `catch (Int e)` compila, mas o `.class` falha no
  load (`ClassFormatError`, disfarçado de "JavaFX launcher error").
- **Reprodução:**
  ```kof
  main() {
      try { throw 42 } catch (String e) { print("texto") }
      println("done")
  }
  ```
- **Causa provável:** backend JVM emite o `throw` primitivo sem o wrap em
  `RuntimeException` que o `catch (String e)` espera → constant pool inválido.
- **O que deveria acontecer:** exceções são Strings — **rejeitar `throw
  <não-String>` / `catch <não-String>` em compile-time** (SEM0xx), ou suportar
  wrap/unwrap tipado.
- **Arquivos:** `CompilerDriver.java` (lowering de Throw/catch), `JvmBackend.java`.

---

### 2. Compound assignment `-=`, `/=`, `%=` produzem resultado ERRADO (JVM e Native)

- **Sintoma:** `x -= 2` vira **sinal invertido**; `x /= 2` vira **0**;
  `x %= 3` vira resto errado. `+=` e `*=` funcionam.
- **Reprodução:**
  ```kof
  main() {
      var a = 10; a -= 2; println(a)   // -8 (deveria 8)
      var b = 10; b /= 2; println(b)   // 0  (deveria 5)
      var c = 10; c %= 3; println(c)   // 3  (deveria 1)
  }
  ```
- **Verificado:** idêntico em JVM e Native (bug de IR, não de backend).
- **Causa provável:** lowering do `AssignmentExpr` compound no CompilerDriver
  (ordem dos operandos / mapeamento de op `-=`/`/=`/`%=` errado).
- **Arquivos:** `CompilerDriver.java` (branch `+=`/`-=`/`*=`/`/=`/`%=` no emit
  de assignment), `IRNodes.java`/backends.

---

### 3. `s += "x"` (compound de String) dentro de loop CRASHA o compilador

- **Sintoma:** `while (i < 100) { s += "x" }` → `RuntimeException: frame crash
  em Default/Main.main (Index 0 out of bounds for length 0)` no
  `JvmBackend.emitClass`.
- **Reprodução:**
  ```kof
  main() {
      var s = ""
      var i = 0
      while (i < 100) { s += "x"; i = i + 1 }
      println(s.length)
  }
  ```
- **Controles:** `s = s + "x"` em loop funciona; `acc += 1` (int) em loop
  funciona. O crash é específico do **compound de String** em ponto de merge
  de frames.
- **Causa provável:** mesma classe do COMP002 de call-void (push/pop
  desbalanceado na emissão do concat compound → merge de frames quebra). Ver
  `training/anti-patterns/void-call-merge-crash.md`.
- **Arquivos:** `JvmBackend.java` (emissão do concat em statement), `CompilerDriver.java`.

---

### 4. `switch` com String gera bytecode inválido no JVM

- **Sintoma:** `switch (s) { case "a": ... }` compila mas falha no load
  (`ClassFormatError`/JavaFX launcher error).
- **Reprodução:**
  ```kof
  main() {
      var s = "b"
      switch (s) {
          case "a": println("A"); break
          case "b": println("B"); break
          default: println("?")
      }
  }
  ```
- **Causa provável:** backend JVM não emite o dispatch de `switch` sobre
  `String` (deveria usar `hashCode`+`equals` ou cadeia de comparações).
- **Arquivos:** `JvmBackend.java` (emissão de `switch`), `CompilerDriver.java`.

---

### 5. Cast de ponto flutuante → inteiro gera bytecode inválido

- **Sintoma:** `3.9 as Int`, `3.9 as Long`, `2.7f as Int` compilam mas falham
  no load (JavaFX/ClassFormatError). `Long as Int` e `Int as Char` funcionam
  (01/09).
- **Reprodução:**
  ```kof
  main() {
      var d = 3.9
      var i = d as Int   // ClassFormatError
      println(i)
  }
  ```
- **Causa provável:** emissão do cast FP→Int (D2I/F2I/D2L) errada no
  `JvmBackend` (o fix de 01/09 cobriu I2C/L2I mas não FP→I/L).
- **Arquivos:** `JvmBackend.java` (emitCheckCast), `CompilerDriver.java`.

---

### 6. Sufixo numérico MAIÚSCULO gera bytecode inválido

- **Sintoma:** `42L` / `1.5F` compilam mas falham no load. Minúsculo
  (`42l`, `1.5f`) funciona.
- **Reprodução:**
  ```kof
  main() {
      var x = 42L    // ClassFormatError
      var y = 1.5F   // idem
  }
  ```
- **Causa provável:** o lexer/lowering trata o sufixo maiúsculo como
  identificador/errado. Deveria ser alias do minúsculo (ou rejeitar com
  diagnostic claro).
- **Arquivos:** `Lexer.java`, `Parser.java` (literais numéricos), `CompilerDriver.java`.

---

### 7. Argumento de tipo nullable em chamada genérica não parseia

- **Sintoma:** `listOf<String?>()` → PARSE041 (Unexpected token `?`).
  `List<String?> l = listOf()` funciona.
- **Reprodução:**
  ```kof
  main() {
      var l = listOf<String?>()   // PARSE041
  }
  ```
- **Causa provável:** o parser de `type arguments` em method call não consome
  o `?` (o `parseTypeRef` consome, mas o caminho de type-args não).
- **Arquivos:** `Parser.java` (parseTypeArguments em method call).

---

### 8. Tipo de função em argumento genérico não parseia

- **Sintoma:** `listOf<(Int) -> Int>()` → PARSE (Unexpected token).
- **Reprodução:**
  ```kof
  main() {
      var fs = listOf<(Int) -> Int>()   // não parseia
  }
  ```
- **Causa provável:** o parser de type-args não aceita `(T) -> R` como
  argumento de tipo.
- **Arquivos:** `Parser.java`.

---

### 9. Captura mutável no Native: ler boxed dentro da lambda após mutação
EXTERNA produz lixo

- **Sintoma:** `var f = (x) -> x + offset; offset = 20; f(5)` retorna lixo no
  Native (JVM correto). A direção "lambda escreve" funciona.
- **Reprodução:**
  ```kof
  main() {
      var offset = 10
      var f2 = (x: Int) -> x + offset
      println(f2(5))        // JVM 15 / Native lixo
      offset = 20
      println(f2(5))        // JVM 25 / Native lixo
  }
  ```
- **Causa provável:** `NativeBackend.resolveFieldOffset` resolve o layout do
  box contra a classe da lambda (fallback `HEADER_SIZE`).
- **Arquivos:** `NativeBackend.java`.

---

## Descobertos na investigação agressiva (02/09, rodada 2)

### 10. `!` (NOT lógico) como VALOR de expressão sempre retorna `true`

- **Sintoma:** `println(!true)` → `true`; `var x = !false` → `true`. Em
  **condição** de `if`, `!` funciona (`if (!ativo)` ok).
- **Reprodução:**
  ```kof
  main() {
      var x = !true      // true (deveria false)
      println(x)
      println(!false)    // true (deveria true — coincidentemente certo)
      println(!(1 > 2))  // true (deveria true — coincidência)
  }
  ```
- **Verificado:** JVM e Native — o valor emitido é sempre `1` (true).
- **Causa provável:** lowering do unário `!` em contexto de expressão
  (argumento/atribuição) não nega; só o caminho de condição (jump negation)
  funciona.
- **Arquivos:** `CompilerDriver.java` (UnaryExpr `!`), backends.

---

### 11. `==` em records usa igualdade de REFERÊNCIA no JVM (não `equals`)

- **Sintoma:** `Ponto(1,2) == Ponto(1,2)` → `false` (deveria `true`);
  `a.equals(b)` → `true`.
- **Reprodução:**
  ```kof
  record Ponto(Int x, Int y)
  main() {
      var a = Ponto(1, 2)
      var b = Ponto(1, 2)
      println(a == b)      // false (deveria true — equals é gerado)
  }
  ```
- **Causa provável:** `==` em tipos de referência emite `if_acmpeq`
  (referência), sem despachar para o `equals` gerado do record.
- **Arquivos:** `CompilerDriver.java`/`JvmBackend.java` (comparação `==` de
  referenciais).

---

### 12. Assignment encadeado (`var c = a = b`) crasha o compilador

- **Sintoma:** `var c = a = b` → `Internal compiler error: frame crash
  COMP002 (Index -1 out of bounds)`.
- **Reprodução:**
  ```kof
  main() {
      var a = 1
      var b = 2
      var c = a = b      // COMP002
      println(c)
  }
  ```
- **Causa provável:** a expressão de atribuição como RHS de outra deixa a
  pilha desbalanceada no emit (AssignmentExpr dentro de AssignmentExpr).
- **Arquivos:** `CompilerDriver.java` (emit de AssignmentExpr).

---

### 13. Cast (`x as T`) usado como operando de aritmética crasha o compilador

- **Sintoma:** `var y = (x as Int) + 1` → `frame crash COMP002 (-1)`.
  `println(x as Int)` isolado funciona.
- **Reprodução:**
  ```kof
  main() {
      var x = 5
      var y = (x as Int) + 1    // COMP002
      println(y)
  }
  ```
- **Verificado:** não é específico de Char — `(Int as Int) + 1` também crasha.
- **Causa provável:** o cast (KofCheckCast) deixa um valor na pilha que o
  binário aritmético assume desbalanceado (push extra).
- **Arquivos:** `CompilerDriver.java`/`JvmBackend.java` (emit de `as` + binário).

---

### 14. `Map.size` (propriedade) → `NoSuchFieldError` confuso em runtime

- **Sintoma:** `m.size` (sem parênteses) compila mas falha em runtime com
  `NoSuchFieldError: java.util.HashMap does not have member field 'size'`.
  `m.size()` (método) funciona. `List.size` (propriedade) funciona.
- **Reprodução:**
  ```kof
  main() {
      var m = mapOf("a", 1)
      println(m.size)     // NoSuchFieldError (use m.size())
  }
  ```
- **Inconsistência:** `List` expõe `.size` (propriedade) e `Map` só `size()`
  (método) — a forma propriedade deveria funcionar (ou rejeitar em
  compile-time com diagnostic claro, não NoSuchFieldError).
- **Arquivos:** `CompilerDriver.java` (field-access dispatch de Map).

---

### 15. Primitivo não é atribuível a `Object` (sem auto-boxing)

- **Sintoma:** `Object n = 42` → `SEM021 type mismatch: cannot assign int to
  Object`. `Object o = "kof"` funciona (String→Object).
- **Reprodução:**
  ```kof
  main() {
      Object n = 42        // SEM021 — primitivo não boxa para Object
      println("done")
  }
  ```
- **Impacto:** impede pattern matching/`instanceof` sobre primitivos via
  `Object` (só funciona com referências). É uma **limitação**, não crash —
  mas vale decisão de design (auto-boxing ou diagnostic melhor).
- **Arquivos:** `SemanticAnalyzer.java` (isAssignable primitivo → Object).

---

### 16. `List.toArray()` quebra em JVM e Native (retorno de array)

- **Sintoma:** JVM → `ClassFormatError` (disfarçado de JavaFX launcher error);
  Native → `undefined reference to 'List_toArray'` no link.
- **Reprodução:**
  ```kof
  main() {
      var arr = listOf(1, 2, 3).toArray()
      println(arr.length)
      println(arr[1])
  }
  ```
- **Causa provável:** o retorno de tipo array (`Int[]`) de um método da
  stdlib não é tratado pelos backends (JVM emite constant-pool inválido para
  o tipo array; Native não gera o símbolo `List_toArray`).
- **Arquivos:** `JvmBackend.java`, `NativeBackend.java`, runtime nativo
  (`kof_c`/`NativeRuntime`).
- **Verificado 02/09 pós-merge riscv64** — persiste.

---

### 17. Array `.get()`/`.set()` (não existem) são aceitos e geram saída quebrada

- **Sintoma:** a API real de array é o operador `arr[i]` / `arr[i] = v`
  (ver `training/language/arrays.md`). Porém `arr.get(0)` / `arr.set(0, 5)`
  **compilam** e produzem: JVM → `ClassFormatError: Illegal class name ""`;
  Native → `undefined reference to 'get'/'set'`.
- **Reprodução:**
  ```kof
  main() {
      var arr = new Int[3]
      arr.set(0, 5)     // JVM: ClassFormatError / Native: undefined ref 'set'
      println(arr.get(0))
  }
  ```
- **O que deveria acontecer:** rejeitar em compile-time com diagnostic claro
  ("array não tem método get()/set(); use arr[i]").
- **Causa provável:** método call sobre tipo array cai no caminho genérico de
  dispatch em vez de emitir o array load/store.
- **Arquivos:** `CompilerDriver.java`/`SemanticAnalyzer.java` (dispatch sobre
  array type), `JvmBackend.java`, `NativeBackend.java`.

---

## Comportamentos que PAREcem bugs mas são esperados (não corrigir)

| Cenário | Comportamento | Por quê |
|---------|---------------|---------|
| `l.get(5)` em lista de 3 | `IndexOutOfBoundsException` | bounds check (verificado 27/08) |
| `json.decode<Int>("abc")` | `NumberFormatException` | parse inválido |
| `json.decode<Point>("{\"x\":5}")` sem `y` | NPE/IllegalArgumentException | campo ausente — erro pouco claro (gap de mensagem, não bug de semântica) |
| `"abc".toInt()` | `NumberFormatException` | parse inválido |
| `10 / 0` (variáveis) | `ArithmeticException` runtime | ARITH001 só pega constantes |
| `"Olá 😀".length` | 6 (JVM UTF-16) | gap `STR001` documentado |
| `Map<String, Int>.get(ausente)` | NPE no unboxing | primitivos não representam null (limitação documentada) |

## Resolvidos nesta branch (referência)

- `42l`/`1.5f` minúsculos funcionam (os maiúsculos são o Bug 6).
- `Long as Int` funciona (fix 01/09) — o FP→Int é o Bug 5.
- Null-safety narrowing JVM (`s.length` pós-guard) — corrigido 02/09.
- Concat `"str" + double` — corrigido 02/09.
- Captura mutável JVM (mutação externa) — corrigido 02/09.