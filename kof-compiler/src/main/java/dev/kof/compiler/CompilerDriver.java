package dev.kof.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CompilerDriver {

    private IRModule currentModule;
    private CompilationUnitNode currentUnit;
    private SemanticAnalyzer semanticAnalyzer;
private Target target = Target.JVM;
    private boolean optimizeEnabled = true;
    private boolean debugInfoEnabled = true;
    private java.util.function.BiConsumer<IRModule, IRModule> irObserver;
    private IRObserver irStatsObserver;
    private DiagnosticCollector currentDiagnostics;
    private String currentSourceName;
    private final java.util.IdentityHashMap<KofOperation, SourcePosition> currentDebugPositions =
            new java.util.IdentityHashMap<>();
    private final java.util.Deque<LabelId> breakLabels = new java.util.ArrayDeque<>();
    private final java.util.Deque<LabelId> continueLabels = new java.util.ArrayDeque<>();
    private boolean loweringMain;
    private boolean mainArgsListField;

    public CompilationResult compile(Path sourceFile, Path outputDir) {
        return compile(sourceFile, outputDir, Target.JVM);
    }

    /** Um caso `test "nome" { }` descoberto em compile-time. */
    public record TestInfo(String name, String functionName) {
    }

    /** Testes descobertos na última compilação (ordem de declaração). */
    public java.util.List<TestInfo> discoveredTests() {
        return List.copyOf(discoveredTests);
    }

    /**
     * Compila em modo harness de testes: cada `test "nome" { }` vira uma
     * função void (`kof_test_N`) e o main do programa é substituído por um
     * runner sintetizado que executa os testes isolados por try/catch,
     * imprime PASS/FAIL por nome e sai com código != 0 quando há falha.
     * O main original é ignorado (como cargo test).
     */
    public CompilationResult compileForTests(Path sourceFile, Path outputDir, Target target) {
        this.testHarnessMode = true;
        try {
            return compile(sourceFile, outputDir, target);
        } finally {
            this.testHarnessMode = false;
        }
    }

    /** Variante multi-arquivo do harness de testes (um diretório = um módulo). */
    public CompilationResult compileForTestsSources(java.util.List<Path> sources,
                                                    Path outputDir, Target target, Path moduleRoot) {
        this.testHarnessMode = true;
        try {
            return compileSources(sources, outputDir, target, moduleRoot);
        } finally {
            this.testHarnessMode = false;
        }
    }

    /** Enable or disable IR optimization passes (enabled by default). */
    public CompilerDriver setOptimizationEnabled(boolean enabled) {
        this.optimizeEnabled = enabled;
        return this;
    }

    /** Enable or disable debug metadata emission (line tables, source names). */
    public CompilerDriver setDebugInfoEnabled(boolean enabled) {
        this.debugInfoEnabled = enabled;
        return this;
    }

    /**
     * Observes the IR before and after optimization (identical modules when
     * optimization is disabled). Used by tooling (kof inspect).
     */
    public CompilerDriver setIRObserver(java.util.function.BiConsumer<IRModule, IRModule> observer) {
        this.irObserver = observer;
        return this;
    }

    /** Observes IR statistics (public API for tooling; no IR types exposed). */
    public CompilerDriver setIRObserver(IRObserver observer) {
        this.irStatsObserver = observer;
        return this;
    }

    /**
     * Classpath externo (.jar/.aar/diretórios) fornecido pelo build tool
     * (Gradle no Android). Usado para resolver assinaturas de métodos de
     * superclasses externas — o INVOKESPECIAL de super.metodo() exige o
     * descritor exato declarado na classe externa.
     */
    private final ExternalClasspath externalClasspath = new ExternalClasspath();
    private final List<String> pendingClasspathWarnings = new ArrayList<>();

    public CompilerDriver setExternalClasspath(java.util.List<Path> entries) {
        try {
            externalClasspath.setEntries(entries);
        } catch (java.io.IOException e) {
            if (currentDiagnostics != null) {
                currentDiagnostics.error("", 0, 0, 0,
                        "external classpath could not be read: " + e.getMessage(), "CP001");
            } else {
                pendingClasspathWarnings.add("external classpath could not be read: " + e.getMessage());
            }
        }
        pendingClasspathWarnings.addAll(externalClasspath.loadWarnings());
        return this;
    }

    /** Emite warnings acumulados do classpath externo quando houver coletor. */
    private void flushClasspathWarnings() {
        if (currentDiagnostics != null && !pendingClasspathWarnings.isEmpty()) {
            for (String w : pendingClasspathWarnings) {
                currentDiagnostics.warning("", 0, 0, 0, w, "CP002");
            }
            pendingClasspathWarnings.clear();
        }
    }

    public CompilationResult compile(Path sourceFile, Path outputDir, Target target) {
        return compileSources(java.util.List.of(sourceFile), outputDir, target);
    }

    /**
     * Compilação MULTI-ARQUIVO: todos os .kf do diretório formam UM módulo
     * (convenção Go-like: diretório = pacote). Classes/funções de um arquivo
     * são visíveis aos demais sem import — o import fica para classes
     * EXTERNAS (JVM/Android via ExternalClasspath).
     */
    public CompilationResult compileSources(java.util.List<Path> sources, Path outputDir, Target target) {
        return compileSources(sources, outputDir, target,
                sources.get(0).toAbsolutePath().normalize().getParent());
    }

    public CompilationResult compileSources(java.util.List<Path> sources, Path outputDir, Target target,
                                            Path moduleRoot) {
        this.moduleRoot = moduleRoot;
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        this.target = target;
        this.currentDiagnostics = diagnostics;
        flushClasspathWarnings();
        this.currentSourceName = sources.get(0).getFileName() != null
                ? sources.get(0).getFileName().toString() : null;
        this.entitySchemas.clear();
        try {
            java.util.List<CompilationUnitNode> parsedUnits = new ArrayList<>();
            Path rootAbs = moduleRoot != null ? moduleRoot.toAbsolutePath().normalize() : null;
            for (Path src : sources) {
                String code = Files.readString(src);
                String fileName = src.getFileName().toString();
                Lexer lexer = new Lexer(code, fileName, diagnostics);
                List<Token> tokens = lexer.tokenize();
                if (diagnostics.hasErrors()) {
                    return new CompilationResult(false, diagnostics, outputDir);
                }
                Parser parser = new Parser(tokens, diagnostics, fileName);
                CompilationUnitNode unit = parser.parse();
                if (diagnostics.hasErrors()) {
                    return new CompilationResult(false, diagnostics, outputDir);
                }
                parsedUnits.add(unit);
            }
            // pacote por unidade: declarado, senão derivado do diretório
            java.util.List<String> unitPkgs = new ArrayList<>();
            for (int i = 0; i < parsedUnits.size(); i++) {
                String declared = parsedUnits.get(i).packageName();
                String derivedPkg = derivedPackageOf(sources.get(i), rootAbs);
                if (!declared.isEmpty() && !declared.equals(derivedPkg)) {
                    diagnostics.error(sources.get(i).toString(), 0, 0, 0,
                            "package '" + declared
                                    + "' não corresponde ao diretório ('" + derivedPkg
                                    + "') — um diretório é um pacote",
                            "PKG004");
                    return new CompilationResult(false, diagnostics, outputDir);
                }
                unitPkgs.add(derivedPkg);
            }
            // MERGE: imports unidos, declarações de TODAS as unidades
            List<String> mergedImports = new ArrayList<>();
            List<AstNode> mergedDecls = new ArrayList<>();
            int mainCount = 0;
            for (int i = 0; i < parsedUnits.size(); i++) {
                CompilationUnitNode u = parsedUnits.get(i);
                for (String imp : u.imports()) {
                    if (!mergedImports.contains(imp)) mergedImports.add(imp);
                }
                String pkgU = unitPkgs.get(i);
                for (AstNode d : u.declarations()) {
                    declarationPackages.put(d, pkgU);
                    if (d instanceof FunctionDeclarationNode fd && "main".equals(fd.name())) mainCount++;
                    mergedDecls.add(d);
                }
            }
            if (mainCount > 1) {
                diagnostics.error("", 0, 0, 0,
                        "module has " + mainCount + " main() functions; expected exactly one",
                        "PKG002");
                return new CompilationResult(false, diagnostics, outputDir);
            }
            CompilationUnitNode unit = new CompilationUnitNode(
                    parsedUnits.get(0).position(), "",
                    mergedImports, mergedDecls);
            unit = expandKofImports(unit);
            if (diagnostics.hasErrors()) {
                return new CompilationResult(false, diagnostics, outputDir);
            }
            lowerAndEmit(unit, diagnostics, outputDir, target);
            if (diagnostics.hasErrors()) {
                return new CompilationResult(false, diagnostics, outputDir);
            }
            return new CompilationResult(true, diagnostics, outputDir);
        } catch (IOException e) {
            diagnostics.error(sources.get(0).toString(), 0, 0, 0,
                    "Error reading source file: " + e.getMessage(), "COMP001");
            return new CompilationResult(false, diagnostics, outputDir);
        } catch (Exception e) {
            e.printStackTrace();
            diagnostics.error(sources.get(0).toString(), 0, 0, 0,
                    "Internal compiler error: " + e.getMessage(), "COMP002");
            return new CompilationResult(false, diagnostics, outputDir);
        }
    }

    /**
     * Imports de PACOTES KOF (código Kof em outras pastas):
     *   import vendas.models            → módulo inteiro do diretório
     *   import vendas.models.Cliente    → arquivo Cliente.kf daquele pacote
     *
     * Resolução: relativa à RAIZ do módulo (diretório passado ao build),
     * TRANSITIVA (imports dos imports), sem ciclos. Tipos ficam visíveis
     * pelo nome simples — a IR é única e global ao build.
     */
    private CompilationUnitNode expandKofImports(CompilationUnitNode unit) {
        java.util.Set<String> visitedDirs = new java.util.HashSet<>();
        List<AstNode> decls = new ArrayList<>(unit.declarations());
        List<String> imports = new ArrayList<>(unit.imports());
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>(imports);
        int rounds = 0;
        while (!queue.isEmpty() && rounds++ < 256) {
            String imp = queue.poll();
            if (imp.endsWith(".*")) {
                imp = imp.substring(0, imp.length() - 2);
            }
            Path pkgDir = moduleRoot != null
                    ? moduleRoot.resolve(imp.replace('.', '/'))
                    : Path.of(imp.replace('.', '/'));
            if (!Files.isDirectory(pkgDir)) continue;          // import externo (android.* etc.)
            String dirKey = pkgDir.toAbsolutePath().normalize().toString();
            if (!visitedDirs.add(dirKey)) continue;
            try (var stream = Files.walk(pkgDir, 1)) {
                for (Path kf : stream.filter(p -> p.toString().endsWith(".kf"))
                        .sorted(java.util.Comparator.comparing(p -> p.getFileName().toString()))
                        .toList()) {
                    String code = Files.readString(kf);
                    String fileName = kf.getFileName().toString();
                    DiagnosticCollector silent = new DiagnosticCollector();
                    Parser parser = new Parser(new Lexer(code, fileName, silent).tokenize(),
                            silent, fileName);
                    CompilationUnitNode libUnit = parser.parse();
                    if (silent.hasErrors()) {
                        for (Diagnostic d : silent.getDiagnostics()) currentDiagnostics.report(d);
                        continue;
                    }
                    String expectedPkg = dirKey.equals(moduleRoot.toAbsolutePath().normalize().toString())
                            ? "" : imp;
                    if (!libUnit.packageName().isEmpty()
                            && !libUnit.packageName().equals(expectedPkg)
                            && currentDiagnostics != null) {
                        SourcePosition p0 = libUnit.position();
                        currentDiagnostics.error(kf.toString(), 0, 0, 0,
                                "package '" + libUnit.packageName()
                                        + "' não corresponde ao diretório do import ('"
                                        + expectedPkg + "')",
                                "PKG004");
                        continue;
                    }
                    for (String libImp : libUnit.imports()) {
                        if (!imports.contains(libImp)) { imports.add(libImp); queue.add(libImp); }
                    }
                    for (AstNode d : libUnit.declarations()) {
                        declarationPackages.put(d, libUnit.packageName());
                        decls.add(d);
                    }
                }
            } catch (IOException e) {
                if (currentDiagnostics != null) {
                    currentDiagnostics.error("", 0, 0, 0,
                            "import '" + imp + "' could not be read: " + e.getMessage(), "PKG003");
                }
            }
        }
        // colisão de nomes entre pacotes → diagnóstico honesto
        java.util.Map<String, String> seen = new java.util.HashMap<>();
        for (AstNode d : decls) {
            String n = declarationName(d);
            if (n == null) continue;
            String pkg = declarationPackages.getOrDefault(d, unit.packageName());
            String prev = seen.get(n);
            if (prev != null && !prev.equals(pkg) && currentDiagnostics != null) {
                currentDiagnostics.error("", 0, 0, 0,
                        "duplicate type name '" + n + "' in packages '" + prev + "' and '"
                                + pkg + "'", "PKG005");
            }
            seen.putIfAbsent(n, pkg);
        }
        return new CompilationUnitNode(unit.position(), unit.packageName(),
                imports, decls);
    }

    private static String declarationName(AstNode d) {
        if (d instanceof TypeDeclarationNode t) return t.name();
        if (d instanceof FunctionDeclarationNode f) return f.name();
        return null;
    }

    private void lowerAndEmit(CompilationUnitNode unit, DiagnosticCollector diagnostics,
                              Path outputDir, Target target) throws IOException {
        if (System.getProperty("kof.trace") != null) {
            System.err.println("LOWER-AND-EMIT decls=" + unit.declarations().size() + " out=" + outputDir);
        }
        this.target = target;
        this.currentDiagnostics = diagnostics;
        flushClasspathWarnings();
        this.entitySchemas.clear();
        BuiltinTypes.resetEnums();
        for (AstNode d : unit.declarations()) {
            if (d instanceof EnumDeclarationNode en) BuiltinTypes.registerEnum(en.name());
        }
            unit = desugarTests(unit);
            if (target == Target.ANDROID) {
                unit = appendAndroidHostIfNeeded(unit);
            }
            semanticAnalyzer = new SemanticAnalyzer();
            semanticAnalyzer.setExternalTypes(externalClasspath);
            semanticAnalyzer.setDeclarationPackageLookup(d -> declarationPackages.get(d));
            semanticAnalyzer.analyze(unit, diagnostics);
            if (diagnostics.hasErrors()) {
                return;
            }
            LabelId.reset();
            currentModule = new IRModule("", List.of(), List.of());
            currentUnit = unit;
            IRModule irModule = applySuperBridges(lowerToIR(unit, diagnostics));
            if (diagnostics.hasErrors()) {
                return;
            }
            currentModule = irModule;
            IRModule unoptimized = irModule;
            if (optimizeEnabled) {
                irModule = Optimizer.optimize(irModule);
                currentModule = irModule;
            }
            if (irObserver != null) {
                irObserver.accept(unoptimized, irModule);
            }
            if (irStatsObserver != null) {
                irStatsObserver.observed(IRStatistics.of(unoptimized, irModule));
            }
            Files.createDirectories(outputDir);
            Backend backend = selectBackend(target);
            backend.emit(irModule, outputDir, debugInfoEnabled);
            if (target == Target.ANDROID) {
                new AndroidProjectWriter().write(outputDir, irModule);
            }
    }

    /**
     * Target android: se o programa não declarou a própria MainActivity
     * (em Kof), injeta o host WebView embutido — escrito EM KOF, compilado
     * pelo mesmo frontend. Nenhum arquivo Java é gerado.
     */
    private CompilationUnitNode appendAndroidHostIfNeeded(CompilationUnitNode unit) {
        boolean userHasHost = unit.declarations().stream()
                .anyMatch(d -> d instanceof TypeDeclarationNode t && "MainActivity".equals(t.name()));
        if (userHasHost) return unit;
        // sem android.jar no ExternalClasspath o host não resolve — avisar
        // (AND004) e seguir com o programa puro em vez de SEM015 confuso
        if (externalClasspath == null || !externalClasspath.knows("android/app/Activity")) {
            if (currentDiagnostics != null) {
                currentDiagnostics.warning("", 0, 0, 0,
                        "target android sem android.jar no ExternalClasspath: "
                                + "a host Activity não foi incluída no jar",
                        "AND004");
            }
            return unit;
        }
        try (var in = CompilerDriver.class.getResourceAsStream("/dev/kof/android-host.kf")) {
            String hostSource = in != null
                    ? new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    : Files.readString(Path.of("src/main/resources/dev/kof/android-host.kf"));
            DiagnosticCollector silent = new DiagnosticCollector();
            Lexer lexer = new Lexer(hostSource, "android-host.kf", silent);
            Parser parser = new Parser(lexer.tokenize(), silent, "android-host.kf");
            CompilationUnitNode hostUnit = parser.parse();
            List<String> imports = new ArrayList<>(unit.imports());
            for (String imp : hostUnit.imports()) {
                if (!imports.contains(imp)) imports.add(imp);
            }
            List<AstNode> decls = new ArrayList<>(unit.declarations());
            decls.addAll(hostUnit.declarations());
            return new CompilationUnitNode(unit.position(), unit.packageName(), imports, decls);
        } catch (IOException e) {
            if (currentDiagnostics != null) {
                currentDiagnostics.error("", 0, 0, 0,
                        "android host could not be loaded: " + e.getMessage(), "AND004");
            }
            return unit;
        }
    }

    private Backend selectBackend(Target target) {
        return switch (target) {
            case JVM -> backendWithClasspath(new JvmBackend());
            case NATIVE -> new NativeBackend();
            case JS -> new JsBackend();
            // Android: ART executa bytecode dex'd — a emissão é a mesma do
            // backend JVM; o alvo vive nas validações AND* e no empacotamento
            case ANDROID -> backendWithClasspath(new JvmBackend());
        };
    }

    private Backend backendWithClasspath(JvmBackend backend) {
        backend.setExternalTypes(externalClasspath);
        return backend;
    }

    private Type toType(String typeName) {
        if ("List".equals(typeName) || "ArrayList".equals(typeName)) return BuiltinTypes.LIST;
        // tipos simples declarados em import ("import android.webkit.WebView")
        Type viaImports = qualifyViaImports(typeName);
        if (viaImports != null) return viaImports;
        // tipos qualificados (android.os.Bundle): pacote vai no packageName
        // para o descritor JVM sair com barras (Landroid/os/Bundle;)
        int lastDot = typeName.lastIndexOf('.');
        if (lastDot > 0 && !typeName.contains("<") && !typeName.contains("/")) {
            return new Type.ClassType(typeName.substring(0, lastDot),
                    typeName.substring(lastDot + 1), List.of());
        }
        return Type.of(typeName);
    }

    /** Espelho driver-side do qualifyViaImports do SemanticAnalyzer. */
    private Type qualifyViaImports(String name) {
        if (name.contains(".") || name.contains("<") || name.endsWith("[]")) return null;
        if (currentUnit == null) return null;
        if (System.getProperty("kof.trace") != null && name.equals("WebView")) {
            System.err.println("QVI WebView imports=" + currentUnit.imports());
        }
        for (String imp : currentUnit.imports()) {
            if (!imp.endsWith("*") && imp.endsWith("." + name)) {
                String pkg = imp.substring(0, imp.lastIndexOf('.'));
                return new Type.ClassType(pkg, name, List.of());
            }
        }
        return null;
    }

    /** Nome JVM da entidade: as classes top-level do programa ficam sem
     *  pacote (User.class); o Main é Default/Main. */
    private String classNameFor(String simpleName) {
        return simpleName;
    }

    private Type ownerTypeFromInternal(String internalName) {
        if (semanticAnalyzer != null) {
            String simpleName = internalName.substring(internalName.lastIndexOf('/') + 1);
            SymbolTable.ClassSymbol cs = semanticAnalyzer.getClass(simpleName);
            if (cs != null) return cs.type();
        }
        String pkg = "";
        String name = internalName;
        int slashIdx = internalName.lastIndexOf('/');
        if (slashIdx >= 0) {
            pkg = internalName.substring(0, slashIdx).replace('/', '.');
            name = internalName.substring(slashIdx + 1);
        }
        return new Type.ClassType(pkg, name, List.of());
    }

    private Type mainClassType() {
        String mod = currentModule != null && !currentModule.name().isEmpty()
                ? currentModule.name() : "Default";
        if (!mod.contains("/")) mod = mod + "/Main";
        int slashIdx = mod.lastIndexOf('/');
        if (slashIdx >= 0) {
            return new Type.ClassType(mod.substring(0, slashIdx).replace('/', '.'), mod.substring(slashIdx + 1), List.of());
        }
        return new Type.ClassType("", mod, List.of());
    }

    private IRModule lowerToIR(CompilationUnitNode unit, DiagnosticCollector diagnostics) {
        List<String> imports = new ArrayList<>(unit.imports());
        List<IRClass> classes = new ArrayList<>();
        List<IRMethod> topLevelFunctions = new ArrayList<>();
        String moduleName = unit.packageName().isEmpty() ? "Default" : unit.packageName().replace('.', '/');
        int nextTypeId = 10;
        for (AstNode decl : unit.declarations()) {
            String declPkg = declPackage(decl, unit.packageName());
            if (decl instanceof ClassDeclarationNode cls) classes.add(lowerClass(cls, declPkg, nextTypeId++));
            else if (decl instanceof InterfaceDeclarationNode iface) classes.add(lowerInterface(iface, declPkg, nextTypeId++));
            else if (decl instanceof RecordDeclarationNode rec) classes.add(lowerRecord(rec, declPkg, nextTypeId++));
            else if (decl instanceof EntityDeclarationNode ent) {
                entitySchemas.put(ent.name(), ent.fields());
                List<RecordComponentNode> components = new java.util.ArrayList<>();
                for (EntityFieldNode f : ent.fields()) {
                    components.add(new RecordComponentNode(f.position(), List.of(), f.type(), f.name(), null));
                }
                classes.add(lowerRecord(new RecordDeclarationNode(ent.position(), ent.name(),
                        ent.modifiers(), null, List.of(), components, List.of()),
                        declPkg, nextTypeId++));
            }
            else if (decl instanceof FunctionDeclarationNode func) {
                topLevelFunctions.add(lowerFunction(func));
                topLevelFunctions.addAll(lowerFunctionDefaults(func));
            }
        }
        if (!topLevelFunctions.isEmpty()) {
            String mainClassName = moduleName.isEmpty() ? "Main" : moduleName + "/Main";
            classes.add(0, new IRClass(mainClassName, "java/lang/Object", List.of(),
                    AccessFlags.PUBLIC | AccessFlags.SUPER, List.of(), topLevelFunctions, List.of(), null, 0));
        }
        classes.addAll(syntheticClasses);
        return new IRModule(moduleName, classes, imports, currentSourceName);
    }

    private final List<IRClass> syntheticClasses = new ArrayList<>();

    /**
     * G6: desugar `test "nome" { }` para função void `kof_test_N` logo
     * após o parse — semântica, resolução e lowering tratam os testes como
     * funções comuns (zero casos especiais). Com o harness ativo, o main
     * do usuário é substituído pelo runner sintetizado em compile-time
     * (nunca reflection): cada teste roda isolado por try/catch, PASS/FAIL
     * por nome e exit code != 0 quando há falha.
     */
    private CompilationUnitNode desugarTests(CompilationUnitNode unit) {
        discoveredTests.clear();
        java.util.List<AstNode> decls = new ArrayList<>();
        int ti = 0;
        for (AstNode d : unit.declarations()) {
            if (d instanceof TestDeclarationNode t) {
                String fn = "kof_test_" + ti++;
                discoveredTests.add(new TestInfo(t.name(), fn));
                decls.add(new FunctionDeclarationNode(t.position(), List.of(), "void", fn,
                        List.of(), List.of(), List.of(), t.body()));
            } else {
                decls.add(d);
            }
        }
        if (testHarnessMode && !discoveredTests.isEmpty()) {
            java.util.List<AstNode> withHarness = new ArrayList<>();
            for (AstNode d : decls) {
                if (d instanceof FunctionDeclarationNode f && "main".equals(f.name())) {
                    continue; // kof test roda só os testes (como cargo test)
                }
                withHarness.add(d);
            }
            withHarness.add(buildTestHarnessMain());
            decls = withHarness;
        }
        return new CompilationUnitNode(unit.position(), unit.packageName(), unit.imports(),
                java.util.Collections.unmodifiableList(decls));
    }


    /**
     * Runner de testes sintetizado em compile-time (nunca reflection):
     *
     * main() {
     *     var __kof_failed = 0
     *     try {
     *         kof_test_0()
     *         println("PASS " + "nome")
     *     } catch (String e) {
     *         println("FAIL " + "nome" + ": " + e)
     *         __kof_failed = __kof_failed + 1
     *     }
     *     ...
     *     println("────────")
     *     println(__kof_failed + " failed of N tests")
     *     if (__kof_failed > 0) {
     *         throw "__kof_tests_failed__"
     *     }
     * }
     *
     * O throw final vira exit code != 0 em todos os targets (JVM: exceção
     * não capturada; Native: kof_panic; JS: runner reporta 1).
     */
    private FunctionDeclarationNode buildTestHarnessMain() {
        SourcePosition p = new SourcePosition(currentSourceName != null ? currentSourceName : "", 0, 0, 0, 0);
        List<StatementNode> body = new ArrayList<>();
        ExpressionNode failedVar = new IdentifierExpr(p, "__kof_failed");
        body.add(new VarDeclStmt(p, "Int", "__kof_failed",
                new LiteralExpr(p, ConcreteLiteralKind.INT, "0")));
        for (int i = 0; i < discoveredTests.size(); i++) {
            TestInfo test = discoveredTests.get(i);
            ExpressionNode nameLit = new LiteralExpr(p, ConcreteLiteralKind.STRING, test.name());
            List<StatementNode> tryBody = new ArrayList<>();
            tryBody.add(new ExpressionStmt(p, new MethodCallExpr(p, null,
                    test.functionName(), List.of(), List.of())));
            tryBody.add(new ExpressionStmt(p, callPrintln(p, concat(p,
                    new LiteralExpr(p, ConcreteLiteralKind.STRING, "PASS "), nameLit))));
            ExpressionNode failMsg = concat(p,
                    new LiteralExpr(p, ConcreteLiteralKind.STRING, "FAIL "), nameLit,
                    new LiteralExpr(p, ConcreteLiteralKind.STRING, ": "),
                    new IdentifierExpr(p, "e"));
            List<StatementNode> catchBody = new ArrayList<>();
            catchBody.add(new ExpressionStmt(p, callPrintln(p, failMsg)));
            catchBody.add(new ExpressionStmt(p, new AssignmentExpr(p, failedVar, "=",
                    new BinaryExpr(p, "+", failedVar,
                            new LiteralExpr(p, ConcreteLiteralKind.INT, "1")))));
            body.add(new TryStmt(p, tryBody,
                    List.of(new CatchClause(p, "String", "e", catchBody)), List.of()));
        }
        body.add(new ExpressionStmt(p, callPrintln(p,
                new LiteralExpr(p, ConcreteLiteralKind.STRING, "────────"))));
        ExpressionNode summary = concat(p,
                failedVar,
                new LiteralExpr(p, ConcreteLiteralKind.STRING, " failed of "
                        + discoveredTests.size() + " tests"));
        body.add(new ExpressionStmt(p, callPrintln(p, summary)));
        // falha = exit code != 0 em todos os targets, sem stack trace:
        // JVM System.exit / Native syscall exit / JS sentinel no runner
        body.add(new IfStmt(p,
                new BinaryExpr(p, ">", failedVar, new LiteralExpr(p, ConcreteLiteralKind.INT, "0")),
                new BlockStmt(p, List.of(new ExpressionStmt(p, new MethodCallExpr(p,
                        new IdentifierExpr(p, "process"), "exit", List.of(),
                        List.of(new LiteralExpr(p, ConcreteLiteralKind.INT, "1")))))),
                null));
        return new FunctionDeclarationNode(p, List.of(), "void", "main",
                List.of(), List.of(), List.of(), List.copyOf(body));
    }

    private static ExpressionNode concat(SourcePosition p, ExpressionNode... parts) {
        ExpressionNode acc = parts[0];
        for (int i = 1; i < parts.length; i++) {
            acc = new BinaryExpr(p, "+", acc, parts[i]);
        }
        return acc;
    }

    private static ExpressionNode callPrintln(SourcePosition p, ExpressionNode arg) {
        return new MethodCallExpr(p, null, "println", List.of(), List.of(arg));
    }

    private final java.util.Map<String, List<EntityFieldNode>> entitySchemas = new java.util.LinkedHashMap<>();
    private final java.util.IdentityHashMap<LambdaExpr, String> lambdaClassNames = new java.util.IdentityHashMap<>();
    /** Pontes super.metodo() geradas para lambdas: dono interno → método. */
    private final Map<String, List<IRMethod>> pendingSuperBridges = new java.util.LinkedHashMap<>();

    /**
     * Garante um método-ponte na classe DONA da lambda:
     *   kof_super$metodo(...) { super.metodo(...); }
     * A lambda chama a ponte (invokevirtual no $outer) — o verificador JVM
     * rejeita INVOKESPECIAL direto quando a classe corrente não é subclasse.
     */
    private String ensureSuperBridge(String ownerInternal, String superInternal,
                                     String methodName, List<Type> paramTypes, Type returnType) {
        String bridgeName = "kof_super$" + methodName;
        List<IRMethod> bridges = pendingSuperBridges.computeIfAbsent(ownerInternal,
                k -> new ArrayList<>());
        for (IRMethod b : bridges) {
            if (b.name().equals(bridgeName)) return bridgeName;
        }
        Type ownerT = ownerTypeFromInternal(ownerInternal);
        Type superT = ownerTypeFromInternal(superInternal);
        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> locals = new ArrayList<>();
        locals.add(new IRLocalVariable(0, "this", ownerT));
        int idx = 1;
        for (Type pt : paramTypes) {
            locals.add(new IRLocalVariable(idx, "arg" + idx, pt));
            idx += isDoubleWidth(pt) ? 2 : 1;
        }
        ops.add(new KofLoadLocal(ownerT, 0));
        int argIdx = 1;
        for (Type pt : paramTypes) {
            ops.add(new KofLoadLocal(pt, argIdx));
            argIdx += isDoubleWidth(pt) ? 2 : 1;
        }
        ops.add(new KofCall(superT, methodName, paramTypes, returnType, KofCallKind.SUPER));
        if (Type.isVoid(returnType)) ops.add(new KofReturnVoid());
        else ops.add(new KofReturn(returnType));
        bridges.add(new IRMethod(bridgeName, returnType, paramTypes,
                AccessFlags.PUBLIC | AccessFlags.FINAL, List.of(),
                List.of(new IRBasicBlock(0, ops)), locals));
        return bridgeName;
    }

    /** Aplica as pontes pendentes às classes do módulo (após lowering). */
    private IRModule applySuperBridges(IRModule module) {
        if (pendingSuperBridges.isEmpty()) return module;
        List<IRClass> classes = new ArrayList<>();
        for (IRClass clazz : module.classes()) {
            List<IRMethod> bridges = pendingSuperBridges.get(clazz.name());
            if (bridges == null) {
                classes.add(clazz);
                continue;
            }
            List<IRMethod> methods = new ArrayList<>(clazz.methods());
            methods.addAll(bridges);
            classes.add(new IRClass(clazz.name(), clazz.superName(), clazz.interfaces(),
                    clazz.accessFlags(), clazz.fields(), methods,
                    clazz.innerClasses(), clazz.signature(), clazz.typeId(),
                    clazz.annotations()));
        }
        pendingSuperBridges.clear();
        return new IRModule(module.name(), classes, module.imports(), module.sourceName());
    }

    /** Raiz do módulo: base para resolver imports de pacotes Kof (dirs). */
    private Path moduleRoot;

    /** Pacote declarado de cada declaração (multi-pacote num só módulo). */
    private final Map<AstNode, String> declarationPackages =
            new java.util.IdentityHashMap<>();


    /** Pacote derivado do DIRETÓRIO do arquivo relativo à raiz do módulo. */
    private static String derivedPackageOf(Path src, Path rootAbs) {
        Path abs = src.toAbsolutePath().normalize();
        if (rootAbs == null || !abs.startsWith(rootAbs)) return "";
        Path parent = rootAbs.relativize(abs).getParent();
        if (parent == null || parent.toString().isEmpty()) return "";
        return parent.toString().replace(java.io.File.separatorChar, '.');
    }

    private String declPackage(AstNode decl, String fallback) {
        String pkg = declarationPackages.get(decl);
        return pkg != null ? pkg : fallback;
    }

    /** Dono real da lambda (classe onde o corpo foi escrito) por classe sintética. */
    private final java.util.Map<String, String> lambdaEnclosingOwner = new java.util.LinkedHashMap<>();
    /** Variáveis externas ESCRITAS dentro de lambdas do método sendo lowered → box mutável. */
    private java.util.Set<String> mutatedCapturedNames = new java.util.HashSet<>();
    /** Nomes das classes BoxN sintéticas (captura mutável) — acesso via campo `value`. */
    private final java.util.Set<String> boxClassNames = new java.util.HashSet<>();
    private final java.util.Map<String, Type> boxValueTypes = new java.util.HashMap<>();
    private int boxCounter = 0;

    private final java.util.IdentityHashMap<LambdaExpr, List<IRLocalVariable>> lambdaEffectiveCaptures =
            new java.util.IdentityHashMap<>();

    /** Lambda que usa super.metodo() precisa capturar o this externo ($outer). */
    private final java.util.IdentityHashMap<LambdaExpr, Boolean> lambdaNeedsOuter =
            new java.util.IdentityHashMap<>();

    /** Dono do método sendo lowered agora (para capturar this de lambda). */
    private String currentLoweringOwner;

    /** Detecta uso de super.metodo() no corpo da lambda. */
    private static boolean lambdaUsesSuper(Object node) {
        if (node instanceof LambdaExpr le) {
            for (StatementNode st : le.body()) {
                if (lambdaUsesSuper(st)) return true;
            }
            return false;
        }
        if (node instanceof MethodCallExpr mc) {
            if (mc.receiver() instanceof IdentifierExpr rid && "super".equals(rid.name())) return true;
            if (lambdaUsesSuper(mc.receiver())) return true;
            for (ExpressionNode arg : mc.arguments()) if (lambdaUsesSuper(arg)) return true;
            return false;
        }
        if (node instanceof IdentifierExpr ie) return "super".equals(ie.name());
        if (node instanceof FieldAccessExpr fa) return lambdaUsesSuper(fa.receiver());
        if (node instanceof BinaryExpr be) return lambdaUsesSuper(be.left()) || lambdaUsesSuper(be.right());
        if (node instanceof UnaryExpr ue) return lambdaUsesSuper(ue.operand());
        if (node instanceof AssignmentExpr ae) return lambdaUsesSuper(ae.target()) || lambdaUsesSuper(ae.value());
        if (node instanceof VarDeclStmt v) return v.initializer() != null && lambdaUsesSuper(v.initializer());
        if (node instanceof ExpressionStmt es) return es.expression() != null && lambdaUsesSuper(es.expression());
        if (node instanceof ReturnStmt rs) return rs.value() != null && lambdaUsesSuper(rs.value());
        if (node instanceof IfStmt is) return lambdaUsesSuper(is.condition())
                || lambdaUsesSuper(is.thenBranch())
                || (is.elseBranch() != null && lambdaUsesSuper(is.elseBranch()));
        if (node instanceof WhileStmt ws) return lambdaUsesSuper(ws.condition()) || lambdaUsesSuper(ws.body());
        if (node instanceof ForStmt fs) return lambdaUsesSuper(fs.init()) || lambdaUsesSuper(fs.condition())
                || lambdaUsesSuper(fs.update()) || lambdaUsesSuper(fs.body());
        if (node instanceof BlockStmt bs) {
            for (StatementNode st : bs.statements()) if (lambdaUsesSuper(st)) return true;
            return false;
        }
        return false;
    }
    private final java.util.List<TestInfo> discoveredTests = new java.util.ArrayList<>();
    private boolean testHarnessMode = false;
    private int lambdaCounter = 0;

    /**
     * Synthetic lambda class. Captured outer locals become private final
     * fields set by a capturing <init>; invoke() copies them into locals at
     * entry, so the body lowers unchanged (captures are read-only snapshots).
     */
    private String lambdaClass(LambdaExpr le, Type.FunctionType ft, List<IRLocalVariable> captures) {
        String existing = lambdaClassNames.get(le);
        if (existing != null) return existing;
        String name = "Lambda" + (lambdaCounter++);
        Type ownerType = new Type.ClassType("", name, List.of());
        // super.metodo() dentro da lambda: captura o this EXTERNO como $outer
        boolean needsOuter = lambdaUsesSuper(le) && currentLoweringOwner != null;
        if (needsOuter) {
            lambdaNeedsOuter.put(le, true);
            lambdaEnclosingOwner.put(name, currentLoweringOwner);
            Type outerType = ownerTypeFromInternal(currentLoweringOwner);
            List<IRLocalVariable> eff = new ArrayList<>();
            eff.add(new IRLocalVariable(0, "$outer", outerType));
            eff.addAll(captures);
            captures = eff;
            lambdaEffectiveCaptures.put(le, eff);
        }
        Type returnType = toType(typeToString(ft.returnType()));
        List<FormalParameterNode> params = le.parameters();
        List<Type> paramTypes = new ArrayList<>();
        for (FormalParameterNode p : params) paramTypes.add(toType(p.type()));

        List<IRField> fields = new ArrayList<>();
        List<Type> captureTypes = new ArrayList<>();
        for (IRLocalVariable cap : captures) {
            fields.add(new IRField(cap.name(), cap.type(),
                    AccessFlags.PRIVATE | AccessFlags.FINAL, null));
            captureTypes.add(cap.type());
        }

        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> locals = new ArrayList<>();
        locals.add(new IRLocalVariable(0, "this", ownerType));
        int localIdx = 1;
        for (IRLocalVariable cap : captures) {
            ops.add(new KofLoadLocal(ownerType, 0));
            ops.add(new KofLoadField(ownerType, cap.name(), cap.type()));
            ops.add(new KofStoreLocal(cap.type(), localIdx));
            locals.add(new IRLocalVariable(localIdx, cap.name(), cap.type()));
            localIdx += isDoubleWidth(cap.type()) ? 2 : 1;
        }
        for (int i = 0; i < params.size(); i++) {
            locals.add(new IRLocalVariable(localIdx, params.get(i).name(), paramTypes.get(i)));
            localIdx += isDoubleWidth(paramTypes.get(i)) ? 2 : 1;
        }
        java.util.Set<String> savedMutated = mutatedCapturedNames;
        mutatedCapturedNames = new java.util.HashSet<>();
        // lambda não-void com corpo de expressão única: a expressão É o retorno
        // (ExpressionStmt emitiria POP e mataria o valor antes do areturn)
        java.util.List<StatementNode> bodyStmts = le.body();
        if (!Type.isVoid(returnType) && bodyStmts.size() == 1
                && bodyStmts.get(0) instanceof ExpressionStmt es) {
            bodyStmts = java.util.List.of(new ReturnStmt(
                    es.position() != null ? es.position() : le.position(), es.expression()));
        }
        for (StatementNode stmt : bodyStmts) {
            localIdx = emitStatement(stmt, ops, name, localIdx, locals, returnType);
        }
        mutatedCapturedNames = savedMutated;
        KofOperation last = ops.isEmpty() ? null : ops.get(ops.size() - 1);
        if (last == null || !(last instanceof KofReturn || last instanceof KofReturnVoid)) {
            if (Type.isVoid(returnType)) ops.add(new KofReturnVoid());
            else ops.add(new KofReturn(returnType));
        }
        IRMethod invoke = new IRMethod("invoke", returnType, paramTypes,
                AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, ops)), locals);

        List<KofOperation> ctorOps = new ArrayList<>();
        List<IRLocalVariable> ctorLocals = new ArrayList<>();
        ctorLocals.add(new IRLocalVariable(0, "this", ownerType));
        int cidx = 1;
        for (IRLocalVariable cap : captures) {
            ctorOps.add(new KofLoadLocal(ownerType, 0));
            ctorOps.add(new KofLoadLocal(cap.type(), cidx));
            ctorOps.add(new KofStoreField(ownerType, cap.name(), cap.type()));
            ctorLocals.add(new IRLocalVariable(cidx, cap.name(), cap.type()));
            cidx += isDoubleWidth(cap.type()) ? 2 : 1;
        }
        ctorOps.add(new KofReturnVoid());
        IRMethod ctor = new IRMethod("<init>", Type.PrimitiveType.VOID, captureTypes,
                AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, ctorOps)), ctorLocals);

        IRClass cls = new IRClass(name, "java/lang/Object", List.of(),
                AccessFlags.PUBLIC | AccessFlags.SUPER, fields,
                List.of(invoke, ctor), List.of(), null, 200 + lambdaCounter);
        syntheticClasses.add(cls);
        lambdaClassNames.put(le, name);
        return name;
    }


    /**
     * Captured outer locals referenced by the lambda body, in first-reference
     * order. Identifiers shadowed by locals declared inside the lambda are
     * not captured.
     */
    private List<IRLocalVariable> collectCaptures(LambdaExpr le, List<IRLocalVariable> outerLocals) {
        List<IRLocalVariable> captures = new ArrayList<>();
        java.util.Set<String> captured = new java.util.HashSet<>();
        collectCapturesStmts(le.body(), outerLocals, captures, captured, new java.util.HashSet<>());
        return captures;
    }

    private void collectCapturesStmts(StatementNode stmt, List<IRLocalVariable> outerLocals,
                                      List<IRLocalVariable> captures, java.util.Set<String> captured,
                                      java.util.Set<String> shadowed) {
        collectCapturesStmts(List.of(stmt), outerLocals, captures, captured, shadowed);
    }

    private void collectCapturesStmts(List<StatementNode> body, List<IRLocalVariable> outerLocals,
                                      List<IRLocalVariable> captures, java.util.Set<String> captured,
                                      java.util.Set<String> shadowed) {
        for (StatementNode s : body) {
            if (s instanceof ExpressionStmt es) {
                collectCapturesExpr(es.expression(), outerLocals, captures, captured, shadowed);
            } else if (s instanceof ReturnStmt rs) {
                if (rs.value() != null) {
                    collectCapturesExpr(rs.value(), outerLocals, captures, captured, shadowed);
                }
            } else if (s instanceof BlockStmt b) {
                java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
                collectCapturesStmts(b.statements(), outerLocals, captures, captured, inner);
            } else if (s instanceof IfStmt i) {
                collectCapturesExpr(i.condition(), outerLocals, captures, captured, shadowed);
                collectCapturesStmts(i.thenBranch(), outerLocals, captures, captured,
                        new java.util.HashSet<>(shadowed));
                if (i.elseBranch() != null) {
                    collectCapturesStmts(i.elseBranch(), outerLocals, captures, captured,
                            new java.util.HashSet<>(shadowed));
                }
            } else if (s instanceof WhileStmt w) {
                collectCapturesExpr(w.condition(), outerLocals, captures, captured, shadowed);
                collectCapturesStmts(w.body(), outerLocals, captures, captured,
                        new java.util.HashSet<>(shadowed));
            } else if (s instanceof DoWhileStmt dw) {
                collectCapturesStmts(dw.body(), outerLocals, captures, captured,
                        new java.util.HashSet<>(shadowed));
                collectCapturesExpr(dw.condition(), outerLocals, captures, captured, shadowed);
            } else if (s instanceof ForStmt f) {
                if (f.init() instanceof VarDeclStmt vds) {
                    collectCapturesVarDecl(vds, outerLocals, captures, captured, shadowed);
                } else if (f.init() instanceof ExpressionStmt ies) {
                    collectCapturesExpr(ies.expression(), outerLocals, captures, captured, shadowed);
                }
                if (f.condition() != null) {
                    collectCapturesExpr(f.condition(), outerLocals, captures, captured, shadowed);
                }
                if (f.update() != null) {
                    collectCapturesExpr(f.update(), outerLocals, captures, captured, shadowed);
                }
                collectCapturesStmts(f.body(), outerLocals, captures, captured,
                        new java.util.HashSet<>(shadowed));
            } else if (s instanceof ForInStmt fi) {
                collectCapturesExpr(fi.collection(), outerLocals, captures, captured, shadowed);
                java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
                inner.add(fi.varName());
                collectCapturesStmts(fi.body(), outerLocals, captures, captured, inner);
            } else if (s instanceof VarDeclStmt vds) {
                collectCapturesVarDecl(vds, outerLocals, captures, captured, shadowed);
            } else if (s instanceof ThrowStmt ts) {
                collectCapturesExpr(ts.expression(), outerLocals, captures, captured, shadowed);
            } else if (s instanceof AssertStmt as) {
                collectCapturesExpr(as.condition(), outerLocals, captures, captured, shadowed);
            } else if (s instanceof SpawnStmt ss) {
                // spawn lambdas have their own (capture-free) scope.
                collectCapturesExpr(ss.expression(), outerLocals, captures, captured, shadowed);
            } else if (s instanceof SwitchStmt sw) {
                collectCapturesExpr(sw.expression(), outerLocals, captures, captured, shadowed);
                for (SwitchCase c : sw.cases()) {
                    if (c.value() != null) {
                        collectCapturesExpr(c.value(), outerLocals, captures, captured, shadowed);
                    }
                    collectCapturesStmts(c.body(), outerLocals, captures, captured,
                            new java.util.HashSet<>(shadowed));
                }
                if (sw.defaultBody() != null) {
                    collectCapturesStmts(sw.defaultBody(), outerLocals, captures, captured,
                            new java.util.HashSet<>(shadowed));
                }
            } else if (s instanceof TryStmt ts) {
                collectCapturesStmts(ts.tryBody(), outerLocals, captures, captured,
                        new java.util.HashSet<>(shadowed));
                for (CatchClause cc : ts.catchClauses()) {
                    java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
                    inner.add(cc.exceptionName());
                    collectCapturesStmts(cc.body(), outerLocals, captures, captured, inner);
                }
                collectCapturesStmts(ts.finallyBody(), outerLocals, captures, captured,
                        new java.util.HashSet<>(shadowed));
            }
        }
    }

    private void collectCapturesVarDecl(VarDeclStmt vds, List<IRLocalVariable> outerLocals,
                                        List<IRLocalVariable> captures, java.util.Set<String> captured,
                                        java.util.Set<String> shadowed) {
        if (vds.initializer() != null) {
            collectCapturesExpr(vds.initializer(), outerLocals, captures, captured, shadowed);
        }
        shadowed.add(vds.name());
    }

    private void collectCapturesExpr(ExpressionNode expr, List<IRLocalVariable> outerLocals,
                                     List<IRLocalVariable> captures, java.util.Set<String> captured,
                                     java.util.Set<String> shadowed) {
        if (expr instanceof IdentifierExpr ie) {
            if (shadowed.contains(ie.name()) || captured.contains(ie.name())) return;
            IRLocalVariable outer = findLocalVar(ie.name(), outerLocals);
            if (outer != null) {
                captures.add(outer);
                captured.add(ie.name());
            }
        } else if (expr instanceof BinaryExpr bin) {
            collectCapturesExpr(bin.left(), outerLocals, captures, captured, shadowed);
            collectCapturesExpr(bin.right(), outerLocals, captures, captured, shadowed);
        } else if (expr instanceof UnaryExpr ue) {
            collectCapturesExpr(ue.operand(), outerLocals, captures, captured, shadowed);
        } else if (expr instanceof AssignmentExpr ae) {
            collectCapturesExpr(ae.target(), outerLocals, captures, captured, shadowed);
            collectCapturesExpr(ae.value(), outerLocals, captures, captured, shadowed);
        } else if (expr instanceof MethodCallExpr mc) {
            if (mc.receiver() != null) {
                collectCapturesExpr(mc.receiver(), outerLocals, captures, captured, shadowed);
            }
            for (ExpressionNode arg : mc.arguments()) {
                collectCapturesExpr(arg, outerLocals, captures, captured, shadowed);
            }
        } else if (expr instanceof FieldAccessExpr fa) {
            collectCapturesExpr(fa.receiver(), outerLocals, captures, captured, shadowed);
        } else if (expr instanceof ArrayAccessExpr aa) {
            collectCapturesExpr(aa.receiver(), outerLocals, captures, captured, shadowed);
            collectCapturesExpr(aa.index(), outerLocals, captures, captured, shadowed);
        } else if (expr instanceof IfExpr iex) {
            collectCapturesExpr(iex.condition(), outerLocals, captures, captured, shadowed);
            collectCapturesExpr(iex.thenExpr(), outerLocals, captures, captured, shadowed);
            collectCapturesExpr(iex.elseExpr(), outerLocals, captures, captured, shadowed);
        } else if (expr instanceof NewExpr ne) {
            for (ExpressionNode arg : ne.arguments()) {
                collectCapturesExpr(arg, outerLocals, captures, captured, shadowed);
            }
        } else if (expr instanceof NewArrayExpr nae) {
            collectCapturesExpr(nae.size(), outerLocals, captures, captured, shadowed);
        }
        // Nested LambdaExpr bodies capture against the enclosing lambda's own
        // locals (lowered at their own site); do not descend.
    }

    private void collectMutatedCaptures(List<StatementNode> body) {
        for (StatementNode stmt : body) {
            collectMutatedCapturesStmt(stmt, new java.util.HashSet<>(), false);
        }
    }

    private void collectMutatedCapturesStmt(StatementNode stmt, java.util.Set<String> shadowed, boolean inLambda) {
        if (stmt instanceof ExpressionStmt es) {
            collectMutatedCapturesExpr(es.expression(), shadowed, inLambda);
        } else if (stmt instanceof ReturnStmt rs) {
            if (rs.value() != null) collectMutatedCapturesExpr(rs.value(), shadowed, inLambda);
        } else if (stmt instanceof BlockStmt b) {
            java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
            for (StatementNode s : b.statements()) collectMutatedCapturesStmt(s, inner, inLambda);
        } else if (stmt instanceof IfStmt i) {
            collectMutatedCapturesExpr(i.condition(), shadowed, inLambda);
            collectMutatedCapturesStmt(i.thenBranch(), new java.util.HashSet<>(shadowed), inLambda);
            if (i.elseBranch() != null) collectMutatedCapturesStmt(i.elseBranch(), new java.util.HashSet<>(shadowed), inLambda);
        } else if (stmt instanceof WhileStmt w) {
            collectMutatedCapturesExpr(w.condition(), shadowed, inLambda);
            collectMutatedCapturesStmt(w.body(), new java.util.HashSet<>(shadowed), inLambda);
        } else if (stmt instanceof DoWhileStmt dw) {
            collectMutatedCapturesStmt(dw.body(), new java.util.HashSet<>(shadowed), inLambda);
            collectMutatedCapturesExpr(dw.condition(), shadowed, inLambda);
        } else if (stmt instanceof ForStmt f) {
            if (f.init() instanceof VarDeclStmt vds) {
                collectMutatedCapturesStmt(vds, shadowed, inLambda);
            } else if (f.init() instanceof ExpressionStmt ies) {
                collectMutatedCapturesExpr(ies.expression(), shadowed, inLambda);
            }
            if (f.condition() != null) collectMutatedCapturesExpr(f.condition(), shadowed, inLambda);
            if (f.update() != null) collectMutatedCapturesExpr(f.update(), shadowed, inLambda);
            collectMutatedCapturesStmt(f.body(), new java.util.HashSet<>(shadowed), inLambda);
        } else if (stmt instanceof ForInStmt fi) {
            collectMutatedCapturesExpr(fi.collection(), shadowed, inLambda);
            java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
            inner.add(fi.varName());
            collectMutatedCapturesStmt(fi.body(), inner, inLambda);
        } else if (stmt instanceof VarDeclStmt vds) {
            shadowed.add(vds.name());
            if (vds.initializer() != null) collectMutatedCapturesExpr(vds.initializer(), shadowed, inLambda);
        } else if (stmt instanceof ThrowStmt ts) {
            collectMutatedCapturesExpr(ts.expression(), shadowed, inLambda);
        } else if (stmt instanceof AssertStmt as) {
            collectMutatedCapturesExpr(as.condition(), shadowed, inLambda);
        } else if (stmt instanceof SpawnStmt ss) {
            collectMutatedCapturesExpr(ss.expression(), shadowed, inLambda);
        } else if (stmt instanceof SwitchStmt sw) {
            collectMutatedCapturesExpr(sw.expression(), shadowed, inLambda);
            for (SwitchCase c : sw.cases()) {
                if (c.value() != null) collectMutatedCapturesExpr(c.value(), shadowed, inLambda);
                for (StatementNode s : c.body()) collectMutatedCapturesStmt(s, new java.util.HashSet<>(shadowed), inLambda);
            }
            if (sw.defaultBody() != null) {
                for (StatementNode s : sw.defaultBody()) collectMutatedCapturesStmt(s, new java.util.HashSet<>(shadowed), inLambda);
            }
        } else if (stmt instanceof TryStmt ts) {
            for (StatementNode s : ts.tryBody()) collectMutatedCapturesStmt(s, new java.util.HashSet<>(shadowed), inLambda);
            for (CatchClause cc : ts.catchClauses()) {
                java.util.Set<String> inner = new java.util.HashSet<>(shadowed);
                inner.add(cc.exceptionName());
                for (StatementNode s : cc.body()) collectMutatedCapturesStmt(s, inner, inLambda);
            }
            if (ts.finallyBody() != null) {
                for (StatementNode s : ts.finallyBody()) collectMutatedCapturesStmt(s, new java.util.HashSet<>(shadowed), inLambda);
            }
        }
    }

    private void collectMutatedCapturesExpr(ExpressionNode expr, java.util.Set<String> shadowed, boolean inLambda) {
        if (expr instanceof LambdaExpr le) {
            for (StatementNode s : le.body()) {
                collectMutatedCapturesStmt(s, new java.util.HashSet<>(), true);
            }
        } else if (expr instanceof AssignmentExpr ae) {
            if (inLambda && ae.target() instanceof IdentifierExpr ie && !shadowed.contains(ie.name())) {
                mutatedCapturedNames.add(ie.name());
            }
            collectMutatedCapturesExpr(ae.target(), shadowed, inLambda);
            collectMutatedCapturesExpr(ae.value(), shadowed, inLambda);
        } else if (expr instanceof UnaryExpr ue) {
            if (inLambda && ue.operand() instanceof IdentifierExpr ie && !shadowed.contains(ie.name())
                    && ("++".equals(ue.operator()) || "--".equals(ue.operator()))) {
                mutatedCapturedNames.add(ie.name());
            }
            collectMutatedCapturesExpr(ue.operand(), shadowed, inLambda);
        } else if (expr instanceof BinaryExpr bin) {
            collectMutatedCapturesExpr(bin.left(), shadowed, inLambda);
            collectMutatedCapturesExpr(bin.right(), shadowed, inLambda);
        } else if (expr instanceof MethodCallExpr mc) {
            if (mc.receiver() != null) collectMutatedCapturesExpr(mc.receiver(), shadowed, inLambda);
            for (ExpressionNode arg : mc.arguments()) collectMutatedCapturesExpr(arg, shadowed, inLambda);
        } else if (expr instanceof FieldAccessExpr fa) {
            collectMutatedCapturesExpr(fa.receiver(), shadowed, inLambda);
        } else if (expr instanceof ArrayAccessExpr aa) {
            collectMutatedCapturesExpr(aa.receiver(), shadowed, inLambda);
            collectMutatedCapturesExpr(aa.index(), shadowed, inLambda);
        } else if (expr instanceof IfExpr iex) {
            collectMutatedCapturesExpr(iex.condition(), shadowed, inLambda);
            collectMutatedCapturesExpr(iex.thenExpr(), shadowed, inLambda);
            collectMutatedCapturesExpr(iex.elseExpr(), shadowed, inLambda);
        } else if (expr instanceof NewExpr ne) {
            for (ExpressionNode arg : ne.arguments()) collectMutatedCapturesExpr(arg, shadowed, inLambda);
        } else if (expr instanceof NewArrayExpr nae) {
            collectMutatedCapturesExpr(nae.size(), shadowed, inLambda);
        }
    }

    private boolean isBoxType(Type type) {
        return type instanceof Type.ClassType ct && boxClassNames.contains(ct.name());
    }

    private String createBoxClass(Type valueType) {
        String boxName = "Box" + (boxCounter++);
        boxClassNames.add(boxName);
        boxValueTypes.put(boxName, valueType);
        Type boxType = new Type.ClassType("", boxName, List.of());
        List<IRField> fields = List.of(new IRField("value", valueType, AccessFlags.PUBLIC, null));
        List<KofOperation> ctorOps = new ArrayList<>();
        ctorOps.add(new KofReturnVoid());
        List<IRLocalVariable> ctorLocals = List.of(new IRLocalVariable(0, "this", boxType));
        IRMethod ctor = new IRMethod("<init>", Type.PrimitiveType.VOID, List.of(),
                AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, ctorOps)), ctorLocals);
        IRClass cls = new IRClass(boxName, "java/lang/Object", List.of(),
                AccessFlags.PUBLIC | AccessFlags.SUPER, fields,
                List.of(ctor), List.of(), null, 300 + lambdaCounter);
        syntheticClasses.add(cls);
        return boxName;
    }

    /** Constantes por enum declarado na unidade atual (nome → [A, B, ...]). */
    private java.util.List<String> enumConstantsOf(String name) {
        if (name == null || currentUnit == null) return List.of();
        for (AstNode d : currentUnit.declarations()) {
            if (d instanceof EnumDeclarationNode en && en.name().equals(name)) {
                return en.constants();
            }
        }
        return List.of();
    }

    private boolean isEnumType(Type t) {
        if (!(t instanceof Type.ClassType ct) || !ct.packageName().isEmpty() || !ct.typeArguments().isEmpty()) return false;
        return !enumConstantsOf(ct.name()).isEmpty();
    }

    /** Nome da constante de enum representada por um rótulo de case. */
    private String enumConstantOfExpr(ExpressionNode e) {
        if (e instanceof FieldAccessExpr fa && fa.receiver() instanceof IdentifierExpr rid
                && isEnumName(rid.name())) {
            return enumConstantsOf(rid.name()).contains(fa.fieldName()) ? fa.fieldName() : null;
        }
        if (e instanceof LiteralExpr l && l.kind() == ConcreteLiteralKind.STRING) {
            return l.value();
        }
        if (e instanceof IdentifierExpr ie) {
            // não-qualificado: procura em todos os enums declarados
            if (currentUnit != null) {
                for (AstNode d : currentUnit.declarations()) {
                    if (d instanceof EnumDeclarationNode en && en.constants().contains(ie.name())) {
                        return ie.name();
                    }
                }
            }
        }
        return null;
    }

    private boolean isEnumName(String name) {
        return currentUnit != null && currentUnit.declarations().stream()
                .anyMatch(d -> d instanceof EnumDeclarationNode en && en.name().equals(name));
    }

    private Type listOfElementType(MethodCallExpr mc, List<IRLocalVariable> locals) {
        if (!mc.arguments().isEmpty()) {
            return inferExprType(mc.arguments().get(0), locals);
        }
        if (!mc.typeArguments().isEmpty()) {
            return toType(mc.typeArguments().get(0));
        }
        return Type.UnknownType.UNKNOWN;
    }

    private String typeToString(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return Type.canonicalPrimitiveName(pt.name());
        }
        if (type instanceof Type.ClassType ct) return ct.name();
        if (type instanceof Type.ArrayType at) return typeToString(at.componentType()) + "[]";
        return "Object";
    }

    private IRMethod lowerFunction(FunctionDeclarationNode func) {
        String prevOwner = currentLoweringOwner;
        currentLoweringOwner = mainClassInternalName();
        try {
            return lowerFunctionInner(func);
        } finally {
            currentLoweringOwner = prevOwner;
        }
    }

    private String mainClassInternalName() {
        return "Default/Main";
    }

    private IRMethod lowerFunctionInner(FunctionDeclarationNode func) {
        Type returnType = resolveWithTypeParams(func.returnType(), func.typeParameters());
        if (Type.isVoid(returnType)) {
            // inferência de retorno: percorre o corpo acumulando locais
            // (params + var decls) até achar um ReturnStmt com valor
            List<IRLocalVariable> tmpLocals = new ArrayList<>();
            int tmpIdx = 0;
            for (FormalParameterNode p : func.parameters()) {
                Type pt = resolveWithTypeParams(p.type(), func.typeParameters());
                tmpLocals.add(new IRLocalVariable(tmpIdx, p.name(), pt));
                tmpIdx += isDoubleWidth(pt) ? 2 : 1;
            }
            for (StatementNode stmt : func.body()) {
                if (stmt instanceof VarDeclStmt vds && vds.initializer() != null) {
                    Type vt = vds.type() != null && !"var".equals(vds.type())
                            ? toType(vds.type())
                            : inferExprType(vds.initializer(), tmpLocals);
                    tmpLocals.add(new IRLocalVariable(tmpIdx, vds.name(), vt));
                    tmpIdx += isDoubleWidth(vt) ? 2 : 1;
                }
                if (stmt instanceof ReturnStmt ret && ret.value() != null) {
                    Type inferred = inferExprType(ret.value(), tmpLocals);
                    if (!(inferred instanceof Type.UnknownType) && !Type.isVoid(inferred)) {
                        returnType = inferred;
                    }
                    break;
                }
                if (stmt instanceof ExpressionStmt es && es.expression() instanceof MethodCallExpr) {
                    break; // void call termina a busca
                }
            }
        }
        List<Type> paramTypes = func.parameters().stream()
                .map(p -> resolveWithTypeParams(p.type(), func.typeParameters())).toList();
        boolean mainArgsList = "main".equals(func.name()) && func.parameters().size() == 1
                && "args".equals(func.parameters().get(0).name())
                && BuiltinTypes.isList(paramTypes.get(0));
        boolean isMain = "main".equals(func.name())
                && (paramTypes.isEmpty() || mainArgsList);
        if (isMain) {
            paramTypes = List.of(new Type.ArrayType(BuiltinTypes.STRING));
        }
        boolean prevMain = loweringMain;
        loweringMain = isMain;
        boolean prevMainArgsList = mainArgsListField;
        mainArgsListField = mainArgsList;
        int access = AccessFlags.PUBLIC | AccessFlags.STATIC;
        List<IRLocalVariable> locals = new ArrayList<>();
        List<KofOperation> body = new ArrayList<>();
        int localIdx = 0;
        if (isMain) {
            if (mainArgsList) {
                // args: List<String> — convert the injected String[] once
                // at method entry (JVM); Native/JS start with an empty list.
                if (target == Target.JVM) {
                    body.add(new KofLoadLocal(new Type.ArrayType(BuiltinTypes.STRING), 0));
                    body.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                            "kof_args_list", List.of(new Type.ArrayType(BuiltinTypes.STRING)),
                            BuiltinTypes.LIST, KofCallKind.FUNCTION));
                    body.add(new KofStoreLocal(BuiltinTypes.LIST, 1));
                } else if (target == Target.JS) {
                    body.add(new KofCall(BuiltinTypes.LIST, "kof_args", List.of(),
                            BuiltinTypes.LIST, KofCallKind.FUNCTION));
                    body.add(new KofStoreLocal(BuiltinTypes.LIST, 1));
                } else {
                    body.add(new KofCall(BuiltinTypes.LIST, "kof_list_new", List.of(),
                            BuiltinTypes.LIST, KofCallKind.FUNCTION));
                    body.add(new KofStoreLocal(BuiltinTypes.LIST, 1));
                }
            }
            localIdx = 1;
        }
        for (FormalParameterNode p : func.parameters()) {
            Type paramType = resolveWithTypeParams(p.type(), func.typeParameters());
            locals.add(new IRLocalVariable(localIdx, p.name(), paramType));
            localIdx += isDoubleWidth(paramType) ? 2 : 1;
        }
        java.util.Set<String> savedMutated = mutatedCapturedNames;
        mutatedCapturedNames = new java.util.HashSet<>();
        collectMutatedCaptures(func.body());
        for (StatementNode stmt : func.body()) {
            localIdx = emitStatement(stmt, body, "", localIdx, locals, returnType);
        }
        mutatedCapturedNames = savedMutated;
        KofOperation last = body.isEmpty() ? null : body.get(body.size() - 1);
        if (last == null || !(last instanceof KofReturn || last instanceof KofReturnVoid)) {
            if (Type.isVoid(returnType)) body.add(new KofReturnVoid());
            else body.add(new KofReturn(returnType));
        }
        KofDebugInfo debugInfo = currentDebugPositions.isEmpty()
                ? KofDebugInfo.EMPTY
                : new KofDebugInfo(new java.util.HashMap<>(currentDebugPositions));
        currentDebugPositions.clear();
        loweringMain = prevMain;
        mainArgsListField = prevMainArgsList;
        return new IRMethod(func.name(), returnType, paramTypes, access, func.thrownExceptions(),
                List.of(new IRBasicBlock(0, body)), locals, debugInfo,
                lowerAnnotations(func.annotations()), lowerParameterAnnotations(func.parameters()));
    }

    /**
     * Default parameter values: for each trailing default, a wrapper with the
     * same name and fewer parameters is generated. The wrapper evaluates the
     * default expressions and delegates to the canonical function — pure
     * compile-time semantics, no runtime machinery.
     */
    private List<IRMethod> lowerFunctionDefaults(FunctionDeclarationNode func) {
        List<IRMethod> wrappers = new ArrayList<>();
        List<FormalParameterNode> params = func.parameters();
        if (params.isEmpty() || params.stream().noneMatch(p -> p.defaultExpression() != null)) {
            return wrappers;
        }
        if ("main".equals(func.name())) return wrappers;
        int n = params.size();
        int firstDefault = n;
        for (int i = 0; i < n; i++) {
            if (params.get(i).defaultExpression() != null) {
                firstDefault = i;
                break;
            }
        }
        if (firstDefault == n) return wrappers;
        List<Type> canonicalTypes = params.stream()
                .map(p -> resolveWithTypeParams(p.type(), func.typeParameters())).toList();
        Type returnType = resolveWithTypeParams(func.returnType(), func.typeParameters());
        for (int drop = 1; drop <= n - firstDefault; drop++) {
            int paramCount = n - drop;
            List<Type> paramTypes = canonicalTypes.subList(0, paramCount);
            List<IRLocalVariable> locals = new ArrayList<>();
            List<KofOperation> ops = new ArrayList<>();
            int localIdx = 0;
            for (int i = 0; i < paramCount; i++) {
                locals.add(new IRLocalVariable(localIdx, params.get(i).name(), paramTypes.get(i)));
                ops.add(new KofLoadLocal(paramTypes.get(i), localIdx));
                localIdx++;
            }
            for (int i = paramCount; i < n; i++) {
                localIdx = emitExpression(params.get(i).defaultExpression(), ops, "",
                        localIdx, locals);
            }
            ops.add(new KofCall(mainClassType(), func.name(), canonicalTypes,
                    returnType, KofCallKind.FUNCTION));
            ops.add(new KofReturn(returnType));
            wrappers.add(new IRMethod(func.name(), returnType, paramTypes,
                    AccessFlags.PUBLIC | AccessFlags.STATIC, func.thrownExceptions(),
                    List.of(new IRBasicBlock(0, ops)), locals));
        }
        return wrappers;
    }

    private int emitStatement(StatementNode stmt, List<KofOperation> ops, String owner, int localIdx,
                              List<IRLocalVariable> locals, Type returnType) {
        int before = ops.size();
        int result = emitStatementInner(stmt, ops, owner, localIdx, locals, returnType);
        if (stmt.position() != null) {
            for (int i = before; i < ops.size(); i++) {
                currentDebugPositions.put(ops.get(i), stmt.position());
            }
        }
        return result;
    }

    private int emitStatementInner(StatementNode stmt, List<KofOperation> ops, String owner, int localIdx,
                                   List<IRLocalVariable> locals, Type returnType) {
        return switch (stmt) {
            case ReturnStmt ret -> {
                if (ret.value() != null) {
                    localIdx = emitExpression(ret.value(), ops, owner, localIdx, locals);
                    ops.add(new KofReturn(returnType));
                } else if (Type.isVoid(returnType)) {
                    ops.add(new KofReturnVoid());
                } else {
                    ops.add(defaultValueOp(returnType));
                    ops.add(new KofReturn(returnType));
                }
                yield localIdx;
            }
            case BreakStmt ignored -> {
                if (!breakLabels.isEmpty()) ops.add(new KofJump(breakLabels.peek()));
                yield localIdx;
            }
            case ContinueStmt ignored -> {
                if (!continueLabels.isEmpty()) ops.add(new KofJump(continueLabels.peek()));
                yield localIdx;
            }
            case ExpressionStmt es -> {
                if (es.expression() != null) {
                    localIdx = emitExpression(es.expression(), ops, owner, localIdx, locals);
                    if (hasReturnValue(es.expression(), locals)) ops.add(new KofPop());
                }
                yield localIdx;
            }
            case VarDeclStmt vds -> {
                Type varType = toType(vds.type());
                if (mutatedCapturedNames.contains(vds.name())) {
                    Type initType = vds.initializer() == null ? Type.PrimitiveType.INT
                            : inferExprType(vds.initializer(), locals);
                    String boxName = createBoxClass(initType);
                    Type boxType = new Type.ClassType("", boxName, List.of());
                    ops.add(new KofNewObject(boxType, List.of()));
                    ops.add(new KofDup());
                    ops.add(new KofCall(boxType, "<init>", List.of(),
                            Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                    ops.add(new KofDup());
                    if (vds.initializer() != null) {
                        localIdx = emitExpression(vds.initializer(), ops, owner, localIdx, locals);
                    } else {
                        ops.add(new KofLoadLiteral(initType, 0));
                    }
                    ops.add(new KofStoreField(boxType, "value", initType));
                    ops.add(new KofStoreLocal(boxType, localIdx));
                    locals.add(new IRLocalVariable(localIdx, vds.name(), boxType));
                    yield localIdx + 1;
                }
                if (vds.initializer() != null) {
                    localIdx = emitExpression(vds.initializer(), ops, owner, localIdx, locals);
                    if ("var".equals(vds.type()) || "val".equals(vds.type())) {
                        varType = inferExprType(vds.initializer(), locals);
                        // spawn-expr: pina Handle<T> com T do corpo (a inferência
                        // genérica pode ter perdido o typeArgument)
                        if (vds.initializer() instanceof MethodCallExpr sm
                                && "__kof_spawn_expr".equals(sm.methodName())
                                && varType instanceof Type.ClassType hct
                                && "kof.concurrent".equals(hct.packageName())
                                && (hct.typeArguments().isEmpty()
                                    || hct.typeArguments().get(0) instanceof Type.UnknownType)) {
                            varType = new Type.ClassType("kof.concurrent", "Handle",
                                    List.of(inferExprType(sm.arguments().get(0), locals)));
                        }
                    } else {
                        emitWideningIfNeeded(ops, inferExprType(vds.initializer(), locals), varType);
                    }
                }
                ops.add(new KofStoreLocal(varType, localIdx));
                locals.add(new IRLocalVariable(localIdx, vds.name(), varType));
                yield localIdx + (isDoubleWidth(varType) ? 2 : 1);
            }
            case BlockStmt block -> {
                int idx = localIdx;
                for (StatementNode s : block.statements()) {
                    idx = emitStatement(s, ops, owner, idx, locals, returnType);
                }
                yield idx;
            }
            case IfStmt ifStmt -> {
                LabelId elseLabel = LabelId.create();
                LabelId endLabel = LabelId.create();
                LabelId thenLabel = LabelId.create();
                if (ifStmt.condition() instanceof BinaryExpr bin && isComparisonShortcut(bin, locals)) {
                    localIdx = emitComparisonShortcut(bin, ops, owner, localIdx, locals);
                    ops.add(new KofConditionalJump(mapComparison(bin.operator()), comparisonOperandType(bin, locals), thenLabel, elseLabel));
                } else {
                    localIdx = emitExpression(ifStmt.condition(), ops, owner, localIdx, locals);
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                    ops.add(new KofConditionalJump(KofComparison.NE, thenLabel, elseLabel));
                }
                ops.add(new KofLabel(thenLabel));
                localIdx = emitStatement(ifStmt.thenBranch(), ops, owner, localIdx, locals, returnType);
                ops.add(new KofJump(endLabel));
                ops.add(new KofLabel(elseLabel));
                if (ifStmt.elseBranch() != null) {
                    localIdx = emitStatement(ifStmt.elseBranch(), ops, owner, localIdx, locals, returnType);
                }
                ops.add(new KofLabel(endLabel));
                yield localIdx;
            }
            case WhileStmt ws -> {
                LabelId startLabel = LabelId.create();
                LabelId endLabel = LabelId.create();
                LabelId bodyLabel = LabelId.create();
                ops.add(new KofLabel(startLabel));
                if (ws.condition() instanceof BinaryExpr bin && isComparisonShortcut(bin, locals)) {
                    localIdx = emitComparisonShortcut(bin, ops, owner, localIdx, locals);
                    ops.add(new KofConditionalJump(mapComparison(bin.operator()), comparisonOperandType(bin, locals), bodyLabel, endLabel));
                } else {
                    localIdx = emitExpression(ws.condition(), ops, owner, localIdx, locals);
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                    ops.add(new KofConditionalJump(KofComparison.NE, bodyLabel, endLabel));
                }
                ops.add(new KofLabel(bodyLabel));
                breakLabels.push(endLabel);
                continueLabels.push(startLabel);
                localIdx = emitStatement(ws.body(), ops, owner, localIdx, locals, returnType);
                breakLabels.pop();
                continueLabels.pop();
                ops.add(new KofJump(startLabel));
                ops.add(new KofLabel(endLabel));
                yield localIdx;
            }
            case DoWhileStmt dws -> {
                LabelId startLabel = LabelId.create();
                LabelId endLabel = LabelId.create();
                ops.add(new KofLabel(startLabel));
                breakLabels.push(endLabel);
                continueLabels.push(startLabel);
                localIdx = emitStatement(dws.body(), ops, owner, localIdx, locals, returnType);
                breakLabels.pop();
                continueLabels.pop();
                if (dws.condition() instanceof BinaryExpr bin && isComparisonShortcut(bin, locals)) {
                    localIdx = emitComparisonShortcut(bin, ops, owner, localIdx, locals);
                    ops.add(new KofConditionalJump(mapComparison(bin.operator()), comparisonOperandType(bin, locals), startLabel, endLabel));
                } else {
                    localIdx = emitExpression(dws.condition(), ops, owner, localIdx, locals);
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                    ops.add(new KofConditionalJump(KofComparison.NE, startLabel, endLabel));
                }
                ops.add(new KofLabel(endLabel));
                yield localIdx;
            }
            case ForStmt fs -> {
                LabelId startLabel = LabelId.create();
                LabelId endLabel = LabelId.create();
                LabelId continueLabel = LabelId.create();
                LabelId bodyLabel = LabelId.create();
                if (fs.init() != null) localIdx = emitStatement(fs.init(), ops, owner, localIdx, locals, returnType);
                ops.add(new KofLabel(startLabel));
                if (fs.condition() != null) {
                    if (fs.condition() instanceof BinaryExpr bin && isComparisonShortcut(bin, locals)) {
                        localIdx = emitComparisonShortcut(bin, ops, owner, localIdx, locals);
                        ops.add(new KofConditionalJump(mapComparison(bin.operator()), comparisonOperandType(bin, locals), bodyLabel, endLabel));
                    } else {
                        localIdx = emitExpression(fs.condition(), ops, owner, localIdx, locals);
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                        ops.add(new KofConditionalJump(KofComparison.NE, bodyLabel, endLabel));
                    }
                }
                ops.add(new KofLabel(bodyLabel));
                breakLabels.push(endLabel);
                continueLabels.push(continueLabel);
                localIdx = emitStatement(fs.body(), ops, owner, localIdx, locals, returnType);
                breakLabels.pop();
                continueLabels.pop();
                ops.add(new KofLabel(continueLabel));
                if (fs.update() != null) {
                    if (fs.update() instanceof UnaryExpr ue && "++".equals(ue.operator()) && ue.operand() instanceof IdentifierExpr id) {
                        IRLocalVariable var = findLocalVar(id.name(), locals);
                        if (var != null) {
                            ops.add(new KofLoadLocal(var.type(), var.index()));
                            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
                            ops.add(new KofBinary(KofBinaryOp.ADD, var.type()));
                            ops.add(new KofStoreLocal(var.type(), var.index()));
                        }
                    } else if (fs.update() instanceof UnaryExpr ue2 && "--".equals(ue2.operator()) && ue2.operand() instanceof IdentifierExpr id2) {
                        IRLocalVariable var2 = findLocalVar(id2.name(), locals);
                        if (var2 != null) {
                            ops.add(new KofLoadLocal(var2.type(), var2.index()));
                            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
                            ops.add(new KofBinary(KofBinaryOp.SUB, var2.type()));
                            ops.add(new KofStoreLocal(var2.type(), var2.index()));
                        }
                    } else {
                        localIdx = emitExpression(fs.update(), ops, owner, localIdx, locals);
                        if (hasReturnValue(fs.update(), locals)) ops.add(new KofPop());
                    }
                }
                ops.add(new KofJump(startLabel));
                ops.add(new KofLabel(endLabel));
                yield localIdx;
            }
            case ForInStmt fis -> {
                LabelId startLabel = LabelId.create();
                LabelId bodyLabel = LabelId.create();
                LabelId endLabel = LabelId.create();
                LabelId continueLabel = LabelId.create();
                Type collType = inferExprType(fis.collection(), locals);
                Type elemType = Type.UnknownType.UNKNOWN;
                boolean isList = BuiltinTypes.isList(collType);
                if (isList) elemType = listElementType(collType);
                else if (collType instanceof Type.ArrayType at) elemType = at.componentType();
                int collIdx = localIdx++;
                int idxIdx = localIdx++;
                int varIdx = localIdx++;
                locals.add(new IRLocalVariable(collIdx, "#coll", collType));
                locals.add(new IRLocalVariable(idxIdx, "#idx", Type.PrimitiveType.INT));
                locals.add(new IRLocalVariable(varIdx, fis.varName(), elemType));
                localIdx = emitExpression(fis.collection(), ops, owner, localIdx, locals);
                ops.add(new KofStoreLocal(collType, collIdx));
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                ops.add(new KofStoreLocal(Type.PrimitiveType.INT, idxIdx));
                ops.add(new KofLabel(startLabel));
                ops.add(new KofLoadLocal(Type.PrimitiveType.INT, idxIdx));
                ops.add(new KofLoadLocal(collType, collIdx));
                if (isList) {
                    ops.add(new KofCall(collType, "kof_list_size", List.of(), Type.PrimitiveType.INT, KofCallKind.INSTANCE));
                } else {
                    ops.add(new KofArrayLength());
                }
                ops.add(new KofConditionalJump(KofComparison.LT, bodyLabel, endLabel));
                ops.add(new KofLabel(bodyLabel));
                ops.add(new KofLoadLocal(collType, collIdx));
                ops.add(new KofLoadLocal(Type.PrimitiveType.INT, idxIdx));
                if (isList) {
                    ops.add(new KofCall(collType, "kof_list_get", List.of(Type.PrimitiveType.INT), elemType, KofCallKind.INSTANCE));
                } else {
                    ops.add(new KofArrayLoad(elemType));
                }
                ops.add(new KofStoreLocal(elemType, varIdx));
                breakLabels.push(endLabel);
                continueLabels.push(continueLabel);
                localIdx = emitStatement(fis.body(), ops, owner, localIdx, locals, returnType);
                breakLabels.pop();
                continueLabels.pop();
                ops.add(new KofLabel(continueLabel));
                ops.add(new KofLoadLocal(Type.PrimitiveType.INT, idxIdx));
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
                ops.add(new KofBinary(KofBinaryOp.ADD, Type.PrimitiveType.INT));
                ops.add(new KofStoreLocal(Type.PrimitiveType.INT, idxIdx));
                ops.add(new KofJump(startLabel));
                ops.add(new KofLabel(endLabel));
                yield localIdx;
            }
            case ThrowStmt ts -> {
                localIdx = emitExpression(ts.expression(), ops, owner, localIdx, locals);
                Type excType = inferExprType(ts.expression(), locals);
                if (BuiltinTypes.isString(excType) && target == Target.JVM) {
                    int tmp = localIdx++;
                    locals.add(new IRLocalVariable(tmp, "#exc", BuiltinTypes.STRING));
                    ops.add(new KofStoreLocal(BuiltinTypes.STRING, tmp));
                    Type runtimeExc = new Type.ClassType("java.lang", "RuntimeException", List.of());
                    ops.add(new KofNewObject(runtimeExc, List.of(BuiltinTypes.STRING)));
                    ops.add(new KofDup());
                    ops.add(new KofLoadLocal(BuiltinTypes.STRING, tmp));
                    ops.add(new KofCall(runtimeExc, "<init>", List.of(BuiltinTypes.STRING),
                            Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                }
                ops.add(new KofThrow());
                yield localIdx;
            }
            case AssertStmt asrt -> {
                localIdx = emitExpression(asrt.condition(), ops, owner, localIdx, locals);
                LabelId okLabel = LabelId.create();
                LabelId failLabel = LabelId.create();
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                ops.add(new KofConditionalJump(KofComparison.EQ, failLabel, okLabel));
                ops.add(new KofLabel(failLabel));
                String message = asrt.message() != null ? asrt.message() : "assertion failed";
                if (target == Target.JVM) {
                    int tmp = localIdx++;
                    locals.add(new IRLocalVariable(tmp, "#exc", BuiltinTypes.STRING));
                    ops.add(new KofLoadLiteral(BuiltinTypes.STRING, message));
                    ops.add(new KofStoreLocal(BuiltinTypes.STRING, tmp));
                    Type runtimeExc = new Type.ClassType("java.lang", "RuntimeException", List.of());
                    ops.add(new KofNewObject(runtimeExc, List.of(BuiltinTypes.STRING)));
                    ops.add(new KofDup());
                    ops.add(new KofLoadLocal(BuiltinTypes.STRING, tmp));
                    ops.add(new KofCall(runtimeExc, "<init>", List.of(BuiltinTypes.STRING),
                            Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                } else {
                    ops.add(new KofLoadLiteral(BuiltinTypes.STRING, message));
                }
                ops.add(new KofThrow());
                ops.add(new KofLabel(okLabel));
                yield localIdx;
            }
            case SpawnStmt ss -> {
                if (target == Target.JS) {
                    // JS é single-threaded: fire-and-forget degenera em execução
                    // imediata do corpo (ordem de output preservada)
                    LambdaExpr jsLe = ss.expression() instanceof LambdaExpr l0 ? l0
                            : new LambdaExpr(ss.position(), List.of(),
                                    List.of(new ExpressionStmt(ss.position(), ss.expression())));
                    for (StatementNode st : jsLe.body()) {
                        localIdx = emitStatement(st, ops, owner, localIdx, locals,
                                Type.PrimitiveType.VOID);
                    }
                    yield localIdx;
                }
                if (target == Target.NATIVE) {
                    if (currentDiagnostics != null) {
                        currentDiagnostics.error("", 0, 0, 0,
                                "spawn: not supported on the Native target yet (JVM supports it)", "CONC001");
                    }
                    yield localIdx;
                }
                if (target == Target.JS) {
                    // nunca silencioso: kof_spawn não tem rota no runtime JS
                    if (currentDiagnostics != null) {
                        currentDiagnostics.error("", 0, 0, 0,
                                "spawn: not supported on the js target yet (CONC003)", "CONC003");
                    }
                    yield localIdx;
                }
                if (target == Target.ANDROID) {
                    // ART não tem virtual threads (Java 21) — a semântica de
                    // spawn não é realizável no alvo hoje
                    if (currentDiagnostics != null) {
                        currentDiagnostics.error("", 0, 0, 0,
                                "spawn: not supported on the Android target yet (no virtual threads on ART)", "AND001");
                    }
                    yield localIdx;
                }
                LambdaExpr le;
                if (ss.expression() instanceof LambdaExpr le0) {
                    le = le0;
                } else {
                    le = new LambdaExpr(ss.position(), List.of(),
                            List.of(new ExpressionStmt(ss.position(), ss.expression())));
                }
                Type.FunctionType ft = new Type.FunctionType(List.of(), Type.PrimitiveType.VOID, null);
                String lambdaClass = lambdaClass(le, ft, List.of());
                Type taskType = new Type.ClassType("", lambdaClass, List.of());
                ops.add(new KofNewObject(taskType, List.of()));
                ops.add(new KofDup());
                ops.add(new KofCall(taskType, "<init>", List.of(), Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                        "kof_spawn", List.of(taskType), Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
                yield localIdx;
            }
            case TryStmt ts -> {
                LabelId tryStart = LabelId.create();
                LabelId tryEnd = LabelId.create();
                LabelId doneLabel = LabelId.create();
                boolean hasFinally = !ts.finallyBody().isEmpty();
                LabelId finallyLabel = LabelId.create();
                LabelId rethrowLabel = hasFinally ? LabelId.create() : doneLabel;
                LabelId catchAllLabel = LabelId.create();
                LabelId primaryHandler = LabelId.create();
                boolean hasCatch = !ts.catchClauses().isEmpty();
                String primaryExcType = hasCatch ? ts.catchClauses().getFirst().exceptionType() : "Throwable";
                int primaryExcLocal = localIdx++;
                if (hasCatch) {
                    locals.add(new IRLocalVariable(primaryExcLocal, ts.catchClauses().getFirst().exceptionName(),
                            toType(primaryExcType)));
                } else if (hasFinally) {
                    locals.add(new IRLocalVariable(primaryExcLocal, "#excTmp",
                            new Type.ClassType("java.lang", "Throwable", List.of())));
                }
                ops.add(new KofTryStart(tryStart, tryEnd,
                        hasCatch ? primaryHandler : catchAllLabel, primaryExcType, primaryExcLocal));
                for (StatementNode s : ts.tryBody()) {
                    localIdx = emitStatement(s, ops, owner, localIdx, locals, returnType);
                }
                ops.add(new KofJump(finallyLabel));
                ops.add(new KofLabel(tryEnd));
                for (int ci = 0; ci < ts.catchClauses().size(); ci++) {
                    CatchClause cc = ts.catchClauses().get(ci);
                    LabelId handlerLabel = ci == 0 ? primaryHandler : LabelId.create();
                    int excIdx = ci == 0 ? primaryExcLocal : localIdx++;
                    if (ci > 0) {
                        locals.add(new IRLocalVariable(excIdx, cc.exceptionName(), toType(cc.exceptionType())));
                    }
                    ops.add(new KofCatchStart(handlerLabel, cc.exceptionType(), excIdx));
                    localIdx = emitStatement(new BlockStmt(cc.position(), cc.body()), ops, owner, localIdx, locals, returnType);
                    ops.add(new KofJump(finallyLabel));
                }
                if (hasFinally) {
                    int excTmp = hasCatch ? localIdx++ : primaryExcLocal;
                    if (hasCatch) {
                        locals.add(new IRLocalVariable(excTmp, "#excTmp",
                                new Type.ClassType("java.lang", "Throwable", List.of())));
                    }
                    ops.add(new KofCatchStart(catchAllLabel, "Throwable", excTmp));
                    ops.add(new KofJump(rethrowLabel));
                    ops.add(new KofTryEnd());
                    ops.add(new KofLabel(finallyLabel));
                    for (StatementNode s : ts.finallyBody()) {
                        localIdx = emitStatement(s, ops, owner, localIdx, locals, returnType);
                    }
                    ops.add(new KofJump(doneLabel));
                    ops.add(new KofLabel(rethrowLabel));
                    for (StatementNode s : ts.finallyBody()) {
                        localIdx = emitStatement(s, ops, owner, localIdx, locals, returnType);
                    }
                    ops.add(new KofLoadLocal(new Type.ClassType("java.lang", "Throwable", List.of()), excTmp));
                    ops.add(new KofThrow());
                } else {
                    ops.add(new KofTryEnd());
                    ops.add(new KofLabel(finallyLabel));
                }
                ops.add(new KofLabel(doneLabel));
                yield localIdx;
            }
            case SwitchStmt ss -> {
                LabelId endLabel = LabelId.create();
                LabelId defaultLabel = LabelId.create();
                Type switchType = inferExprType(ss.expression(), locals);
                // ── exaustividade: switch sobre enum precisa cobrir todas as
                // constantes ou ter default (nunca cair silenciosamente)
                boolean enumSwitch = false;
                java.util.List<String> missing = java.util.List.of();
                if (switchType instanceof Type.ClassType sct && sct.packageName().isEmpty()
                        && !enumConstantsOf(sct.name()).isEmpty()) {
                    enumSwitch = true;
                    java.util.Set<String> covered = new java.util.HashSet<>();
                    for (SwitchCase sc : ss.cases()) {
                        String cn = enumConstantOfExpr(sc.value());
                        if (cn != null) covered.add(cn);
                    }
                    missing = enumConstantsOf(sct.name()).stream()
                            .filter(c -> !covered.contains(c)).toList();
                    if (!missing.isEmpty() && ss.defaultBody().isEmpty()
                            && currentDiagnostics != null) {
                        currentDiagnostics.error(ss.position() != null ? ss.position().file() : "",
                                ss.position() != null ? ss.position().line() : 0,
                                ss.position() != null ? ss.position().column() : 0, 0,
                                "switch sobre '" + sct.name() + "' não cobre: "
                                        + String.join(", ", missing)
                                        + " (adicione default ou os casos faltantes)",
                                "SEM031");
                    }
                }
                int switchTmp = localIdx++;
                localIdx = emitExpression(ss.expression(), ops, owner, localIdx, locals);
                ops.add(new KofStoreLocal(switchType, switchTmp));
                locals.add(new IRLocalVariable(switchTmp, "#switch", switchType));
                List<LabelId> testLabels = new ArrayList<>();
                List<LabelId> bodyLabels = new ArrayList<>();
                for (int i = 0; i < ss.cases().size(); i++) {
                    testLabels.add(LabelId.create());
                    bodyLabels.add(LabelId.create());
                }
                for (int i = 0; i < ss.cases().size(); i++) {
                    if (i > 0) ops.add(new KofLabel(testLabels.get(i)));
                    SwitchCase sc = ss.cases().get(i);
                    ops.add(new KofLoadLocal(switchType, switchTmp));
                    localIdx = emitExpression(sc.value(), ops, owner, localIdx, locals);
                    if (enumSwitch) {
                        // comparação por conteúdo (o valor do enum é o nome)
                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_equals",
                                List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                                Type.PrimitiveType.BOOL, KofCallKind.FUNCTION));
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                        ops.add(new KofConditionalJump(KofComparison.NE, bodyLabels.get(i),
                                i + 1 < ss.cases().size() ? testLabels.get(i + 1) : defaultLabel));
                    } else {
                        ops.add(new KofBinary(KofBinaryOp.SUB, switchType));
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                        ops.add(new KofConditionalJump(KofComparison.EQ, bodyLabels.get(i),
                                i + 1 < ss.cases().size() ? testLabels.get(i + 1) : defaultLabel));
                    }
                }
                for (int i = 0; i < ss.cases().size(); i++) {
                    SwitchCase sc = ss.cases().get(i);
                    ops.add(new KofLabel(bodyLabels.get(i)));
                    localIdx = emitStatement(new BlockStmt(sc.position(), sc.body()), ops, owner, localIdx, locals, returnType);
                    ops.add(new KofJump(endLabel));
                }
                ops.add(new KofLabel(defaultLabel));
                if (!ss.defaultBody().isEmpty()) {
                    localIdx = emitStatement(new BlockStmt(ss.defaultBody().get(0).position(), ss.defaultBody()), ops, owner, localIdx, locals, returnType);
                }
                ops.add(new KofLabel(endLabel));
                yield localIdx;
            }
            default -> localIdx;
        };
    }

    private int emitExpression(ExpressionNode expr, List<KofOperation> ops, String owner, int localIdx,
                               List<IRLocalVariable> locals) {
        return switch (expr) {
            case LiteralExpr lit -> {
                switch (lit.kind()) {
                    case ConcreteLiteralKind.INT -> ops.add(KofLoadLiteral.ofInt(parseIntLiteral(lit.value())));
                    case ConcreteLiteralKind.LONG -> ops.add(KofLoadLiteral.ofLong(Long.parseLong(stripSuffix(lit.value()))));
                    case ConcreteLiteralKind.FLOAT -> ops.add(KofLoadLiteral.ofFloat(Float.parseFloat(stripSuffix(lit.value()))));
                    case ConcreteLiteralKind.DOUBLE -> ops.add(KofLoadLiteral.ofDouble(Double.parseDouble(stripSuffix(lit.value()))));
                    case ConcreteLiteralKind.STRING -> ops.add(KofLoadLiteral.ofString(lit.value()));
                    case ConcreteLiteralKind.BOOLEAN -> ops.add(KofLoadLiteral.ofBool(Boolean.parseBoolean(lit.value())));
                    case ConcreteLiteralKind.CHAR -> ops.add(KofLoadLiteral.ofInt(lit.value().charAt(0)));
                    case ConcreteLiteralKind.NULL -> ops.add(KofLoadLiteral.ofNull());
                }
                yield localIdx;
            }
            case IdentifierExpr ie -> {
                if (loweringMain && "args".equals(ie.name())) {
                    if (mainArgsListField) {
                        // args: List<String> — the converted list lives in
                        // slot 1 (set by the main prologue)
                        ops.add(new KofLoadLocal(KofProcess.STRING_LIST, 1));
                    } else if (target == Target.JVM) {
                        ops.add(new KofLoadLocal(new Type.ArrayType(BuiltinTypes.STRING), 0));
                    } else {
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                        ops.add(new KofNewArray(BuiltinTypes.STRING));
                    }
                    yield localIdx;
                }
                // constante de enum não-qualificada → literal String tipado
                if (currentUnit != null && findLocalVar(ie.name(), locals) == null
                        && (semanticAnalyzer == null || !semanticAnalyzer.allClasses().containsKey(ie.name()))) {
                    for (AstNode d0 : currentUnit.declarations()) {
                        if (d0 instanceof EnumDeclarationNode en0
                                && en0.constants().contains(ie.name())) {
                            ops.add(new KofLoadLiteral(new Type.ClassType("", en0.name(), List.of()),
                                    ie.name()));
                            yield localIdx;
                        }
                    }
                }
                for (int i = locals.size() - 1; i >= 0; i--) {
                    if (locals.get(i).name().equals(ie.name())) {
                        IRLocalVariable lv = locals.get(i);
                        if (isBoxType(lv.type())) {
                            ops.add(new KofLoadLocal(lv.type(), lv.index()));
                            ops.add(new KofLoadField(lv.type(), "value",
                                    boxValueTypes.get(((Type.ClassType) lv.type()).name())));
                        } else {
                            ops.add(new KofLoadLocal(lv.type(), lv.index()));
                        }
                        yield localIdx;
                    }
                }
                if (!owner.isEmpty() && semanticAnalyzer != null) {
                    String className = owner.substring(owner.lastIndexOf('/') + 1);
                    SymbolTable.ClassSymbol cs = semanticAnalyzer.getClass(className);
                    if (cs == null) {
                        for (var entry : semanticAnalyzer.allClasses().entrySet()) {
                            if (entry.getValue().internalName().equals(owner)) { cs = entry.getValue(); break; }
                        }
                    }
                    if (cs != null) {
                        SymbolTable.Symbol fieldSym = resolveFieldInHierarchy(cs.name(), ie.name());
                        if (fieldSym instanceof SymbolTable.FieldSymbol fs) {
                            ops.add(new KofLoadLocal(cs.type(), 0));
                            ops.add(new KofLoadField(cs.type(), ie.name(), fs.type()));
                            yield localIdx;
                        } else if (fieldSym instanceof SymbolTable.MethodSymbol ms
                                && ms.parameterTypes().isEmpty()) {
                            // Record/class-with-primary-constructor: the
                            // accessor method (kind()) shares the component
                            // field name (kind); a bare identifier refers to
                            // the field, not the accessor call.
                            ops.add(new KofLoadLocal(cs.type(), 0));
                            ops.add(new KofLoadField(cs.type(), ie.name(), ms.returnType()));
                            yield localIdx;
                        }
                    }
                }
                ops.add(new KofLoadLocal(Type.UnknownType.UNKNOWN, 0));
                yield localIdx;
            }
            case BinaryExpr bin -> {
                if ("instanceof".equals(bin.operator()) || "as".equals(bin.operator())) {
                    localIdx = emitExpression(bin.left(), ops, owner, localIdx, locals);
                    Type targetType = Type.UnknownType.UNKNOWN;
                    if (bin.right() instanceof IdentifierExpr ie) {
                        // toType resolve imports ("View" + import → android.view.View)
                        targetType = toType(ie.name());
                    }
                    if ("instanceof".equals(bin.operator())) {
                        ops.add(new KofInstanceOf(targetType));
                    } else {
                        ops.add(new KofCheckCast(targetType));
                        // o resultado do cast tem o tipo alvo — o próximo
                        // acesso (campo/método) precisa enxergá-lo
                        if (bin.left() instanceof IdentifierExpr lie && !Type.isUnknown(targetType)) {
                            for (int li = locals.size() - 1; li >= 0; li--) {
                                if (locals.get(li).name().equals(lie.name())) {
                                    locals.set(li, new IRLocalVariable(locals.get(li).index(),
                                            lie.name(), targetType));
                                    break;
                                }
                            }
                        }
                    }
                    yield localIdx;
                }
                // Short-circuit evaluation for || and &&:
                // a || b → eval a; if true, jump to true_label; eval b; result = b
                // a && b → eval a; if false, jump to false_label; eval b; result = b
                if (("||".equals(bin.operator()) || "&&".equals(bin.operator()))
                        && target != Target.JS) {
                    LabelId trueLabel = LabelId.create();
                    LabelId falseLabel = LabelId.create();
                    LabelId endLabel = LabelId.create();
                    localIdx = emitExpression(bin.left(), ops, owner, localIdx, locals);
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                    if ("||".equals(bin.operator())) {
                        ops.add(new KofConditionalJump(KofComparison.NE, trueLabel, falseLabel));
                    } else {
                        ops.add(new KofConditionalJump(KofComparison.NE, falseLabel, trueLabel));
                    }
                    ops.add(new KofLabel(falseLabel));
                    localIdx = emitExpression(bin.right(), ops, owner, localIdx, locals);
                    ops.add(new KofJump(endLabel));
                    ops.add(new KofLabel(trueLabel));
                    if ("||".equals(bin.operator())) {
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
                    } else {
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                    }
                    ops.add(new KofLabel(endLabel));
                    yield localIdx;
                }
                // Left-associative chains (huge string concatenations in
                // generated UIs, editors) are emitted iteratively instead of
                // recursing: deep chains would overflow the compiler stack.
                java.util.List<BinaryExpr> chain = new ArrayList<>();
                ExpressionNode cursor = bin;
                while (cursor instanceof BinaryExpr be) {
                    chain.add(be);
                    cursor = be.left();
                }
                localIdx = emitExpression(cursor, ops, owner, localIdx, locals);
                Type accType = inferExprType(cursor, locals);
                for (int ci = chain.size() - 1; ci >= 0; ci--) {
                    BinaryExpr be = chain.get(ci);
                    Type rightType = inferExprType(be.right(), locals);
                    boolean isArithmetic = switch (be.operator()) {
                        case "+", "-", "*", "/", "%" -> true;
                        default -> false;
                    };
                    boolean isNumericComparison = isComparisonOp(be.operator())
                            && isNumeric(accType) && isNumeric(rightType);
                    if ((isArithmetic || isNumericComparison)
                            && isNumeric(accType) && isNumeric(rightType)) {
                        // OBS-009: divisão (ou resto) por zero constante é
                        // detectada em compile-time — o compilador conhece a
                        // intenção; o usuário não vê o ArithmeticException do
                        // JVM.
                        boolean integerArithmetic = Type.isInteger(accType) && Type.isInteger(rightType);
                        if (integerArithmetic && ("/".equals(be.operator()) || "%".equals(be.operator()))
                                && be.right() instanceof LiteralExpr lit
                                && isZeroLiteral(lit)) {
                            if (currentDiagnostics != null) {
                                currentDiagnostics.error(be.position() != null ? be.position().file() : "",
                                        be.position() != null ? be.position().line() : 0,
                                        be.position() != null ? be.position().column() : 0,
                                        0,
                                        "division by zero: constant " + be.operator()
                                                + " by zero is not allowed",
                                        "ARITH001");
                            }
                            yield localIdx;
                        }
                        Type commonType = commonNumericType(accType, rightType);
                        if (!fpSupportedOnNative(commonType, be.position())) {
                            yield localIdx;
                        }
                        emitWideningIfNeeded(ops, accType, commonType);
                        localIdx = emitExpression(be.right(), ops, owner, localIdx, locals);
                        emitWideningIfNeeded(ops, rightType, commonType);
                        ops.add(new KofBinary(mapArithmeticOp(be.operator()), commonType));
                        accType = commonType;
                    } else if ("+".equals(be.operator())
                            && (Type.isString(accType) || Type.isString(rightType))) {
                        // concatenação com float/double no Native formataria
                        // os bits como inteiro — diagnóstico em vez de lixo
                        if ((Type.isString(accType) && isFloatingPoint(rightType))
                                || (Type.isString(rightType) && isFloatingPoint(accType))) {
                            fpSupportedOnNative(isFloatingPoint(rightType) ? rightType : accType,
                                    be.position());
                            yield localIdx;
                        }
                        if (!Type.isString(accType) && isPrimitiveType(accType)) boxPrimitive(ops, accType);
                        ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                                List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING, KofCallKind.STATIC));
                        localIdx = emitExpression(be.right(), ops, owner, localIdx, locals);
                        if (!Type.isString(rightType) && isPrimitiveType(rightType)) boxPrimitive(ops, rightType);
                        ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                                List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING, KofCallKind.STATIC));
                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                                List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                                BuiltinTypes.STRING, KofCallKind.FUNCTION));
                        accType = BuiltinTypes.STRING;
                    } else if (("==".equals(be.operator()) || "!=".equals(be.operator()))
                            && ((be.right() instanceof LiteralExpr rl
                                    && rl.kind() == ConcreteLiteralKind.NULL
                                    && isPrimitiveType(accType))
                                || (be.left() instanceof LiteralExpr ll
                                    && ll.kind() == ConcreteLiteralKind.NULL
                                    && isPrimitiveType(rightType)))) {
                        // primitivo nunca é null: == → false, != → true
                        // (o lado não-nulo já está na pilha — descarta)
                        ops.add(new KofPop());
                        boolean eq = "==".equals(be.operator());
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.BOOL, eq ? 0 : 1));
                        accType = Type.PrimitiveType.BOOL;
                    } else if (("==".equals(be.operator()) || "!=".equals(be.operator()))
                            && (Type.isString(accType) || Type.isString(rightType)
                                || isEnumType(accType) || isEnumType(rightType))) {
                        localIdx = emitExpression(be.right(), ops, owner, localIdx, locals);
                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_equals",
                                List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                                Type.PrimitiveType.BOOL, KofCallKind.FUNCTION));
                        if ("!=".equals(be.operator())) {
                            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                            ops.add(new KofBinary(KofBinaryOp.EQ, Type.PrimitiveType.INT));
                        }
                        accType = Type.PrimitiveType.BOOL;
                    } else {
                        localIdx = emitExpression(be.right(), ops, owner, localIdx, locals);
                        Type operandType = accType;
                        switch (be.operator()) {
                            case "+" -> ops.add(new KofBinary(KofBinaryOp.ADD, operandType));
                            case "-" -> ops.add(new KofBinary(KofBinaryOp.SUB, operandType));
                            case "*" -> ops.add(new KofBinary(KofBinaryOp.MUL, operandType));
                            case "/" -> ops.add(new KofBinary(KofBinaryOp.DIV, operandType));
                            case "%" -> ops.add(new KofBinary(KofBinaryOp.MOD, operandType));
                            case "==" -> ops.add(new KofBinary(KofBinaryOp.EQ, operandType));
                            case "!=" -> ops.add(new KofBinary(KofBinaryOp.NE, operandType));
                            case "<" -> ops.add(new KofBinary(KofBinaryOp.LT, operandType));
                            case "<=" -> ops.add(new KofBinary(KofBinaryOp.LE, operandType));
                            case ">" -> ops.add(new KofBinary(KofBinaryOp.GT, operandType));
                            case ">=" -> ops.add(new KofBinary(KofBinaryOp.GE, operandType));
                            case "&&" -> ops.add(new KofBinary(KofBinaryOp.AND, operandType));
                            case "||" -> ops.add(new KofBinary(KofBinaryOp.OR, operandType));
                            case "&" -> ops.add(new KofBinary(KofBinaryOp.AND, operandType));
                            case "|" -> ops.add(new KofBinary(KofBinaryOp.OR, operandType));
                            case "^" -> ops.add(new KofBinary(KofBinaryOp.XOR, operandType));
                            case "<<" -> ops.add(new KofBinary(KofBinaryOp.SHL, operandType));
                            case ">>" -> ops.add(new KofBinary(KofBinaryOp.SHR, operandType));
                            case ">>>" -> ops.add(new KofBinary(KofBinaryOp.USHR, operandType));
                            default -> ops.add(new KofBinary(KofBinaryOp.ADD, operandType));
                        }
                        accType = switch (be.operator()) {
                            case "==", "!=", "<", "<=", ">", ">=" -> Type.PrimitiveType.BOOL;
                            default -> accType;
                        };
                    }
                }
                yield localIdx;
            }
            case UnaryExpr ue -> {
                Type operandType = inferExprType(ue.operand(), locals);
                if ("++".equals(ue.operator()) || "--".equals(ue.operator())) {
                    localIdx = emitIncrement(ue, operandType, ops, owner, localIdx, locals);
                    yield localIdx;
                }
                localIdx = emitExpression(ue.operand(), ops, owner, localIdx, locals);
                if ("-".equals(ue.operator())) {
                    ops.add(new KofUnary(KofUnaryOp.NEG, operandType));
                } else if ("!".equals(ue.operator())) {
                    ops.add(new KofUnary(KofUnaryOp.NOT, operandType));
                }
                yield localIdx;
            }
            case MethodCallExpr mc -> {
                // User-defined classes take precedence over builtin helpers
                // with the same name: ClassName(args) is implicit construction.
                SymbolTable.ClassSymbol userCtor = semanticAnalyzer != null
                        ? semanticAnalyzer.getClass(mc.methodName()) : null;
                if (mc.receiver() == null && userCtor != null) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                    SymbolTable.ConstructorSymbol ctor = null;
                    SymbolTable.Symbol ctorSym = userCtor.members().resolve("<init>");
                    if (ctorSym instanceof SymbolTable.ConstructorSymbol c) ctor = c;
                    ops.add(new KofNewObject(userCtor.type(), argTypes));
                    ops.add(new KofDup());
                    List<Type> ctorParamTypes = (ctor != null
                            && ctor.parameterTypes().size() == mc.arguments().size())
                            ? ctor.parameterTypes() : argTypes;
                    localIdx = emitArgumentsWithFormalTypes(mc.arguments(), ctorParamTypes, ops, owner, localIdx, locals);
                    ops.add(new KofCall(userCtor.type(), "<init>", ctorParamTypes,
                            Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                    yield localIdx;
                }
                if (mc.receiver() == null && "now".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                    ops.add(new KofCall(new Type.ClassType("kof", "time", List.of()), "kof_now",
                            List.of(), Type.PrimitiveType.LONG, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "readLine".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                    ops.add(new KofCall(new Type.ClassType("kof", "io", List.of()), "kof_read_line",
                            List.of(), BuiltinTypes.STRING, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && KofWeb.isContextFunction(mc.methodName())) {
                    KofWeb.WebCall webCtx = KofWeb.contextCall(mc.methodName(), mc.arguments().size());
                    if (webCtx != null) {
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(KofWeb.APP, webCtx.function(), webCtx.parameterTypes(),
                                webCtx.returnType(), KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                }
                if (mc.receiver() == null && "transaction".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    if (!KofDb.supportedOn(target)) {
                        if (currentDiagnostics != null) {
                            currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                    mc.position() != null ? mc.position().line() : 0,
                                    mc.position() != null ? mc.position().column() : 0,
                                    0,
                                    "transaction: not available on the " + target
                                            + " target yet (" + KofDb.gapCode() + ")",
                                    KofDb.gapCode());
                        }
                        yield localIdx;
                    }
                    localIdx = emitExpression(mc.arguments().get(0), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.db", "Db", List.of()),
                            "kof_db_transaction", List.of(Type.UnknownType.UNKNOWN),
                            Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "readFile".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    localIdx = emitExpression(mc.arguments().get(0), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof", "io", List.of()), "kof_read_file",
                            List.of(BuiltinTypes.STRING), BuiltinTypes.STRING, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "writeFile".equals(mc.methodName()) && mc.arguments().size() == 2) {
                    localIdx = emitExpression(mc.arguments().get(0), ops, owner, localIdx, locals);
                    localIdx = emitExpression(mc.arguments().get(1), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof", "io", List.of()), "kof_write_file",
                            List.of(BuiltinTypes.STRING, BuiltinTypes.STRING), Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && KofIo.isConstructor(mc.methodName()) && mc.arguments().size() == 1) {
                    localIdx = emitExpression(mc.arguments().get(0), ops, owner, localIdx, locals);
                    yield localIdx;
                }
                if (mc.receiver() == null && "Color".equals(mc.methodName()) && mc.arguments().size() == 3) {
                    localIdx = emitPackedColor(mc.arguments(), ops, owner, localIdx, locals);
                    yield localIdx;
                }
                if (mc.receiver() == null && "Color".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    localIdx = emitExpression(mc.arguments().get(0), ops, owner, localIdx, locals);
                    yield localIdx;
                }
                if (mc.receiver() == null && ("Window".equals(mc.methodName()) || "Label".equals(mc.methodName()))
                        && mc.arguments().size() == 1) {
                    localIdx = emitExpression(mc.arguments().get(0), ops, owner, localIdx, locals);
                    String fn = "Window".equals(mc.methodName()) ? "kof_ui_window_new" : "kof_ui_label_new";
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            fn, List.of(BuiltinTypes.STRING), Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "Input".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    localIdx = emitExpression(mc.arguments().get(0), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_input_new", List.of(BuiltinTypes.STRING),
                            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && ("Column".equals(mc.methodName()) || "Row".equals(mc.methodName()))
                        && mc.arguments().size() == 1) {
                    localIdx = emitExpression(mc.arguments().get(0), ops, owner, localIdx, locals);
                    String fn = "Column".equals(mc.methodName()) ? "kof_ui_column_new" : "kof_ui_row_new";
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            fn, List.of(new Type.ClassType("kof", "List", List.of(Type.PrimitiveType.INT))),
                            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "View".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    localIdx = emitExpression(mc.arguments().get(0), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_view_new", List.of(Type.PrimitiveType.INT),
                            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "Style".equals(mc.methodName()) && mc.arguments().size() == 4) {
                    for (ExpressionNode arg : mc.arguments()) {
                        localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                    }
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_style_new", List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT,
                            Type.PrimitiveType.INT, Type.PrimitiveType.INT),
                            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "Link".equals(mc.methodName()) && mc.arguments().size() == 2) {
                    for (ExpressionNode arg : mc.arguments()) {
                        localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                    }
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_link_new", List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "Image".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    localIdx = emitExpression(mc.arguments().get(0), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_image_new", List.of(BuiltinTypes.STRING),
                            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "Icon".equals(mc.methodName())
                        && (mc.arguments().size() == 1 || mc.arguments().size() == 2)) {
                    for (ExpressionNode arg : mc.arguments()) {
                        localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                    }
                    if (mc.arguments().size() == 2) {
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_icon_new_size", List.of(BuiltinTypes.STRING, Type.PrimitiveType.INT),
                                Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    } else {
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_icon_new", List.of(BuiltinTypes.STRING),
                                Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                }
                if (mc.receiver() == null && "Font".equals(mc.methodName())
                        && (mc.arguments().size() == 2 || mc.arguments().size() == 3)) {
                    for (ExpressionNode arg : mc.arguments()) {
                        localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                    }
                    if (mc.arguments().size() == 3) {
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_font_new_bold", List.of(BuiltinTypes.STRING,
                                        Type.PrimitiveType.INT, Type.PrimitiveType.BOOL),
                                Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    } else {
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_font_new", List.of(BuiltinTypes.STRING, Type.PrimitiveType.INT),
                                Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                }
                if (mc.receiver() == null && "Button".equals(mc.methodName())
                        && (mc.arguments().size() == 1 || mc.arguments().size() == 2)) {
                    for (ExpressionNode arg : mc.arguments()) {
                        localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                    }
                    if (mc.arguments().size() == 2) {
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_button_new_action",
                                List.of(BuiltinTypes.STRING, Type.UnknownType.UNKNOWN),
                                Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    } else {
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_button_new", List.of(BuiltinTypes.STRING),
                                Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                }
                if ("listOf".equals(mc.methodName()) && mc.receiver() == null) {
                    Type elemType = listOfElementType(mc, locals);
                    Type listType = new Type.ClassType("kof", "List", List.of(elemType));
                    ops.add(new KofCall(listType, "kof_list_new", List.of(), listType, KofCallKind.FUNCTION));
                    for (ExpressionNode arg : mc.arguments()) {
                        ops.add(new KofDup());
                        localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                        ops.add(new KofCall(listType, "kof_list_add",
                                List.of(inferExprType(arg, locals)), Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
                    }
                    yield localIdx;
                }
                if (mc.receiver() == null && ("poll".equals(mc.methodName()) || "done".equals(mc.methodName()))
                        && mc.arguments().size() == 1
                        && findLocalVar(mc.methodName(), locals) == null) {
                    // sem threads no alvo não há Handle real: gap honesto
                    if (target == Target.NATIVE || target == Target.ANDROID) {
                        if (currentDiagnostics != null) {
                            String code = target == Target.NATIVE ? "CONC001" : "AND001";
                            currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                    mc.position() != null ? mc.position().line() : 0,
                                    mc.position() != null ? mc.position().column() : 0, 0,
                                    mc.methodName() + ": not supported on the " + target
                                            + " target yet (" + code + ")", code);
                        }
                        yield localIdx;
                    }
                    localIdx = emitExpression(mc.arguments().get(0), ops, owner, localIdx, locals);
                    Type hE = inferExprType(mc.arguments().get(0), locals);
                    Type rE = Type.UnknownType.UNKNOWN;
                    if (hE instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()
                            && "poll".equals(mc.methodName())) {
                        rE = ct.typeArguments().get(0);
                    }
                    Type ret = "poll".equals(mc.methodName()) ? rE : Type.PrimitiveType.BOOL;
                    ops.add(new KofCall(
                            new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                            "kof_" + mc.methodName(),
                            List.of(hE), ret, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "__kof_spawn_expr".equals(mc.methodName())) {
                    if (target == Target.JS) {
                        // sequencial: o corpo roda agora; o handle só memoiza
                        ExpressionNode jsBody = mc.arguments().get(0);
                        Type jsT = inferExprType(jsBody, locals);
                        localIdx = emitExpression(jsBody, ops, owner, localIdx, locals);
                        ops.add(new KofCall(
                                new Type.ClassType("kof.concurrent", "Handle", List.of(jsT)),
                                "kof_spawn_result", List.of(jsT),
                                new Type.ClassType("kof.concurrent", "Handle", List.of(jsT)),
                                KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                    if (target != Target.JVM) {
                        if (currentDiagnostics != null) {
                            String code = target == Target.NATIVE ? "CONC001"
                                    : target == Target.ANDROID ? "AND001" : "CONC003";
                            currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                    mc.position() != null ? mc.position().line() : 0,
                                    mc.position() != null ? mc.position().column() : 0, 0,
                                    "spawn expr: not supported on the " + target + " target yet (" + code + ")",
                                    code);
                        }
                        yield localIdx;
                    }
                    ExpressionNode body = mc.arguments().get(0);
                    Type resultT = inferExprType(body, locals);
                    Type handleT = new Type.ClassType("kof.concurrent", "Handle", List.of(resultT));
                    LambdaExpr le = body instanceof LambdaExpr l0 ? l0
                            : new LambdaExpr(body.position() != null ? body.position() : mc.position(),
                                    List.of(), List.of(new ExpressionStmt(
                                            body.position() != null ? body.position() : mc.position(), body)));
                    Type.FunctionType ft = new Type.FunctionType(List.of(), resultT, null);
                    String lambdaClass = lambdaClass(le, ft, List.of());
                    Type taskType = new Type.ClassType("", lambdaClass, List.of());
                    ops.add(new KofNewObject(taskType, List.of()));
                    ops.add(new KofDup());
                    ops.add(new KofCall(taskType, "<init>", List.of(),
                            Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                    ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                            "kof_spawn_result", List.of(taskType), handleT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() == null && "__kof_await".equals(mc.methodName())) {
                    if (target == Target.NATIVE || target == Target.ANDROID) {
                        if (currentDiagnostics != null) {
                            String code = target == Target.NATIVE ? "CONC001" : "AND001";
                            currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                    mc.position() != null ? mc.position().line() : 0,
                                    mc.position() != null ? mc.position().column() : 0, 0,
                                    "await: not supported on the " + target + " target yet (" + code + ")",
                                    code);
                        }
                        yield localIdx;
                    }
                    localIdx = emitExpression(mc.arguments().get(0), ops, owner, localIdx, locals);
                    Type hT = inferExprType(mc.arguments().get(0), locals);
                    Type resT = Type.UnknownType.UNKNOWN;
                    if (hT instanceof Type.ClassType ct
                            && !ct.typeArguments().isEmpty()) resT = ct.typeArguments().get(0);
                    ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                            "kof_await", List.of(hT), resT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (mc.receiver() instanceof IdentifierExpr rid && isEnumName(rid.name())
                        && !isLocalVarName(rid.name(), locals)) {
                    Type enumT = new Type.ClassType("", rid.name(), List.of());
                    // lista interna com elemento STRING (runtime do enum é o nome);
                    // a tipagem List<Color> fica na checagem de tipos
                    Type stringListT = new Type.ClassType("kof", "List", List.of(BuiltinTypes.STRING));
                    if ("values".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                        ops.add(new KofCall(stringListT,
                                "kof_list_new", List.of(), stringListT,
                                KofCallKind.FUNCTION));
                        for (String c : enumConstantsOf(rid.name())) {
                            ops.add(new KofDup());
                            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, c));
                            ops.add(new KofCall(stringListT,
                                    "kof_list_add", List.of(BuiltinTypes.STRING), Type.PrimitiveType.VOID,
                                    KofCallKind.INSTANCE));
                        }
                        yield localIdx;
                    }
                    if ("valueOf".equals(mc.methodName()) && mc.arguments().size() == 1) {
                        Type listT = stringListT;
                        ops.add(new KofCall(listT, "kof_list_new", List.of(), listT,
                                KofCallKind.FUNCTION));
                        for (String c : enumConstantsOf(rid.name())) {
                            ops.add(new KofDup());
                            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, c));
                            ops.add(new KofCall(listT, "kof_list_add", List.of(enumT),
                                    Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
                        }
                        localIdx = emitExpression(mc.arguments().get(0), ops, owner, localIdx, locals);
                        ops.add(new KofCall(enumT, "kof_enum_value_of",
                                List.of(listT, BuiltinTypes.STRING), enumT, KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                    yield localIdx;
                }
                if ("mapOf".equals(mc.methodName()) && mc.receiver() == null) {

                    Type keyType = Type.UnknownType.UNKNOWN;
                    Type valueType = Type.UnknownType.UNKNOWN;
                    if (!mc.arguments().isEmpty()) {
                        // Infer from first argument if it is a Pair or two values? For now, use Unknown
                        // mapOf() with no args creates empty Map<Unknown, Unknown>
                    }
                    Type mapType = new Type.ClassType("kof", "Map", List.of(keyType, valueType));
                    ops.add(new KofCall(mapType, "kof_map_new", List.of(), mapType, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if ("setOf".equals(mc.methodName()) && mc.receiver() == null) {
                    Type elemType = Type.UnknownType.UNKNOWN;
                    if (!mc.arguments().isEmpty()) elemType = inferExprType(mc.arguments().get(0), locals);
                    Type setType = new Type.ClassType("kof", "Set", List.of(elemType));
                    ops.add(new KofCall(setType, "kof_set_new", List.of(), setType, KofCallKind.FUNCTION));
                    for (ExpressionNode arg : mc.arguments()) {
                        ops.add(new KofDup());
                        localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                        // VOID na construção: o backend descarta o bool e o set
                        // duplicado continua na pilha para o próximo append
                        ops.add(new KofCall(setType, "kof_set_add",
                                List.of(inferExprType(arg, locals)), Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
                    }
                    yield localIdx;
                }
                if (("print".equals(mc.methodName()) || "println".equals(mc.methodName())) && mc.arguments().size() == 1) {
                    Type printedType = inferExprType(mc.arguments().get(0), locals);
                    if (!fpSupportedOnNative(printedType, mc.position())) {
                        yield localIdx;
                    }
                    ops.add(new KofGetStatic(
                            new Type.ClassType("java.lang", "System", List.of()),
                            "out", new Type.ClassType("java.io", "PrintStream", List.of())));
                    localIdx = emitExpression(mc.arguments().get(0), ops, owner, localIdx, locals);
                    Type argType = inferExprType(mc.arguments().get(0), locals);
                    if (isPrimitiveType(argType)) {
                        if (target == Target.NATIVE) {
                            ops.add(new KofCall(
                                    BuiltinTypes.STRING,
                                    "valueOf", List.of(argType),
                                    BuiltinTypes.STRING, KofCallKind.STATIC));
                        } else {
                            boxPrimitive(ops, argType);
                            ops.add(new KofCall(
                                    BuiltinTypes.STRING,
                                    "valueOf", List.of(Type.UnknownType.UNKNOWN),
                                    BuiltinTypes.STRING, KofCallKind.STATIC));
                        }
                    } else {
                        ops.add(new KofCall(
                                BuiltinTypes.STRING,
                                "valueOf", List.of(Type.UnknownType.UNKNOWN),
                                BuiltinTypes.STRING, KofCallKind.STATIC));
                    }
                    ops.add(new KofCall(
                            new Type.ClassType("java.io", "PrintStream", List.of()),
                            mc.methodName(), List.of(BuiltinTypes.STRING),
                            Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
                } else if (mc.receiver() instanceof IdentifierExpr rid && !isLocalVarName(rid.name(), locals)
                        && semanticAnalyzer != null
                        && semanticAnalyzer.getClass(rid.name()) != null) {
                    // Metodo ESTATICO de classe KOF de outro pacote:
                    // Desconto.aplicar(c) -> invokestatic vendas/regras/Desconto.aplicar
                    SymbolTable.MethodSymbol ksm = null;
                    SymbolTable.Symbol ks = semanticAnalyzer.resolveInHierarchy(rid.name(), mc.methodName());
                    if (ks instanceof SymbolTable.MethodSymbol ms0
                            && ms0.parameterTypes().size() == mc.arguments().size()) {
                        ksm = ms0;
                    }
                    if (ksm != null) {
                        SymbolTable.ClassSymbol kt = semanticAnalyzer.getClass(rid.name());
                        localIdx = emitArgumentsWithFormalTypes(mc.arguments(), ksm.parameterTypes(),
                                ops, owner, localIdx, locals);
                        ops.add(new KofCall(kt.type(), mc.methodName(), ksm.parameterTypes(),
                                ksm.returnType(), KofCallKind.STATIC));
                        yield localIdx;
                    }
                    yield localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && !isLocalVarName(rid.name(), locals)
                        && qualifyViaImports(rid.name()) instanceof Type.ClassType extQ
                        && !extQ.packageName().isEmpty()
                        && externalClasspath != null
                        && externalClasspath.knows(extQ.internalName())
                        && externalClasspath.resolveMethod(extQ.internalName(), mc.methodName(),
                                mc.arguments().size()) != null) {
                    // Nome de CLASSE EXTERNA como receiver: Button.inflate(...)
                    // estático, interface externa ou instância — resolve pelo
                    // classpath ANTES dos namespaces builtin (Button também é
                    // widget do kof.ui; o import decide). Local sombreia.
                    ExternalClasspath.MethodSignature extSig = externalClasspath.resolveMethod(
                            extQ.internalName(), mc.methodName(), mc.arguments().size());
                    List<Type> extFormal = new ArrayList<>();
                    for (String d : extSig.parameterDescriptors()) {
                        extFormal.add(ExternalClasspath.typeFromDescriptor(d));
                    }
                    Type extRet = ExternalClasspath.typeFromDescriptor(extSig.returnDescriptor());
                    localIdx = emitArgumentsWithFormalTypes(mc.arguments(), extFormal,
                            ops, owner, localIdx, locals);
                    KofCallKind extKind = extSig.isStatic() ? KofCallKind.STATIC
                            : (extSig.ownerIsInterface() ? KofCallKind.INTERFACE
                            : KofCallKind.INSTANCE);
                    ops.add(new KofCall(extQ, mc.methodName(), extFormal, extRet, extKind));
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && !isLocalVarName(rid.name(), locals)
                            && "json".equals(rid.name())) {
                    if ("encode".equals(mc.methodName()) && mc.arguments().size() == 1) {
                        Type argType = inferExprType(mc.arguments().get(0), locals);
                        if (!jsonSupported(argType, false)) {
                            yield localIdx;
                        }
                        localIdx = emitExpression(mc.arguments().get(0), ops, owner, localIdx, locals);
                        List<Type> paramTypes = List.of(argType);
                        if (BuiltinTypes.isList(argType)) {
                            int tag = jsonListTag(listElementType(argType));
                            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, tag));
                            paramTypes = List.of(argType, Type.PrimitiveType.INT);
                        } else if (target == Target.NATIVE
                                && argType instanceof Type.ClassType ect
                                && !BuiltinTypes.isString(argType)
                                // List/Map têm caminho builtin próprio
                                && !BuiltinTypes.isList(argType) && !BuiltinTypes.isMap(argType)) {
                            // JSN002: compoe o JSON em compile-time a partir
                            // dos campos conhecidos (sem reflection, sem
                            // walker generico) — so primitivas testadas.
                            String cn2 = ect.packageName().isEmpty()
                                    ? ect.name() : ect.packageName() + "." + ect.name();
                            java.util.List<String[]> flds = classFieldsOrdered(cn2);
                            // guarda o objeto em local temporario
                            ops.add(new KofStoreLocal(argType, localIdx));
                            locals.add(new IRLocalVariable(localIdx, "#jsonobj", argType));
                            int objTmp = localIdx;
                            localIdx += isDoubleWidth(argType) ? 2 : 1;
                            // acc = "{"
                            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, "{"));
                            for (int fi = 0; fi < flds.size(); fi++) {
                                String fname = flds.get(fi)[0];
                                Type ftype = toType(flds.get(fi)[1]);
                                if (fi > 0) {
                                    ops.add(new KofLoadLiteral(BuiltinTypes.STRING, ","));
                                    ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                                            List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                                            BuiltinTypes.STRING, KofCallKind.FUNCTION));
                                }
                                ops.add(new KofLoadLiteral(BuiltinTypes.STRING,
                                        "\"" + fname + "\":"));
                                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
                                // valor do campo
                                ops.add(new KofLoadLocal(argType, objTmp));
                                ops.add(new KofLoadField(argType, fname, ftype));
                                switch (ftype instanceof Type.PrimitiveType fp
                                        ? Type.canonicalPrimitiveName(fp.name()) : "") {
                                    case "long":
                                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_long_to_string",
                                                List.of(Type.PrimitiveType.LONG), BuiltinTypes.STRING,
                                                KofCallKind.FUNCTION));
                                        break;
                                    case "bool":
                                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_bool_to_string",
                                                List.of(Type.PrimitiveType.BOOL), BuiltinTypes.STRING,
                                                KofCallKind.FUNCTION));
                                        break;
                                    case "int":
                                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_int_to_string",
                                                List.of(Type.PrimitiveType.INT), BuiltinTypes.STRING,
                                                KofCallKind.FUNCTION));
                                        break;
                                    default: // string
                                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_json_quote",
                                                List.of(BuiltinTypes.STRING), BuiltinTypes.STRING,
                                                KofCallKind.FUNCTION));
                                }
                                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
                            }
                            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, "}"));
                            ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                                    List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                                    BuiltinTypes.STRING, KofCallKind.FUNCTION));
                            yield localIdx;
                        }
                        ops.add(new KofCall(argType, jsonEncodeFunction(argType), paramTypes,
                                BuiltinTypes.STRING, KofCallKind.FUNCTION));
                    } else if ("decode".equals(mc.methodName()) && mc.arguments().size() == 1
                            && !mc.typeArguments().isEmpty()) {
                        Type targetType = toType(mc.typeArguments().get(0));
                        if (!jsonSupported(targetType, true)) {
                            yield localIdx;
                        }
                        localIdx = emitExpression(mc.arguments().get(0), ops, owner, localIdx, locals);
                        String decodeFn = jsonDecodeFunction(targetType);
                        List<Type> decodeParams = List.of(BuiltinTypes.STRING);
                        if (BuiltinTypes.isList(targetType)
                                && listElementType(targetType) instanceof Type.ClassType ect
                                && !BuiltinTypes.isString(ect)) {
                            // decode<List<T>> where T is a user class: bind
                            // each element to T (the element type survives the
                            // generic erasure through the type system).
                            decodeFn = "kof_json_decode_object_list";
                            decodeParams = List.of(BuiltinTypes.STRING, BuiltinTypes.STRING);
                            String className = ect.packageName().isEmpty()
                                    ? ect.name() : ect.packageName() + "." + ect.name();
                            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, className));
                        } else if (target == Target.NATIVE
                                && targetType instanceof Type.ClassType dct
                                && !BuiltinTypes.isString(targetType)
                                // List/Map têm caminho builtin próprio
                                && !BuiltinTypes.isList(targetType) && !BuiltinTypes.isMap(targetType)) {
                            // JSN002: decode composto — find_value por campo +
                            // decoders escalares + construtor canonico
                            String cn3 = dct.packageName().isEmpty()
                                    ? dct.name() : dct.packageName() + "." + dct.name();
                            java.util.List<String[]> flds = classFieldsOrdered(cn3);
                            // json em local temporario
                            ops.add(new KofStoreLocal(BuiltinTypes.STRING, localIdx));
                            locals.add(new IRLocalVariable(localIdx, "#jsonsrc", BuiltinTypes.STRING));
                            int jTmp = localIdx;
                            localIdx += 1;
                            List<Type> ctorTypes = new ArrayList<>();
                            ops.add(new KofNewObject(targetType,
                                    flds.stream().map(f -> toType(f[1])).toList()));
                            ops.add(new KofDup());
                            for (String[] f : flds) {
                                Type ft = toType(f[1]);
                                ctorTypes.add(ft);
                                ops.add(new KofLoadLocal(BuiltinTypes.STRING, jTmp));
                                ops.add(new KofLoadLiteral(BuiltinTypes.STRING, f[0]));
                                ops.add(new KofCall(BuiltinTypes.STRING, "kof_json_find_value",
                                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
                                String dec = switch (ft instanceof Type.PrimitiveType fp
                                        ? Type.canonicalPrimitiveName(fp.name()) : "") {
                                    case "int", "char", "byte", "short" -> "kof_json_decode_int";
                                    case "long" -> "kof_json_decode_long";
                                    case "bool" -> "kof_json_decode_bool";
                                    default -> "kof_json_decode_string";
                                };
                                ops.add(new KofCall(targetType, dec,
                                        List.of(BuiltinTypes.STRING), ft, KofCallKind.FUNCTION));
                            }
                            ops.add(new KofCall(targetType, "<init>", ctorTypes,
                                    Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                            yield localIdx;
                        }
                        ops.add(new KofCall(targetType, decodeFn, decodeParams,
                                targetType, KofCallKind.FUNCTION));
                    }
                    yield localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && KofDb.isDbNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                    boolean typed = KofDb.isQuery(mc.methodName()) && !mc.typeArguments().isEmpty();
                    KofDb.DbCall dbCall = KofDb.staticCall(mc.methodName(), argTypes, typed);
                    if (dbCall != null) {
                        if (!KofDb.supportedOn(target)) {
                            if (currentDiagnostics != null) {
                                currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        rid.name() + "." + mc.methodName()
                                                + ": not available on the " + target
                                                + " target yet (" + KofDb.gapCode() + ")",
                                        KofDb.gapCode());
                            }
                            yield localIdx;
                        }
                        for (int i = 0; i < mc.arguments().size() && i < 2; i++) {
                            localIdx = emitExpression(mc.arguments().get(i), ops, owner, localIdx, locals);
                        }
                        for (int i = 2; i < mc.arguments().size(); i++) {
                            localIdx = emitExpression(mc.arguments().get(i), ops, owner, localIdx, locals);
                            boxPrimitive(ops, argTypes.get(i));
                        }
                        if (KofDb.isQuery(mc.methodName())) {
                            if (typed && !mc.typeArguments().isEmpty()) {
                                ops.add(new KofLoadLiteral(BuiltinTypes.STRING, mc.typeArguments().get(0)));
                            } else {
                                ops.add(new KofLoadLiteral(Type.UnknownType.UNKNOWN, null));
                            }
                        }
                        List<Type> params = new ArrayList<>(dbCall.parameterTypes());
                        Type retType = dbCall.returnType();
                        if (KofDb.isQuery(mc.methodName())) {
                            // o className (ou null) é sempre empurrado; o
                            // param precisa estar na lista para o native
                            // popar na ordem certa
                            params.add(BuiltinTypes.STRING);
                            if (typed) {
                                retType = new Type.ClassType("kof", "List",
                                        List.of(toType(mc.typeArguments().get(0))));
                            }
                        }
                        ops.add(new KofCall(new Type.ClassType("kof.db", "Db", List.of()),
                                dbCall.function(), params, retType, KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && !isLocalVarName(rid.name(), locals)
                            && KofOrm.isOrmNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                    boolean typed = !mc.typeArguments().isEmpty();
                    String entityName = typed ? mc.typeArguments().get(0) : null;
                    if (entityName == null && "save".equals(mc.methodName()) && !argTypes.isEmpty()) {
                        Type objType = argTypes.get(argTypes.size() - 1);
                        if (objType instanceof Type.ClassType ct) entityName = ct.name();
                    }
                    KofOrm.OrmCall ormCall = KofOrm.staticCall(mc.methodName(), argTypes, typed, entityName);
                    if (ormCall != null) {
                        if (!KofOrm.supportedOn(target)) {
                            if (currentDiagnostics != null) {
                                currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        rid.name() + "." + mc.methodName()
                                                + ": not available on the " + target
                                                + " target yet (" + KofOrm.gapCode() + ")",
                                        KofOrm.gapCode());
                            }
                            yield localIdx;
                        }
                        List<EntityFieldNode> fields = entityName == null ? null : entitySchemas.get(entityName);
                        boolean needsEntity = !"migrate".equals(mc.methodName());
                        if (needsEntity && fields == null) {
                            if (currentDiagnostics != null) {
                                currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        "orm." + mc.methodName() + ": unknown entity '"
                                                + (entityName == null ? "?" : entityName) + "' (ORM002)",
                                        "ORM002");
                            }
                            yield localIdx;
                        }
                        // args do usuário: (db[, obj|id]) — primitivos são
                        // boxed (o runtime espera Object para obj/id)
                        for (int ai = 0; ai < mc.arguments().size(); ai++) {
                            ExpressionNode arg = mc.arguments().get(ai);
                            localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                            if (ai > 0 && isPrimitiveType(inferExprType(arg, locals))) {
                                boxPrimitive(ops, inferExprType(arg, locals));
                            }
                        }
                        // literais do schema (conhecidos em compile-time):
                        // table, schema, [className]
                        boolean isMigrate = "migrate".equals(mc.methodName());
                        String table = entityName == null ? "" : KofOrm.tableName(entityName);
                        String schema = entityName == null ? "" : KofOrm.schemaString(fields);
                        boolean needsClassName = "find".equals(mc.methodName())
                                || "all".equals(mc.methodName())
                                || "where".equals(mc.methodName())
                                || "page".equals(mc.methodName());
                        List<Type> params = new ArrayList<>(ormCall.parameterTypes());
                        if (!isMigrate) {
                            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, table));
                            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, schema));
                            params.add(BuiltinTypes.STRING); // table
                            params.add(BuiltinTypes.STRING); // schema
                        }
                        if (needsClassName) {
                            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, classNameFor(entityName)));
                            params.add(BuiltinTypes.STRING); // className
                        }
                        Type retType = ormCall.returnType();
                        if ("save".equals(mc.methodName()) && !argTypes.isEmpty()) {
                            retType = argTypes.get(argTypes.size() - 1);
                        } else if (typed) {
                            if ("all".equals(mc.methodName()) || "page".equals(mc.methodName())
                                    || "where".equals(mc.methodName())) {
                                retType = new Type.ClassType("kof", "List",
                                        List.of(toType(mc.typeArguments().get(0))));
                            } else if ("find".equals(mc.methodName())) {
                                retType = toType(mc.typeArguments().get(0));
                            }
                        }
                        ops.add(new KofCall(new Type.ClassType("kof.orm", "Orm", List.of()),
                                ormCall.function(), params, retType, KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && !isLocalVarName(rid.name(), locals)
                            && KofLog.isLogNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                    KofLog.LogCall logCall = KofLog.staticCall(mc.methodName(), argTypes);
                    if (logCall != null) {
                        if (!KofLog.supportedOn(target)) {
                            if (currentDiagnostics != null) {
                                currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        rid.name() + "." + mc.methodName()
                                                + ": not available on the " + target
                                                + " target yet (" + KofLog.gapCode() + ")",
                                        KofLog.gapCode());
                            }
                            yield localIdx;
                        }
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(new Type.ClassType("kof.log", "Log", List.of()),
                                logCall.function(), logCall.parameterTypes(), logCall.returnType(),
                                KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && "process".equals(rid.name())
                        && findLocalVar(rid.name(), locals) == null) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                    KofProcess.ProcessCall procCall = KofProcess.runCall(argTypes);
                    if (procCall != null) {
                        if (target == Target.NATIVE) {
                            if (currentDiagnostics != null) {
                                currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        "process.run: not supported on the Native target yet (JVM supports it)",
                                        "PROC001");
                            }
                            yield localIdx;
                        }
                        // process.run(program, args...) →
                        // kof_process_run(program, List<String>)
                        localIdx = emitExpression(mc.arguments().get(0), ops, owner, localIdx, locals);
                        Type listType = KofProcess.STRING_LIST;
                        ops.add(new KofCall(listType, "kof_list_new", List.of(), listType, KofCallKind.FUNCTION));
                        for (int i = 1; i < mc.arguments().size(); i++) {
                            ops.add(new KofDup());
                            localIdx = emitExpression(mc.arguments().get(i), ops, owner, localIdx, locals);
                            ops.add(new KofCall(listType, "kof_list_add",
                                    List.of(BuiltinTypes.STRING), Type.PrimitiveType.VOID,
                                    KofCallKind.INSTANCE));
                        }
                        ops.add(new KofCall(KofProcess.RESULT, "kof_process_run",
                                List.of(BuiltinTypes.STRING, KofProcess.STRING_LIST),
                                KofProcess.RESULT, KofCallKind.FUNCTION));
                    } else {
                        // process.exit(code) — todos os targets
                        KofProcess.ProcessCall exitCall = KofProcess.exitCall(argTypes);
                        if (exitCall != null) {
                            localIdx = emitExpression(mc.arguments().get(0), ops, owner, localIdx, locals);
                            ops.add(new KofCall(new Type.ClassType("kof.process", "Process", List.of()),
                                    exitCall.function(), exitCall.parameterTypes(), exitCall.returnType(),
                                    KofCallKind.FUNCTION));
                        }
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && !isLocalVarName(rid.name(), locals)
                            && KofHttp.isHttpNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                    KofHttp.HttpCall httpCall = KofHttp.staticCall(mc.methodName(), argTypes);
                    if (httpCall != null) {
                        if (!KofHttp.supportedOn(target)) {
                            if (currentDiagnostics != null) {
                                currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        rid.name() + "." + mc.methodName()
                                                + ": not available on the " + target
                                                + " target yet (HTTP002)",
                                        "HTTP002");
                            }
                            yield localIdx;
                        }
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(KofHttp.HTTP, httpCall.function(), httpCall.parameterTypes(),
                                httpCall.returnType(), KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && !isLocalVarName(rid.name(), locals)
                            && KofTime.isTimeNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                    KofTime.TimeCall timeCall = KofTime.staticCall(mc.methodName(), argTypes);
                    if (timeCall != null) {
                        if (!KofTime.supportedOn(target)) {
                            if (currentDiagnostics != null) {
                                currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        rid.name() + "." + mc.methodName()
                                                + ": not available on the " + target
                                                + " target yet (TIME001)",
                                        "TIME001");
                            }
                            yield localIdx;
                        }
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(KofTime.TIME, timeCall.function(), timeCall.parameterTypes(),
                                timeCall.returnType(), KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && !isLocalVarName(rid.name(), locals)
                            && KofMq.isMqNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                    KofMq.MqCall mqCall = KofMq.staticCall(mc.methodName(), argTypes);
                    if (mqCall != null) {
                        if (!KofMq.supportedOn(target)) {
                            if (currentDiagnostics != null) {
                                currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        rid.name() + "." + mc.methodName()
                                                + ": not available on the " + target
                                                + " target yet (MQ001)",
                                        "MQ001");
                            }
                            yield localIdx;
                        }
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(KofMq.MQ, mqCall.function(), mqCall.parameterTypes(),
                                mqCall.returnType(), KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && !isLocalVarName(rid.name(), locals)
                            && KofConfig.isConfigNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                    KofConfig.ConfigCall cfgCall = KofConfig.staticCall(mc.methodName(), argTypes);
                    if (cfgCall != null) {
                        if (!KofConfig.supportedOn(target)) {
                            if (currentDiagnostics != null) {
                                currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        rid.name() + "." + mc.methodName()
                                                + ": not available on the " + target
                                                + " target yet (CONF001)",
                                        "CONF001");
                            }
                            yield localIdx;
                        }
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(KofConfig.CONFIG, cfgCall.function(), cfgCall.parameterTypes(),
                                cfgCall.returnType(), KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && !isLocalVarName(rid.name(), locals)
                            && KofSecurity.isSecurityNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                    KofSecurity.SecCall secCall = KofSecurity.staticMethod(rid.name(), mc.methodName(), argTypes);
                    if (secCall != null) {
                        if (!KofSecurity.supportedOn(secCall.function(), target)) {
                            if (currentDiagnostics != null) {
                                currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        rid.name() + "." + mc.methodName()
                                                + ": not available on the " + target
                                                + " target yet (" + KofSecurity.gapCode(secCall.function()) + ")",
                                        KofSecurity.gapCode(secCall.function()));
                            }
                            yield localIdx;
                        }
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(new Type.ClassType("kof.security", "Security", List.of()),
                                secCall.function(), secCall.parameterTypes(), secCall.returnType(),
                                KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && !isLocalVarName(rid.name(), locals)
                            && KofValidation.isValidationNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                    KofValidation.ValidationCall vCall = KofValidation.staticMethod(rid.name(), mc.methodName(), argTypes);
                    if (vCall != null) {
                        if (!KofValidation.supportedOn(vCall.function(), target)) {
                            if (currentDiagnostics != null) {
                                currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        rid.name() + "." + mc.methodName()
                                                + ": not available on the " + target
                                                + " target yet (" + KofValidation.gapCode(vCall.function()) + ")",
                                        KofValidation.gapCode(vCall.function()));
                            }
                            yield localIdx;
                        }
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(new Type.ClassType("kof.validation", "Validation", List.of()),
                                vCall.function(), vCall.parameterTypes(), vCall.returnType(),
                                KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && !isLocalVarName(rid.name(), locals)
                            && KofObservability.isObservabilityNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                    KofObservability.ObservabilityCall oCall = KofObservability.staticMethod(rid.name(), mc.methodName(), argTypes);
                    if (oCall != null) {
                        if (!KofObservability.supportedOn(oCall.function(), target)) {
                            if (currentDiagnostics != null) {
                                currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        rid.name() + "." + mc.methodName()
                                                + ": not available on the " + target
                                                + " target yet (" + KofObservability.gapCode(oCall.function()) + ")",
                                        KofObservability.gapCode(oCall.function()));
                            }
                            yield localIdx;
                        }
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(new Type.ClassType("kof.observability", "Observability", List.of()),
                                oCall.function(), oCall.parameterTypes(), oCall.returnType(),
                                KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && !isLocalVarName(rid.name(), locals)
                            && KofTetris.isTetrisNamespace(rid.name())) {
                    KofTetris.TetrisCall tetrisCall = KofTetris.staticMethod(rid.name(), mc.methodName(),
                            mc.arguments().size());
                    if (tetrisCall != null) {
                        if (!KofTetris.supportedOn(target)) {
                            if (currentDiagnostics != null) {
                                currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        rid.name() + "." + mc.methodName()
                                                + ": not available on the " + target
                                                + " target yet (" + KofTetris.gapCode() + ")",
                                        KofTetris.gapCode());
                            }
                            yield localIdx;
                        }
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(new Type.ClassType("kof.tetris", "Tetris", List.of()),
                                tetrisCall.function(), tetrisCall.parameterTypes(), tetrisCall.returnType(),
                                KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && !isLocalVarName(rid.name(), locals)
                            && KofWeb.isWebNamespace(rid.name())) {
                    if ("app".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                        if (target != Target.JVM) {
                            if (currentDiagnostics != null) {
                                currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                        mc.position() != null ? mc.position().line() : 0,
                                        mc.position() != null ? mc.position().column() : 0,
                                        0,
                                        "web: not available on the " + target
                                                + " target yet (WEB001)",
                                        "WEB001");
                            }
                            yield localIdx;
                        }
                        KofWeb.WebCall appCall = KofWeb.appConstructor();
                        ops.add(new KofCall(KofWeb.APP, appCall.function(), appCall.parameterTypes(),
                                appCall.returnType(), KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid && KofIo.isConstructor(rid.name())) {
                    KofIo.IoCall ioCall = KofIo.staticMethod(rid.name(), mc.methodName(), mc.arguments().size());
                    if (ioCall != null) {
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                        }
                        ops.add(new KofCall(new Type.ClassType("kof.io", "Io", List.of()),
                                ioCall.function(), ioCall.parameterTypes(), ioCall.returnType(), KofCallKind.FUNCTION));
                    }
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid2 && KofUi.isPalette(rid2.name())) {
                    yield localIdx;
                } else if (mc.receiver() instanceof IdentifierExpr rid3 && KofUi.isConstructor(rid3.name())) {
                    KofUi.UiCall uiCall = KofUi.staticMethod(rid3.name(), mc.methodName(), mc.arguments().size());
                    if (uiCall != null && "kof_ui_color_rgba".equals(uiCall.function())) {
                        localIdx = emitPackedColor(mc.arguments(), ops, owner, localIdx, locals);
                        yield localIdx;
                    }
                    if (uiCall != null && "kof_ui_theme_light".equals(uiCall.function())) {
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                        yield localIdx;
                    }
                    if (uiCall != null && "kof_ui_theme_dark".equals(uiCall.function())) {
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
                        yield localIdx;
                    }
                    yield localIdx;
                } else if (mc.receiver() != null) {
                    if (mc.receiver() instanceof IdentifierExpr sid && "super".equals(sid.name())
                            && !owner.isEmpty()) {
                        // super.method(args): non-virtual call to the
                        // superclass implementation — lowered to
                        // INVOKESPECIAL on the direct superclass (JVM).
                        if (target == Target.NATIVE && currentDiagnostics != null) {
                            SourcePosition p = mc.position();
                            currentDiagnostics.error(p != null ? p.file() : "",
                                    p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                                    "super." + mc.methodName()
                                            + "() is not supported on the native target yet (SUP001)",
                                    "SUP001");
                            yield localIdx;
                        }
                        // super.metodo() só faz sentido no corpo de um método
                        // de classe; dentro de lambda sintética usa o this
                        // externo capturado ($outer) — sem ele, gap honesto
                        String effectiveOwner = owner;
                        String ownerSimple0 = owner.substring(owner.lastIndexOf('/') + 1);
                        if (semanticAnalyzer == null || semanticAnalyzer.getClass(ownerSimple0) == null) {
                            String enc = lambdaEnclosingOwner.get(owner);
                            if (enc == null) {
                                if (currentDiagnostics != null) {
                                    SourcePosition p = mc.position();
                                    currentDiagnostics.error(p != null ? p.file() : "",
                                            p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                                            "super." + mc.methodName()
                                                    + "() is only valid inside class methods (SUP002)",
                                            "SUP002");
                                }
                                yield localIdx;
                            }
                            effectiveOwner = enc;
                        }
                        String superInternal = findSuperClass(effectiveOwner);
                        if (superInternal == null) superInternal = "java/lang/Object";
                        // nomes declarados com pontos (android.view.View)
                        // viram nome interno JVM para resolução e emissão
                        superInternal = superInternal.replace('.', '/');
                        Type superType = ownerTypeFromInternal(superInternal);
                        SymbolTable.MethodSymbol superMethod = null;
                        if (semanticAnalyzer != null) {
                            String superSimple = superInternal.substring(superInternal.lastIndexOf('/') + 1);
                            SymbolTable.Symbol s = semanticAnalyzer.resolveInHierarchy(superSimple, mc.methodName());
                            if (s instanceof SymbolTable.MethodSymbol ms) superMethod = ms;
                        }
                        List<Type> paramTypes;
                        Type returnType;
                        ObjectMethodSig osig = objectMethodSignature(mc.methodName(), mc.arguments().size());
                        ExternalClasspath.MethodSignature extSig = null;
                        if (superMethod == null && osig == null && externalClasspath != null) {
                            extSig = externalClasspath.resolveMethod(superInternal, mc.methodName(),
                                    mc.arguments().size());
                        }
                        if (superMethod == null && osig == null && extSig == null
                                && hierarchyFullyKnown(superInternal) && currentDiagnostics != null) {
                            // hierarquia inteiramente conhecida e o método não
                            // existe — erro em compile-time, não NoSuchMethodError
                            SourcePosition p = mc.position();
                            currentDiagnostics.error(p != null ? p.file() : "",
                                    p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                                    "method '" + mc.methodName() + "' does not exist in superclass '"
                                            + superSimpleName(superInternal) + "'",
                                    "SEM016");
                            yield localIdx;
                        }
                        if (superMethod != null
                                && superMethod.parameterTypes().size() == mc.arguments().size()) {
                            paramTypes = superMethod.parameterTypes();
                            returnType = superMethod.returnType();
                        } else if (osig != null) {
                            paramTypes = osig.parameterTypes();
                            returnType = osig.returnType();
                        } else if (extSig != null) {
                            // assinatura real lida do classpath externo — o
                            // descritor emitido casa com a classe externa
                            List<Type> formal = new ArrayList<>();
                            for (String d : extSig.parameterDescriptors()) {
                                formal.add(ExternalClasspath.typeFromDescriptor(d));
                            }
                            paramTypes = formal;
                            returnType = ExternalClasspath.typeFromDescriptor(extSig.returnDescriptor());
                        } else {
                            paramTypes = new ArrayList<>();
                            for (ExpressionNode arg : mc.arguments()) paramTypes.add(inferExprType(arg, locals));
                            returnType = inferExprType(mc, locals);
                        }
                        // receiver: dentro de lambda sintética é o $outer e a
                        // chamada vira uma PONTE kof_super$metodo na classe dona
                        IRLocalVariable outerVar = findLocalVar("$outer", locals);
                        if (outerVar != null) {
                            ops.add(new KofLoadLocal(outerVar.type(), outerVar.index()));
                            String bridgeName = ensureSuperBridge(effectiveOwner, superInternal,
                                    mc.methodName(), paramTypes, returnType);
                            localIdx = emitArgumentsWithFormalTypes(mc.arguments(), paramTypes,
                                    ops, effectiveOwner, localIdx, locals);
                            ops.add(new KofCall(ownerTypeFromInternal(effectiveOwner), bridgeName,
                                    paramTypes, returnType, KofCallKind.INSTANCE));
                            yield localIdx;
                        } else {
                            ops.add(new KofLoadLocal(ownerTypeFromInternal(effectiveOwner), 0));
                            localIdx = emitArgumentsWithFormalTypes(mc.arguments(), paramTypes, ops, owner, localIdx, locals);
                            ops.add(new KofCall(superType, mc.methodName(), paramTypes, returnType, KofCallKind.SUPER));
                            yield localIdx;
                        }
                    }
                    localIdx = emitExpression(mc.receiver(), ops, owner, localIdx, locals);
                    Type recvType = inferExprType(mc.receiver(), locals);
                    if (KofUi.isUiType(recvType)) {
                        localIdx = emitUiInstance(recvType, mc, ops, owner, localIdx, locals);
                        yield localIdx;
                    }
                    if (isEnumType(recvType) && "name".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                        // o valor do enum JÁ é o nome (String em runtime): identidade
                        yield localIdx;
                    }
                    if (KofWeb.isAppType(recvType)) {
                        List<Type> webArgTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) webArgTypes.add(inferExprType(arg, locals));
                        KofWeb.WebCall webCall = KofWeb.instanceMethod(mc.methodName(), webArgTypes);
                        if (webCall != null) {
                            if (target != Target.JVM) {
                                String webCode = "kof_web_listen_secure".equals(webCall.function()) ? "WEB002" : "WEB001";
                                String webMsg = "kof_web_listen_secure".equals(webCall.function())
                                        ? "web TLS: not available on the " + target + " target yet (WEB002)"
                                        : "web: not available on the " + target + " target yet (WEB001)";
                                if (currentDiagnostics != null) {
                                    currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                                            mc.position() != null ? mc.position().line() : 0,
                                            mc.position() != null ? mc.position().column() : 0,
                                            0, webMsg, webCode);
                                }
                                yield localIdx;
                            }
                            if (KofWeb.isRouteMethod(mc.methodName())) {
                                ops.add(new KofLoadLiteral(BuiltinTypes.STRING, mc.methodName().toUpperCase()));
                            }
                            for (ExpressionNode arg : mc.arguments()) {
                                localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                            }
                            List<Type> webParams = new ArrayList<>();
                            webParams.add(BuiltinTypes.STRING);
                            if (KofWeb.isRouteMethod(mc.methodName())) {
                                webParams.add(BuiltinTypes.STRING);
                            }
                            webParams.addAll(webCall.parameterTypes());
                            ops.add(new KofCall(KofWeb.APP, webCall.function(), webParams,
                                    webCall.returnType(), KofCallKind.FUNCTION));
                        }
                        yield localIdx;
                    }
                    if (KofIo.isIoType(recvType)) {
                        if (KofIo.isIdentityMethod(mc.methodName())) {
                            yield localIdx;
                        }
                        KofIo.IoCall ioCall = KofIo.instanceMethod(recvType, mc.methodName(), mc.arguments().size());
                        if (ioCall != null) {
                            for (ExpressionNode arg : mc.arguments()) {
                                localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                            }
                            List<Type> ioParams = new ArrayList<>();
                            ioParams.add(BuiltinTypes.STRING);
                            ioParams.addAll(ioCall.parameterTypes());
                            ops.add(new KofCall(new Type.ClassType("kof.io", "Io", List.of()),
                                    ioCall.function(), ioParams, ioCall.returnType(), KofCallKind.FUNCTION));
                            yield localIdx;
                        }
                    }
                    if (recvType instanceof Type.FunctionType ft) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                        for (ExpressionNode arg : mc.arguments()) {
                            localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                        }
                        // f.invoke(): o owner precisa ser a classe sintética
                        // da lambda — FunctionType não tem nome JVM
                        Type invokeOwner = ft.className() != null
                                ? new Type.ClassType("", ft.className(), List.of()) : ft;
                        ops.add(new KofCall(invokeOwner, "invoke", argTypes, ft.returnType(), KofCallKind.INSTANCE));
                        yield localIdx;
                    }
                    if (BuiltinTypes.isList(recvType)) {
                        String listFn = switch (mc.methodName()) {
                            case "add", "push", "append" -> "kof_list_add";
                            case "get" -> "kof_list_get";
                            case "set" -> "kof_list_set";
                            case "size", "length", "count" -> "kof_list_size";
                            case "contains" -> "kof_list_contains";
                            case "isEmpty" -> "kof_list_is_empty";
                            case "remove" -> "kof_list_remove";
                            case "clear" -> "kof_list_clear";
                            default -> null;
                        };
                        if (listFn != null) {
                            List<Type> argTypes = new ArrayList<>();
                            for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                            Type elemType = listElementType(recvType);
                            // listOf() with no type argument produces
                            // List<Unknown>; the first add() pins the element
                            // type on the local so later get() calls are
                            // typed (records, classes) instead of Object.
                            if ("kof_list_add".equals(listFn)
                                    && Type.UnknownType.UNKNOWN.equals(elemType)
                                    && !argTypes.isEmpty()
                                    && !(argTypes.get(0) instanceof Type.UnknownType)
                                    && mc.receiver() instanceof IdentifierExpr rid) {
                                for (int li = 0; li < locals.size(); li++) {
                                    IRLocalVariable lv = locals.get(li);
                                    if (lv.name().equals(rid.name())) {
                                        locals.set(li, new IRLocalVariable(lv.index(), lv.name(),
                                                new Type.ClassType("kof", "List", List.of(argTypes.get(0)))));
                                        break;
                                    }
                                }
                            }
                            for (ExpressionNode arg : mc.arguments()) localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                            Type retType = switch (listFn) {
                                case "kof_list_add", "kof_list_set", "kof_list_clear" -> Type.PrimitiveType.VOID;
                                case "kof_list_contains", "kof_list_is_empty" -> Type.PrimitiveType.BOOL;
                                case "kof_list_remove" -> elemType;
                                default -> elemType;
                            };
                            if ("kof_list_contains".equals(listFn)) {

                                int tag = BuiltinTypes.isString(elemType) ? 1 : 0;
                                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, tag));
                                argTypes = new ArrayList<>(argTypes);
                                argTypes.add(Type.PrimitiveType.INT);
                            }
                            ops.add(new KofCall(recvType, listFn, argTypes, retType, KofCallKind.INSTANCE));
                            yield localIdx;
                        }
                    }
                    if (BuiltinTypes.isMap(recvType)) {

                        String mapFn = switch (mc.methodName()) {
                            case "put" -> "kof_map_put";
                            case "get" -> "kof_map_get";
                            case "remove" -> "kof_map_remove";
                            case "containsKey", "contains" -> "kof_map_contains";
                            case "size", "length", "count" -> "kof_map_size";
                            case "clear" -> "kof_map_clear";
                            case "isEmpty" -> "kof_map_is_empty";
                            case "keys" -> "kof_map_keys";
                            case "values" -> "kof_map_values";
                            default -> null;
                        };
                        if (mapFn != null) {
                            List<Type> argTypes = new ArrayList<>();
                            for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                            Type keyType = Type.UnknownType.UNKNOWN;
                            Type valueType = Type.UnknownType.UNKNOWN;
                            if (recvType instanceof Type.ClassType ct && ct.typeArguments().size() == 2) {
                                keyType = ct.typeArguments().get(0);
                                valueType = ct.typeArguments().get(1);
                            }
                            // mapOf() nasce Map<Unknown,Unknown>: o primeiro put()
                            // pina os tipos no local para que get()/remove() tenham
                            // tipo concreto (comparações e unboxing corretos)
                            if ("kof_map_put".equals(mapFn)
                                    && keyType instanceof Type.UnknownType
                                    && argTypes.size() == 2
                                    && !(argTypes.get(0) instanceof Type.UnknownType)
                                    && mc.receiver() instanceof IdentifierExpr rid) {
                                for (int li = 0; li < locals.size(); li++) {
                                    IRLocalVariable lv = locals.get(li);
                                    if (lv.name().equals(rid.name())) {
                                        locals.set(li, new IRLocalVariable(lv.index(), lv.name(),
                                                new Type.ClassType("kof", "Map", List.of(argTypes.get(0), argTypes.get(1)))));
                                        break;
                                    }
                                }
                            }
                            Type retType = switch (mapFn) {
                                case "kof_map_put", "kof_map_remove" -> valueType;
                                case "kof_map_get" -> valueType;
                                case "kof_map_contains", "kof_map_is_empty" -> Type.PrimitiveType.BOOL;
                                case "kof_map_size" -> Type.PrimitiveType.INT;
                                case "kof_map_clear" -> Type.PrimitiveType.VOID;
                                case "kof_map_keys", "kof_map_values" -> new Type.ClassType("kof", "List", List.of(mapFn.equals("kof_map_keys") ? keyType : valueType));
                                default -> Type.UnknownType.UNKNOWN;
                            };
                            for (ExpressionNode arg : mc.arguments()) localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                            ops.add(new KofCall(recvType, mapFn, argTypes, retType, KofCallKind.INSTANCE));
                            yield localIdx;
                        }
                    }
                    if (BuiltinTypes.isSet(recvType)) {

                        String setFn = switch (mc.methodName()) {
                            case "add" -> "kof_set_add";
                            case "contains" -> "kof_set_contains";
                            case "remove" -> "kof_set_remove";
                            // add/contains/remove recebem tag de tipo (1=string)
                            case "size", "length", "count" -> "kof_set_size";
                            case "clear" -> "kof_set_clear";
                            case "isEmpty" -> "kof_set_is_empty";
                            default -> null;
                        };
                        if (setFn != null) {
                            List<Type> argTypes = new ArrayList<>();
                            for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                            Type elemType = Type.UnknownType.UNKNOWN;
                            if (recvType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) elemType = ct.typeArguments().get(0);
                            Type retType = switch (setFn) {
                                case "kof_set_add", "kof_set_remove" -> Type.PrimitiveType.BOOL;
                                case "kof_set_contains", "kof_set_is_empty" -> Type.PrimitiveType.BOOL;
                                case "kof_set_size" -> Type.PrimitiveType.INT;
                                case "kof_set_clear" -> Type.PrimitiveType.VOID;
                                default -> Type.UnknownType.UNKNOWN;
                            };
                            for (ExpressionNode arg : mc.arguments()) localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                            if (target == Target.NATIVE
                                    && ("kof_set_add".equals(setFn) || "kof_set_contains".equals(setFn)
                                        || "kof_set_remove".equals(setFn))) {
                                // tag de tipo só no Native (HashSet usa equals no JVM)
                                int tag = BuiltinTypes.isString(elemType) ? 1 : 0;
                                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, tag));
                                argTypes = new ArrayList<>(argTypes);
                                argTypes.add(Type.PrimitiveType.INT);
                            }
                            ops.add(new KofCall(recvType, setFn, argTypes, retType, KofCallKind.INSTANCE));
                            yield localIdx;
                        }
                    }
                    Type methodReturnType = Type.UnknownType.UNKNOWN;
                    List<Type> methodParamTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) {
                        methodParamTypes.add(inferExprType(arg, locals));
                    }
                    SymbolTable.MethodSymbol resolvedMethod = semanticAnalyzer.getResolvedMethod(mc);
                    if (resolvedMethod != null) {
                        recvType = ownerTypeFromInternal(resolvedMethod.ownerClass());
                        methodReturnType = resolvedMethod.returnType();
                        methodParamTypes = resolvedMethod.parameterTypes();
                    } else if (BuiltinTypes.isString(recvType)) {
                        StringMethodSig sig = stringMethodSignature(mc.methodName(), mc.arguments().size(),
                                methodParamTypes);
                        if (sig != null) {
                            methodReturnType = sig.returnType();
                            methodParamTypes = sig.parameterTypes();
                        }
                    } else if (isPrimitiveType(recvType) && "toString".equals(mc.methodName())
                            && mc.arguments().isEmpty()) {
                        // primitivo.toString(): o primitivo não tem classe —
                        // boxar e converter (String.valueOf) em vez de gerar
                        // um owner vazio no bytecode (ClassFormatError)
                        boxPrimitive(ops, recvType);
                        ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                                List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING, KofCallKind.STATIC));
                        yield localIdx;
                    } else {
                        ObjectMethodSig osig = objectMethodSignature(mc.methodName(), mc.arguments().size());
                        if (osig != null) {
                            methodReturnType = osig.returnType();
                            methodParamTypes = osig.parameterTypes();
                        }
                    }
                    if (methodReturnType instanceof Type.UnknownType) {
                        // fall back to the lowering's own inference (list-get
                        // chains, user classes resolved through hierarchy)
                        Type inferred = inferExprType(mc, locals);
                        if (!(inferred instanceof Type.UnknownType)) {
                            methodReturnType = inferred;
                        }
                    }
                    localIdx = emitArgumentsWithFormalTypes(mc.arguments(), methodParamTypes, ops, owner, localIdx, locals);
                    KofCallKind callKind = KofCallKind.INSTANCE;
                    if (recvType instanceof Type.ClassType rt && semanticAnalyzer != null) {
                        if (semanticAnalyzer.isInterfaceType(rt.name())) {
                            callKind = KofCallKind.INTERFACE;
                        }
                    }
                    if (callKind == KofCallKind.INSTANCE && resolvedMethod != null && semanticAnalyzer != null) {
                        String ownerName = resolvedMethod.ownerClass();
                        if (ownerName.contains("/")) ownerName = ownerName.substring(ownerName.lastIndexOf('/') + 1);
                        if (semanticAnalyzer.isInterfaceType(ownerName)) {
                            callKind = KofCallKind.INTERFACE;
                        }
                    }
                    String runtimeMethod = BuiltinTypes.isString(recvType)
                            ? stringRuntimeMethod(mc.methodName()) : null;
                    // receiver de classe EXTERNA sem símbolo resolvido: última
                    // linha de defesa — assinatura vem do classpath, senão o
                    // descritor sairia errado (owner vazio / retorno Object)
                    if (resolvedMethod == null && runtimeMethod == null
                            && mc.receiver() != null && currentDiagnostics != null) {
                        Type rt2 = semanticAnalyzer != null
                                ? semanticAnalyzer.getExpressionType(mc.receiver())
                                : Type.UnknownType.UNKNOWN;
                        if (!(rt2 instanceof Type.ClassType)) {
                            rt2 = inferExprType(mc.receiver(), locals);
                        }
                        if (rt2 instanceof Type.ClassType ct2 && !ct2.packageName().isEmpty()
                                && externalClasspath != null
                                && externalClasspath.knows(ct2.internalName())) {
                            ExternalClasspath.MethodSignature sig = externalClasspath.resolveMethod(
                                    ct2.internalName(), mc.methodName(), mc.arguments().size());
                            if (sig != null) {
                                List<Type> formal = new ArrayList<>();
                                for (String d : sig.parameterDescriptors()) {
                                    formal.add(ExternalClasspath.typeFromDescriptor(d));
                                }
                                recvType = ct2;
                                methodParamTypes = formal;
                                methodReturnType = ExternalClasspath.typeFromDescriptor(sig.returnDescriptor());
                            }
                        }
                    }
                    ops.add(new KofCall(recvType,
                            runtimeMethod != null ? runtimeMethod : mc.methodName(),
                            methodParamTypes, methodReturnType, callKind));
                    if (methodReturnType instanceof Type.TypeVariable) {
                        Type effective = inferExprType(mc, locals);
                        if (isPrimitiveType(effective)) {
                            emitErasureUnbox(ops, effective);
                        }
                    }
                } else {
                    if (("super".equals(mc.methodName()) || "this".equals(mc.methodName()))
                            && semanticAnalyzer != null && !owner.isEmpty()) {
                        // super(args): construtor da superclasse (Object quando
                        // a classe não tem extends). this(args): delegação para
                        // outro construtor da própria classe — o alvo executa
                        // super() e os inicializadores de campo.
                        boolean delegation = "this".equals(mc.methodName());
                        String targetInternal;
                        if (delegation) {
                            targetInternal = owner;
                        } else {
                            targetInternal = findSuperClass(owner);
                            if (targetInternal == null) targetInternal = "java/lang/Object";
                            targetInternal = targetInternal.replace('.', '/');
                        }
                        Type targetType = ownerTypeFromInternal(targetInternal);
                        SymbolTable.ClassSymbol targetCs = semanticAnalyzer.getClass(
                                targetInternal.substring(targetInternal.lastIndexOf('/') + 1));
                        SymbolTable.ConstructorSymbol ctor = null;
                        if (targetCs != null) {
                            SymbolTable.Symbol ctorSym = targetCs.members().resolve("<init>");
                            if (ctorSym instanceof SymbolTable.ConstructorSymbol c
                                    && c.parameterTypes().size() == mc.arguments().size()) {
                                ctor = c;
                            }
                        }
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                        ops.add(new KofLoadLocal(ownerTypeFromInternal(owner), 0));
                        List<Type> ctorParamTypes;
                        if (ctor != null && ctor.parameterTypes().size() == mc.arguments().size()) {
                            ctorParamTypes = ctor.parameterTypes();
                        } else {
                            if (targetCs != null && currentDiagnostics != null) {
                                // classe conhecida e nenhum construtor com essa
                                // aridade — erro em compile-time
                                SourcePosition p = mc.position();
                                currentDiagnostics.error(p != null ? p.file() : "",
                                        p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                                        (delegation ? "no constructor of '" : "no super constructor of '")
                                                + targetInternal.substring(targetInternal.lastIndexOf('/') + 1)
                                                + "' with " + mc.arguments().size() + " argument(s)",
                                        "SEM017");
                                yield localIdx;
                            }
                            ctorParamTypes = argTypes;
                        }
                        localIdx = emitArgumentsWithFormalTypes(mc.arguments(), ctorParamTypes, ops, owner, localIdx, locals);
                        ops.add(new KofCall(targetType, "<init>", ctorParamTypes, Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                        yield localIdx;
                    }
                    SymbolTable.MethodSymbol selfMethod = semanticAnalyzer != null
                            ? semanticAnalyzer.getResolvedMethod(mc) : null;
                    if (selfMethod != null && !owner.isEmpty()
                            && !"<init>".equals(selfMethod.name())
                            && selfMethod.ownerClass() != null) {
                        Type ownerType = ownerTypeFromInternal(selfMethod.ownerClass());
                        ops.add(new KofLoadLocal(ownerType, 0));
                        localIdx = emitArgumentsWithFormalTypes(mc.arguments(), selfMethod.parameterTypes(),
                                ops, owner, localIdx, locals);
                        ops.add(new KofCall(ownerType, mc.methodName(), selfMethod.parameterTypes(),
                                selfMethod.returnType(), KofCallKind.INSTANCE));
                        yield localIdx;
                    }
                    SymbolTable.ClassSymbol cs = semanticAnalyzer != null ? semanticAnalyzer.getClass(mc.methodName()) : null;
                    if (cs != null) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                        SymbolTable.ConstructorSymbol ctor = null;
                        SymbolTable.Symbol ctorSym = cs.members().resolve("<init>");
                        if (ctorSym instanceof SymbolTable.ConstructorSymbol c) ctor = c;
                        ops.add(new KofNewObject(cs.type(), argTypes));
                        ops.add(new KofDup());
                        List<Type> ctorParamTypes = (ctor != null
                                && ctor.parameterTypes().size() == mc.arguments().size())
                                ? ctor.parameterTypes() : argTypes;
                        localIdx = emitArgumentsWithFormalTypes(mc.arguments(), ctorParamTypes, ops, owner, localIdx, locals);
                        ops.add(new KofCall(cs.type(), "<init>", ctorParamTypes, Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                    } else {
                        IRLocalVariable lambdaVar = findLocalVar(mc.methodName(), locals);
                        if (lambdaVar != null && lambdaVar.type() instanceof Type.FunctionType lft) {
                            localIdx = emitExpression(new IdentifierExpr(mc.position(), mc.methodName()),
                                    ops, owner, localIdx, locals);
                            List<Type> argTypes = new ArrayList<>();
                            for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                            localIdx = emitArgumentsWithFormalTypes(mc.arguments(), lft.parameterTypes(), ops, owner, localIdx, locals);
                            Type invokeOwner = lft.className() != null
                                    ? new Type.ClassType("", lft.className(), List.of()) : lft;
                            ops.add(new KofCall(invokeOwner, "invoke", argTypes, lft.returnType(), KofCallKind.INSTANCE));
                        } else {
                            List<Type> argTypes = new ArrayList<>();
                            for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                            Type returnType = Type.UnknownType.UNKNOWN;
                            if (currentUnit != null) {
                                for (AstNode d : currentUnit.declarations()) {
                                    if (d instanceof FunctionDeclarationNode fn && fn.name().equals(mc.methodName())) {
                                        returnType = resolveWithTypeParams(fn.returnType(), fn.typeParameters());
                                        List<Type> fnTypes = fn.parameters().stream()
                                                .map(p -> resolveWithTypeParams(p.type(), fn.typeParameters())).toList();
                                        boolean hasDefaults = fn.parameters().stream()
                                                .anyMatch(p -> p.defaultExpression() != null);
                                        if (hasDefaults && mc.arguments().size() < fnTypes.size()) {
                                            argTypes = fnTypes.subList(0, mc.arguments().size());
                                        } else {
                                            argTypes = fnTypes;
                                        }
                                        break;
                                    }
                                }
                            }
                            localIdx = emitArgumentsWithFormalTypes(mc.arguments(), argTypes, ops, owner, localIdx, locals);
                            ops.add(new KofCall(mainClassType(), mc.methodName(), argTypes, returnType, KofCallKind.FUNCTION));
                            Type effective = inferExprType(mc, locals);
                            if (returnType instanceof Type.TypeVariable && isPrimitiveType(effective)) {
                                emitErasureUnbox(ops, effective);
                            }
                        }
                    }
                }
                yield localIdx;
            }
            case AssignmentExpr ae -> {
                if (ae.target() instanceof IdentifierExpr ie && !owner.isEmpty()) {
                    boolean isLocal = false;
                    for (int i = locals.size() - 1; i >= 0; i--) {
                        if (locals.get(i).name().equals(ie.name())) { isLocal = true; break; }
                    }
                    if (!isLocal) {
                        String className = owner.substring(owner.lastIndexOf('/') + 1);
                        SymbolTable.Symbol fieldSym = semanticAnalyzer != null
                                ? resolveFieldInHierarchy(className, ie.name()) : null;
                        if (fieldSym != null
                                && (fieldSym instanceof SymbolTable.FieldSymbol
                                || (fieldSym instanceof SymbolTable.MethodSymbol ms
                                        && ms.parameterTypes().isEmpty()))) {
                            Type ownerType = ownerTypeFromInternal(owner);
                            ops.add(new KofLoadLocal(ownerType, 0));
                            String op = ae.operator();
                            if ("+=".equals(op) || "-=".equals(op) || "*=".equals(op)
                                    || "/=".equals(op) || "%=".equals(op)
                                    || "&=".equals(op) || "|=".equals(op) || "^=".equals(op)) {
                                ops.add(new KofLoadField(ownerType, ie.name(), fieldSym.type()));
                            }
                            localIdx = emitExpression(ae.value(), ops, owner, localIdx, locals);
                            if ("+=".equals(op) || "-=".equals(op) || "*=".equals(op)
                                    || "/=".equals(op) || "%=".equals(op)
                                    || "&=".equals(op) || "|=".equals(op) || "^=".equals(op)) {
                                KofBinaryOp binOp = switch (op) {
                                    case "+=" -> KofBinaryOp.ADD;
                                    case "-=" -> KofBinaryOp.SUB;
                                    case "*=" -> KofBinaryOp.MUL;
                                    case "/=" -> KofBinaryOp.DIV;
                                    case "%=" -> KofBinaryOp.MOD;
                                    case "&=" -> KofBinaryOp.AND;
                                    case "|=" -> KofBinaryOp.OR;
                                    case "^=" -> KofBinaryOp.XOR;
                                    default -> KofBinaryOp.ADD;
                                };
                                ops.add(new KofBinary(binOp, fieldSym.type()));
                            }
                            ops.add(new KofStoreField(ownerType, ie.name(), fieldSym.type()));
                            yield localIdx;
                        }
                    }
                }
                if (ae.target() instanceof FieldAccessExpr fa) {
                    if (fa.receiver() instanceof IdentifierExpr rid && semanticAnalyzer != null
                            && semanticAnalyzer.getClass(rid.name()) != null) {
                        // Static field store: Class.field = value.
                        SymbolTable.ClassSymbol cs = semanticAnalyzer.getClass(rid.name());
                        SymbolTable.Symbol fs = resolveFieldInHierarchy(cs.name(), fa.fieldName());
                        if (fs instanceof SymbolTable.FieldSymbol fld) {
                            localIdx = emitExpression(ae.value(), ops, owner, localIdx, locals);
                            ops.add(new KofPutStatic(cs.type(), fa.fieldName(), fld.type()));
                            yield localIdx;
                        }
                    }
                    Type faRecvType = inferExprType(fa.receiver(), locals);
                    if (KofUi.isWindow(faRecvType) && "title".equals(fa.fieldName())) {
                        localIdx = emitExpression(fa.receiver(), ops, owner, localIdx, locals);
                        localIdx = emitExpression(ae.value(), ops, owner, localIdx, locals);
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_window_set_title", List.of(Type.PrimitiveType.INT, BuiltinTypes.STRING),
                                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                    if (KofUi.isLabel(faRecvType) && "text".equals(fa.fieldName())) {
                        localIdx = emitExpression(fa.receiver(), ops, owner, localIdx, locals);
                        localIdx = emitExpression(ae.value(), ops, owner, localIdx, locals);
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_label_set_text", List.of(Type.PrimitiveType.INT, BuiltinTypes.STRING),
                                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                    if (KofUi.isLabel(faRecvType) && "fontSize".equals(fa.fieldName())) {
                        localIdx = emitExpression(fa.receiver(), ops, owner, localIdx, locals);
                        localIdx = emitExpression(ae.value(), ops, owner, localIdx, locals);
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_label_set_font_size", List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT),
                                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                    if (KofUi.isLabel(faRecvType) && "bold".equals(fa.fieldName())) {
                        localIdx = emitExpression(fa.receiver(), ops, owner, localIdx, locals);
                        localIdx = emitExpression(ae.value(), ops, owner, localIdx, locals);
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_label_set_bold", List.of(Type.PrimitiveType.INT, Type.PrimitiveType.BOOL),
                                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                    if (KofUi.isLabel(faRecvType) && "color".equals(fa.fieldName())) {
                        localIdx = emitExpression(fa.receiver(), ops, owner, localIdx, locals);
                        localIdx = emitExpression(ae.value(), ops, owner, localIdx, locals);
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_label_set_color", List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT),
                                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                    if (KofUi.isWindow(faRecvType) && "theme".equals(fa.fieldName())) {
                        localIdx = emitExpression(fa.receiver(), ops, owner, localIdx, locals);
                        localIdx = emitExpression(ae.value(), ops, owner, localIdx, locals);
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_window_set_theme", List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT),
                                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                    if (KofUi.isButton(faRecvType) && "text".equals(fa.fieldName())) {
                        localIdx = emitExpression(fa.receiver(), ops, owner, localIdx, locals);
                        localIdx = emitExpression(ae.value(), ops, owner, localIdx, locals);
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_button_set_text", List.of(Type.PrimitiveType.INT, BuiltinTypes.STRING),
                                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                    if (KofUi.isInput(faRecvType) && "text".equals(fa.fieldName())) {
                        localIdx = emitExpression(fa.receiver(), ops, owner, localIdx, locals);
                        localIdx = emitExpression(ae.value(), ops, owner, localIdx, locals);
                        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                                "kof_ui_input_set_text", List.of(Type.PrimitiveType.INT, BuiltinTypes.STRING),
                                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
                        yield localIdx;
                    }
                    localIdx = emitExpression(fa.receiver(), ops, owner, localIdx, locals);
                    String faOp = ae.operator();
                    if ("+=".equals(faOp) || "-=".equals(faOp) || "*=".equals(faOp)
                            || "/=".equals(faOp) || "%=".equals(faOp)
                            || "&=".equals(faOp) || "|=".equals(faOp) || "^=".equals(faOp)) {
                        ops.add(new KofDup());
                        ops.add(new KofLoadField(inferExprType(fa.receiver(), locals), fa.fieldName(),
                                Type.UnknownType.UNKNOWN));
                    }
                    localIdx = emitExpression(ae.value(), ops, owner, localIdx, locals);
                    Type recvType = inferExprType(fa.receiver(), locals);
                    Type fieldType = Type.UnknownType.UNKNOWN;
                    if (recvType instanceof Type.ClassType ct) {
                        SymbolTable.Symbol fs = resolveFieldInHierarchy(ct.name(), fa.fieldName());
                        if (fs != null) fieldType = fs.type();
                        else if (!ct.packageName().isEmpty() && externalClasspath != null
                                && externalClasspath.knows(ct.internalName())) {
                            String desc = externalClasspath.resolveFieldType(
                                    ct.internalName(), fa.fieldName());
                            if (desc != null) fieldType = ExternalClasspath.typeFromDescriptor(desc);
                        }
                    }
                    if ("+=".equals(faOp) || "-=".equals(faOp) || "*=".equals(faOp)
                            || "/=".equals(faOp) || "%=".equals(faOp)
                            || "&=".equals(faOp) || "|=".equals(faOp) || "^=".equals(faOp)) {
                        KofBinaryOp binOp = switch (faOp) {
                            case "+=" -> KofBinaryOp.ADD;
                            case "-=" -> KofBinaryOp.SUB;
                            case "*=" -> KofBinaryOp.MUL;
                            case "/=" -> KofBinaryOp.DIV;
                            case "%=" -> KofBinaryOp.MOD;
                            case "&=" -> KofBinaryOp.AND;
                            case "|=" -> KofBinaryOp.OR;
                            case "^=" -> KofBinaryOp.XOR;
                            default -> KofBinaryOp.ADD;
                        };
                        ops.add(new KofBinary(binOp, fieldType));
                    }
                    ops.add(new KofStoreField(recvType, fa.fieldName(), fieldType));
                    yield localIdx;
                }
                if (ae.target() instanceof ArrayAccessExpr aa) {
                    localIdx = emitExpression(aa.receiver(), ops, owner, localIdx, locals);
                    localIdx = emitExpression(aa.index(), ops, owner, localIdx, locals);
                    localIdx = emitExpression(ae.value(), ops, owner, localIdx, locals);
                    Type recvType = inferExprType(aa.receiver(), locals);
                    Type elemType = Type.arrayElementType(recvType);
                    ops.add(new KofArrayStore(elemType));
                    yield localIdx;
                }
                if (ae.target() instanceof IdentifierExpr ieBox) {
                    for (int i = locals.size() - 1; i >= 0; i--) {
                        if (locals.get(i).name().equals(ieBox.name()) && isBoxType(locals.get(i).type())) {
                            IRLocalVariable boxLv = locals.get(i);
                            String op = ae.operator();
                            Type valType = boxValueTypes.get(((Type.ClassType) boxLv.type()).name());
                            if ("+=".equals(op) && BuiltinTypes.isString(valType)) {
                                ops.add(new KofLoadLocal(boxLv.type(), boxLv.index()));
                                ops.add(new KofLoadField(boxLv.type(), "value", valType));
                                localIdx = emitExpression(ae.value(), ops, owner, localIdx, locals);
                                ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                                        List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING,
                                        KofCallKind.STATIC));
                                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
                                ops.add(new KofStoreField(boxLv.type(), "value", valType));
                            } else if ("+=".equals(op) || "-=".equals(op) || "*=".equals(op)
                                    || "/=".equals(op) || "%=".equals(op)
                                    || "&=".equals(op) || "|=".equals(op) || "^=".equals(op)) {
                                ops.add(new KofLoadLocal(boxLv.type(), boxLv.index()));
                                ops.add(new KofLoadField(boxLv.type(), "value", valType));
                                localIdx = emitExpression(ae.value(), ops, owner, localIdx, locals);
                                emitWideningIfNeeded(ops, inferExprType(ae.value(), locals), valType);
                                KofBinaryOp binOp = switch (op) {
                                    case "+=" -> KofBinaryOp.ADD;
                                    case "-=" -> KofBinaryOp.SUB;
                                    case "*=" -> KofBinaryOp.MUL;
                                    case "/=" -> KofBinaryOp.DIV;
                                    case "%=" -> KofBinaryOp.MOD;
                                    case "&=" -> KofBinaryOp.AND;
                                    case "|=" -> KofBinaryOp.OR;
                                    case "^=" -> KofBinaryOp.XOR;
                                    default -> KofBinaryOp.ADD;
                                };
                                ops.add(new KofBinary(binOp, valType));
                                emitWideningIfNeeded(ops, valType, valType);
                                ops.add(new KofStoreField(boxLv.type(), "value", valType));
                            } else {
                                ops.add(new KofLoadLocal(boxLv.type(), boxLv.index()));
                                localIdx = emitExpression(ae.value(), ops, owner, localIdx, locals);
                                emitWideningIfNeeded(ops, inferExprType(ae.value(), locals), valType);
                                ops.add(new KofStoreField(boxLv.type(), "value", valType));
                            }
                            yield localIdx;
                        }
                    }
                }
                localIdx = emitExpression(ae.value(), ops, owner, localIdx, locals);
                if (ae.target() instanceof IdentifierExpr ie) {
                    for (int i = locals.size() - 1; i >= 0; i--) {
                        if (locals.get(i).name().equals(ie.name())) {
                            String op = ae.operator();
                            if ("+=".equals(op) && BuiltinTypes.isString(locals.get(i).type())) {
                                ops.add(new KofLoadLocal(locals.get(i).type(), locals.get(i).index()));
                                ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                                        List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING,
                                        KofCallKind.STATIC));
                                localIdx = emitExpression(ae.value(), ops, owner, localIdx, locals);
                                ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                                        List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING,
                                        KofCallKind.STATIC));
                                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
                            } else if ("+=".equals(op) || "-=".equals(op) || "*=".equals(op)
                                    || "/=".equals(op) || "%=".equals(op)
                                    || "&=".equals(op) || "|=".equals(op) || "^=".equals(op)) {
                                ops.add(new KofLoadLocal(locals.get(i).type(), locals.get(i).index()));
                                KofBinaryOp binOp = switch (op) {
                                    case "+=" -> KofBinaryOp.ADD;
                                    case "-=" -> KofBinaryOp.SUB;
                                    case "*=" -> KofBinaryOp.MUL;
                                    case "/=" -> KofBinaryOp.DIV;
                                    case "%=" -> KofBinaryOp.MOD;
                                    case "&=" -> KofBinaryOp.AND;
                                    case "|=" -> KofBinaryOp.OR;
                                    case "^=" -> KofBinaryOp.XOR;
                                    default -> KofBinaryOp.ADD;
                                };
                                ops.add(new KofBinary(binOp, locals.get(i).type()));
                            }
                            emitWideningIfNeeded(ops, inferExprType(ae.value(), locals), locals.get(i).type());
                            ops.add(new KofStoreLocal(locals.get(i).type(), locals.get(i).index()));
                            yield localIdx;
                        }
                    }
                }
                ops.add(new KofStoreLocal(Type.UnknownType.UNKNOWN, localIdx));
                yield localIdx;
            }
            case NewExpr ne -> {
                Type type = toType(ne.typeName());
                if ("List".equals(ne.typeName()) || "ArrayList".equals(ne.typeName())) {
                    type = BuiltinTypes.LIST;
                }
                if (!ne.typeArguments().isEmpty() && type instanceof Type.ClassType cts) {
                    type = new Type.ClassType(cts.packageName(), cts.name(),
                            ne.typeArguments().stream().map(this::toType).toList());
                }
                if (BuiltinTypes.isList(type)) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : ne.arguments()) argTypes.add(inferExprType(arg, locals));
                    ops.add(new KofCall(BuiltinTypes.LIST, "kof_list_new", argTypes, BuiltinTypes.LIST, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                List<Type> argTypes = new ArrayList<>();
                for (ExpressionNode arg : ne.arguments()) argTypes.add(inferExprType(arg, locals));
                SymbolTable.ConstructorSymbol resolvedCtor = semanticAnalyzer.getResolvedConstructor(ne);
                ops.add(new KofNewObject(type, argTypes));
                ops.add(new KofDup());
                List<Type> ctorParamTypes;
                if (resolvedCtor != null
                        && resolvedCtor.parameterTypes().size() == ne.arguments().size()) {
                    ctorParamTypes = resolvedCtor.parameterTypes();
                } else if (type instanceof Type.ClassType ct && !ct.packageName().isEmpty()
                        && externalClasspath != null
                        && externalClasspath.knows(ct.internalName())) {
                    // construtor de classe externa: descritor exato do classpath
                    ExternalClasspath.MethodSignature extCtor =
                            externalClasspath.resolveConstructor(ct.internalName(), ne.arguments().size());
                    if (extCtor != null) {
                        List<Type> formal = new ArrayList<>();
                        for (String d : extCtor.parameterDescriptors()) {
                            formal.add(ExternalClasspath.typeFromDescriptor(d));
                        }
                        ctorParamTypes = formal;
                    } else {
                        ctorParamTypes = argTypes;
                    }
                } else {
                    ctorParamTypes = argTypes;
                }
                localIdx = emitArgumentsWithFormalTypes(ne.arguments(), ctorParamTypes, ops, owner, localIdx, locals);
                ops.add(new KofCall(type, "<init>", ctorParamTypes, Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                yield localIdx;
            }
            case NewArrayExpr na -> {
                Type elemType = toType(na.elementType());
                localIdx = emitExpression(na.size(), ops, owner, localIdx, locals);
                ops.add(new KofNewArray(elemType));
                yield localIdx;
            }
            case ArrayAccessExpr aa -> {
                localIdx = emitExpression(aa.receiver(), ops, owner, localIdx, locals);
                localIdx = emitExpression(aa.index(), ops, owner, localIdx, locals);
                Type recvType = inferExprType(aa.receiver(), locals);
                Type elemType = Type.arrayElementType(recvType);
                ops.add(new KofArrayLoad(elemType));
                yield localIdx;
            }
            case FieldAccessExpr fa -> {
                if (fa.receiver() instanceof IdentifierExpr pId && KofUi.isPalette(pId.name())) {
                    Integer color = KofUi.paletteColor(fa.fieldName());
                    if (color != null) {
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, color));
                        yield localIdx;
                    }
                }
                if (fa.receiver() instanceof IdentifierExpr sid2 && "super".equals(sid2.name())
                        && !owner.isEmpty() && semanticAnalyzer != null) {
                    // super.campo: GETFIELD com owner na superclasse
                    String superInternal = findSuperClass(owner);
                    if (superInternal == null) superInternal = "java/lang/Object";
                    superInternal = superInternal.replace('.', '/');
                    Type superType = ownerTypeFromInternal(superInternal);
                    String superSimple = superInternal.substring(superInternal.lastIndexOf('/') + 1);
                    SymbolTable.Symbol fieldSym = semanticAnalyzer.resolveInHierarchy(superSimple, fa.fieldName());
                    Type fieldType = fieldSym != null ? fieldSym.type() : inferExprType(fa, locals);
                    ops.add(new KofLoadLocal(ownerTypeFromInternal(owner), 0));
                    ops.add(new KofLoadField(superType, fa.fieldName(), fieldType));
                    yield localIdx;
                }
                {
                    // campo de classe EXTERNA: owner e tipo vêm do classpath
                    Type extRecv = inferExprType(fa.receiver(), locals);
                    if (extRecv instanceof Type.ClassType ect && !ect.packageName().isEmpty()
                            && externalClasspath != null
                            && externalClasspath.knows(ect.internalName())) {
                        String desc = externalClasspath.resolveFieldType(ect.internalName(), fa.fieldName());
                        if (desc != null) {
                            localIdx = emitExpression(fa.receiver(), ops, owner, localIdx, locals);
                            ops.add(new KofLoadField(ect, fa.fieldName(),
                                    ExternalClasspath.typeFromDescriptor(desc)));
                            yield localIdx;
                        }
                    }
                }
                Type faType = inferExprType(fa.receiver(), locals);
                if (KofProcess.isResult(faType) && KofProcess.isField(fa.fieldName())) {
                    localIdx = emitExpression(fa.receiver(), ops, owner, localIdx, locals);
                    ops.add(new KofLoadField(KofProcess.RESULT, fa.fieldName(),
                            KofProcess.fieldType(fa.fieldName())));
                    yield localIdx;
                }
                if (KofUi.isWindow(faType) && "title".equals(fa.fieldName())) {
                    localIdx = emitExpression(fa.receiver(), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_window_title", List.of(Type.PrimitiveType.INT),
                            BuiltinTypes.STRING, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (KofUi.isLabel(faType) && "text".equals(fa.fieldName())) {
                    localIdx = emitExpression(fa.receiver(), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_label_text", List.of(Type.PrimitiveType.INT),
                            BuiltinTypes.STRING, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (KofUi.isLabel(faType) && "fontSize".equals(fa.fieldName())) {
                    localIdx = emitExpression(fa.receiver(), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_label_font_size", List.of(Type.PrimitiveType.INT),
                            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (KofUi.isLabel(faType) && "bold".equals(fa.fieldName())) {
                    localIdx = emitExpression(fa.receiver(), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_label_bold", List.of(Type.PrimitiveType.INT),
                            Type.PrimitiveType.BOOL, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (KofUi.isLabel(faType) && "color".equals(fa.fieldName())) {
                    localIdx = emitExpression(fa.receiver(), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_label_color", List.of(Type.PrimitiveType.INT),
                            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (KofUi.isButton(faType) && "text".equals(fa.fieldName())) {
                    localIdx = emitExpression(fa.receiver(), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_button_text", List.of(Type.PrimitiveType.INT),
                            BuiltinTypes.STRING, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                if (KofUi.isInput(faType) && "text".equals(fa.fieldName())) {
                    localIdx = emitExpression(fa.receiver(), ops, owner, localIdx, locals);
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_input_text", List.of(Type.PrimitiveType.INT),
                            BuiltinTypes.STRING, KofCallKind.FUNCTION));
                    yield localIdx;
                }
                Type recvType = inferExprType(fa.receiver(), locals);
                if (BuiltinTypes.isList(recvType) && ("size".equals(fa.fieldName()) || "length".equals(fa.fieldName()))) {
                    localIdx = emitExpression(fa.receiver(), ops, owner, localIdx, locals);
                    ops.add(new KofCall(recvType, "kof_list_size", List.of(), Type.PrimitiveType.INT, KofCallKind.INSTANCE));
                    yield localIdx;
                }
                if (Type.isString(recvType) && ("name".equals(fa.fieldName()) || "path".equals(fa.fieldName()))) {
                    localIdx = emitExpression(fa.receiver(), ops, owner, localIdx, locals);
                    yield localIdx;
                }
                // enum constant access: Color.Red — literal String tipado como Color
                if (recvType instanceof Type.ClassType ct && ct.packageName().isEmpty()
                        && isEnumName(ct.name())) {
                    if (!enumConstantsOf(ct.name()).contains(fa.fieldName())) {
                        if (currentDiagnostics != null) {
                            currentDiagnostics.error(fa.position() != null ? fa.position().file() : "",
                                    fa.position() != null ? fa.position().line() : 0,
                                    fa.position() != null ? fa.position().column() : 0, 0,
                                    "enum '" + ct.name() + "' não tem constante '" + fa.fieldName() + "'",
                                    "SEM030");
                        }
                        yield localIdx;
                    }
                    ops.add(new KofLoadLiteral(BuiltinTypes.STRING, fa.fieldName()));
                    yield localIdx;
                }
                // static field access: Class.field — no receiver on the stack
                if (recvType instanceof Type.ClassType ct && ct.packageName().isEmpty()
                        && isEnumName(ct.name()) && enumConstantsOf(ct.name()).contains(fa.fieldName())) {
                    ops.add(new KofLoadLiteral(BuiltinTypes.STRING, fa.fieldName()));
                    yield localIdx;
                }
                if (recvType instanceof Type.ClassType ct && semanticAnalyzer != null) {
                    SymbolTable.Symbol staticSym = resolveFieldInHierarchy(ct.name(), fa.fieldName());
                    if (staticSym instanceof SymbolTable.FieldSymbol fs
                            && (fs.accessFlags() & AccessFlags.STATIC) != 0) {
                        ops.add(new KofGetStatic(recvType, fa.fieldName(), fs.type()));
                        yield localIdx;
                    }
                }
                localIdx = emitExpression(fa.receiver(), ops, owner, localIdx, locals);
                if (recvType instanceof Type.ArrayType && "length".equals(fa.fieldName())) {
                    ops.add(new KofArrayLength());
                } else if (Type.isString(recvType) && "length".equals(fa.fieldName())) {
                    ops.add(new KofLoadField(recvType, fa.fieldName(), Type.PrimitiveType.INT));
                } else {
                    Type fieldType = Type.UnknownType.UNKNOWN;
                    SymbolTable.Symbol accessor = null;
                    if (recvType instanceof Type.ClassType ct && semanticAnalyzer != null) {
                        accessor = resolveFieldInHierarchy(ct.name(), fa.fieldName());
                        if (accessor != null) fieldType = accessor.type();
                    }
                    if (accessor instanceof SymbolTable.MethodSymbol ms && ms.parameterTypes().isEmpty()) {
                        ops.add(new KofCall(recvType, fa.fieldName(), List.of(), ms.returnType(), KofCallKind.INSTANCE));
                    } else {
                        ops.add(new KofLoadField(recvType, fa.fieldName(), fieldType));
                    }
                }
                yield localIdx;
            }
            case IfExpr ie -> {
                LabelId thenLabel = LabelId.create();
                LabelId elseLabel = LabelId.create();
                LabelId endLabel = LabelId.create();
                if (ie.condition() instanceof BinaryExpr bin && isComparisonShortcut(bin, locals)) {
                    localIdx = emitComparisonShortcut(bin, ops, owner, localIdx, locals);
                    ops.add(new KofConditionalJump(mapComparison(bin.operator()), comparisonOperandType(bin, locals), thenLabel, elseLabel));
                } else {
                    localIdx = emitExpression(ie.condition(), ops, owner, localIdx, locals);
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                    ops.add(new KofConditionalJump(KofComparison.NE, thenLabel, elseLabel));
                }
                ops.add(new KofLabel(thenLabel));
                localIdx = emitExpression(ie.thenExpr(), ops, owner, localIdx, locals);
                ops.add(new KofJump(endLabel));
                ops.add(new KofLabel(elseLabel));
                localIdx = emitExpression(ie.elseExpr(), ops, owner, localIdx, locals);
                ops.add(new KofLabel(endLabel));
                yield localIdx;
            }
            case LambdaExpr le -> {
                Type.FunctionType ft = (Type.FunctionType) inferExprType(le, locals);
                List<IRLocalVariable> captures = collectCaptures(le, locals);
                String lambdaClass = lambdaClass(le, ft, captures);
                List<IRLocalVariable> effective = lambdaEffectiveCaptures.get(le);
                if (effective != null) captures = effective;
                if (ft.className() == null) {
                    ft = new Type.FunctionType(ft.parameterTypes(), ft.returnType(), lambdaClass);
                }
                Type lambdaType = new Type.ClassType("", lambdaClass, List.of());
                List<Type> captureTypes = new ArrayList<>();
                for (IRLocalVariable cap : captures) captureTypes.add(cap.type());
                ops.add(new KofNewObject(lambdaType, captureTypes));
                ops.add(new KofDup());
                for (IRLocalVariable cap : captures) {
                    ops.add(new KofLoadLocal(cap.type(), cap.index()));
                }
                ops.add(new KofCall(lambdaType, "<init>", captureTypes,
                        Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                yield localIdx;
            }
            default -> localIdx;
        };
    }

    private Type inferExprType(ExpressionNode expr, List<IRLocalVariable> locals) {
        return switch (expr) {
            case LiteralExpr lit -> switch (lit.kind()) {
                case ConcreteLiteralKind.INT -> Type.PrimitiveType.INT;
                case ConcreteLiteralKind.LONG -> Type.PrimitiveType.LONG;
                case ConcreteLiteralKind.FLOAT -> Type.PrimitiveType.FLOAT;
                case ConcreteLiteralKind.DOUBLE -> Type.PrimitiveType.DOUBLE;
                case ConcreteLiteralKind.STRING -> BuiltinTypes.STRING;
                case ConcreteLiteralKind.BOOLEAN -> Type.PrimitiveType.BOOL;
                case ConcreteLiteralKind.CHAR -> Type.PrimitiveType.CHAR;
                case ConcreteLiteralKind.NULL -> Type.UnknownType.UNKNOWN;
            };
            case IdentifierExpr ie -> {
                if (loweringMain && "args".equals(ie.name())) {
                    if (mainArgsListField) {
                        yield KofProcess.STRING_LIST;
                    }
                    yield new Type.ArrayType(BuiltinTypes.STRING);
                }
                for (int i = locals.size() - 1; i >= 0; i--) {
                    if (locals.get(i).name().equals(ie.name())) {
                        IRLocalVariable lv = locals.get(i);
                        if (isBoxType(lv.type())) {
                            yield boxValueTypes.get(((Type.ClassType) lv.type()).name());
                        }
                        yield lv.type();
                    }
                }
                if (semanticAnalyzer != null) {
                    // Resolve field within the current class first (via 'this'
                    // at index 0) to avoid picking a same-named field from an
                    // unrelated class — e.g. Config.entries vs MemoryLayer.entries.
                    if (!locals.isEmpty() && locals.get(0).type() instanceof Type.ClassType thisType
                            && !thisType.name().equals("Object")) {
                        SymbolTable.Symbol thisField = semanticAnalyzer.resolveInHierarchy(
                                thisType.name(), ie.name());
                        if (thisField != null) {
                            if (thisField instanceof SymbolTable.FieldSymbol fs) yield fs.type();
                            if (thisField instanceof SymbolTable.MethodSymbol ms
                                    && ms.parameterTypes().isEmpty()) yield ms.returnType();
                        }
                    }
                    SymbolTable.Symbol sym = resolveFromSemantic(ie.name());
                    if (sym != null) yield sym.type();
                    SymbolTable.ClassSymbol cls = semanticAnalyzer.getClass(ie.name());
                    if (cls != null) yield cls.type();
                }
                yield Type.UnknownType.UNKNOWN;
            }
            case UnaryExpr ue -> inferExprType(ue.operand(), locals);
            case BinaryExpr bin -> {
                // Left-associative chains (huge string concatenations in
                // generated UIs, editors) are iterated instead of recursed:
                // deep chains would overflow the compiler's own stack.
                java.util.List<BinaryExpr> chain = new ArrayList<>();
                ExpressionNode cursor = bin;
                while (cursor instanceof BinaryExpr be) {
                    chain.add(be);
                    cursor = be.left();
                }
                Type leftType = inferExprType(cursor, locals);
                for (int ci = chain.size() - 1; ci >= 0; ci--) {
                    BinaryExpr be = chain.get(ci);
                    Type rightType = inferExprType(be.right(), locals);
                    if ("+".equals(be.operator())
                            && (Type.isString(leftType) || Type.isString(rightType))) {
                        leftType = BuiltinTypes.STRING;
                        continue;
                    }
                    if ("instanceof".equals(be.operator())) {
                        leftType = Type.PrimitiveType.BOOL;
                        continue;
                    }
                    if ("as".equals(be.operator())) {
                        // "x as Tipo": o tipo alvo passa pelo toType (imports)
                        if (be.right() instanceof IdentifierExpr rie
                                && rightType instanceof Type.UnknownType) {
                            Type q = toType(rie.name());
                            if (!(q instanceof Type.UnknownType)) leftType = q;
                            else leftType = rightType;
                        } else {
                            leftType = rightType;
                        }
                        continue;
                    }
                    if (isComparisonOp(be.operator())) {
                        leftType = Type.PrimitiveType.BOOL;
                        continue;
                    }
                    leftType = leftType;
                }
                yield leftType;
            }
            case MethodCallExpr mc -> {
                // super.metodo(): resolvido AQUI (o cache do analyzer é
                // limpo a cada classe/passe — não dá para confiar nele)
                if (mc.receiver() instanceof IdentifierExpr srid && "super".equals(srid.name())
                        && semanticAnalyzer != null && currentLoweringOwner != null) {
                    String simple = currentLoweringOwner.substring(currentLoweringOwner.lastIndexOf('/') + 1);
                    SymbolTable.ClassSymbol self = semanticAnalyzer.getClass(simple);
                    String sup = self != null && self.superClass() != null ? self.superClass() : "Object";
                    sup = sup.replace('.', '/');
                    SymbolTable.Symbol m2 = semanticAnalyzer.resolveInHierarchy(
                            sup.substring(sup.lastIndexOf('/') + 1), mc.methodName());
                    if (m2 instanceof SymbolTable.MethodSymbol ms2) yield ms2.returnType();
                    yield Type.UnknownType.UNKNOWN;
                }
                // o analyzer já tipou esta expressão durante a análise:
                // fonte secundária para os demais casos
                if (semanticAnalyzer != null) {
                    Type semantic = semanticAnalyzer.getExpressionType(mc);
                    if (!(semantic instanceof Type.UnknownType)) {
                        if (semantic instanceof Type.TypeVariable tv && mc.receiver() != null) {
                            Type recvT = inferExprType(mc.receiver(), locals);
                            Type subst = substituteTypeVariable(tv.name(), recvT);
                            if (subst != null) yield subst;
                        }
                        yield semantic;
                    }
                }
                if (mc.receiver() == null && semanticAnalyzer != null
                        && semanticAnalyzer.getClass(mc.methodName()) != null) {
                    yield semanticAnalyzer.getClass(mc.methodName()).type();
                }
                if ("println".equals(mc.methodName()) || "print".equals(mc.methodName())) yield Type.PrimitiveType.VOID;
                if ("now".equals(mc.methodName()) && mc.receiver() == null && mc.arguments().isEmpty()) {
                    yield Type.PrimitiveType.LONG;
                }
                if (mc.receiver() == null && "transaction".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    yield Type.PrimitiveType.VOID;
                }
                if (("readLine".equals(mc.methodName()) || "readFile".equals(mc.methodName()))
                        && mc.receiver() == null) {
                    yield BuiltinTypes.STRING;
                }
                if (mc.receiver() == null && KofWeb.isContextFunction(mc.methodName())
                        && KofWeb.contextCall(mc.methodName(), mc.arguments().size()) != null) {
                    yield BuiltinTypes.STRING;
                }
                if ("writeFile".equals(mc.methodName()) && mc.receiver() == null) {
                    yield Type.PrimitiveType.INT;
                }
                if (mc.receiver() == null && KofIo.isConstructor(mc.methodName()) && mc.arguments().size() == 1) {
                    yield KofIo.constructorType(mc.methodName());
                }
                if (mc.receiver() == null && "Color".equals(mc.methodName())
                        && (mc.arguments().size() == 1 || mc.arguments().size() == 3)) {
                    yield KofUi.COLOR;
                }
                if (mc.receiver() == null && "Window".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    yield KofUi.WINDOW;
                }
                if (mc.receiver() == null && "Label".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    yield KofUi.LABEL;
                }
                if (mc.receiver() == null && "Button".equals(mc.methodName())
                        && (mc.arguments().size() == 1 || mc.arguments().size() == 2)) {
                    yield KofUi.BUTTON;
                }
                if (mc.receiver() == null && "Input".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    yield KofUi.INPUT;
                }
                if (mc.receiver() == null && ("Column".equals(mc.methodName()) || "Row".equals(mc.methodName()))
                        && mc.arguments().size() == 1) {
                    yield "Column".equals(mc.methodName()) ? KofUi.COLUMN : KofUi.ROW;
                }
                if (mc.receiver() == null && "View".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    yield KofUi.VIEW;
                }
                if (mc.receiver() == null && "Style".equals(mc.methodName()) && mc.arguments().size() == 4) {
                    yield KofUi.STYLE;
                }
                if (mc.receiver() instanceof IdentifierExpr rid3 && KofUi.isConstructor(rid3.name())) {
                    KofUi.UiCall uiCall = KofUi.staticMethod(rid3.name(), mc.methodName(), mc.arguments().size());
                    if (uiCall != null) yield uiCall.returnType();
                }
                if ("listOf".equals(mc.methodName()) && mc.receiver() == null) {
                    yield new Type.ClassType("kof", "List", List.of(listOfElementType(mc, locals)));
                }
                if ("mapOf".equals(mc.methodName()) && mc.receiver() == null) {
                    yield new Type.ClassType("kof", "Map", List.of(Type.UnknownType.UNKNOWN, Type.UnknownType.UNKNOWN));
                }
                if ("setOf".equals(mc.methodName()) && mc.receiver() == null) {
                    Type elemType = mc.arguments().isEmpty() ? Type.UnknownType.UNKNOWN : inferExprType(mc.arguments().get(0), locals);
                    yield new Type.ClassType("kof", "Set", List.of(elemType));
                }
                if (mc.receiver() == null && "__kof_spawn_expr".equals(mc.methodName())) {
                    Type t = inferExprType(mc.arguments().get(0), locals);
                    yield new Type.ClassType("kof.concurrent", "Handle", List.of(t));
                }
                                if (mc.receiver() == null && "poll".equals(mc.methodName())
                        && mc.arguments().size() == 1 && findLocalVar("poll", locals) == null) {
                    Type h = inferExprType(mc.arguments().get(0), locals);
                    if (h instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) {
                        yield ct.typeArguments().get(0);
                    }
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() == null && "done".equals(mc.methodName())
                        && mc.arguments().size() == 1 && findLocalVar("done", locals) == null) {
                    yield Type.PrimitiveType.BOOL;
                }
if (mc.receiver() == null && "__kof_await".equals(mc.methodName())) {
                    Type t = inferExprType(mc.arguments().get(0), locals);
                    if (t instanceof Type.ClassType ct
                            && "kof.concurrent".equals(ct.packageName())
                            && !ct.typeArguments().isEmpty()) {
                        yield ct.typeArguments().get(0);
                    }
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid && isEnumName(rid.name())
                        && findLocalVar(rid.name(), locals) == null) {
                    java.util.List<String> consts = enumConstantsOf(rid.name());
                    Type enumT = new Type.ClassType("", rid.name(), List.of());
                    // MVP: elementos tipados como String (runtime do enum é o nome);
                    // comparação com constantes funciona via string-equals
                    if ("values".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                        yield new Type.ClassType("kof", "List", List.of(BuiltinTypes.STRING));
                    }
                    if ("valueOf".equals(mc.methodName()) && mc.arguments().size() == 1) {
                        yield enumT;
                    }
                    // constante via sintaxe de método? Color.Red() — não suportado
                    if (consts.contains(mc.methodName())) yield enumT;
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid && "json".equals(rid.name())) {
                    if ("encode".equals(mc.methodName())) yield BuiltinTypes.STRING;
                    if ("decode".equals(mc.methodName()) && !mc.typeArguments().isEmpty()) {
                        yield toType(mc.typeArguments().get(0));
                    }
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid && KofWeb.isWebNamespace(rid.name())) {
                    if ("app".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                        yield KofWeb.APP;
                    }
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid && KofDb.isDbNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                    boolean typed = KofDb.isQuery(mc.methodName()) && !mc.typeArguments().isEmpty();
                    KofDb.DbCall dbCall = KofDb.staticCall(mc.methodName(), argTypes, typed);
                    if (dbCall != null) {
                        if (typed && !mc.typeArguments().isEmpty()) {
                            yield new Type.ClassType("kof", "List",
                                    List.of(toType(mc.typeArguments().get(0))));
                        }
                        yield dbCall.returnType();
                    }
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid && KofHttp.isHttpNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                    KofHttp.HttpCall httpCall = KofHttp.staticCall(mc.methodName(), argTypes);
                    if (httpCall != null) yield httpCall.returnType();
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid && KofMq.isMqNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                    KofMq.MqCall mqCall = KofMq.staticCall(mc.methodName(), argTypes);
                    if (mqCall != null) yield mqCall.returnType();
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid && KofTime.isTimeNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                    KofTime.TimeCall timeCall = KofTime.staticCall(mc.methodName(), argTypes);
                    if (timeCall != null) yield timeCall.returnType();
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid && KofLog.isLogNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                    KofLog.LogCall logCall = KofLog.staticCall(mc.methodName(), argTypes);
                    if (logCall != null) yield logCall.returnType();
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid && KofOrm.isOrmNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                    boolean typed = !mc.typeArguments().isEmpty();
                    String entityName = typed ? mc.typeArguments().get(0) : null;
                    KofOrm.OrmCall ormCall = KofOrm.staticCall(mc.methodName(), argTypes, typed, entityName);
                    if (ormCall != null) {
                        if ("save".equals(mc.methodName()) && !argTypes.isEmpty()) {
                            yield argTypes.get(argTypes.size() - 1);
                        }
                        if (typed && !mc.typeArguments().isEmpty()) {
                            if ("all".equals(mc.methodName()) || "where".equals(mc.methodName())
                                    || "page".equals(mc.methodName())) {
                                yield new Type.ClassType("kof", "List",
                                        List.of(toType(mc.typeArguments().get(0))));
                            }
                            if ("find".equals(mc.methodName())) {
                                yield toType(mc.typeArguments().get(0));
                            }
                        }
                        yield ormCall.returnType();
                    }
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid && "process".equals(rid.name())
                        && findLocalVar(rid.name(), locals) == null) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                    KofProcess.ProcessCall procCall = KofProcess.runCall(argTypes);
                    if (procCall != null) yield procCall.returnType();
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid && KofConfig.isConfigNamespace(rid.name())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                    KofConfig.ConfigCall cfgCall = KofConfig.staticCall(mc.methodName(), argTypes);
                    if (cfgCall != null) yield cfgCall.returnType();
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid && KofTetris.isTetrisNamespace(rid.name())) {
                    KofTetris.TetrisCall tetrisCall = KofTetris.staticMethod(rid.name(), mc.methodName(),
                            mc.arguments().size());
                    if (tetrisCall != null) yield tetrisCall.returnType();
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid2 && KofIo.isConstructor(rid2.name())) {
                    KofIo.IoCall ioCall = KofIo.staticMethod(rid2.name(), mc.methodName(), mc.arguments().size());
                    if (ioCall != null) yield ioCall.returnType();
                }
                if (mc.receiver() != null) {
                    Type recvType = inferExprType(mc.receiver(), locals);
                    if (mc.receiver() instanceof IdentifierExpr rid && KofSecurity.isSecurityNamespace(rid.name())) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                        KofSecurity.SecCall secCall = KofSecurity.staticMethod(rid.name(), mc.methodName(), argTypes);
                        if (secCall != null) yield secCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && KofValidation.isValidationNamespace(rid.name())) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                        KofValidation.ValidationCall vCall = KofValidation.staticMethod(rid.name(), mc.methodName(), argTypes);
                        if (vCall != null) yield vCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && KofObservability.isObservabilityNamespace(rid.name())) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                        KofObservability.ObservabilityCall oCall = KofObservability.staticMethod(rid.name(), mc.methodName(), argTypes);
                        if (oCall != null) yield oCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (KofUi.isUiType(recvType)) {
                        KofUi.UiCall uiCall = KofUi.instanceMethod(recvType, mc.methodName(), mc.arguments().size());
                        if (uiCall != null) yield uiCall.returnType();
                    }
                    if (KofWeb.isAppType(recvType)) {
                        List<Type> webArgTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) webArgTypes.add(inferExprType(arg, locals));
                        KofWeb.WebCall webCall = KofWeb.instanceMethod(mc.methodName(), webArgTypes);
                        if (webCall != null) yield webCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (KofIo.isIoType(recvType)) {
                        KofIo.IoCall ioCall = KofIo.instanceMethod(recvType, mc.methodName(), mc.arguments().size());
                        if (ioCall != null) yield ioCall.returnType();
                        if (KofIo.isIdentityMethod(mc.methodName())) yield recvType;
                    }
                    if (recvType instanceof Type.FunctionType ft) {
                        yield ft.returnType();
                    }
                    if (isEnumType(recvType) && "name".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                        yield BuiltinTypes.STRING;
                    }
                    if (BuiltinTypes.isList(recvType)) {
                        String mn = mc.methodName();
                        if ("get".equals(mn) || "remove".equals(mn)) yield listElementType(recvType);
                        if ("size".equals(mn) || "length".equals(mn) || "count".equals(mn)) yield Type.PrimitiveType.INT;
                        if ("contains".equals(mn) || "isEmpty".equals(mn)) yield Type.PrimitiveType.BOOL;
                        if ("add".equals(mn) || "push".equals(mn) || "append".equals(mn)
                                || "set".equals(mn) || "clear".equals(mn)) {
                            yield Type.PrimitiveType.VOID;
                        }
                    }
                    if (BuiltinTypes.isMap(recvType)) {
                        String mn = mc.methodName();
                        Type valueType = Type.UnknownType.UNKNOWN;
                        if (recvType instanceof Type.ClassType ct && ct.typeArguments().size() == 2) valueType = ct.typeArguments().get(1);
                        Type keyType = Type.UnknownType.UNKNOWN;
                        if (recvType instanceof Type.ClassType ct && ct.typeArguments().size() == 2) keyType = ct.typeArguments().get(0);
                        if ("get".equals(mn) || "remove".equals(mn)) yield valueType;
                        if ("put".equals(mn)) yield valueType;
                        if ("size".equals(mn) || "length".equals(mn) || "count".equals(mn)) yield Type.PrimitiveType.INT;
                        if ("containsKey".equals(mn) || "contains".equals(mn) || "isEmpty".equals(mn)) yield Type.PrimitiveType.BOOL;
                        if ("clear".equals(mn)) yield Type.PrimitiveType.VOID;
                        if ("keys".equals(mn)) yield new Type.ClassType("kof", "List", List.of(keyType));
                        if ("values".equals(mn)) yield new Type.ClassType("kof", "List", List.of(valueType));
                    }
                    if (BuiltinTypes.isSet(recvType)) {
                        String mn = mc.methodName();
                        Type elemType = Type.UnknownType.UNKNOWN;
                        if (recvType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) elemType = ct.typeArguments().get(0);
                        if ("size".equals(mn) || "length".equals(mn) || "count".equals(mn)) yield Type.PrimitiveType.INT;
                        if ("contains".equals(mn) || "isEmpty".equals(mn)) yield Type.PrimitiveType.BOOL;
                        if ("add".equals(mn) || "remove".equals(mn)) yield Type.PrimitiveType.BOOL;
                        if ("clear".equals(mn)) yield Type.PrimitiveType.VOID;
                    }
                    if (Type.isString(recvType)) {
                        String mn = mc.methodName();
                        if ("charAt".equals(mn)) yield Type.PrimitiveType.CHAR;
                        if ("toInt".equals(mn)) yield Type.PrimitiveType.INT;
                        if ("toLong".equals(mn)) yield Type.PrimitiveType.LONG;
                        if ("toDouble".equals(mn)) yield Type.PrimitiveType.DOUBLE;
                        if ("toFloat".equals(mn)) yield Type.PrimitiveType.FLOAT;
                        if ("length".equals(mn) || "indexOf".equals(mn) || "compareTo".equals(mn)) yield Type.PrimitiveType.INT;
                        if ("contains".equals(mn) || "startsWith".equals(mn) || "endsWith".equals(mn)
                                || "equals".equals(mn) || "equalsIgnoreCase".equals(mn)) {
                            yield Type.PrimitiveType.BOOL;
                        }
                        if ("substring".equals(mn) || "concat".equals(mn) || "trim".equals(mn)
                                || "toUpperCase".equals(mn) || "toLowerCase".equals(mn)
                                || "replace".equals(mn) || "valueOf".equals(mn)) {
                            yield BuiltinTypes.STRING;
                        }
                        if ("split".equals(mn)) {
                            yield new Type.ArrayType(BuiltinTypes.STRING);
                        }
                    }
                } else if (currentUnit != null) {
                    IRLocalVariable lambdaVar = findLocalVar(mc.methodName(), locals);
                    if (lambdaVar != null && lambdaVar.type() instanceof Type.FunctionType lft) {
                        yield lft.returnType();
                    }
                    for (AstNode d : currentUnit.declarations()) {
                        if (d instanceof FunctionDeclarationNode fn && fn.name().equals(mc.methodName())) {
                            Type returnType = toType(fn.returnType());
                            if (fn.typeParameters().contains(fn.returnType())) {
                                returnType = new Type.TypeVariable(fn.returnType());
                            }
                            if (returnType instanceof Type.TypeVariable tv) {
                                for (int pi = 0; pi < fn.parameters().size(); pi++) {
                                    if (pi < mc.arguments().size() && tv.name().equals(fn.parameters().get(pi).type())) {
                                        yield inferExprType(mc.arguments().get(pi), locals);
                                    }
                                }
                                yield Type.UnknownType.UNKNOWN;
                            }
                            yield returnType;
                        }
                    }
                }
                SymbolTable.MethodSymbol resolvedMethod = semanticAnalyzer.getResolvedMethod(mc);
                if (resolvedMethod != null) {
                    Type rt = resolvedMethod.returnType();
                    if (rt instanceof Type.TypeVariable tv && mc.receiver() != null) {
                        Type recvT = inferExprType(mc.receiver(), locals);
                        Type subst = substituteTypeVariable(tv.name(), recvT);
                        if (subst != null) yield subst;
                    }
                    yield rt;
                }
                if (mc.receiver() != null) {
                    Type recvT = inferExprType(mc.receiver(), locals);
                    if (recvT instanceof Type.ClassType ct && semanticAnalyzer != null) {
                        SymbolTable.Symbol m = semanticAnalyzer.resolveInHierarchy(ct.name(), mc.methodName());
                        if (m instanceof SymbolTable.MethodSymbol ms) {
                            Type rt = ms.returnType();
                            if (rt instanceof Type.TypeVariable tv) {
                                Type subst = substituteTypeVariable(tv.name(), recvT);
                                if (subst != null) yield subst;
                            }
                            yield rt;
                        }
                    }
                    if (recvT instanceof Type.ClassType) {
                        ObjectMethodSig osig = objectMethodSignature(mc.methodName(), mc.arguments().size());
                        if (osig != null) yield osig.returnType();
                    }
                }
                SymbolTable.ClassSymbol cs = semanticAnalyzer != null ? semanticAnalyzer.getClass(mc.methodName()) : null;
                if (cs != null) yield cs.type();
                yield Type.UnknownType.UNKNOWN;
            }
            case NewArrayExpr na -> {
                Type elemType = toType(na.elementType());
                yield new Type.ArrayType(elemType);
            }
            case NewExpr ne -> {
                Type t = toType(ne.typeName());
                if ("List".equals(ne.typeName()) || "ArrayList".equals(ne.typeName())) {
                    t = BuiltinTypes.LIST;
                }
                if (!ne.typeArguments().isEmpty() && t instanceof Type.ClassType cts) {
                    t = new Type.ClassType(cts.packageName(), cts.name(),
                            ne.typeArguments().stream().map(this::toType).toList());
                }
                yield t;
            }
            case ArrayAccessExpr aa -> {
                Type recvType = inferExprType(aa.receiver(), locals);
                if (recvType instanceof Type.ArrayType at) yield at.componentType();
                yield Type.UnknownType.UNKNOWN;
            }
            case FieldAccessExpr fa -> {
                Type recvType = inferExprType(fa.receiver(), locals);
                if (KofProcess.isResult(recvType) && KofProcess.isField(fa.fieldName())) {
                    yield KofProcess.fieldType(fa.fieldName());
                }
                if (KofUi.isWindow(recvType) && "title".equals(fa.fieldName())) {
                    yield BuiltinTypes.STRING;
                }
                if (KofUi.isLabel(recvType) && "text".equals(fa.fieldName())) {
                    yield BuiltinTypes.STRING;
                }
                if (KofUi.isLabel(recvType) && "fontSize".equals(fa.fieldName())) {
                    yield Type.PrimitiveType.INT;
                }
                if (KofUi.isLabel(recvType) && "bold".equals(fa.fieldName())) {
                    yield Type.PrimitiveType.BOOL;
                }
                if (KofUi.isLabel(recvType) && "color".equals(fa.fieldName())) {
                    yield KofUi.COLOR;
                }
                if (fa.receiver() instanceof IdentifierExpr pId && KofUi.isPalette(pId.name())
                        && KofUi.paletteColor(fa.fieldName()) != null) {
                    yield KofUi.COLOR;
                }
                if (BuiltinTypes.isList(recvType) && ("size".equals(fa.fieldName()) || "length".equals(fa.fieldName()))) {
                    yield Type.PrimitiveType.INT;
                }
                if (recvType instanceof Type.ArrayType at && "length".equals(fa.fieldName())) {
                    yield Type.PrimitiveType.INT;
                }
                if (Type.isString(recvType) && "length".equals(fa.fieldName())) {
                    yield Type.PrimitiveType.INT;
                }
                if (Type.isString(recvType) && ("name".equals(fa.fieldName()) || "path".equals(fa.fieldName()))) {
                    yield BuiltinTypes.STRING;
                }
                if (recvType instanceof Type.ClassType ct && ct.packageName().isEmpty()
                        && isEnumName(ct.name())) {
                    if (!enumConstantsOf(ct.name()).contains(fa.fieldName()) && currentDiagnostics != null) {
                        currentDiagnostics.error("", 0, 0, 0,
                                "enum '" + ct.name() + "' não tem constante '" + fa.fieldName() + "'",
                                "SEM030");
                    }
                    yield recvType;
                }
                if (recvType instanceof Type.ClassType ct && semanticAnalyzer != null) {
                    SymbolTable.Symbol s = semanticAnalyzer.resolveInHierarchy(ct.name(), fa.fieldName());
                    if (s instanceof SymbolTable.FieldSymbol fs) yield fs.type();
                    if (s instanceof SymbolTable.MethodSymbol ms && ms.parameterTypes().isEmpty()) {
                        yield ms.returnType();
                    }
                }
                yield Type.UnknownType.UNKNOWN;
            }
            case LambdaExpr le -> {
                List<Type> paramTypes = new ArrayList<>();
                List<IRLocalVariable> extended = new ArrayList<>(locals);
                int pidx = 0;
                for (FormalParameterNode p : le.parameters()) {
                    Type pt = toType(p.type());
                    paramTypes.add(pt);
                    extended.add(new IRLocalVariable(pidx++, p.name(), pt));
                }
                Type returnType = Type.UnknownType.UNKNOWN;
                for (StatementNode s : le.body()) {
                    if (s instanceof ReturnStmt rs && rs.value() != null) {
                        returnType = inferExprType(rs.value(), extended);
                        break;
                    }
                }
                if (Type.UnknownType.UNKNOWN.equals(returnType)) {
                    // A lambda whose body has no return statement is void.
                    // Without this, the synthetic invoke method is lowered with
                    // an Object return and the backends misparse the bare
                    // KofReturn (empty value stack).
                    returnType = Type.PrimitiveType.VOID;
                }
                yield new Type.FunctionType(paramTypes, returnType, lambdaClassNames.get(le));
            }
            case IfExpr ie -> {
                Type thenType = inferExprType(ie.thenExpr(), locals);
                Type elseType = inferExprType(ie.elseExpr(), locals);
                yield thenType;
            }
            default -> Type.UnknownType.UNKNOWN;
        };
    }

    private SymbolTable.Symbol resolveFromSemantic(String name) {
        if (semanticAnalyzer == null) return null;
        for (var entry : semanticAnalyzer.allClasses().entrySet()) {
            SymbolTable.ClassSymbol cs = entry.getValue();
            SymbolTable.Symbol s = cs.members().resolve(name);
            if (s != null) return s;
        }
        return null;
    }

    private SymbolTable.Symbol resolveFieldInHierarchy(String className, String fieldName) {
        if (semanticAnalyzer == null) return null;
        return semanticAnalyzer.resolveInHierarchy(className, fieldName);
    }

    private String findSuperClass(String internalName) {
        if (semanticAnalyzer == null) return null;
        String simpleName = internalName.substring(internalName.lastIndexOf('/') + 1);
        SymbolTable.ClassSymbol cs = semanticAnalyzer.getClass(simpleName);
        if (cs == null) return null;
        String superName = cs.superClass();
        if (superName == null || superName.isEmpty() || "Object".equals(superName)) return null;
        if (!superName.contains("/")) {
            SymbolTable.ClassSymbol superCs = semanticAnalyzer.getClass(superName);
            if (superCs != null) return superCs.internalName();
        }
        return superName;
    }

    /**
     * True quando a cadeia de superclasses a partir de internalName é
     * inteiramente conhecida pelo SemanticAnalyzer (nenhuma classe externa
     * no caminho). Só nesse caso "método não resolvido" prova inexistência.
     */
    private boolean hierarchyFullyKnown(String internalName) {
        if (semanticAnalyzer == null) return false;
        String cur = internalName;
        int hops = 0;
        while (cur != null && !"java/lang/Object".equals(cur) && hops++ < 32) {
            String simple = cur.substring(cur.lastIndexOf('/') + 1);
            SymbolTable.ClassSymbol cs = semanticAnalyzer.getClass(simple);
            if (cs == null) return false;
            String sup = cs.superClass();
            if (sup == null || sup.isEmpty() || "Object".equals(sup)) return true;
            if (sup.contains(".")) {
                cur = sup.replace('.', '/');
            } else {
                SymbolTable.ClassSymbol supCs = semanticAnalyzer.getClass(sup);
                cur = supCs != null ? supCs.internalName() : sup;
            }
        }
        return true;
    }

    private String superSimpleName(String internalName) {
        return internalName.substring(internalName.lastIndexOf('/') + 1);
    }

    private boolean isPrimitiveType(Type type) {
        return type instanceof Type.PrimitiveType pt && !"void".equals(pt.name());
    }


    private boolean needsErasureBoxing() {
        return target == Target.JVM;
    }

    private boolean isJvmTarget() {
        return target == Target.JVM;
    }

    private KofBinaryOp mapArithmeticOp(String op) {
        return switch (op) {
            case "+" -> KofBinaryOp.ADD;
            case "-" -> KofBinaryOp.SUB;
            case "*" -> KofBinaryOp.MUL;
            case "/" -> KofBinaryOp.DIV;
            case "%" -> KofBinaryOp.MOD;
            case "==" -> KofBinaryOp.EQ;
            case "!=" -> KofBinaryOp.NE;
            case "<" -> KofBinaryOp.LT;
            case "<=" -> KofBinaryOp.LE;
            case ">" -> KofBinaryOp.GT;
            case ">=" -> KofBinaryOp.GE;
            default -> KofBinaryOp.ADD;
        };
    }

    private boolean isNumeric(Type t) {
        if (!(t instanceof Type.PrimitiveType pt)) return false;
        String name = Type.canonicalPrimitiveName(pt.name());
        return switch (name) {
            case "int", "long", "float", "double", "byte", "short", "char" -> true;
            default -> false;
        };
    }

    private String primitiveName(Type t) {
        if (t instanceof Type.PrimitiveType pt) {
            return Type.canonicalPrimitiveName(pt.name());
        }
        return "";
    }

    private Type commonNumericType(Type a, Type b) {
        String an = primitiveName(a);
        String bn = primitiveName(b);
        if (an.equals("double") || an.equals("Double") || bn.equals("double") || bn.equals("Double")) {
            return Type.PrimitiveType.DOUBLE;
        }
        if (an.equals("float") || an.equals("Float") || bn.equals("float") || bn.equals("Float")) {
            return Type.PrimitiveType.FLOAT;
        }
        if (an.equals("long") || an.equals("Long") || bn.equals("long") || bn.equals("Long")) {
            return Type.PrimitiveType.LONG;
        }
        return a instanceof Type.PrimitiveType ? a : Type.PrimitiveType.INT;
    }

    private void emitWideningIfNeeded(List<KofOperation> ops, Type from, Type to) {
        if (from.equals(to)) return;
        String fn = primitiveName(from);
        String tn = primitiveName(to);
        KofUnaryOp conv = switch (tn) {
            case "long", "Long" -> switch (fn) {
                case "int", "Int", "char", "Char", "short", "Short", "byte", "Byte" -> KofUnaryOp.I2L;
                default -> null;
            };
            case "float", "Float" -> switch (fn) {
                case "int", "Int", "char", "Char", "short", "Short", "byte", "Byte" -> KofUnaryOp.I2F;
                case "long", "Long" -> KofUnaryOp.L2F;
                default -> null;
            };
            case "double", "Double" -> switch (fn) {
                case "int", "Int", "char", "Char", "short", "Short", "byte", "Byte" -> KofUnaryOp.I2D;
                case "long", "Long" -> KofUnaryOp.L2D;
                case "float", "Float" -> KofUnaryOp.F2D;
                default -> null;
            };
            default -> null;
        };
        if (conv != null) {
            ops.add(new KofUnary(conv, from));
        }
    }

    private static boolean isZeroLiteral(LiteralExpr lit) {
        if (lit.value() == null) return false;
        String v = lit.value().trim();
        boolean zero = "0".equals(v) || "-0".equals(v)
                || "0.0".equals(v) || "-0.0".equals(v) || "0.00".equals(v);
        return switch (lit.kind()) {
            case ConcreteLiteralKind.INT, ConcreteLiteralKind.LONG -> zero;
            case ConcreteLiteralKind.FLOAT, ConcreteLiteralKind.DOUBLE -> zero;
            default -> false;
        };
    }

    private Type boxedTypeFor(Type primitive) {
        if (primitive instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "int", "Int", "char", "Char" -> new Type.ClassType("java.lang", "Integer", List.of());
                case "long", "Long" -> new Type.ClassType("java.lang", "Long", List.of());
                case "float", "Float" -> new Type.ClassType("java.lang", "Float", List.of());
                case "double", "Double" -> new Type.ClassType("java.lang", "Double", List.of());
                case "boolean", "bool", "Bool" -> new Type.ClassType("java.lang", "Boolean", List.of());
                case "byte", "Byte" -> new Type.ClassType("java.lang", "Byte", List.of());
                case "short", "Short" -> new Type.ClassType("java.lang", "Short", List.of());
                default -> Type.UnknownType.UNKNOWN;
            };
        }
        return Type.UnknownType.UNKNOWN;
    }

    private void emitErasureBox(List<KofOperation> ops, Type primitive) {
        if (!needsErasureBoxing()) return;
        Type boxed = boxedTypeFor(primitive);
        Type boxParam = primitive instanceof Type.PrimitiveType pt
                && ("char".equals(pt.name()) || "Char".equals(pt.name())) ? Type.PrimitiveType.INT : primitive;
        ops.add(new KofCall(boxed, "kof_box", List.of(boxParam), boxed, KofCallKind.FUNCTION));
    }

    private void emitErasureUnbox(List<KofOperation> ops, Type primitive) {
        if (!needsErasureBoxing()) return;
        Type boxed = boxedTypeFor(primitive);
        ops.add(new KofCall(primitive, "kof_unbox", List.of(boxed), primitive, KofCallKind.FUNCTION));
    }

    private boolean erasesToReference(Type t) {
        return t instanceof Type.TypeVariable || t instanceof Type.ClassType
                || t instanceof Type.ArrayType || t instanceof Type.UnknownType;
    }

    private int emitArgumentsWithFormalTypes(List<ExpressionNode> args, List<Type> formalTypes,
                                             List<KofOperation> ops, String owner, int localIdx,
                                             List<IRLocalVariable> locals) {
        for (int i = 0; i < args.size(); i++) {
            Type formal = i < formalTypes.size() ? formalTypes.get(i) : null;
            // SAM conversion: lambda → interface funcional externa
            // (setOnClickListener(v -> ...) com OnClickListener no classpath)
            if (args.get(i) instanceof LambdaExpr le && formal instanceof Type.ClassType ct
                    && !ct.packageName().isEmpty() && externalClasspath != null
                    && externalClasspath.isInterface(ct.internalName())) {
                ExternalClasspath.Sam sam = externalClasspath.resolveSam(ct.internalName());
                if (sam != null) {
                    localIdx = emitSamAdapter(le, ct, sam, ops, owner, localIdx, locals);
                    continue;
                }
            }
            localIdx = emitExpression(args.get(i), ops, owner, localIdx, locals);
            Type argType = inferExprType(args.get(i), locals);
            if (formal != null && formal instanceof Type.PrimitiveType fpt
                    && argType instanceof Type.PrimitiveType apt
                    && !BuiltinTypes.isString(formal)) {
                emitWideningIfNeeded(ops, argType, formal);
            }
            if (formal != null && erasesToReference(formal) && isPrimitiveType(argType)
                    && !BuiltinTypes.isString(formal)) {
                emitErasureBox(ops, argType);
            }
            if (formal instanceof Type.PrimitiveType fp && "float".equals(fp.name())
                    && argType instanceof Type.PrimitiveType ap && "double".equals(ap.name())) {
                ops.add(new KofUnary(KofUnaryOp.D2F, Type.PrimitiveType.DOUBLE));
            }
        }
        return localIdx;
    }

    private final java.util.IdentityHashMap<LambdaExpr, String> samAdapterNames =
            new java.util.IdentityHashMap<>();

    /**
     * Gera (uma vez por lambda) a classe sintética que IMPLEMENTA a
     * interface externa: o método SAM contém o corpo da lambda e as
     * capturas viram campos finais + construtor — o mesmo modelo das
     * lambdas nativas. Emite NEW+DUP+capturas+&lt;init&gt; na pilha.
     */
    private int emitSamAdapter(LambdaExpr le, Type.ClassType iface, ExternalClasspath.Sam sam,
                               List<KofOperation> ops, String owner, int localIdx,
                               List<IRLocalVariable> locals) {
        List<IRLocalVariable> captures = collectCaptures(le, locals);
        if (lambdaUsesSuper(le) && currentLoweringOwner != null) {
            Type outerType = ownerTypeFromInternal(currentLoweringOwner);
            List<IRLocalVariable> eff = new ArrayList<>();
            eff.add(new IRLocalVariable(0, "$outer", outerType));
            eff.addAll(captures);
            captures = eff;
        }
        String className = samAdapterNames.computeIfAbsent(le,
                k -> "Sam" + iface.name().replace('.', '_') + "_" + (++lambdaCounter));
        if (lambdaUsesSuper(le) && currentLoweringOwner != null) {
            lambdaEnclosingOwner.put(className, currentLoweringOwner);
        }

        List<Type> samParamTypes = new ArrayList<>();
        for (String d : sam.signature().parameterDescriptors()) {
            samParamTypes.add(ExternalClasspath.typeFromDescriptor(d));
        }
        Type samReturn = ExternalClasspath.typeFromDescriptor(sam.signature().returnDescriptor());

        if (le.parameters().size() != samParamTypes.size() && currentDiagnostics != null) {
            SourcePosition p = le.position();
            currentDiagnostics.error(p != null ? p.file() : "",
                    p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                    "SAM mismatch: lambda has " + le.parameters().size()
                            + " parameter(s) but " + iface.name() + "." + sam.methodName()
                            + " needs " + samParamTypes.size(),
                    "SAM001");
        }

        if (!samAdapterNames.containsValue(className) || !syntheticExists(className)) {
            buildSyntheticAdapter(className, iface.internalName(), sam.methodName(),
                    samParamTypes, samReturn, le, captures);
        }

        Type adapterType = new Type.ClassType("", className, List.of());
        List<Type> captureTypes = new ArrayList<>();
        for (IRLocalVariable cap : captures) captureTypes.add(cap.type());
        ops.add(new KofNewObject(adapterType, captureTypes));
        ops.add(new KofDup());
        for (IRLocalVariable cap : captures) {
            ops.add(new KofLoadLocal(cap.type(), cap.index()));
        }
        ops.add(new KofCall(adapterType, "<init>", captureTypes,
                Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
        return localIdx;
    }

    private boolean syntheticExists(String name) {
        for (IRClass c : syntheticClasses) {
            if (c.name().equals(name)) return true;
        }
        return false;
    }

    /**
     * Corpo do adapter: mesmo esqueleto de lambdaClass, mas implementa a
     * interface externa e o método tem o nome/assinatura do SAM. Os params
     * da lambda são ligados POSICIONALMENTE aos params do SAM.
     */
    private void buildSyntheticAdapter(String className, String ifaceInternal, String samName,
                                       List<Type> samParamTypes, Type samReturnType,
                                       LambdaExpr le, List<IRLocalVariable> captures) {
        Type ownerType = new Type.ClassType("", className, List.of());
        List<FormalParameterNode> params = le.parameters();

        List<IRField> fields = new ArrayList<>();
        List<Type> captureTypes = new ArrayList<>();
        for (IRLocalVariable cap : captures) {
            fields.add(new IRField(cap.name(), cap.type(),
                    AccessFlags.PRIVATE | AccessFlags.FINAL, null));
            captureTypes.add(cap.type());
        }

        // invoke(): copia capturas pra locais e chama o método SAM real,
        // que contém o corpo da lambda
        List<KofOperation> ctorOps = new ArrayList<>();
        List<IRLocalVariable> ctorLocals = new ArrayList<>();
        ctorLocals.add(new IRLocalVariable(0, "this", ownerType));
        int cidx = 1;
        for (IRLocalVariable cap : captures) {
            ctorOps.add(new KofLoadLocal(ownerType, 0));
            ctorOps.add(new KofLoadLocal(cap.type(), cidx));
            ctorOps.add(new KofStoreField(ownerType, cap.name(), cap.type()));
            ctorLocals.add(new IRLocalVariable(cidx, cap.name(), cap.type()));
            cidx += isDoubleWidth(cap.type()) ? 2 : 1;
        }
        ctorOps.add(new KofReturnVoid());
        IRMethod ctor = new IRMethod("<init>", Type.PrimitiveType.VOID, captureTypes,
                AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, ctorOps)), ctorLocals);

        // método SAM: this + capturas nos primeiros slots + params do SAM
        List<KofOperation> bodyOps = new ArrayList<>();
        List<IRLocalVariable> bodyLocals = new ArrayList<>();
        bodyLocals.add(new IRLocalVariable(0, "this", ownerType));
        int bidx = 1;
        for (IRLocalVariable cap : captures) {
            bodyOps.add(new KofLoadLocal(ownerType, 0));
            bodyOps.add(new KofLoadField(ownerType, cap.name(), cap.type()));
            bodyOps.add(new KofStoreLocal(cap.type(), bidx));
            bodyLocals.add(new IRLocalVariable(bidx, cap.name(), cap.type()));
            bidx += isDoubleWidth(cap.type()) ? 2 : 1;
        }
        for (int i = 0; i < params.size() && i < samParamTypes.size(); i++) {
            bodyLocals.add(new IRLocalVariable(bidx, params.get(i).name(), samParamTypes.get(i)));
            bidx += isDoubleWidth(samParamTypes.get(i)) ? 2 : 1;
        }
        int localEnd = bidx;
        for (StatementNode stmt : le.body()) {
            localEnd = emitStatement(stmt, bodyOps, className, localEnd, bodyLocals, samReturnType);
        }
        KofOperation last = bodyOps.isEmpty() ? null : bodyOps.get(bodyOps.size() - 1);
        if (last == null || !(last instanceof KofReturn || last instanceof KofReturnVoid)) {
            if (Type.isVoid(samReturnType)) bodyOps.add(new KofReturnVoid());
            else bodyOps.add(new KofReturn(samReturnType));
        }
        IRMethod samMethod = new IRMethod(samName, samReturnType, samParamTypes,
                AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, bodyOps)), bodyLocals);

        IRClass cls = new IRClass(className, "java/lang/Object",
                List.of(ifaceInternal),
                AccessFlags.PUBLIC | AccessFlags.SUPER | AccessFlags.FINAL,
                fields, List.of(samMethod, ctor), List.of(), null, 300 + lambdaCounter);
        syntheticClasses.add(cls);
    }

    private record StringMethodSig(Type returnType, List<Type> parameterTypes) {}

    private record ObjectMethodSig(Type returnType, List<Type> parameterTypes) {}

    private ObjectMethodSig objectMethodSignature(String name, int argCount) {
        Type INT = Type.PrimitiveType.INT;
        Type BOOL = Type.PrimitiveType.BOOL;
        Type object = new Type.ClassType("java.lang", "Object", List.of());
        return switch (name) {
            case "hashCode" -> argCount == 0 ? new ObjectMethodSig(INT, List.of()) : null;
            case "toString" -> argCount == 0 ? new ObjectMethodSig(BuiltinTypes.STRING, List.of()) : null;
            case "equals" -> argCount == 1 ? new ObjectMethodSig(BOOL, List.of(object)) : null;
            case "getClass" -> argCount == 0 ? new ObjectMethodSig(
                    new Type.ClassType("java.lang", "Class", List.of()), List.of()) : null;
            default -> null;
        };
    }

    private StringMethodSig stringMethodSignature(String name, int argCount) {
        return stringMethodSignature(name, argCount, List.of());
    }

    private StringMethodSig stringMethodSignature(String name, int argCount, List<Type> argTypes) {
        Type str = BuiltinTypes.STRING;
        Type INT = Type.PrimitiveType.INT;
        Type BOOL = Type.PrimitiveType.BOOL;
        Type CHAR = Type.PrimitiveType.CHAR;
        Type charSeq = new Type.ClassType("java.lang", "CharSequence", List.of());
        Type object = new Type.ClassType("java.lang", "Object", List.of());
        Type strArray = new Type.ArrayType(BuiltinTypes.STRING);
        return switch (name) {
            case "length" -> argCount == 0 ? new StringMethodSig(INT, List.of()) : null;
            case "charAt" -> argCount == 1 ? new StringMethodSig(CHAR, List.of(INT)) : null;
            case "substring" -> argCount == 1 ? new StringMethodSig(str, List.of(INT))
                    : argCount == 2 ? new StringMethodSig(str, List.of(INT, INT)) : null;
            case "contains" -> argCount == 1 ? new StringMethodSig(BOOL, List.of(charSeq)) : null;
            case "startsWith" -> argCount == 1 ? new StringMethodSig(BOOL, List.of(str))
                    : argCount == 2 ? new StringMethodSig(BOOL, List.of(str, INT)) : null;
            case "endsWith" -> argCount == 1 ? new StringMethodSig(BOOL, List.of(str)) : null;
            case "equals" -> argCount == 1 ? new StringMethodSig(BOOL, List.of(object)) : null;
            case "equalsIgnoreCase" -> argCount == 1 ? new StringMethodSig(BOOL, List.of(str)) : null;
            case "indexOf" -> argCount == 1 ? new StringMethodSig(INT, List.of(str))
                    : argCount == 2 ? new StringMethodSig(INT, List.of(str, INT)) : null;
            case "concat" -> argCount == 1 ? new StringMethodSig(str, List.of(str)) : null;
            case "trim" -> argCount == 0 ? new StringMethodSig(str, List.of()) : null;
            case "toInt" -> argCount == 0 ? new StringMethodSig(INT, List.of()) : null;
            case "toLong" -> argCount == 0 ? new StringMethodSig(Type.PrimitiveType.LONG, List.of()) : null;
            case "toDouble" -> argCount == 0 ? new StringMethodSig(Type.PrimitiveType.DOUBLE, List.of()) : null;
            case "toFloat" -> argCount == 0 ? new StringMethodSig(Type.PrimitiveType.FLOAT, List.of()) : null;
            case "toUpperCase", "toLowerCase" -> argCount == 0 ? new StringMethodSig(str, List.of()) : null;
            case "replace" -> argCount == 2 ? replaceSignature(argTypes, str, CHAR, charSeq) : null;
            case "split" -> argCount == 1 ? new StringMethodSig(strArray, List.of(str))
                    : argCount == 2 ? new StringMethodSig(strArray, List.of(str, INT)) : null;
            default -> null;
        };
    }

    /**
     * String.replace(a, b): with two String arguments the call must target
     * Java's replace(CharSequence, CharSequence); with two characters (Kof
     * Ints) it targets replace(char, char). The overload is resolved by the
     * argument types — a previous version always picked (char, char), which
     * pushed Strings onto a (C, C) descriptor (VerifyError on the JVM).
     */
    /** Métodos do String implementados pelo runtime Kof (não existem no
     *  java.lang.String): as conversões numéricas. */
    private static String stringRuntimeMethod(String name) {
        return switch (name) {
            case "toInt" -> "kof_string_to_int";
            case "toLong" -> "kof_string_to_long";
            case "toDouble" -> "kof_string_to_double";
            case "toFloat" -> "kof_string_to_float";
            default -> null;
        };
    }

    private StringMethodSig replaceSignature(List<Type> argTypes, Type str, Type CHAR, Type charSeq) {
        boolean stringArgs = argTypes.size() == 2
                && BuiltinTypes.isString(argTypes.get(0)) && BuiltinTypes.isString(argTypes.get(1));
        return stringArgs
                ? new StringMethodSig(str, List.of(charSeq, charSeq))
                : new StringMethodSig(str, List.of(CHAR, CHAR));
    }

    private void boxPrimitive(List<KofOperation> ops, Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            String name = Type.canonicalPrimitiveName(pt.name());
            Type boxed = switch (name) {
                case "int" -> new Type.ClassType("java.lang", "Integer", List.of());
                case "long" -> new Type.ClassType("java.lang", "Long", List.of());
                case "float" -> new Type.ClassType("java.lang", "Float", List.of());
                case "double" -> new Type.ClassType("java.lang", "Double", List.of());
                case "bool" -> new Type.ClassType("java.lang", "Boolean", List.of());
                case "char" -> new Type.ClassType("java.lang", "Integer", List.of());
                case "byte" -> new Type.ClassType("java.lang", "Byte", List.of());
                case "short" -> new Type.ClassType("java.lang", "Short", List.of());
                default -> Type.UnknownType.UNKNOWN;
            };
            Type boxParam = "char".equals(name) ? Type.PrimitiveType.INT : type;
            ops.add(new KofCall(boxed, "valueOf", List.of(boxParam), boxed, KofCallKind.STATIC));
        }
    }

    private IRLocalVariable findLocalVar(String name, List<IRLocalVariable> locals) {
        for (int i = locals.size() - 1; i >= 0; i--) {
            if (locals.get(i).name().equals(name)) return locals.get(i);
        }
        return null;
    }

    /**
     * Namespace da stdlib (web/db/log/...) sombreado por variável local:
     * "var web = ..." torna "web.foo()" chamada de instância, não de namespace.
     */
    private boolean isLocalVarName(String name, List<IRLocalVariable> locals) {
        return findLocalVar(name, locals) != null;
    }

    private int findLocalIndex(String name, List<IRLocalVariable> locals) {
        for (int i = locals.size() - 1; i >= 0; i--) {
            if (locals.get(i).name().equals(name)) return locals.get(i).index();
        }
        return 0;
    }

    /**
     * Emits ++/-- on assignable targets (locals, fields, array elements) with
     * correct prefix/postfix semantics: the result value stays on the stack
     * and the target is stored back.
     */
    private int emitIncrement(UnaryExpr ue, Type operandType, List<KofOperation> ops,
                              String owner, int localIdx, List<IRLocalVariable> locals) {
        boolean prefix = ue.prefix();
        KofBinaryOp op = "++".equals(ue.operator()) ? KofBinaryOp.ADD : KofBinaryOp.SUB;
        ExpressionNode target = ue.operand();
        if (target instanceof IdentifierExpr ie) {
            IRLocalVariable var = findLocalVar(ie.name(), locals);
            if (var != null) {
                // local: [load v, (dup), 1, add, (dup), store v]
                ops.add(new KofLoadLocal(var.type(), var.index()));
                if (!prefix) ops.add(new KofDup());
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
                ops.add(new KofBinary(op, var.type()));
                if (prefix) ops.add(new KofDup());
                ops.add(new KofStoreLocal(var.type(), var.index()));
                return localIdx;
            }
            if (!owner.isEmpty() && semanticAnalyzer != null) {
                String className = owner.substring(owner.lastIndexOf('/') + 1);
                SymbolTable.Symbol fieldSym = resolveFieldInHierarchy(className, ie.name());
                if (fieldSym instanceof SymbolTable.FieldSymbol fs) {
                    Type ownerType = ownerTypeFromInternal(owner);
                    ops.add(new KofLoadLocal(ownerType, 0));
                    localIdx = emitFieldIncrement(ownerType, ie.name(), fs.type(), prefix, op,
                            ops, localIdx, locals);
                    return localIdx;
                }
            }
        }
        if (target instanceof FieldAccessExpr fa) {
            localIdx = emitExpression(fa.receiver(), ops, owner, localIdx, locals);
            Type recvType = inferExprType(fa.receiver(), locals);
            Type fieldType = Type.UnknownType.UNKNOWN;
            if (recvType instanceof Type.ClassType ct) {
                SymbolTable.Symbol fs = resolveFieldInHierarchy(ct.name(), fa.fieldName());
                if (fs != null) fieldType = fs.type();
            }
            localIdx = emitFieldIncrement(recvType, fa.fieldName(), fieldType, prefix, op,
                    ops, localIdx, locals);
            return localIdx;
        }
        if (target instanceof ArrayAccessExpr aa) {
            localIdx = emitExpression(aa.receiver(), ops, owner, localIdx, locals);
            Type recvType = inferExprType(aa.receiver(), locals);
            Type elemType = Type.arrayElementType(recvType);
            int arrTmp = localIdx++;
            int idxTmp = localIdx++;
            int valTmp = localIdx++;
            locals.add(new IRLocalVariable(arrTmp, "#arr", recvType));
            locals.add(new IRLocalVariable(idxTmp, "#idx", Type.PrimitiveType.INT));
            locals.add(new IRLocalVariable(valTmp, "#val", elemType));
            ops.add(new KofStoreLocal(recvType, arrTmp));
            localIdx = emitExpression(aa.index(), ops, owner, localIdx, locals);
            ops.add(new KofStoreLocal(Type.PrimitiveType.INT, idxTmp));
            ops.add(new KofLoadLocal(recvType, arrTmp));
            ops.add(new KofLoadLocal(Type.PrimitiveType.INT, idxTmp));
            ops.add(new KofArrayLoad(elemType));
            ops.add(new KofStoreLocal(elemType, valTmp));
            ops.add(new KofLoadLocal(elemType, valTmp));
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
            ops.add(new KofBinary(op, elemType));
            if (prefix) {
                // [array, index, new] -> [new, array, index, new]
                ops.add(new KofDupX2());
                ops.add(new KofArrayStore(elemType));
            } else {
                ops.add(new KofArrayStore(elemType));
                ops.add(new KofLoadLocal(elemType, valTmp));
            }
            return localIdx;
        }
        // non-assignable operand: evaluate as expression (legacy behavior)
        localIdx = emitExpression(ue.operand(), ops, owner, localIdx, locals);
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
        ops.add(new KofBinary(op, operandType));
        return localIdx;
    }

    /**
     * Field increment: the receiver must survive the field read for the store.
     * Postfix needs a temp for the previous value (JVM putfield consumes the
     * top two slots as value+receiver).
     */
    private int emitFieldIncrement(Type ownerType, String fieldName, Type fieldType,
                                   boolean prefix, KofBinaryOp op,
                                   List<KofOperation> ops, int localIdx,
                                   List<IRLocalVariable> locals) {
        int recvTmp = localIdx++;
        int valTmp = localIdx++;
        int newTmp = localIdx++;
        locals.add(new IRLocalVariable(recvTmp, "#recv", ownerType));
        locals.add(new IRLocalVariable(valTmp, "#inc", fieldType));
        locals.add(new IRLocalVariable(newTmp, "#new", fieldType));
        ops.add(new KofStoreLocal(ownerType, recvTmp));
        ops.add(new KofLoadLocal(ownerType, recvTmp));
        ops.add(new KofLoadField(ownerType, fieldName, fieldType));
        ops.add(new KofStoreLocal(fieldType, valTmp));
        ops.add(new KofLoadLocal(fieldType, valTmp));
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
        ops.add(new KofBinary(op, fieldType));
        ops.add(new KofStoreLocal(fieldType, newTmp));
        ops.add(new KofLoadLocal(ownerType, recvTmp));
        ops.add(new KofLoadLocal(fieldType, newTmp));
        ops.add(new KofStoreField(ownerType, fieldName, fieldType));
        ops.add(new KofLoadLocal(fieldType, prefix ? newTmp : valTmp));
        return localIdx;
    }

    private int emitPackedColor(List<ExpressionNode> args, List<KofOperation> ops,
                               String owner, int localIdx, List<IRLocalVariable> locals) {
        for (int i = 0; i < args.size(); i++) {
            localIdx = emitExpression(args.get(i), ops, owner, localIdx, locals);
            if (i == 0) {
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 24));
                ops.add(new KofBinary(KofBinaryOp.SHL, Type.PrimitiveType.INT));
            } else if (i == 1) {
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 16));
                ops.add(new KofBinary(KofBinaryOp.SHL, Type.PrimitiveType.INT));
            } else if (i == 2) {
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 8));
                ops.add(new KofBinary(KofBinaryOp.SHL, Type.PrimitiveType.INT));
            }
            if (i < 3 && i > 0) {
                ops.add(new KofBinary(KofBinaryOp.OR, Type.PrimitiveType.INT));
            }
            if (i == 3) {
                ops.add(new KofBinary(KofBinaryOp.OR, Type.PrimitiveType.INT));
            }
        }
        if (args.size() == 3) {
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0xFF));
            ops.add(new KofBinary(KofBinaryOp.OR, Type.PrimitiveType.INT));
        }
        return localIdx;
    }

    private int emitUiInstance(Type recvType, MethodCallExpr mc, List<KofOperation> ops,
                               String owner, int localIdx, List<IRLocalVariable> locals) {
        if (KofUi.isWindow(recvType) || KofUi.isLabel(recvType) || KofUi.isButton(recvType)
                || KofUi.isInput(recvType) || KofUi.isView(recvType)
                || KofUi.isLink(recvType) || KofUi.isImage(recvType) || KofUi.isIcon(recvType)) {
            KofUi.UiCall uiCall = KofUi.instanceMethod(recvType, mc.methodName(), mc.arguments().size());
            if (uiCall != null) {
                for (ExpressionNode arg : mc.arguments()) {
                    localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                }
                List<Type> uiParams = new ArrayList<>();
                uiParams.add(Type.PrimitiveType.INT);
                uiParams.addAll(uiCall.parameterTypes());
                ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                        uiCall.function(), uiParams, uiCall.returnType(), KofCallKind.FUNCTION));
                return localIdx;
            }
            return localIdx;
        }
        if (KofUi.isColor(recvType)) {
            switch (mc.methodName()) {
                case "red" -> {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 24));
                    ops.add(new KofBinary(KofBinaryOp.USHR, Type.PrimitiveType.INT));
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0xFF));
                    ops.add(new KofBinary(KofBinaryOp.AND, Type.PrimitiveType.INT));
                    return localIdx;
                }
                case "green" -> {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 16));
                    ops.add(new KofBinary(KofBinaryOp.USHR, Type.PrimitiveType.INT));
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0xFF));
                    ops.add(new KofBinary(KofBinaryOp.AND, Type.PrimitiveType.INT));
                    return localIdx;
                }
                case "blue" -> {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 8));
                    ops.add(new KofBinary(KofBinaryOp.USHR, Type.PrimitiveType.INT));
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0xFF));
                    ops.add(new KofBinary(KofBinaryOp.AND, Type.PrimitiveType.INT));
                    return localIdx;
                }
                case "alpha" -> {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0xFF));
                    ops.add(new KofBinary(KofBinaryOp.AND, Type.PrimitiveType.INT));
                    return localIdx;
                }
                case "isOpaque" -> {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0xFF));
                    ops.add(new KofBinary(KofBinaryOp.AND, Type.PrimitiveType.INT));
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0xFF));
                    ops.add(new KofBinary(KofBinaryOp.EQ, Type.PrimitiveType.INT));
                    return localIdx;
                }
                case "withAlpha" -> {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0xFFFFFF00));
                    ops.add(new KofBinary(KofBinaryOp.AND, Type.PrimitiveType.INT));
                    for (ExpressionNode arg : mc.arguments()) {
                        localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                    }
                    ops.add(new KofBinary(KofBinaryOp.OR, Type.PrimitiveType.INT));
                    return localIdx;
                }
                case "toCss" -> {
                    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                            "kof_ui_color_to_css", List.of(Type.PrimitiveType.INT),
                            BuiltinTypes.STRING, KofCallKind.FUNCTION));
                    return localIdx;
                }
                default -> {
                    return localIdx;
                }
            }
        }
        if (KofUi.isTheme(recvType)) {
            switch (mc.methodName()) {
                case "background", "surface", "primary", "secondary", "text", "error" -> {
                    int tagTmp = localIdx++;
                    locals.add(new IRLocalVariable(tagTmp, "#theme", Type.PrimitiveType.INT));
                    ops.add(new KofStoreLocal(Type.PrimitiveType.INT, tagTmp));
                    ops.add(new KofLoadLocal(Type.PrimitiveType.INT, tagTmp));
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
                    LabelId darkLabel = LabelId.create();
                    LabelId lightLabel = LabelId.create();
                    LabelId endLabel = LabelId.create();
                    ops.add(new KofConditionalJump(KofComparison.NE, lightLabel, darkLabel));
                    ops.add(new KofLabel(lightLabel));
                    Integer light = KofUi.themeColor(mc.methodName(), 0);
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, light));
                    ops.add(new KofJump(endLabel));
                    ops.add(new KofLabel(darkLabel));
                    Integer dark = KofUi.themeColor(mc.methodName(), 1);
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, dark));
                    ops.add(new KofLabel(endLabel));
                    return localIdx;
                }
                case "isDark" -> {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
                    ops.add(new KofBinary(KofBinaryOp.EQ, Type.PrimitiveType.INT));
                    return localIdx;
                }
                default -> {
                    return localIdx;
                }
            }
        }
        return localIdx;
    }

    private boolean isComparisonOp(String op) {
        return ">".equals(op) || "<".equals(op) || ">=".equals(op) || "<=".equals(op) || "==".equals(op) || "!=".equals(op);
    }

    /**
     * FLT001: no Native, float/double ainda não têm aritmética SSE nem
     * formatação real (os bits vivem na pilha como inteiros). Operações de
     * ponto flutuante viram diagnóstico em compile-time — nunca resultado
     * silenciosamente errado. JSON já tem o próprio código (JSN001).
     */
    private boolean fpSupportedOnNative(Type type, SourcePosition pos) {
        if (target != Target.NATIVE || type == null) return true;
        if (!(type instanceof Type.PrimitiveType pt)) return true;
        String name = pt.name();
        if ("float".equals(name) || "double".equals(name)) {
            if (currentDiagnostics != null) {
                currentDiagnostics.error(pos != null ? pos.file() : "",
                        pos != null ? pos.line() : 0,
                        pos != null ? pos.column() : 0,
                        0,
                        "floating-point arithmetic/printing is not supported on the Native target yet"
                                + " (use Int/Long for now)",
                        "FLT001");
            }
            return false;
        }
        return true;
    }

    private static boolean isFloatingPoint(Type type) {
        return type instanceof Type.PrimitiveType pt
                && ("float".equals(pt.name()) || "double".equals(pt.name()));
    }

    private int jsonListTag(Type elemType) {
        if (BuiltinTypes.isString(elemType)) return 1;
        if (elemType instanceof Type.PrimitiveType pt && "bool".equals(pt.name())) return 2;
        return 0;
    }

    private boolean jsonSupported(Type type, boolean isDecode) {
        Type check = BuiltinTypes.isList(type) ? listElementType(type) : type;
        if (check instanceof Type.PrimitiveType pt && ("float".equals(pt.name()) || "double".equals(pt.name()))) {
            if (target == Target.NATIVE) {
                if (currentDiagnostics != null) {
                    currentDiagnostics.error("", 0, 0, 0,
                            "json: Float/Double is not supported on the Native target yet (use int, long, bool or String)",
                            "JSN001");
                }
                return false;
            }
            return true;
        }
        if (isDecode && type instanceof Type.ArrayType at) {
            // JSN003 fechado: int/long/bool/string[] tem decoders nativos.
            if (!(at.componentType() instanceof Type.PrimitiveType ap
                    && ("float".equals(ap.name()) || "double".equals(ap.name())))) {
                return true;
            }
            // arrays de float/double permanecem sob o gap FP
            if (target == Target.NATIVE) {
                if (currentDiagnostics != null) {
                    currentDiagnostics.error("", 0, 0, 0,
                            "json.decode: Float/Double arrays are not supported on the Native target yet",
                            "JSN001");
                }
                return false;
            }
            return true;
        }
        if (check instanceof Type.ClassType && target == Target.NATIVE && !BuiltinTypes.isList(type)
                && !BuiltinTypes.isString(type)) {
            // JSN002 fechado para classes cujos campos sao todos suportados
            // pelo walker nativo (primitivos, string e objetos aninhados).
            String cn = check instanceof Type.ClassType ct
                    ? (ct.packageName().isEmpty() ? ct.name()
                      : ct.packageName() + "." + ct.name())
                    : "";
            if (!nativeObjJsonFieldsOk(cn, new java.util.HashSet<>(), null)) {
                return false;
            }
        }
        return true;
    }

    /**
     * JSN002: valida recursivamente que toda instancia tem layout
     * conhecido em compile-time e campos suportados pelo walker nativo.
     * Qualquer campo fora do conjunto (List, Map, float/double) diagnostica
     * explicitamente — nunca resultado silenciosamente errado.
     */
    /**
     * Campos ordenados (nome, tipo) de uma classe/record/entity declarada
     * na unidade corrente — usados pela composicao JSON no Native.
     */
    private java.util.List<String[]> classFieldsOrdered(String className) {
        java.util.List<String[]> out = new ArrayList<>();
        for (AstNode d : currentUnit.declarations()) {
            if (d instanceof RecordDeclarationNode r && r.name().equals(className)) {
                for (RecordComponentNode f : r.components()) {
                    out.add(new String[]{f.name(), f.type()});
                }
            } else if (d instanceof EntityDeclarationNode e && e.name().equals(className)) {
                for (EntityFieldNode f : e.fields()) {
                    out.add(new String[]{f.name(), f.type()});
                }
            } else if (d instanceof ClassDeclarationNode c && c.name().equals(className)) {
                for (AstNode m : c.members()) {
                    if (m instanceof FieldDeclarationNode f) {
                        out.add(new String[]{f.name(), f.type()});
                    }
                }
            }
        }
        return out;
    }

    private boolean nativeObjJsonFieldsOk(String className, java.util.Set<String> visiting,
                                          String ownerForDiag) {
        if (visiting.contains(className)) return true; // ciclo: aceita no nivel externo
        visiting.add(className);
        boolean ok = true;
        for (String[] f : classFieldsOrdered(className)) {
            if (!fieldOk(f[1], className, visiting)) ok = false;
        }
        return ok;
    }

    // v1 flat: objetos aninhados ainda nao sao suportados pelo walker
    private boolean fieldOk(String typeName, String className, java.util.Set<String> visiting) {
        Type t = toType(typeName);
        if (t instanceof Type.PrimitiveType) return true;
        if (BuiltinTypes.isString(t)) return true;
        if (currentDiagnostics != null) {
            currentDiagnostics.error("", 0, 0, 0,
                    "json: class " + className + " has field of type " + typeName
                            + " not supported by the Native JSON encoder yet"
                            + " (use int, long, bool or string fields; nested objects coming soon)",
                    "JSN002");
        }
        return false;
    }

    private String jsonEncodeFunction(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "int", "char", "byte", "short" -> "kof_json_encode_int";
                case "long" -> "kof_json_encode_long";
                case "bool" -> "kof_json_encode_bool";
                case "float" -> "kof_json_encode_float";
                case "double" -> "kof_json_encode_double";
                default -> "kof_json_encode_int";
            };
        }
        if (BuiltinTypes.isString(type)) return "kof_json_encode_string";
        if (BuiltinTypes.isList(type)) return "kof_json_encode_list";
        if (type instanceof Type.ArrayType) return "kof_json_encode_array";
        return "kof_json_encode";
    }

    private String jsonDecodeFunction(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "int", "char", "byte", "short" -> "kof_json_decode_int";
                case "long" -> "kof_json_decode_long";
                case "bool" -> "kof_json_decode_bool";
                case "float" -> "kof_json_decode_float";
                case "double" -> "kof_json_decode_double";
                default -> "kof_json_decode_int";
            };
        }
        if (type instanceof Type.ArrayType at) {
            if (at.componentType() instanceof Type.PrimitiveType ap) {
                return switch (ap.name()) {
                    case "int", "char", "byte", "short" -> "kof_json_decode_int_array";
                    case "bool" -> "kof_json_decode_bool_array";
                    case "long" -> "kof_json_decode_long_array";
                    case "double", "float" -> "kof_json_decode_double_array";
                    default -> "kof_json_decode_int_array";
                };
            }
            if (BuiltinTypes.isString(at.componentType())) return "kof_json_decode_string_array";
            return "kof_json_decode_string_array";
        }
        if (BuiltinTypes.isString(type)) return "kof_json_decode_string";
        if (BuiltinTypes.isList(type)) {
            Type elem = listElementType(type);
            if (elem instanceof Type.PrimitiveType ep && "int".equals(ep.name())) return "kof_json_decode_int_list";
            if (BuiltinTypes.isString(elem)) return "kof_json_decode_string_list";
            return "kof_json_decode_list";
        }
        if (type instanceof Type.ClassType ct) return "kof_json_decode_" + sanitize(ct.name());
        return "kof_json_decode_string";
    }

    private String sanitize(String name) {
        return name.replace(".", "_").replace("/", "_").replace("-", "_");
    }

    private Type listElementType(Type listType) {
        if (listType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) {
            return ct.typeArguments().get(0);
        }
        return Type.UnknownType.UNKNOWN;
    }

    private Type substituteTypeVariable(String tvName, Type recvType) {
        if (!(recvType instanceof Type.ClassType ct) || ct.typeArguments().isEmpty()) return null;
        if (currentUnit != null) {
            for (AstNode d : currentUnit.declarations()) {
                if (d instanceof ClassDeclarationNode cls && cls.name().equals(ct.name())) {
                    for (int i = 0; i < cls.typeParameters().size(); i++) {
                        if (i < ct.typeArguments().size() && cls.typeParameters().get(i).equals(tvName)) {
                            return ct.typeArguments().get(i);
                        }
                    }
                }
            }
        }
        return null;
    }

    private KofLoadLiteral defaultValueOp(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return switch (Type.canonicalPrimitiveName(pt.name())) {
                case "long" -> new KofLoadLiteral(Type.PrimitiveType.LONG, 0L);
                case "float" -> new KofLoadLiteral(Type.PrimitiveType.FLOAT, 0.0f);
                case "double" -> new KofLoadLiteral(Type.PrimitiveType.DOUBLE, 0.0d);
                default -> new KofLoadLiteral(Type.PrimitiveType.INT, 0);
            };
        }
        return new KofLoadLiteral(type, null);
    }

    private boolean isComparisonShortcut(BinaryExpr bin, List<IRLocalVariable> locals) {
        if (!isComparisonOp(bin.operator())) return false;
        if ("==".equals(bin.operator()) || "!=".equals(bin.operator())) {
            Type left = inferExprType(bin.left(), locals);
            Type right = inferExprType(bin.right(), locals);
            if (Type.isString(left) || Type.isString(right)) return false;
            // enum == enum compara conteúdo (string) — nunca identidade
            if (isEnumType(left) || isEnumType(right)) return false;
            // primitivo vs null → constante (caminho da cadeia binária)
            boolean leftNull = bin.left() instanceof LiteralExpr ll2 && ll2.kind() == ConcreteLiteralKind.NULL;
            boolean rightNull = bin.right() instanceof LiteralExpr rl2 && rl2.kind() == ConcreteLiteralKind.NULL;
            if ((leftNull && isPrimitiveType(right)) || (rightNull && isPrimitiveType(left))) return false;
        }
        return true;
    }

    /**
     * Operand type of a comparison shortcut: the common numeric type of the
     * two operands (int, long, float or double). The IR carries it so the
     * JVM backend can emit the correct compare instruction.
     */
    private Type comparisonOperandType(BinaryExpr bin, List<IRLocalVariable> locals) {
        Type left = inferExprType(bin.left(), locals);
        Type right = inferExprType(bin.right(), locals);
        if (isNumeric(left) && isNumeric(right)) {
            return commonNumericType(left, right);
        }
        return Type.PrimitiveType.INT;
    }

    /**
     * Emits both operands of a comparison-shortcut condition, widening each
     * to the common numeric type (e.g. `longExpr < 2000` must widen the
     * literal before the compare).
     */
    private int emitComparisonShortcut(BinaryExpr bin, List<KofOperation> ops, String owner,
                                       int localIdx, List<IRLocalVariable> locals) {
        Type common = comparisonOperandType(bin, locals);
        if (!fpSupportedOnNative(common, bin.position())) {
            return localIdx;
        }
        localIdx = emitExpression(bin.left(), ops, owner, localIdx, locals);
        emitWideningIfNeeded(ops, inferExprType(bin.left(), locals), common);
        localIdx = emitExpression(bin.right(), ops, owner, localIdx, locals);
        emitWideningIfNeeded(ops, inferExprType(bin.right(), locals), common);
        return localIdx;
    }

    private KofComparison mapComparison(String op) {
        return switch (op) {
            case ">" -> KofComparison.GT;
            case "<" -> KofComparison.LT;
            case ">=" -> KofComparison.GE;
            case "<=" -> KofComparison.LE;
            case "==" -> KofComparison.EQ;
            case "!=" -> KofComparison.NE;
            default -> KofComparison.NE;
        };
    }

    private KofComparison invertComparison(String op) {
        return switch (op) {
            case ">" -> KofComparison.LE;
            case "<" -> KofComparison.GE;
            case ">=" -> KofComparison.LT;
            case "<=" -> KofComparison.GT;
            case "==" -> KofComparison.NE;
            case "!=" -> KofComparison.EQ;
            default -> KofComparison.NE;
        };
    }

    private boolean hasReturnValue(ExpressionNode expr, List<IRLocalVariable> locals) {
        if (expr instanceof AssignmentExpr) return false;
        if (expr instanceof MethodCallExpr mc) {
            if ("print".equals(mc.methodName()) || "println".equals(mc.methodName())) return false;
            if (mc.receiver() != null && KofIo.instanceMethod(Type.UnknownType.UNKNOWN,
                    mc.methodName(), mc.arguments().size()) != null) {
                return true;
            }
            // List methods that leave a value on the stack (get, remove,
            // size, contains, isEmpty) must be popped at statement level;
            // add/set/clear are already popped by the JVM backend.
            if (mc.receiver() != null && BuiltinTypes.isList(inferExprType(mc.receiver(), locals))) {
                return switch (mc.methodName()) {
                    case "get", "remove", "size", "length", "count",
                            "contains", "isEmpty" -> true;
                    default -> false;
                };
            }
            if (mc.receiver() != null && BuiltinTypes.isMap(inferExprType(mc.receiver(), locals))) {
                return switch (mc.methodName()) {
                    case "get", "remove", "put", "size", "length", "count",
                            "contains", "containsKey", "isEmpty", "keys", "values" -> true;
                    default -> false;
                };
            }
            if (mc.receiver() != null && BuiltinTypes.isSet(inferExprType(mc.receiver(), locals))) {
                return switch (mc.methodName()) {
                    case "contains", "isEmpty", "size", "length", "count",
                            "add", "remove" -> true;
                    default -> false;
                };
            }
            if (mc.receiver() instanceof IdentifierExpr rid && KofOrm.isOrmNamespace(rid.name())) {
                // todos os orm.* retornam valor (Bool/Object/List/Long) — antes
                // dos checks genéricos (o "delete" também é rota do web)
                return true;
            }
            if (mc.receiver() != null) {
                List<Type> webArgTypes = new ArrayList<>();
                for (ExpressionNode arg : mc.arguments()) webArgTypes.add(inferExprType(arg, List.of()));
                KofWeb.WebCall webCall = KofWeb.instanceMethod(mc.methodName(), webArgTypes);
                if (webCall != null) {
                    return !(webCall.returnType() instanceof Type.PrimitiveType pt && "void".equals(pt.name()));
                }
            }
            if (mc.receiver() instanceof IdentifierExpr rid && KofIo.isConstructor(rid.name())
                    && KofIo.staticMethod(rid.name(), mc.methodName(), mc.arguments().size()) != null) {
                return true;
            }
            if (semanticAnalyzer != null) {
                SymbolTable.MethodSymbol resolved = semanticAnalyzer.getResolvedMethod(mc);
                if (resolved != null) {
                    Type resolvedType = resolved.returnType();
                    if (Type.isVoid(resolvedType)) return false;
                    return !(resolvedType instanceof Type.UnknownType);
                }
            }
            Type t = inferExprType(mc, locals);
            if (t instanceof Type.UnknownType || Type.isVoid(t)) return false;
            return true;
        }
        return true;
    }

    private IRClass lowerClass(ClassDeclarationNode cls, String packageName, int typeId) {
        String internalName = toInternalName(packageName, cls.name());
        // usa o superClass QUALIFICADO pelo analyzer ("extends Activity" +
        // import → android/app/Activity); cai pro cru se analyzer ausente
        String superName = null;
        if (semanticAnalyzer != null) {
            SymbolTable.ClassSymbol sym = semanticAnalyzer.getClass(cls.name());
            if (sym != null && sym.superClass() != null && !"Object".equals(sym.superClass())) {
                superName = toInternalName("", sym.superClass());
            }
        }
        if (superName == null) {
            superName = cls.superClass() != null ? toInternalName("", cls.superClass())
                    : "java/lang/Object";
        }
        List<String> ifaces = cls.interfaces().stream().map(this::externalOrLocalInternalName).toList();
        int access = computeAccess(cls.modifiers());
        List<IRField> fields = new ArrayList<>();
        List<IRMethod> methods = new ArrayList<>();
        java.util.Map<String, ExpressionNode> fieldInits = new java.util.LinkedHashMap<>();
        for (AstNode member : cls.members()) {
            if (member instanceof FieldDeclarationNode field) {
                IRField irField = lowerField(field, cls.typeParameters());
                fields.add(irField);
                if (field.initializer() != null && irField.initialValue() == null) {
                    fieldInits.put(field.name(), field.initializer());
                }
            } else if (member instanceof MethodDeclarationNode method) {
                methods.add(lowerMethod(method, internalName, false, cls.typeParameters()));
            } else if (member instanceof ConstructorDeclarationNode ctor) {
                methods.add(lowerConstructor(ctor, internalName, superName, cls.typeParameters(), fields, fieldInits));
                methods.addAll(lowerConstructorDefaults(ctor, internalName, superName,
                        cls.typeParameters(), fields, fieldInits));
            }
        }
        if (!methods.stream().anyMatch(m -> m.name().equals("<init>"))) {
            methods.add(0, generateDefaultConstructor(internalName, superName, fields, fieldInits));
        }
        return new IRClass(internalName, superName, ifaces, access, fields, methods, List.of(), null,
                typeId, lowerAnnotations(cls.annotations()));
    }

    private IRClass lowerInterface(InterfaceDeclarationNode iface, String packageName, int typeId) {
        String internalName = toInternalName(packageName, iface.name());
        List<String> ifaces = iface.interfaces().stream().map(this::externalOrLocalInternalName).toList();
        int access = computeAccess(iface.modifiers()) | AccessFlags.ABSTRACT | AccessFlags.INTERFACE;
        List<IRMethod> methods = new ArrayList<>();
        List<IRField> fields = new ArrayList<>();
        for (AstNode member : iface.members()) {
            if (member instanceof MethodDeclarationNode method) methods.add(lowerMethod(method, internalName, true, List.of()));
            else if (member instanceof FieldDeclarationNode field) fields.add(lowerField(field, List.of()));
        }
        return new IRClass(internalName, "java/lang/Object", ifaces, access, fields, methods, List.of(), null,
                typeId, lowerAnnotations(iface.annotations()));
    }

    private IRClass lowerRecord(RecordDeclarationNode rec, String packageName, int typeId) {
        String internalName = toInternalName(packageName, rec.name());
        String superName = "java/lang/Record";
        List<String> ifaces = rec.interfaces().stream().map(this::externalOrLocalInternalName).toList();
        int access = computeAccess(rec.modifiers()) | AccessFlags.FINAL | AccessFlags.PUBLIC;
        List<IRField> fields = new ArrayList<>();
        List<IRMethod> methods = new ArrayList<>();
        for (RecordComponentNode comp : rec.components()) {
            fields.add(new IRField(comp.name(), toType(comp.type()), AccessFlags.PRIVATE | AccessFlags.FINAL,
                    null, lowerAnnotations(comp.annotations())));
        }
        methods.add(0, generateRecordConstructor(rec, internalName));
        methods.addAll(generateRecordDefaultOverloads(rec, internalName));
        Type ownerType = ownerTypeFromInternal(internalName);
        for (RecordComponentNode comp : rec.components()) {
            Type compType = toType(comp.type());
            List<KofOperation> body = new ArrayList<>();
            body.add(new KofLoadLocal(ownerType, 0));
            body.add(new KofLoadField(ownerType, comp.name(), compType));
            body.add(new KofReturn(compType));
            methods.add(new IRMethod(comp.name(), compType, List.of(), AccessFlags.PUBLIC, List.of(),
                    List.of(new IRBasicBlock(0, body)),
                    List.of(new IRLocalVariable(0, "this", ownerType))));
        }
        for (AstNode member : rec.members()) {
            if (member instanceof MethodDeclarationNode method) {
                methods.add(lowerMethod(method, internalName, false, List.of()));
            } else if (member instanceof ConstructorDeclarationNode ctor) {
                methods.add(lowerConstructor(ctor, internalName, "java/lang/Record",
                        List.of(), fields, java.util.Map.of()));
            }
        }
        return new IRClass(internalName, superName, ifaces, access, fields, methods, List.of(), null,
                typeId, lowerAnnotations(rec.annotations()));
    }

    private Type resolveWithTypeParams(String typeName, List<String> typeParams) {
        if (typeParams.contains(typeName)) return new Type.TypeVariable(typeName);
        return toType(typeName);
    }

    private IRField lowerField(FieldDeclarationNode field, List<String> typeParams) {
        Type fieldType = resolveWithTypeParams(field.type(), typeParams);
        Object initVal = null;
        if (field.initializer() instanceof LiteralExpr lit) {
            initVal = switch (lit.kind()) {
                case ConcreteLiteralKind.INT -> parseIntLiteral(lit.value());
                case ConcreteLiteralKind.LONG -> Long.parseLong(stripSuffix(lit.value()));
                case ConcreteLiteralKind.FLOAT -> Float.parseFloat(stripSuffix(lit.value()));
                case ConcreteLiteralKind.DOUBLE -> Double.parseDouble(stripSuffix(lit.value()));
                case ConcreteLiteralKind.STRING -> lit.value();
                case ConcreteLiteralKind.BOOLEAN -> Boolean.parseBoolean(lit.value()) ? 1 : 0;
                default -> null;
            };
        }
        return new IRField(field.name(), fieldType, computeAccess(field.modifiers()), initVal,
                lowerAnnotations(field.annotations()));
    }

    /**
     * Converte annotations do AST para a IR: nome resolvido para o formato
     * interno JVM e valores já constantes (o parser só aceita literais).
     */
    private List<IRAnnotation> lowerAnnotations(List<AnnotationNode> annos) {
        if (annos == null || annos.isEmpty()) return List.of();
        List<IRAnnotation> out = new ArrayList<>();
        for (AnnotationNode anno : annos) {
            java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
            for (AnnotationPair pair : anno.pairs()) {
                String key = pair.key() != null ? pair.key()
                        : (anno.pairs().size() == 1 ? "value" : null);
                if (key != null) {
                    Object folded = foldAnnotationValue(pair.value());
                    if (!(folded instanceof ParseSentinel)) values.put(key, folded);
                }
            }
            out.add(new IRAnnotation(resolveAnnotationInternalName(anno.name()), values));
        }
        return out;
    }

    /**
     * Nome interno JVM de uma interface declarada: simples vinda de import
     * ("import android.view.OnClickListener") qualifica; senão, classe local.
     */
    private String externalOrLocalInternalName(String name) {
        Type q = qualifyViaImports(name);
        if (q instanceof Type.ClassType qt && !qt.packageName().isEmpty()) {
            return qt.internalName();
        }
        return toInternalName("", name);
    }

    /**
     * Dobra valores de annotation: refs de Classe.class e Enum.CONST viram
     * constantes resolvidas; enum só passa se o classpath provar a classe.
     */
    private Object foldAnnotationValue(Object value) {
        if (value instanceof AnnotationClassRef ref) {
            return new IRClassConstant(resolveAnnotationInternalName(ref.typeName()));
        }
        if (value instanceof AnnotationEnumRef ref) {
            int lastDot = ref.qualifiedConstant().lastIndexOf('.');
            if (lastDot > 0 && externalClasspath != null) {
                String internal = resolveAnnotationInternalName(
                        ref.qualifiedConstant().substring(0, lastDot));
                String constant = ref.qualifiedConstant().substring(lastDot + 1);
                if (externalClasspath.knows(internal)
                        && externalClasspath.isEnum(internal)
                        && externalClasspath.hasEnumConstant(internal, constant)) {
                    return new IREnumConstant(internal, constant);
                }
            }
            if (currentDiagnostics != null) {
                currentDiagnostics.error("", 0, 0, 0,
                        "enum constant '" + ref.qualifiedConstant()
                                + "' could not be resolved from the external classpath",
                        "ANNOT001");
            }
            return ParseSentinel.INSTANCE;
        }
        if (value instanceof List<?> items) {
            List<Object> folded = new ArrayList<>();
            for (Object item : items) folded.add(foldAnnotationValue(item));
            return folded;
        }
        return value;
    }

    private static final class ParseSentinel {
        static final ParseSentinel INSTANCE = new ParseSentinel();
    }

    /**
     * Resolve o nome da annotation para o formato interno JVM. Nomes
     * qualificados vão direto; simples usam imports do arquivo; os de
     * java.lang são embutidos; senão assume-se a própria classe local.
     */
    private String resolveAnnotationInternalName(String name) {
        if (name.contains(".")) return name.replace('.', '/');
        switch (name) {
            case "Override": return "java/lang/Override";
            case "Deprecated": return "java/lang/Deprecated";
            case "FunctionalInterface": return "java/lang/FunctionalInterface";
            case "SafeVarargs": return "java/lang/SafeVarargs";
            case "SuppressWarnings": return "java/lang/SuppressWarnings";
        }
        if (currentUnit != null) {
            for (String imp : currentUnit.imports()) {
                if (!"*.kof".equals(imp) && imp.endsWith("." + name)) {
                    return imp.replace('.', '/');
                }
            }
        }
        return name;
    }

    private IRMethod lowerMethod(MethodDeclarationNode method, String owner, boolean isInterface, List<String> typeParams) {
        String prevOwner = currentLoweringOwner;
        currentLoweringOwner = owner;
        try {
            return lowerMethodInner(method, owner, isInterface, typeParams);
        } finally {
            currentLoweringOwner = prevOwner;
        }
    }

    private IRMethod lowerMethodInner(MethodDeclarationNode method, String owner, boolean isInterface, List<String> typeParams) {
        Type returnType = resolveWithTypeParams(method.returnType(), typeParams);
        List<Type> paramTypes = method.parameters().stream()
                .map(p -> resolveWithTypeParams(p.type(), typeParams)).toList();
        if (Type.isVoid(returnType) && method.body() != null && !method.body().isEmpty()
                && method.body().getLast() instanceof ReturnStmt ret && ret.value() != null) {
            List<IRLocalVariable> tmpLocals = new ArrayList<>();
            int tmpIdx = 1;
            for (FormalParameterNode p : method.parameters()) {
                tmpLocals.add(new IRLocalVariable(tmpIdx, p.name(), resolveWithTypeParams(p.type(), typeParams)));
                tmpIdx++;
            }
            Type inferred = inferExprType(ret.value(), tmpLocals);
            if (inferred instanceof Type.UnknownType && semanticAnalyzer != null) {
                Type semanticRt = semanticAnalyzer.resolvedMethodReturnType(method);
                if (semanticRt != null && !(semanticRt instanceof Type.UnknownType) && !Type.isVoid(semanticRt)) {
                    inferred = semanticRt;
                }
            }
            if (!(inferred instanceof Type.UnknownType)) {
                returnType = inferred;
            }
        }
        int access = computeAccess(method.modifiers());
        if (isInterface && !method.modifiers().contains("default")) access |= AccessFlags.ABSTRACT;
        List<IRBasicBlock> body = List.of();
        List<IRLocalVariable> locals = List.of();
        if (method.body() != null && !method.body().isEmpty() && !isAbstractMethod(method)) {
            List<KofOperation> ops = new ArrayList<>();
            List<IRLocalVariable> localVars = new ArrayList<>();
            Type ownerType = ownerTypeFromInternal(owner);
            // método ESTÁTICO: sem this, params começam no slot 0
            boolean isStaticMethod = (access & AccessFlags.STATIC) != 0;
            if (!isStaticMethod) {
                localVars.add(new IRLocalVariable(0, "this", ownerType));
            }
            int localIdx = isStaticMethod ? 0 : 1;
            for (FormalParameterNode param : method.parameters()) {
                Type paramType = resolveWithTypeParams(param.type(), typeParams);
                localVars.add(new IRLocalVariable(localIdx, param.name(), paramType));
                localIdx += isDoubleWidth(paramType) ? 2 : 1;
            }
            java.util.Set<String> savedMutated = mutatedCapturedNames;
            mutatedCapturedNames = new java.util.HashSet<>();
            collectMutatedCaptures(method.body());
            for (StatementNode stmt : method.body()) localIdx = emitStatement(stmt, ops, owner, localIdx, localVars, returnType);
            mutatedCapturedNames = savedMutated;
            KofOperation lastOp = ops.isEmpty() ? null : ops.get(ops.size() - 1);
            if (lastOp == null || !(lastOp instanceof KofReturn || lastOp instanceof KofReturnVoid)) {
                if (Type.isVoid(returnType)) ops.add(new KofReturnVoid());
                else ops.add(new KofReturn(returnType));
            }
            body = List.of(new IRBasicBlock(0, ops));
            locals = localVars;
        }
        KofDebugInfo debugInfo = currentDebugPositions.isEmpty()
                ? KofDebugInfo.EMPTY
                : new KofDebugInfo(new java.util.HashMap<>(currentDebugPositions));
        currentDebugPositions.clear();
        return new IRMethod(method.name(), returnType, paramTypes, access, method.thrownExceptions(),
                body, locals, debugInfo,
                lowerAnnotations(method.annotations()), lowerParameterAnnotations(method.parameters()));
    }

    /** Annotations por parâmetro, alinhadas à ordem de parameterTypes. */
    private List<List<IRAnnotation>> lowerParameterAnnotations(List<FormalParameterNode> params) {
        List<List<IRAnnotation>> out = new ArrayList<>();
        boolean any = false;
        for (FormalParameterNode p : params) {
            List<IRAnnotation> annos = lowerAnnotations(p.annotations());
            if (!annos.isEmpty()) any = true;
            out.add(annos);
        }
        return any ? out : List.of();
    }

    /**
     * Default parameter values on constructors: for each trailing default, a
     * wrapper <init> with fewer parameters evaluates the default expressions
     * and delegates to the canonical constructor — the same semantics as
     * lowerFunctionDefaults for functions.
     */
    private List<IRMethod> lowerConstructorDefaults(ConstructorDeclarationNode ctor, String owner,
                                                    String superName, List<String> typeParams,
                                                    List<IRField> fields,
                                                    java.util.Map<String, ExpressionNode> fieldInits) {
        List<IRMethod> wrappers = new ArrayList<>();
        List<FormalParameterNode> params = ctor.parameters();
        if (params.isEmpty() || params.stream().noneMatch(p -> p.defaultExpression() != null)) {
            return wrappers;
        }
        int n = params.size();
        int firstDefault = n;
        for (int i = 0; i < n; i++) {
            if (params.get(i).defaultExpression() != null) {
                firstDefault = i;
                break;
            }
        }
        if (firstDefault == n) return wrappers;
        List<Type> canonicalTypes = new ArrayList<>();
        for (FormalParameterNode p : params) canonicalTypes.add(resolveWithTypeParams(p.type(), typeParams));
        Type ownerType = ownerTypeFromInternal(owner);
        Type superType = ownerTypeFromInternal(superName);
        for (int drop = 1; drop <= n - firstDefault; drop++) {
            int paramCount = n - drop;
            List<Type> paramTypes = canonicalTypes.subList(0, paramCount);
            List<IRLocalVariable> locals = new ArrayList<>();
            locals.add(new IRLocalVariable(0, "this", ownerType));
            List<KofOperation> ops = new ArrayList<>();
            // No super()/field inits here: the canonical <init> performs
            // them; the wrapper only supplies the default arguments.
            ops.add(new KofLoadLocal(ownerType, 0));
            int localIdx = 1;
            for (int i = 0; i < paramCount; i++) {
                locals.add(new IRLocalVariable(localIdx, params.get(i).name(), paramTypes.get(i)));
                ops.add(new KofLoadLocal(paramTypes.get(i), localIdx));
                localIdx++;
            }
            for (int i = paramCount; i < n; i++) {
                localIdx = emitExpression(params.get(i).defaultExpression(), ops, owner,
                        localIdx, locals);
            }
            ops.add(new KofCall(ownerType, "<init>", canonicalTypes, Type.PrimitiveType.VOID,
                    KofCallKind.CONSTRUCTOR));
            ops.add(new KofReturnVoid());
            wrappers.add(new IRMethod("<init>", Type.PrimitiveType.VOID, paramTypes,
                    computeAccess(ctor.modifiers()), ctor.thrownExceptions(),
                    List.of(new IRBasicBlock(0, ops)), locals));
        }
        return wrappers;
    }

    private IRMethod lowerConstructor(ConstructorDeclarationNode ctor, String owner, String superName,
                                      List<String> typeParams, List<IRField> fields,
                                      java.util.Map<String, ExpressionNode> fieldInits) {
        String prevOwner = currentLoweringOwner;
        currentLoweringOwner = owner;
        try {
            return lowerConstructorInner(ctor, owner, superName, typeParams, fields, fieldInits);
        } finally {
            currentLoweringOwner = prevOwner;
        }
    }

    private IRMethod lowerConstructorInner(ConstructorDeclarationNode ctor, String owner, String superName,
                                      List<String> typeParams, List<IRField> fields,
                                      java.util.Map<String, ExpressionNode> fieldInits) {
        List<Type> paramTypes = ctor.parameters().stream()
                .map(p -> resolveWithTypeParams(p.type(), typeParams)).toList();
        int access = computeAccess(ctor.modifiers());
        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> localVars = new ArrayList<>();
        Type ownerType = ownerTypeFromInternal(owner);
        Type superType = ownerTypeFromInternal(superName);
        localVars.add(new IRLocalVariable(0, "this", ownerType));
        boolean delegatesToThis = !ctor.body().isEmpty() &&
                ctor.body().getFirst() instanceof ExpressionStmt es &&
                es.expression() instanceof MethodCallExpr mc &&
                "this".equals(mc.methodName());
        boolean hasExplicitSuper = !ctor.body().isEmpty() &&
                ctor.body().getFirst() instanceof ExpressionStmt es &&
                es.expression() instanceof MethodCallExpr mc &&
                "super".equals(mc.methodName());
        // this(...): o construtor alvo executa super() e os inicializadores
        if (!delegatesToThis && !hasExplicitSuper && !"java/lang/Object".equals(superName)) {
            ops.add(new KofLoadLocal(ownerType, 0));
            ops.add(new KofCall(superType, "<init>", List.of(), Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
        }
        if (!delegatesToThis) {
            emitFieldInitializers(ops, ownerType, fields);
        }
        int localIdx = 1;
        for (FormalParameterNode param : ctor.parameters()) {
            Type paramType = resolveWithTypeParams(param.type(), typeParams);
            localVars.add(new IRLocalVariable(localIdx, param.name(), paramType));
            localIdx += isDoubleWidth(paramType) ? 2 : 1;
        }
        for (var entry : fieldInits.entrySet()) {
            if (delegatesToThis) break;
            Type fieldType = fields.stream().filter(f -> f.name().equals(entry.getKey())).findFirst()
                    .map(f -> f.type()).orElse(Type.UnknownType.UNKNOWN);
            ops.add(new KofLoadLocal(ownerType, 0));
            localIdx = emitExpression(entry.getValue(), ops, owner, localIdx, localVars);
            ops.add(new KofStoreField(ownerType, entry.getKey(), fieldType));
        }
        for (StatementNode stmt : ctor.body()) localIdx = emitStatement(stmt, ops, owner, localIdx, localVars, Type.PrimitiveType.VOID);
        ops.add(new KofReturnVoid());
        return new IRMethod("<init>", Type.PrimitiveType.VOID, paramTypes, access, ctor.thrownExceptions(),
                List.of(new IRBasicBlock(0, ops)), localVars, KofDebugInfo.EMPTY,
                lowerAnnotations(ctor.annotations()), lowerParameterAnnotations(ctor.parameters()));
    }

    private IRMethod generateDefaultConstructor(String owner, String superName, List<IRField> fields,
                                                 java.util.Map<String, ExpressionNode> fieldInits) {
        Type ownerType = ownerTypeFromInternal(owner);
        Type superType = ownerTypeFromInternal(superName);
        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> locals = new ArrayList<>();
        locals.add(new IRLocalVariable(0, "this", ownerType));
        if (!"java/lang/Object".equals(superName)) {
            ops.add(new KofLoadLocal(ownerType, 0));
            ops.add(new KofCall(superType, "<init>", List.of(), Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
        }
        emitFieldInitializers(ops, ownerType, fields);
        int localIdx = 1;
        for (var entry : fieldInits.entrySet()) {
            Type fieldType = fields.stream().filter(f -> f.name().equals(entry.getKey())).findFirst()
                    .map(f -> f.type()).orElse(Type.UnknownType.UNKNOWN);
            ops.add(new KofLoadLocal(ownerType, 0));
            localIdx = emitExpression(entry.getValue(), ops, owner, localIdx, locals);
            ops.add(new KofStoreField(ownerType, entry.getKey(), fieldType));
        }
        ops.add(new KofReturnVoid());
        return new IRMethod("<init>", Type.PrimitiveType.VOID, List.of(), AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, ops)), locals);
    }

    /**
     * Field initializers must run in the constructor (after super(), before
     * the body) — instance fields with a default value are assigned there.
     * Never silently ignore an initializer.
     */
    private void emitFieldInitializers(List<KofOperation> ops, Type ownerType, List<IRField> fields) {
        for (IRField field : fields) {
            if (field.initialValue() == null || (field.accessFlags() & AccessFlags.STATIC) != 0) continue;
            ops.add(new KofLoadLocal(ownerType, 0));
            Object v = field.initialValue();
            String fieldName = field.type() instanceof Type.PrimitiveType pt
                    ? Type.canonicalPrimitiveName(pt.name()) : "";
            if (v instanceof Integer) {
                int iv = (Integer) v;
                if ("long".equals(fieldName)) {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.LONG, (long) iv));
                } else if ("double".equals(fieldName)) {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.DOUBLE, (double) iv));
                } else if ("float".equals(fieldName)) {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.FLOAT, (float) iv));
                } else {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, iv));
                }
            } else if (v instanceof Long) {
                ops.add(new KofLoadLiteral(Type.PrimitiveType.LONG, (Long) v));
            } else if (v instanceof String) {
                ops.add(new KofLoadLiteral(BuiltinTypes.STRING, (String) v));
            } else if (v instanceof Double) {
                ops.add(new KofLoadLiteral(Type.PrimitiveType.DOUBLE, (Double) v));
            } else if (v instanceof Float) {
                ops.add(new KofLoadLiteral(Type.PrimitiveType.FLOAT, (Float) v));
            } else if (v instanceof Boolean) {
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, ((Boolean) v) ? 1 : 0));
            } else {
                continue;
            }
            ops.add(new KofStoreField(ownerType, field.name(), field.type()));
        }
    }

    private IRMethod generateRecordConstructor(RecordDeclarationNode rec, String owner) {
        List<Type> compTypes = rec.components().stream().map(c -> toType(c.type())).toList();
        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> locals = new ArrayList<>();
        Type ownerType = ownerTypeFromInternal(owner);
        Type superType = new Type.ClassType("java.lang", "Record", List.of());
        locals.add(new IRLocalVariable(0, "this", ownerType));
        if (isJvmTarget()) {


            ops.add(new KofLoadLocal(ownerType, 0));
            ops.add(new KofCall(superType, "<init>", List.of(), Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
        }
        int localIdx = 1;
        for (RecordComponentNode comp : rec.components()) {
            Type compType = toType(comp.type());
            locals.add(new IRLocalVariable(localIdx, comp.name(), compType));
            ops.add(new KofLoadLocal(ownerType, 0));
            ops.add(new KofLoadLocal(compType, localIdx));
            ops.add(new KofStoreField(ownerType, comp.name(), compType));
            localIdx += isDoubleWidth(compType) ? 2 : 1;
        }
        ops.add(new KofReturnVoid());
        return new IRMethod("<init>", Type.PrimitiveType.VOID, compTypes, AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, ops)), locals);
    }

    private List<IRMethod> generateRecordDefaultOverloads(RecordDeclarationNode rec, String owner) {
        List<IRMethod> overloads = new ArrayList<>();
        int n = rec.components().size();
        int firstDefault = n;
        for (int i = 0; i < n; i++) {
            if (rec.components().get(i).initializer() != null) {
                firstDefault = i;
                break;
            }
        }
        if (firstDefault == n) return overloads;
        Type ownerType = ownerTypeFromInternal(owner);
        List<Type> canonicalTypes = rec.components().stream().map(c -> toType(c.type())).toList();
        for (int drop = 1; drop <= n - firstDefault; drop++) {
            int paramCount = n - drop;
            List<Type> paramTypes = new ArrayList<>();
            List<IRLocalVariable> locals = new ArrayList<>();
            List<KofOperation> ops = new ArrayList<>();
            locals.add(new IRLocalVariable(0, "this", ownerType));
            ops.add(new KofLoadLocal(ownerType, 0));
            int localIdx = 1;
            for (int i = 0; i < paramCount; i++) {
                Type t = toType(rec.components().get(i).type());
                paramTypes.add(t);
                locals.add(new IRLocalVariable(localIdx, rec.components().get(i).name(), t));
                ops.add(new KofLoadLocal(t, localIdx));
                localIdx += isDoubleWidth(t) ? 2 : 1;
            }
            for (int i = paramCount; i < n; i++) {
                ExpressionNode init = rec.components().get(i).initializer();
                if (init != null) {
                    localIdx = emitExpression(init, ops, owner, localIdx, locals);
                }
            }
            ops.add(new KofCall(ownerType, "<init>", canonicalTypes, Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
            ops.add(new KofReturnVoid());
            IRMethod m = new IRMethod("<init>", Type.PrimitiveType.VOID, paramTypes, AccessFlags.PUBLIC,
                    List.of(), List.of(new IRBasicBlock(0, ops)), locals);
            overloads.add(m);
        }
        return overloads;
    }

    private String toInternalName(String packageName, String simpleName) {
        if (simpleName.contains("/")) return simpleName;
        if (simpleName.contains(".")) return simpleName.replace('.', '/');
        if (packageName.isEmpty()) return simpleName;
        return packageName.replace('.', '/') + "/" + simpleName;
    }

    private int computeAccess(List<String> modifiers) {
        int access = 0;
        boolean hasVisibility = false;
        for (String mod : modifiers) {
            access |= switch (mod) {
                case "public" -> AccessFlags.PUBLIC;
                case "private" -> AccessFlags.PRIVATE;
                case "protected" -> AccessFlags.PROTECTED;
                case "static" -> AccessFlags.STATIC;
                case "final" -> AccessFlags.FINAL;
                case "abstract" -> AccessFlags.ABSTRACT;
                default -> 0;
            };
            if ("public".equals(mod) || "private".equals(mod) || "protected".equals(mod)) {
                hasVisibility = true;
            }
        }
        if (!hasVisibility) access |= AccessFlags.PUBLIC;
        return access;
    }

    private boolean isAbstractMethod(MethodDeclarationNode method) {
        return method.body() == null || method.body().isEmpty();
    }

    /**
     * Parses an integer literal, including hexadecimal (0xFF...). ARGB color
     * values may exceed Integer.MAX_VALUE; they wrap to the signed 32-bit
     * representation, which the Kof color semantics use (shifts + mask).
     */
    private int parseIntLiteral(String value) {
        if (value.startsWith("0x") || value.startsWith("0X")) {
            // no suffix stripping: hex digits may end in a..f
            return (int) Long.parseLong(value.substring(2), 16);
        }
        return Integer.parseInt(stripSuffix(value));
    }

    private String stripSuffix(String value) {
        if (value.endsWith("l") || value.endsWith("L") ||
            value.endsWith("f") || value.endsWith("F") ||
            value.endsWith("d") || value.endsWith("D")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private boolean isDoubleWidth(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return "long".equals(pt.name()) || "Long".equals(pt.name()) ||
                   "double".equals(pt.name()) || "Double".equals(pt.name());
        }
        return false;
    }
}
