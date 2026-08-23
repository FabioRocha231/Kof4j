# KofJS — o backend JavaScript da Kof

> **Status: alpha.** O pipeline `.kf → Kof IR → KofJS → .js → execução` funciona
> e roda programas reais. O target JS não depende de Node.js: o próprio Kof
> executa o JavaScript gerado com a engine embarcada.

## O que é

KofJS é o backend da Kof que gera **JavaScript moderno** (ECMAScript 2022+,
ES Modules) a partir da **mesma Kof IR** usada pelos backends JVM e Native.

**Não é uma segunda linguagem.** Não existe parser, AST, type checker ou
semântica alternativos. O frontend é um só; o backend muda:

```text
                    Kof Source
                         │
                         ▼
                 ┌──────────────┐
                 │ Kof Frontend │
                 └──────┬───────┘
                        │
                        ▼
                    Kof IR
                        │
          ┌─────────────┼─────────────┐
          │             │             │
          ▼             ▼             ▼
        JVM           Native        KofJS
          │             │             │
       .class          ELF           .mjs
```

## Arquitetura do backend

```text
Kof IR
   ↓
JS Lowering (JsBackend)
   ↓
JsIr  (JsModule / JsClass / JsFunction / JsStatement / JsExpression)
   ↓
JsEmitter
   ↓
.mjs  (ESM, ES2022+)
```

- **JsBackend** — converte o IR stack-based em um AST JavaScript (JsIr).
  Os padrões de controle de fluxo emitidos pelo frontend (if/while/for/
  do-while/for-in/switch/try) são reconstruídos como controle de fluxo
  JavaScript nativo.
- **JsIr** — AST próprio do backend (nunca parse de JavaScript).
- **JsEmitter** — imprime o AST como texto ESM.

## Execução — nada de Node

O KofJS **não depende de Node.js nem de nenhum runtime externo**. O Kof
embarca uma engine JavaScript (GraalJS) e executa o módulo gerado no próprio
processo:

- `kof run main.kf --target=js` — compila e executa com a engine embarcada.
- A suíte E2E também executa assim (sem processos externos).

O JavaScript gerado, porém, é **ESM padrão** — o mesmo `.mjs` roda em qualquer
engine (browser, Node, Deno, Bun) quando o programa não usa operações de
plataforma.

## Camadas de runtime

```text
Default.mjs (programa gerado — JS puro)
   ├── kof-runtime.mjs     → core platform-neutral (print, List, String, JSON, time)
   └── kof-runtime-io.mjs  → operações de plataforma (filesystem, stdin, stdout)
                               delega para `kof_platform`, implementado em Java
                               (dev.kof.runtime.KofJsRunner)
```

O código gerado nunca chama `console.*`/`process.*` diretamente; tudo passa
pelo runtime. Quando executado por outra engine, o núcleo (`kof-runtime.mjs`)
funciona; as operações de IO precisam de um `kof_platform` correspondente
(no futuro: `kof_platform` web para o browser).

## CLI

```bash
kof build src/ --target=js --output=build/js
kof run main.kf --target=js
```

O target JS está em desenvolvimento e o CLI informa isso no `--help`.

## Tipos e semântica

| Kof | JS | Observações |
|---|---|---|
| `Int` | `number` | aritmética com wrap 32-bit (`\| 0`), divisão trunca |
| `Long` | `number` | precisão até 2^53; documentar valores maiores |
| `Float` / `Double` | `number` | literal integral imprime sem `.0` (difere do JVM) |
| `Bool` | `boolean` / `0\|1` | comparações/equals produzem `true/false`; operações bitwise coercem |
| `Char` | `number` | code unit; `charAt` → `charCodeAt` |
| `String` | `string` | mapeamento direto na API |
| `List<T>` | `Array` + runtime | bounds check em get/set/remove |
| `Array` | `Array` | `new Int[n]` → `new Array(n).fill(0)` |
| classes | `class` | herança, super, override nativos |
| records | `class` + accessors | campos internos `_name` para não colidir com accessor |
| interfaces | — (type-level) | chamadas estruturais `recv.method(...)` |
| generics | erasure | a informação de tipo fica no compilador |

### Diferenças semânticas documentadas

- **Long além de 2^53** perde precisão (JS `number` é double).
- **Bitwise em Long** trunca para 32 bits (operadores JS).
- **Arrays JS** crescem automaticamente em atribuição (JVM lança
  ArrayIndexOutOfBounds).
- **Float/Double literal integral**: `println(1.0)` → `1` (JVM: `1.0`).
- **Interface runtime** não existe em JS; a semântica é resolvida no
  compile-time (chamadas estruturais).
- **`hashCode`/`getClass`** de `Object` não têm equivalente direto.

## JSON

```text
json.encode   → JSON.stringify
json.decode   → JSON.parse (com binding para classes/records via helper)
```

A informação de tipo permanece no compilador: `json.decode<User>` gera um
helper `__kof_decode_User` que instancia a classe e atribui os campos.

## Source maps

Cada módulo gera `<name>.mjs.map` (v3) com `sources` e `sourcesContent`.
Precisão linha-a-linha depende de posições na Kof IR (trabalho futuro); o
skeleton já é emitido desde o primeiro backend funcional.

## Exceções

`throw "mensagem"` vira `throw <string>`; `try/catch/finally` é traduzido
para o nativo do JS (o catch-all + rethrow emulado no IR é eliminado porque
o `finally` do JS já cobre a semântica).

## Testes

`KofJsE2ETest` compila `.kf` → `.mjs` → executa na engine embarcada e
compara stdout/exit code. Cobre: hello world, aritmética, variáveis, if/else,
loops, funções, lambdas, classes, construtores, herança, interfaces,
generics, List, String API, arrays, JSON, exceções, ESM, múltiplos arquivos,
kof.time/kof.io.

## Estado atual (alpha)

**Funciona:**
- Pipeline completo `.kf → IR → JS → execução` na engine embarcada
- Classes, herança, construtores, records, interfaces (type-level)
- List, String API, arrays, JSON (encode/decode com binding)
- Exceções (try/catch/finally), lambdas, if-expressões
- kof.time, kof.io (via `kof_platform`), `kof run --target=js`

**Em andamento / futuro:**
- Browser como plataforma (`--platform=browser`, `kof_platform` web)
- `async/await` quando a semântica Kof for definida
- Interoperabilidade (`js.import(...)` — sintaxe futura)
- Source maps precisos (posições na IR)
- HTTP no runtime web

## Debugging

Erros de compilação apontam para o arquivo `.kf` (linha/coluna), nunca para
o `.mjs` gerado. Erros de execução aparecem como mensagens da engine com o
nome da função JS correspondente; o source map permite mapear ao `.kf`
futuramente.