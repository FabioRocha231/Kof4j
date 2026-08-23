# Kof — Roadmap de Longo Prazo

**Última atualização:** 21 de agosto de 2026

---

## Filosofia

Kof deve simplificar radicalmente o desenvolvimento moderno sem sacrificar poder, performance, segurança ou interoperabilidade.

Princípios:

- abstrair complexidade recorrente;
- manter o código extremamente curto e legível;
- oferecer APIs nativas da linguagem/runtime;
- manter compatibilidade com o ecossistema Java existente;
- evitar reinventar bibliotecas Java apenas por estética;
- permitir que Kof ofereça uma experiência moderna sem obrigar o usuário a depender de frameworks externos;
- colocar complexidade na implementação/runtime/compiler, e não no código da aplicação;
- preservar liberdade arquitetural;
- permitir monólitos, modularização e posteriormente microserviços sem reescrever a aplicação inteira.

---

## 1. Targets da Plataforma

### Kof4J — JVM

Kof compilado para JVM/bytecode.

Objetivos:
- máxima compatibilidade com Java;
- acesso a bibliotecas Java;
- compatibilidade com Maven/ecossistema existente;
- execução como JAR;
- possibilidade de utilizar frameworks legados como Spring, Hibernate etc.;
- backend principal durante a consolidação inicial.

Estado atual: ✅ funcional (Fase D concluída)

### KofNative — Binário Nativo

Kof compilado diretamente para código nativo/binário.

Objetivos:
- ELF/PE/Mach-O conforme plataforma;
- baixo consumo;
- startup extremamente rápido;
- possibilidade de servidores sem JVM;
- runtime Kof nativo;
- reutilização da mesma semântica da linguagem;
- mesma aplicação podendo ser compilada para JVM ou Native.

Estado atual: ✅ funcional (Fases E+F concluídas, 381/381 testes passam, JSON parity, exceptions reais no JVM)

### KofJS — Web

Kof executando no lado servidor/compilando para aplicações web.

KofJS NÃO deve ser tratado simplesmente como "Kof que vira JavaScript".

A visão é gerar frontend moderno de forma declarativa e minimalista:

```kof
page Home {
    column {
        text("Olá")
        button("Entrar") {
            login()
        }
    }
}
```

A intenção é semelhante à filosofia do Flutter:
- UI declarativa;
- componentes;
- composição;
- estado;
- eventos;
- layouts;
- pouca verbosidade;
- geração otimizada de HTML/CSS/JS.

Estado atual: 🟡 alpha — pipeline `.kf → Kof IR → KofJS → .mjs` funcional com
execução na engine JS embarcada do próprio Kof (sem Node.js). Classes,
herança, List, String API, JSON, exceções, kof.time/kof.io e `kof run
--target=js` funcionam. A plataforma web (HTML/CSS/JS, browser) é a próxima
fase. Ver: [docs/targets/KOFJS.md](../targets/KOFJS.md).

### KofScript — Execução Direta

Runtime para executar código Kof diretamente.

Comando planejado:

```
kof run arquivo.kf
```

A implementação interna poderá evoluir para interpretação, compilação incremental, JIT ou execução híbrida, mas a decisão será tomada posteriormente com base em benchmarks.

Estado atual: ❌ não implementado

---

## 2. Princípio Multi-Target

A linguagem deve possuir uma semântica única:

```
Source
  ↓
Lexer
  ↓
Parser
  ↓
AST
  ↓
Type System
  ↓
Symbol Resolution
  ↓
Semantic Model
  ↓
Kof IR
  ├── Kof4J Backend
  ├── KofNative Backend
  ├── KofJS Backend
  └── KofScript Runtime
```

A Kof IR deve permanecer independente de JVM, ASM, JavaScript ou código nativo.

Backends são responsáveis por transformar a representação semântica em sua plataforma.

Estado atual: ✅ arquitetura definida e parcialmente implementada

---

