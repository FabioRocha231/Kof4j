package dev.kof.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Native Backend — transforms Kof IR into x86-64 Linux ELF assembly.
 * Uses System V AMD64 ABI calling convention.
 */
public class NativeBackend implements Backend {

    private final Map<LabelId, String> labelMap = new HashMap<>();
    private int labelCounter = 0;
    private final List<String[]> stringLiterals = new ArrayList<>();
    private int stringCounter = 0;
    private Type lastPushedType = Type.UnknownType.UNKNOWN;
    private IRClass currentClass = null;

    private String resolveLabel(LabelId id) {
        return labelMap.computeIfAbsent(id, k -> ".Lkof_" + (labelCounter++));
    }

    private String internString(String value) {
        for (String[] entry : stringLiterals) {
            if (entry[0].equals(value)) return entry[1];
        }
        String label = ".Lstr_" + (stringCounter++);
        stringLiterals.add(new String[]{value, label});
        return label;
    }

    @Override
    public void emit(IRModule module, Path outputDir) throws IOException {
        for (IRClass clazz : module.classes()) {
            emitNative(clazz, outputDir);
        }
    }

    private void emitNative(IRClass clazz, Path outputDir) throws IOException {
        String sourceName = clazz.name();
        Path asmFile = outputDir.resolve(sourceName + ".s");
        Path binFile = outputDir.resolve(sourceName);
        generateAssembly(clazz, asmFile);
        assemble(asmFile, binFile);
    }

    private void generateAssembly(IRClass clazz, Path asmFile) throws IOException {
        StringBuilder sb = new StringBuilder();
        labelCounter = 0;
        labelMap.clear();
        stringLiterals.clear();
        stringCounter = 0;
        currentClass = clazz;

        collectStrings(clazz);

        sb.append(".section .data\n");
        emitStringData(sb);
        sb.append("\n.section .text\n");
        emitBuiltinFunctions(sb);

        for (IRMethod method : clazz.methods()) {
            emitMethod(sb, clazz, method);
        }

        emitStart(sb, clazz);
        Files.writeString(asmFile, sb.toString());
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
    }

    private void emitBuiltinFunctions(StringBuilder sb) {
        sb.append(".globl kof_print\n");
        sb.append(".type kof_print, @function\n");
        sb.append("kof_print:\n");
        sb.append("    pushq %rbx\n");
        sb.append("    movq %rdi, %rbx\n");
        sb.append("    xorq %rdx, %rdx\n");
        sb.append(".Lkof_print_len:\n");
        sb.append("    cmpb $0, (%rbx,%rdx)\n");
        sb.append("    je .Lkof_print_do\n");
        sb.append("    incq %rdx\n");
        sb.append("    jmp .Lkof_print_len\n");
        sb.append(".Lkof_print_do:\n");
        sb.append("    movq $1, %rax\n");
        sb.append("    movq $1, %rdi\n");
        sb.append("    movq %rbx, %rsi\n");
        sb.append("    syscall\n");
        sb.append("    popq %rbx\n");
        sb.append("    ret\n");

        sb.append(".globl kof_println\n");
        sb.append(".type kof_println, @function\n");
        sb.append("kof_println:\n");
        sb.append("    call kof_print\n");
        sb.append("    pushq %rbx\n");
        sb.append("    leaq .Lnewline(%rip), %rdi\n");
        sb.append("    call kof_print\n");
        sb.append("    popq %rbx\n");
        sb.append("    ret\n");

        sb.append(".globl kof_print_int\n");
        sb.append(".type kof_print_int, @function\n");
        sb.append("kof_print_int:\n");
        sb.append("    pushq %rbx\n");
        sb.append("    pushq %r12\n");
        sb.append("    movl %edi, %eax\n");
        sb.append("    movq $0, %r12\n");
        sb.append("    testl %eax, %eax\n");
        sb.append("    jns .Lkof_print_int_pos\n");
        sb.append("    movq $1, %r12\n");
        sb.append("    negl %eax\n");
        sb.append(".Lkof_print_int_pos:\n");
        sb.append("    movq $0, %rbx\n");
        sb.append("    movq $10, %rcx\n");
        sb.append(".Lkof_print_int_loop:\n");
        sb.append("    xorq %rdx, %rdx\n");
        sb.append("    divq %rcx\n");
        sb.append("    addl $48, %edx\n");
        sb.append("    pushq %rdx\n");
        sb.append("    incq %rbx\n");
        sb.append("    testl %eax, %eax\n");
        sb.append("    jnz .Lkof_print_int_loop\n");
        sb.append("    testq %r12, %r12\n");
        sb.append("    jz .Lkof_print_int_digits\n");
        sb.append("    movq $45, %rax\n");
        sb.append("    pushq %rax\n");
        sb.append("    incq %rbx\n");
        sb.append(".Lkof_print_int_digits:\n");
        sb.append("    movq $1, %rax\n");
        sb.append("    movq $1, %rdi\n");
        sb.append("    movq %rsp, %rsi\n");
        sb.append("    movq %rbx, %rdx\n");
        sb.append("    syscall\n");
        sb.append("    addq %rbx, %rsp\n");
        sb.append("    popq %r12\n");
        sb.append("    popq %rbx\n");
        sb.append("    ret\n");
    }

