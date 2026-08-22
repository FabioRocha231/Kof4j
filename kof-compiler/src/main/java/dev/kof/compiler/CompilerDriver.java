package dev.kof.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CompilerDriver {

    private IRModule currentModule;
    private CompilationUnitNode currentUnit;
    private SemanticAnalyzer semanticAnalyzer;

    public CompilationResult compile(Path sourceFile, Path outputDir) {
        return compile(sourceFile, outputDir, Target.JVM);
    }

    public CompilationResult compile(Path sourceFile, Path outputDir, Target target) {
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        try {
            String source = Files.readString(sourceFile);
            String fileName = sourceFile.getFileName().toString();
            Lexer lexer = new Lexer(source, fileName, diagnostics);
            List<Token> tokens = lexer.tokenize();
            if (diagnostics.hasErrors()) {
                return new CompilationResult(false, diagnostics, outputDir);
            }
            Parser parser = new Parser(tokens, diagnostics, fileName);
            CompilationUnitNode unit = parser.parse();
            if (diagnostics.hasErrors()) {
                return new CompilationResult(false, diagnostics, outputDir);
            }
            semanticAnalyzer = new SemanticAnalyzer();
            semanticAnalyzer.analyze(unit, diagnostics);
            if (diagnostics.hasErrors()) {
                return new CompilationResult(false, diagnostics, outputDir);
            }
            LabelId.reset();
            currentModule = new IRModule("", List.of(), List.of());
            currentUnit = unit;
            IRModule irModule = lowerToIR(unit, diagnostics);
            if (diagnostics.hasErrors()) {
                return new CompilationResult(false, diagnostics, outputDir);
            }
            currentModule = irModule;
            Files.createDirectories(outputDir);
            Backend backend = selectBackend(target);
            backend.emit(irModule, outputDir);
            return new CompilationResult(true, diagnostics, outputDir);
        } catch (IOException e) {
            diagnostics.error(sourceFile.toString(), 0, 0, 0, "Error reading source file: " + e.getMessage(), "COMP001");
            return new CompilationResult(false, diagnostics, outputDir);
        } catch (Exception e) {
            e.printStackTrace();
            diagnostics.error(sourceFile.toString(), 0, 0, 0, "Internal compiler error: " + e.getMessage(), "COMP002");
            return new CompilationResult(false, diagnostics, outputDir);
        }
    }

    private Backend selectBackend(Target target) {
        return switch (target) {
            case JVM -> new JvmBackend();
            case NATIVE -> new NativeBackend();
        };
    }

    private Type toType(String typeName) {
        return Type.of(typeName);
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

    private IRModule lowerToIR(CompilationUnitNode unit, DiagnosticCollector diagnostics) {
        List<String> imports = new ArrayList<>(unit.imports());
        List<IRClass> classes = new ArrayList<>();
        List<IRMethod> topLevelFunctions = new ArrayList<>();
        String moduleName = unit.packageName().isEmpty() ? "Default" : unit.packageName().replace('.', '/');
        for (AstNode decl : unit.declarations()) {
            if (decl instanceof ClassDeclarationNode cls) classes.add(lowerClass(cls, unit.packageName()));
            else if (decl instanceof InterfaceDeclarationNode iface) classes.add(lowerInterface(iface, unit.packageName()));
            else if (decl instanceof RecordDeclarationNode rec) classes.add(lowerRecord(rec, unit.packageName()));
            else if (decl instanceof FunctionDeclarationNode func) topLevelFunctions.add(lowerFunction(func));
        }
        if (!topLevelFunctions.isEmpty()) {
            String mainClassName = moduleName.isEmpty() ? "Main" : moduleName + "/Main";
            classes.add(0, new IRClass(mainClassName, "java/lang/Object", List.of(),
                    AccessFlags.PUBLIC | AccessFlags.SUPER, List.of(), topLevelFunctions, List.of(), null));
        }
        return new IRModule(moduleName, classes, imports);
    }

    private IRMethod lowerFunction(FunctionDeclarationNode func) {
        Type returnType = toType(func.returnType());
        if (Type.isVoid(returnType) && func.body().size() == 1 && func.body().getFirst() instanceof ReturnStmt ret && ret.value() != null) {
            List<IRLocalVariable> tmpLocals = new ArrayList<>();
            int tmpIdx = 0;
            for (FormalParameterNode p : func.parameters()) {
                tmpLocals.add(new IRLocalVariable(tmpIdx, p.name(), toType(p.type())));
                tmpIdx++;
            }
            returnType = inferExprType(ret.value(), tmpLocals);
        }
        List<Type> paramTypes = func.parameters().stream().map(p -> toType(p.type())).toList();
        if ("main".equals(func.name()) && paramTypes.isEmpty()) {
            paramTypes = List.of(new Type.ArrayType(BuiltinTypes.STRING));
        }
        int access = AccessFlags.PUBLIC | AccessFlags.STATIC;
        List<IRLocalVariable> locals = new ArrayList<>();
        List<KofOperation> body = new ArrayList<>();
        int localIdx = 0;
        for (FormalParameterNode p : func.parameters()) {
            locals.add(new IRLocalVariable(localIdx, p.name(), toType(p.type())));
            localIdx++;
        }
        for (StatementNode stmt : func.body()) {
            localIdx = emitStatement(stmt, body, "", localIdx, locals, returnType);
        }
        KofOperation last = body.isEmpty() ? null : body.get(body.size() - 1);
        if (last == null || !(last instanceof KofReturn || last instanceof KofReturnVoid)) {
            if (Type.isVoid(returnType)) body.add(new KofReturnVoid());
            else body.add(new KofReturn(returnType));
        }
        return new IRMethod(func.name(), returnType, paramTypes, access, func.thrownExceptions(),
                List.of(new IRBasicBlock(0, body)), locals);
    }

    private int emitStatement(StatementNode stmt, List<KofOperation> ops, String owner, int localIdx,
                              List<IRLocalVariable> locals, Type returnType) {
        return switch (stmt) {
            case ReturnStmt ret -> {
                if (ret.value() != null) {
                    localIdx = emitExpression(ret.value(), ops, owner, localIdx, locals);
                    ops.add(new KofReturn(returnType));
                } else {
                    ops.add(new KofReturnVoid());
                }
                yield localIdx;
            }
            case ExpressionStmt es -> {
                if (es.expression() != null) {
                    localIdx = emitExpression(es.expression(), ops, owner, localIdx, locals);
                    if (hasReturnValue(es.expression())) ops.add(new KofPop());
                }
                yield localIdx;
            }
            case VarDeclStmt vds -> {
                Type varType = toType(vds.type());
                if (vds.initializer() != null) {
                    localIdx = emitExpression(vds.initializer(), ops, owner, localIdx, locals);
                    if ("var".equals(vds.type()) || "val".equals(vds.type())) {
                        varType = inferExprType(vds.initializer(), locals);
                    }
                }
                ops.add(new KofStoreLocal(varType, localIdx));
                locals.add(new IRLocalVariable(localIdx, vds.name(), varType));
                yield localIdx + 1;
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
                if (ifStmt.condition() instanceof BinaryExpr bin && isComparisonOp(bin.operator())) {
                    localIdx = emitExpression(bin.left(), ops, owner, localIdx, locals);
                    localIdx = emitExpression(bin.right(), ops, owner, localIdx, locals);
                    ops.add(new KofConditionalJump(invertComparison(bin.operator()), elseLabel, endLabel));
                } else {
                    localIdx = emitExpression(ifStmt.condition(), ops, owner, localIdx, locals);
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                    ops.add(new KofConditionalJump(KofComparison.EQ, elseLabel, endLabel));
                }
                localIdx = emitStatement(ifStmt.thenBranch(), ops, owner, localIdx, locals, returnType);
                if (ifStmt.elseBranch() != null) ops.add(new KofJump(endLabel));
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
                ops.add(new KofLabel(startLabel));
                if (ws.condition() instanceof BinaryExpr bin && isComparisonOp(bin.operator())) {
                    localIdx = emitExpression(bin.left(), ops, owner, localIdx, locals);
                    localIdx = emitExpression(bin.right(), ops, owner, localIdx, locals);
                    ops.add(new KofConditionalJump(invertComparison(bin.operator()), endLabel, startLabel));
                } else {
                    localIdx = emitExpression(ws.condition(), ops, owner, localIdx, locals);
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                    ops.add(new KofConditionalJump(KofComparison.EQ, endLabel, startLabel));
                }
                localIdx = emitStatement(ws.body(), ops, owner, localIdx, locals, returnType);
                ops.add(new KofJump(startLabel));
                ops.add(new KofLabel(endLabel));
                yield localIdx;
            }
            case DoWhileStmt dws -> {
                LabelId startLabel = LabelId.create();
                LabelId endLabel = LabelId.create();
                ops.add(new KofLabel(startLabel));
                localIdx = emitStatement(dws.body(), ops, owner, localIdx, locals, returnType);
                if (dws.condition() instanceof BinaryExpr bin && isComparisonOp(bin.operator())) {
                    localIdx = emitExpression(bin.left(), ops, owner, localIdx, locals);
                    localIdx = emitExpression(bin.right(), ops, owner, localIdx, locals);
                    ops.add(new KofConditionalJump(
                            switch (bin.operator()) {
                                case ">" -> KofComparison.GT;
                                case "<" -> KofComparison.LT;
                                case ">=" -> KofComparison.GE;
                                case "<=" -> KofComparison.LE;
                                case "==" -> KofComparison.EQ;
                                case "!=" -> KofComparison.NE;
                                default -> KofComparison.NE;
                            }, startLabel, endLabel));
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
                if (fs.init() != null) localIdx = emitStatement(fs.init(), ops, owner, localIdx, locals, returnType);
                ops.add(new KofLabel(startLabel));
                if (fs.condition() != null) {
                    if (fs.condition() instanceof BinaryExpr bin && isComparisonOp(bin.operator())) {
                        localIdx = emitExpression(bin.left(), ops, owner, localIdx, locals);
                        localIdx = emitExpression(bin.right(), ops, owner, localIdx, locals);
                        ops.add(new KofConditionalJump(invertComparison(bin.operator()), endLabel, startLabel));
                    } else {
                        localIdx = emitExpression(fs.condition(), ops, owner, localIdx, locals);
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                        ops.add(new KofConditionalJump(KofComparison.EQ, endLabel, startLabel));
                    }
                }
                localIdx = emitStatement(fs.body(), ops, owner, localIdx, locals, returnType);
                if (fs.update() != null) {
                    if (fs.update() instanceof UnaryExpr ue && "++".equals(ue.operator()) && ue.operand() instanceof IdentifierExpr id) {
                        int idx = findLocalIndex(id.name(), locals);
                        Type varType = locals.get(idx).type();
                        ops.add(new KofLoadLocal(varType, idx));
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
                        ops.add(new KofBinary(KofBinaryOp.ADD, varType));
                        ops.add(new KofStoreLocal(varType, idx));
                    } else if (fs.update() instanceof UnaryExpr ue2 && "--".equals(ue2.operator()) && ue2.operand() instanceof IdentifierExpr id2) {
                        int idx = findLocalIndex(id2.name(), locals);
                        Type varType = locals.get(idx).type();
                        ops.add(new KofLoadLocal(varType, idx));
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
                        ops.add(new KofBinary(KofBinaryOp.SUB, varType));
                        ops.add(new KofStoreLocal(varType, idx));
                    } else {
                        localIdx = emitExpression(fs.update(), ops, owner, localIdx, locals);
                        if (hasReturnValue(fs.update())) ops.add(new KofPop());
                    }
                }
                ops.add(new KofJump(startLabel));
                ops.add(new KofLabel(endLabel));
                yield localIdx;
            }
            case ThrowStmt ts -> {
                localIdx = emitExpression(ts.expression(), ops, owner, localIdx, locals);
                ops.add(new KofThrow());
                yield localIdx;
            }
            case TryStmt ts -> {
                for (StatementNode s : ts.tryBody()) {
                    localIdx = emitStatement(s, ops, owner, localIdx, locals, returnType);
                }
                for (CatchClause cc : ts.catchClauses()) {
                    localIdx = emitStatement(new BlockStmt(cc.position(), cc.body()), ops, owner, localIdx, locals, returnType);
                }
                if (!ts.finallyBody().isEmpty()) {
                    for (StatementNode s : ts.finallyBody()) {
                        localIdx = emitStatement(s, ops, owner, localIdx, locals, returnType);
                    }
                }
                yield localIdx;
            }
            case SwitchStmt ss -> {
                LabelId endLabel = LabelId.create();
                localIdx = emitExpression(ss.expression(), ops, owner, localIdx, locals);
                List<LabelId> caseLabels = new ArrayList<>();
                for (int i = 0; i < ss.cases().size(); i++) {
                    caseLabels.add(LabelId.create());
                }
                for (int i = 0; i < ss.cases().size(); i++) {
                    SwitchCase sc = ss.cases().get(i);
                    localIdx = emitExpression(sc.value(), ops, owner, localIdx, locals);
                    ops.add(new KofLoadLocal(Type.UnknownType.UNKNOWN, 0));
                    ops.add(new KofBinary(KofBinaryOp.SUB, Type.PrimitiveType.INT));
                    ops.add(new KofConditionalJump(KofComparison.EQ, caseLabels.get(i), endLabel));
                }
                for (int i = 0; i < ss.cases().size(); i++) {
                    SwitchCase sc = ss.cases().get(i);
                    ops.add(new KofLabel(caseLabels.get(i)));
                    localIdx = emitStatement(new BlockStmt(sc.position(), sc.body()), ops, owner, localIdx, locals, returnType);
                }
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
                    case ConcreteLiteralKind.INT -> ops.add(KofLoadLiteral.ofInt(Integer.parseInt(lit.value())));
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
                for (int i = locals.size() - 1; i >= 0; i--) {
                    if (locals.get(i).name().equals(ie.name())) {
                        ops.add(new KofLoadLocal(locals.get(i).type(), locals.get(i).index()));
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
                        }
                    }
                }
                ops.add(new KofLoadLocal(Type.UnknownType.UNKNOWN, 0));
                yield localIdx;
            }
            case BinaryExpr bin -> {
                localIdx = emitExpression(bin.left(), ops, owner, localIdx, locals);
                localIdx = emitExpression(bin.right(), ops, owner, localIdx, locals);
                Type leftType = inferExprType(bin.left(), locals);
                Type rightType = inferExprType(bin.right(), locals);
                if ("instanceof".equals(bin.operator())) {
                    ops.add(new KofInstanceOf(rightType));
                } else if ("as".equals(bin.operator())) {
                    ops.add(new KofCheckCast(rightType));
                } else if ("+".equals(bin.operator()) && (Type.isString(leftType) || Type.isString(rightType))) {
                    ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                            List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                            BuiltinTypes.STRING, KofCallKind.FUNCTION));
                } else {
                    Type operandType = leftType;
                    switch (bin.operator()) {
                        case "+" -> ops.add(new KofBinary(KofBinaryOp.ADD, operandType));
                        case "-" -> ops.add(new KofBinary(KofBinaryOp.SUB, operandType));
                        case "*" -> ops.add(new KofBinary(KofBinaryOp.MUL, operandType));
                        case "/" -> ops.add(new KofBinary(KofBinaryOp.DIV, operandType));
                        case "%" -> ops.add(new KofBinary(KofBinaryOp.MOD, operandType));
                        default -> ops.add(new KofBinary(KofBinaryOp.ADD, operandType));
                    }
                }
                yield localIdx;
            }
            case UnaryExpr ue -> {
                localIdx = emitExpression(ue.operand(), ops, owner, localIdx, locals);
                Type operandType = inferExprType(ue.operand(), locals);
                if ("-".equals(ue.operator())) {
                    ops.add(new KofUnary(KofUnaryOp.NEG, operandType));
                } else if ("++".equals(ue.operator())) {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
                    ops.add(new KofBinary(KofBinaryOp.ADD, operandType));
                } else if ("--".equals(ue.operator())) {
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
                    ops.add(new KofBinary(KofBinaryOp.SUB, operandType));
                }
                yield localIdx;
            }
            case MethodCallExpr mc -> {
                if (("print".equals(mc.methodName()) || "println".equals(mc.methodName())) && mc.arguments().size() == 1) {
                    ops.add(new KofGetStatic(
                            new Type.ClassType("java.lang", "System", List.of()),
                            "out", new Type.ClassType("java.io", "PrintStream", List.of())));
                    localIdx = emitExpression(mc.arguments().get(0), ops, owner, localIdx, locals);
                    Type argType = inferExprType(mc.arguments().get(0), locals);
                    if (isPrimitiveType(argType)) {
                        boxPrimitive(ops, argType);
                    }
                    ops.add(new KofCall(
                            BuiltinTypes.STRING,
                            "valueOf", List.of(Type.UnknownType.UNKNOWN),
                            BuiltinTypes.STRING, KofCallKind.STATIC));
                    ops.add(new KofCall(
                            new Type.ClassType("java.io", "PrintStream", List.of()),
                            mc.methodName(), List.of(BuiltinTypes.STRING),
                            Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
                } else if (mc.receiver() != null) {
                    localIdx = emitExpression(mc.receiver(), ops, owner, localIdx, locals);
                    Type recvType = inferExprType(mc.receiver(), locals);
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
                    }
                    for (ExpressionNode arg : mc.arguments()) {
                        localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                    }
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
                    ops.add(new KofCall(recvType, mc.methodName(), methodParamTypes, methodReturnType, callKind));
                } else {
                    if ("super".equals(mc.methodName()) && semanticAnalyzer != null) {
                        String superName = findSuperClass(owner);
                        if (superName != null) {
                            Type superType = ownerTypeFromInternal(superName);
                            SymbolTable.ClassSymbol superCs = semanticAnalyzer.getClass(superName.substring(superName.lastIndexOf('/') + 1));
                            SymbolTable.ConstructorSymbol ctor = null;
                            if (superCs != null) {
                                SymbolTable.Symbol ctorSym = superCs.members().resolve("<init>");
                                if (ctorSym instanceof SymbolTable.ConstructorSymbol c) ctor = c;
                            }
                            List<Type> argTypes = new ArrayList<>();
                            for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                            ops.add(new KofLoadLocal(ownerTypeFromInternal(owner), 0));
                            for (ExpressionNode arg : mc.arguments()) localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                            List<Type> ctorParamTypes = ctor != null ? ctor.parameterTypes() : argTypes;
                            ops.add(new KofCall(superType, "<init>", ctorParamTypes, Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                            yield localIdx;
                        }
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
                        for (ExpressionNode arg : mc.arguments()) localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                        if (ctor != null) {
                            ops.add(new KofCall(cs.type(), "<init>", ctor.parameterTypes(), Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                        }
                    } else {
                        List<Type> argTypes = new ArrayList<>();
                        for (ExpressionNode arg : mc.arguments()) argTypes.add(inferExprType(arg, locals));
                        Type returnType = Type.UnknownType.UNKNOWN;
                        if (currentUnit != null) {
                            for (AstNode d : currentUnit.declarations()) {
                                if (d instanceof FunctionDeclarationNode fn && fn.name().equals(mc.methodName())) {
                                    returnType = toType(fn.returnType());
                                    argTypes = fn.parameters().stream().map(p -> toType(p.type())).toList();
                                    break;
                                }
                            }
                        }
                        for (ExpressionNode arg : mc.arguments()) localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                        ops.add(new KofCall(Type.UnknownType.UNKNOWN, mc.methodName(), argTypes, returnType, KofCallKind.FUNCTION));
                    }
                }
                yield localIdx;
            }
            case AssignmentExpr ae -> {
                if (ae.target() instanceof FieldAccessExpr fa) {
                    localIdx = emitExpression(fa.receiver(), ops, owner, localIdx, locals);
                    localIdx = emitExpression(ae.value(), ops, owner, localIdx, locals);
                    Type recvType = inferExprType(fa.receiver(), locals);
                    Type fieldType = Type.UnknownType.UNKNOWN;
                    if (recvType instanceof Type.ClassType ct) {
                        SymbolTable.Symbol fs = resolveFieldInHierarchy(ct.name(), fa.fieldName());
                        if (fs != null) fieldType = fs.type();
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
                localIdx = emitExpression(ae.value(), ops, owner, localIdx, locals);
                if (ae.target() instanceof IdentifierExpr ie) {
                    for (int i = locals.size() - 1; i >= 0; i--) {
                        if (locals.get(i).name().equals(ie.name())) {
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
                List<Type> argTypes = new ArrayList<>();
                for (ExpressionNode arg : ne.arguments()) argTypes.add(inferExprType(arg, locals));
                SymbolTable.ConstructorSymbol resolvedCtor = semanticAnalyzer.getResolvedConstructor(ne);
                ops.add(new KofNewObject(type, argTypes));
                ops.add(new KofDup());
                for (ExpressionNode arg : ne.arguments()) localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                List<Type> ctorParamTypes = resolvedCtor != null ? resolvedCtor.parameterTypes() : argTypes;
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
                localIdx = emitExpression(fa.receiver(), ops, owner, localIdx, locals);
                Type recvType = inferExprType(fa.receiver(), locals);
                if (recvType instanceof Type.ArrayType && "length".equals(fa.fieldName())) {
                    ops.add(new KofArrayLength());
                } else if (Type.isString(recvType) && "length".equals(fa.fieldName())) {
                    ops.add(new KofLoadField(recvType, fa.fieldName(), Type.PrimitiveType.INT));
                } else {
                    Type fieldType = Type.UnknownType.UNKNOWN;
                    if (recvType instanceof Type.ClassType ct && semanticAnalyzer != null) {
                        SymbolTable.Symbol fs = resolveFieldInHierarchy(ct.name(), fa.fieldName());
                        if (fs != null) fieldType = fs.type();
                    }
                    ops.add(new KofLoadField(recvType, fa.fieldName(), fieldType));
                }
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
                for (int i = locals.size() - 1; i >= 0; i--) {
                    if (locals.get(i).name().equals(ie.name())) yield locals.get(i).type();
                }
                if (semanticAnalyzer != null) {
                    SymbolTable.Symbol sym = resolveFromSemantic(ie.name());
                    if (sym != null) yield sym.type();
                }
                yield Type.UnknownType.UNKNOWN;
            }
            case BinaryExpr bin -> {
                Type leftType = inferExprType(bin.left(), locals);
                Type rightType = inferExprType(bin.right(), locals);
                if ("+".equals(bin.operator()) && (Type.isString(leftType) || Type.isString(rightType))) {
                    yield BuiltinTypes.STRING;
                }
                yield leftType;
            }
            case MethodCallExpr mc -> {
                if ("println".equals(mc.methodName()) || "print".equals(mc.methodName())) yield Type.PrimitiveType.VOID;
                SymbolTable.MethodSymbol resolvedMethod = semanticAnalyzer.getResolvedMethod(mc);
                if (resolvedMethod != null) yield resolvedMethod.returnType();
                SymbolTable.ClassSymbol cs = semanticAnalyzer != null ? semanticAnalyzer.getClass(mc.methodName()) : null;
                if (cs != null) yield cs.type();
                yield Type.UnknownType.UNKNOWN;
            }
            case NewArrayExpr na -> {
                Type elemType = toType(na.elementType());
                yield new Type.ArrayType(elemType);
            }
            case NewExpr ne -> toType(ne.typeName());
            case ArrayAccessExpr aa -> {
                Type recvType = inferExprType(aa.receiver(), locals);
                if (recvType instanceof Type.ArrayType at) yield at.componentType();
                yield Type.UnknownType.UNKNOWN;
            }
            case FieldAccessExpr fa -> {
                Type recvType = inferExprType(fa.receiver(), locals);
                if (recvType instanceof Type.ArrayType at && "length".equals(fa.fieldName())) {
                    yield Type.PrimitiveType.INT;
                }
                if (Type.isString(recvType) && "length".equals(fa.fieldName())) {
                    yield Type.PrimitiveType.INT;
                }
                yield Type.UnknownType.UNKNOWN;
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

    private boolean isPrimitiveType(Type type) {
        return type instanceof Type.PrimitiveType pt && !"void".equals(pt.name());
    }

    private void boxPrimitive(List<KofOperation> ops, Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            Type boxed = switch (pt.name()) {
                case "int", "Int" -> new Type.ClassType("java.lang", "Integer", List.of());
                case "long", "Long" -> new Type.ClassType("java.lang", "Long", List.of());
                case "float", "Float" -> new Type.ClassType("java.lang", "Float", List.of());
                case "double", "Double" -> new Type.ClassType("java.lang", "Double", List.of());
                case "boolean", "bool", "Bool" -> new Type.ClassType("java.lang", "Boolean", List.of());
                default -> Type.UnknownType.UNKNOWN;
            };
            ops.add(new KofCall(boxed, "valueOf", List.of(type), boxed, KofCallKind.STATIC));
        }
    }

    private int findLocalIndex(String name, List<IRLocalVariable> locals) {
        for (int i = locals.size() - 1; i >= 0; i--) {
            if (locals.get(i).name().equals(name)) return locals.get(i).index();
        }
        return 0;
    }

    private boolean isComparisonOp(String op) {
        return ">".equals(op) || "<".equals(op) || ">=".equals(op) || "<=".equals(op) || "==".equals(op) || "!=".equals(op);
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

    private boolean hasReturnValue(ExpressionNode expr) {
        if (expr instanceof AssignmentExpr) return false;
        if (expr instanceof MethodCallExpr mc) {
            if ("print".equals(mc.methodName()) || "println".equals(mc.methodName())) return false;
        }
        return true;
    }

    private IRClass lowerClass(ClassDeclarationNode cls, String packageName) {
        String internalName = toInternalName(packageName, cls.name());
        String superName = cls.superClass() != null ? toInternalName("", cls.superClass()) : "java/lang/Object";
        List<String> ifaces = cls.interfaces().stream().map(i -> toInternalName("", i)).toList();
        int access = computeAccess(cls.modifiers());
        List<IRField> fields = new ArrayList<>();
        List<IRMethod> methods = new ArrayList<>();
        for (AstNode member : cls.members()) {
            if (member instanceof FieldDeclarationNode field) fields.add(lowerField(field));
            else if (member instanceof MethodDeclarationNode method) methods.add(lowerMethod(method, internalName, false));
            else if (member instanceof ConstructorDeclarationNode ctor) methods.add(lowerConstructor(ctor, internalName, superName));
        }
        if (!methods.stream().anyMatch(m -> m.name().equals("<init>"))) methods.add(0, generateDefaultConstructor(internalName, superName));
        return new IRClass(internalName, superName, ifaces, access, fields, methods, List.of(), null);
    }

    private IRClass lowerInterface(InterfaceDeclarationNode iface, String packageName) {
        String internalName = toInternalName(packageName, iface.name());
        List<String> ifaces = iface.interfaces().stream().map(i -> toInternalName("", i)).toList();
        int access = computeAccess(iface.modifiers()) | AccessFlags.ABSTRACT;
        List<IRMethod> methods = new ArrayList<>();
        List<IRField> fields = new ArrayList<>();
        for (AstNode member : iface.members()) {
            if (member instanceof MethodDeclarationNode method) methods.add(lowerMethod(method, internalName, true));
            else if (member instanceof FieldDeclarationNode field) fields.add(lowerField(field));
        }
        return new IRClass(internalName, "java/lang/Object", ifaces, access, fields, methods, List.of(), null);
    }

    private IRClass lowerRecord(RecordDeclarationNode rec, String packageName) {
        String internalName = toInternalName(packageName, rec.name());
        String superName = "java/lang/Record";
        List<String> ifaces = rec.interfaces().stream().map(i -> toInternalName("", i)).toList();
        int access = computeAccess(rec.modifiers()) | AccessFlags.FINAL | AccessFlags.PUBLIC;
        List<IRField> fields = new ArrayList<>();
        List<IRMethod> methods = new ArrayList<>();
        for (RecordComponentNode comp : rec.components()) {
            fields.add(new IRField(comp.name(), toType(comp.type()), AccessFlags.PRIVATE | AccessFlags.FINAL, null));
        }
        methods.add(0, generateRecordConstructor(rec, internalName));
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
        return new IRClass(internalName, superName, ifaces, access, fields, methods, List.of(), null);
    }

    private IRField lowerField(FieldDeclarationNode field) {
        Type fieldType = toType(field.type());
        Object initVal = null;
        if (field.initializer() instanceof LiteralExpr lit) {
            initVal = switch (lit.kind()) {
                case ConcreteLiteralKind.INT -> Integer.parseInt(lit.value());
                case ConcreteLiteralKind.LONG -> Long.parseLong(stripSuffix(lit.value()));
                case ConcreteLiteralKind.FLOAT -> Float.parseFloat(stripSuffix(lit.value()));
                case ConcreteLiteralKind.DOUBLE -> Double.parseDouble(stripSuffix(lit.value()));
                case ConcreteLiteralKind.STRING -> lit.value();
                case ConcreteLiteralKind.BOOLEAN -> Boolean.parseBoolean(lit.value()) ? 1 : 0;
                default -> null;
            };
        }
        return new IRField(field.name(), fieldType, computeAccess(field.modifiers()), initVal);
    }

    private IRMethod lowerMethod(MethodDeclarationNode method, String owner, boolean isInterface) {
        Type returnType = toType(method.returnType());
        List<Type> paramTypes = method.parameters().stream().map(p -> toType(p.type())).toList();
        int access = computeAccess(method.modifiers());
        if (isInterface && !method.modifiers().contains("default")) access |= AccessFlags.ABSTRACT;
        List<IRBasicBlock> body = List.of();
        List<IRLocalVariable> locals = List.of();
        if (method.body() != null && !method.body().isEmpty() && !isAbstractMethod(method)) {
            List<KofOperation> ops = new ArrayList<>();
            List<IRLocalVariable> localVars = new ArrayList<>();
            Type ownerType = ownerTypeFromInternal(owner);
            localVars.add(new IRLocalVariable(0, "this", ownerType));
            int localIdx = 1;
            for (FormalParameterNode param : method.parameters()) {
                Type paramType = toType(param.type());
                localVars.add(new IRLocalVariable(localIdx, param.name(), paramType));
                localIdx += isDoubleWidth(paramType) ? 2 : 1;
            }
            for (StatementNode stmt : method.body()) localIdx = emitStatement(stmt, ops, owner, localIdx, localVars, returnType);
            KofOperation lastOp = ops.isEmpty() ? null : ops.get(ops.size() - 1);
            if (lastOp == null || !(lastOp instanceof KofReturn || lastOp instanceof KofReturnVoid)) {
                if (Type.isVoid(returnType)) ops.add(new KofReturnVoid());
                else ops.add(new KofReturn(returnType));
            }
            body = List.of(new IRBasicBlock(0, ops));
            locals = localVars;
        }
        return new IRMethod(method.name(), returnType, paramTypes, access, method.thrownExceptions(), body, locals);
    }

    private IRMethod lowerConstructor(ConstructorDeclarationNode ctor, String owner, String superName) {
        List<Type> paramTypes = ctor.parameters().stream().map(p -> toType(p.type())).toList();
        int access = computeAccess(ctor.modifiers());
        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> localVars = new ArrayList<>();
        Type ownerType = ownerTypeFromInternal(owner);
        Type superType = ownerTypeFromInternal(superName);
        localVars.add(new IRLocalVariable(0, "this", ownerType));
        boolean hasExplicitSuper = !ctor.body().isEmpty() &&
                ctor.body().getFirst() instanceof ExpressionStmt es &&
                es.expression() instanceof MethodCallExpr mc &&
                "super".equals(mc.methodName());
        if (!hasExplicitSuper) {
            ops.add(new KofLoadLocal(ownerType, 0));
            ops.add(new KofCall(superType, "<init>", List.of(), Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
        }
        int localIdx = 1;
        for (FormalParameterNode param : ctor.parameters()) {
            Type paramType = toType(param.type());
            localVars.add(new IRLocalVariable(localIdx, param.name(), paramType));
            localIdx += isDoubleWidth(paramType) ? 2 : 1;
        }
        for (StatementNode stmt : ctor.body()) localIdx = emitStatement(stmt, ops, owner, localIdx, localVars, Type.PrimitiveType.VOID);
        ops.add(new KofReturnVoid());
        return new IRMethod("<init>", Type.PrimitiveType.VOID, paramTypes, access, ctor.thrownExceptions(),
                List.of(new IRBasicBlock(0, ops)), localVars);
    }

    private IRMethod generateDefaultConstructor(String owner, String superName) {
        Type ownerType = ownerTypeFromInternal(owner);
        Type superType = ownerTypeFromInternal(superName);
        return new IRMethod("<init>", Type.PrimitiveType.VOID, List.of(), AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, List.of(
                        new KofLoadLocal(ownerType, 0),
                        new KofCall(superType, "<init>", List.of(), Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR),
                        new KofReturnVoid()))),
                List.of(new IRLocalVariable(0, "this", ownerType)));
    }

    private IRMethod generateRecordConstructor(RecordDeclarationNode rec, String owner) {
        List<Type> compTypes = rec.components().stream().map(c -> toType(c.type())).toList();
        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> locals = new ArrayList<>();
        Type ownerType = ownerTypeFromInternal(owner);
        Type superType = new Type.ClassType("java.lang", "Record", List.of());
        locals.add(new IRLocalVariable(0, "this", ownerType));
        ops.add(new KofLoadLocal(ownerType, 0));
        ops.add(new KofCall(superType, "<init>", List.of(), Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
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

    private String toInternalName(String packageName, String simpleName) {
        if (simpleName.contains("/")) return simpleName;
        if (simpleName.contains(".")) return simpleName.replace('.', '/');
        if (packageName.isEmpty()) return simpleName;
        return packageName.replace('.', '/') + "/" + simpleName;
    }

    private int computeAccess(List<String> modifiers) {
        int access = 0;
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
        }
        return access;
    }

    private boolean isAbstractMethod(MethodDeclarationNode method) {
        return method.body() == null || method.body().isEmpty();
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