## 3. Kof como Plataforma de Backend

A visão de longo prazo é permitir construir backends modernos sem Spring.

Não reimplementar Spring. Em vez disso, transformar capacidades recorrentes em primitivas do Kof Runtime.

Objetivos futuros:
- HTTP / REST / WebSocket / SSE;
- HTTP client;
- JSON;
- RPC;
- eventos / filas / pub/sub;
- concorrência / async;
- cache;
- configuração;
- observabilidade / logging / métricas / tracing;
- health checks / graceful shutdown;
- validation / serialization / scheduling.

Exemplo conceitual:

```kof
api "/users" {
    get "/{id}" {
        return User.find(id)
    }
    post "/" {
        return User.create(input())
    }
}
```

Estado atual: ❌ não implementado

---

## 4. Segurança Nativa

Camada de segurança própria do Kof, inspirada em necessidades resolvidas por Spring Security, mas NÃO como cópia.

Objetivos:
- authentication / authorization;
- JWT / OAuth/OIDC;
- sessions / roles / permissions;
- security policies / CSRF / CORS;
- secure headers / rate limiting;
- input validation / password hashing;
- audit logging / API security.

A filosofia deve ser declarativa e segura por padrão:

```kof
security {
    auth jwt
    route "/admin" requires role("admin")
    route "/users" requires auth
    rate "/login" 10/minute
}
```

Estado atual: ❌ não implementado

---

## 5. Data / ORM / Hibernate

A visão não é substituir Hibernate à força. Kof deve manter Java interoperability e permitir `import org.hibernate.Session`.

Mas deve existir futuramente uma camada de dados nativa:

- SQL / NoSQL / transactions / connection pools;
- migrations / repositories / query APIs;
- PostgreSQL / MySQL / SQLite / MongoDB.

Experiência conceitual:

```kof
entity User {
    id: Long
    name: String
    email: String
}

User.find(id)
User.findAll()
User.save(user)

User.query {
    where age > 18
    orderBy name
}

transaction {
    user.save()
    account.update()
}

sql """
    SELECT * FROM users WHERE active = true
"""
```

Princípio: "Abstração quando ajuda, SQL quando precisa."

Estado atual: ❌ não implementado

---

## 6. Dependency Management

O usuário não deveria precisar editar `pom.xml` diretamente.

Comandos futuros:

```
kof init
kof install lombok
kof remove lombok
kof update
```

Arquivo próprio da linguagem (`kofdeps`). Para Kof4J, o sistema poderá gerar `pom.xml` temporário em memória durante o build e utilizar Maven para resolução/download.

Estado atual: ❌ não implementado

---

## 7. Java Interoperability

A compatibilidade Java é requisito estratégico. Kof deve conseguir utilizar classes, métodos, interfaces, bibliotecas, annotations, Maven artifacts e frameworks legados Java.

A existência de APIs nativas do Kof NÃO deve quebrar essa capacidade.

Regra: "Legado continua funcionando. Kof oferece uma experiência melhor por cima."

Estado atual: ✅ funcional (records, classes, constructors, methods, fields)

---

## 8. Frontend

API de UI declarativa inspirada conceitualmente em Flutter.

Objetivos:
- componentes / composição / layout;
- estado / eventos / routing;
- forms / validation;
- responsive design / accessibility;
- animation / theming.

```kof
column {
    text("Hello")
    button("Click") { action() }
}
button.color = red
button.alignment = center
button.size = 10
```

O KofJS deverá gerar:

```
output/
├── index.html
├── assets/
├── app.js
└── app.css
```

Estado atual: ❌ não implementado

---

## 9. Frontend + Backend no Mesmo Projeto

Um mesmo projeto Kof pode conter backend e frontend. O compilador deve entender os contextos através da estrutura/declarações do projeto.

Shared models/types poderão futuramente ser utilizados nos dois lados.

Estado atual: ❌ não implementado

---

