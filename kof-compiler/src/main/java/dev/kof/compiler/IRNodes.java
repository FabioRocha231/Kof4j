package dev.kof.compiler;

import java.util.List;

/**
 * Kof IR — Backend-agnostic intermediate representation.
 *
 * This IR represents Kof language semantics, NOT any specific VM or backend.
 * JVM descriptors, ASM labels, and JVM opcodes do NOT belong here.
 */

// ── Module / Class / Method / Field ───────────────────────────────

record IRModule(String name, List<IRClass> classes, List<String> imports) {
}

record IRClass(String name, String superName, List<String> interfaces,
               int accessFlags, List<IRField> fields, List<IRMethod> methods,
               List<String> innerClasses, String signature) {
}

record IRField(String name, Type type, int accessFlags, Object initialValue) {
}

record IRMethod(String name, Type returnType, List<Type> parameterTypes, int accessFlags,
                List<String> thrownExceptions, List<IRBasicBlock> basicBlocks,
                List<IRLocalVariable> localVariables) {
}

record IRBasicBlock(int index, List<KofOperation> operations) {
}

record IRLocalVariable(int index, String name, Type type) {
}

// ── Labels ────────────────────────────────────────────────────────

record LabelId(int id) {
    private static int counter = 0;
    static LabelId create() { return new LabelId(counter++); }
    static void reset() { counter = 0; }
}

// ── Literals ──────────────────────────────────────────────────────

sealed interface KofOperation {
}

record KofLoadLiteral(Type type, Object value) implements KofOperation {
    static KofLoadLiteral ofInt(int value) {
        return new KofLoadLiteral(Type.PrimitiveType.INT, value);
    }
    static KofLoadLiteral ofLong(long value) {
        return new KofLoadLiteral(Type.PrimitiveType.LONG, value);
    }
    static KofLoadLiteral ofFloat(float value) {
        return new KofLoadLiteral(Type.PrimitiveType.FLOAT, value);
    }
    static KofLoadLiteral ofDouble(double value) {
        return new KofLoadLiteral(Type.PrimitiveType.DOUBLE, value);
    }
    static KofLoadLiteral ofString(String value) {
        return new KofLoadLiteral(BuiltinTypes.STRING, value);
    }
    static KofLoadLiteral ofBool(boolean value) {
        return new KofLoadLiteral(Type.PrimitiveType.BOOL, value ? 1 : 0);
    }
    static KofLoadLiteral ofNull() {
        return new KofLoadLiteral(Type.UnknownType.UNKNOWN, null);
    }
}

// ── Variables ─────────────────────────────────────────────────────

record KofLoadLocal(Type type, int index) implements KofOperation {
}
record KofStoreLocal(Type type, int index) implements KofOperation {
}

// ── Fields ────────────────────────────────────────────────────────

record KofLoadField(Type ownerType, String name, Type fieldType) implements KofOperation {
}
record KofStoreField(Type ownerType, String name, Type fieldType) implements KofOperation {
}

// ── Static Fields ─────────────────────────────────────────────────

record KofGetStatic(Type ownerType, String name, Type fieldType) implements KofOperation {
}
record KofPutStatic(Type ownerType, String name, Type fieldType) implements KofOperation {
}

// ── Arithmetic ────────────────────────────────────────────────────

enum KofBinaryOp { ADD, SUB, MUL, DIV, MOD }
enum KofUnaryOp { NEG, NOT }

record KofBinary(KofBinaryOp op, Type operandType) implements KofOperation {
}
record KofUnary(KofUnaryOp op, Type operandType) implements KofOperation {
}

// ── Comparisons ───────────────────────────────────────────────────

enum KofComparison { EQ, NE, LT, LE, GT, GE }

// ── Control Flow ──────────────────────────────────────────────────

record KofLabel(LabelId label) implements KofOperation {
}
record KofJump(LabelId target) implements KofOperation {
}
record KofConditionalJump(KofComparison comparison, LabelId trueLabel, LabelId falseLabel) implements KofOperation {
}

// ── Calls ─────────────────────────────────────────────────────────

enum KofCallKind { INSTANCE, STATIC, CONSTRUCTOR, FUNCTION, INTERFACE }

record KofCall(Type ownerType, String methodName, List<Type> parameterTypes,
               Type returnType, KofCallKind kind) implements KofOperation {
}

// ── Object Creation ───────────────────────────────────────────────

record KofNewObject(Type type, List<Type> argumentTypes) implements KofOperation {
}

// ── Return ────────────────────────────────────────────────────────

record KofReturn(Type returnType) implements KofOperation {
}
record KofReturnVoid() implements KofOperation {
}

// ── Stack (low-level, needed by some backends) ────────────────────

record KofDup() implements KofOperation {
}
record KofPop() implements KofOperation {
}

// ── Type Operations ───────────────────────────────────────────────

record KofCheckCast(Type type) implements KofOperation {
}
record KofInstanceOf(Type type) implements KofOperation {
}

// ── Arrays ────────────────────────────────────────────────────────

record KofArrayLoad(Type elementType) implements KofOperation {
}
record KofArrayStore(Type elementType) implements KofOperation {
}
record KofNewArray(Type elementType) implements KofOperation {
}
record KofArrayLength() implements KofOperation {
}

// ── Exception ─────────────────────────────────────────────────────

record KofThrow() implements KofOperation {
}

record TryCatchRegion(LabelId tryStart, LabelId tryEnd, LabelId handlerStart,
                      String catchType) implements KofOperation {
}
