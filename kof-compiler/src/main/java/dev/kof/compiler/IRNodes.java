package dev.kof.compiler;

import java.util.List;





record IRModule(String name, List<IRClass> classes, List<String> imports, String sourceName) {
    IRModule(String name, List<IRClass> classes, List<String> imports) {
        this(name, classes, imports, null);
    }
}

record IRClass(String name, String superName, List<String> interfaces,
               int accessFlags, List<IRField> fields, List<IRMethod> methods,
               List<String> innerClasses, String signature, int typeId) {
}

record IRField(String name, Type type, int accessFlags, Object initialValue) {
}

record IRMethod(String name, Type returnType, List<Type> parameterTypes, int accessFlags,
                List<String> thrownExceptions, List<IRBasicBlock> basicBlocks,
                List<IRLocalVariable> localVariables, KofDebugInfo debugInfo) {
    IRMethod(String name, Type returnType, List<Type> parameterTypes, int accessFlags,
             List<String> thrownExceptions, List<IRBasicBlock> basicBlocks,
             List<IRLocalVariable> localVariables) {
        this(name, returnType, parameterTypes, accessFlags, thrownExceptions,
                basicBlocks, localVariables, KofDebugInfo.EMPTY);
    }
}

record IRBasicBlock(int index, List<KofOperation> operations) {
}

/**
 * KofDebugInfo — backend-agnostic debug metadata.
 * Maps each IR operation to its source position so backends can emit
 * line tables / source maps that keep the Kof identity. The position is
 * registered before the backend, never synthesized there.
 */
record KofDebugInfo(java.util.Map<KofOperation, SourcePosition> positions) {
    static final KofDebugInfo EMPTY = new KofDebugInfo(java.util.Map.of());
}

record IRLocalVariable(int index, String name, Type type) {
}



record LabelId(int id) {
    private static int counter = 0;
    static LabelId create() { return new LabelId(counter++); }
    static void reset() { counter = 0; }
}



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



record KofLoadLocal(Type type, int index) implements KofOperation {
}
record KofStoreLocal(Type type, int index) implements KofOperation {
}



record KofLoadField(Type ownerType, String name, Type fieldType) implements KofOperation {
}
record KofStoreField(Type ownerType, String name, Type fieldType) implements KofOperation {
}



record KofGetStatic(Type ownerType, String name, Type fieldType) implements KofOperation {
}
record KofPutStatic(Type ownerType, String name, Type fieldType) implements KofOperation {
}



enum KofBinaryOp { ADD, SUB, MUL, DIV, MOD, EQ, NE, LT, LE, GT, GE, AND, OR, XOR, SHL, SHR, USHR }
enum KofUnaryOp { NEG, NOT, I2L, I2F, I2D, L2F, L2D, F2D }

record KofBinary(KofBinaryOp op, Type operandType) implements KofOperation {
}
record KofUnary(KofUnaryOp op, Type operandType) implements KofOperation {
}



enum KofComparison { EQ, NE, LT, LE, GT, GE }



record KofLabel(LabelId label) implements KofOperation {
}
record KofJump(LabelId target) implements KofOperation {
}
record KofConditionalJump(KofComparison comparison, LabelId trueLabel, LabelId falseLabel) implements KofOperation {
}



enum KofCallKind { INSTANCE, STATIC, CONSTRUCTOR, FUNCTION, INTERFACE }

record KofCall(Type ownerType, String methodName, List<Type> parameterTypes,
               Type returnType, KofCallKind kind) implements KofOperation {
}



record KofNewObject(Type type, List<Type> argumentTypes) implements KofOperation {
}



record KofReturn(Type returnType) implements KofOperation {
}
record KofReturnVoid() implements KofOperation {
}



record KofDup() implements KofOperation {
}

/**
 * Duplicates the top value below the second slot: [A, B] → [B, A, B].
 * Used for postfix field increments (the receiver must survive for the store).
 */
record KofDupX1() implements KofOperation {
}

/**
 * Duplicates the top value two slots below: [A, B, C] → [C, A, B, C].
 * Used for prefix array increments (the value survives the array store).
 */
record KofDupX2() implements KofOperation {
}
record KofPop() implements KofOperation {
}



record KofCheckCast(Type type) implements KofOperation {
}
record KofInstanceOf(Type type) implements KofOperation {
}



record KofArrayLoad(Type elementType) implements KofOperation {
}
record KofArrayStore(Type elementType) implements KofOperation {
}
record KofNewArray(Type elementType) implements KofOperation {
}
record KofArrayLength() implements KofOperation {
}



record KofThrow() implements KofOperation {
}


record KofTryStart(LabelId startLabel, LabelId endLabel, LabelId handlerLabel,
                   String exceptionType, int excLocalIndex) implements KofOperation {
}


record KofTryEnd() implements KofOperation {
}


record KofCatchStart(LabelId handlerLabel, String exceptionType, int localIndex) implements KofOperation {
}