## 10. Arquitetura de Aplicação

Kof não deve impor MVC, Clean Architecture ou Hexagonal Architecture. Deve permitir todas.

```kof
// Simples
main() {
    get "/users" { return User.all() }
}

// Modular
app/
├── domain/
├── application/
├── infrastructure/
└── api/
```

Princípio: "A linguagem fornece primitivas; a arquitetura é escolha do desenvolvedor."

Estado atual: ❌ não implementado

---

## 11. Monólito → Microserviços

A meta é permitir evolução sem reescrita:

```
monolith → modular monolith → services → microservices
```

Compilação `kof build` pode gerar `app.jar` ou `app` nativo. Posteriormente o mesmo projeto pode ser particionado.

Estado atual: ❌ não implementado

---

## 12. Performance

Kof deve permitir implementar aplicações rápidas, eficientes, escaláveis, com baixo consumo e startup rápido.

Regras:
- compile-time > runtime magic;
- type information > reflection;
- generated code > runtime discovery;
- explicit semantics > hidden framework behavior.

Estado atual: ✅ JVM funcional, Native funcional

---

## 13. Observabilidade

APIs nativas para log, metric, trace, health, audit. Integração com OpenTelemetry.

Estado atual: ❌ não implementado

---

## 14. Standard Library / Runtime

Progressivamente:

```
kof-runtime / kof-http / kof-json / kof-data /
kof-security / kof-concurrency / kof-io / kof-ui
```

Mas NÃO criar dezenas de módulos prematuramente. Primeiro definir contratos, tipos e arquitetura.

Estado atual: ❌ não implementado

---

## 15. Roadmap por Fases

### Fase 0 — Consolidação Atual ✅

- parser;
- type system;
- symbol resolution;
- semantic model;
- Kof IR;
- JVM backend;
- Native backend (concluído).

### Fase F — Runtime + Object Model ✅

- auditoria do runtime atual ✅
- Kof Runtime ABI definida ✅
- Object Model definido ✅
- ClassLayout / FieldLayout centralizados ✅
- NativeRuntime (kof_alloc, kof_panic, etc.) ✅
- NativeBackend refatorado (heap alloc, constructors, KofDup) ✅
- **Fase F.1 — String Model:** ✅
  - BuiltinTypes.STRING centralizado ✅
  - KofString layout (type_id, flags, length, UTF-8 data) ✅
  - kof_string_from_literal ✅
  - kof_string_length ✅
  - kof_string_concat ✅
  - kof_string_equals ✅
  - kof_print_string / kof_println_string ✅
  - NativeBackend usa KofString para literals ✅
  - STRING_MODEL.md documentado ✅
- **Fase F.2 — Array Model:** ✅
  - ArrayType no Type System ✅
  - NewArrayExpr + ArrayAccessExpr no AST ✅
  - Parser: new Type[size], expr[expr], expr.length ✅
  - SemanticAnalyzer: type checking de arrays ✅
  - CompilerDriver: lowering para KofNewArray/KofArrayLoad/KofArrayStore/KofArrayLength ✅
  - NativeRuntime: kof_array_alloc, kof_array_length, kof_array_get, kof_array_set ✅
  - NativeBackend: lowering completo das operações de array ✅
  - JVM Backend: NEWARRAY/IALOAD/IASTORE/ARRAYLENGTH ✅
  - ARRAY_MODEL.md documentado ✅
  - 25 novos testes (criação, acesso, length, long, string, loop, argumento, retorno, vazio) ✅
- **Fase F.3 — Inheritance:** ✅
  - SemanticAnalyzer: resolveInHierarchy() caminha cadeia de superclasses ✅
  - ClassLayout: buildWithSuper() inclui fields herdados ✅
  - NativeBackend: allClassesMap para resolver superclasses ✅
  - CompilerDriver: super(args) com argumentos, findSuperClass() ✅
  - Constructor chaining com super(args) explícito ✅
  - Acesso a fields e métodos herdados ✅
  - Herança de 3 níveis ✅
  - INHERITANCE_MODEL.md documentado ✅
  - 20 novos testes (subclasse, fields herdados, methods herdados, constructor chaining, 3 níveis) ✅