    private void emitMethod(StringBuilder sb, IRClass clazz, IRMethod method) {
        if ("<init>".equals(method.name()) || "<clinit>".equals(method.name())) return;

        currentClass = clazz;

        String mangled = clazz.name() + "_" + method.name();
        sb.append("\n.globl ").append(mangled).append("\n");
        sb.append(".type ").append(mangled).append(", @function\n");
        sb.append(mangled).append(":\n");

        sb.append("    pushq %rbp\n");
        sb.append("    movq %rsp, %rbp\n");

        int localSlots = method.localVariables().size();
        int frameSize = Math.max(localSlots * 8, 16);
        frameSize = (frameSize + 15) & ~15;
        if (frameSize > 0) {
            sb.append("    subq $").append(frameSize).append(", %rsp\n");
        }

        int intArgIdx = 0;
        for (IRLocalVariable lv : method.localVariables()) {
            if (lv.name().equals("this")) {
                sb.append("    movq %rdi, ").append(lv.index() * 8).append("(%rbp)\n");
                intArgIdx++;
                continue;
            }
            String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
            if (intArgIdx < 6) {
                sb.append("    movq ").append(intRegs[intArgIdx]).append(", ").append(lv.index() * 8).append("(%rbp)\n");
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
        // Track the type of the last pushed value for println dispatch
        if (op instanceof KofLoadLiteral lit) {
            lastPushedType = lit.type();
        } else if (op instanceof KofLoadLocal ll) {
            lastPushedType = ll.type();
        } else if (op instanceof KofLoadField lf) {
            lastPushedType = lf.fieldType();
        } else if (op instanceof KofBinary kb) {
            lastPushedType = kb.operandType();
        } else if (op instanceof KofUnary ku) {
            lastPushedType = ku.operandType();
        }

        switch (op) {
            case KofLoadLiteral lit -> emitLoadLiteral(sb, lit);
            case KofLoadLocal ll -> {
                sb.append("    movq ").append(ll.index() * 8).append("(%rbp), %rax\n");
                sb.append("    pushq %rax\n");
            }
            case KofStoreLocal sl -> {
                sb.append("    popq %rax\n");
                sb.append("    movq %rax, ").append(sl.index() * 8).append("(%rbp)\n");
            }
            case KofLoadField lf -> {
                sb.append("    popq %rax\n");
                int offset = computeFieldOffset(lf.ownerType(), lf.name());
                sb.append("    movq ").append(offset).append("(%rax), %rax\n");
                sb.append("    pushq %rax\n");
            }
            case KofStoreField sf -> {
                sb.append("    popq %rax\n");
                sb.append("    popq %rcx\n");
                int offset = computeFieldOffset(sf.ownerType(), sf.name());
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
            case KofJump kj -> sb.append("    jmp ").append(resolveLabel(kj.target())).append("\n");
            case KofConditionalJump kc -> emitConditionalJump(sb, kc);
            case KofCall kc -> emitCall(sb, kc);
            case KofNewObject no -> {
                int size = computeObjectSize(no.type());
                sb.append("    subq $").append(size).append(", %rsp\n");
                sb.append("    movq %rsp, %rax\n");
                sb.append("    pushq %rax\n");
            }
            case KofDup dup -> { }
            case KofPop pop -> sb.append("    addq $8, %rsp\n");
            case KofGetStatic gs -> { }
            case KofPutStatic ps -> sb.append("    addq $8, %rsp\n");
            case KofCheckCast cc -> { }
            case KofInstanceOf io -> sb.append("    pushq $0\n");
            default -> { }
        }
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
            sb.append("    leaq ").append(label).append("(%rip), %rax\n");
        } else if (lit.value() instanceof Boolean b) {
            sb.append("    movq $").append(b ? 1 : 0).append(", %rax\n");
        } else if (lit.value() == null) {
            sb.append("    movq $0, %rax\n");
        }
        sb.append("    pushq %rax\n");
    }

    private void emitBinary(StringBuilder sb, KofBinary kb) {
        sb.append("    popq %rbx\n");
        sb.append("    popq %rax\n");
        switch (kb.op()) {
            case ADD -> sb.append("    addq %rbx, %rax\n");
            case SUB -> sb.append("    subq %rax, %rbx\n    movq %rbx, %rax\n");
            case MUL -> sb.append("    imulq %rbx, %rax\n");
            case DIV -> sb.append("    cqo\n    idivq %rbx\n");
            case MOD -> sb.append("    cqo\n    idivq %rbx\n    movq %rdx, %rax\n");
        }
        sb.append("    pushq %rax\n");
    }

    private void emitUnary(StringBuilder sb, KofUnary ku) {
        sb.append("    popq %rax\n");
        if (ku.op() == KofUnaryOp.NEG) {
            sb.append("    negq %rax\n");
        } else if (ku.op() == KofUnaryOp.NOT) {
            sb.append("    cmpq $0, %rax\n");
            sb.append("    sete %al\n");
            sb.append("    movzbl %al, %eax\n");
        }
        sb.append("    pushq %rax\n");
    }

    private void emitConditionalJump(StringBuilder sb, KofConditionalJump kc) {
        sb.append("    popq %rax\n");
        sb.append("    popq %rbx\n");
        String cond = switch (kc.comparison()) {
            case EQ -> "je";
            case NE -> "jne";
            case LT -> "jl";
            case LE -> "jle";
            case GT -> "jg";
            case GE -> "jge";
        };
        sb.append("    cmpq %rax, %rbx\n");
        sb.append("    ").append(cond).append(" ").append(resolveLabel(kc.trueLabel())).append("\n");
        sb.append("    jmp ").append(resolveLabel(kc.falseLabel())).append("\n");
    }

    private void emitCall(StringBuilder sb, KofCall kc) {
        if (kc.kind() == KofCallKind.INSTANCE && "println".equals(kc.methodName())) {
            // Use the tracked last pushed type to dispatch
            if (lastPushedType instanceof Type.PrimitiveType pt && "int".equals(pt.name())) {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_print_int\n");
                sb.append("    pushq $0\n");
                sb.append("    leaq .Lnewline(%rip), %rdi\n");
                sb.append("    call kof_print\n");
            } else {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_println\n");
                sb.append("    pushq $0\n");
            }
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "print".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_print\n");
            sb.append("    pushq $0\n");
            return;
        }
        if (kc.kind() == KofCallKind.STATIC && "valueOf".equals(kc.methodName())) {
            return;
        }
        if (kc.kind() == KofCallKind.CONSTRUCTOR && "<init>".equals(kc.methodName())) {
            return;
        }

        int argCount = kc.parameterTypes().size();
        String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
        for (int i = argCount - 1; i >= 0; i--) {
            if (i < 6) {
                sb.append("    popq ").append(intRegs[i]).append("\n");
            } else {
                sb.append("    addq $8, %rsp\n");
            }
        }
        String callee = resolveCalleeName(kc);
        sb.append("    call ").append(callee).append("\n");
        sb.append("    pushq %rax\n");
    }

    private String resolveCalleeName(KofCall kc) {
        if (kc.ownerType() instanceof Type.ClassType ct) {
            return ct.name() + "_" + kc.methodName();
        }
        return kc.methodName();
    }

    private int computeFieldOffset(Type ownerType, String fieldName) {
        // Compute field offset based on the owner type's fields
        // Fields are laid out in declaration order, each 8 bytes (pointer-sized)
        if (currentClass != null) {
            int offset = 0;
            for (IRField field : currentClass.fields()) {
                if (field.name().equals(fieldName)) return offset;
                offset += NativeTypeMapper.typeSize(field.type());
            }
        }
        // Fallback: hash-based offset
        int hash = Math.abs(fieldName.hashCode()) % 8;
        return hash * 8;
    }

    private int computeObjectSize(Type type) {
        return 64;
    }

    private void emitStart(StringBuilder sb, IRClass clazz) {
        boolean hasMain = clazz.methods().stream().anyMatch(m -> "main".equals(m.name()));
        if (!hasMain) return;
        sb.append("\n.globl _start\n");
        sb.append("_start:\n");
        sb.append("    call Main_main\n");
        sb.append("    movq $60, %rax\n");
        sb.append("    xorq %rdi, %rdi\n");
        sb.append("    syscall\n");
    }

    private void assemble(Path asmFile, Path binFile) throws IOException {
        Path objFile = asmFile.resolveSibling(asmFile.getFileName() + ".o");
        runCommand(new String[]{"as", "-o", objFile.toString(), asmFile.toString()}, "as");
        runCommand(new String[]{"ld", "-o", binFile.toString(), objFile.toString()}, "ld");
        Files.deleteIfExists(objFile);
        
        binFile.toFile().setExecutable(true);
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
