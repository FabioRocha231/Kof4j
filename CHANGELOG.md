# Changelog

Todas as mudanças relevantes do Kof são registradas aqui.

O formato segue [Keep a Changelog](https://keepachangelog.com/) com a convenção
de commits do projeto (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`,
`build:`, `tooling:`). A seção de cada release é gerada por
`scripts/changelog.sh` e inserida pela pipeline neste marcador:

## [0.0.5-alpha] - 2026-08-22

### Features

  - KofJS backend (alpha) — same Kof IR lowered to ECMAScript 2022+ ESM modules
  - embedded JS engine (GraalJS) — `kof run --target=js` executes without Node.js
  - KofJS runtime layers — kof-runtime.mjs (core) + kof-runtime-io.mjs (platform via kof_platform)
  - KofJS classes, records, inheritance, interfaces (type-level), generics erasure
  - KofJS List, String API, arrays, JSON (encode/decode with class binding)
  - KofJS exceptions (try/catch/finally), lambdas, if-expressions, source maps
  - record-style class syntax — `class User(String name)` same semantics as record
  - generic return types in function declarations (e.g. `List<Int> ints()`)
  - KofJsE2ETest suite — .kf → .mjs → embedded engine → stdout/exit code
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.6-alpha] - 2026-08-22

### Features

  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.7-alpha] - 2026-08-23

### Features

  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - update local build instructions with lib/kof.jar workaround
  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.6-alpha [skip ci]
  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.8-alpha] - 2026-08-23

### Features

  - JSON completo (Float/Double, arrays), logging estruturado, kof.db (JDBC + transactions)
  - enhance process output handling with virtual threads
  - enhance kof.ui documentation and add KofJS details
  - add kof.ui section to documentation with UI rendering details and widget descriptions
  - update documentation for UI components, add window and widget examples
  - multiple windows, window size and close-to-exit
  - add window size adjustment functionality in KofUi
  - add support for font size, bold, and color properties in KofUi labels
  - add label styling and window theme support in KofUi and related backends
  - Introduce new UI components and bindings for Input, Column, Row, View, and Style
  - enhance KofJsRunner to support program arguments and update related components
  - update documentation and fix issues in Kof Spring Starter phases, enhance runtime functions
  - implement native configuration and logging modules, update documentation
  - update documentation and enhance semantic analysis for config and logging namespaces
  - enhance KofJsRunner output handling and add webview settings for file access
  - implement native web stack with routing, middleware, and JSON support
  - enhance Kof compiler and runtime with new features and bug fixes
  - native webview shell — kof-webview (WebKitGTK embedded)
  - kof.ui webview — DOM shim, HTML serialization, system webview
  - enhance KofJsRunner to support window rendering and HTML capture
  - introduce kof.security module for password hashing, JWT, and cryptography
  - kof-debug MVP completo — breakpoints por linha Kof + stack trace
  - kof.ui Window and Label — webview container with binding
  - kof.ui foundation — Color, Palette, Theme + main(args)
  - add kof.ui foundation with Color, Palette, and Theme support
  - enhance benchmarking with JS target and add CPU time tracking
  - debugger Fase 2 — LocalVariableTable no JVM
  - Kof debugger — Fase 1 (DebugInfo na IR) + docs + JVM line metadata
  - add debug information support with source file and line number mapping in JVM backend
  - enhance IRModule and backend to support source name and debugging information
  - implement Kof debugging support with source mapping and debug metadata
  - add initializer support for record components and enhance semantic analysis
  - idiomatic core — field initializers applied, \uXXXX escapes, typed listOf<T>()
  - implement increment operations with correct semantics and add tests for idiomatic behavior
  - implement generics in Kof with examples for lists and sets
  - enhance method symbol to allow dynamic return type updates and improve semantic analysis
  - refactor semantic analysis by defining constructor and method symbols, and analyzing their bodies
  - add Color class with ARGB semantics and enhance color handling in the compiler
  - enhance literal parsing and add hexadecimal support in lexer
  - Fase L — release gate hardened + package revalidated
  - Fase K — assert primitive + expanded golden + kof test integration
  - implement assertion handling with AssertE2ETest and add various test cases for control flow, functions, and records
  - add AssertStmt for assertion handling and update lexer and token types
  - Fase J — LSP textDocument/didClose clears diagnostics
  - add KofJS backend and runtime support, including parity tests for JVM and JS
  - Enhance parsing and runtime capabilities with new if-expression handling and runtime options
  - kof test — run programs and report PASS/FAIL by exit code
  - Fase I — spawn: concurrent tasks on the JVM (virtual threads)
  - Introduce kof.io filesystem API for file and directory operations
  - kof.io documentation, multiplatform CI and platform guard
  - Fase J — LSP URI fix + editor grammar builtins
  - Fase I+L — concurrency semantics design + distribution validation
  - Fase K — real golden and integration test infrastructure
  - Enhance KofJS backend with improved function handling and module support
  - Implement Kof HTTP server and I/O library
  - idioms corpus, anti-pattern catalog, datasets, corrections
  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - update expected output for label style binding in JS target
  - guard kofUiButtonRemove against missing action registry
  - update ClassPrepare event kind and improve event logging in JdwpClient
  - sound IR optimizer, JS switch routing and list construction
  - field initializers, record defaults and increment semantics
  - idiomatic core — name resolution by symbol, return inference, this-free fields
  - bool semantics parity — 0/1 results, true/false formatting, Multi-Release shade
  - restore kof_io_ dispatch in JVM runtime helper
  - JVM constructor super detection, List<ref> checkcast, kof.List descriptor
  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - README e status finais (513/513, kof.db, JSON completo)
  - document the intent-oriented paradigm with honest framing
  - update local build instructions with lib/kof.jar workaround
  - document the kof.ui platform (widgets, events, webview)
  - auditoria do ecossistema da stdlib — matriz de cobertura (G1-G12)
  - debugger — Fases 1-3 implementadas (kof-debug MVP validado)
  - status — debugger Fases 1-2 (DebugInfo na IR, JVM metadata)
  - status — 394 testes, guidelines idiomáticas e estado real
  - fake-idioms — primary constructor is implemented (record-style since 0.0.5)
  - sync all .md with real 0.0.5 state
  - reorganize — move completed docs out of future/
  - status — 375/375, KofJS 100% (GraalJS embutido)
  - status — kof.io filesystem API, kof test, current test state
  - status — Fases H/J/K/L concluídas, I design pronto
  - Legacy Migration Platform architecture
  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.7-alpha [skip ci]
  - rebuild kof-webview with file:// module CORS fix
  - bump version to 0.0.6-alpha [skip ci]
  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.9-alpha] - 2026-08-24

### Features

  - implement MySQL authentication scramble using SHA-1
  - MySQL/MariaDB via wire protocol sobre sockets nativos (WIP)
  - add hidden easter egg registry and corresponding tests
  - native kof.db with SQLite via direct .so linking (no JDBC driver)
  - enhance string replacement functionality and content type handling in HTTP server
  - enhance string replace functionality and constructor handling in backends
  - JSON completo (Float/Double, arrays), logging estruturado, kof.db (JDBC + transactions)
  - enhance process output handling with virtual threads
  - enhance kof.ui documentation and add KofJS details
  - add kof.ui section to documentation with UI rendering details and widget descriptions
  - update documentation for UI components, add window and widget examples
  - multiple windows, window size and close-to-exit
  - add window size adjustment functionality in KofUi
  - add support for font size, bold, and color properties in KofUi labels
  - add label styling and window theme support in KofUi and related backends
  - Introduce new UI components and bindings for Input, Column, Row, View, and Style
  - enhance KofJsRunner to support program arguments and update related components
  - update documentation and fix issues in Kof Spring Starter phases, enhance runtime functions
  - implement native configuration and logging modules, update documentation
  - update documentation and enhance semantic analysis for config and logging namespaces
  - enhance KofJsRunner output handling and add webview settings for file access
  - implement native web stack with routing, middleware, and JSON support
  - enhance Kof compiler and runtime with new features and bug fixes
  - native webview shell — kof-webview (WebKitGTK embedded)
  - kof.ui webview — DOM shim, HTML serialization, system webview
  - enhance KofJsRunner to support window rendering and HTML capture
  - introduce kof.security module for password hashing, JWT, and cryptography
  - kof-debug MVP completo — breakpoints por linha Kof + stack trace
  - kof.ui Window and Label — webview container with binding
  - kof.ui foundation — Color, Palette, Theme + main(args)
  - add kof.ui foundation with Color, Palette, and Theme support
  - enhance benchmarking with JS target and add CPU time tracking
  - debugger Fase 2 — LocalVariableTable no JVM
  - Kof debugger — Fase 1 (DebugInfo na IR) + docs + JVM line metadata
  - add debug information support with source file and line number mapping in JVM backend
  - enhance IRModule and backend to support source name and debugging information
  - implement Kof debugging support with source mapping and debug metadata
  - add initializer support for record components and enhance semantic analysis
  - idiomatic core — field initializers applied, \uXXXX escapes, typed listOf<T>()
  - implement increment operations with correct semantics and add tests for idiomatic behavior
  - implement generics in Kof with examples for lists and sets
  - enhance method symbol to allow dynamic return type updates and improve semantic analysis
  - refactor semantic analysis by defining constructor and method symbols, and analyzing their bodies
  - add Color class with ARGB semantics and enhance color handling in the compiler
  - enhance literal parsing and add hexadecimal support in lexer
  - Fase L — release gate hardened + package revalidated
  - Fase K — assert primitive + expanded golden + kof test integration
  - implement assertion handling with AssertE2ETest and add various test cases for control flow, functions, and records
  - add AssertStmt for assertion handling and update lexer and token types
  - Fase J — LSP textDocument/didClose clears diagnostics
  - add KofJS backend and runtime support, including parity tests for JVM and JS
  - Enhance parsing and runtime capabilities with new if-expression handling and runtime options
  - kof test — run programs and report PASS/FAIL by exit code
  - Fase I — spawn: concurrent tasks on the JVM (virtual threads)
  - Introduce kof.io filesystem API for file and directory operations
  - kof.io documentation, multiplatform CI and platform guard
  - Fase J — LSP URI fix + editor grammar builtins
  - Fase I+L — concurrency semantics design + distribution validation
  - Fase K — real golden and integration test infrastructure
  - Enhance KofJS backend with improved function handling and module support
  - Implement Kof HTTP server and I/O library
  - idioms corpus, anti-pattern catalog, datasets, corrections
  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - enhance try-finally parsing logic to correctly handle labels and control flow
  - enhance MySQL connection detection and linker command for conditional library inclusion
  - add --as-needed flag to linker command for improved dependency handling
  - update output handling in various E2E tests for consistent UTF-8 encoding and line endings
  - update file path handling for cross-platform compatibility and enhance test process encoding
  - golden tests need the CLI jar; launcher must not break JDK 21
  - update expected output for label style binding in JS target
  - guard kofUiButtonRemove against missing action registry
  - update ClassPrepare event kind and improve event logging in JdwpClient
  - sound IR optimizer, JS switch routing and list construction
  - field initializers, record defaults and increment semantics
  - idiomatic core — name resolution by symbol, return inference, this-free fields
  - bool semantics parity — 0/1 results, true/false formatting, Multi-Release shade
  - restore kof_io_ dispatch in JVM runtime helper
  - JVM constructor super detection, List<ref> checkcast, kof.List descriptor
  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - README e status finais (513/513, kof.db, JSON completo)
  - document the intent-oriented paradigm with honest framing
  - update local build instructions with lib/kof.jar workaround
  - document the kof.ui platform (widgets, events, webview)
  - auditoria do ecossistema da stdlib — matriz de cobertura (G1-G12)
  - debugger — Fases 1-3 implementadas (kof-debug MVP validado)
  - status — debugger Fases 1-2 (DebugInfo na IR, JVM metadata)
  - status — 394 testes, guidelines idiomáticas e estado real
  - fake-idioms — primary constructor is implemented (record-style since 0.0.5)
  - sync all .md with real 0.0.5 state
  - reorganize — move completed docs out of future/
  - status — 375/375, KofJS 100% (GraalJS embutido)
  - status — kof.io filesystem API, kof test, current test state
  - status — Fases H/J/K/L concluídas, I design pronto
  - Legacy Migration Platform architecture
  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.8-alpha [skip ci]
  - bump version to 0.0.7-alpha [skip ci]
  - rebuild kof-webview with file:// module CORS fix
  - bump version to 0.0.6-alpha [skip ci]
  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.10-alpha] - 2026-08-24

### Features

  - implement MySQL authentication scramble using SHA-1
  - MySQL/MariaDB via wire protocol sobre sockets nativos (WIP)
  - add hidden easter egg registry and corresponding tests
  - native kof.db with SQLite via direct .so linking (no JDBC driver)
  - enhance string replacement functionality and content type handling in HTTP server
  - enhance string replace functionality and constructor handling in backends
  - JSON completo (Float/Double, arrays), logging estruturado, kof.db (JDBC + transactions)
  - enhance process output handling with virtual threads
  - enhance kof.ui documentation and add KofJS details
  - add kof.ui section to documentation with UI rendering details and widget descriptions
  - update documentation for UI components, add window and widget examples
  - multiple windows, window size and close-to-exit
  - add window size adjustment functionality in KofUi
  - add support for font size, bold, and color properties in KofUi labels
  - add label styling and window theme support in KofUi and related backends
  - Introduce new UI components and bindings for Input, Column, Row, View, and Style
  - enhance KofJsRunner to support program arguments and update related components
  - update documentation and fix issues in Kof Spring Starter phases, enhance runtime functions
  - implement native configuration and logging modules, update documentation
  - update documentation and enhance semantic analysis for config and logging namespaces
  - enhance KofJsRunner output handling and add webview settings for file access
  - implement native web stack with routing, middleware, and JSON support
  - enhance Kof compiler and runtime with new features and bug fixes
  - native webview shell — kof-webview (WebKitGTK embedded)
  - kof.ui webview — DOM shim, HTML serialization, system webview
  - enhance KofJsRunner to support window rendering and HTML capture
  - introduce kof.security module for password hashing, JWT, and cryptography
  - kof-debug MVP completo — breakpoints por linha Kof + stack trace
  - kof.ui Window and Label — webview container with binding
  - kof.ui foundation — Color, Palette, Theme + main(args)
  - add kof.ui foundation with Color, Palette, and Theme support
  - enhance benchmarking with JS target and add CPU time tracking
  - debugger Fase 2 — LocalVariableTable no JVM
  - Kof debugger — Fase 1 (DebugInfo na IR) + docs + JVM line metadata
  - add debug information support with source file and line number mapping in JVM backend
  - enhance IRModule and backend to support source name and debugging information
  - implement Kof debugging support with source mapping and debug metadata
  - add initializer support for record components and enhance semantic analysis
  - idiomatic core — field initializers applied, \uXXXX escapes, typed listOf<T>()
  - implement increment operations with correct semantics and add tests for idiomatic behavior
  - implement generics in Kof with examples for lists and sets
  - enhance method symbol to allow dynamic return type updates and improve semantic analysis
  - refactor semantic analysis by defining constructor and method symbols, and analyzing their bodies
  - add Color class with ARGB semantics and enhance color handling in the compiler
  - enhance literal parsing and add hexadecimal support in lexer
  - Fase L — release gate hardened + package revalidated
  - Fase K — assert primitive + expanded golden + kof test integration
  - implement assertion handling with AssertE2ETest and add various test cases for control flow, functions, and records
  - add AssertStmt for assertion handling and update lexer and token types
  - Fase J — LSP textDocument/didClose clears diagnostics
  - add KofJS backend and runtime support, including parity tests for JVM and JS
  - Enhance parsing and runtime capabilities with new if-expression handling and runtime options
  - kof test — run programs and report PASS/FAIL by exit code
  - Fase I — spawn: concurrent tasks on the JVM (virtual threads)
  - Introduce kof.io filesystem API for file and directory operations
  - kof.io documentation, multiplatform CI and platform guard
  - Fase J — LSP URI fix + editor grammar builtins
  - Fase I+L — concurrency semantics design + distribution validation
  - Fase K — real golden and integration test infrastructure
  - Enhance KofJS backend with improved function handling and module support
  - Implement Kof HTTP server and I/O library
  - idioms corpus, anti-pattern catalog, datasets, corrections
  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - CI multiplataforma + kof.db link seletivo + JS try/finally + package Adoptium
  - enhance try-finally parsing logic to correctly handle labels and control flow
  - enhance MySQL connection detection and linker command for conditional library inclusion
  - add --as-needed flag to linker command for improved dependency handling
  - update output handling in various E2E tests for consistent UTF-8 encoding and line endings
  - update file path handling for cross-platform compatibility and enhance test process encoding
  - golden tests need the CLI jar; launcher must not break JDK 21
  - update expected output for label style binding in JS target
  - guard kofUiButtonRemove against missing action registry
  - update ClassPrepare event kind and improve event logging in JdwpClient
  - sound IR optimizer, JS switch routing and list construction
  - field initializers, record defaults and increment semantics
  - idiomatic core — name resolution by symbol, return inference, this-free fields
  - bool semantics parity — 0/1 results, true/false formatting, Multi-Release shade
  - restore kof_io_ dispatch in JVM runtime helper
  - JVM constructor super detection, List<ref> checkcast, kof.List descriptor
  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - README e status finais (513/513, kof.db, JSON completo)
  - document the intent-oriented paradigm with honest framing
  - update local build instructions with lib/kof.jar workaround
  - document the kof.ui platform (widgets, events, webview)
  - auditoria do ecossistema da stdlib — matriz de cobertura (G1-G12)
  - debugger — Fases 1-3 implementadas (kof-debug MVP validado)
  - status — debugger Fases 1-2 (DebugInfo na IR, JVM metadata)
  - status — 394 testes, guidelines idiomáticas e estado real
  - fake-idioms — primary constructor is implemented (record-style since 0.0.5)
  - sync all .md with real 0.0.5 state
  - reorganize — move completed docs out of future/
  - status — 375/375, KofJS 100% (GraalJS embutido)
  - status — kof.io filesystem API, kof test, current test state
  - status — Fases H/J/K/L concluídas, I design pronto
  - Legacy Migration Platform architecture
  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.9-alpha [skip ci]
  - bump version to 0.0.8-alpha [skip ci]
  - bump version to 0.0.7-alpha [skip ci]
  - rebuild kof-webview with file:// module CORS fix
  - bump version to 0.0.6-alpha [skip ci]
  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.11-alpha] - 2026-08-24

### Features

  - implement MySQL authentication scramble using SHA-1
  - MySQL/MariaDB via wire protocol sobre sockets nativos (WIP)
  - add hidden easter egg registry and corresponding tests
  - native kof.db with SQLite via direct .so linking (no JDBC driver)
  - enhance string replacement functionality and content type handling in HTTP server
  - enhance string replace functionality and constructor handling in backends
  - JSON completo (Float/Double, arrays), logging estruturado, kof.db (JDBC + transactions)
  - enhance process output handling with virtual threads
  - enhance kof.ui documentation and add KofJS details
  - add kof.ui section to documentation with UI rendering details and widget descriptions
  - update documentation for UI components, add window and widget examples
  - multiple windows, window size and close-to-exit
  - add window size adjustment functionality in KofUi
  - add support for font size, bold, and color properties in KofUi labels
  - add label styling and window theme support in KofUi and related backends
  - Introduce new UI components and bindings for Input, Column, Row, View, and Style
  - enhance KofJsRunner to support program arguments and update related components
  - update documentation and fix issues in Kof Spring Starter phases, enhance runtime functions
  - implement native configuration and logging modules, update documentation
  - update documentation and enhance semantic analysis for config and logging namespaces
  - enhance KofJsRunner output handling and add webview settings for file access
  - implement native web stack with routing, middleware, and JSON support
  - enhance Kof compiler and runtime with new features and bug fixes
  - native webview shell — kof-webview (WebKitGTK embedded)
  - kof.ui webview — DOM shim, HTML serialization, system webview
  - enhance KofJsRunner to support window rendering and HTML capture
  - introduce kof.security module for password hashing, JWT, and cryptography
  - kof-debug MVP completo — breakpoints por linha Kof + stack trace
  - kof.ui Window and Label — webview container with binding
  - kof.ui foundation — Color, Palette, Theme + main(args)
  - add kof.ui foundation with Color, Palette, and Theme support
  - enhance benchmarking with JS target and add CPU time tracking
  - debugger Fase 2 — LocalVariableTable no JVM
  - Kof debugger — Fase 1 (DebugInfo na IR) + docs + JVM line metadata
  - add debug information support with source file and line number mapping in JVM backend
  - enhance IRModule and backend to support source name and debugging information
  - implement Kof debugging support with source mapping and debug metadata
  - add initializer support for record components and enhance semantic analysis
  - idiomatic core — field initializers applied, \uXXXX escapes, typed listOf<T>()
  - implement increment operations with correct semantics and add tests for idiomatic behavior
  - implement generics in Kof with examples for lists and sets
  - enhance method symbol to allow dynamic return type updates and improve semantic analysis
  - refactor semantic analysis by defining constructor and method symbols, and analyzing their bodies
  - add Color class with ARGB semantics and enhance color handling in the compiler
  - enhance literal parsing and add hexadecimal support in lexer
  - Fase L — release gate hardened + package revalidated
  - Fase K — assert primitive + expanded golden + kof test integration
  - implement assertion handling with AssertE2ETest and add various test cases for control flow, functions, and records
  - add AssertStmt for assertion handling and update lexer and token types
  - Fase J — LSP textDocument/didClose clears diagnostics
  - add KofJS backend and runtime support, including parity tests for JVM and JS
  - Enhance parsing and runtime capabilities with new if-expression handling and runtime options
  - kof test — run programs and report PASS/FAIL by exit code
  - Fase I — spawn: concurrent tasks on the JVM (virtual threads)
  - Introduce kof.io filesystem API for file and directory operations
  - kof.io documentation, multiplatform CI and platform guard
  - Fase J — LSP URI fix + editor grammar builtins
  - Fase I+L — concurrency semantics design + distribution validation
  - Fase K — real golden and integration test infrastructure
  - Enhance KofJS backend with improved function handling and module support
  - Implement Kof HTTP server and I/O library
  - idioms corpus, anti-pattern catalog, datasets, corrections
  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - launcher e validate usam o JDK embarcado em todas as plataformas
  - CI multiplataforma + kof.db link seletivo + JS try/finally + package Adoptium
  - enhance try-finally parsing logic to correctly handle labels and control flow
  - enhance MySQL connection detection and linker command for conditional library inclusion
  - add --as-needed flag to linker command for improved dependency handling
  - update output handling in various E2E tests for consistent UTF-8 encoding and line endings
  - update file path handling for cross-platform compatibility and enhance test process encoding
  - golden tests need the CLI jar; launcher must not break JDK 21
  - update expected output for label style binding in JS target
  - guard kofUiButtonRemove against missing action registry
  - update ClassPrepare event kind and improve event logging in JdwpClient
  - sound IR optimizer, JS switch routing and list construction
  - field initializers, record defaults and increment semantics
  - idiomatic core — name resolution by symbol, return inference, this-free fields
  - bool semantics parity — 0/1 results, true/false formatting, Multi-Release shade
  - restore kof_io_ dispatch in JVM runtime helper
  - JVM constructor super detection, List<ref> checkcast, kof.List descriptor
  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - DATABASE_VISION — nível 0 do kof.db implementado (JDBC idiomático JVM, SQLite nativo, MySQL WIP); níveis 1-4 seguem a visão
  - README e status finais (513/513, kof.db, JSON completo)
  - document the intent-oriented paradigm with honest framing
  - update local build instructions with lib/kof.jar workaround
  - document the kof.ui platform (widgets, events, webview)
  - auditoria do ecossistema da stdlib — matriz de cobertura (G1-G12)
  - debugger — Fases 1-3 implementadas (kof-debug MVP validado)
  - status — debugger Fases 1-2 (DebugInfo na IR, JVM metadata)
  - status — 394 testes, guidelines idiomáticas e estado real
  - fake-idioms — primary constructor is implemented (record-style since 0.0.5)
  - sync all .md with real 0.0.5 state
  - reorganize — move completed docs out of future/
  - status — 375/375, KofJS 100% (GraalJS embutido)
  - status — kof.io filesystem API, kof test, current test state
  - status — Fases H/J/K/L concluídas, I design pronto
  - Legacy Migration Platform architecture
  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.10-alpha [skip ci]
  - bump version to 0.0.9-alpha [skip ci]
  - bump version to 0.0.8-alpha [skip ci]
  - bump version to 0.0.7-alpha [skip ci]
  - rebuild kof-webview with file:// module CORS fix
  - bump version to 0.0.6-alpha [skip ci]
  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.12-alpha] - 2026-08-24

### Features

  - implement MySQL authentication scramble using SHA-1
  - MySQL/MariaDB via wire protocol sobre sockets nativos (WIP)
  - add hidden easter egg registry and corresponding tests
  - native kof.db with SQLite via direct .so linking (no JDBC driver)
  - enhance string replacement functionality and content type handling in HTTP server
  - enhance string replace functionality and constructor handling in backends
  - JSON completo (Float/Double, arrays), logging estruturado, kof.db (JDBC + transactions)
  - enhance process output handling with virtual threads
  - enhance kof.ui documentation and add KofJS details
  - add kof.ui section to documentation with UI rendering details and widget descriptions
  - update documentation for UI components, add window and widget examples
  - multiple windows, window size and close-to-exit
  - add window size adjustment functionality in KofUi
  - add support for font size, bold, and color properties in KofUi labels
  - add label styling and window theme support in KofUi and related backends
  - Introduce new UI components and bindings for Input, Column, Row, View, and Style
  - enhance KofJsRunner to support program arguments and update related components
  - update documentation and fix issues in Kof Spring Starter phases, enhance runtime functions
  - implement native configuration and logging modules, update documentation
  - update documentation and enhance semantic analysis for config and logging namespaces
  - enhance KofJsRunner output handling and add webview settings for file access
  - implement native web stack with routing, middleware, and JSON support
  - enhance Kof compiler and runtime with new features and bug fixes
  - native webview shell — kof-webview (WebKitGTK embedded)
  - kof.ui webview — DOM shim, HTML serialization, system webview
  - enhance KofJsRunner to support window rendering and HTML capture
  - introduce kof.security module for password hashing, JWT, and cryptography
  - kof-debug MVP completo — breakpoints por linha Kof + stack trace
  - kof.ui Window and Label — webview container with binding
  - kof.ui foundation — Color, Palette, Theme + main(args)
  - add kof.ui foundation with Color, Palette, and Theme support
  - enhance benchmarking with JS target and add CPU time tracking
  - debugger Fase 2 — LocalVariableTable no JVM
  - Kof debugger — Fase 1 (DebugInfo na IR) + docs + JVM line metadata
  - add debug information support with source file and line number mapping in JVM backend
  - enhance IRModule and backend to support source name and debugging information
  - implement Kof debugging support with source mapping and debug metadata
  - add initializer support for record components and enhance semantic analysis
  - idiomatic core — field initializers applied, \uXXXX escapes, typed listOf<T>()
  - implement increment operations with correct semantics and add tests for idiomatic behavior
  - implement generics in Kof with examples for lists and sets
  - enhance method symbol to allow dynamic return type updates and improve semantic analysis
  - refactor semantic analysis by defining constructor and method symbols, and analyzing their bodies
  - add Color class with ARGB semantics and enhance color handling in the compiler
  - enhance literal parsing and add hexadecimal support in lexer
  - Fase L — release gate hardened + package revalidated
  - Fase K — assert primitive + expanded golden + kof test integration
  - implement assertion handling with AssertE2ETest and add various test cases for control flow, functions, and records
  - add AssertStmt for assertion handling and update lexer and token types
  - Fase J — LSP textDocument/didClose clears diagnostics
  - add KofJS backend and runtime support, including parity tests for JVM and JS
  - Enhance parsing and runtime capabilities with new if-expression handling and runtime options
  - kof test — run programs and report PASS/FAIL by exit code
  - Fase I — spawn: concurrent tasks on the JVM (virtual threads)
  - Introduce kof.io filesystem API for file and directory operations
  - kof.io documentation, multiplatform CI and platform guard
  - Fase J — LSP URI fix + editor grammar builtins
  - Fase I+L — concurrency semantics design + distribution validation
  - Fase K — real golden and integration test infrastructure
  - Enhance KofJS backend with improved function handling and module support
  - Implement Kof HTTP server and I/O library
  - idioms corpus, anti-pattern catalog, datasets, corrections
  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - Windows — o zip do JDK não preserva o bit de execução; aceitar java.exe por existência (-f) no launcher e no validate
  - launcher e validate usam o JDK embarcado em todas as plataformas
  - CI multiplataforma + kof.db link seletivo + JS try/finally + package Adoptium
  - enhance try-finally parsing logic to correctly handle labels and control flow
  - enhance MySQL connection detection and linker command for conditional library inclusion
  - add --as-needed flag to linker command for improved dependency handling
  - update output handling in various E2E tests for consistent UTF-8 encoding and line endings
  - update file path handling for cross-platform compatibility and enhance test process encoding
  - golden tests need the CLI jar; launcher must not break JDK 21
  - update expected output for label style binding in JS target
  - guard kofUiButtonRemove against missing action registry
  - update ClassPrepare event kind and improve event logging in JdwpClient
  - sound IR optimizer, JS switch routing and list construction
  - field initializers, record defaults and increment semantics
  - idiomatic core — name resolution by symbol, return inference, this-free fields
  - bool semantics parity — 0/1 results, true/false formatting, Multi-Release shade
  - restore kof_io_ dispatch in JVM runtime helper
  - JVM constructor super detection, List<ref> checkcast, kof.List descriptor
  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - DATABASE_VISION — nível 0 do kof.db implementado (JDBC idiomático JVM, SQLite nativo, MySQL WIP); níveis 1-4 seguem a visão
  - README e status finais (513/513, kof.db, JSON completo)
  - document the intent-oriented paradigm with honest framing
  - update local build instructions with lib/kof.jar workaround
  - document the kof.ui platform (widgets, events, webview)
  - auditoria do ecossistema da stdlib — matriz de cobertura (G1-G12)
  - debugger — Fases 1-3 implementadas (kof-debug MVP validado)
  - status — debugger Fases 1-2 (DebugInfo na IR, JVM metadata)
  - status — 394 testes, guidelines idiomáticas e estado real
  - fake-idioms — primary constructor is implemented (record-style since 0.0.5)
  - sync all .md with real 0.0.5 state
  - reorganize — move completed docs out of future/
  - status — 375/375, KofJS 100% (GraalJS embutido)
  - status — kof.io filesystem API, kof test, current test state
  - status — Fases H/J/K/L concluídas, I design pronto
  - Legacy Migration Platform architecture
  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.11-alpha [skip ci]
  - bump version to 0.0.10-alpha [skip ci]
  - bump version to 0.0.9-alpha [skip ci]
  - bump version to 0.0.8-alpha [skip ci]
  - bump version to 0.0.7-alpha [skip ci]
  - rebuild kof-webview with file:// module CORS fix
  - bump version to 0.0.6-alpha [skip ci]
  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.13-alpha] - 2026-08-24

### Features

  - implement MySQL authentication scramble using SHA-1
  - MySQL/MariaDB via wire protocol sobre sockets nativos (WIP)
  - add hidden easter egg registry and corresponding tests
  - native kof.db with SQLite via direct .so linking (no JDBC driver)
  - enhance string replacement functionality and content type handling in HTTP server
  - enhance string replace functionality and constructor handling in backends
  - JSON completo (Float/Double, arrays), logging estruturado, kof.db (JDBC + transactions)
  - enhance process output handling with virtual threads
  - enhance kof.ui documentation and add KofJS details
  - add kof.ui section to documentation with UI rendering details and widget descriptions
  - update documentation for UI components, add window and widget examples
  - multiple windows, window size and close-to-exit
  - add window size adjustment functionality in KofUi
  - add support for font size, bold, and color properties in KofUi labels
  - add label styling and window theme support in KofUi and related backends
  - Introduce new UI components and bindings for Input, Column, Row, View, and Style
  - enhance KofJsRunner to support program arguments and update related components
  - update documentation and fix issues in Kof Spring Starter phases, enhance runtime functions
  - implement native configuration and logging modules, update documentation
  - update documentation and enhance semantic analysis for config and logging namespaces
  - enhance KofJsRunner output handling and add webview settings for file access
  - implement native web stack with routing, middleware, and JSON support
  - enhance Kof compiler and runtime with new features and bug fixes
  - native webview shell — kof-webview (WebKitGTK embedded)
  - kof.ui webview — DOM shim, HTML serialization, system webview
  - enhance KofJsRunner to support window rendering and HTML capture
  - introduce kof.security module for password hashing, JWT, and cryptography
  - kof-debug MVP completo — breakpoints por linha Kof + stack trace
  - kof.ui Window and Label — webview container with binding
  - kof.ui foundation — Color, Palette, Theme + main(args)
  - add kof.ui foundation with Color, Palette, and Theme support
  - enhance benchmarking with JS target and add CPU time tracking
  - debugger Fase 2 — LocalVariableTable no JVM
  - Kof debugger — Fase 1 (DebugInfo na IR) + docs + JVM line metadata
  - add debug information support with source file and line number mapping in JVM backend
  - enhance IRModule and backend to support source name and debugging information
  - implement Kof debugging support with source mapping and debug metadata
  - add initializer support for record components and enhance semantic analysis
  - idiomatic core — field initializers applied, \uXXXX escapes, typed listOf<T>()
  - implement increment operations with correct semantics and add tests for idiomatic behavior
  - implement generics in Kof with examples for lists and sets
  - enhance method symbol to allow dynamic return type updates and improve semantic analysis
  - refactor semantic analysis by defining constructor and method symbols, and analyzing their bodies
  - add Color class with ARGB semantics and enhance color handling in the compiler
  - enhance literal parsing and add hexadecimal support in lexer
  - Fase L — release gate hardened + package revalidated
  - Fase K — assert primitive + expanded golden + kof test integration
  - implement assertion handling with AssertE2ETest and add various test cases for control flow, functions, and records
  - add AssertStmt for assertion handling and update lexer and token types
  - Fase J — LSP textDocument/didClose clears diagnostics
  - add KofJS backend and runtime support, including parity tests for JVM and JS
  - Enhance parsing and runtime capabilities with new if-expression handling and runtime options
  - kof test — run programs and report PASS/FAIL by exit code
  - Fase I — spawn: concurrent tasks on the JVM (virtual threads)
  - Introduce kof.io filesystem API for file and directory operations
  - kof.io documentation, multiplatform CI and platform guard
  - Fase J — LSP URI fix + editor grammar builtins
  - Fase I+L — concurrency semantics design + distribution validation
  - Fase K — real golden and integration test infrastructure
  - Enhance KofJS backend with improved function handling and module support
  - Implement Kof HTTP server and I/O library
  - idioms corpus, anti-pattern catalog, datasets, corrections
  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - extração do zip do JDK (Windows) — mover o subdiretório jdk-* com verificação, sem engolir falha
  - verificação explícita do JDK embarcado após a extração (Windows)
  - Windows — o zip do JDK não preserva o bit de execução; aceitar java.exe por existência (-f) no launcher e no validate
  - launcher e validate usam o JDK embarcado em todas as plataformas
  - CI multiplataforma + kof.db link seletivo + JS try/finally + package Adoptium
  - enhance try-finally parsing logic to correctly handle labels and control flow
  - enhance MySQL connection detection and linker command for conditional library inclusion
  - add --as-needed flag to linker command for improved dependency handling
  - update output handling in various E2E tests for consistent UTF-8 encoding and line endings
  - update file path handling for cross-platform compatibility and enhance test process encoding
  - golden tests need the CLI jar; launcher must not break JDK 21
  - update expected output for label style binding in JS target
  - guard kofUiButtonRemove against missing action registry
  - update ClassPrepare event kind and improve event logging in JdwpClient
  - sound IR optimizer, JS switch routing and list construction
  - field initializers, record defaults and increment semantics
  - idiomatic core — name resolution by symbol, return inference, this-free fields
  - bool semantics parity — 0/1 results, true/false formatting, Multi-Release shade
  - restore kof_io_ dispatch in JVM runtime helper
  - JVM constructor super detection, List<ref> checkcast, kof.List descriptor
  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - DATABASE_VISION — nível 0 do kof.db implementado (JDBC idiomático JVM, SQLite nativo, MySQL WIP); níveis 1-4 seguem a visão
  - README e status finais (513/513, kof.db, JSON completo)
  - document the intent-oriented paradigm with honest framing
  - update local build instructions with lib/kof.jar workaround
  - document the kof.ui platform (widgets, events, webview)
  - auditoria do ecossistema da stdlib — matriz de cobertura (G1-G12)
  - debugger — Fases 1-3 implementadas (kof-debug MVP validado)
  - status — debugger Fases 1-2 (DebugInfo na IR, JVM metadata)
  - status — 394 testes, guidelines idiomáticas e estado real
  - fake-idioms — primary constructor is implemented (record-style since 0.0.5)
  - sync all .md with real 0.0.5 state
  - reorganize — move completed docs out of future/
  - status — 375/375, KofJS 100% (GraalJS embutido)
  - status — kof.io filesystem API, kof test, current test state
  - status — Fases H/J/K/L concluídas, I design pronto
  - Legacy Migration Platform architecture
  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.12-alpha [skip ci]
  - bump version to 0.0.11-alpha [skip ci]
  - bump version to 0.0.10-alpha [skip ci]
  - bump version to 0.0.9-alpha [skip ci]
  - bump version to 0.0.8-alpha [skip ci]
  - bump version to 0.0.7-alpha [skip ci]
  - rebuild kof-webview with file:// module CORS fix
  - bump version to 0.0.6-alpha [skip ci]
  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

## [0.0.14-alpha] - 2026-08-24

### Features

  - implement MySQL authentication scramble using SHA-1
  - MySQL/MariaDB via wire protocol sobre sockets nativos (WIP)
  - add hidden easter egg registry and corresponding tests
  - native kof.db with SQLite via direct .so linking (no JDBC driver)
  - enhance string replacement functionality and content type handling in HTTP server
  - enhance string replace functionality and constructor handling in backends
  - JSON completo (Float/Double, arrays), logging estruturado, kof.db (JDBC + transactions)
  - enhance process output handling with virtual threads
  - enhance kof.ui documentation and add KofJS details
  - add kof.ui section to documentation with UI rendering details and widget descriptions
  - update documentation for UI components, add window and widget examples
  - multiple windows, window size and close-to-exit
  - add window size adjustment functionality in KofUi
  - add support for font size, bold, and color properties in KofUi labels
  - add label styling and window theme support in KofUi and related backends
  - Introduce new UI components and bindings for Input, Column, Row, View, and Style
  - enhance KofJsRunner to support program arguments and update related components
  - update documentation and fix issues in Kof Spring Starter phases, enhance runtime functions
  - implement native configuration and logging modules, update documentation
  - update documentation and enhance semantic analysis for config and logging namespaces
  - enhance KofJsRunner output handling and add webview settings for file access
  - implement native web stack with routing, middleware, and JSON support
  - enhance Kof compiler and runtime with new features and bug fixes
  - native webview shell — kof-webview (WebKitGTK embedded)
  - kof.ui webview — DOM shim, HTML serialization, system webview
  - enhance KofJsRunner to support window rendering and HTML capture
  - introduce kof.security module for password hashing, JWT, and cryptography
  - kof-debug MVP completo — breakpoints por linha Kof + stack trace
  - kof.ui Window and Label — webview container with binding
  - kof.ui foundation — Color, Palette, Theme + main(args)
  - add kof.ui foundation with Color, Palette, and Theme support
  - enhance benchmarking with JS target and add CPU time tracking
  - debugger Fase 2 — LocalVariableTable no JVM
  - Kof debugger — Fase 1 (DebugInfo na IR) + docs + JVM line metadata
  - add debug information support with source file and line number mapping in JVM backend
  - enhance IRModule and backend to support source name and debugging information
  - implement Kof debugging support with source mapping and debug metadata
  - add initializer support for record components and enhance semantic analysis
  - idiomatic core — field initializers applied, \uXXXX escapes, typed listOf<T>()
  - implement increment operations with correct semantics and add tests for idiomatic behavior
  - implement generics in Kof with examples for lists and sets
  - enhance method symbol to allow dynamic return type updates and improve semantic analysis
  - refactor semantic analysis by defining constructor and method symbols, and analyzing their bodies
  - add Color class with ARGB semantics and enhance color handling in the compiler
  - enhance literal parsing and add hexadecimal support in lexer
  - Fase L — release gate hardened + package revalidated
  - Fase K — assert primitive + expanded golden + kof test integration
  - implement assertion handling with AssertE2ETest and add various test cases for control flow, functions, and records
  - add AssertStmt for assertion handling and update lexer and token types
  - Fase J — LSP textDocument/didClose clears diagnostics
  - add KofJS backend and runtime support, including parity tests for JVM and JS
  - Enhance parsing and runtime capabilities with new if-expression handling and runtime options
  - kof test — run programs and report PASS/FAIL by exit code
  - Fase I — spawn: concurrent tasks on the JVM (virtual threads)
  - Introduce kof.io filesystem API for file and directory operations
  - kof.io documentation, multiplatform CI and platform guard
  - Fase J — LSP URI fix + editor grammar builtins
  - Fase I+L — concurrency semantics design + distribution validation
  - Fase K — real golden and integration test infrastructure
  - Enhance KofJS backend with improved function handling and module support
  - Implement Kof HTTP server and I/O library
  - idioms corpus, anti-pattern catalog, datasets, corrections
  - kof.time and kof.io stdlib primitives with JVM+Native parity
  - add support for string length and charAt methods in NativeBackend
  - implement standard library functions for time and I/O operations
  - add JvmJsonRuntime for JSON handling in JVM backend
  - native exception unwinding — real try/catch/finally on x86-64
  - lambda expressions and if-expressions with real lowering
  - real native memory management — allocator header, functional kof_free, live memstats
  - CLI platform commands — info, check, lsp, install
  - JVM backend correctness — records, Object methods, concat, comparisons
  - remove fun keyword — functions declared by name
  - JSON parity JVM+Native — object/record encode-decode, long, arrays, field inference
  - List rich API — contains, isEmpty, remove, clear, listOf (JVM + Native parity)
  - native string API parity — indexOf, trim, toUpperCase/toLowerCase, replace, equalsIgnoreCase, split
  - enhance parsing and execution for generic calls and string operations in JVM backend
  - enhance JSON encoding/decoding with improved parameter handling and type inference
  - add JSON support with encoding and decoding functions
  - List<T> builtin collection (native + JVM)
  - implement Kof list operations in JVM backend and native runtime
  - add Kof List type support and associated runtime functions
  - generics with erasure (classes, functions, type args)
  - add support for type parameters in symbol table
  - add support for type parameters in function and class declarations
  - strengthen compile-time type checking
  - constructors in native backend, skip implicit Object super() call
  - add break/continue, fix if/while/for control flow, comparison expressions
  - add .balign directive for method table alignment in NativeBackend and NativeRuntime
  - enhance Kof language type system with type IDs and instanceof support
  - implement switch statement and case handling in Kof language
  - enhance Kof language documentation with comprehensive references, examples, and common patterns
  - add support for do-while statements and enhance type system
  - Complete Phase F implementation with runtime, object model, exceptions, and memory management
  - Add logging for assembly generation and error handling in NativeBackend
  - Phase C+D+E - complete compiler with native backend

### Bugfixes

  - Windows — converter paths MSYS para Windows antes do extractall do Python
  - extração do zip do JDK (Windows) — mover o subdiretório jdk-* com verificação, sem engolir falha
  - verificação explícita do JDK embarcado após a extração (Windows)
  - Windows — o zip do JDK não preserva o bit de execução; aceitar java.exe por existência (-f) no launcher e no validate
  - launcher e validate usam o JDK embarcado em todas as plataformas
  - CI multiplataforma + kof.db link seletivo + JS try/finally + package Adoptium
  - enhance try-finally parsing logic to correctly handle labels and control flow
  - enhance MySQL connection detection and linker command for conditional library inclusion
  - add --as-needed flag to linker command for improved dependency handling
  - update output handling in various E2E tests for consistent UTF-8 encoding and line endings
  - update file path handling for cross-platform compatibility and enhance test process encoding
  - golden tests need the CLI jar; launcher must not break JDK 21
  - update expected output for label style binding in JS target
  - guard kofUiButtonRemove against missing action registry
  - update ClassPrepare event kind and improve event logging in JdwpClient
  - sound IR optimizer, JS switch routing and list construction
  - field initializers, record defaults and increment semantics
  - idiomatic core — name resolution by symbol, return inference, this-free fields
  - bool semantics parity — 0/1 results, true/false formatting, Multi-Release shade
  - restore kof_io_ dispatch in JVM runtime helper
  - JVM constructor super detection, List<ref> checkcast, kof.List descriptor
  - centralize primitive names, reject lambdas with a clear diagnostic
  - native JSON long parity + array element stride
  - JVM backend execution parity — if/else, strings, generics erasure boxing, records, interfaces, access flags, bitwise ops, long arithmetic
  - switch case fall-through, SUB operand order, function call typing
  - resolve native SIGSEGV and complete string/object ABI

### Documentation

  - DATABASE_VISION — nível 0 do kof.db implementado (JDBC idiomático JVM, SQLite nativo, MySQL WIP); níveis 1-4 seguem a visão
  - README e status finais (513/513, kof.db, JSON completo)
  - document the intent-oriented paradigm with honest framing
  - update local build instructions with lib/kof.jar workaround
  - document the kof.ui platform (widgets, events, webview)
  - auditoria do ecossistema da stdlib — matriz de cobertura (G1-G12)
  - debugger — Fases 1-3 implementadas (kof-debug MVP validado)
  - status — debugger Fases 1-2 (DebugInfo na IR, JVM metadata)
  - status — 394 testes, guidelines idiomáticas e estado real
  - fake-idioms — primary constructor is implemented (record-style since 0.0.5)
  - sync all .md with real 0.0.5 state
  - reorganize — move completed docs out of future/
  - status — 375/375, KofJS 100% (GraalJS embutido)
  - status — kof.io filesystem API, kof test, current test state
  - status — Fases H/J/K/L concluídas, I design pronto
  - Legacy Migration Platform architecture
  - align learning and training corpus with 0.0.4-alpha
  - distribution, packaging, versioning and state aligned with 0.0.4-alpha
  - atualizar status, architecture, actual-state, README

### Build

  - bump version to 0.0.13-alpha [skip ci]
  - bump version to 0.0.12-alpha [skip ci]
  - bump version to 0.0.11-alpha [skip ci]
  - bump version to 0.0.10-alpha [skip ci]
  - bump version to 0.0.9-alpha [skip ci]
  - bump version to 0.0.8-alpha [skip ci]
  - bump version to 0.0.7-alpha [skip ci]
  - rebuild kof-webview with file:// module CORS fix
  - bump version to 0.0.6-alpha [skip ci]
  - bump version to 0.0.5-alpha [skip ci]
  - centralized versioning, official launchers and packaging

### Tooling

  - official TextMate grammar and editor/LSP documentation

<!-- NEXT-RELEASE -->

## Versionamento

O Kof usa `MAJOR.MINOR.PATCH` (ver [docs/distribution/VERSIONING.md](docs/distribution/VERSIONING.md)).

- `0.0.x-alpha` — estágio inicial (Alpha), cada commit na `main` gera a próxima versão.
- O `PATCH` é o *pontinho da vergonha*: bugfixes, correções, regressões e pequenos ajustes.
- Nada é chamado de stable enquanto estiver em Alpha.

## [0.0.4-alpha] - 2026-08-22

### Infraestrutura de distribuição

- Versionamento centralizado: `VERSION` como fonte única, `<revision>` no Maven,
  `kof/version.properties` empacotado, `scripts/bump-version.sh`.
- `kof info` — relatório do ambiente (versão, Tooling API, target, JVM, install).
- `kof check` — type-check sem emissão de código.
- `kof lsp` — Language Server sobre stdio consumindo o frontend real do compilador.
- Launcher `bin/kof` (Unix) e `bin/kof.bat` (Windows) com suporte a JDK embutido.
- `scripts/package.sh` — pacote oficial (`kof-<versão>-<os>-<arch>` + SHA256SUMS),
  com JDK embutido opcional (`--jdk`, Temurin 21).
- GitHub Actions: `ci.yml` (PR) e `release.yml` (push na `main` → teste, bump,
  empacotamento multiplataforma, changelog e GitHub Release).
- Suporte a editores: grammar TextMate oficial em `editor/kof.tmLanguage.json`
  e documentação de consumo em `docs/tooling/`.

### Features

- JSON parity JVM + Native — encode/decode de objetos e records (JVM),
  `long`, arrays e inferência de campos.
- List rich API — `contains`, `isEmpty`, `remove`, `clear`, `listOf` (JVM + Native parity).
- Native string API parity — `indexOf`, `trim`, `toUpperCase`/`toLowerCase`,
  `replace`, `equalsIgnoreCase`, `split`.
- Backend JVM: generics com erasure, boxing, records, interfaces, bitwise,
  aritmética de `long`.

### Tooling

- Build Maven estável sob JDK 25 (reuso de compilador desabilitado no reactor).

## [0.0.3] - 2026-08-21

Estado anterior do projeto — veja `git log` e `docs/status.md` para o histórico completo.

## Formato da convenção de commits

```text
feat:      nova capacidade
fix:       correção de bug
docs:      documentação
refactor:  mudança interna sem mudança de comportamento
test:      testes
build:     build/CI/empacotamento
tooling:   ferramentas e editor support
```

A pipeline gera a seção do changelog a partir desses prefixos
(`scripts/changelog.sh`).