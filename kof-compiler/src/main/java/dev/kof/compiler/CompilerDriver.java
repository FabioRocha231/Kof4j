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
    private DiagnosticCollector currentDiagnostics;
    private final java.util.IdentityHashMap<KofOperation, SourcePosition> currentDebugPositions =
            new java.util.IdentityHashMap<>();
    private final java.util.Deque<LabelId> breakLabels = new java.util.ArrayDeque<>();
    private final java.util.Deque<LabelId> continueLabels = new java.util.ArrayDeque<>();

    public CompilationResult compile(Path sourceFile, Path outputDir) {
        return compile(sourceFile, outputDir, Target.JVM);
    }

    public CompilationResult compile(Path sourceFile, Path outputDir, Target target) {
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        this.target = target;
        this.currentDiagnostics = diagnostics;
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
            case JS -> new JsBackend();
        };
    }

    private Type toType(String typeName) {
        if ("List".equals(typeName) || "ArrayList".equals(typeName)) return BuiltinTypes.LIST;
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
            if (decl instanceof ClassDeclarationNode cls) classes.add(lowerClass(cls, unit.packageName(), nextTypeId++));
            else if (decl instanceof InterfaceDeclarationNode iface) classes.add(lowerInterface(iface, unit.packageName(), nextTypeId++));
            else if (decl instanceof RecordDeclarationNode rec) classes.add(lowerRecord(rec, unit.packageName(), nextTypeId++));
            else if (decl instanceof FunctionDeclarationNode func) topLevelFunctions.add(lowerFunction(func));
        }
        if (!topLevelFunctions.isEmpty()) {
            String mainClassName = moduleName.isEmpty() ? "Main" : moduleName + "/Main";
            classes.add(0, new IRClass(mainClassName, "java/lang/Object", List.of(),
                    AccessFlags.PUBLIC | AccessFlags.SUPER, List.of(), topLevelFunctions, List.of(), null, 0));
        }
        classes.addAll(syntheticClasses);
        return new IRModule(moduleName, classes, imports, sourceFile.getFileName() != null
                ? sourceFile.getFileName().toString() : null);
    }

    private final List<IRClass> syntheticClasses = new ArrayList<>();
    private final java.util.IdentityHashMap<LambdaExpr, String> lambdaClassNames = new java.util.IdentityHashMap<>();
    private int lambdaCounter = 0;

    private String lambdaClass(LambdaExpr le, Type.FunctionType ft) {
        String existing = lambdaClassNames.get(le);
        if (existing != null) return existing;
        String name = "Lambda" + (lambdaCounter++);
        String returnTypeName = typeToString(ft.returnType());
        List<FormalParameterNode> params = le.parameters();
        MethodDeclarationNode synthetic = new MethodDeclarationNode(le.position(), List.of("public"),
                returnTypeName, "invoke", params, List.of(), le.body());
        IRMethod invoke = lowerMethod(synthetic, name, false, List.of());
        IRClass cls = new IRClass(name, "java/lang/Object", List.of(),
                AccessFlags.PUBLIC | AccessFlags.SUPER, List.of(),
                List.of(invoke, generateDefaultConstructor(name, "java/lang/Object", List.of(),
                        new java.util.LinkedHashMap<>())), List.of(), null, 200 + lambdaCounter);
        syntheticClasses.add(cls);
        lambdaClassNames.put(le, name);
        return name;
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
        Type returnType = resolveWithTypeParams(func.returnType(), func.typeParameters());
        if (Type.isVoid(returnType) && func.body().size() == 1 && func.body().getFirst() instanceof ReturnStmt ret && ret.value() != null) {
            List<IRLocalVariable> tmpLocals = new ArrayList<>();
            int tmpIdx = 0;
            for (FormalParameterNode p : func.parameters()) {
                tmpLocals.add(new IRLocalVariable(tmpIdx, p.name(), resolveWithTypeParams(p.type(), func.typeParameters())));
                tmpIdx++;
            }
            returnType = inferExprType(ret.value(), tmpLocals);
        }
        List<Type> paramTypes = func.parameters().stream()
                .map(p -> resolveWithTypeParams(p.type(), func.typeParameters())).toList();
        boolean isMain = "main".equals(func.name()) && paramTypes.isEmpty();
        if (isMain) {
            paramTypes = List.of(new Type.ArrayType(BuiltinTypes.STRING));
        }
        int access = AccessFlags.PUBLIC | AccessFlags.STATIC;
        List<IRLocalVariable> locals = new ArrayList<>();
        List<KofOperation> body = new ArrayList<>();
        int localIdx = 0;
        if (isMain) {


            localIdx = 1;
        }
        for (FormalParameterNode p : func.parameters()) {
            locals.add(new IRLocalVariable(localIdx, p.name(), resolveWithTypeParams(p.type(), func.typeParameters())));
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
        KofDebugInfo debugInfo = currentDebugPositions.isEmpty()
                ? KofDebugInfo.EMPTY
                : new KofDebugInfo(new java.util.HashMap<>(currentDebugPositions));
        currentDebugPositions.clear();
        return new IRMethod(func.name(), returnType, paramTypes, access, func.thrownExceptions(),
                List.of(new IRBasicBlock(0, body)), locals, debugInfo);
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
                } else {
                    ops.add(new KofReturnVoid());
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
                    localIdx = emitExpression(bin.left(), ops, owner, localIdx, locals);
                    localIdx = emitExpression(bin.right(), ops, owner, localIdx, locals);
                    ops.add(new KofConditionalJump(mapComparison(bin.operator()), thenLabel, elseLabel));
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
                    localIdx = emitExpression(bin.left(), ops, owner, localIdx, locals);
                    localIdx = emitExpression(bin.right(), ops, owner, localIdx, locals);
                    ops.add(new KofConditionalJump(mapComparison(bin.operator()), bodyLabel, endLabel));
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
                    localIdx = emitExpression(bin.left(), ops, owner, localIdx, locals);
                    localIdx = emitExpression(bin.right(), ops, owner, localIdx, locals);
                    ops.add(new KofConditionalJump(mapComparison(bin.operator()), startLabel, endLabel));
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
                        localIdx = emitExpression(bin.left(), ops, owner, localIdx, locals);
                        localIdx = emitExpression(bin.right(), ops, owner, localIdx, locals);
                        ops.add(new KofConditionalJump(mapComparison(bin.operator()), bodyLabel, endLabel));
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
                        if (hasReturnValue(fs.update())) ops.add(new KofPop());
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
                continueLabels.push(startLabel);
                localIdx = emitStatement(fis.body(), ops, owner, localIdx, locals, returnType);
                breakLabels.pop();
                continueLabels.pop();
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
                ops.add(new KofConditionalJump(KofComparison.NE, okLabel, failLabel));
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
                if (target == Target.NATIVE) {
                    if (currentDiagnostics != null) {
                        currentDiagnostics.error("", 0, 0, 0,
                                "spawn: not supported on the Native target yet (JVM supports it)", "CONC001");
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
                String lambdaClass = lambdaClass(le, ft);
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
                    ops.add(new KofBinary(KofBinaryOp.SUB, switchType));
                    ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                    ops.add(new KofConditionalJump(KofComparison.EQ, bodyLabels.get(i),
                            i + 1 < ss.cases().size() ? testLabels.get(i + 1) : defaultLabel));
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
                if ("instanceof".equals(bin.operator()) || "as".equals(bin.operator())) {

                    localIdx = emitExpression(bin.left(), ops, owner, localIdx, locals);
                    Type targetType = Type.UnknownType.UNKNOWN;
                    if (bin.right() instanceof IdentifierExpr ie) {
                        targetType = Type.of(ie.name());
                    }
                    if ("instanceof".equals(bin.operator())) {
                        ops.add(new KofInstanceOf(targetType));
                    } else {
                        ops.add(new KofCheckCast(targetType));
                    }
                    yield localIdx;
                }
                Type leftType = inferExprType(bin.left(), locals);
                Type rightType = inferExprType(bin.right(), locals);
                boolean isArithmetic = switch (bin.operator()) {
                    case "+", "-", "*", "/", "%" -> true;
                    default -> false;
                };
                boolean isNumericComparison = isComparisonOp(bin.operator())
                        && isNumeric(leftType) && isNumeric(rightType);
                if ((isArithmetic || isNumericComparison) && isNumeric(leftType) && isNumeric(rightType)) {
                    Type commonType = commonNumericType(leftType, rightType);
                    localIdx = emitExpression(bin.left(), ops, owner, localIdx, locals);
                    emitWideningIfNeeded(ops, leftType, commonType);
                    localIdx = emitExpression(bin.right(), ops, owner, localIdx, locals);
                    emitWideningIfNeeded(ops, rightType, commonType);
                    ops.add(new KofBinary(mapArithmeticOp(bin.operator()), commonType));
                    yield localIdx;
                }
                if ("+".equals(bin.operator()) && (Type.isString(leftType) || Type.isString(rightType))) {
                    localIdx = emitExpression(bin.left(), ops, owner, localIdx, locals);
                    if (!Type.isString(leftType) && isPrimitiveType(leftType)) boxPrimitive(ops, leftType);
                    ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                            List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING, KofCallKind.STATIC));
                    localIdx = emitExpression(bin.right(), ops, owner, localIdx, locals);
                    if (!Type.isString(rightType) && isPrimitiveType(rightType)) boxPrimitive(ops, rightType);
                    ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                            List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING, KofCallKind.STATIC));
                    ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                            List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                            BuiltinTypes.STRING, KofCallKind.FUNCTION));
                } else if (("==".equals(bin.operator()) || "!=".equals(bin.operator()))
                        && (Type.isString(leftType) || Type.isString(rightType))) {
                    localIdx = emitExpression(bin.left(), ops, owner, localIdx, locals);
                    localIdx = emitExpression(bin.right(), ops, owner, localIdx, locals);
                    ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_equals",
                            List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                            Type.PrimitiveType.BOOL, KofCallKind.FUNCTION));
                    if ("!=".equals(bin.operator())) {
                        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                        ops.add(new KofBinary(KofBinaryOp.EQ, Type.PrimitiveType.INT));
                    }
                } else {
                    localIdx = emitExpression(bin.left(), ops, owner, localIdx, locals);
                    localIdx = emitExpression(bin.right(), ops, owner, localIdx, locals);
                    Type operandType = leftType;
                    switch (bin.operator()) {
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
                }
                yield localIdx;
            }
            case UnaryExpr ue -> {
                Type operandType = inferExprType(ue.operand(), locals);
                if ("++".equals(ue.operator()) || "--".equals(ue.operator())) {
                    System.err.println("DBG emitIncrement op=" + ue.operator() + " operand=" + ue.operand());
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
                } else if (mc.receiver() instanceof IdentifierExpr rid && "json".equals(rid.name())) {
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
                        ops.add(new KofCall(targetType, jsonDecodeFunction(targetType), List.of(BuiltinTypes.STRING),
                                targetType, KofCallKind.FUNCTION));
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
                } else if (mc.receiver() != null) {
                    localIdx = emitExpression(mc.receiver(), ops, owner, localIdx, locals);
                    Type recvType = inferExprType(mc.receiver(), locals);
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
                        ops.add(new KofCall(recvType, "invoke", argTypes, ft.returnType(), KofCallKind.INSTANCE));
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
                            for (ExpressionNode arg : mc.arguments()) localIdx = emitExpression(arg, ops, owner, localIdx, locals);
                            Type elemType = listElementType(recvType);
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
                        StringMethodSig sig = stringMethodSignature(mc.methodName(), mc.arguments().size());
                        if (sig != null) {
                            methodReturnType = sig.returnType();
                            methodParamTypes = sig.parameterTypes();
                        }
                    } else {
                        ObjectMethodSig osig = objectMethodSignature(mc.methodName(), mc.arguments().size());
                        if (osig != null) {
                            methodReturnType = osig.returnType();
                            methodParamTypes = osig.parameterTypes();
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
                    ops.add(new KofCall(recvType, mc.methodName(), methodParamTypes, methodReturnType, callKind));
                    if (methodReturnType instanceof Type.TypeVariable) {
                        Type effective = inferExprType(mc, locals);
                        if (isPrimitiveType(effective)) {
                            emitErasureUnbox(ops, effective);
                        }
                    }
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
                            List<Type> ctorParamTypes = ctor != null ? ctor.parameterTypes() : argTypes;
                            localIdx = emitArgumentsWithFormalTypes(mc.arguments(), ctorParamTypes, ops, owner, localIdx, locals);
                            ops.add(new KofCall(superType, "<init>", ctorParamTypes, Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                            yield localIdx;
                        }
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
                                        argTypes = fn.parameters().stream()
                                                .map(p -> resolveWithTypeParams(p.type(), fn.typeParameters())).toList();
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
                        if (fieldSym != null) {
                            Type ownerType = ownerTypeFromInternal(owner);
                            ops.add(new KofLoadLocal(ownerType, 0));
                            localIdx = emitExpression(ae.value(), ops, owner, localIdx, locals);
                            ops.add(new KofStoreField(ownerType, ie.name(), fieldSym.type()));
                            yield localIdx;
                        }
                    }
                }
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
                List<Type> ctorParamTypes = resolvedCtor != null ? resolvedCtor.parameterTypes() : argTypes;
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
                // static field access: Class.field — no receiver on the stack
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
                    localIdx = emitExpression(bin.left(), ops, owner, localIdx, locals);
                    localIdx = emitExpression(bin.right(), ops, owner, localIdx, locals);
                    ops.add(new KofConditionalJump(mapComparison(bin.operator()), thenLabel, elseLabel));
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
                String lambdaClass = lambdaClass(le, ft);
                if (ft.className() == null) {
                    ft = new Type.FunctionType(ft.parameterTypes(), ft.returnType(), lambdaClass);
                }
                ops.add(new KofNewObject(new Type.ClassType("", lambdaClass, List.of()), ft.parameterTypes()));
                ops.add(new KofDup());
                ops.add(new KofCall(new Type.ClassType("", lambdaClass, List.of()), "<init>",
                        List.of(), Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
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
                    SymbolTable.ClassSymbol cls = semanticAnalyzer.getClass(ie.name());
                    if (cls != null) yield cls.type();
                }
                yield Type.UnknownType.UNKNOWN;
            }
            case UnaryExpr ue -> inferExprType(ue.operand(), locals);
            case BinaryExpr bin -> {
                Type leftType = inferExprType(bin.left(), locals);
                Type rightType = inferExprType(bin.right(), locals);
                if ("+".equals(bin.operator()) && (Type.isString(leftType) || Type.isString(rightType))) {
                    yield BuiltinTypes.STRING;
                }
                if ("instanceof".equals(bin.operator())) yield Type.PrimitiveType.BOOL;
                if ("as".equals(bin.operator())) yield rightType;
                if (isComparisonOp(bin.operator())) yield Type.PrimitiveType.BOOL;
                yield leftType;
            }
            case MethodCallExpr mc -> {
                if ("println".equals(mc.methodName()) || "print".equals(mc.methodName())) yield Type.PrimitiveType.VOID;
                if ("now".equals(mc.methodName()) && mc.receiver() == null && mc.arguments().isEmpty()) {
                    yield Type.PrimitiveType.LONG;
                }
                if (("readLine".equals(mc.methodName()) || "readFile".equals(mc.methodName()))
                        && mc.receiver() == null) {
                    yield BuiltinTypes.STRING;
                }
                if ("writeFile".equals(mc.methodName()) && mc.receiver() == null) {
                    yield Type.PrimitiveType.INT;
                }
                if (mc.receiver() == null && KofIo.isConstructor(mc.methodName()) && mc.arguments().size() == 1) {
                    yield KofIo.constructorType(mc.methodName());
                }
                if ("listOf".equals(mc.methodName()) && mc.receiver() == null) {
                    yield new Type.ClassType("kof", "List", List.of(listOfElementType(mc, locals)));
                }
                if (mc.receiver() instanceof IdentifierExpr rid && "json".equals(rid.name())) {
                    if ("encode".equals(mc.methodName())) yield BuiltinTypes.STRING;
                    if ("decode".equals(mc.methodName()) && !mc.typeArguments().isEmpty()) {
                        yield toType(mc.typeArguments().get(0));
                    }
                    yield Type.UnknownType.UNKNOWN;
                }
                if (mc.receiver() instanceof IdentifierExpr rid2 && KofIo.isConstructor(rid2.name())) {
                    KofIo.IoCall ioCall = KofIo.staticMethod(rid2.name(), mc.methodName(), mc.arguments().size());
                    if (ioCall != null) yield ioCall.returnType();
                }
                if (mc.receiver() != null) {
                    Type recvType = inferExprType(mc.receiver(), locals);
                    if (KofIo.isIoType(recvType)) {
                        KofIo.IoCall ioCall = KofIo.instanceMethod(recvType, mc.methodName(), mc.arguments().size());
                        if (ioCall != null) yield ioCall.returnType();
                        if (KofIo.isIdentityMethod(mc.methodName())) yield recvType;
                    }
                    if (recvType instanceof Type.FunctionType ft) {
                        yield ft.returnType();
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
                    if (Type.isString(recvType)) {
                        String mn = mc.methodName();
                        if ("charAt".equals(mn)) yield Type.PrimitiveType.CHAR;
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
            localIdx = emitExpression(args.get(i), ops, owner, localIdx, locals);
            Type argType = inferExprType(args.get(i), locals);
            Type formal = i < formalTypes.size() ? formalTypes.get(i) : null;
            if (formal != null && erasesToReference(formal) && isPrimitiveType(argType)
                    && !BuiltinTypes.isString(formal)) {
                emitErasureBox(ops, argType);
            }
        }
        return localIdx;
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
            case "toUpperCase", "toLowerCase" -> argCount == 0 ? new StringMethodSig(str, List.of()) : null;
            case "replace" -> argCount == 2 ? new StringMethodSig(str, List.of(CHAR, CHAR)) : null;
            case "split" -> argCount == 1 ? new StringMethodSig(strArray, List.of(str))
                    : argCount == 2 ? new StringMethodSig(strArray, List.of(str, INT)) : null;
            default -> null;
        };
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
        ops.add(new KofLoadLocal(ownerType, 0));
        ops.add(new KofLoadField(ownerType, fieldName, fieldType));
        int tmp = localIdx++;
        locals.add(new IRLocalVariable(tmp, "#inc", fieldType));
        ops.add(new KofStoreLocal(fieldType, tmp));
        ops.add(new KofLoadLocal(fieldType, tmp));
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
        ops.add(new KofBinary(op, fieldType));
        if (prefix) {
            // [receiver, new] -> [new, receiver, new]
            ops.add(new KofDupX1());
            ops.add(new KofStoreField(ownerType, fieldName, fieldType));
        } else {
            ops.add(new KofStoreField(ownerType, fieldName, fieldType));
            ops.add(new KofLoadLocal(fieldType, tmp));
        }
        return localIdx;
    }

    private boolean isComparisonOp(String op) {
        return ">".equals(op) || "<".equals(op) || ">=".equals(op) || "<=".equals(op) || "==".equals(op) || "!=".equals(op);
    }

    private int jsonListTag(Type elemType) {
        if (BuiltinTypes.isString(elemType)) return 1;
        if (elemType instanceof Type.PrimitiveType pt && "bool".equals(pt.name())) return 2;
        return 0;
    }

    private boolean jsonSupported(Type type, boolean isDecode) {
        Type check = BuiltinTypes.isList(type) ? listElementType(type) : type;
        if (check instanceof Type.PrimitiveType pt && ("float".equals(pt.name()) || "double".equals(pt.name()))) {
            if (currentDiagnostics != null) {
                currentDiagnostics.error("", 0, 0, 0,
                        "json: Float/Double is not supported yet (use int, long, bool or String)", "JSN001");
            }
            return false;
        }
        if (isDecode && type instanceof Type.ArrayType) {
            if (currentDiagnostics != null) {
                currentDiagnostics.error("", 0, 0, 0,
                        "json.decode: arrays are not supported yet (use List<Int> or List<String>)", "JSN003");
            }
            return false;
        }
        if (check instanceof Type.ClassType && target == Target.NATIVE && !BuiltinTypes.isList(type)
                && !BuiltinTypes.isString(type)) {
            if (currentDiagnostics != null) {
                currentDiagnostics.error("", 0, 0, 0,
                        "json: objects are not supported on the Native target yet (JVM supports object JSON)",
                        "JSN002");
            }
            return false;
        }
        return true;
    }

    private String jsonEncodeFunction(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "int", "char", "byte", "short" -> "kof_json_encode_int";
                case "long" -> "kof_json_encode_long";
                case "bool" -> "kof_json_encode_bool";
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
                default -> "kof_json_decode_int";
            };
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

    private boolean isComparisonShortcut(BinaryExpr bin, List<IRLocalVariable> locals) {
        if (!isComparisonOp(bin.operator())) return false;
        if ("==".equals(bin.operator()) || "!=".equals(bin.operator())) {
            Type left = inferExprType(bin.left(), locals);
            Type right = inferExprType(bin.right(), locals);
            if (Type.isString(left) || Type.isString(right)) return false;
        }
        return true;
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

    private boolean hasReturnValue(ExpressionNode expr) {
        if (expr instanceof AssignmentExpr) return false;
        if (expr instanceof MethodCallExpr mc) {
            if ("print".equals(mc.methodName()) || "println".equals(mc.methodName())) return false;
            if (mc.receiver() != null && KofIo.instanceMethod(Type.UnknownType.UNKNOWN,
                    mc.methodName(), mc.arguments().size()) != null) {
                return true;
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
            Type t = inferExprType(mc, List.of());
            if (t instanceof Type.UnknownType || Type.isVoid(t)) return false;
            return true;
        }
        return true;
    }

    private IRClass lowerClass(ClassDeclarationNode cls, String packageName, int typeId) {
        String internalName = toInternalName(packageName, cls.name());
        String superName = cls.superClass() != null ? toInternalName("", cls.superClass()) : "java/lang/Object";
        List<String> ifaces = cls.interfaces().stream().map(i -> toInternalName("", i)).toList();
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
            }
        }
        if (!methods.stream().anyMatch(m -> m.name().equals("<init>"))) {
            methods.add(0, generateDefaultConstructor(internalName, superName, fields, fieldInits));
        }
        return new IRClass(internalName, superName, ifaces, access, fields, methods, List.of(), null, typeId);
    }

    private IRClass lowerInterface(InterfaceDeclarationNode iface, String packageName, int typeId) {
        String internalName = toInternalName(packageName, iface.name());
        List<String> ifaces = iface.interfaces().stream().map(i -> toInternalName("", i)).toList();
        int access = computeAccess(iface.modifiers()) | AccessFlags.ABSTRACT | AccessFlags.INTERFACE;
        List<IRMethod> methods = new ArrayList<>();
        List<IRField> fields = new ArrayList<>();
        for (AstNode member : iface.members()) {
            if (member instanceof MethodDeclarationNode method) methods.add(lowerMethod(method, internalName, true, List.of()));
            else if (member instanceof FieldDeclarationNode field) fields.add(lowerField(field, List.of()));
        }
        return new IRClass(internalName, "java/lang/Object", ifaces, access, fields, methods, List.of(), null, typeId);
    }

    private IRClass lowerRecord(RecordDeclarationNode rec, String packageName, int typeId) {
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
        return new IRClass(internalName, superName, ifaces, access, fields, methods, List.of(), null, typeId);
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
        return new IRField(field.name(), fieldType, computeAccess(field.modifiers()), initVal);
    }

    private IRMethod lowerMethod(MethodDeclarationNode method, String owner, boolean isInterface, List<String> typeParams) {
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
            localVars.add(new IRLocalVariable(0, "this", ownerType));
            int localIdx = 1;
            for (FormalParameterNode param : method.parameters()) {
                Type paramType = resolveWithTypeParams(param.type(), typeParams);
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
        KofDebugInfo debugInfo = currentDebugPositions.isEmpty()
                ? KofDebugInfo.EMPTY
                : new KofDebugInfo(new java.util.HashMap<>(currentDebugPositions));
        currentDebugPositions.clear();
        return new IRMethod(method.name(), returnType, paramTypes, access, method.thrownExceptions(),
                body, locals, debugInfo);
    }

    private IRMethod lowerConstructor(ConstructorDeclarationNode ctor, String owner, String superName,
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
        boolean hasExplicitSuper = !ctor.body().isEmpty() &&
                ctor.body().getFirst() instanceof ExpressionStmt es &&
                es.expression() instanceof MethodCallExpr mc &&
                "super".equals(mc.methodName());
        if (!hasExplicitSuper && !"java/lang/Object".equals(superName)) {
            ops.add(new KofLoadLocal(ownerType, 0));
            ops.add(new KofCall(superType, "<init>", List.of(), Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
        }
        emitFieldInitializers(ops, ownerType, fields);
        int localIdx = 1;
        for (FormalParameterNode param : ctor.parameters()) {
            Type paramType = resolveWithTypeParams(param.type(), typeParams);
            localVars.add(new IRLocalVariable(localIdx, param.name(), paramType));
            localIdx += isDoubleWidth(paramType) ? 2 : 1;
        }
        for (var entry : fieldInits.entrySet()) {
            Type fieldType = fields.stream().filter(f -> f.name().equals(entry.getKey())).findFirst()
                    .map(f -> f.type()).orElse(Type.UnknownType.UNKNOWN);
            ops.add(new KofLoadLocal(ownerType, 0));
            localIdx = emitExpression(entry.getValue(), ops, owner, localIdx, localVars);
            ops.add(new KofStoreField(ownerType, entry.getKey(), fieldType));
        }
        for (StatementNode stmt : ctor.body()) localIdx = emitStatement(stmt, ops, owner, localIdx, localVars, Type.PrimitiveType.VOID);
        ops.add(new KofReturnVoid());
        return new IRMethod("<init>", Type.PrimitiveType.VOID, paramTypes, access, ctor.thrownExceptions(),
                List.of(new IRBasicBlock(0, ops)), localVars);
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
