package dev.kof.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

class SemanticAnalyzer {

    private SymbolTable currentScope;
    private final Map<String, SymbolTable.ClassSymbol> knownClasses = new HashMap<>();
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
        for (AstNode decl : unit.declarations()) {
            preDeclareType(decl);
        }
        for (AstNode decl : unit.declarations()) {
            analyzeDeclaration(decl);
        }
        resolveMethodCalls(unit);
        resolveNewExpressions(unit);
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

    private void analyzeClass(ClassDeclarationNode cls) {
        String prevClass = currentClassName;
        currentClassName = cls.name();
        SymbolTable.ClassSymbol classSym = knownClasses.get(cls.name());
        SymbolTable classScope = classSym.members().enterScope();
        SymbolTable prevScope = currentScope;
        currentScope = classScope;
        for (AstNode member : cls.members()) {
            if (member instanceof FieldDeclarationNode field) {
                Type fieldType = Type.of(field.type());
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
                Type returnType = Type.of(method.returnType());
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
        Type returnType = Type.of(func.returnType());
        SymbolTable funcScope = currentScope.enterScope();
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
            Type paramType = Type.of(param.type());
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
        Type returnType = Type.of(method.returnType());
        List<Type> paramTypes = new ArrayList<>();
        SymbolTable methodScope = classScope.enterScope();
        methodScope.define(new SymbolTable.ParameterSymbol("this",
                new Type.ClassType(currentPackage, className, List.of()), 0));
        int idx = 1;
        for (FormalParameterNode param : method.parameters()) {
            Type paramType = Type.of(param.type());
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
                }
            }
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
            case ForStmt fs -> {
                SymbolTable forScope = scope.enterScope();
                if (fs.init() != null) analyzeStatement(fs.init(), forScope, returnType);
                if (fs.condition() != null) inferType(fs.condition(), forScope);
                analyzeStatement(fs.body(), forScope, returnType);
                if (fs.update() != null) inferType(fs.update(), forScope);
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
                yield Type.UnknownType.UNKNOWN;
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
            case AssignmentExpr ae -> {
                Type valueType = inferType(ae.value(), scope);
                if (ae.target() instanceof IdentifierExpr ie) {
                    SymbolTable.Symbol sym = scope.resolve(ie.name());
                    if (sym != null) {
                        expressionTypes.put(ae.target(), sym.type());
                        yield sym.type();
                    }
                }
                yield valueType;
            }
            case MethodCallExpr mc -> {
                if ("println".equals(mc.methodName()) || "print".equals(mc.methodName())) {
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    yield Type.PrimitiveType.VOID;
                }
                if (mc.receiver() != null) {
                    Type recvType = inferType(mc.receiver(), scope);
                    for (ExpressionNode arg : mc.arguments()) inferType(arg, scope);
                    if (recvType instanceof Type.ClassType ct) {
                        SymbolTable.ClassSymbol cs = knownClasses.get(ct.name());
                        if (cs != null) {
                            SymbolTable.Symbol m = cs.members().resolve(mc.methodName());
                            if (m instanceof SymbolTable.MethodSymbol ms) {
                                resolvedMethods.put(mc, ms);
                                yield ms.returnType();
                            }
                        }
                    }
                    yield Type.UnknownType.UNKNOWN;
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
                if (recvType instanceof Type.ClassType ct) {
                    SymbolTable.ClassSymbol cs = knownClasses.get(ct.name());
                    if (cs != null) {
                        SymbolTable.Symbol field = cs.members().resolve(fa.fieldName());
                        if (field != null) yield field.type();
                    }
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
            case ConcreteLiteralKind.STRING -> new Type.ClassType("java.lang", "String", List.of());
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
        if (left instanceof Type.PrimitiveType) return left;
        if (right instanceof Type.PrimitiveType) return right;
        return Type.UnknownType.UNKNOWN;
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

    private void resolveNewExpressions(CompilationUnitNode unit) {
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

    private boolean areTypesCompatible(Type target, Type source) {
        if (Type.isUnknown(target) || Type.isUnknown(source)) return true;
        if (target.equals(source)) return true;
        if (target instanceof Type.PrimitiveType t && source instanceof Type.PrimitiveType s) {
            return t.sort() >= s.sort();
        }
        return true;
    }
}
