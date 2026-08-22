package dev.kof.compiler;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.objectweb.asm.Opcodes.*;

/**
 * JVM Backend — transforms Kof IR into JVM bytecode via ASM.
 *
 * This class is the ONLY place that should know about ASM, JVM descriptors,
 * and JVM opcodes. The core IR does NOT depend on any of these.
 */
class JvmBackend implements Backend {

    private final Map<LabelId, Label> labelMap = new HashMap<>();

    private Label resolveLabel(LabelId id) {
        return labelMap.computeIfAbsent(id, k -> new Label());
    }

    @Override
    public void emit(IRModule module, Path outputDir) throws IOException {
        for (IRClass clazz : module.classes()) {
            emitClass(clazz, outputDir);
        }
    }

    private void emitClass(IRClass clazz, Path outputDir) throws IOException {
        Path classFile = outputDir.resolve(clazz.name() + ".class");
        Files.createDirectories(classFile.getParent());

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        String superName = clazz.superName() != null ? clazz.superName() : "java/lang/Object";
        cw.visit(V21, clazz.accessFlags(), clazz.name(), clazz.signature(),
                superName, clazz.interfaces().toArray(new String[0]));

        for (IRField field : clazz.fields()) {
            String desc = JvmTypeMapper.toDescriptor(field.type());
            cw.visitField(field.accessFlags(), field.name(), desc, null, field.initialValue()).visitEnd();
        }

        for (IRMethod method : clazz.methods()) {
            emitMethod(cw, clazz.name(), method);
        }

        cw.visitEnd();
        Files.write(classFile, cw.toByteArray());
    }

    private void emitMethod(ClassWriter cw, String className, IRMethod method) {
        String desc = JvmTypeMapper.toMethodDescriptor(method.returnType(), method.parameterTypes());
        MethodVisitor mv = cw.visitMethod(method.accessFlags(), method.name(), desc,
                null, method.thrownExceptions().toArray(new String[0]));
        mv.visitCode();

        int maxStack = 0;
        int maxLocals = 0;
        for (IRBasicBlock block : method.basicBlocks()) {
            maxLocals = Math.max(maxLocals, computeLocals(block.operations()));
            maxStack = Math.max(maxStack, computeStack(block.operations()));
        }

        for (IRBasicBlock block : method.basicBlocks()) {
            for (KofOperation op : block.operations()) {
                emitOperation(mv, className, op);
            }
        }

        mv.visitMaxs(maxStack, maxLocals);
        mv.visitEnd();
    }