- **Fase F.4 — Virtual Dispatch:** ✅
  - Object header estendido: 8 → 16 bytes (type_id + flags + method_table_ptr) ✅
  - Method tables geradas por classe ✅
  - kof_init_object para inicializar header ✅
  - Virtual dispatch via vtable no NativeBackend ✅
  - JVM usa INVOKEVIRTUAL nativo ✅
  - Parser: suporte a `ClassName varName = value` ✅
  - CompilerDriver: NewExpr no inferExprType ✅
  - VIRTUAL_DISPATCH.md documentado ✅
  - 11 novos testes (override, polymorphism, 3 níveis, slots) ✅
- **Fase F.5 — Interfaces:** ✅
  - KofCallKind.INTERFACE na IR ✅
  - Parser: interface declaration + implements ✅
  - SemanticAnalyzer: isInterfaceType(), resolveInHierarchy() caminha interfaces ✅
  - CompilerDriver: define KofCallKind.INTERFACE para chamadas via interface ✅
  - JvmBackend: INVOKEINTERFACE ✅
  - NativeBackend: dispatch via vtable para interfaces ✅
  - INTERFACES_MODEL.md documentado ✅
  - 13 novos testes ✅
- **Fase F.6 — Exceptions/Runtime Errors:** ✅
  - AST: ThrowStmt, TryStmt, CatchClause ✅
  - Parser: try/catch/finally ✅
  - IR: KofThrow ✅
  - JvmBackend: ATHROW ✅
  - NativeBackend: kof_panic para throw ✅
  - Runtime errors: kof_null_error, kof_bounds_error ✅
  - EXCEPTIONS_MODEL.md documentado ✅
  - 14 novos testes ✅
- **Fase F.7 — Memory Management:** ✅
  - kof_alloc com tracking de alocações ✅
  - kof_free (no-op, documentado) ✅
  - kof_memstats para debug ✅
  - MEMORY_MODEL.md documentado ✅
> **Atualizado (0.0.5):** interfaces (F.5), exceptions reais (F.6, JVM +
> Native unwinding) e memory management (allocator com header, kof_free
> funcional) estão implementados.

### Fase 1 — Core

- runtime (consolidação);
- standard types;
- collections;
- IO;
- errors/exceptions;
- concurrency;
- serialization.

### Fase 2 — Developer Experience

- `kof init` / `kofdeps` / `kof install` / `kof remove`;
- `kof update` / `kof check` / `kof fmt` / `kof test` / `kof clean`;
- REPL / LSP.

### Fase 3 — Web Platform (`kof serve`)

- syscalls de rede no NativeRuntime (socket, bind, listen, accept, read, write, close) ✅;
- `kof serve` command no CLI ✅;
- KofHttpServer (thread pool, Content-Length, query, headers, 404/500) ✅;
- `kof serve` com handlers top-level (`handle(...)`) ✅;
- JSON serialization (`json.encode`/`json.decode`) ✅;
- 8 testes E2E in-process (sockets reais) ✅;
- Documentação (`docs/http.md`) ✅;
- Path parameters (pendente — routing explícito no handler).

### Fase 4 — Security

- auth / authorization / JWT / OAuth/OIDC;
- sessions / policies / rate limiting;
- security defaults / audit.

### Fase 5 — KofJS

- frontend / declarative UI / components;
- state / routing / forms / SSR;
- HTML/CSS/JS generation.

### Fase 6 — KofScript

- direct execution / fast startup;
- REPL / incremental execution / scripting APIs.

### Fase 7 — Native Completo

- full language support / native runtime;
- networking / database / security;
- production server support.

### Fase 8 — Maturidade da Plataforma

