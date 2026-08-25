package dev.kof.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

class SemanticAnalyzer {

    private SymbolTable currentScope;
    private CompilationUnitNode currentUnit;

    /** Classpath externo (android.jar etc.) para resolver membros de classes fora da IR. */
    private ExternalClasspath externalTypes;

    void setExternalTypes(ExternalClasspath cp) {
        this.externalTypes = cp;
    }

    private boolean isExternal(Type.ClassType ct) {
        return externalTypes != null && !ct.packageName().isEmpty()
                && externalTypes.knows(ct.internalName());
    }

    /**
     * Nome simples declarado em import vira tipo qualificado
     * ("import android.webkit.WebView" → ClassType("android.webkit","WebView")).
     * Sem isso, tipos de classes externas saem sem pacote e o descritor
     * JVM quebra.
     */
    private Type qualifyViaImports(String name) {
        if (name.contains(".") || name.contains("<") || name.endsWith("[]")) return null;
        if (currentUnit == null) return null;
        for (String imp : currentUnit.imports()) {
            if (!imp.endsWith("*") && imp.endsWith("." + name)) {
                String pkg = imp.substring(0, imp.lastIndexOf('.'));
                return new Type.ClassType(pkg, name, List.of());
            }
        }
        return null;
    }
    private final Map<String, SymbolTable.ClassSymbol> knownClasses = new HashMap<>();
    private final java.util.Set<String> interfaceNames = new java.util.HashSet<>();
    private final Map<ExpressionNode, Type> expressionTypes = new IdentityHashMap<>();
    private final Map<MethodCallExpr, SymbolTable.MethodSymbol> resolvedMethods = new IdentityHashMap<>();
    private final Map<NewExpr, SymbolTable.ConstructorSymbol> resolvedConstructors = new IdentityHashMap<>();
    private String currentClassName;
    private String currentFunctionName;
    private String currentPackage;
    private DiagnosticCollector diagnostics;

    void analyze(CompilationUnitNode unit, DiagnosticCollector diagnostics) {
        this.diagnostics = diagnostics;
        this.currentPackage = unit.packageName();
        this.currentScope = new SymbolTable();
        this.currentUnit = unit;
        for (AstNode decl : unit.declarations()) {
            preDeclareType(decl);
        }
        for (AstNode decl : unit.declarations()) {
            analyzeDeclaration(decl);
        }
        resolveMethodCalls(unit);
    }

    Type getExpressionType(ExpressionNode expr) {
        Type t = expressionTypes.get(expr);
        return t != null ? t : Type.UnknownType.UNKNOWN;
    }

    SymbolTable.MethodSymbol getResolvedMethod(MethodCallExpr mc) {
        return resolvedMethods.get(mc);
    }

    Type resolvedMethodReturnType(MethodDeclarationNode method) {
        SymbolTable.MethodSymbol ms = methodSymbols.get(method);
        return ms != null ? ms.returnType() : null;
    }

    SymbolTable.ConstructorSymbol getResolvedConstructor(NewExpr ne) {
        return resolvedConstructors.get(ne);
    }

    SymbolTable.ClassSymbol getClass(String name) {
        return knownClasses.get(name);
    }

    Map<String, SymbolTable.ClassSymbol> allClasses() {
        return knownClasses;
    }

    boolean isInterfaceType(String name) {
        return interfaceNames.contains(name);
    }

