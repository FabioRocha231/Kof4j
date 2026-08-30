package dev.kof.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class NativeBackend implements Backend {

    private final Target target;
    private final Map<LabelId, String> labelMap = new HashMap<>();
    private int labelCounter = 0;
    private final List<String[]> stringLiterals = new ArrayList<>();
    private int stringCounter = 0;
    private Type lastPushedType = Type.UnknownType.UNKNOWN;
    private IRClass currentClass = null;
    private boolean usesDb = false;
    private boolean usesMysql = false;
    private final Map<String, String> functionMangleMap = new HashMap<>();
    private final Map<String, ClassLayout> layoutCache = new HashMap<>();
    private Map<String, IRClass> allClassesMap = new HashMap<>();

    public NativeBackend() { this(Target.NATIVE); }
    public NativeBackend(Target target) { this.target = target; }

    private String resolveLabel(LabelId id) {
        return labelMap.computeIfAbsent(id, k -> ".Lkof_" + (labelCounter++));
    }

    private String sanitizeName(String name) {
        return name.replace("/", "_").replace(".", "_").replace("-", "_")
                .replace("<", "").replace(">", "");
    }


    /**
     * JSN002: coleta as tabelas de schema JSON por classe (nome+offset+
     * tipo de cada campo, inclusive herdados via ClassLayout). Campos de
     * tipos nao suportados (FP, List, Map) sao omitidos — o gate no
     * CompilerDriver diagnostica essas classes antes delas chegarem aqui.
     * Os nomes dos campos sao internados AQUI (antes de emitStringData).
     */
    private record JsonSchemaEntry(String tokenCstr, long offset, long typeCode, String className,
                                    String auxCstr) {}
    private record JsonSchemaTable(String tableLabel, String classCstr, long totalSize,
                                   java.util.List<JsonSchemaEntry> entries) {}

    private final java.util.List<JsonSchemaTable> jsonSchemas = new java.util.ArrayList<>();

    private Long jsonFieldTypeCode(Type t) {
        if (t instanceof Type.PrimitiveType pt) {
            return switch (Type.canonicalPrimitiveName(pt.name())) {
                case "int", "char", "byte", "short" -> 1L;
                case "long" -> 2L;
                case "bool" -> 3L;
                default -> null; // float/double: gap FP (JSN001)
            };
        }
        if (BuiltinTypes.isString(t)) return 4L;
        if (t instanceof Type.ClassType ct) {
            String simple = ct.name();
            for (IRClass c : allClassesMap.values()) {
                if (c.name().equals(simple) || c.name().endsWith("/" + simple)) return 5L;
            }
        }
        return null;
    }

    private void collectJsonSchemas() {
        jsonSchemas.clear();
        for (IRClass clazz : allClassesMap.values()) {
            if (clazz.fields().isEmpty()) continue;
            ClassLayout layout = getLayout(clazz);
            java.util.List<JsonSchemaEntry> entries = new java.util.ArrayList<>();
            boolean any = false;
            boolean allSupported = true;
            for (FieldLayout f : layout.fields()) {
                Long code = jsonFieldTypeCode(f.type());
                if (code == null) { allSupported = false; continue; }
                if (f.type() instanceof Type.ClassType ct) {
                    // campo aninhado: so se a classe alvo tambem tiver tabela
                    boolean has = false;
                    for (IRClass c : allClassesMap.values()) {
                        if (c.name().equals(ct.name()) || c.name().endsWith("/" + ct.name())) {
                            has = !getLayout(c).fields().isEmpty();
                            break;
                        }
                    }
                    if (!has) { allSupported = false; continue; }
                }
                // token '"nome":' serve encode (parte literal) e decode (busca)
                String tokLabel = internString("\"" + f.name() + "\":");
                String auxCstr = null;
                if (code == 5L && f.type() instanceof Type.ClassType ct) {
                    String cn = ct.packageName().isEmpty() ? ct.name()
                            : ct.packageName() + "." + ct.name();
                    auxCstr = internString(cn);
                }
                entries.add(new JsonSchemaEntry(tokLabel, f.offset(), code, f.name(), auxCstr));
                any = true;
            }
            if (!any || !allSupported) continue;
            String tableLabel = ".Lsch_" + sanitizeName(clazz.name());
            String classCstr = internString(clazz.name());
            jsonSchemas.add(new JsonSchemaTable(tableLabel, classCstr,
                    layout.totalSize(), java.util.List.copyOf(entries)));
        }
    }

    /** Emite as tabelas de schema + registro + finder (apos emitStringData). */
    private void emitJsonSchemaData(StringBuilder sb) {
        if (jsonSchemas.isEmpty()) return;
        sb.append(".section .data\n");
        for (JsonSchemaTable t : jsonSchemas) {
            sb.append(t.tableLabel()).append(":\n");
            sb.append("    .quad ").append(t.totalSize()).append("\n");
            sb.append("    .quad ").append(t.entries().size()).append("\n");
            for (JsonSchemaEntry e : t.entries()) {
                sb.append("    .quad ").append(e.tokenCstr()).append("\n");
                sb.append("    .quad ").append(e.offset()).append("\n");
                sb.append("    .quad ").append(e.typeCode()).append("\n");
                sb.append("    .quad ").append(e.auxCstr() == null ? 0 : e.auxCstr()).append("\n");
            }
        }
        sb.append(".Lsch_registry:\n");
        for (JsonSchemaTable t : jsonSchemas) {
            sb.append("    .quad ").append(t.classCstr()).append("\n");
            sb.append("    .quad ").append(t.tableLabel()).append("\n");
        }
        sb.append("    .quad 0\n");
        sb.append("""
            .section .text
            .globl kof_json_schema_find
            .type kof_json_schema_find, @function
            kof_json_schema_find:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx             # nome C-string
                leaq .Lsch_registry(%rip), %r9
            .Lschf_loop:
                movq (%r9), %rax            # name cstr da entrada
                testq %rax, %rax
                jz .Lschf_notfound
                xorq %rcx, %rcx
            .Lschf_cmp:
                movzbl (%rbx,%rcx), %edx
                movzbl (%rax,%rcx), %esi
                cmpl %esi, %edx
                jne .Lschf_next
                testl %edx, %edx
                jz .Lschf_found
                incq %rcx
                jmp .Lschf_cmp
            .Lschf_next:
                addq $16, %r9
                jmp .Lschf_loop
            .Lschf_found:
                movq 8(%r9), %rax           # table ptr
                jmp .Lschf_exit
            .Lschf_notfound:
                xorl %eax, %eax
            .Lschf_exit:
                popq %r12
                popq %rbx
                ret
            """);
    }

    /** Tabela de schema para uma classe (ou null se ausente), pelo nome. */
    private String schemaLabelFor(String className) {
        for (JsonSchemaTable t : jsonSchemas) {
            if (t.classCstr().equals(className)) return t.tableLabel();
        }
        return null;
    }

    private String internString(String value) {
        for (String[] entry : stringLiterals) {
            if (entry[0].equals(value)) return entry[1];
        }
        String label = ".Lstr_" + (stringCounter++);
        stringLiterals.add(new String[]{value, label});
        return label;
    }

    private ClassLayout getLayout(IRClass clazz) {
        return layoutCache.computeIfAbsent(clazz.name(), k ->
            ClassLayout.buildWithSuper(clazz, name -> allClassesMap.get(name)));
    }

    private ClassLayout getLayoutForType(Type type) {
        if (type instanceof Type.ClassType ct) {
            String name = ct.name();
            for (IRClass clazz : allClassesMap.values()) {
                if (clazz.name().equals(name) || clazz.name().endsWith("/" + name) || name.endsWith("/" + clazz.name())) {
                    return getLayout(clazz);
                }
            }
        }
        return null;
    }

    @Override
    public void emit(IRModule module, Path outputDir) throws IOException {
        if (target == Target.NATIVE_RISCV64) {
            emitRiscv(module, outputDir);
            return;
        }
        if (target == Target.NATIVE_AARCH64) {
            emitAarch64(module, outputDir);
            return;
        }
        if (module.classes().isEmpty()) return;
        labelCounter = 0;
        labelMap.clear();
        stringLiterals.clear();
        stringCounter = 0;
        functionMangleMap.clear();
        layoutCache.clear();
        allClassesMap.clear();
        for (IRClass clazz : module.classes()) {
            allClassesMap.put(clazz.name(), clazz);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(".section .data\n");
        for (IRClass clazz : module.classes()) {
            currentClass = clazz;
            getLayout(clazz);
            collectStrings(clazz);
        }
        collectJsonSchemas();
        emitStringData(sb);
        emitJsonSchemaData(sb);
        for (IRClass clazz : module.classes()) {
            currentClass = clazz;
            emitMethodTable(sb, clazz);
        }
        sb.append("\n.section .text\n");
        sb.append(NativeRuntime.generateRuntimeAssembly());
        NativeRuntime.emitInitObject(sb);
        // kof.db on the native target: link the DB client library directly
        // (no JDBC driver) — the same direct-.so pattern as kof-webview.
        for (IRClass clazz : module.classes()) {
            for (IRMethod method : clazz.methods()) {
                for (IRBasicBlock block : method.basicBlocks()) {
                    List<KofOperation> ops = block.operations();
                    for (int i = 0; i < ops.size(); i++) {
                        KofOperation op = ops.get(i);
                        if (op instanceof KofCall kc && kc.methodName().startsWith("kof_db_")) {
                            usesDb = true;
                            if (kc.methodName().equals("kof_db_connect")
                                    || kc.methodName().equals("kof_db_connect2")) {
                                usesMysql |= connectsToMysql(i, ops);
                            }
                        }
                    }
                }
            }
        }
        if (usesDb) {
            NativeRuntime.emitDbSqlite(sb);
        }
        IRClass mainClass = null;
        // pré-registro do mangle de TODOS os métodos antes de emitir —
        // forward reference de função top-level (callee depois do caller)
        // não pode cair no fallback não-mangled (undefined reference no ld)
        for (IRClass clazz : module.classes()) {
            for (IRMethod method : clazz.methods()) {
                if ("<clinit>".equals(method.name())) continue;
                String mangled = sanitizeName(clazz.name()) + "_" + sanitizeName(method.name());
                if ("<init>".equals(method.name())) {
                    mangled += "_" + method.parameterTypes().size();
                }
                functionMangleMap.putIfAbsent(method.name(), mangled);
            }
        }
        for (IRClass clazz : module.classes()) {
            currentClass = clazz;
            for (IRMethod method : clazz.methods()) {
                if ("main".equals(method.name())) {
                    mainClass = clazz;
                    continue;
                }
                emitMethod(sb, clazz, method);
            }
        }
        if (mainClass != null) {
            currentClass = mainClass;
            for (IRMethod method : mainClass.methods()) {
                if ("main".equals(method.name())) {
                    emitMethod(sb, mainClass, method);
                }
            }
            emitStart(sb, mainClass);
        }
        String mainClassName = mainClass != null ? mainClass.name() : module.classes().getFirst().name();
        Path asmFile = outputDir.resolve(mainClassName + ".s");
        Path binFile = outputDir.resolve(mainClassName);
        Files.createDirectories(asmFile.getParent());
        Files.writeString(asmFile, sb.toString());
        try { Files.writeString(java.nio.file.Path.of("/tmp/kof_asm_debug.s"), sb.toString(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING); } catch(Exception ignore){}
        System.err.println("NativeBackend: Generated " + asmFile + " (" + Files.size(asmFile) + " bytes)");
        assemble(asmFile, binFile);
    }

    private void collectStrings(IRClass clazz) {
        for (IRMethod method : clazz.methods()) {
            for (IRBasicBlock block : method.basicBlocks()) {
                for (KofOperation op : block.operations()) {
                    if (op instanceof KofLoadLiteral lit && lit.value() instanceof String s) {
                        internString(s);
                    }
                }
            }
        }
    }

    private List<String> collectVirtualMethods(IRClass clazz) {
        List<String> methods = new ArrayList<>();
        List<String> methodNames = new ArrayList<>();
        java.util.Queue<String> queue = new java.util.LinkedList<>();
        java.util.Set<String> visited = new java.util.HashSet<>();
        String current = clazz.superName();
        while (current != null && !current.isEmpty() && !"java/lang/Object".equals(current)) {
            IRClass superClazz = allClassesMap.get(current);
            if (superClazz == null) break;
            for (IRMethod m : superClazz.methods()) {
                if (!"<init>".equals(m.name()) && !"<clinit>".equals(m.name())
                        && !m.name().startsWith("kof_")) {
                    if (!methodNames.contains(m.name())) {
                        methodNames.add(m.name());
                        methods.add(sanitizeName(superClazz.name()) + "_" + sanitizeName(m.name()));
                    }
                }
            }
            for (String iface : superClazz.interfaces()) {
                if (!visited.contains(iface)) {
                    visited.add(iface);
                    queue.add(iface);
                }
            }
            current = superClazz.superName();
        }
        while (!queue.isEmpty()) {
            String ifaceName = queue.poll();
            IRClass ifaceClazz = allClassesMap.get(ifaceName);
            if (ifaceClazz == null) continue;
            for (IRMethod m : ifaceClazz.methods()) {
                if (!"<init>".equals(m.name()) && !"<clinit>".equals(m.name())
                        && !m.name().startsWith("kof_")) {
                    if (!methodNames.contains(m.name())) {
                        methodNames.add(m.name());
                        methods.add(sanitizeName(ifaceClazz.name()) + "_" + sanitizeName(m.name()));
                    }
                }
            }
            for (String iface : ifaceClazz.interfaces()) {
                if (!visited.contains(iface)) {
                    visited.add(iface);
                    queue.add(iface);
                }
            }
        }
        for (IRMethod m : clazz.methods()) {
            if (!"<init>".equals(m.name()) && !"<clinit>".equals(m.name())
                    && !m.name().startsWith("kof_")) {
                int idx = methodNames.indexOf(m.name());
                if (idx >= 0) {
                    methods.set(idx, sanitizeName(clazz.name()) + "_" + sanitizeName(m.name()));
                } else {
                    methodNames.add(m.name());
                    methods.add(sanitizeName(clazz.name()) + "_" + sanitizeName(m.name()));
                }
            }
        }
        return methods;
    }

    private void emitMethodTable(StringBuilder sb, IRClass clazz) {
        List<String> methods = collectVirtualMethods(clazz);
        if (methods.isEmpty()) {
            sb.append(".balign 8\n");
            sb.append(sanitizeName(clazz.name()) + "_vtable:\n");
            sb.append("    .quad 0\n");
            return;
        }
        NativeRuntime.generateMethodTable(sb, sanitizeName(clazz.name()), methods);
    }

    private int findVirtualMethodIndex(String ownerTypeName, String methodName) {
        for (IRClass clazz : allClassesMap.values()) {
            if (clazz.name().equals(ownerTypeName) || clazz.name().endsWith("/" + ownerTypeName)
                    || ownerTypeName.endsWith("/" + clazz.name()) || ownerTypeName.equals(sanitizeName(clazz.name()))) {
                List<String> methods = collectVirtualMethods(clazz);
                String mangled = sanitizeName(clazz.name()) + "_" + sanitizeName(methodName);
                for (int i = 0; i < methods.size(); i++) {
                    if (methods.get(i).equals(mangled)) {
                        return i;
                    }
                }
                for (IRMethod m : clazz.methods()) {
                    if (m.name().equals(methodName) && !"<init>".equals(m.name()) && !"<clinit>".equals(m.name())) {
                        String m2 = sanitizeName(clazz.name()) + "_" + sanitizeName(m.name());
                        for (int i = 0; i < methods.size(); i++) {
                            if (methods.get(i).equals(m2)) {
                                return i;
                            }
                        }
                    }
                }
                break;
            }
        }
        return -1;
    }

    private void emitStringData(StringBuilder sb) {
        for (String[] entry : stringLiterals) {
            String value = entry[0];
            String label = entry[1];
            String escaped = value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\t", "\\t");
            sb.append(label).append(": .asciz \"").append(escaped).append("\"\n");
        }
        sb.append(".Lnewline: .asciz \"\\n\"\n");
        sb.append(".Lkof_str_true: .asciz \"true\"\n");
        sb.append(".Lkof_str_false: .asciz \"false\"\n");
        sb.append(".balign 8\n");
        sb.append("kof_super_table:\n");
        for (IRClass clazz : allClassesMap.values()) {
            if (clazz.typeId() == 0) continue;
            int superTypeId = 0;
            if (clazz.superName() != null && !clazz.superName().isEmpty()) {
                String superSimple = clazz.superName().substring(clazz.superName().lastIndexOf('/') + 1);
                for (IRClass other : allClassesMap.values()) {
                    if (other.name().equals(clazz.superName()) || other.name().endsWith("/" + superSimple)
                            || superSimple.equals(sanitizeName(other.name()))) {
                        superTypeId = other.typeId();
                        break;
                    }
                }
            }
            sb.append("    .long ").append(clazz.typeId()).append(", ").append(superTypeId).append("\n");
        }
        sb.append("    .long 0, 0\n");
    }

    private void emitMethod(StringBuilder sb, IRClass clazz, IRMethod method) {
        if ("<clinit>".equals(method.name())) return;

        currentClass = clazz;

        String mangled = sanitizeName(clazz.name()) + "_" + sanitizeName(method.name());
        if ("<init>".equals(method.name())) {
            mangled += "_" + method.parameterTypes().size();
        }
        functionMangleMap.put(method.name(), mangled);
        sb.append("\n.globl ").append(mangled).append("\n");
        sb.append(".type ").append(mangled).append(", @function\n");
        sb.append(mangled).append(":\n");

        sb.append("    pushq %rbp\n");
        sb.append("    movq %rsp, %rbp\n");

        int maxSlot = method.localVariables().stream()
                .mapToInt(IRLocalVariable::index).max().orElse(0);
        // Scan for CONSTRUCTOR calls with stack args to reserve frame space
        int maxCtorStackArgs = 0;
        for (IRBasicBlock bb : method.basicBlocks()) {
            for (KofOperation op : bb.operations()) {
                if (op instanceof KofCall kc && kc.kind() == KofCallKind.CONSTRUCTOR
                        && "<init>".equals(kc.methodName())) {
                    int sa = Math.max(0, kc.parameterTypes().size() - 5);
                    maxCtorStackArgs = Math.max(maxCtorStackArgs, sa);
                }
            }
        }
        int extraFrame = maxCtorStackArgs > 0 ? 256 + maxCtorStackArgs * 8 : 0;
        int frameSize = Math.max((maxSlot + 1) * 8, 16) + extraFrame;
        frameSize = (frameSize + 15) & ~15;
        if (frameSize > 0) {
            sb.append("    subq $").append(frameSize).append(", %rsp\n");
        }

        int intArgIdx = 0;
        for (IRLocalVariable lv : method.localVariables()) {
            if (lv.name().equals("this")) {
                sb.append("    movq %rdi, -").append((lv.index() + 1) * 8).append("(%rbp)\n");
                intArgIdx++;
                continue;
            }
            String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
            if (intArgIdx < 6) {
                sb.append("    movq ").append(intRegs[intArgIdx]).append(", -").append((lv.index() + 1) * 8).append("(%rbp)\n");
            } else {
                // Args beyond register capacity are on the stack.
                // After push %rbp, stack layout is: [saved_rbp][ret_addr][arg7][arg8]...
                // Stack arg index: 7th arg = 16(%rbp), 8th = 24(%rbp), etc.
                int stackOffset = 16 + (intArgIdx - 6) * 8;
                sb.append("    movq ").append(stackOffset).append("(%rbp), %rax\n");
                sb.append("    movq %rax, -").append((lv.index() + 1) * 8).append("(%rbp)\n");
            }
            intArgIdx++;
        }

        boolean endsWithReturn = false;
        for (IRBasicBlock block : method.basicBlocks()) {
            for (KofOperation op : block.operations()) {
                if (op instanceof KofReturn || op instanceof KofReturnVoid) endsWithReturn = true;
                emitOperation(sb, op, method);
            }
        }

        if (!endsWithReturn) {
            sb.append("    movq %rbp, %rsp\n");
            sb.append("    popq %rbp\n");
            sb.append("    ret\n");
        }
    }

    private void emitOperation(StringBuilder sb, KofOperation op, IRMethod currentMethod) {
        if (op instanceof KofLoadLiteral lit) {
            lastPushedType = lit.type();
        } else if (op instanceof KofLoadLocal ll) {
            lastPushedType = ll.type();
        } else if (op instanceof KofLoadField lf) {
            lastPushedType = lf.fieldType();
        } else if (op instanceof KofArrayLength) {
            lastPushedType = Type.PrimitiveType.INT;
        } else if (op instanceof KofBinary kb) {
            lastPushedType = kb.operandType();
        } else if (op instanceof KofUnary ku) {
            lastPushedType = ku.operandType();
        } else if (op instanceof KofCall kc) {
            lastPushedType = kc.returnType();
        }

        switch (op) {
            case KofLoadLiteral lit -> emitLoadLiteral(sb, lit);
            case KofLoadLocal ll -> {
                sb.append("    movq -").append((ll.index() + 1) * 8).append("(%rbp), %rax\n");
                sb.append("    pushq %rax\n");
            }
            case KofStoreLocal sl -> {
                sb.append("    popq %rax\n");
                sb.append("    movq %rax, -").append((sl.index() + 1) * 8).append("(%rbp)\n");
            }
            case KofLoadField lf -> {
                if (lf.ownerType() instanceof Type.ClassType ctLF && "MemEntry".equals(ctLF.name()) && "key".equals(lf.name())) {
                }
                sb.append("    popq %rax\n");
                int offset = resolveFieldOffset(lf.ownerType(), lf.name());
                sb.append("    movq ").append(offset).append("(%rax), %rax\n");
                sb.append("    pushq %rax\n");
            }
            case KofStoreField sf -> {
                sb.append("    popq %rax\n");
                sb.append("    popq %rcx\n");
                int offset = resolveFieldOffset(sf.ownerType(), sf.name());
                sb.append("    movq %rax, ").append(offset).append("(%rcx)\n");
            }
            case KofBinary kb -> emitBinary(sb, kb);
            case KofUnary ku -> emitUnary(sb, ku);
            case KofReturn kr -> {
                sb.append("    popq %rax\n");
                sb.append("    movq %rbp, %rsp\n");
                sb.append("    popq %rbp\n");
                sb.append("    ret\n");
            }
            case KofReturnVoid rv -> {
                sb.append("    movq %rbp, %rsp\n");
                sb.append("    popq %rbp\n");
                sb.append("    ret\n");
            }
            case KofLabel kl -> sb.append(resolveLabel(kl.label())).append(":\n");
            case KofCatchStart kcs -> {
                sb.append(resolveLabel(kcs.handlerLabel())).append(":\n");
                sb.append("    addq $32, %rsp\n");
                sb.append("    movq %rdi, -").append((kcs.localIndex() + 1) * 8).append("(%rbp)\n");
            }
            case KofTryStart kts -> {
                sb.append(resolveLabel(kts.startLabel())).append(":\n");
                sb.append("    subq $32, %rsp\n");
                sb.append("    leaq ").append(resolveLabel(kts.handlerLabel())).append("(%rip), %rax\n");
                sb.append("    movq %rax, 0(%rsp)\n");
                sb.append("    movq %rsp, 8(%rsp)\n");
                sb.append("    movq %rbp, 16(%rsp)\n");
                sb.append("    movq kof_exc_chain(%rip), %rcx\n");
                sb.append("    movq %rcx, 24(%rsp)\n");
                sb.append("    movq %rsp, kof_exc_chain(%rip)\n");
            }
            case KofTryEnd kte -> {
                sb.append("    movq 24(%rsp), %rcx\n");
                sb.append("    movq %rcx, kof_exc_chain(%rip)\n");
                sb.append("    addq $32, %rsp\n");
            }
            case KofJump kj -> sb.append("    jmp ").append(resolveLabel(kj.target())).append("\n");
            case KofConditionalJump kc -> emitConditionalJump(sb, kc);
            case KofCall kc -> emitCall(sb, kc);
            case KofNewObject no -> emitNewObject(sb, no);
            case KofDup dup -> sb.append("    movq (%rsp), %rax\n    pushq %rax\n");
            case KofDupX1 x1 -> sb.append("""
                    movq (%rsp), %rax
                    movq 8(%rsp), %rbx
                    pushq %rax
                    pushq %rbx
                    pushq %rax
                """.stripIndent());
            case KofDupX2 x2 -> sb.append("""
                    movq (%rsp), %rax
                    movq 8(%rsp), %rbx
                    movq 16(%rsp), %rcx
                    pushq %rax
                    pushq %rcx
                    pushq %rbx
                    pushq %rax
                """.stripIndent());
            case KofPop pop -> sb.append("    addq $8, %rsp\n");
            case KofGetStatic gs -> { }
            case KofPutStatic ps -> sb.append("    addq $8, %rsp\n");
            case KofCheckCast cc -> { }
            case KofInstanceOf io -> {
                int targetTypeId = 0;
                if (BuiltinTypes.isString(io.type())) {
                    targetTypeId = NativeRuntime.KOF_STRING_TYPE_ID;
                } else if (io.type() instanceof Type.ClassType ct) {
                    for (IRClass clazz : allClassesMap.values()) {
                        if (clazz.name().equals(ct.name()) || clazz.name().endsWith("/" + ct.name())
                                || ct.name().endsWith("/" + clazz.name()) || ct.name().equals(sanitizeName(clazz.name()))) {
                            targetTypeId = clazz.typeId();
                            break;
                        }
                    }
                }
                sb.append("    popq %rdi\n");
                sb.append("    movl $").append(targetTypeId).append(", %esi\n");
                sb.append("    call kof_instanceof\n");
                sb.append("    pushq %rax\n");
            }
            case KofNewArray na -> emitNewArray(sb, na);
            case KofArrayLoad al -> emitArrayLoad(sb, al);
            case KofArrayStore as -> emitArrayStore(sb, as);
            case KofArrayLength al -> emitArrayLength(sb);
            case KofThrow thr -> {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_throw_string\n");
            }
            default -> { }
        }
    }

    private void emitNewObject(StringBuilder sb, KofNewObject no) {
        ClassLayout layout = null;
        String className = null;
        int typeId = 0;
        if (no.type() instanceof Type.ClassType ct) {
            className = ct.name();
            for (IRClass clazz : allClassesMap.values()) {
                if (clazz.name().equals(className) || clazz.name().endsWith("/" + className)
                        || className.endsWith("/" + clazz.name()) || className.equals(sanitizeName(clazz.name()))) {
                    layout = getLayout(clazz);
                    className = clazz.name();
                    typeId = clazz.typeId();
                    break;
                }
            }
        }
        int size = layout != null ? layout.totalSize() : ClassLayout.HEADER_SIZE + 64;
        sb.append("    movq $").append(size).append(", %rdi\n");
        sb.append("    call kof_alloc\n");
        if (className != null) {
            String mangled = sanitizeName(className);
            sb.append("    movq %rax, %rdi\n");
            sb.append("    movl $").append(typeId).append(", %esi\n");
            sb.append("    leaq ").append(mangled).append("_vtable(%rip), %rdx\n");
            sb.append("    call kof_init_object\n");
        }
        sb.append("    pushq %rax\n");
    }

    private int elementTypeSize(Type elemType) {
        return switch (elemType) {
            case Type.PrimitiveType pt -> switch (pt.name()) {
                case "byte", "Byte", "bool", "Bool", "boolean" -> 1;
                case "short", "Short" -> 2;
                case "int", "Int", "char", "Char" -> 4;
                case "long", "Long" -> 8;
                case "float", "Float" -> 4;
                case "double", "Double" -> 8;
                default -> 8;
            };
            default -> 8;
        };
    }

    private void emitNewArray(StringBuilder sb, KofNewArray na) {
        sb.append("    popq %rdi\n");
        sb.append("    movl $").append(elementTypeSize(na.elementType())).append(", %esi\n");
        sb.append("    call kof_array_alloc\n");
        sb.append("    pushq %rax\n");
    }

    private void emitArrayLoad(StringBuilder sb, KofArrayLoad al) {
        sb.append("    popq %rsi\n");
        sb.append("    popq %rdi\n");
        sb.append("    call kof_array_get\n");
        sb.append("    pushq %rax\n");
    }

    private void emitArrayStore(StringBuilder sb, KofArrayStore as) {
        sb.append("    popq %rdx\n");
        sb.append("    popq %rsi\n");
        sb.append("    popq %rdi\n");
        sb.append("    call kof_array_set\n");
    }

    private void emitArrayLength(StringBuilder sb) {
        sb.append("    popq %rdi\n");
        sb.append("    call kof_array_length\n");
        sb.append("    movslq %eax, %rax\n");
        sb.append("    pushq %rax\n");
    }

    private void emitLoadLiteral(StringBuilder sb, KofLoadLiteral lit) {
        if (lit.value() instanceof Integer i) {
            sb.append("    movq $").append(i).append(", %rax\n");
        } else if (lit.value() instanceof Long l) {
            sb.append("    movq $").append(l).append(", %rax\n");
        } else if (lit.value() instanceof Float f) {
            sb.append("    movq $").append(Float.floatToIntBits(f)).append(", %rax\n");
        } else if (lit.value() instanceof Double d) {
            sb.append("    movq $").append(Double.doubleToLongBits(d)).append(", %rax\n");
        } else if (lit.value() instanceof String s) {
            String label = internString(s);
            int byteLen = s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            sb.append("    leaq ").append(label).append("(%rip), %rdi\n");
            sb.append("    movl $").append(byteLen).append(", %esi\n");
            sb.append("    call kof_string_from_literal\n");
        } else if (lit.value() instanceof Boolean b) {
            sb.append("    movq $").append(b ? 1 : 0).append(", %rax\n");
        } else if (lit.value() == null) {
            sb.append("    movq $0, %rax\n");
        }
        sb.append("    pushq %rax\n");
    }

    private static boolean isFloatType(Type t) {
        return t instanceof Type.PrimitiveType pt && "float".equals(Type.canonicalPrimitiveName(pt.name()));
    }
    private static boolean isDoubleType(Type t) {
        return t instanceof Type.PrimitiveType pt && "double".equals(Type.canonicalPrimitiveName(pt.name()));
    }
    private static boolean isInt32Type(Type t) {
        return t instanceof Type.PrimitiveType pt && "int".equals(Type.canonicalPrimitiveName(pt.name()));
    }

    private void emitBinary(StringBuilder sb, KofBinary kb) {
        Type opTy = kb.operandType();
        if (isFloatType(opTy)) {
            sb.append("    popq %rcx\n");
            sb.append("    popq %rax\n");
            sb.append("    movd %eax, %xmm0\n");
            sb.append("    movd %ecx, %xmm1\n");
            switch (kb.op()) {
                case ADD -> sb.append("    addss %xmm1, %xmm0\n");
                case SUB -> sb.append("    subss %xmm1, %xmm0\n");
                case MUL -> sb.append("    mulss %xmm1, %xmm0\n");
                case DIV -> sb.append("    divss %xmm1, %xmm0\n");
                case EQ -> {
                    sb.append("    ucomiss %xmm1, %xmm0\n");
                    sb.append("    sete %al\n");
                    sb.append("    setnp %dl\n");
                    sb.append("    andb %dl, %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case NE -> {
                    sb.append("    ucomiss %xmm1, %xmm0\n");
                    sb.append("    setne %al\n");
                    sb.append("    setp %dl\n");
                    sb.append("    orb %dl, %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case LT -> {
                    sb.append("    ucomiss %xmm1, %xmm0\n");
                    sb.append("    setb %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case LE -> {
                    sb.append("    ucomiss %xmm1, %xmm0\n");
                    sb.append("    setbe %al\n");
                    sb.append("    setp %dl\n");
                    // NaN => unordered => CF=1 PF=1 => be would be true, clear it
                    sb.append("    testb %dl, %dl\n");
                    sb.append("    jnz 1f\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("    jmp 2f\n");
                    sb.append("1: xorl %eax, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("2:\n");
                    return;
                }
                case GT -> {
                    sb.append("    ucomiss %xmm1, %xmm0\n");
                    sb.append("    seta %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case GE -> {
                    sb.append("    ucomiss %xmm1, %xmm0\n");
                    sb.append("    setae %al\n");
                    sb.append("    setp %dl\n");
                    sb.append("    testb %dl, %dl\n");
                    sb.append("    jnz 1f\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("    jmp 2f\n");
                    sb.append("1: xorl %eax, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("2:\n");
                    return;
                }
                default -> { sb.append("    movd %xmm0, %eax\n"); sb.append("    pushq %rax\n"); return; }
            }
            sb.append("    movd %xmm0, %eax\n");
            sb.append("    movl %eax, %eax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (isDoubleType(opTy)) {
            sb.append("    popq %rcx\n");
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %xmm0\n");
            sb.append("    movq %rcx, %xmm1\n");
            sb.append("    movq %xmm0, %xmm0\n");
            switch (kb.op()) {
                case ADD -> sb.append("    addsd %xmm1, %xmm0\n");
                case SUB -> sb.append("    subsd %xmm1, %xmm0\n");
                case MUL -> sb.append("    mulsd %xmm1, %xmm0\n");
                case DIV -> sb.append("    divsd %xmm1, %xmm0\n");
                case EQ -> {
                    sb.append("    ucomisd %xmm1, %xmm0\n");
                    sb.append("    sete %al\n");
                    sb.append("    setnp %dl\n");
                    sb.append("    andb %dl, %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case NE -> {
                    sb.append("    ucomisd %xmm1, %xmm0\n");
                    sb.append("    setne %al\n");
                    sb.append("    setp %dl\n");
                    sb.append("    orb %dl, %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case LT -> {
                    sb.append("    ucomisd %xmm1, %xmm0\n");
                    sb.append("    setb %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case LE -> {
                    sb.append("    ucomisd %xmm1, %xmm0\n");
                    sb.append("    setbe %al\n");
                    sb.append("    setp %dl\n");
                    sb.append("    testb %dl, %dl\n");
                    sb.append("    jnz 1f\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("    jmp 2f\n");
                    sb.append("1: xorl %eax, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("2:\n");
                    return;
                }
                case GT -> {
                    sb.append("    ucomisd %xmm1, %xmm0\n");
                    sb.append("    seta %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case GE -> {
                    sb.append("    ucomisd %xmm1, %xmm0\n");
                    sb.append("    setae %al\n");
                    sb.append("    setp %dl\n");
                    sb.append("    testb %dl, %dl\n");
                    sb.append("    jnz 1f\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("    jmp 2f\n");
                    sb.append("1: xorl %eax, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("2:\n");
                    return;
                }
                default -> { sb.append("    movq %xmm0, %rax\n"); sb.append("    pushq %rax\n"); return; }
            }
            sb.append("    movq %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        sb.append("    popq %rcx\n");
        sb.append("    popq %rax\n");
        boolean int32 = isInt32Type(opTy);
        String suf = int32 ? "l" : "q";
        String a32 = int32 ? "e" : "r";
        switch (kb.op()) {
            case ADD -> sb.append("    add").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n");
            case SUB -> sb.append("    sub").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n");
            case MUL -> sb.append("    imul").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n");
            case DIV -> {
                if (int32) {
                    sb.append("    cdq\n    idivl %ecx\n");
                } else {
                    sb.append("    cqo\n    idivq %rcx\n");
                }
            }
            case MOD -> {
                if (int32) {
                    sb.append("    cdq\n    idivl %ecx\n    movl %edx, %eax\n");
                } else {
                    sb.append("    cqo\n    idivq %rcx\n    movq %rdx, %rax\n");
                }
            }
            case EQ -> sb.append("    cmp").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n    sete %al\n    movzbl %al, %eax\n");
            case NE -> sb.append("    cmp").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n    setne %al\n    movzbl %al, %eax\n");
            case LT -> sb.append("    cmp").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n    setl %al\n    movzbl %al, %eax\n");
            case LE -> sb.append("    cmp").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n    setle %al\n    movzbl %al, %eax\n");
            case GT -> sb.append("    cmp").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n    setg %al\n    movzbl %al, %eax\n");
            case GE -> sb.append("    cmp").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n    setge %al\n    movzbl %al, %eax\n");
            case AND -> sb.append("    and").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n");
            case OR -> sb.append("    or").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n");
            case XOR -> sb.append("    xor").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n");
            case SHL -> sb.append("    shl").append(suf).append(" %cl, %").append(a32).append("ax\n");
            case SHR -> sb.append("    sar").append(suf).append(" %cl, %").append(a32).append("ax\n");
            case USHR -> sb.append("    shr").append(suf).append(" %cl, %").append(a32).append("ax\n");
        }
        sb.append("    pushq %rax\n");
    }

    private void emitUnary(StringBuilder sb, KofUnary ku) {
        if (ku.operandType() != null && isFloatType(ku.operandType()) && ku.op() == KofUnaryOp.NEG) {
            sb.append("    popq %rax\n");
            sb.append("    movd %eax, %xmm0\n");
            sb.append("    movl $0x80000000, %ecx\n");
            sb.append("    movd %ecx, %xmm1\n");
            sb.append("    xorps %xmm1, %xmm0\n");
            sb.append("    movd %xmm0, %eax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.operandType() != null && isDoubleType(ku.operandType()) && ku.op() == KofUnaryOp.NEG) {
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %xmm0\n");
            sb.append("    movabs $0x8000000000000000, %rcx\n");
            sb.append("    movq %rcx, %xmm1\n");
            sb.append("    xorpd %xmm1, %xmm0\n");
            sb.append("    movq %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        // primitive conversions
        if (ku.op() == KofUnaryOp.I2F) {
            sb.append("    popq %rax\n");
            sb.append("    cvtsi2ss %eax, %xmm0\n");
            sb.append("    movd %xmm0, %eax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.I2D) {
            sb.append("    popq %rax\n");
            sb.append("    cvtsi2sd %eax, %xmm0\n");
            sb.append("    movq %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.L2F) {
            sb.append("    popq %rax\n");
            sb.append("    cvtsi2ss %rax, %xmm0\n");
            sb.append("    movd %xmm0, %eax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.L2D) {
            sb.append("    popq %rax\n");
            sb.append("    cvtsi2sd %rax, %xmm0\n");
            sb.append("    movq %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.F2D) {
            sb.append("    popq %rax\n");
            sb.append("    movd %eax, %xmm0\n");
            sb.append("    cvtss2sd %xmm0, %xmm0\n");
            sb.append("    movq %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.D2F) {
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %xmm0\n");
            sb.append("    cvtsd2ss %xmm0, %xmm0\n");
            sb.append("    movd %xmm0, %eax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.I2L) {
            sb.append("    popq %rax\n");
            sb.append("    movslq %eax, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        sb.append("    popq %rax\n");
        boolean int32u = isInt32Type(ku.operandType());
        String suf = int32u ? "l" : "q";
        String reg = int32u ? "%eax" : "%rax";
        if (ku.op() == KofUnaryOp.NEG) {
            sb.append("    neg").append(suf).append(" ").append(reg).append("\n");
        } else if (ku.op() == KofUnaryOp.NOT) {
            sb.append("    cmp").append(suf).append(" $0, ").append(reg).append("\n");
            sb.append("    sete %al\n");
            sb.append("    movzbl %al, %eax\n");
        }

        sb.append("    pushq %rax\n");
    }

    private void emitConditionalJump(StringBuilder sb, KofConditionalJump kc) {
        Type opTy = kc.operandType();
        if (opTy != null && isFloatType(opTy)) {
            sb.append("    popq %rax\n");
            sb.append("    popq %rcx\n");
            sb.append("    movd %ecx, %xmm0\n");
            sb.append("    movd %eax, %xmm1\n");
            sb.append("    ucomiss %xmm1, %xmm0\n");
            String jmp;
            switch (kc.comparison()) {
                case EQ -> jmp = "je";
                case NE -> jmp = "jne";
                case LT -> jmp = "jb";
                case LE -> jmp = "jbe";
                case GT -> jmp = "ja";
                case GE -> jmp = "jae";
                default -> jmp = "je";
            }
            // NaN handling: ordered compares must be false when unordered (PF=1)
            boolean needsOrderedCheck = kc.comparison() == KofComparison.LE
                    || kc.comparison() == KofComparison.GE
                    || kc.comparison() == KofComparison.EQ;
            if (needsOrderedCheck) {
                // if unordered (PF=1) skip the true branch
                sb.append("    jp ").append(resolveLabel(kc.falseLabel())).append("\n");
            } else if (kc.comparison() == KofComparison.NE) {
                sb.append("    jp ").append(resolveLabel(kc.trueLabel())).append("\n");
                // still need fallback: if NaN, we already jumped to true
            }
            sb.append("    ").append(jmp).append(" ").append(resolveLabel(kc.trueLabel())).append("\n");
            sb.append("    jmp ").append(resolveLabel(kc.falseLabel())).append("\n");
            return;
        }
        if (opTy != null && isDoubleType(opTy)) {
            sb.append("    popq %rax\n");
            sb.append("    popq %rcx\n");
            sb.append("    movq %rcx, %xmm0\n");
            sb.append("    movq %rax, %xmm1\n");
            sb.append("    ucomisd %xmm1, %xmm0\n");
            String jmp;
            switch (kc.comparison()) {
                case EQ -> jmp = "je";
                case NE -> jmp = "jne";
                case LT -> jmp = "jb";
                case LE -> jmp = "jbe";
                case GT -> jmp = "ja";
                case GE -> jmp = "jae";
                default -> jmp = "je";
            }
            boolean needsOrderedCheck = kc.comparison() == KofComparison.LE
                    || kc.comparison() == KofComparison.GE
                    || kc.comparison() == KofComparison.EQ;
            if (needsOrderedCheck) {
                sb.append("    jp ").append(resolveLabel(kc.falseLabel())).append("\n");
            } else if (kc.comparison() == KofComparison.NE) {
                sb.append("    jp ").append(resolveLabel(kc.trueLabel())).append("\n");
            }
            sb.append("    ").append(jmp).append(" ").append(resolveLabel(kc.trueLabel())).append("\n");
            sb.append("    jmp ").append(resolveLabel(kc.falseLabel())).append("\n");
            return;
        }
        sb.append("    popq %rax\n");
        sb.append("    popq %rcx\n");
        String cond = switch (kc.comparison()) {
            case EQ -> "je";
            case NE -> "jne";
            case LT -> "jl";
            case LE -> "jle";
            case GT -> "jg";
            case GE -> "jge";
        };
        if (opTy != null && isInt32Type(opTy)) {
            sb.append("    cmpl %eax, %ecx\n");
        } else {
            sb.append("    cmpq %rax, %rcx\n");
        }
        sb.append("    ").append(cond).append(" ").append(resolveLabel(kc.trueLabel())).append("\n");
        sb.append("    jmp ").append(resolveLabel(kc.falseLabel())).append("\n");
    }

    private void emitCall(StringBuilder sb, KofCall kc) {
        if ("kof_box".equals(kc.methodName()) || "kof_unbox".equals(kc.methodName())) {

            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "println".equals(kc.methodName())) {
            Type argType = kc.parameterTypes().isEmpty() ? Type.UnknownType.UNKNOWN : kc.parameterTypes().get(0);
            if (argType instanceof Type.PrimitiveType pt && ("int".equals(pt.name()) || "char".equals(pt.name())
                    || "long".equals(pt.name()) || "short".equals(pt.name()) || "byte".equals(pt.name()))) {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_print_int\n");
                sb.append("    pushq $0\n");
                sb.append("    leaq .Lnewline(%rip), %rdi\n");
                sb.append("    call kof_print\n");
            } else if (argType instanceof Type.PrimitiveType pt && isFloatType(pt)) {
                sb.append("    popq %rdi\n");
                sb.append("    movd %edi, %xmm0\n");
                sb.append("    call kof_print_float\n");
                sb.append("    pushq $0\n");
                sb.append("    leaq .Lnewline(%rip), %rdi\n");
                sb.append("    call kof_print\n");
            } else if (argType instanceof Type.PrimitiveType pt && isDoubleType(pt)) {
                sb.append("    popq %rdi\n");
                sb.append("    movq %rdi, %xmm0\n");
                sb.append("    call kof_print_double\n");
                sb.append("    pushq $0\n");
                sb.append("    leaq .Lnewline(%rip), %rdi\n");
                sb.append("    call kof_print\n");
            } else if (BuiltinTypes.isString(argType)) {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_println_string\n");
                sb.append("    pushq $0\n");
            } else {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_println\n");
                sb.append("    pushq $0\n");
            }
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "print".equals(kc.methodName())) {
            Type argType = kc.parameterTypes().isEmpty() ? Type.UnknownType.UNKNOWN : kc.parameterTypes().get(0);
            if (BuiltinTypes.isString(argType)) {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_print_string\n");
                sb.append("    pushq $0\n");
            } else if (argType instanceof Type.PrimitiveType pt && isFloatType(pt)) {
                sb.append("    popq %rdi\n");
                sb.append("    movd %edi, %xmm0\n");
                sb.append("    call kof_print_float\n");
                sb.append("    pushq $0\n");
            } else if (argType instanceof Type.PrimitiveType pt && isDoubleType(pt)) {
                sb.append("    popq %rdi\n");
                sb.append("    movq %rdi, %xmm0\n");
                sb.append("    call kof_print_double\n");
                sb.append("    pushq $0\n");
            } else {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_print\n");
                sb.append("    pushq $0\n");
            }
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isString(kc.ownerType())
                && "length".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_length\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "charAt".equals(kc.methodName())) {
            int argCount = kc.parameterTypes().size();
            String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
            for (int i = argCount - 1; i >= 0; i--) {
                sb.append("    popq ").append(intRegs[i + 1]).append("\n");
            }
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %rdi\n");
            sb.append("    call kof_string_char_at\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "substring".equals(kc.methodName())) {
            int argCount = kc.parameterTypes().size();
            if (argCount == 1) {
                sb.append("    popq %rsi\n");
                sb.append("    xorq %rdx, %rdx\n");
            } else {
                sb.append("    popq %rdx\n");
                sb.append("    popq %rsi\n");
            }
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %rdi\n");
            sb.append("    call kof_string_substring\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "contains".equals(kc.methodName())) {
            int argCount = kc.parameterTypes().size();
            String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
            for (int i = argCount - 1; i >= 0; i--) {
                sb.append("    popq ").append(intRegs[i + 1]).append("\n");
            }
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %rdi\n");
            sb.append("    call kof_string_contains\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "startsWith".equals(kc.methodName())) {
            int argCount = kc.parameterTypes().size();
            String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
            for (int i = argCount - 1; i >= 0; i--) {
                sb.append("    popq ").append(intRegs[i + 1]).append("\n");
            }
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %rdi\n");
            sb.append("    call kof_string_starts_with\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "endsWith".equals(kc.methodName())) {
            int argCount = kc.parameterTypes().size();
            String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
            for (int i = argCount - 1; i >= 0; i--) {
                sb.append("    popq ").append(intRegs[i + 1]).append("\n");
            }
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %rdi\n");
            sb.append("    call kof_string_ends_with\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "concat".equals(kc.methodName())) {
            int argCount = kc.parameterTypes().size();
            String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
            for (int i = argCount - 1; i >= 0; i--) {
                sb.append("    popq ").append(intRegs[i + 1]).append("\n");
            }
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %rdi\n");
            sb.append("    call kof_string_concat\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "indexOf".equals(kc.methodName())) {
            String[] regs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
            for (int i = kc.parameterTypes().size() - 1; i >= 0; i--) {
                sb.append("    popq ").append(regs[i + 1]).append("\n");
            }
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_index_of\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "trim".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_trim\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "toUpperCase".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_to_upper\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "toLowerCase".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_to_lower\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "replace".equals(kc.methodName())) {
            sb.append("    popq %rdx\n");
            sb.append("    popq %rsi\n");
            sb.append("    popq %rdi\n");
            // replace(char, char) passes raw character codes (Ints);
            // replace(String, String) passes KofString pointers. The two
            // runtime helpers must be selected by the call's parameter types.
            Type first = !kc.parameterTypes().isEmpty() ? kc.parameterTypes().get(0) : null;
            boolean charArgs = first instanceof Type.PrimitiveType pt
                    && "char".equals(Type.canonicalPrimitiveName(pt.name()));
            sb.append(charArgs
                    ? "    call kof_string_replace_char\n"
                    : "    call kof_string_replace\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "equalsIgnoreCase".equals(kc.methodName())) {
            sb.append("    popq %rsi\n");
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_equals_ignore_case\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "split".equals(kc.methodName())) {
            sb.append("    popq %rsi\n");
            sb.append("    movl 16(%rsi), %ecx\n");
            sb.append("    testl %ecx, %ecx\n");
            sb.append("    jz .Lkof_split_empty_sep\n");
            sb.append("    movzbl 24(%rsi), %esi\n");
            sb.append("    jmp .Lkof_split_call\n");
            sb.append(".Lkof_split_empty_sep:\n");
            sb.append("    xorl %esi, %esi\n");
            sb.append(".Lkof_split_call:\n");
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_split\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if ("kof_string_to_int".equals(kc.methodName())
                || "kof_string_to_long".equals(kc.methodName())
                || "kof_string_to_double".equals(kc.methodName())
                || "kof_string_to_float".equals(kc.methodName())) {
            String fn = kc.methodName();
            sb.append("    popq %rdi\n");
            sb.append("    call ").append(fn).append("\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.STATIC && "valueOf".equals(kc.methodName())) {
            Type argType = kc.parameterTypes().isEmpty() ? Type.UnknownType.UNKNOWN : kc.parameterTypes().get(0);
            if (argType instanceof Type.PrimitiveType pt && ("int".equals(pt.name()) || "char".equals(pt.name())
                    || "short".equals(pt.name()) || "byte".equals(pt.name()))) {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_int_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (argType instanceof Type.PrimitiveType pt && "long".equals(pt.name())) {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_long_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (argType instanceof Type.PrimitiveType pt && "bool".equals(pt.name())) {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_bool_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (argType instanceof Type.PrimitiveType pt && isFloatType(pt)) {
                sb.append("    popq %rdi\n");
                sb.append("    movd %edi, %xmm0\n");
                sb.append("    call kof_float_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (argType instanceof Type.PrimitiveType pt && isDoubleType(pt)) {
                sb.append("    popq %rdi\n");
                sb.append("    movq %rdi, %xmm0\n");
                sb.append("    call kof_double_to_string\n");
                sb.append("    pushq %rax\n");
            }
            return;
        }
        if (kc.kind() == KofCallKind.CONSTRUCTOR && "<init>".equals(kc.methodName())) {
            int argCount = kc.parameterTypes().size();
            String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
            int stackArgs = Math.max(0, argCount - 5);
            if (stackArgs > 0) {
                // Save stack args to local frame (high offsets to avoid collision)
                for (int s = stackArgs - 1; s >= 0; s--) {
                    int off = 256 + s * 8;
                    sb.append("    popq %rax\n");
                    sb.append("    movq %rax, -").append(off).append("(%rbp)\n");
                }
                // Pop 5 register args
                for (int i = 4; i >= 0; i--) {
                    sb.append("    popq ").append(intRegs[i + 1]).append("\n");
                }
                // Pop this
                sb.append("    popq %rax\n");
                sb.append("    movq %rax, %rdi\n");
                // Push stack args back
                for (int s = 0; s < stackArgs; s++) {
                    int off = 256 + s * 8;
                    sb.append("    pushq -").append(off).append("(%rbp)\n");
                }
            } else {
                for (int i = argCount - 1; i >= 0; i--) {
                    sb.append("    popq ").append(intRegs[i + 1]).append("\n");
                }
                sb.append("    popq %rax\n");
                sb.append("    movq %rax, %rdi\n");
            }
            String ctorLabel = resolveCalleeName(kc);
            sb.append("    call ").append(ctorLabel).append("\n");
            return;
        }

        if (kc.kind() == KofCallKind.INSTANCE
                && (BuiltinTypes.isMap(kc.ownerType()) || BuiltinTypes.isSet(kc.ownerType()))) {
            String collFn = (kc.methodName().startsWith("kof_map_") || kc.methodName().startsWith("kof_set_"))
                    ? kc.methodName() : null;
            if (collFn != null) {
                int argCount = kc.parameterTypes().size();
                String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
                for (int i = argCount - 1; i >= 0; i--) {
                    sb.append("    popq ").append(intRegs[i + 1]).append("\n");
                }
                sb.append("    popq %rax\n");
                sb.append("    movq %rax, %rdi\n");
                sb.append("    call ").append(collFn).append("\n");
                if (!Type.isVoid(kc.returnType())) {
                    sb.append("    pushq %rax\n");
                }
                return;
            }
        }
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isList(kc.ownerType())) {
            String listFn = kc.methodName().startsWith("kof_list_") ? kc.methodName() : null;
            if (listFn != null) {
                int argCount = kc.parameterTypes().size();
                String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
                for (int i = argCount - 1; i >= 0; i--) {
                    sb.append("    popq ").append(intRegs[i + 1]).append("\n");
                }
                sb.append("    popq %rax\n");
                sb.append("    movq %rax, %rdi\n");
                sb.append("    call ").append(listFn).append("\n");
                if (!Type.isVoid(kc.returnType())) {
                    sb.append("    pushq %rax\n");
                }
                return;
            }
        }
        if (kc.kind() == KofCallKind.INSTANCE && kc.ownerType() instanceof Type.ClassType ct) {
            int vtableIdx = findVirtualMethodIndex(ct.name(), kc.methodName());
            if (vtableIdx >= 0) {
                int argCount = kc.parameterTypes().size();
                String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
                int stackArgs = Math.max(0, argCount - 5);
                if (stackArgs > 0) {
                    for (int s = stackArgs - 1; s >= 0; s--) {
                        sb.append("    popq %r10\n");
                    }
                }
                for (int i = Math.min(argCount, 5) - 1; i >= 0; i--) {
                    sb.append("    popq ").append(intRegs[i + 1]).append("\n");
                }
                sb.append("    popq %rax\n");
                sb.append("    movq %rax, %rdi\n");
                if (stackArgs > 0) {
                    for (int s = 0; s < stackArgs; s++) {
                        sb.append("    pushq %r10\n");
                    }
                }
                sb.append("    movq 8(%rax), %rbx\n");
                sb.append("    addq $").append(vtableIdx * 8).append(", %rbx\n");
                sb.append("    movq (%rbx), %rbx\n");
                sb.append("    call *%rbx\n");
                if (stackArgs > 0) {
                    sb.append("    addq $").append(stackArgs * 8).append(", %rsp\n");
                }
                if (!Type.isVoid(kc.returnType())) {
                    sb.append("    pushq %rax\n");
                }
                return;
            }
        }
        if (kc.kind() == KofCallKind.INTERFACE && kc.ownerType() instanceof Type.ClassType ct) {
            int vtableIdx = findVirtualMethodIndex(ct.name(), kc.methodName());
            if (vtableIdx >= 0) {
                int argCount = kc.parameterTypes().size();
                String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
                int stackArgs = Math.max(0, argCount - 5);
                if (stackArgs > 0) {
                    for (int s = stackArgs - 1; s >= 0; s--) {
                        sb.append("    popq %r10\n");
                    }
                }
                for (int i = Math.min(argCount, 5) - 1; i >= 0; i--) {
                    sb.append("    popq ").append(intRegs[i + 1]).append("\n");
                }
                sb.append("    popq %rax\n");
                sb.append("    movq %rax, %rdi\n");
                if (stackArgs > 0) {
                    for (int s = 0; s < stackArgs; s++) {
                        sb.append("    pushq %r10\n");
                    }
                }
                sb.append("    movq 8(%rax), %rbx\n");
                sb.append("    addq $").append(vtableIdx * 8).append(", %rbx\n");
                sb.append("    movq (%rbx), %rbx\n");
                sb.append("    call *%rbx\n");
                if (stackArgs > 0) {
                    sb.append("    addq $").append(stackArgs * 8).append(", %rsp\n");
                }
                if (!Type.isVoid(kc.returnType())) {
                    sb.append("    pushq %rax\n");
                }
                return;
            }
        }

        int argCount = kc.parameterTypes().size();
        String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
        int stackArgs = Math.max(0, argCount - 6);
        if (stackArgs > 0) {
            sb.append("    addq $").append(stackArgs * 8).append(", %rsp\n");
        }
        for (int i = 5; i >= 0; i--) {
            if (i < argCount) {
                sb.append("    popq ").append(intRegs[i]).append("\n");
            }
        }
        String callee = resolveCalleeName(kc);
        sb.append("    call ").append(callee).append("\n");
        if (!Type.isVoid(kc.returnType())) {
            sb.append("    pushq %rax\n");
        }
    }

    private String resolveCalleeName(KofCall kc) {
        // builtins de coleção são símbolos globais do runtime — nunca
        // mangle com o dono (Map_kof_map_put etc.)
        String mn = kc.methodName();
        if (mn.startsWith("kof_map_") || mn.startsWith("kof_set_")) {
            return mn;
        }
        if (kc.kind() == KofCallKind.FUNCTION) {
            return functionMangleMap.getOrDefault(kc.methodName(), sanitizeName(kc.methodName()));
        }
        if (kc.kind() == KofCallKind.CONSTRUCTOR) {
            if (kc.ownerType() instanceof Type.ClassType ct) {
                return sanitizeName(ct.name()) + "_" + sanitizeName("<init>") + "_" + kc.parameterTypes().size();
            }
        }
        if (kc.ownerType() instanceof Type.ClassType ct) {
            String key = ct.name() + "." + kc.methodName();
            return functionMangleMap.getOrDefault(key, sanitizeName(ct.name()) + "_" + sanitizeName(kc.methodName()));
        }
        return sanitizeName(kc.methodName());
    }

    private int resolveFieldOffset(Type ownerType, String fieldName) {
        ClassLayout layout = getLayoutForType(ownerType);
        if (layout != null) {
            int offset = layout.fieldOffset(fieldName);
            if (offset >= 0) return offset;
        }
        if (currentClass != null) {
            layout = getLayout(currentClass);
            int offset = layout.fieldOffset(fieldName);
            if (offset >= 0) return offset;
        }
        return ClassLayout.HEADER_SIZE;
    }

    private void emitStart(StringBuilder sb, IRClass clazz) {
        boolean hasMain = clazz.methods().stream().anyMatch(m -> "main".equals(m.name()));
        if (!hasMain) return;
        boolean mainHasArgs = clazz.methods().stream()
                .filter(m -> "main".equals(m.name()))
                .anyMatch(m -> !m.parameterTypes().isEmpty());
        sb.append("\n.globl _start\n");
        sb.append("_start:\n");
        if (mainHasArgs) {
            // N3: passa array vazio — evita segfault ao tratar argc como ponteiro
            sb.append("    xorl %edi, %edi\n");
            sb.append("    movl $8, %esi\n");
            sb.append("    call kof_array_alloc\n");
            sb.append("    movq %rax, %rdi\n");
        }
        sb.append("    call ").append(sanitizeName(clazz.name())).append("_main\n");
        sb.append("    movq $60, %rax\n");
        sb.append("    xorq %rdi, %rdi\n");
        sb.append("    syscall\n");
    }

    /** Detecta o protocolo do URL de conexão quando é um literal em
     *  compile-time (intenção conhecida pelo compilador): mysql/mariadb
     *  exigem a lib do cliente no link; sqlite, não. URLs dinâmicos
     *  linkam as duas (default conservador). */
    private boolean connectsToMysql(int callIndex, List<KofOperation> ops) {
        for (int j = callIndex - 1; j >= 0 && j >= callIndex - 8; j--) {
            if (ops.get(j) instanceof KofLoadLiteral lit && lit.value() instanceof String url) {
                String u = url.toLowerCase();
                return !u.startsWith("sqlite:");
            }
        }
        return true;
    }

    private void assemble(Path asmFile, Path binFile) throws IOException {
        Path objFile = asmFile.resolveSibling(asmFile.getFileName() + ".o");
        System.err.println("NativeBackend: assembling " + asmFile);
        try {
            runCommand(new String[]{"as", "-o", objFile.toString(), asmFile.toString()}, "as");
        } catch (IOException e) {
            System.err.println("NativeBackend: as failed: " + e.getMessage());
            throw e;
        }
        // Native always needs dynamic linker + libc now (printf for float, db optionally)
        // to keep single codegen path; plain integer programs still work via ld+ld.so.
        boolean needsDynamic = true;
        String os = System.getProperty("os.name", "").toLowerCase();
        if (needsDynamic && os.contains("linux")) {
            if (usesDb) {
                String[] extra = usesMysql
                        ? new String[]{"-l:libsqlite3.so.0", "-l:libmariadb.so.3"}
                        : new String[]{"-l:libsqlite3.so.0"};
                String[] cmd = new String[7 + extra.length];
                cmd[0] = "ld";
                cmd[1] = "-o";
                cmd[2] = binFile.toString();
                cmd[3] = objFile.toString();
                cmd[4] = "-dynamic-linker";
                cmd[5] = "/lib64/ld-linux-x86-64.so.2";
                cmd[6] = "-lc";
                System.arraycopy(extra, 0, cmd, 7, extra.length);
                runCommand(cmd, "ld");
            } else {
                runCommand(new String[]{"ld", "-o", binFile.toString(), objFile.toString(),
                        "-dynamic-linker", "/lib64/ld-linux-x86-64.so.2", "-lc"}, "ld");
            }
        } else {
            if (usesDb) {
                String os2 = System.getProperty("os.name", "").toLowerCase();
                if (os2.contains("linux")) {
                    String[] extra = usesMysql
                            ? new String[]{"-l:libsqlite3.so.0", "-l:libmariadb.so.3"}
                            : new String[]{"-l:libsqlite3.so.0"};
                    String[] cmd = new String[7 + extra.length];
                    cmd[0] = "ld"; cmd[1] = "-o"; cmd[2] = binFile.toString(); cmd[3] = objFile.toString();
                    cmd[4] = "-dynamic-linker"; cmd[5] = "/lib64/ld-linux-x86-64.so.2"; cmd[6] = "-lc";
                    System.arraycopy(extra, 0, cmd, 7, extra.length);
                    runCommand(cmd, "ld");
                } else {
                    runCommand(new String[]{"ld", "-o", binFile.toString(), objFile.toString()}, "ld");
                }
            } else {
                runCommand(new String[]{"ld", "-o", binFile.toString(), objFile.toString()}, "ld");
            }
        }
        Files.deleteIfExists(objFile);
        if (System.getenv("KOF_KEEP_ASM") == null) Files.deleteIfExists(asmFile);
        binFile.toFile().setExecutable(true);
    }

    private void emitRiscv(IRModule module, Path outputDir) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(".option arch, rv64g\n");
        sb.append(".section .text\n");
        sb.append(".globl _start\n");
        sb.append("_start:\n");
        sb.append("  call main\n");
        sb.append("  li a7, 93\n");
        sb.append("  li a0, 0\n");
        sb.append("  ecall\n");
        sb.append("  li a7, 214\n");
        sb.append("  li a7, 64\n");
        // minimal main stub
        sb.append("main:\n");
        sb.append("  li a0, 0\n");
        sb.append("  ret\n");
        Path asmFile = outputDir.resolve("Default/Main.s");
        Files.createDirectories(asmFile.getParent());
        Files.writeString(asmFile, sb.toString());
        System.err.println("NativeBackend: generated riscv64 " + asmFile);
        try {
            Path objFile = asmFile.resolveSibling("Main.o");
            runCommand(new String[]{"riscv64-linux-gnu-as", "-o", objFile.toString(), asmFile.toString()}, "riscv64-as");
            Path binFile = outputDir.resolve("Default/Main");
            runCommand(new String[]{"riscv64-linux-gnu-ld", "-o", binFile.toString(), objFile.toString(), "-dynamic-linker", "/lib/ld-linux-riscv64-lp64d.so.1", "-lc"}, "riscv64-ld");
            Files.deleteIfExists(objFile);
            if (System.getenv("KOF_KEEP_ASM") == null) Files.deleteIfExists(asmFile);
            binFile.toFile().setExecutable(true);
        } catch (IOException e) {
            System.err.println("NativeBackend: riscv64 toolchain not found, keeping asm: " + e.getMessage());
        }
    }

    private void emitAarch64(IRModule module, Path outputDir) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(".arch armv8-a\n");
        sb.append(".section .text\n");
        sb.append(".globl _start\n");
        sb.append("_start:\n");
        sb.append("  bl main\n");
        sb.append("  mov x8, #93\n");
        sb.append("  mov x0, #0\n");
        sb.append("  svc #0\n");
        sb.append("main:\n");
        sb.append("  mov x0, #0\n");
        sb.append("  ret\n");
        Path asmFile = outputDir.resolve("Default/Main.s");
        Files.createDirectories(asmFile.getParent());
        Files.writeString(asmFile, sb.toString());
        System.err.println("NativeBackend: generated aarch64 " + asmFile);
        try {
            Path objFile = asmFile.resolveSibling("Main.o");
            runCommand(new String[]{"aarch64-linux-gnu-as", "-o", objFile.toString(), asmFile.toString()}, "aarch64-as");
            Path binFile = outputDir.resolve("Default/Main");
            runCommand(new String[]{"aarch64-linux-gnu-ld", "-o", binFile.toString(), objFile.toString(), "-dynamic-linker", "/lib/ld-linux-aarch64.so.1", "-lc"}, "aarch64-ld");
            Files.deleteIfExists(objFile);
            if (System.getenv("KOF_KEEP_ASM") == null) Files.deleteIfExists(asmFile);
            binFile.toFile().setExecutable(true);
        } catch (IOException e) {
            System.err.println("NativeBackend: aarch64 toolchain not found, keeping asm: " + e.getMessage());
        }
    }

    private void runCommand(String[] cmd, String name) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            if (p.exitValue() != 0) {
                throw new IOException(name + " failed (exit " + p.exitValue() + "): " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(name + " interrupted");
        }
    }
}
