package dev.kof.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JsBackend — KofJS backend.
 *
 * Consumes the same Kof IR as the JVM and Native backends and lowers it to a
 * JavaScript AST (JsIr), which the JsEmitter renders as a modern ECMAScript
 * module (ES2022+, ESM) for Node.js.
 *
 * The Kof IR is a stack-based linear instruction list; this backend converts
 * the stack discipline into the tree-shaped JsIr. Control-flow patterns
 * emitted by the frontend (if/while/for/do-while/for-in/switch/try) are
 * recognized structurally and re-created as native JavaScript control flow.
 *
 * Runtime semantics (List, String helpers, JSON, IO, print) are provided by
 * the small KofJS runtime modules (kof-runtime.mjs, kof-runtime-node.mjs)
 * written next to the generated program. This backend never emits
 * console.* / process.* calls directly into user code.
 */
class JsBackend implements Backend {

    private static final Set<String> RESERVED = Set.of(
            "class", "function", "var", "let", "const", "return", "if", "else", "while", "do",
            "for", "switch", "case", "default", "break", "continue", "new", "delete", "typeof",
            "instanceof", "in", "try", "catch", "finally", "throw", "this", "super", "null",
            "true", "false", "void", "static", "extends", "import", "export", "yield", "await",
            "async", "of", "arguments", "eval");

    private final List<String> runtimeImports = new ArrayList<>();
    private final List<String> ioRuntimeImports = new ArrayList<>();
    private final Set<String> decodeHelpers = new HashSet<>();
    private Map<String, Set<String>> classMethodNames = Map.of();

    @Override
    public void emit(IRModule module, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        runtimeImports.clear();
        ioRuntimeImports.clear();
        JsIr.JsModule jsModule = lowerModule(module);
        String code = new JsEmitter().emit(jsModule);
        String fileName = moduleFileName(module.name());
        Path outFile = outputDir.resolve(fileName);
        Files.writeString(outFile, code + "//# sourceMappingURL=" + fileName + ".map\n");
        writeRuntime(outputDir);
        writeSourceMap(module, outputDir, fileName);
    }

    private static String moduleFileName(String moduleName) {
        if (moduleName == null || moduleName.isBlank()) return "Default.mjs";
        return moduleName + ".mjs";
    }

    // ── Module lowering ─────────────────────────────────────────────

    private JsIr.JsModule lowerModule(IRModule module) {
        List<JsIr.JsClass> classes = new ArrayList<>();
        List<JsIr.JsFunction> functions = new ArrayList<>();
        Map<String, Set<String>> methodNames = new HashMap<>();
        for (IRClass clazz : module.classes()) {
            if (!skipClass(clazz)) {
                methodNames.put(clazz.name(), new HashSet<>());
                for (IRMethod m : clazz.methods()) {
                    methodNames.get(clazz.name()).add(m.name());
                }
            }
        }
        this.classMethodNames = methodNames;
        for (IRClass clazz : module.classes()) {
            if (skipClass(clazz)) continue;
            if (isMainClass(clazz)) {
                for (IRMethod method : clazz.methods()) {
                    if ("<init>".equals(method.name())) continue;
                    functions.add(lowerFunction(method, null, false, true));
                }
            }
        }
        for (IRClass clazz : module.classes()) {
            if (skipClass(clazz) || isMainClass(clazz)) continue;
            classes.add(lowerClass(clazz));
        }
        for (IRClass clazz : module.classes()) {
            if (skipClass(clazz) || isMainClass(clazz)) continue;
            if (decodeHelpers.contains(jsClassName(clazz.name()))) {
                functions.add(lowerDecodeHelper(clazz));
            }
        }
        List<JsIr.JsStatement> moduleStatements = new ArrayList<>();
        for (JsIr.JsFunction fn : functions) {
            if ("main".equals(fn.name())) {
                moduleStatements.add(new JsIr.JsExprStmt(new JsIr.JsCall(
                        new JsIr.JsIdentifier("main"), List.of())));
                break;
            }
        }
        return new JsIr.JsModule(module.name(), classes, functions,
                new ArrayList<>(new LinkedHashSet<>(runtimeImports)),
                new ArrayList<>(new LinkedHashSet<>(ioRuntimeImports)), moduleStatements);
    }

    private static boolean skipClass(IRClass clazz) {
        if (clazz.name() == null || clazz.name().isBlank()) return true;
        if ("java/lang/Object".equals(clazz.name()) || "java/lang/Record".equals(clazz.name())) return true;
        // Interfaces are type-level only in Kof; JavaScript has no runtime
        // interface. Calls through interfaces lower to structural method
        // calls (receiver.method(...)), so no JS entity is required.
        return (clazz.accessFlags() & AccessFlags.INTERFACE) != 0;
    }

    private static boolean isMainClass(IRClass clazz) {
        return "Main".equals(clazz.name()) || clazz.name().endsWith("/Main");
    }

    private JsIr.JsClass lowerClass(IRClass clazz) {
        String jsName = jsClassName(clazz.name());
        String jsSuper = null;
        if (clazz.superName() != null && !clazz.superName().isEmpty()
                && !"java/lang/Object".equals(clazz.superName())
                && !"java/lang/Record".equals(clazz.superName())) {
            jsSuper = jsClassName(clazz.superName());
        }
        boolean isRecord = "java/lang/Record".equals(clazz.superName());
        List<JsIr.JsField> fields = new ArrayList<>();
        for (IRField field : clazz.fields()) {
            boolean isStatic = (field.accessFlags() & AccessFlags.STATIC) != 0;
            String fieldName = isRecord ? "_" + sanitizeName(field.name()) : sanitizeName(field.name());
            fields.add(new JsIr.JsField(fieldName,
                    field.initialValue() != null ? literalText(field.initialValue()) : null, isStatic));
        }
        List<JsIr.JsFunction> methods = new ArrayList<>();
        for (IRMethod method : clazz.methods()) {
            if ("<init>".equals(method.name())) {
                methods.add(lowerConstructor(clazz, method));
            } else {
                boolean isStatic = (method.accessFlags() & AccessFlags.STATIC) != 0;
                methods.add(lowerFunction(method, clazz, isStatic));
            }
        }
        if (isRecord) {
            methods.add(lowerRecordToString(clazz));
            methods.add(lowerRecordToJson(clazz));
        }
        return new JsIr.JsClass(jsName, jsSuper, fields, methods);
    }

    /**
     * Records: the component fields are private in Kof/JVM; in JS the accessor
     * method shares the component name, so the backing field gets a "_" prefix
     * (this.name as a property would shadow the name() accessor).
     */
    private String jsFieldName(IRClass clazz, String name) {
        if ("java/lang/Record".equals(clazz.superName())) {
            return "_" + sanitizeName(name);
        }
        return sanitizeName(name);
    }