    SymbolTable.Symbol resolveInHierarchy(String className, String memberName) {
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.Queue<String> queue = new java.util.LinkedList<>();
        queue.add(className);
        visited.add(className);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            SymbolTable.ClassSymbol cs = knownClasses.get(current);
            if (cs == null) continue;
            SymbolTable.Symbol s = cs.members().resolve(memberName);
            if (s != null) return s;
            if (cs.superClass() != null && !"Object".equals(cs.superClass()) && !visited.contains(cs.superClass())) {
                visited.add(cs.superClass());
                queue.add(cs.superClass());
            }
            for (String iface : cs.interfaces()) {
                if (!visited.contains(iface)) {
                    visited.add(iface);
                    queue.add(iface);
                }
            }
        }
        return null;
    }

    private void preDeclareType(AstNode decl) {
        if (decl instanceof ClassDeclarationNode cls) {
            SymbolTable members = new SymbolTable();
            // superclasse qualificada pelos imports: "extends Activity" com
            // "import android.app.Activity" vira "android.app.Activity" —
            // sem isso a resolução externa (classpath) nunca encontra a classe
            String superQualified = cls.superClass();
            if (superQualified != null && !"Object".equals(superQualified)) {
                Type viaImports = qualifyViaImports(superQualified);
                if (viaImports instanceof Type.ClassType qt) {
                    superQualified = qt.packageName() + "." + qt.name();
                }
            }
            SymbolTable.ClassSymbol sym = new SymbolTable.ClassSymbol(cls.name(), currentPackage,
                    cls.superClass() != null ? superQualified : "Object",
                    cls.interfaces(), members);
            knownClasses.put(cls.name(), sym);
            currentScope.define(sym);
        } else if (decl instanceof RecordDeclarationNode rec) {
            SymbolTable members = new SymbolTable();
            SymbolTable.ClassSymbol sym = new SymbolTable.ClassSymbol(rec.name(), currentPackage,
                    "Record", rec.interfaces(), members);
            knownClasses.put(rec.name(), sym);
            currentScope.define(sym);
        } else if (decl instanceof EntityDeclarationNode ent) {
            SymbolTable members = new SymbolTable();
            SymbolTable.ClassSymbol sym = new SymbolTable.ClassSymbol(ent.name(), currentPackage,
                    "Record", List.of(), members);
            knownClasses.put(ent.name(), sym);
            currentScope.define(sym);
        } else if (decl instanceof InterfaceDeclarationNode iface) {
            SymbolTable members = new SymbolTable();
            SymbolTable.ClassSymbol sym = new SymbolTable.ClassSymbol(iface.name(), currentPackage,
                    "Object", iface.interfaces(), members);
            knownClasses.put(iface.name(), sym);
            interfaceNames.add(iface.name());
            currentScope.define(sym);
        }
    }

    private void analyzeDeclaration(AstNode decl) {
        switch (decl) {
            case ClassDeclarationNode cls -> analyzeClass(cls);
            case RecordDeclarationNode rec -> analyzeRecord(rec);
            case EntityDeclarationNode ent -> analyzeEntity(ent);
            case InterfaceDeclarationNode iface -> analyzeInterface(iface);
            case FunctionDeclarationNode func -> analyzeFunction(func);
            default -> {}
        }
    }

    private boolean isLocalName(String name, SymbolTable scope) {
        if (scope == null) return false;
        return scope.resolve(name) != null;
    }

    private Type resolveType(String name, SymbolTable scope) {
        SymbolTable.Symbol sym = scope != null ? scope.resolve(name) : null;
        if (sym instanceof SymbolTable.TypeParameterSymbol) return sym.type();
        Type viaImports = qualifyViaImports(name);
        if (viaImports != null) return viaImports;
        return qualifiedType(Type.of(name));
    }

    /**
     * Nomes qualificados ("android.os.Bundle") precisam do pacote separado
     * do nome simples — senão o descritor JVM sai com pontos
     * (Landroid.os.Bundle;) e a classe não carrega.
     */
    static Type qualifiedType(Type type) {
        if (type instanceof Type.ClassType ct && !ct.name().contains("<")
                && ct.packageName().isEmpty()) {
            int lastDot = ct.name().lastIndexOf('.');
            if (lastDot > 0) {
                return new Type.ClassType(ct.name().substring(0, lastDot),
                        ct.name().substring(lastDot + 1), ct.typeArguments());
            }
        }
        return type;
    }

    private void analyzeClass(ClassDeclarationNode cls) {
        String prevClass = currentClassName;
        currentClassName = cls.name();
        SymbolTable.ClassSymbol classSym = knownClasses.get(cls.name());
        SymbolTable classScope = classSym.members().enterScope();
        SymbolTable prevScope = currentScope;
        currentScope = classScope;
        for (String tp : cls.typeParameters()) {
            classScope.define(new SymbolTable.TypeParameterSymbol(tp));
        }
        for (AstNode member : cls.members()) {
            if (member instanceof FieldDeclarationNode field) {
                Type fieldType = resolveType(field.type(), classScope);
                int flags = field.modifiers().contains("static") ? AccessFlags.STATIC : 0;
                SymbolTable.FieldSymbol fs = new SymbolTable.FieldSymbol(field.name(), fieldType, flags, cls.name());
                classSym.members().define(fs);
                classScope.define(fs);
                if (field.initializer() != null) {
                    inferType(field.initializer(), classScope);
                }
            }
        }
        boolean hasCtor = false;
        for (AstNode member : cls.members()) {
            if (member instanceof ConstructorDeclarationNode ctor) {
                defineConstructorSymbol(ctor, cls.name(), classScope);
                hasCtor = true;
            } else if (member instanceof MethodDeclarationNode method) {
                defineMethodSymbol(method, cls.name(), classScope);
            }
        }
        if (!hasCtor) {
            // implicit default constructor: classes without an explicit
            // constructor always have one (Kof semantics)
            classScope.define(new SymbolTable.ConstructorSymbol(cls.name(), List.of(), 1));
        }
        for (int pass = 0; pass < 4; pass++) {
            boolean changed = false;
            expressionTypes.clear();
            for (AstNode member : cls.members()) {
                if (member instanceof ConstructorDeclarationNode ctor) {
                    analyzeConstructorBody(ctor);
                } else if (member instanceof MethodDeclarationNode method) {
                    SymbolTable.MethodSymbol ms = methodSymbols.get(method);
                    Type before = ms != null ? ms.returnType() : null;
                    analyzeMethodBody(method);
                    Type after = ms != null ? ms.returnType() : null;
                    if (before != null && after != null && !before.equals(after)) {
                        changed = true;
                    }
                }
            }
            if (!changed) break;
        }
        currentScope = prevScope;
        currentClassName = prevClass;
    }

    private final java.util.IdentityHashMap<ConstructorDeclarationNode, SymbolTable> ctorScopes = new java.util.IdentityHashMap<>();
    private final java.util.IdentityHashMap<MethodDeclarationNode, SymbolTable> methodScopes = new java.util.IdentityHashMap<>();
    private final java.util.IdentityHashMap<MethodDeclarationNode, SymbolTable.MethodSymbol> methodSymbols = new java.util.IdentityHashMap<>();

    private void defineConstructorSymbol(ConstructorDeclarationNode ctor, String className, SymbolTable classScope) {
        List<Type> paramTypes = new ArrayList<>();
        SymbolTable ctorScope = classScope.enterScope();
        ctorScope.define(new SymbolTable.ParameterSymbol("this",
                new Type.ClassType(currentPackage, className, List.of()), 0));
        int idx = 1;
        for (FormalParameterNode param : ctor.parameters()) {
            Type paramType = resolveType(param.type(), ctorScope);
            paramTypes.add(paramType);
            ctorScope.define(new SymbolTable.ParameterSymbol(param.name(), paramType, idx));
            idx++;
        }
        SymbolTable.ConstructorSymbol ctorSym = new SymbolTable.ConstructorSymbol(className, paramTypes, 1);
        classScope.define(ctorSym);
        SymbolTable.ClassSymbol cs = knownClasses.get(className);
        if (cs != null) cs.members().define(ctorSym);
        ctorScopes.put(ctor, ctorScope);
    }

    private void defineMethodSymbol(MethodDeclarationNode method, String className, SymbolTable classScope) {
        SymbolTable methodScope = classScope.enterScope();
        methodScope.define(new SymbolTable.ParameterSymbol("this",
                new Type.ClassType(currentPackage, className, List.of()), 0));
        Type returnType = resolveType(method.returnType(), methodScope);
        List<Type> paramTypes = new ArrayList<>();
        int idx = 1;
        for (FormalParameterNode param : method.parameters()) {
            Type paramType = resolveType(param.type(), methodScope);
            paramTypes.add(paramType);
            methodScope.define(new SymbolTable.ParameterSymbol(param.name(), paramType, idx));
            idx++;
        }
        SymbolTable.MethodSymbol methodSym = new SymbolTable.MethodSymbol(method.name(), className,
                returnType, paramTypes, 1, SymbolTable.DispatchKind.INSTANCE);
        classScope.define(methodSym);
        SymbolTable.ClassSymbol cs = knownClasses.get(className);
        if (cs != null) cs.members().define(methodSym);
        methodScopes.put(method, methodScope);
        methodSymbols.put(method, methodSym);
    }

    private void analyzeConstructorBody(ConstructorDeclarationNode ctor) {
        SymbolTable ctorScope = ctorScopes.get(ctor);
        if (ctorScope == null || ctor.body() == null || ctor.body().isEmpty()) return;
        SymbolTable prevScope = currentScope;
        currentScope = ctorScope;
        analyzeBody(ctor.body(), ctorScope, Type.PrimitiveType.VOID);
        currentScope = prevScope;
    }

    private void analyzeMethodBody(MethodDeclarationNode method) {
        SymbolTable methodScope = methodScopes.get(method);
        if (methodScope == null || method.body() == null || method.body().isEmpty()) return;
        Type returnType = resolveType(method.returnType(), methodScope);
        SymbolTable prevScope = currentScope;
        currentScope = methodScope;
        analyzeBody(method.body(), methodScope, returnType);
        currentScope = prevScope;
        if (Type.isVoid(returnType) && method.body().getLast() instanceof ReturnStmt ret
                && ret.value() != null) {
            Type inferred = inferType(ret.value(), methodScope);
            SymbolTable.MethodSymbol ms = methodSymbols.get(method);
            if (ms != null && !(inferred instanceof Type.UnknownType)) {
                ms.setReturnType(inferred);
            }
        }
    }

    private void analyzeEntity(EntityDeclarationNode ent) {
        List<RecordComponentNode> components = new java.util.ArrayList<>();
        for (EntityFieldNode f : ent.fields()) {
            components.add(new RecordComponentNode(f.position(), List.of(), f.type(), f.name(), null));
        }
        analyzeRecord(new RecordDeclarationNode(ent.position(), ent.name(), ent.modifiers(),
                null, List.of(), components, List.of()));
    }

    private void analyzeRecord(RecordDeclarationNode rec) {
        String prevClass = currentClassName;
        currentClassName = rec.name();
        SymbolTable.ClassSymbol classSym = knownClasses.get(rec.name());
        SymbolTable classScope = classSym.members().enterScope();
        SymbolTable prevScope = currentScope;
        currentScope = classScope;
        List<Type> compTypes = new ArrayList<>();
        for (RecordComponentNode comp : rec.components()) {
            Type compType = Type.of(comp.type());
            compTypes.add(compType);
            SymbolTable.FieldSymbol fs = new SymbolTable.FieldSymbol(comp.name(), compType, 0, rec.name());
            classSym.members().define(fs);
            classScope.define(fs);
            if (comp.initializer() != null) {
                inferType(comp.initializer(), classScope);
            }
        }
        SymbolTable.ConstructorSymbol ctorSym = new SymbolTable.ConstructorSymbol(rec.name(), compTypes, 1);
        classSym.members().define(ctorSym);
        classScope.define(ctorSym);
        for (RecordComponentNode comp : rec.components()) {
            Type compType = Type.of(comp.type());
            SymbolTable.MethodSymbol ms = new SymbolTable.MethodSymbol(comp.name(), rec.name(),
                    compType, List.of(), 1, SymbolTable.DispatchKind.INSTANCE);
            classSym.members().define(ms);
            classScope.define(ms);
        }
        for (AstNode member : rec.members()) {
            if (member instanceof MethodDeclarationNode method) {
                defineMethodSymbol(method, rec.name(), classScope);
            }
        }
        for (int pass = 0; pass < 4; pass++) {
            boolean changed = false;
            expressionTypes.clear();
            for (AstNode member : rec.members()) {
                if (member instanceof MethodDeclarationNode method) {
                    SymbolTable.MethodSymbol ms = methodSymbols.get(method);
                    Type before = ms != null ? ms.returnType() : null;
                    analyzeMethodBody(method);
                    Type after = ms != null ? ms.returnType() : null;
                    if (before != null && after != null && !before.equals(after)) {
                        changed = true;
                    }
                }
            }
            if (!changed) break;
        }
        currentScope = prevScope;
        currentClassName = prevClass;
    }

    private void analyzeInterface(InterfaceDeclarationNode iface) {
        String prevClass = currentClassName;
        currentClassName = iface.name();
        SymbolTable.ClassSymbol classSym = knownClasses.get(iface.name());
        SymbolTable classScope = classSym.members().enterScope();
        SymbolTable prevScope = currentScope;
        currentScope = classScope;
        for (AstNode member : iface.members()) {
            if (member instanceof MethodDeclarationNode method) {
                Type returnType = resolveType(method.returnType(), classScope);
                List<Type> paramTypes = new ArrayList<>();
                for (FormalParameterNode p : method.parameters()) paramTypes.add(Type.of(p.type()));
                SymbolTable.MethodSymbol ms = new SymbolTable.MethodSymbol(method.name(), iface.name(),
                        returnType, paramTypes, 0, SymbolTable.DispatchKind.INSTANCE);
                classScope.define(ms);
                // Interface methods must be resolvable from outside the
                // interface's own scope (resolveInHierarchy uses members()).
                classSym.members().define(ms);
            }
        }
        currentScope = prevScope;
        currentClassName = prevClass;
    }

    private void analyzeFunction(FunctionDeclarationNode func) {
        String prevFunction = currentFunctionName;
        currentFunctionName = func.name();
        SymbolTable funcScope = currentScope.enterScope();
        for (String tp : func.typeParameters()) {
            funcScope.define(new SymbolTable.TypeParameterSymbol(tp));
        }
        Type returnType = resolveType(func.returnType(), funcScope);
        int idx = 0;
        for (FormalParameterNode param : func.parameters()) {
            Type paramType = Type.of(param.type());
            funcScope.define(new SymbolTable.ParameterSymbol(param.name(), paramType, idx));
            idx++;
        }
        SymbolTable prevScope = currentScope;
        currentScope = funcScope;
        analyzeBody(func.body(), funcScope, returnType);
        currentScope = prevScope;
    }

    private void analyzeBody(List<StatementNode> body, SymbolTable scope, Type returnType) {
        for (StatementNode stmt : body) {
            analyzeStatement(stmt, scope, returnType);
        }
    }

    private void analyzeStatement(StatementNode stmt, SymbolTable scope, Type returnType) {
        switch (stmt) {
            case BlockStmt block -> {
                SymbolTable blockScope = scope.enterScope();
                for (StatementNode s : block.statements()) analyzeStatement(s, blockScope, returnType);
            }
            case VarDeclStmt vds -> {
                Type varType;
                if (vds.type() != null && !vds.type().isEmpty() && !"var".equals(vds.type())) {
                    Type viaImports = qualifyViaImports(vds.type());
                    varType = viaImports != null ? viaImports : Type.of(vds.type());
                } else if (vds.initializer() != null) {
                    varType = inferType(vds.initializer(), scope);
                } else {
                    varType = Type.UnknownType.UNKNOWN;
                }
                if (vds.initializer() != null) inferType(vds.initializer(), scope);
                scope.define(new SymbolTable.LocalVariableSymbol(vds.name(), varType, 0));
            }
            case ReturnStmt ret -> {
                if (ret.value() != null) {
                    Type valueType = inferType(ret.value(), scope);
                    expressionTypes.put(ret.value(), valueType);
                    if (diagnostics != null && !Type.isUnknown(returnType) && !Type.isVoid(returnType)
                            && !Type.isUnknown(valueType) && !isAssignable(valueType, returnType)) {
                        diagnostics.error("", 0, 0, 0,
                                "Return type mismatch: expected " + returnType + " but got " + valueType, "SEM010");
                    }
                }
            }
            case BreakStmt ignored -> {}
            case ContinueStmt ignored -> {}
            case IfStmt ifStmt -> {
                inferType(ifStmt.condition(), scope);
                SymbolTable ifScope = scope.enterScope();
                analyzeStatement(ifStmt.thenBranch(), ifScope, returnType);
                if (ifStmt.elseBranch() != null) analyzeStatement(ifStmt.elseBranch(), ifScope, returnType);
            }
            case WhileStmt ws -> {
                inferType(ws.condition(), scope);
                SymbolTable whileScope = scope.enterScope();
                analyzeStatement(ws.body(), whileScope, returnType);
            }
            case DoWhileStmt dws -> {
                SymbolTable doScope = scope.enterScope();
                analyzeStatement(dws.body(), doScope, returnType);
                inferType(dws.condition(), doScope);
            }
            case ForStmt fs -> {
                SymbolTable forScope = scope.enterScope();
                if (fs.init() != null) analyzeStatement(fs.init(), forScope, returnType);
                if (fs.condition() != null) inferType(fs.condition(), forScope);
                analyzeStatement(fs.body(), forScope, returnType);
                if (fs.update() != null) inferType(fs.update(), forScope);
            }
            case ForInStmt fis -> {
                SymbolTable forScope = scope.enterScope();
                Type collType = inferType(fis.collection(), forScope);
                Type elemType = Type.UnknownType.UNKNOWN;
                if (collType instanceof Type.ClassType ct && "List".equals(ct.name()) && !ct.typeArguments().isEmpty()) {
                    elemType = ct.typeArguments().get(0);
                } else if (collType instanceof Type.ArrayType at) {
                    elemType = at.componentType();
                }
                forScope.define(new SymbolTable.LocalVariableSymbol(fis.varName(), elemType, 0));
                analyzeStatement(fis.body(), forScope, returnType);
            }
            case SwitchStmt ss -> {
                inferType(ss.expression(), scope);
                SymbolTable switchScope = scope.enterScope();
                for (SwitchCase sc : ss.cases()) {
                    inferType(sc.value(), scope);
                    SymbolTable caseScope = switchScope.enterScope();
                    analyzeBody(sc.body(), caseScope, returnType);
                }
                if (!ss.defaultBody().isEmpty()) {
                    SymbolTable defaultScope = switchScope.enterScope();
                    analyzeBody(ss.defaultBody(), defaultScope, returnType);
                }
            }
            case ExpressionStmt es -> {
                if (es.expression() != null) {
                    Type exprType = inferType(es.expression(), scope);
                    expressionTypes.put(es.expression(), exprType);
                }
            }
            case ThrowStmt ts -> {
                if (ts.expression() != null) inferType(ts.expression(), scope);
            }
            case SpawnStmt ss -> {
                if (ss.expression() != null) inferType(ss.expression(), scope);
            }
            case AssertStmt asrt -> {
                if (asrt.condition() != null) inferType(asrt.condition(), scope);
            }
            default -> {}
        }
    }

    Type inferType(ExpressionNode expr, SymbolTable scope) {
        Type cached = expressionTypes.get(expr);
        if (cached != null && !Type.isUnknown(cached)) return cached;
        Type result = inferTypeInternal(expr, scope);
        expressionTypes.put(expr, result);
        return result;
    }

    private Type inferTypeInternal(ExpressionNode expr, SymbolTable scope) {
        return switch (expr) {
            case LiteralExpr lit -> inferLiteralType(lit);
            case IdentifierExpr ie -> {
                SymbolTable.Symbol sym = scope.resolve(ie.name());
                if (sym != null) yield sym.type();
                if ("args".equals(ie.name()) && "main".equals(currentFunctionName)) {
                    yield new Type.ArrayType(BuiltinTypes.STRING);
                }
                if (currentClassName != null && !currentClassName.isEmpty()) {
                    SymbolTable.Symbol fieldSym = resolveInHierarchy(currentClassName, ie.name());
                    if (fieldSym != null) {
                        expressionTypes.put(ie, fieldSym.type());
                        yield fieldSym.type();
                    }
                }
                if (diagnostics != null && !"this".equals(ie.name()) && !"super".equals(ie.name())
                        && !"json".equals(ie.name()) && !"process".equals(ie.name())
                        && !KofWeb.isWebNamespace(ie.name())
                        && !KofConfig.isConfigNamespace(ie.name())
                        && !KofDb.isDbNamespace(ie.name())
                        && !KofOrm.isOrmNamespace(ie.name())
                        && !KofLog.isLogNamespace(ie.name())
                        && !KofSecurity.isSecurityNamespace(ie.name())
                        && !KofHttp.isHttpNamespace(ie.name())
                        && !KofMq.isMqNamespace(ie.name())
                        && !KofTime.isTimeNamespace(ie.name())
                        && !KofTetris.isTetrisNamespace(ie.name())
                        && !KofUi.isPalette(ie.name()) && !KofUi.isConstructor(ie.name())
                        && !"Theme".equals(ie.name())
                        && !knownClasses.containsKey(ie.name())) {
                    diagnostics.error("", 0, 0, 0,
                            "Undefined variable or type: '" + ie.name() + "'", "SEM011");
                }
                yield Type.UnknownType.UNKNOWN;
            }
            case AssignmentExpr ae -> {
                Type valueType = inferType(ae.value(), scope);
                Type targetType = Type.UnknownType.UNKNOWN;
                if (ae.target() instanceof IdentifierExpr ie) {
                    SymbolTable.Symbol sym = scope.resolve(ie.name());
                    if (sym != null) {
                        targetType = sym.type();
                        if (diagnostics != null && !Type.isUnknown(targetType) && !Type.isUnknown(valueType)
                                && !isAssignable(valueType, targetType)) {
                            diagnostics.error("", 0, 0, 0,
                                    "Type mismatch: cannot assign " + valueType + " to " + targetType, "SEM012");
                        }
                    }
                } else if (ae.target() instanceof FieldAccessExpr fa) {
                    targetType = inferType(fa, scope);
                }
                yield targetType;
            }
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
                Type accType = inferType(cursor, scope);
                for (int ci = chain.size() - 1; ci >= 0; ci--) {
                    BinaryExpr be = chain.get(ci);
                    Type rightType = inferType(be.right(), scope);
                    accType = inferBinaryResultType(be.operator(), accType, rightType);
                }
                yield accType;
            }
            case UnaryExpr ue -> {
                Type operandType = inferType(ue.operand(), scope);
                if ("!".equals(ue.operator())) yield Type.PrimitiveType.BOOL;
                yield operandType;
            }
            case MethodCallExpr mc -> {
                if (mc.receiver() == null && "listOf".equals(mc.methodName())) {
                    // listOf(...) keeps its element type: List<T> must survive
                    // the whole pipeline (for-in, get, method resolution).
                    Type elemType = Type.UnknownType.UNKNOWN;
                    if (!mc.typeArguments().isEmpty()) {
                        elemType = resolveType(mc.typeArguments().get(0), scope);
                    } else if (!mc.arguments().isEmpty()) {
                        elemType = inferType(mc.arguments().get(0), scope);
                    }
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield new Type.ClassType("kof", "List", List.of(elemType));
                }
                if (mc.receiver() == null && knownClasses.containsKey(mc.methodName())) {
                    // Implicit construction: ClassName(args) without `new`.
                    // User classes take precedence over builtin helpers with
                    // the same name (e.g. KofUi's Color).
                    SymbolTable.ClassSymbol ctorClass = knownClasses.get(mc.methodName());
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    SymbolTable.ConstructorSymbol ctor = SymbolTable.constructorFor(
                            ctorClass.members(), mc.arguments().size());
                    if (ctor != null) {
                        resolvedMethods.put(mc, new SymbolTable.MethodSymbol("<init>", mc.methodName(),
                                ctor.type(), ctor.parameterTypes(), ctor.accessFlags(), SymbolTable.DispatchKind.STATIC));
                    }
                    yield new Type.ClassType(ctorClass.packageName(), ctorClass.name(), List.of());
                }
                if ("println".equals(mc.methodName()) || "print".equals(mc.methodName())) {
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield Type.PrimitiveType.VOID;
                }
                if (mc.receiver() == null && "now".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                    yield Type.PrimitiveType.LONG;
                }
                if (mc.receiver() == null && "readLine".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                    yield BuiltinTypes.STRING;
                }
                if (mc.receiver() == null && KofWeb.isContextFunction(mc.methodName())
                        && KofWeb.contextCall(mc.methodName(), mc.arguments().size()) != null) {
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield BuiltinTypes.STRING;
                }
                if (mc.receiver() == null && "transaction".equals(mc.methodName())
                        && mc.arguments().size() == 1) {
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield Type.PrimitiveType.VOID;
                }
                if (mc.receiver() == null && "readFile".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    inferType(mc.arguments().get(0), scope);
                    yield BuiltinTypes.STRING;
                }
                if (mc.receiver() == null && "writeFile".equals(mc.methodName()) && mc.arguments().size() == 2) {
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield Type.PrimitiveType.INT;
                }
                if (mc.receiver() == null && KofIo.isConstructor(mc.methodName()) && mc.arguments().size() == 1) {
                    inferType(mc.arguments().get(0), scope);
                    yield KofIo.constructorType(mc.methodName());
                }
                if (mc.receiver() == null && "Color".equals(mc.methodName())
                        && (mc.arguments().size() == 1 || mc.arguments().size() == 3)) {
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield KofUi.COLOR;
                }
                if (mc.receiver() == null && "Window".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    inferType(mc.arguments().get(0), scope);
                    yield KofUi.WINDOW;
                }
                if (mc.receiver() == null && "Label".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    inferType(mc.arguments().get(0), scope);
                    yield KofUi.LABEL;
                }
                if (mc.receiver() == null && "Button".equals(mc.methodName())
                        && (mc.arguments().size() == 1 || mc.arguments().size() == 2)) {
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield KofUi.BUTTON;
                }
                if (mc.receiver() == null && "Input".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    inferType(mc.arguments().get(0), scope);
                    yield KofUi.INPUT;
                }
                if (mc.receiver() == null && ("Column".equals(mc.methodName()) || "Row".equals(mc.methodName()))
                        && mc.arguments().size() == 1) {
                    inferType(mc.arguments().get(0), scope);
                    yield "Column".equals(mc.methodName()) ? KofUi.COLUMN : KofUi.ROW;
                }
                if (mc.receiver() == null && "View".equals(mc.methodName()) && mc.arguments().size() == 1) {
                    inferType(mc.arguments().get(0), scope);
                    yield KofUi.VIEW;
                }
                if (mc.receiver() == null && "Style".equals(mc.methodName()) && mc.arguments().size() == 4) {
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield KofUi.STYLE;
                }
                if (mc.receiver() instanceof IdentifierExpr rid3 && KofUi.isConstructor(rid3.name())) {
                    KofUi.UiCall uiCall = KofUi.staticMethod(rid3.name(), mc.methodName(), mc.arguments().size());
                    if (uiCall != null) {
                        for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                        yield uiCall.returnType();
                    }
                }
                if (mc.receiver() != null) {
                    // Nome de CLASSE EXTERNA como receiver: Button.inflate(...)
                    // — resolve pelo classpath antes dos namespaces builtin
                    // (Button também é widget do kof.ui; o import decide)
                    if (mc.receiver() instanceof IdentifierExpr rid) {
                        Type q = qualifyViaImports(rid.name());
                        if (q == null && rid.name().contains(".")) {
                            q = qualifiedType(Type.of(rid.name()));
                        }
                        if (q instanceof Type.ClassType qt && isExternal(qt)) {
                            ExternalClasspath.MethodSignature sig = externalTypes.resolveMethod(
                                    qt.internalName(), mc.methodName(), mc.arguments().size());
                            if (sig != null) {
                                List<Type> params = new ArrayList<>();
                                for (String d : sig.parameterDescriptors()) {
                                    params.add(ExternalClasspath.typeFromDescriptor(d));
                                }
                                Type ret = ExternalClasspath.typeFromDescriptor(sig.returnDescriptor());
                                resolvedMethods.put(mc, new SymbolTable.MethodSymbol(mc.methodName(),
                                        qt.internalName(), ret, params, 1,
                                        SymbolTable.DispatchKind.STATIC));
                                yield ret;
                            }
                        }
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && "super".equals(rid.name())) {
                        // super.method(args): resolve against the superclass
                        // hierarchy of the enclosing class. The resolved symbol
                        // is intentionally NOT registered in resolvedMethods —
                        // lowering emits a non-virtual SUPER call.
                        String superName = "Object";
                        if (currentClassName != null) {
                            SymbolTable.ClassSymbol self = knownClasses.get(currentClassName);
                            if (self != null && self.superClass() != null && !"Object".equals(self.superClass())) {
                                superName = self.superClass();
                            }
                        }
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        SymbolTable.Symbol m = resolveInHierarchy(superName, mc.methodName());
                        if (m instanceof SymbolTable.MethodSymbol ms) {
                            checkArgTypes(mc.methodName(), argTypes, ms.parameterTypes());
                            yield ms.returnType();
                        }
                        yield Type.UnknownType.UNKNOWN;
                    }
                    Type recvType = inferType(mc.receiver(), scope);
                    if (mc.receiver() instanceof IdentifierExpr rid && !isLocalName(rid.name(), scope) && KofDb.isDbNamespace(rid.name())) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        boolean typed = KofDb.isQuery(mc.methodName()) && !mc.typeArguments().isEmpty();
                        KofDb.DbCall dbCall = KofDb.staticCall(mc.methodName(), argTypes, typed);
                        if (dbCall != null) {
                            if (typed && !mc.typeArguments().isEmpty()) {
                                yield new Type.ClassType("kof", "List",
                                        List.of(resolveType(mc.typeArguments().get(0), scope)));
                            }
                            yield dbCall.returnType();
                        }
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && !isLocalName(rid.name(), scope) && KofLog.isLogNamespace(rid.name())) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        KofLog.LogCall logCall = KofLog.staticCall(mc.methodName(), argTypes);
                        if (logCall != null) yield logCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && !isLocalName(rid.name(), scope) && KofOrm.isOrmNamespace(rid.name())) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
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
                                            List.of(resolveType(mc.typeArguments().get(0), scope)));
                                }
                                if ("find".equals(mc.methodName())) {
                                    yield resolveType(mc.typeArguments().get(0), scope);
                                }
                            }
                            yield ormCall.returnType();
                        }
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && "process".equals(rid.name())
                            && !isLocalName(rid.name(), scope)) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        KofProcess.ProcessCall procCall = KofProcess.runCall(argTypes);
                        if (procCall != null) yield procCall.returnType();
                        KofProcess.ProcessCall exitCall = KofProcess.exitCall(argTypes);
                        if (exitCall != null) yield exitCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && !isLocalName(rid.name(), scope) && KofConfig.isConfigNamespace(rid.name())) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        KofConfig.ConfigCall cfgCall = KofConfig.staticCall(mc.methodName(), argTypes);
                        if (cfgCall != null) yield cfgCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && !isLocalName(rid.name(), scope) && KofHttp.isHttpNamespace(rid.name())) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        KofHttp.HttpCall httpCall = KofHttp.staticCall(mc.methodName(), argTypes);
                        if (httpCall != null) yield httpCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && !isLocalName(rid.name(), scope) && KofMq.isMqNamespace(rid.name())) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        KofMq.MqCall mqCall = KofMq.staticCall(mc.methodName(), argTypes);
                        if (mqCall != null) yield mqCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && !isLocalName(rid.name(), scope) && KofTime.isTimeNamespace(rid.name())) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        KofTime.TimeCall timeCall = KofTime.staticCall(mc.methodName(), argTypes);
                        if (timeCall != null) yield timeCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && !isLocalName(rid.name(), scope) && KofSecurity.isSecurityNamespace(rid.name())) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        KofSecurity.SecCall secCall = KofSecurity.staticMethod(rid.name(), mc.methodName(), argTypes);
                        if (secCall != null) yield secCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && !isLocalName(rid.name(), scope) && KofTetris.isTetrisNamespace(rid.name())) {
                        KofTetris.TetrisCall tetrisCall = KofTetris.staticMethod(rid.name(), mc.methodName(),
                                mc.arguments().size());
                        if (tetrisCall != null) {
                            for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                            yield tetrisCall.returnType();
                        }
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (mc.receiver() instanceof IdentifierExpr rid && !isLocalName(rid.name(), scope) && KofWeb.isWebNamespace(rid.name())
                            && "app".equals(mc.methodName()) && mc.arguments().isEmpty()) {
                        yield KofWeb.APP;
                    }
                    if (KofWeb.isAppType(recvType)) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        KofWeb.WebCall webCall = KofWeb.instanceMethod(mc.methodName(), argTypes);
                        if (webCall != null) yield webCall.returnType();
                        yield Type.UnknownType.UNKNOWN;
                    }
                    if (recvType instanceof Type.FunctionType ft) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        checkArgTypes("function call", argTypes, ft.parameterTypes());
                        yield ft.returnType();
                    }
                    if (recvType instanceof Type.ClassType ct) {
                        SymbolTable.Symbol m = resolveInHierarchy(ct.name(), mc.methodName());
                        if (m instanceof SymbolTable.MethodSymbol ms) {
                            resolvedMethods.put(mc, ms);
                            List<Type> argTypes = new ArrayList<>();
                            for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                            checkArgTypes(mc.methodName(), argTypes, ms.parameterTypes());
                            yield ms.returnType();
                        }
                        // receiver de classe EXTERNA (android.* etc.): assinatura
                        // vem do classpath — sem isso o lowering emitiria
                        // invokevirtual com owner vazio
                        if (isExternal(ct)) {
                            ExternalClasspath.MethodSignature sig = externalTypes.resolveMethod(
                                    ct.internalName(), mc.methodName(), mc.arguments().size());
                            if (sig != null) {
                                List<Type> params = new ArrayList<>();
                                for (String d : sig.parameterDescriptors()) {
                                    params.add(ExternalClasspath.typeFromDescriptor(d));
                                }
                                Type ret = ExternalClasspath.typeFromDescriptor(sig.returnDescriptor());
                                resolvedMethods.put(mc, new SymbolTable.MethodSymbol(mc.methodName(),
                                        ct.internalName(), ret, params, 1,
                                        SymbolTable.DispatchKind.INSTANCE));
                                yield ret;
                            }
                        }
                    }
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() == null) {
                    SymbolTable.Symbol localSym = scope != null ? scope.resolve(mc.methodName()) : null;
                    if (localSym != null && localSym.type() instanceof Type.FunctionType lft) {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                        checkArgTypes(mc.methodName(), argTypes, lft.parameterTypes());
                        yield lft.returnType();
                    }
                    if (currentClassName != null && !currentClassName.isEmpty()) {
                        SymbolTable.Symbol m = resolveInHierarchy(currentClassName, mc.methodName());
                        if (m instanceof SymbolTable.MethodSymbol ms) {
                            List<Type> argTypes = new ArrayList<>();
                            for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                            checkArgTypes(mc.methodName(), argTypes, ms.parameterTypes());
                            resolvedMethods.put(mc, ms);
                            yield ms.returnType();
                        }
                        // chamada implícita (this) herdada de SUPERCLASSE
                        // EXTERNA: setContentView(...) dentro da Activity Kof
                        SymbolTable.ClassSymbol self = knownClasses.get(currentClassName);
                        String superName = self != null ? self.superClass() : null;
                        if (superName != null && externalTypes != null && !"Object".equals(superName)) {
                            String superInternal = superName.contains(".")
                                    ? superName.replace('.', '/') : superName;
                            ExternalClasspath.MethodSignature sig = externalTypes.resolveMethod(
                                    superInternal, mc.methodName(), mc.arguments().size());
                            if (sig != null) {
                                List<Type> params = new ArrayList<>();
                                for (String d : sig.parameterDescriptors()) {
                                    params.add(ExternalClasspath.typeFromDescriptor(d));
                                }
                                Type ret = ExternalClasspath.typeFromDescriptor(sig.returnDescriptor());
                                resolvedMethods.put(mc, new SymbolTable.MethodSymbol(mc.methodName(),
                                        superInternal, ret, params, 1,
                                        SymbolTable.DispatchKind.INSTANCE));
                                yield ret;
                            }
                        }
                    }
                }
                if (mc.receiver() == null
                        && ("super".equals(mc.methodName()) || "this".equals(mc.methodName()))) {
                    // super(args) / this(args): chamadas de construtor —
                    // válidas apenas dentro do corpo de um construtor
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield Type.PrimitiveType.VOID;
                }
                if (mc.receiver() == null && currentUnit != null
                        && !"println".equals(mc.methodName()) && !"print".equals(mc.methodName())
                        && !"listOf".equals(mc.methodName())
                        && !"now".equals(mc.methodName()) && !"readLine".equals(mc.methodName())
                        && !"readFile".equals(mc.methodName()) && !"writeFile".equals(mc.methodName())
                        && !"super".equals(mc.methodName())
                        && !KofIo.isConstructor(mc.methodName())
                        && !KofUi.isConstructor(mc.methodName())
                        && !KofWeb.isContextFunction(mc.methodName())
                        && !"transaction".equals(mc.methodName())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                    boolean found = false;
                    for (AstNode d : currentUnit.declarations()) {
                        if (d instanceof FunctionDeclarationNode fn && fn.name().equals(mc.methodName())) {
                            found = true;
                            boolean hasDefaults = fn.parameters().stream()
                                    .anyMatch(p -> p.defaultExpression() != null);
                            if (fn.typeParameters().isEmpty() && (!hasDefaults
                                    || mc.arguments().size() >= fn.parameters().size())) {
                                List<Type> paramTypes = new ArrayList<>();
                                for (FormalParameterNode p : fn.parameters()) paramTypes.add(resolveType(p.type(), scope));
                                checkArgTypes(mc.methodName(), argTypes, paramTypes);
                            }
                            break;
                        }
                    }
                    if (!found && diagnostics != null && !knownClasses.containsKey(mc.methodName())) {
                        diagnostics.error("", 0, 0, 0,
                                "Undefined function: '" + mc.methodName() + "'", "SEM015");
                    }
                }
                SymbolTable.ClassSymbol ctorClass = knownClasses.get(mc.methodName());
                if (ctorClass != null) {
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    SymbolTable.ConstructorSymbol ctor = SymbolTable.constructorFor(
                            ctorClass.members(), mc.arguments().size());
                    if (ctor != null) {
                        resolvedMethods.put(mc, new SymbolTable.MethodSymbol("<init>", mc.methodName(),
                                ctor.type(), ctor.parameterTypes(), ctor.accessFlags(), SymbolTable.DispatchKind.STATIC));
                    }
                    yield new Type.ClassType(ctorClass.packageName(), ctorClass.name(), List.of());
                }
                for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                yield Type.UnknownType.UNKNOWN;
            }
            case NewExpr ne -> {
                SymbolTable.ClassSymbol cs = knownClasses.get(ne.typeName());
                if (cs != null) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : ne.arguments()) {
                        argTypes.add(inferType(arg, scope));
                    }
                    SymbolTable.ConstructorSymbol ctor3 =
                            SymbolTable.constructorFor(cs.members(), ne.arguments().size());
                    if (ctor3 != null) {
                        resolvedConstructors.put(ne, ctor3);
                    }
                    yield new Type.ClassType(cs.packageName(), cs.name(), List.of());
                }
                // classe EXTERNA (android.webkit.WebView etc.): qualifica pelo
                // import e registra o construtor do classpath — sem isso a
                // variável fica Unknown e toda a cadeia de chamadas seguinte
                // perde o tipo
                String qname = ne.typeName();
                if (!qname.contains(".")) {
                    Type viaImport = qualifyViaImports(qname);
                    if (viaImport != null) qname = viaImport instanceof Type.ClassType qt
                            ? qt.packageName() + "." + qt.name() : qname;
                }
                if (qname.contains(".") && externalTypes != null) {
                    String internal = qname.replace('.', '/');
                    if (externalTypes.knows(internal)) {
                        ExternalClasspath.MethodSignature sig =
                                externalTypes.resolveConstructor(internal, ne.arguments().size());
                        if (sig != null) {
                            List<Type> params = new ArrayList<>();
                            for (String d : sig.parameterDescriptors()) {
                                params.add(ExternalClasspath.typeFromDescriptor(d));
                            }
                            resolvedConstructors.put(ne, new SymbolTable.ConstructorSymbol(
                                    internal.substring(internal.lastIndexOf('/') + 1), params, 1));
                        }
                        int lastDot = qname.lastIndexOf('.');
                        yield new Type.ClassType(qname.substring(0, lastDot),
                                qname.substring(lastDot + 1), List.of());
                    }
                }
                yield Type.UnknownType.UNKNOWN;
            }
            case FieldAccessExpr fa -> {
                if (fa.receiver() instanceof IdentifierExpr pId && KofUi.isPalette(pId.name())
                        && KofUi.paletteColor(fa.fieldName()) != null) {
                    yield KofUi.COLOR;
                }
                Type recvType = inferType(fa.receiver(), scope);
                if (KofProcess.isResult(recvType) && KofProcess.isField(fa.fieldName())) {
                    yield KofProcess.fieldType(fa.fieldName());
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
                if (recvType instanceof Type.ClassType ct) {
                    SymbolTable.Symbol field = resolveInHierarchy(ct.name(), fa.fieldName());
                    if (field != null) yield field.type();
                    if (isExternal(ct)) {
                        String desc = externalTypes.resolveFieldType(ct.internalName(), fa.fieldName());
                        if (desc != null) {
                            yield ExternalClasspath.typeFromDescriptor(desc);
                        }
                    }
                }
                yield Type.UnknownType.UNKNOWN;
            }
            case NewArrayExpr na -> {
                Type elemType = Type.of(na.elementType());
                inferType(na.size(), scope);
                yield new Type.ArrayType(elemType);
            }
            case ArrayAccessExpr aa -> {
                Type recvType = inferType(aa.receiver(), scope);
                inferType(aa.index(), scope);
                if (recvType instanceof Type.ArrayType at) {
                    yield at.componentType();
                }
                yield Type.UnknownType.UNKNOWN;
            }
            case LambdaExpr le -> {
                SymbolTable lambdaScope = scope.enterScope();
                List<Type> paramTypes = new ArrayList<>();
                int idx = 0;
                for (FormalParameterNode p : le.parameters()) {
                    Type paramType = resolveType(p.type(), scope);
                    paramTypes.add(paramType);
                    lambdaScope.define(new SymbolTable.ParameterSymbol(p.name(), paramType, idx));
                    idx++;
                }
                analyzeBody(le.body(), lambdaScope, Type.UnknownType.UNKNOWN);
                Type returnType = Type.UnknownType.UNKNOWN;
                for (StatementNode s : le.body()) {
                    if (s instanceof ReturnStmt rs && rs.value() != null) {
                        returnType = inferType(rs.value(), lambdaScope);
                        break;
                    }
                    if (s instanceof BlockStmt b) {
                        for (StatementNode inner : b.statements()) {
                            if (inner instanceof ReturnStmt rs2 && rs2.value() != null) {
                                returnType = inferType(rs2.value(), lambdaScope);
                                break;
                            }
                        }
                    }
                }
                yield new Type.FunctionType(paramTypes, returnType);
            }
            case IfExpr ie -> {
                Type thenType = inferType(ie.thenExpr(), scope);
                Type elseType = inferType(ie.elseExpr(), scope);
                if (thenType.equals(elseType)) yield thenType;
                if (thenType instanceof Type.PrimitiveType && elseType instanceof Type.PrimitiveType) {
                    yield thenType;
                }
                yield thenType;
            }
            default -> Type.UnknownType.UNKNOWN;
        };
    }

    private Type inferLiteralType(LiteralExpr lit) {
        return switch (lit.kind()) {
            case ConcreteLiteralKind.INT -> Type.PrimitiveType.INT;
            case ConcreteLiteralKind.LONG -> Type.PrimitiveType.LONG;
            case ConcreteLiteralKind.FLOAT -> Type.PrimitiveType.FLOAT;
            case ConcreteLiteralKind.DOUBLE -> Type.PrimitiveType.DOUBLE;
            case ConcreteLiteralKind.STRING -> BuiltinTypes.STRING;
            case ConcreteLiteralKind.BOOLEAN -> Type.PrimitiveType.BOOL;
            case ConcreteLiteralKind.CHAR -> Type.PrimitiveType.CHAR;
            case ConcreteLiteralKind.NULL -> Type.UnknownType.UNKNOWN;
        };
    }

    private Type inferBinaryResultType(String operator, Type left, Type right) {
        if ("==".equals(operator) || "!=".equals(operator) || "<".equals(operator) ||
                ">".equals(operator) || "<=".equals(operator) || ">=".equals(operator)) {
            return Type.PrimitiveType.BOOL;
        }

        if ("&&".equals(operator) || "||".equals(operator)) {
            return Type.PrimitiveType.BOOL;
        }
        if ("instanceof".equals(operator)) {
            return Type.PrimitiveType.BOOL;
        }
        if ("as".equals(operator)) {
            return right;
        }
        if ("!".equals(operator)) {
            return Type.PrimitiveType.BOOL;
        }
        if (Type.isString(left) || Type.isString(right)) {
            if ("+".equals(operator)) {
                return BuiltinTypes.STRING;
            }
            if (diagnostics != null) {
                diagnostics.error("", 0, 0, 0,
                        "Cannot apply '" + operator + "' to String and " + right, "SEM001");
            }
            return Type.UnknownType.UNKNOWN;
        }
        if (left instanceof Type.PrimitiveType lp && right instanceof Type.PrimitiveType rp) {
            if ("int".equals(lp.name())) {
                if ("long".equals(rp.name()) || "Long".equals(rp.name())) return Type.PrimitiveType.LONG;
                if ("float".equals(rp.name()) || "Float".equals(rp.name())) return Type.PrimitiveType.FLOAT;
                if ("double".equals(rp.name()) || "Double".equals(rp.name())) return Type.PrimitiveType.DOUBLE;
                return Type.PrimitiveType.INT;
            }
            if ("long".equals(lp.name()) || "Long".equals(lp.name())) {
                if ("float".equals(rp.name()) || "Float".equals(rp.name())) return Type.PrimitiveType.FLOAT;
                if ("double".equals(rp.name()) || "Double".equals(rp.name())) return Type.PrimitiveType.DOUBLE;
                return Type.PrimitiveType.LONG;
            }
            if ("float".equals(lp.name()) || "Float".equals(lp.name())) {
                if ("double".equals(rp.name()) || "Double".equals(rp.name())) return Type.PrimitiveType.DOUBLE;
                return Type.PrimitiveType.FLOAT;
            }
            if ("double".equals(lp.name()) || "Double".equals(lp.name())) {
                return Type.PrimitiveType.DOUBLE;
            }
                if ("bool".equals(lp.name()) || "bool".equals(rp.name())) {
                    if ("+".equals(operator) || "-".equals(operator) || "*".equals(operator) ||
                            "/".equals(operator) || "%".equals(operator)) {
                        if (diagnostics != null) {
                            diagnostics.error("", 0, 0, 0,
                                    "Cannot apply '" + operator + "' to boolean types. Use == or != for comparison.", "SEM002");
                        }
                        return Type.UnknownType.UNKNOWN;
                    }
                }
            return left;
        }
        if (left instanceof Type.ArrayType || right instanceof Type.ArrayType) {
            return Type.UnknownType.UNKNOWN;
        }
        if (left instanceof Type.UnknownType || right instanceof Type.UnknownType) {
            return Type.UnknownType.UNKNOWN;
        }
        return left;
    }

    private void checkArgTypes(String methodName, List<Type> argTypes, List<Type> paramTypes) {
        if (diagnostics == null || paramTypes.isEmpty() && !argTypes.isEmpty()) return;
        if (argTypes.size() != paramTypes.size()) {
            diagnostics.error("", 0, 0, 0,
                    "Wrong number of arguments for '" + methodName + "': expected "
                            + paramTypes.size() + " but got " + argTypes.size(), "SEM013");
            return;
        }
        for (int i = 0; i < argTypes.size(); i++) {
            if (!Type.isUnknown(argTypes.get(i)) && !Type.isUnknown(paramTypes.get(i))
                    && !isAssignable(argTypes.get(i), paramTypes.get(i))) {
                diagnostics.error("", 0, 0, 0,
                        "Argument " + (i + 1) + " of '" + methodName + "': expected "
                                + paramTypes.get(i) + " but got " + argTypes.get(i), "SEM014");
                return;
            }
        }
    }

    private boolean isAssignable(Type from, Type to) {
        if (from == null || to == null) return true;
        if (Type.isUnknown(from) || Type.isUnknown(to)) return true;
        if (from instanceof Type.TypeVariable || to instanceof Type.TypeVariable) return true;
        if (from.equals(to)) return true;
        if (from instanceof Type.PrimitiveType fp && to instanceof Type.PrimitiveType tp) {
            int fw = primitiveWidth(fp);
            int tw = primitiveWidth(tp);
            return fw <= tw;
        }
        if (from instanceof Type.FunctionType && to instanceof Type.ClassType) {
            // lambda → interface funcional externa (SAM conversion): a
            // compatibilidade real (aridade/tipos) é validada na emissão
            return true;
        }
        if (to instanceof Type.ClassType) {
            return from instanceof Type.ClassType;
        }
        return false;
    }

    private int primitiveWidth(Type.PrimitiveType pt) {
        return switch (pt.name()) {
            case "bool", "Bool" -> 0;
            case "char", "Char" -> 1;
            case "int", "Int", "byte", "short" -> 2;
            case "long", "Long" -> 3;
            case "float", "Float" -> 4;
            case "double", "Double" -> 5;
            default -> 2;
        };
    }

    private void resolveMethodCalls(CompilationUnitNode unit) {
        for (AstNode decl : unit.declarations()) {
            if (decl instanceof FunctionDeclarationNode func && func.body() != null) {
                for (StatementNode stmt : func.body()) resolveInStatement(stmt);
            } else if (decl instanceof ClassDeclarationNode cls) {
                for (AstNode member : cls.members()) {
                    if (member instanceof MethodDeclarationNode method && method.body() != null) {
                        for (StatementNode stmt : method.body()) resolveInStatement(stmt);
                    } else if (member instanceof ConstructorDeclarationNode ctor) {
                        for (StatementNode stmt : ctor.body()) resolveInStatement(stmt);
                    }
                }
            } else if (decl instanceof RecordDeclarationNode rec) {
                for (AstNode member : rec.members()) {
                    if (member instanceof MethodDeclarationNode method && method.body() != null) {
                        for (StatementNode stmt : method.body()) resolveInStatement(stmt);
                    }
                }
            }
        }
    }

    private void resolveInStatement(StatementNode stmt) {
        switch (stmt) {
            case BlockStmt block -> {
                for (StatementNode s : block.statements()) resolveInStatement(s);
            }
            case IfStmt ifStmt -> {
                resolveInStatement(ifStmt.thenBranch());
                if (ifStmt.elseBranch() != null) resolveInStatement(ifStmt.elseBranch());
            }
            case WhileStmt ws -> resolveInStatement(ws.body());
            case DoWhileStmt dws -> resolveInStatement(dws.body());
            case ForStmt fs -> {
                if (fs.init() != null) resolveInStatement(fs.init());
                if (fs.update() != null) resolveInExpression(fs.update());
                resolveInStatement(fs.body());
            }
            case ExpressionStmt es -> resolveInExpression(es.expression());
            case ReturnStmt ret -> {
                if (ret.value() != null) resolveInExpression(ret.value());
            }
            default -> {}
        }
    }

    private void resolveInExpression(ExpressionNode expr) {
        if (expr == null) return;
        switch (expr) {
            case MethodCallExpr mc -> {
                if (mc.receiver() != null) resolveInExpression(mc.receiver());
                for (ExpressionNode arg : mc.arguments()) resolveInExpression(arg);
            }
            case BinaryExpr bin -> {
                // iterate the left-associative chain (huge concat trees)
                ExpressionNode cur = bin;
                while (cur instanceof BinaryExpr be) {
                    resolveInExpression(be.right());
                    cur = be.left();
                }
                resolveInExpression(cur);
            }
            case UnaryExpr ue -> resolveInExpression(ue.operand());
            case AssignmentExpr ae -> {
                resolveInExpression(ae.target());
                resolveInExpression(ae.value());
            }
            case NewExpr ne -> {
                for (ExpressionNode arg : ne.arguments()) resolveInExpression(arg);
            }
            case FieldAccessExpr fa -> resolveInExpression(fa.receiver());
            default -> {}
        }
    }
}