    private void emitOperation(MethodVisitor mv, String className, KofOperation op) {
        if (op instanceof KofLoadLiteral lit) {
            emitLoadLiteral(mv, lit);
        } else if (op instanceof KofLoadLocal ll) {
            mv.visitVarInsn(loadVarOpcode(ll.type()), ll.index());
        } else if (op instanceof KofStoreLocal sl) {
            mv.visitVarInsn(storeVarOpcode(sl.type()), sl.index());
        } else if (op instanceof KofLoadField lf) {
            String owner = JvmTypeMapper.toInternalName(
                    lf.ownerType() instanceof Type.ClassType ct ? ct.packageName() : "",
                    lf.ownerType() instanceof Type.ClassType ct ? ct.name() : "?");
            mv.visitFieldInsn(GETFIELD, owner, lf.name(), JvmTypeMapper.toDescriptor(lf.fieldType()));
        } else if (op instanceof KofStoreField sf) {
            String owner = JvmTypeMapper.toInternalName(
                    sf.ownerType() instanceof Type.ClassType ct ? ct.packageName() : "",
                    sf.ownerType() instanceof Type.ClassType ct ? ct.name() : "?");
            mv.visitFieldInsn(PUTFIELD, owner, sf.name(), JvmTypeMapper.toDescriptor(sf.fieldType()));
        } else if (op instanceof KofGetStatic gs) {
            String owner = JvmTypeMapper.toInternalName(
                    gs.ownerType() instanceof Type.ClassType ct ? ct.packageName() : "",
                    gs.ownerType() instanceof Type.ClassType ct ? ct.name() : "?");
            mv.visitFieldInsn(GETSTATIC, owner, gs.name(), JvmTypeMapper.toDescriptor(gs.fieldType()));
        } else if (op instanceof KofPutStatic ps) {
            String owner = JvmTypeMapper.toInternalName(
                    ps.ownerType() instanceof Type.ClassType ct ? ct.packageName() : "",
                    ps.ownerType() instanceof Type.ClassType ct ? ct.name() : "?");
            mv.visitFieldInsn(PUTSTATIC, owner, ps.name(), JvmTypeMapper.toDescriptor(ps.fieldType()));
        } else if (op instanceof KofBinary kb) {
            switch (kb.op()) {
                case ADD -> mv.visitInsn(opcodeForArithmetic(kb.operandType(), IADD));
                case SUB -> mv.visitInsn(opcodeForArithmetic(kb.operandType(), ISUB));
                case MUL -> mv.visitInsn(opcodeForArithmetic(kb.operandType(), IMUL));
                case DIV -> mv.visitInsn(opcodeForArithmetic(kb.operandType(), IDIV));
                case MOD -> mv.visitInsn(opcodeForArithmetic(kb.operandType(), IREM));
                case EQ, NE, LT, LE, GT, GE -> {
                    int cmpOpcode = switch (kb.op()) {
                        case EQ -> IF_ICMPEQ;
                        case NE -> IF_ICMPNE;
                        case LT -> IF_ICMPLT;
                        case LE -> IF_ICMPLE;
                        case GT -> IF_ICMPGT;
                        case GE -> IF_ICMPGE;
                        default -> IF_ICMPEQ;
                    };
                    Label trueLabel = new Label();
                    Label endLabel = new Label();
                    mv.visitJumpInsn(cmpOpcode, trueLabel);
                    mv.visitInsn(ICONST_0);
                    mv.visitJumpInsn(GOTO, endLabel);
                    mv.visitLabel(trueLabel);
                    mv.visitInsn(ICONST_1);
                    mv.visitLabel(endLabel);
                }
            }
        } else if (op instanceof KofUnary ku) {
            if (ku.op() == KofUnaryOp.NEG) {
                mv.visitInsn(opcodeForArithmetic(ku.operandType(), INEG));
            }
        } else if (op instanceof KofLabel kl) {
            mv.visitLabel(resolveLabel(kl.label()));
        } else if (op instanceof KofJump kj) {
            mv.visitJumpInsn(GOTO, resolveLabel(kj.target()));
        } else if (op instanceof KofConditionalJump kc) {
            int opcode = switch (kc.comparison()) {
                case EQ -> IF_ICMPEQ;
                case NE -> IF_ICMPNE;
                case LT -> IF_ICMPLT;
                case LE -> IF_ICMPLE;
                case GT -> IF_ICMPGT;
                case GE -> IF_ICMPGE;
            };
            mv.visitJumpInsn(opcode, resolveLabel(kc.trueLabel()));
        } else if (op instanceof KofCall kc && BuiltinTypes.isList(kc.ownerType())) {
            switch (kc.methodName()) {
                case "kof_list_new" -> {
                    mv.visitTypeInsn(NEW, "java/util/ArrayList");
                    mv.visitInsn(DUP);
                    mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
                }
                case "kof_list_add" -> {
                    Type elemType = listElementType(kc.ownerType());
                    if (elemType instanceof Type.PrimitiveType pt && "int".equals(pt.name())) {
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                    }
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false);
                }
                case "kof_list_get" -> {
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "get", "(I)Ljava/lang/Object;", false);
                    Type elemType = listElementType(kc.ownerType());
                    if (elemType instanceof Type.PrimitiveType pt && "int".equals(pt.name())) {
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                    }
                }
                case "kof_list_set" -> mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "set", "(ILjava/lang/Object;)Ljava/lang/Object;", false);
                case "kof_list_size" -> mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "size", "()I", false);
                default -> {}
            }
        } else if (op instanceof KofCall kc) {
            String owner = "";
            if (kc.ownerType() instanceof Type.ClassType ct) {
                owner = JvmTypeMapper.toInternalName(ct.packageName(), ct.name());
            }
            String desc = JvmTypeMapper.toMethodDescriptor(kc.returnType(), kc.parameterTypes());
            switch (kc.kind()) {
                case INSTANCE -> mv.visitMethodInsn(INVOKEVIRTUAL, owner, kc.methodName(), desc, false);
                case STATIC -> mv.visitMethodInsn(INVOKESTATIC, owner, kc.methodName(), desc, false);
                case CONSTRUCTOR -> mv.visitMethodInsn(INVOKESPECIAL, owner, kc.methodName(), desc, false);
                case FUNCTION -> mv.visitMethodInsn(INVOKESTATIC, owner, kc.methodName(), desc, false);
                case INTERFACE -> mv.visitMethodInsn(INVOKEINTERFACE, owner, kc.methodName(), desc, true);
            }
        } else if (op instanceof KofNewObject no) {
            String typeName = no.type() instanceof Type.ClassType ct
                    ? JvmTypeMapper.toInternalName(ct.packageName(), ct.name()) : "?";
            mv.visitTypeInsn(NEW, typeName);
        } else if (op instanceof KofDup) {
            mv.visitInsn(DUP);
        } else if (op instanceof KofPop) {
            mv.visitInsn(POP);
        } else if (op instanceof KofReturn kr) {
            mv.visitInsn(returnOpcode(kr.returnType()));
        } else if (op instanceof KofReturnVoid) {
            mv.visitInsn(RETURN);
        } else if (op instanceof KofThrow) {
            mv.visitInsn(ATHROW);
        } else if (op instanceof KofCheckCast cc) {
            String type = cc.type() instanceof Type.ClassType ct
                    ? JvmTypeMapper.toInternalName(ct.packageName(), ct.name()) : "?";
            mv.visitTypeInsn(CHECKCAST, type);
        } else if (op instanceof KofInstanceOf io) {
            String type = io.type() instanceof Type.ClassType ct
                    ? JvmTypeMapper.toInternalName(ct.packageName(), ct.name()) : "?";
            mv.visitTypeInsn(INSTANCEOF, type);
        } else if (op instanceof KofNewArray na) {
            mv.visitIntInsn(NEWARRAY, arrayTypeForType(na.elementType()));
        } else if (op instanceof KofArrayLoad al) {
            mv.visitInsn(arrayLoadOpcode(al.elementType()));
        } else if (op instanceof KofArrayStore as) {
            mv.visitInsn(arrayStoreOpcode(as.elementType()));
        } else if (op instanceof KofArrayLength) {
            mv.visitInsn(ARRAYLENGTH);
        }
    }

    private void emitLoadLiteral(MethodVisitor mv, KofLoadLiteral lit) {
        if (lit.value() instanceof Integer i) {
            emitLoadInt(mv, i);
        } else if (lit.value() instanceof Long l) {
            emitLoadLong(mv, l);
        } else if (lit.value() instanceof Float f) {
            emitLoadFloat(mv, f);
        } else if (lit.value() instanceof Double d) {
            emitLoadDouble(mv, d);
        } else if (lit.value() instanceof String s) {
            mv.visitLdcInsn(s);
        } else if (lit.value() == null) {
            mv.visitInsn(ACONST_NULL);
        }
    }

    private void emitLoadInt(MethodVisitor mv, int value) {
        if (value >= -1 && value <= 5) mv.visitInsn(ICONST_0 + value);
        else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) mv.visitIntInsn(BIPUSH, value);
        else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) mv.visitIntInsn(SIPUSH, value);
        else mv.visitLdcInsn(value);
    }

    private void emitLoadLong(MethodVisitor mv, long value) {
        if (value == 0L) mv.visitInsn(LCONST_0);
        else if (value == 1L) mv.visitInsn(LCONST_1);
        else mv.visitLdcInsn(value);
    }

    private void emitLoadFloat(MethodVisitor mv, float value) {
        if (value == 0f) mv.visitInsn(FCONST_0);
        else if (value == 1f) mv.visitInsn(FCONST_1);
        else if (value == 2f) mv.visitInsn(FCONST_2);
        else mv.visitLdcInsn(value);
    }

    private void emitLoadDouble(MethodVisitor mv, double value) {
        if (value == 0.0) mv.visitInsn(DCONST_0);
        else if (value == 1.0) mv.visitInsn(DCONST_1);
        else mv.visitLdcInsn(value);
    }

    private int opcodeForArithmetic(Type type, int intOpcode) {
        if (type instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "long", "Long" -> intOpcode + 1;
                case "float", "Float" -> intOpcode + 2;
                case "double", "Double" -> intOpcode + 3;
                default -> intOpcode;
            };
        }
        return intOpcode;
    }

    private int returnOpcode(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "void" -> RETURN;
                case "int", "Int", "boolean", "bool", "Bool", "byte", "Byte", "short", "Short", "char", "Char" -> IRETURN;
                case "long", "Long" -> LRETURN;
                case "float", "Float" -> FRETURN;
                case "double", "Double" -> DRETURN;
                default -> ARETURN;
            };
        }
        return ARETURN;
    }

    private int arrayTypeForType(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "boolean", "bool", "Bool" -> T_BOOLEAN;
                case "byte", "Byte" -> T_BYTE;
                case "short", "Short" -> T_SHORT;
                case "char", "Char" -> T_CHAR;
                case "int", "Int" -> T_INT;
                case "long", "Long" -> T_LONG;
                case "float", "Float" -> T_FLOAT;
                case "double", "Double" -> T_DOUBLE;
                default -> T_BYTE;
            };
        }
        return T_BYTE;
    }

    private int arrayLoadOpcode(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "int", "Int", "boolean", "bool", "Bool", "byte", "Byte", "short", "Short", "char", "Char" -> IALOAD;
                case "long", "Long" -> LALOAD;
                case "float", "Float" -> FALOAD;
                case "double", "Double" -> DALOAD;
                default -> AALOAD;
            };
        }
        return AALOAD;
    }

    private int arrayStoreOpcode(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "int", "Int", "boolean", "bool", "Bool", "byte", "Byte", "short", "Short", "char", "Char" -> IASTORE;
                case "long", "Long" -> LASTORE;
                case "float", "Float" -> FASTORE;
                case "double", "Double" -> DASTORE;
                default -> AASTORE;
            };
        }
        return AASTORE;
    }

    private int computeLocals(List<KofOperation> ops) {
        int max = 0;
        for (KofOperation op : ops) {
            if (op instanceof KofLoadLocal ll) {
                max = Math.max(max, ll.index() + (isDoubleWidth(ll.type()) ? 2 : 1));
            } else if (op instanceof KofStoreLocal sl) {
                max = Math.max(max, sl.index() + (isDoubleWidth(sl.type()) ? 2 : 1));
            }
        }
        return Math.max(max, 1);
    }

    private int computeStack(List<KofOperation> ops) {
        int depth = 0;
        int max = 0;
        for (KofOperation op : ops) {
            if (op instanceof KofLoadLocal ll) {
                depth++;
                if (isDoubleWidth(ll.type())) depth++;
            } else if (op instanceof KofLoadLiteral || op instanceof KofNewObject || op instanceof KofArrayLength || op instanceof KofInstanceOf || op instanceof KofGetStatic) {
                depth++;
            } else if (op instanceof KofDup) {
                depth++;
            } else if (op instanceof KofPop) {
                depth--;
            } else if (op instanceof KofStoreLocal || op instanceof KofStoreField || op instanceof KofPutStatic) {
                depth -= 2;
            } else if (op instanceof KofLoadField || op instanceof KofUnary || op instanceof KofCheckCast) {
            } else if (op instanceof KofBinary) {
                depth--;
            } else if (op instanceof KofReturn kr) {
                if (!Type.isVoid(kr.returnType())) depth--;
            } else if (op instanceof KofReturnVoid) {
            } else if (op instanceof KofNewArray || op instanceof KofArrayLoad) {
                depth--;
            } else if (op instanceof KofArrayStore) {
                depth -= 3;
            } else if (op instanceof KofThrow) {
                depth--;
            } else if (op instanceof KofLabel || op instanceof KofJump) {
            } else if (op instanceof KofConditionalJump) {
                depth -= 2;
            } else if (op instanceof KofCall) {
                depth -= 1;
            }
            max = Math.max(max, depth);
            if (depth < 0) depth = 0;
        }
        return Math.max(max, 1);
    }

    private int loadVarOpcode(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "int", "Int", "boolean", "bool", "Bool", "byte", "Byte", "short", "Short", "char", "Char" -> ILOAD;
                case "long", "Long" -> LLOAD;
                case "float", "Float" -> FLOAD;
                case "double", "Double" -> DLOAD;
                default -> ALOAD;
            };
        }
        return ALOAD;
    }

    private int storeVarOpcode(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "int", "Int", "boolean", "bool", "Bool", "byte", "Byte", "short", "Short", "char", "Char" -> ISTORE;
                case "long", "Long" -> LSTORE;
                case "float", "Float" -> FSTORE;
                case "double", "Double" -> DSTORE;
                default -> ASTORE;
            };
        }
        return ASTORE;
    }

    private boolean isDoubleWidth(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return "long".equals(pt.name()) || "Long".equals(pt.name()) ||
                   "double".equals(pt.name()) || "Double".equals(pt.name());
        }
        return false;
    }

    private Type listElementType(Type listType) {
        if (listType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) {
            return ct.typeArguments().get(0);
        }
        return Type.UnknownType.UNKNOWN;
    }
}