- distributed systems / service discovery;
- messaging / RPC;
- observability / deployment / cloud integrations.

---

## 16. Não Fazer

- não copiar Spring;
- não copiar Hibernate;
- não criar um framework monolítico gigante;
- não adicionar annotations para tudo;
- não depender de reflection quando compile-time for suficiente;
- não acoplar o core à JVM;
- não criar APIs específicas de um backend dentro da linguagem;
- não sacrificar Java interoperability;
- não implementar features gigantes antes de consolidar o core;
- não transformar cada problema em um novo módulo;
- não adicionar complexidade só porque outras linguagens fazem assim.

---

## 17. Distribuição e Tooling (0.0.4+)

O Kof é uma plataforma distribuível, não apenas um JAR:

- distribuição autocontida (compiler, CLI, runtime, stdlib, tooling, editor support, JDK embutido);
- OpenJDK embutido no pacote oficial (Temurin 21, Tooling API Level 21);
- versionamento centralizado (`VERSION` → pom/properties via `scripts/bump-version.sh`);
- releases automáticas por push na `main` (testes → bump → package → GitHub Release);
- artefatos multiplataforma + SHA256SUMS;
- editor support oficial: grammar TextMate + LSP consumindo o frontend real;
- `kof info`, `kof check`, `kof lsp`, `kof test` ✅; `kof fmt` planejado.

Referências: `docs/distribution/`, `docs/tooling/`.

---

## 18. Kof Escrito em Kof (auto-hospedagem)

Planejado desde já como evolução arquitetural real, não demonstração.

Pré-requisitos antes da migração:

- generics; collections; exceptions;
- stdlib; filesystem; strings; concurrency; HTTP;
- tooling; expressividade suficiente da linguagem.

O compilador atual permanece arquiteturalmente preparado para a migração
(frontend único alimentando compiler, LSP, formatter e diagnostics), mas a
migração **não** deve ser tentada prematuramente.

---

## 19. Kof + LLM

Kof é *Human First, LLM Friendly by Consequence*:

- menos ceremony; menos arquivos; menos abstrações artificiais;
- menos configuração; mais intenção.

A consistência do design faz com que humanos e LLMs entendam a mesma
linguagem da mesma forma. O diretório `training/` é parte oficial dessa
estratégia.

---

## 20. Princípios de Design

1. Simplicidade primeiro.
2. Legibilidade primeiro.
3. Compile-time sempre que possível.
4. Runtime pequeno e previsível.
5. Segurança por padrão.
6. Performance mensurável.
7. Interoperabilidade sem compromisso.
8. Abstrações nativas para problemas recorrentes.
9. Escape hatches sempre disponíveis.
10. Uma linguagem, múltiplos targets.
11. Monólito e microserviços devem ser escolhas arquiteturais, não limitações da linguagem.
12. O código deve expressar intenção, não infraestrutura.
13. Kof deve esconder complexidade sem esconder poder.
14. Compatibilidade com legado é feature.
15. Nenhuma decisão futura deve quebrar o core agnostic da linguagem.

---

## 21. Legacy Migration Platform (plano futuro)

Iniciativa de longo prazo para analisar, recuperar, traduzir e modernizar
sistemas legados para Kof — **fora do escopo 0.0.x**.

- Documento central: `LEGACY_MIGRATION.md`
- Componentes planejados: `kof inspect`, `kof decompile`, `kof translate`,
  `kof migrate`, `kof compare`
- Arquitetura: `Legacy Input → Legacy Semantic IR → Kof AST → Kof IR → Backend`
- Java é origem suportada, nunca representação intermediária obrigatória
- Documentos relacionados: `DECOMPILER.md`, `TRANSLATOR.md`, `LEGACY_IR.md`,
  `DIFFERENTIAL_TESTING.md`

**Não implementar nada desta seção antes da consolidação da linguagem,
compilador, runtime, stdlib e tooling.**