    /**
     * Records get a toString() in JS to mirror the JVM backend's synthetic
     * record toString: "Name[f1=..., f2=...]".
     */
    private JsIr.JsFunction lowerRecordToString(IRClass clazz) {
        String simpleName = clazz.name().contains("/")
                ? clazz.name().substring(clazz.name().lastIndexOf('/') + 1) : clazz.name();
        List<JsIr.JsExpression> parts = new ArrayList<>();
        parts.add(new JsIr.JsString(simpleName + "["));
        for (int i = 0; i < clazz.fields().size(); i++) {
            if (i > 0) parts.add(new JsIr.JsString(", "));
            parts.add(new JsIr.JsString(clazz.fields().get(i).name() + "="));
            parts.add(new JsIr.JsMember(new JsIr.JsThis(),
                    "_" + sanitizeName(clazz.fields().get(i).name())));
        }
        parts.add(new JsIr.JsString("]"));
        JsIr.JsExpression joined = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            joined = new JsIr.JsBinary(joined, "+", parts.get(i));
        }
        return new JsIr.JsFunction("toString", List.of(),
                List.of(new JsIr.JsReturn(joined)), false, false, false);
    }

    /**
     * Records serialize as { "f1": ..., "f2": ... } to mirror the JVM backend's
     * reflection-based JSON encoding (JSON.stringify honors toJSON()).
     */
    private JsIr.JsFunction lowerRecordToJson(IRClass clazz) {
        List<JsIr.JsObjectEntry> entries = new ArrayList<>();
        for (IRField field : clazz.fields()) {
            entries.add(new JsIr.JsObjectEntry(field.name(),
                    new JsIr.JsMember(new JsIr.JsThis(), "_" + sanitizeName(field.name()))));
        }
        return new JsIr.JsFunction("toJSON", List.of(),
                List.of(new JsIr.JsReturn(new JsIr.JsObjectLiteral(entries))), false, false, false);
    }

    /**
     * json.decode&lt;Class&gt; binds the parsed object to the Kof class:
     * records use their canonical constructor; classes get a default instance
     * with fields assigned by name (mirroring the JVM reflection binding).
     */
    private JsIr.JsFunction lowerDecodeHelper(IRClass clazz) {
        String jsName = jsClassName(clazz.name());
        boolean isRecord = "java/lang/Record".equals(clazz.superName());
        JsIr.JsExpression parsed = new JsIr.JsCall(
                new JsIr.JsIdentifier("JSON.parse"), List.of(new JsIr.JsIdentifier("json")));
        List<JsIr.JsStatement> body = new ArrayList<>();
        body.add(new JsIr.JsVarDecl("p", parsed, true));
        List<JsIr.JsExpression> ctorArgs = new ArrayList<>();
        for (IRField field : clazz.fields()) {
            ctorArgs.add(new JsIr.JsMember(new JsIr.JsIdentifier("p"), sanitizeName(field.name())));
        }
        JsIr.JsExpression instance = new JsIr.JsNew(new JsIr.JsIdentifier(jsName), ctorArgs);
        if (isRecord) {
            body.add(new JsIr.JsVarDecl("o", instance, true));
        } else {
            body.add(new JsIr.JsVarDecl("o", new JsIr.JsNew(new JsIr.JsIdentifier(jsName), List.of()), true));
            for (IRField field : clazz.fields()) {
                body.add(new JsIr.JsExprStmt(new JsIr.JsBinary(
                        new JsIr.JsMember(new JsIr.JsIdentifier("o"), sanitizeName(field.name())), "=",
                        new JsIr.JsMember(new JsIr.JsIdentifier("p"), sanitizeName(field.name())))));
            }
        }
        body.add(new JsIr.JsReturn(new JsIr.JsIdentifier("o")));
        return new JsIr.JsFunction("__kof_decode_" + jsName, List.of("json"), body, false, false, true);
    }

    private JsIr.JsFunction lowerConstructor(IRClass clazz, IRMethod method) {
        MethodCtx ctx = new MethodCtx(method, clazz);
        List<JsIr.JsStatement> body = parseMethodBody(ctx);
        insertFieldDefaults(clazz, body);
        insertSuperCall(clazz, body);
        return new JsIr.JsFunction("constructor", parameterNames(ctx), body, false, true, false);
    }

    private JsIr.JsFunction lowerFunction(IRMethod method, IRClass clazz, boolean isStatic) {
        return lowerFunction(method, clazz, isStatic, false);
    }

    private JsIr.JsFunction lowerFunction(IRMethod method, IRClass clazz, boolean isStatic, boolean isTopLevel) {
        MethodCtx ctx = new MethodCtx(method, clazz);
        String name = method.name();
        if ("<init>".equals(name)) name = "constructor";
        return new JsIr.JsFunction(name, parameterNames(ctx), parseMethodBody(ctx), isStatic, false, isTopLevel);
    }

    private List<JsIr.JsStatement> parseMethodBody(MethodCtx ctx) {
        int[] pos = {0};
        List<JsIr.JsStatement> body = parseStatements(ctx, pos, Set.of(), new ArrayList<>());
        if (pos[0] < ctx.ops.size()) {
            throw new IllegalStateException("KofJS: unconsumed ops in method "
                    + ctx.kofClassName + "." + (ctx.methodName == null ? "?" : ctx.methodName)
                    + " at " + ctx.ops.get(pos[0]));
        }
        if (!ctx.tempDecls.isEmpty()) {
            List<JsIr.JsStatement> withTemps = new ArrayList<>();
            for (String decl : ctx.tempDecls) {
                withTemps.add(new JsIr.JsVarDecl(decl, null, false));
            }
            withTemps.addAll(body);
            return withTemps;
        }
        return body;
    }

    // ── Per-method context ──────────────────────────────────────────

    private record LoopCtx(LabelId start, LabelId continueLabel, LabelId end) {
    }

    /**
     * A pending `new T` awaiting its <init> call: [NewPending, args...] or
     * [NewPending, DupMarker, args...] — lowered to `new T(args)`.
     */
    private record NewPending(String typeName) {
    }

    private static final class DupMarker {
    }

    private final class MethodCtx {
        final List<KofOperation> ops;
        final Map<Integer, String> localNames = new HashMap<>();
        final Map<Integer, String> rawLocalNames = new HashMap<>();
        final Set<Integer> declared = new HashSet<>();
        final Set<String> usedNames = new HashSet<>();
        final List<String> tempDecls = new ArrayList<>();
        final List<LoopCtx> loops = new ArrayList<>();
        final boolean instanceMethod;
        final String kofClassName;
        final String methodName;
        final int paramCount;
        final boolean recordClass;
        int tempCounter = 0;

        MethodCtx(IRMethod method, IRClass clazz) {
            this.ops = new ArrayList<>(method.basicBlocks().stream()
                    .flatMap(b -> b.operations().stream()).toList());
            this.instanceMethod = clazz != null && !isMainClass(clazz)
                    && (method.accessFlags() & AccessFlags.STATIC) == 0;
            this.kofClassName = clazz == null ? null : clazz.name();
            this.methodName = method.name();
            this.paramCount = method.parameterTypes().size();
            this.recordClass = clazz != null && "java/lang/Record".equals(clazz.superName());
            for (IRLocalVariable lv : method.localVariables()) {
                rawLocalNames.put(lv.index(), lv.name());
                if (instanceMethod && lv.index() == 0) {
                    localNames.put(lv.index(), "this");
                    continue;
                }
                localNames.put(lv.index(), uniqueName(sanitizeName(lv.name())));
            }
        }

        String uniqueName(String base) {
            String name = base;
            int n = 1;
            while (!usedNames.add(name)) {
                name = base + "_" + (n++);
            }
            return name;
        }

        String freshTemp() {
            String name = uniqueName("__kof_t" + (tempCounter++));
            tempDecls.add(name);
            return name;
        }

        LoopCtx currentLoop() {
            return loops.isEmpty() ? null : loops.get(loops.size() - 1);
        }

        boolean isLoopLabel(LabelId label) {
            for (LoopCtx lc : loops) {
                if (label.equals(lc.start) || label.equals(lc.continueLabel) || label.equals(lc.end)) {
                    return true;
                }
            }
            return false;
        }

        boolean isLoopEnd(LabelId label) {
            for (LoopCtx lc : loops) {
                if (label.equals(lc.end)) return true;
            }
            return false;
        }

        boolean hasClassMethod(String kofClassName, String method) {
            Set<String> names = classMethodNames.get(kofClassName);
            return names != null && names.contains(method);
        }
    }

    // ── Statement parser ────────────────────────────────────────────

    /**
     * Parses a statement list. The region ends when:
     *  - a label/jump in endLabels is encountered (consumed);
     *  - a jump to an unknown label is found (region exit, consumed);
     *  - the continue label of the enclosing for-loop is found (not consumed);
     *  - an unmatched label (belongs to the enclosing pattern) is found
     *    (not consumed).
     * Region exits are recorded in exits (jump targets).
     */
    private List<JsIr.JsStatement> parseStatements(MethodCtx ctx, int[] pos,
                                                   Set<LabelId> endLabels, List<LabelId> exits) {
        List<JsIr.JsStatement> out = new ArrayList<>();
        while (pos[0] < ctx.ops.size()) {
            KofOperation op = ctx.ops.get(pos[0]);
            if (op instanceof KofLabel kl) {
                if (endLabels.contains(kl.label())) {
                    pos[0]++;
                    exits.add(kl.label());
                    return out;
                }
                if (ctx.isLoopLabel(kl.label()) || looksLikeContinueLabel(ctx, pos, kl.label())) {
                    return out;
                }
                if (isLoopStart(ctx, pos, kl.label())) {
                    out.add(parseLoop(ctx, pos, kl.label()));
                    continue;
                }
                // unmatched label — the enclosing pattern owns it
                return out;
            }
            if (op instanceof KofJump kj) {
                if (endLabels.contains(kj.target())) {
                    pos[0]++;
                    exits.add(kj.target());
                    return out;
                }
                if (ctx.isLoopLabel(kj.target())) {
                    pos[0]++;
                    if (ctx.isLoopEnd(kj.target())) {
                        out.add(new JsIr.JsBreak());
                    } else {
                        out.add(new JsIr.JsContinue());
                    }
                    continue;
                }
                // region exit (if/try/finally jump)
                pos[0]++;
                exits.add(kj.target());
                return out;
            }
            out.add(parseStatement(ctx, pos));
        }
        return out;
    }

    /**
     * The continue label of the enclosing for-loop: a label followed by the
     * update statements and the back-edge jump to the loop start.
     */
    private boolean looksLikeContinueLabel(MethodCtx ctx, int[] pos, LabelId label) {
        LoopCtx loop = ctx.currentLoop();
        if (loop == null || label.equals(loop.start) || label.equals(loop.end)) return false;
        for (int i = pos[0] + 1; i < ctx.ops.size(); i++) {
            KofOperation op = ctx.ops.get(i);
            if (op instanceof KofJump kj) {
                return kj.target().equals(loop.start);
            }
            if (op instanceof KofLabel || op instanceof KofConditionalJump
                    || op instanceof KofTryStart || op instanceof KofCatchStart
                    || op instanceof KofReturn || op instanceof KofReturnVoid
                    || op instanceof KofThrow) {
                return false;
            }
        }
        return false;
    }

    /**
     * A label is a loop start when a later instruction jumps to it (back edge)
     * or conditionally jumps to it (do-while condition).
     */
    private boolean isLoopStart(MethodCtx ctx, int[] pos, LabelId label) {
        for (int i = pos[0] + 1; i < ctx.ops.size(); i++) {
            KofOperation op = ctx.ops.get(i);
            if (op instanceof KofJump kj && kj.target().equals(label)) return true;
            if (op instanceof KofConditionalJump cj && cj.trueLabel().equals(label)) return true;
        }
        return false;
    }

    private JsIr.JsStatement parseStatement(MethodCtx ctx, int[] pos) {
        KofOperation op = ctx.ops.get(pos[0]);
        if (op instanceof KofReturnVoid) {
            pos[0]++;
            return new JsIr.JsReturn(null);
        }
        if (op instanceof KofTryStart) {
            return parseTryStatement(ctx, pos);
        }
        if (op instanceof KofLoadLocal ll && "#switch".equals(ctx.rawLocalNames.get(ll.index()))) {
            return parseSwitchStatement(ctx, pos);
        }
        if (op instanceof KofJump kj) {
            pos[0]++;
            if (ctx.isLoopEnd(kj.target())) {
                return new JsIr.JsBreak();
            }
            return new JsIr.JsContinue();
        }
        if (op instanceof KofLabel) {
            throw new IllegalStateException("KofJS: unexpected label at statement level");
        }
        if (op instanceof KofCatchStart) {
            throw new IllegalStateException("KofJS: unexpected KofCatchStart at statement level");
        }
        return parseExpressionStatement(ctx, pos);
    }

    // ── If statement ────────────────────────────────────────────────

    /**
     * Statement-level if: [cond ops, CJump, Label(true), then, Jump(end),
     * Label(false), (else), Label(end)].
     */
    private JsIr.JsStatement parseIfBody(MethodCtx ctx, int[] pos, KofConditionalJump cj,
                                         JsIr.JsExpression condition, List<Object> stack) {
        if (!(ctx.ops.get(pos[0]) instanceof KofLabel kl && kl.label().equals(cj.trueLabel()))) {
            throw new IllegalStateException("KofJS: if pattern expected Label(true)");
        }
        pos[0]++;
        List<JsIr.JsStatement> thenBranch = parseStatements(ctx, pos, Set.of(cj.falseLabel()), new ArrayList<>());
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel kl2
                && kl2.label().equals(cj.falseLabel())) {
            pos[0]++;
        }
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel) {
            // Label(end) — no else branch
            pos[0]++;
            return new JsIr.JsIf(condition, thenBranch, List.of());
        }
        List<JsIr.JsStatement> elseBranch = parseStatements(ctx, pos, Set.of(), new ArrayList<>());
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel) {
            // Label(end) — end of else branch
            pos[0]++;
        }
        return new JsIr.JsIf(condition, thenBranch, elseBranch);
    }

    /**
     * Attempts to parse an if-expression: [Label(true), expr, Jump(end),
     * Label(false), expr, Label(end)]. Returns null (restoring the position)
     * when the upcoming ops form a statement-level if instead.
     */
    private JsIr.JsExpression tryParseIfExpr(MethodCtx ctx, int[] pos, KofConditionalJump cj,
                                             JsIr.JsExpression condition) {
        int saved = pos[0];
        try {
            if (!(ctx.ops.get(pos[0]) instanceof KofLabel kl && kl.label().equals(cj.trueLabel()))) {
                pos[0] = saved;
                return null;
            }
            pos[0]++;
            JsIr.JsExpression thenExpr = parseExpressionFragment(ctx, pos);
            if (!(ctx.ops.get(pos[0]) instanceof KofJump)) {
                pos[0] = saved;
                return null;
            }
            pos[0]++;
            if (!(ctx.ops.get(pos[0]) instanceof KofLabel kl2 && kl2.label().equals(cj.falseLabel()))) {
                pos[0] = saved;
                return null;
            }
            pos[0]++;
            JsIr.JsExpression elseExpr = parseExpressionFragment(ctx, pos);
            if (!(ctx.ops.get(pos[0]) instanceof KofLabel)) {
                pos[0] = saved;
                return null;
            }
            pos[0]++;
            return new JsIr.JsConditional(condition, thenExpr, elseExpr);
        } catch (RuntimeException e) {
            // statement-level if: expressions in the branches failed, so the
            // construct is a statement, not an if-expression
            pos[0] = saved;
            return null;
        }
    }

    private JsIr.JsExpression comparisonExpr(KofComparison comp, JsIr.JsExpression left, JsIr.JsExpression right) {
        if (comp == KofComparison.NE && right instanceof JsIr.JsNumber n && "0".equals(n.text())) {
            // boolean conditions: (cond, 0) CJump(NE) — truthiness in JS
            return left;
        }
        return switch (comp) {
            case EQ -> new JsIr.JsBinary(left, "===", right);
            case NE -> new JsIr.JsBinary(left, "!==", right);
            case LT -> new JsIr.JsBinary(left, "<", right);
            case LE -> new JsIr.JsBinary(left, "<=", right);
            case GT -> new JsIr.JsBinary(left, ">", right);
            case GE -> new JsIr.JsBinary(left, ">=", right);
        };
    }

    // ── Loops ───────────────────────────────────────────────────────

    private JsIr.JsStatement parseLoop(MethodCtx ctx, int[] pos, LabelId startLabel) {
        pos[0]++;
        // A do-while loop is the only construct whose conditional jump targets
        // its own start label; scan the whole remaining stream to find it.
        KofConditionalJump doWhileJump = null;
        for (int i = pos[0]; i < ctx.ops.size(); i++) {
            if (ctx.ops.get(i) instanceof KofConditionalJump cj && cj.trueLabel().equals(startLabel)) {
                doWhileJump = cj;
                break;
            }
        }
        if (doWhileJump != null) {
            return parseDoWhile(ctx, pos, startLabel, doWhileJump);
        }
        // while / for: condition ops, CJump(body, end), Label(body), body, ...
        // while / for / for-in: condition ops, CJump(body, end), Label(body), body, ...
        List<Object> condStack = new ArrayList<>();
        while (pos[0] < ctx.ops.size() && !(ctx.ops.get(pos[0]) instanceof KofConditionalJump)) {
            if (!isExpressionOp(ctx.ops.get(pos[0]))) {
                throw new IllegalStateException("KofJS: unexpected op in loop condition: " + ctx.ops.get(pos[0]));
            }
            consumeExpressionOp(ctx, pos, condStack);
        }
        if (!(ctx.ops.get(pos[0]) instanceof KofConditionalJump cj2)) {
            throw new IllegalStateException("KofJS: loop condition not terminated");
        }
        pos[0]++;
        JsIr.JsExpression right = pop(condStack);
        JsIr.JsExpression left = pop(condStack);
        if (!condStack.isEmpty()) {
            throw new IllegalStateException("KofJS: malformed loop condition stack");
        }
        JsIr.JsExpression condition = comparisonExpr(cj2.comparison(), left, right);
        if (!(ctx.ops.get(pos[0]) instanceof KofLabel bodyLabel && bodyLabel.label().equals(cj2.trueLabel()))) {
            throw new IllegalStateException("KofJS: loop body label mismatch");
        }
        pos[0]++;
        // Detect the continue label before parsing the body: the back edge is
        // the first Jump(start) after the body label, and the continue label
        // is the label immediately before the update statements (for-loops).
        LabelId continueLabel = startLabel;
        for (int i = pos[0]; i < ctx.ops.size(); i++) {
            if (ctx.ops.get(i) instanceof KofJump kj && kj.target().equals(startLabel)) {
                for (int j = i - 1; j >= pos[0]; j--) {
                    KofOperation op = ctx.ops.get(j);
                    if (op instanceof KofLabel kl) {
                        continueLabel = kl.label();
                        break;
                    }
                    if (op instanceof KofJump || op instanceof KofConditionalJump) break;
                }
                break;
            }
        }
        ctx.loops.add(new LoopCtx(startLabel, continueLabel, cj2.falseLabel()));
        List<JsIr.JsStatement> body = parseStatements(ctx, pos, Set.of(startLabel), new ArrayList<>());
        ctx.loops.remove(ctx.loops.size() - 1);
        // After the body: either Jump(start) (while) or Label(continue) + update + Jump(start) (for).
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel nextLabel
                && !nextLabel.label().equals(startLabel)
                && !nextLabel.label().equals(cj2.falseLabel())) {
            pos[0]++;
            List<JsIr.JsStatement> update = new ArrayList<>();
            while (pos[0] < ctx.ops.size() && !(ctx.ops.get(pos[0]) instanceof KofJump)) {
                update.add(parseStatement(ctx, pos));
            }
            if (!(ctx.ops.get(pos[0]) instanceof KofJump kj) || !kj.target().equals(startLabel)) {
                throw new IllegalStateException("KofJS: for-loop expected Jump(start)");
            }
            pos[0]++;
            if (!(ctx.ops.get(pos[0]) instanceof KofLabel end && end.label().equals(cj2.falseLabel()))) {
                throw new IllegalStateException("KofJS: for-loop expected Label(end)");
            }
            pos[0]++;
            return new JsIr.JsFor(List.of(), condition, update, body);
        }
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofJump kj
                && kj.target().equals(startLabel)) {
            pos[0]++;
        }
        if (!(ctx.ops.get(pos[0]) instanceof KofLabel end && end.label().equals(cj2.falseLabel()))) {
            throw new IllegalStateException("KofJS: loop expected Label(end)");
        }
        pos[0]++;
        return new JsIr.JsWhile(condition, body, false);
    }

    private JsIr.JsStatement parseDoWhile(MethodCtx ctx, int[] pos, LabelId startLabel,
                                          KofConditionalJump loopJump) {
        ctx.loops.add(new LoopCtx(startLabel, startLabel, loopJump.falseLabel()));
        List<JsIr.JsStatement> body = new ArrayList<>();
        while (true) {
            if (pos[0] >= ctx.ops.size()) {
                throw new IllegalStateException("KofJS: do-while condition not found");
            }
            if (isDoWhileConditionAhead(ctx, pos, startLabel)) {
                break;
            }
            body.add(parseStatement(ctx, pos));
        }
        ctx.loops.remove(ctx.loops.size() - 1);
        List<Object> condStack = new ArrayList<>();
        while (pos[0] < ctx.ops.size() && !(ctx.ops.get(pos[0]) instanceof KofConditionalJump)) {
            if (!isExpressionOp(ctx.ops.get(pos[0]))) {
                throw new IllegalStateException("KofJS: unexpected op in do-while condition: " + ctx.ops.get(pos[0]));
            }
            consumeExpressionOp(ctx, pos, condStack);
        }
        if (!(ctx.ops.get(pos[0]) instanceof KofConditionalJump cj)) {
            throw new IllegalStateException("KofJS: do-while condition not terminated");
        }
        pos[0]++;
        JsIr.JsExpression right = pop(condStack);
        JsIr.JsExpression left = pop(condStack);
        JsIr.JsExpression condition = comparisonExpr(cj.comparison(), left, right);
        if (!(ctx.ops.get(pos[0]) instanceof KofLabel end && end.label().equals(loopJump.falseLabel()))) {
            throw new IllegalStateException("KofJS: do-while expected Label(end)");
        }
        pos[0]++;
        return new JsIr.JsWhile(condition, body, true);
    }

    private boolean isDoWhileConditionAhead(MethodCtx ctx, int[] pos, LabelId startLabel) {
        for (int i = pos[0]; i < ctx.ops.size(); i++) {
            KofOperation op = ctx.ops.get(i);
            if (op instanceof KofConditionalJump cj) {
                return cj.trueLabel().equals(startLabel);
            }
            if (!isExpressionOp(op)) {
                return false;
            }
        }
        return false;
    }

    // ── Try statement ───────────────────────────────────────────────

    private JsIr.JsStatement parseTryStatement(MethodCtx ctx, int[] pos) {
        KofTryStart ts = (KofTryStart) ctx.ops.get(pos[0]);
        pos[0]++;
        List<JsIr.JsStatement> tryBody = parseStatements(ctx, pos, Set.of(ts.endLabel()), new ArrayList<>());
        if (pos[0] >= ctx.ops.size() || !(ctx.ops.get(pos[0]) instanceof KofLabel tryEnd)
                || !tryEnd.label().equals(ts.endLabel())) {
            throw new IllegalStateException("KofJS: try expected Label(tryEnd)");
        }
        pos[0]++;
        List<JsIr.JsCatchClause> catches = new ArrayList<>();
        while (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofCatchStart cs) {
            if ("Throwable".equals(cs.exceptionType())) {
                // catch-all + rethrow emulates finally; JS finally is native.
                pos[0]++;
                if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofJump) {
                    pos[0]++;
                }
                break;
            }
            pos[0]++;
            String param = localName(ctx, cs.localIndex());
            List<JsIr.JsStatement> catchBody = parseStatements(ctx, pos, Set.of(), new ArrayList<>());
            catches.add(new JsIr.JsCatchClause(param, catchBody));
        }
        if (pos[0] >= ctx.ops.size() || !(ctx.ops.get(pos[0]) instanceof KofTryEnd)) {
            throw new IllegalStateException("KofJS: try expected KofTryEnd");
        }
        pos[0]++;
        List<JsIr.JsStatement> finallyBody = List.of();
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel) {
            pos[0]++;
            List<LabelId> exits = new ArrayList<>();
            finallyBody = parseStatements(ctx, pos, Set.of(), exits);
            // skip the rethrow machinery: Label(rethrow) ... Label(done)
            LabelId done = exits.isEmpty() ? null : exits.get(exits.size() - 1);
            if (done != null) {
                while (pos[0] < ctx.ops.size() && !(ctx.ops.get(pos[0]) instanceof KofLabel kl
                        && kl.label().equals(done))) {
                    pos[0]++;
                }
                if (pos[0] < ctx.ops.size()) pos[0]++;
            } else if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel) {
                // no-finally: the trailing empty label (done) ends the try
                pos[0]++;
            }
        }
        return new JsIr.JsTry(tryBody, catches, finallyBody);
    }

    // ── Switch statement ────────────────────────────────────────────

    private JsIr.JsStatement parseSwitchStatement(MethodCtx ctx, int[] pos) {
        // [load #switch, <caseValue>, SUB, load 0, CJump(EQ, body, next)] *
        // followed by: [Label(body0), stmts, Jump(end)] * [Label(default), stmts, Label(end)]
        List<JsIr.JsExpression> caseValues = new ArrayList<>();
        List<LabelId> bodyLabels = new ArrayList<>();
        LabelId defaultLabel = null;
        LabelId endLabel = null;
        String subjectName = null;
        while (true) {
            if (!(ctx.ops.get(pos[0]) instanceof KofLoadLocal ll
                    && "#switch".equals(ctx.rawLocalNames.get(ll.index())))) {
                break;
            }
            pos[0]++;
            if (subjectName == null) {
                subjectName = localName(ctx, ll.index());
            }
            List<Object> stack = new ArrayList<>();
            stack.add(new JsIr.JsIdentifier(subjectName));
            while (true) {
                KofOperation op = ctx.ops.get(pos[0]);
                if (op instanceof KofBinary kb && kb.op() == KofBinaryOp.SUB && stack.size() == 2) {
                    pos[0]++;
                    break;
                }
                if (!isExpressionOp(op)) {
                    throw new IllegalStateException("KofJS: unexpected op in switch case: " + op);
                }
                consumeExpressionOp(ctx, pos, stack);
            }
            JsIr.JsExpression caseValue = pop(stack);
            pop(stack);
            if (!(ctx.ops.get(pos[0]) instanceof KofLoadLiteral zero
                    && zero.value() instanceof Integer i && i == 0)) {
                throw new IllegalStateException("KofJS: switch case expected 0");
            }
            pos[0]++;
            if (!(ctx.ops.get(pos[0]) instanceof KofConditionalJump cj
                    && cj.comparison() == KofComparison.EQ)) {
                throw new IllegalStateException("KofJS: switch case expected CJump(EQ)");
            }
            pos[0]++;
            caseValues.add(caseValue);
            bodyLabels.add(cj.trueLabel());
            defaultLabel = cj.falseLabel();
            endLabel = cj.falseLabel();
            if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel
                    && pos[0] + 1 < ctx.ops.size()
                    && ctx.ops.get(pos[0] + 1) instanceof KofLoadLocal next
                    && "#switch".equals(ctx.rawLocalNames.get(next.index()))) {
                pos[0]++;
                continue;
            }
            break;
        }
        if (subjectName == null) {
            throw new IllegalStateException("KofJS: switch subject not found");
        }
        List<JsIr.JsSwitchCase> fullCases = new ArrayList<>();
        for (LabelId bodyLabel : bodyLabels) {
            if (pos[0] >= ctx.ops.size() || !(ctx.ops.get(pos[0]) instanceof KofLabel kl)
                    || !kl.label().equals(bodyLabel)) {
                throw new IllegalStateException("KofJS: switch body label missing");
            }
            pos[0]++;
            List<LabelId> exits = new ArrayList<>();
            List<JsIr.JsStatement> body = parseStatements(ctx, pos, Set.of(), exits);
            if (endLabel == null && !exits.isEmpty()) {
                endLabel = exits.get(exits.size() - 1);
            }
            fullCases.add(new JsIr.JsSwitchCase(caseValues.get(fullCases.size()), body));
        }
        List<JsIr.JsStatement> defaultCase = List.of();
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel dl
                && dl.label().equals(defaultLabel)) {
            pos[0]++;
            defaultCase = parseStatements(ctx, pos, Set.of(), new ArrayList<>());
        }
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel) {
            // Label(end) — end of the switch
            pos[0]++;
        }
        return new JsIr.JsSwitch(new JsIr.JsIdentifier(subjectName), fullCases, defaultCase);
    }

    // ── Expression statements ───────────────────────────────────────

    /**
     * Thrown when a void call (or a constructor super call) completes the
     * current statement.
     */
    private static final class StatementEnd extends RuntimeException {
        final JsIr.JsExpression call;

        StatementEnd(JsIr.JsExpression call) {
            this.call = call;
        }
    }

    private JsIr.JsStatement parseExpressionStatement(MethodCtx ctx, int[] pos) {
        List<Object> stack = new ArrayList<>();
        List<JsIr.JsStatement> preamble = new ArrayList<>();
        while (pos[0] < ctx.ops.size()) {
            KofOperation op = ctx.ops.get(pos[0]);
            if (op instanceof KofStoreLocal sl) {
                pos[0]++;
                JsIr.JsStatement stmt = storeLocalStatement(ctx, sl, pop(stack));
                if (stack.isEmpty()) {
                    return finishExpressionStatement(preamble, stmt);
                }
                // mid-expression store (++/-- on locals): keep evaluating
                preamble.add(stmt);
                continue;
            }
            if (op instanceof KofStoreField sf) {
                pos[0]++;
                JsIr.JsExpression value = pop(stack);
                JsIr.JsExpression receiver = pop(stack);
                JsIr.JsStatement stmt = new JsIr.JsExprStmt(new JsIr.JsBinary(
                        new JsIr.JsMember(receiver,
                                ctx.recordClass ? "_" + sanitizeName(sf.name()) : sanitizeName(sf.name())), "=", value));
                if (stack.isEmpty()) {
                    return finishExpressionStatement(preamble, stmt);
                }
                preamble.add(stmt);
                continue;
            }
            if (op instanceof KofPutStatic ps) {
                pos[0]++;
                JsIr.JsExpression value = pop(stack);
                String owner = jsClassName(ownerInternalName(ps.ownerType()));
                return finishExpressionStatement(preamble, new JsIr.JsExprStmt(new JsIr.JsBinary(
                        new JsIr.JsMember(new JsIr.JsIdentifier(owner), sanitizeName(ps.name())), "=", value)));
            }
            if (op instanceof KofArrayStore as) {
                pos[0]++;
                JsIr.JsExpression value = pop(stack);
                JsIr.JsExpression index = pop(stack);
                JsIr.JsExpression array = pop(stack);
                JsIr.JsStatement stmt = new JsIr.JsExprStmt(new JsIr.JsBinary(
                        new JsIr.JsIndex(array, index), "=", value));
                if (stack.isEmpty()) {
                    return finishExpressionStatement(preamble, stmt);
                }
                preamble.add(stmt);
                continue;
            }
            if (op instanceof KofPop) {
                pos[0]++;
                if (stack.isEmpty()) {
                    throw new IllegalStateException("KofJS: dangling pop");
                }
                pop(stack);
                if (!stack.isEmpty()) {
                    throw new IllegalStateException("KofJS: dangling stack at pop");
                }
                return finishExpressionStatement(preamble, null);
            }
            if (op instanceof KofReturn kr) {
                pos[0]++;
                return finishExpressionStatement(preamble, new JsIr.JsReturn(pop(stack)));
            }
            if (op instanceof KofThrow) {
                pos[0]++;
                return finishExpressionStatement(preamble, new JsIr.JsThrow(pop(stack)));
            }
            if (op instanceof KofConditionalJump cj && pos[0] + 1 < ctx.ops.size()
                    && ctx.ops.get(pos[0] + 1) instanceof KofLabel kl
                    && kl.label().equals(cj.trueLabel())) {
                // if-statement OR if-expression (var x = if (...) ... else ...)
                pos[0]++;
                JsIr.JsExpression right = pop(stack);
                JsIr.JsExpression left = pop(stack);
                JsIr.JsExpression condition = comparisonExpr(cj.comparison(), left, right);
                JsIr.JsExpression ifExpr = tryParseIfExpr(ctx, pos, cj, condition);
                if (ifExpr != null) {
                    stack.add(ifExpr);
                    continue;
                }
                return parseIfBody(ctx, pos, cj, condition, stack);
            }
            if (!isExpressionOp(op)) {
                // statement boundary: wrap any leftover stack (listOf(...) chains)
                if (!stack.isEmpty()) {
                    JsIr.JsExpression wrapped = wrapStack(stack);
                    stack.clear();
                    return new JsIr.JsExprStmt(wrapped);
                }
                throw new IllegalStateException("KofJS: unexpected op in expression statement: " + op);
            }
            try {
                consumeExpressionOp(ctx, pos, stack);
            } catch (StatementEnd se) {
                if (!stack.isEmpty()) {
                    JsIr.JsExpression wrapped = wrapStack(stack);
                    stack.clear();
                    return new JsIr.JsExprStmt(new JsIr.JsSequence(
                            List.of(wrapped), se.call));
                }
                return new JsIr.JsExprStmt(se.call);
            }
        }
        if (!stack.isEmpty()) {
            return new JsIr.JsExprStmt(wrapStack(stack));
        }
        throw new IllegalStateException("KofJS: unterminated expression statement");
    }

    private JsIr.JsStatement finishExpressionStatement(List<JsIr.JsStatement> preamble,
                                                    JsIr.JsStatement finalStmt) {
        if (preamble.isEmpty()) {
            return finalStmt;
        }
        if (finalStmt != null) {
            List<JsIr.JsStatement> all = new ArrayList<>(preamble);
            all.add(finalStmt);
            return new JsIr.JsBlock(all);
        }
        if (preamble.size() == 1) {
            return preamble.get(0);
        }
        return new JsIr.JsBlock(preamble);
    }

    private JsIr.JsExpression wrapStack(List<Object> stack) {
        if (stack.size() == 1) {
            return pop(stack);
        }
        List<JsIr.JsExpression> exprs = new ArrayList<>();
        for (int i = 0; i < stack.size() - 1; i++) {
            Object o = stack.get(i);
            exprs.add(o instanceof NewPending np
                    ? new JsIr.JsNew(new JsIr.JsIdentifier(np.typeName()), List.of())
                    : (JsIr.JsExpression) o);
        }
        return new JsIr.JsSequence(exprs, pop(stack));
    }

    private JsIr.JsStatement storeLocalStatement(MethodCtx ctx, KofStoreLocal sl, JsIr.JsExpression value) {
        String name = localName(ctx, sl.index());
        if ("this".equals(name)) {
            throw new IllegalStateException("KofJS: cannot store to 'this'");
        }
        if (ctx.declared.add(sl.index())) {
            return new JsIr.JsVarDecl(name, value, false);
        }
        return new JsIr.JsAssign(name, value);
    }

    private String localName(MethodCtx ctx, int index) {
        String name = ctx.localNames.get(index);
        if (name == null) {
            throw new IllegalStateException("KofJS: unknown local slot " + index);
        }
        return name;
    }

    // ── Expression lowering ─────────────────────────────────────────

    private boolean isExpressionOp(KofOperation op) {
        return op instanceof KofLoadLiteral || op instanceof KofLoadLocal
                || op instanceof KofLoadField || op instanceof KofGetStatic
                || op instanceof KofBinary || op instanceof KofUnary
                || op instanceof KofCall || op instanceof KofNewObject
                || op instanceof KofDup || op instanceof KofDupX1 || op instanceof KofNewArray
                || op instanceof KofArrayLoad || op instanceof KofArrayLength
                || op instanceof KofInstanceOf || op instanceof KofCheckCast;
    }

    private void consumeExpressionOp(MethodCtx ctx, int[] pos, List<Object> stack) {
        KofOperation op = ctx.ops.get(pos[0]);
        pos[0]++;
        if (op instanceof KofLoadLiteral lit) {
            stack.add(literalExpr(lit));
        } else if (op instanceof KofLoadLocal ll) {
            stack.add(new JsIr.JsIdentifier(localName(ctx, ll.index())));
        } else if (op instanceof KofLoadField lf) {
            JsIr.JsExpression receiver = pop(stack);
            stack.add(new JsIr.JsMember(receiver, ctx.recordClass ? "_" + sanitizeName(lf.name()) : sanitizeName(lf.name())));
        } else if (op instanceof KofGetStatic gs) {
            if ("java.lang".equals(classPackage(gs.ownerType())) && "System".equals(className(gs.ownerType()))
                    && "out".equals(gs.name())) {
                stack.add(new JsIr.JsIdentifier("$kofOut"));
            } else {
                String owner = jsClassName(ownerInternalName(gs.ownerType()));
                stack.add(new JsIr.JsMember(new JsIr.JsIdentifier(owner), sanitizeName(gs.name())));
            }
        } else if (op instanceof KofBinary kb) {
            JsIr.JsExpression right = pop(stack);
            JsIr.JsExpression left = pop(stack);
            stack.add(binaryExpr(kb, left, right));
        } else if (op instanceof KofUnary ku) {
            JsIr.JsExpression operand = pop(stack);
            stack.add(unaryExpr(ku, operand));
        } else if (op instanceof KofNewObject no) {
            stack.add(new NewPending(jsClassName(ownerInternalName(no.type()))));
        } else if (op instanceof KofDup) {
            if (!stack.isEmpty() && stack.get(stack.size() - 1) instanceof NewPending) {
                stack.add(new DupMarker());
                return;
            }
            JsIr.JsExpression top = pop(stack);
            if (isPureDuplicate(top)) {
                stack.add(top);
                stack.add(top);
                return;
            }
            String temp = ctx.freshTemp();
            stack.add(new JsIr.JsSequence(
                    List.of(new JsIr.JsAssignExpr(temp, top)), new JsIr.JsIdentifier(temp)));
            stack.add(new JsIr.JsIdentifier(temp));
        } else if (op instanceof KofDupX1) {
            JsIr.JsExpression top = pop(stack);
            JsIr.JsExpression below = pop(stack);
            stack.add(top);
            stack.add(below);
            stack.add(top);
        } else if (op instanceof KofNewArray na) {
            JsIr.JsExpression size = pop(stack);
            stack.add(new JsIr.JsArray(size, arrayFill(na.elementType())));
        } else if (op instanceof KofArrayLoad al) {
            JsIr.JsExpression index = pop(stack);
            JsIr.JsExpression array = pop(stack);
            stack.add(new JsIr.JsIndex(array, index));
        } else if (op instanceof KofArrayLength) {
            JsIr.JsExpression array = pop(stack);
            stack.add(new JsIr.JsMember(array, "length"));
        } else if (op instanceof KofCheckCast) {
            // JavaScript has no runtime casts; Kof semantics are enforced by
            // the type checker at compile time.
        } else if (op instanceof KofInstanceOf io) {
            JsIr.JsExpression operand = pop(stack);
            stack.add(new JsIr.JsConditional(
                    new JsIr.JsInstanceOf(operand, jsClassName(ownerInternalName(io.type()))),
                    new JsIr.JsNumber("1"), new JsIr.JsNumber("0")));
        } else if (op instanceof KofConditionalJump cj) {
            // if-expression: (cond ? then : else)
            JsIr.JsExpression right = pop(stack);
            JsIr.JsExpression left = pop(stack);
            JsIr.JsExpression condition = comparisonExpr(cj.comparison(), left, right);
            JsIr.JsExpression ifExpr = tryParseIfExpr(ctx, pos, cj, condition);
            if (ifExpr == null) {
                throw new IllegalStateException("KofJS: malformed if-expression");
            }
            stack.add(ifExpr);
        } else if (op instanceof KofCall kc) {
            handleCall(ctx, stack, kc);
        } else {
            throw new IllegalStateException("KofJS: unhandled IR op " + op);
        }
    }

    /**
     * Parses a self-contained expression fragment (if-expr branches): expression
     * ops until the next statement-level op. The stack must hold exactly one
     * value when finished.
     */
    private JsIr.JsExpression parseExpressionFragment(MethodCtx ctx, int[] pos) {
        List<Object> stack = new ArrayList<>();
        while (pos[0] < ctx.ops.size()) {
            KofOperation op = ctx.ops.get(pos[0]);
            if (op instanceof KofJump || op instanceof KofLabel || op instanceof KofPop
                    || op instanceof KofStoreLocal || op instanceof KofStoreField
                    || op instanceof KofPutStatic || op instanceof KofArrayStore
                    || op instanceof KofReturn || op instanceof KofReturnVoid
                    || op instanceof KofThrow || op instanceof KofTryStart
                    || op instanceof KofCatchStart) {
                break;
            }
            if (op instanceof KofConditionalJump && pos[0] + 1 < ctx.ops.size()
                    && ctx.ops.get(pos[0] + 1) instanceof KofLabel kl
                    && kl.label().equals(((KofConditionalJump) op).trueLabel())) {
                // if-expression or a nested if-statement inside a branch
                int saved = pos[0];
                try {
                    consumeExpressionOp(ctx, pos, stack);
                } catch (RuntimeException e) {
                    pos[0] = saved;
                    break;
                }
                continue;
            }
            if (!isExpressionOp(op)) {
                throw new IllegalStateException("KofJS: unexpected op in expression fragment: " + op);
            }
            consumeExpressionOp(ctx, pos, stack);
        }
        if (stack.size() != 1) {
            throw new IllegalStateException("KofJS: malformed expression fragment (stack size " + stack.size() + ")");
        }
        return pop(stack);
    }

    // ── Calls ───────────────────────────────────────────────────────

    private void handleCall(MethodCtx ctx, List<Object> stack, KofCall kc) {
        if (kc.kind() == KofCallKind.CONSTRUCTOR) {
            handleConstructorCall(stack, kc);
            return;
        }
        boolean hasReceiver = kc.kind() == KofCallKind.INSTANCE || kc.kind() == KofCallKind.INTERFACE;
        List<JsIr.JsExpression> args = new ArrayList<>();
        for (int i = 0; i < kc.parameterTypes().size(); i++) {
            args.add(pop(stack));
        }
        java.util.Collections.reverse(args);
        JsIr.JsExpression receiver = hasReceiver ? pop(stack) : null;
        if (isPrintCall(kc)) {
            JsIr.JsExpression value = args.get(0);
            String fn = "println".equals(kc.methodName()) ? "kofPrintln" : "kofPrint";
            if ("kofPrint".equals(fn)) {
                registerIoRuntime(fn);
            } else {
                registerRuntime(fn);
            }
            throw new StatementEnd(new JsIr.JsCall(new JsIr.JsIdentifier(fn), List.of(value)));
        }
        if ("valueOf".equals(kc.methodName()) && kc.kind() == KofCallKind.STATIC) {
            if (BuiltinTypes.isString(kc.ownerType())) {
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("String"), List.of(args.get(0))));
            } else if (!kc.parameterTypes().isEmpty()
                    && kc.parameterTypes().get(0) instanceof Type.PrimitiveType pt
                    && "bool".equals(Type.canonicalPrimitiveName(pt.name()))) {
                // Boolean.valueOf(Z) — format 0/1 as true/false
                stack.add(new JsIr.JsConditional(args.get(0),
                        new JsIr.JsIdentifier("true"), new JsIr.JsIdentifier("false")));
            } else {
                // boxed valueOf — JS values are already boxed; identity
                stack.add(args.get(0));
            }
            return;
        }
        if (isListOp(kc)) {
            handleListOp(ctx, stack, kc, receiver, args);
            return;
        }
        if (isRuntimeOp(kc)) {
            // kof_json_* / kof_io_* / kof_now / kof_box / kof_unbox — checked
            // before string ops: json.encode("...") has a String owner.
            handleRuntimeOp(ctx, stack, kc, receiver, args);
            return;
        }
        if (isStringOp(kc)) {
            handleStringOp(ctx, stack, kc, receiver, args);
            return;
        }
        if (kc.kind() == KofCallKind.FUNCTION) {
            // top-level function call
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier(kc.methodName()), args));
            return;
        }
        if (kc.kind() == KofCallKind.STATIC) {
            String owner = jsClassName(ownerInternalName(kc.ownerType()));
            stack.add(new JsIr.JsCall(
                    new JsIr.JsMember(new JsIr.JsIdentifier(owner), sanitizeName(kc.methodName())), args));
            return;
        }
        // INSTANCE / INTERFACE — structural dispatch
        String owner = ownerInternalName(kc.ownerType());
        if ("equals".equals(kc.methodName()) && owner != null
                && !ctx.hasClassMethod(owner, "equals")) {
            // Object.equals — reference equality (JVM semantics)
            stack.add(new JsIr.JsBinary(receiver, "===", args.get(0)));
            return;
        }
        JsIr.JsExpression call = new JsIr.JsCall(
                new JsIr.JsMember(receiver, sanitizeName(kc.methodName())), args);
        if (Type.isVoid(kc.returnType())) {
            throw new StatementEnd(call);
        }
        stack.add(call);
    }

    private void handleConstructorCall(List<Object> stack, KofCall kc) {
        List<JsIr.JsExpression> args = new ArrayList<>();
        for (int i = 0; i < kc.parameterTypes().size(); i++) {
            if (stack.isEmpty()) break;
            Object top = stack.get(stack.size() - 1);
            if (top instanceof NewPending || top instanceof DupMarker) break;
            args.add(pop(stack));
        }
        java.util.Collections.reverse(args);
        Object top = popRaw(stack);
        if (top instanceof DupMarker) {
            Object newObj = popRaw(stack);
            if (newObj instanceof NewPending np) {
                stack.add(new JsIr.JsNew(new JsIr.JsIdentifier(np.typeName()), args));
                return;
            }
            throw new IllegalStateException("KofJS: DupMarker without NewPending");
        }
        if (top instanceof NewPending np) {
            stack.add(new JsIr.JsNew(new JsIr.JsIdentifier(np.typeName()), args));
            return;
        }
        // super(...) constructor call
        throw new StatementEnd(new JsIr.JsCall(new JsIr.JsIdentifier("super"), args));
    }

    private boolean isPrintCall(KofCall kc) {
        if (!(kc.ownerType() instanceof Type.ClassType ct)) return false;
        return "java.io".equals(ct.packageName()) && "PrintStream".equals(ct.name())
                && ("println".equals(kc.methodName()) || "print".equals(kc.methodName()));
    }

    // ── Operator lowering ───────────────────────────────────────────

    private boolean isIntFamily(Type type) {
        if (!(type instanceof Type.PrimitiveType pt)) return false;
        return switch (Type.canonicalPrimitiveName(pt.name())) {
            case "int", "byte", "short", "char" -> true;
            default -> false;
        };
    }

    private boolean isLongType(Type type) {
        if (!(type instanceof Type.PrimitiveType pt)) return false;
        return "long".equals(Type.canonicalPrimitiveName(pt.name()));
    }

    private JsIr.JsExpression binaryExpr(KofBinary kb, JsIr.JsExpression left, JsIr.JsExpression right) {
        return switch (kb.op()) {
            case ADD -> intWrap(kb.operandType(), new JsIr.JsBinary(left, "+", right));
            case SUB -> intWrap(kb.operandType(), new JsIr.JsBinary(left, "-", right));
            case MUL -> intWrap(kb.operandType(), new JsIr.JsBinary(left, "*", right));
            case DIV -> {
                if (isIntFamily(kb.operandType())) {
                    yield intWrap(kb.operandType(), new JsIr.JsBinary(left, "/", right));
                }
                if (isLongType(kb.operandType())) {
                    // JS / yields doubles; truncate toward zero like JVM LIDIV
                    yield new JsIr.JsCall(new JsIr.JsMember(new JsIr.JsIdentifier("Math"), "trunc"),
                            List.of(new JsIr.JsBinary(left, "/", right)));
                }
                yield new JsIr.JsBinary(left, "/", right);
            }
            case MOD -> new JsIr.JsBinary(left, "%", right);
            case EQ -> new JsIr.JsBinary(left, "===", right);
            case NE -> new JsIr.JsBinary(left, "!==", right);
            case LT -> new JsIr.JsBinary(left, "<", right);
            case LE -> new JsIr.JsBinary(left, "<=", right);
            case GT -> new JsIr.JsBinary(left, ">", right);
            case GE -> new JsIr.JsBinary(left, ">=", right);
            case AND -> new JsIr.JsBinary(left, "&", right);
            case OR -> new JsIr.JsBinary(left, "|", right);
            case XOR -> new JsIr.JsBinary(left, "^", right);
            case SHL -> new JsIr.JsBinary(left, "<<", right);
            case SHR -> new JsIr.JsBinary(left, ">>", right);
            case USHR -> new JsIr.JsBinary(left, ">>>", right);
        };
    }

    /**
     * Kof Int is a signed 32-bit type; JavaScript numbers are doubles. Wrap
     * int arithmetic with ToInt32 (| 0) to preserve Kof/JVM 32-bit semantics.
     */
    private JsIr.JsExpression intWrap(Type operandType, JsIr.JsExpression inner) {
        if (isIntFamily(operandType)) {
            return new JsIr.JsBinary(inner, "|", new JsIr.JsNumber("0"));
        }
        return inner;
    }

    private JsIr.JsExpression unaryExpr(KofUnary ku, JsIr.JsExpression operand) {
        return switch (ku.op()) {
            case NEG -> new JsIr.JsUnary("-", operand);
            case NOT -> new JsIr.JsConditional(operand, new JsIr.JsNumber("0"), new JsIr.JsNumber("1"));
            case I2L, I2F, I2D, L2F, L2D, F2D -> operand;
        };
    }

    private JsIr.JsExpression literalExpr(KofLoadLiteral lit) {
        if (lit.type() instanceof Type.PrimitiveType pt
                && "bool".equals(Type.canonicalPrimitiveName(pt.name()))) {
            Object v = lit.value();
            return new JsIr.JsIdentifier((v instanceof Integer i && i != 0) ? "true" : "false");
        }
        if (lit.value() instanceof Integer i) return new JsIr.JsNumber(Integer.toString(i));
        if (lit.value() instanceof Long l) return new JsIr.JsNumber(Long.toString(l));
        if (lit.value() instanceof Float f) return new JsIr.JsNumber(Float.toString(f));
        if (lit.value() instanceof Double d) return new JsIr.JsNumber(Double.toString(d));
        if (lit.value() instanceof String s) return new JsIr.JsString(s);
        return new JsIr.JsNull();
    }

    private String literalText(Object value) {
        if (value instanceof Float f) return Float.toString(f);
        if (value instanceof Double d) return Double.toString(d);
        if (value instanceof Boolean b) return b ? "1" : "0";
        return String.valueOf(value);
    }

    private String arrayFill(Type elementType) {
        if (elementType instanceof Type.PrimitiveType) return "0";
        return "null";
    }

    // ── List / String / runtime lowering ────────────────────────────

    private boolean isListOp(KofCall kc) {
        return BuiltinTypes.isList(kc.ownerType()) && kc.methodName().startsWith("kof_list_");
    }

    private void handleListOp(MethodCtx ctx, List<Object> stack, KofCall kc,
                              JsIr.JsExpression receiver, List<JsIr.JsExpression> args) {
        String fn = switch (kc.methodName()) {
            case "kof_list_new" -> "kofListNew";
            case "kof_list_add" -> "kofListAdd";
            case "kof_list_get" -> "kofListGet";
            case "kof_list_set" -> "kofListSet";
            case "kof_list_size" -> "kofListSize";
            case "kof_list_contains" -> "kofListContains";
            case "kof_list_is_empty" -> "kofListIsEmpty";
            case "kof_list_remove" -> "kofListRemove";
            case "kof_list_clear" -> "kofListClear";
            default -> throw new IllegalStateException("KofJS: unknown list op " + kc.methodName());
        };
        registerRuntime(fn);
        if ("kof_list_new".equals(kc.methodName())) {
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier(fn), List.of()));
            return;
        }
        List<JsIr.JsExpression> callArgs = new ArrayList<>();
        callArgs.add(receiver);
        callArgs.addAll(args);
        JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier(fn), callArgs);
        if (Type.isVoid(kc.returnType())) {
            if (receiver instanceof JsIr.JsIdentifier id && id.name().startsWith("__kof_t")
                    && !stack.isEmpty() && stack.get(stack.size() - 1) instanceof JsIr.JsSequence seq
                    && seq.value().equals(receiver)) {
                // mid-expression list construction (listOf(...) element append)
                List<JsIr.JsExpression> exprs = new ArrayList<>(seq.expressions());
                exprs.add(call);
                stack.remove(stack.size() - 1);
                stack.add(new JsIr.JsSequence(exprs, seq.value()));
                return;
            }
            throw new StatementEnd(call);
        }
        stack.add(call);
    }

    private boolean isStringOp(KofCall kc) {
        return BuiltinTypes.isString(kc.ownerType());
    }

    private void handleStringOp(MethodCtx ctx, List<Object> stack, KofCall kc,
                                JsIr.JsExpression receiver, List<JsIr.JsExpression> args) {
        switch (kc.methodName()) {
            case "kof_string_concat" -> stack.add(new JsIr.JsBinary(args.get(0), "+", args.get(1)));
            case "kof_string_equals" -> stack.add(new JsIr.JsConditional(
                    new JsIr.JsBinary(args.get(0), "===", args.get(1)),
                    new JsIr.JsNumber("1"), new JsIr.JsNumber("0")));
            case "valueOf" -> stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("String"), List.of(args.get(0))));
            case "charAt" -> stack.add(new JsIr.JsCall(
                    new JsIr.JsMember(receiver, "charCodeAt"), List.of(args.get(0))));
            case "length" -> stack.add(new JsIr.JsMember(receiver, "length"));
            case "equals" -> stack.add(new JsIr.JsBinary(receiver, "===", args.get(0)));
            case "equalsIgnoreCase" -> stack.add(new JsIr.JsBinary(
                    new JsIr.JsCall(new JsIr.JsMember(receiver, "toUpperCase"), List.of()),
                    "===",
                    new JsIr.JsCall(new JsIr.JsMember(args.get(0), "toUpperCase"), List.of())));
            case "replace" -> {
                // Kof replace(char, char) replaces all occurrences; JS replace
                // only the first, so lower through split/join.
                JsIr.JsExpression from = new JsIr.JsCall(
                        new JsIr.JsMember(new JsIr.JsIdentifier("String"), "fromCharCode"),
                        List.of(args.get(0)));
                JsIr.JsExpression to = new JsIr.JsCall(
                        new JsIr.JsMember(new JsIr.JsIdentifier("String"), "fromCharCode"),
                        List.of(args.get(1)));
                stack.add(new JsIr.JsCall(
                        new JsIr.JsMember(
                                new JsIr.JsCall(new JsIr.JsMember(receiver, "split"), List.of(from)),
                                "join"),
                        List.of(to)));
            }
            default -> {
                // substring, contains, indexOf, trim, toUpperCase, toLowerCase,
                // startsWith, endsWith, concat, split — direct JS mapping.
                JsIr.JsExpression method = "contains".equals(kc.methodName())
                        ? new JsIr.JsMember(receiver, "includes")
                        : new JsIr.JsMember(receiver, sanitizeName(kc.methodName()));
                stack.add(new JsIr.JsCall(method, args));
            }
        }
    }

    private boolean isRuntimeOp(KofCall kc) {
        String name = kc.methodName();
        return name.startsWith("kof_json_") || name.startsWith("kof_io_")
                || name.equals("kof_now") || name.equals("kof_read_line")
                || name.equals("kof_read_file") || name.equals("kof_write_file")
                || name.equals("kof_box") || name.equals("kof_unbox");
    }

    private void handleRuntimeOp(MethodCtx ctx, List<Object> stack, KofCall kc,
                                 JsIr.JsExpression receiver, List<JsIr.JsExpression> args) {
        String name = kc.methodName();
        if (name.startsWith("kof_json_")) {
            // JSON encode/decode maps directly to JSON.stringify/parse; the
            // type information stays in the Kof compiler (generics erasure).
            JsIr.JsExpression value = kc.kind() == KofCallKind.FUNCTION
                    ? args.get(0) : receiver;
            if (name.contains("encode")) {
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("JSON.stringify"), List.of(value)));
            } else if (name.startsWith("kof_json_decode_")
                    && classMethodNames.containsKey(ownerInternalName(kc.ownerType()))) {
                // decode<Class> — bind the parsed object to the Kof class
                String jsName = jsClassName(ownerInternalName(kc.ownerType()));
                decodeHelpers.add(jsName);
                stack.add(new JsIr.JsCall(
                        new JsIr.JsIdentifier("__kof_decode_" + jsName), List.of(value)));
            } else {
                stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("JSON.parse"), List.of(value)));
            }
            return;
        }
        if (name.equals("kof_box") || name.equals("kof_unbox")) {
            // JS values are already boxed; these are identity.
            stack.add(kc.kind() == KofCallKind.FUNCTION ? args.get(0) : receiver);
            return;
        }
        if (name.equals("kof_now")) {
            stack.add(new JsIr.JsCall(new JsIr.JsMember(new JsIr.JsIdentifier("Date"), "now"), List.of()));
            return;
        }
        String fn = runtimeJsName(name);
        if (name.startsWith("kof_io_") || name.equals("kof_read_line")
                || name.equals("kof_read_file") || name.equals("kof_write_file")) {
            registerIoRuntime(fn);
        } else {
            registerRuntime(fn);
        }
        List<JsIr.JsExpression> callArgs = new ArrayList<>();
        if (name.startsWith("kof_io_") && receiver != null) {
            callArgs.add(receiver);
        }
        callArgs.addAll(args);
        JsIr.JsExpression call = new JsIr.JsCall(new JsIr.JsIdentifier(fn), callArgs);
        if (Type.isVoid(kc.returnType())) {
            throw new StatementEnd(call);
        }
        stack.add(call);
    }

    private String runtimeJsName(String kofName) {
        StringBuilder sb = new StringBuilder("kof");
        boolean upper = false;
        for (int i = 3; i < kofName.length(); i++) {
            char c = kofName.charAt(i);
            if (c == '_') {
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return sb.toString();
    }

    // ── Plumbing ────────────────────────────────────────────────────

    private void registerRuntime(String fn) {
        if (!runtimeImports.contains(fn)) runtimeImports.add(fn);
    }

    private void registerIoRuntime(String fn) {
        if (!ioRuntimeImports.contains(fn)) ioRuntimeImports.add(fn);
    }

    private JsIr.JsExpression pop(List<Object> stack) {
        Object top = popRaw(stack);
        if (top instanceof NewPending np) {
            return new JsIr.JsNew(new JsIr.JsIdentifier(np.typeName()), List.of());
        }
        if (top instanceof DupMarker) {
            // The frontend omits the <init> call when the class has only the
            // implicit default constructor; complete the pending new.
            Object base = popRaw(stack);
            if (base instanceof NewPending np) {
                return new JsIr.JsNew(new JsIr.JsIdentifier(np.typeName()), List.of());
            }
            throw new IllegalStateException("KofJS: DupMarker without NewPending; stack=" + stack);
        }
        return (JsIr.JsExpression) top;
    }

    private Object popRaw(List<Object> stack) {
        if (stack.isEmpty()) {
            throw new IllegalStateException("KofJS: expression stack underflow");
        }
        return stack.remove(stack.size() - 1);
    }

    private boolean isPureDuplicate(JsIr.JsExpression expr) {
        return expr instanceof JsIr.JsIdentifier || expr instanceof JsIr.JsThis
                || expr instanceof JsIr.JsMember || expr instanceof JsIr.JsNull
                || expr instanceof JsIr.JsNumber || expr instanceof JsIr.JsString;
    }

    private String ownerInternalName(Type type) {
        if (type instanceof Type.ClassType ct) return ct.internalName();
        return "";
    }

    private String classPackage(Type type) {
        if (type instanceof Type.ClassType ct) return ct.packageName();
        return "";
    }

    private String className(Type type) {
        if (type instanceof Type.ClassType ct) return ct.name();
        return "";
    }

    private static String jsClassName(String internalName) {
        if (internalName == null || internalName.isEmpty()) return "Object";
        return sanitizeName(internalName.replace('/', '_'));
    }

    private static String sanitizeName(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '$') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String result = sb.toString();
        if (result.isEmpty() || Character.isDigit(result.charAt(0))) {
            result = "_" + result;
        }
        if (RESERVED.contains(result)) {
            result = "_" + result;
        }
        return result;
    }

    private List<String> parameterNames(MethodCtx ctx) {
        if ("main".equals(ctx.methodName) && ctx.paramCount == 1) {
            // The injected String[] parameter is not a source parameter.
            return List.of();
        }
        List<String> names = new ArrayList<>();
        int start = ctx.instanceMethod ? 1 : 0;
        for (int i = start; i < ctx.localNames.size() && names.size() < ctx.paramCount; i++) {
            String name = ctx.localNames.get(i);
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }

    private void insertSuperCall(IRClass clazz, List<JsIr.JsStatement> body) {
        if (clazz.superName() == null || "java/lang/Object".equals(clazz.superName())
                || "java/lang/Record".equals(clazz.superName())) {
            return;
        }
        boolean hasSuper = body.stream().anyMatch(stmt -> stmt instanceof JsIr.JsExprStmt es
                && es.expression() instanceof JsIr.JsCall call
                && call.callee() instanceof JsIr.JsIdentifier id && "super".equals(id.name()));
        if (!hasSuper) {
            body.add(0, new JsIr.JsExprStmt(new JsIr.JsCall(new JsIr.JsIdentifier("super"), List.of())));
        }
    }

    /**
     * JavaScript class fields are undefined until assigned; JVM instance fields
     * default to 0/false/null. Field defaults are emitted at the start of every
     * constructor (after the super call) to preserve Kof/JVM semantics.
     */
    private void insertFieldDefaults(IRClass clazz, List<JsIr.JsStatement> body) {
        List<JsIr.JsStatement> defaults = new ArrayList<>();
        for (IRField field : clazz.fields()) {
            if ((field.accessFlags() & AccessFlags.STATIC) != 0) continue;
            JsIr.JsExpression value = field.initialValue() != null
                    ? literalExpr(new KofLoadLiteral(field.type(), field.initialValue()))
                    : defaultForType(field.type());
            defaults.add(new JsIr.JsExprStmt(new JsIr.JsBinary(
                    new JsIr.JsMember(new JsIr.JsThis(), jsFieldName(clazz, field.name())), "=", value)));
        }
        if (defaults.isEmpty()) return;
        int insertAt = 0;
        for (int i = 0; i < body.size(); i++) {
            if (body.get(i) instanceof JsIr.JsExprStmt es
                    && es.expression() instanceof JsIr.JsCall call
                    && call.callee() instanceof JsIr.JsIdentifier id && "super".equals(id.name())) {
                insertAt = i + 1;
                break;
            }
        }
        body.addAll(insertAt, defaults);
    }

    private JsIr.JsExpression defaultForType(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return switch (Type.canonicalPrimitiveName(pt.name())) {
                case "bool" -> new JsIr.JsNumber("0");
                default -> new JsIr.JsNumber("0");
            };
        }
        return new JsIr.JsNull();
    }

    // ── Runtime emission ────────────────────────────────────────────

    private static final String CORE_RUNTIME = """
            // KofJS core runtime — platform-neutral helpers.
            // Generated by the Kof compiler (KofJS backend).
            // This module is not a VM: it only provides operations that
            // JavaScript does not represent directly.

            export function kofPrintln(x) {
                console.log(x);
            }

            export function kofListNew() {
                return [];
            }

            export function kofListAdd(list, value) {
                list.push(value);
            }

            export function kofListGet(list, index) {
                if (index < 0 || index >= list.length) {
                    throw new Error("Index out of bounds: " + index + " (size " + list.length + ")");
                }
                return list[index];
            }

            export function kofListSet(list, index, value) {
                if (index < 0 || index >= list.length) {
                    throw new Error("Index out of bounds: " + index + " (size " + list.length + ")");
                }
                list[index] = value;
            }

            export function kofListSize(list) {
                return list.length;
            }

            export function kofListContains(list, value) {
                return list.includes(value) ? 1 : 0;
            }

            export function kofListIsEmpty(list) {
                return list.length === 0 ? 1 : 0;
            }

            export function kofListRemove(list, index) {
                if (index < 0 || index >= list.length) {
                    throw new Error("Index out of bounds: " + index + " (size " + list.length + ")");
                }
                return list.splice(index, 1)[0];
            }

            export function kofListClear(list) {
                list.length = 0;
            }

            export function kofNow() {
                return Date.now();
            }
            """;

    private static final String IO_RUNTIME = """
            // KofJS platform runtime — filesystem/console operations for the
            // KofJS target. Generated by the Kof compiler (KofJS backend).
            //
            // This module delegates to the kof_platform object exposed by the
            // Kof process (dev.kof.runtime.KofJsRunner) when Kof executes the
            // generated module with its embedded JavaScript engine. The
            // generated .mjs files are standard ES2022+ modules and do not
            // depend on Node.js or any other external runtime.

            export function kofPrint(x) {
                kof_platform.print(String(x));
            }

            export function kofReadLine() {
                return kof_platform.readLine();
            }

            export function kofReadFile(p) {
                return kof_platform.readFile(p);
            }

            export function kofWriteFile(p, content) {
                return kof_platform.writeFile(p, content);
            }

            export function kofIoFileExists(p) {
                return kof_platform.fileExists(p);
            }

            export function kofIoFileIsFile(p) {
                return kof_platform.fileIsFile(p);
            }

            export function kofIoFileIsDir(p) {
                return kof_platform.fileIsDir(p);
            }

            export function kofIoReadText(p) {
                return kof_platform.readText(p);
            }

            export function kofIoWriteText(p, content) {
                return kof_platform.writeText(p, content);
            }

            export function kofIoAppendText(p, content) {
                return kof_platform.appendText(p, content);
            }

            export function kofIoReadBytes(p) {
                return kof_platform.readBytes(p);
            }

            export function kofIoWriteBytes(p, bytes) {
                return kof_platform.writeBytes(p, bytes);
            }

            export function kofIoAppendBytes(p, bytes) {
                return kof_platform.appendBytes(p, bytes);
            }

            export function kofIoDelete(p) {
                return kof_platform.delete(p);
            }

            export function kofIoFileSize(p) {
                return kof_platform.fileSize(p);
            }

            export function kofIoFileName(p) {
                return kof_platform.fileName(p);
            }

            export function kofIoPathParent(p) {
                return kof_platform.pathParent(p);
            }

            export function kofIoPathFileName(p) {
                return kof_platform.pathFileName(p);
            }

            export function kofIoPathExtension(p) {
                return kof_platform.pathExtension(p);
            }

            export function kofIoPathNormalize(p) {
                return kof_platform.pathNormalize(p);
            }

            export function kofIoPathResolve(base, child) {
                return kof_platform.pathResolve(base, child);
            }

            export function kofIoPathIsAbsolute(p) {
                return kof_platform.pathIsAbsolute(p);
            }

            export function kofIoPathToAbsolute(p) {
                return kof_platform.pathToAbsolute(p);
            }

            export function kofIoDirCreate(p) {
                return kof_platform.dirCreate(p);
            }

            export function kofIoDirCreateDirs(p) {
                return kof_platform.dirCreateDirs(p);
            }

            export function kofIoDirDelete(p) {
                return kof_platform.dirDelete(p);
            }

            export function kofIoDirList(p) {
                return kof_platform.dirList(p);
            }
            """;

private void writeRuntime(Path outputDir) throws IOException {
        Path core = outputDir.resolve("kof-runtime.mjs");
        if (!Files.exists(core)) {
            Files.writeString(core, CORE_RUNTIME);
        }
        Path node = outputDir.resolve("kof-runtime-io.mjs");
        if (!Files.exists(node)) {
            Files.writeString(node, IO_RUNTIME);
        }
    }

    private void writeSourceMap(IRModule module, Path outputDir, String fileName) throws IOException {
        String source = module.name().isEmpty() ? "Default.kf" : module.name() + ".kf";
        String map = "{\"version\":3,\"file\":\"" + fileName
                + "\",\"sources\":[\"" + source + "\"],\"names\":[],\"mappings\":\"\"}";
        Files.writeString(outputDir.resolve(fileName + ".map"), map);
    }
}