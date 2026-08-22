package dev.kof.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

class SemanticAnalyzer {

    private SymbolTable currentScope;
    private CompilationUnitNode currentUnit;
    private final Map<String, SymbolTable.ClassSymbol> knownClasses = new HashMap<>();
    private final java.util.Set<String> interfaceNames = new java.util.HashSet<>();
    private final Map<ExpressionNode, Type> expressionTypes = new IdentityHashMap<>();
    private final Map<MethodCallExpr, SymbolTable.MethodSymbol> resolvedMethods = new IdentityHashMap<>();
    private final Map<NewExpr, SymbolTable.ConstructorSymbol> resolvedConstructors = new IdentityHashMap<>();
    private String currentClassName;
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
            SymbolTable.ClassSymbol sym = new SymbolTable.ClassSymbol(cls.name(), currentPackage,
                    cls.superClass() != null ? cls.superClass() : "Object",
                    cls.interfaces(), members);
            knownClasses.put(cls.name(), sym);
            currentScope.define(sym);
        } else if (decl instanceof RecordDeclarationNode rec) {
            SymbolTable members = new SymbolTable();
            SymbolTable.ClassSymbol sym = new SymbolTable.ClassSymbol(rec.name(), currentPackage,
                    "Record", rec.interfaces(), members);
            knownClasses.put(rec.name(), sym);
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
            case InterfaceDeclarationNode iface -> analyzeInterface(iface);
            case FunctionDeclarationNode func -> analyzeFunction(func);
            default -> {}
        }
    }

    private Type resolveType(String name, SymbolTable scope) {
        SymbolTable.Symbol sym = scope != null ? scope.resolve(name) : null;
        if (sym instanceof SymbolTable.TypeParameterSymbol) return sym.type();
        return Type.of(name);
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
                SymbolTable.FieldSymbol fs = new SymbolTable.FieldSymbol(field.name(), fieldType, 0, cls.name());
                classSym.members().define(fs);
                classScope.define(fs);
            }
        }
        for (AstNode member : cls.members()) {
            if (member instanceof ConstructorDeclarationNode ctor) {
                analyzeConstructor(ctor, cls.name(), classScope);
            } else if (member instanceof MethodDeclarationNode method) {
                analyzeMethod(method, cls.name(), classScope);
            }
        }
        currentScope = prevScope;
        currentClassName = prevClass;
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
                analyzeMethod(method, rec.name(), classScope);
            }
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
                classScope.define(new SymbolTable.MethodSymbol(method.name(), iface.name(),
                        returnType, paramTypes, 0, SymbolTable.DispatchKind.INSTANCE));
            }
        }
        currentScope = prevScope;
        currentClassName = prevClass;
    }

    private void analyzeFunction(FunctionDeclarationNode func) {
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

    private void analyzeConstructor(ConstructorDeclarationNode ctor, String className, SymbolTable classScope) {
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
        SymbolTable prevScope = currentScope;
        currentScope = ctorScope;
        analyzeBody(ctor.body(), ctorScope, Type.PrimitiveType.VOID);
        currentScope = prevScope;
    }

    private void analyzeMethod(MethodDeclarationNode method, String className, SymbolTable classScope) {
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
        if (method.body() != null && !method.body().isEmpty()) {
            SymbolTable prevScope = currentScope;
            currentScope = methodScope;
            analyzeBody(method.body(), methodScope, returnType);
            currentScope = prevScope;
        }
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
                    varType = Type.of(vds.type());
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
                if (currentClassName != null && !currentClassName.isEmpty()) {
                    SymbolTable.Symbol fieldSym = resolveInHierarchy(currentClassName, ie.name());
                    if (fieldSym != null) {
                        expressionTypes.put(ie, fieldSym.type());
                        yield fieldSym.type();
                    }
                }
                if (diagnostics != null && !"this".equals(ie.name()) && !"super".equals(ie.name())
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
                Type leftType = inferType(bin.left(), scope);
                Type rightType = inferType(bin.right(), scope);
                yield inferBinaryResultType(bin.operator(), leftType, rightType);
            }
            case UnaryExpr ue -> {
                Type operandType = inferType(ue.operand(), scope);
                if ("!".equals(ue.operator())) yield Type.PrimitiveType.BOOL;
                yield operandType;
            }
            case MethodCallExpr mc -> {
                if ("println".equals(mc.methodName()) || "print".equals(mc.methodName())) {
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield Type.PrimitiveType.VOID;
                }
                if (mc.receiver() != null) {
                    Type recvType = inferType(mc.receiver(), scope);
                    if (recvType instanceof Type.ClassType ct) {
                        SymbolTable.Symbol m = resolveInHierarchy(ct.name(), mc.methodName());
                        if (m instanceof SymbolTable.MethodSymbol ms) {
                            resolvedMethods.put(mc, ms);
                            List<Type> argTypes = new ArrayList<>();
                            for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                            checkArgTypes(mc.methodName(), argTypes, ms.parameterTypes());
                            yield ms.returnType();
                        }
                    }
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() == null && currentUnit != null
                        && !"println".equals(mc.methodName()) && !"print".equals(mc.methodName())
                        && !"super".equals(mc.methodName())) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(inferType(arg, scope));
                    boolean found = false;
                    for (AstNode d : currentUnit.declarations()) {
                        if (d instanceof FunctionDeclarationNode fn && fn.name().equals(mc.methodName())) {
                            found = true;
                            if (fn.typeParameters().isEmpty()) {
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
                    SymbolTable.Symbol ctorSym = ctorClass.members().resolve("<init>");
                    if (ctorSym instanceof SymbolTable.ConstructorSymbol ctor) {
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
                    SymbolTable.Symbol ctorSym = cs.members().resolve("<init>");
                    if (ctorSym instanceof SymbolTable.ConstructorSymbol ctor) {
                        resolvedConstructors.put(ne, ctor);
                    }
                    yield new Type.ClassType(cs.packageName(), cs.name(), List.of());
                }
                yield Type.UnknownType.UNKNOWN;
            }
            case FieldAccessExpr fa -> {
                Type recvType = inferType(fa.receiver(), scope);
                if (recvType instanceof Type.ArrayType at && "length".equals(fa.fieldName())) {
                    yield Type.PrimitiveType.INT;
                }
                if (Type.isString(recvType) && "length".equals(fa.fieldName())) {
                    yield Type.PrimitiveType.INT;
                }
                if (recvType instanceof Type.ClassType ct) {
                    SymbolTable.Symbol field = resolveInHierarchy(ct.name(), fa.fieldName());
                    if (field != null) yield field.type();
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
            case LambdaExpr le -> Type.UnknownType.UNKNOWN;
            case IfExpr ie -> Type.UnknownType.UNKNOWN;
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
                resolveInExpression(bin.left());
                resolveInExpression(bin.right());
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
