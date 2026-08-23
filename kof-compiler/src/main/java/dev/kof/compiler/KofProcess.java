package dev.kof.compiler;

import java.util.List;


/**
 * kof.process — multiplatform process abstraction.
 *
 * The same Kof code runs on every backend; each runtime implements the
 * ProcessBuilder/fork/execve/child_process mechanics behind one API:
 *
 *   var result = process.run("git", "status")
 *   println(result.stdout)
 *   println(result.exitCode)
 */
final class KofProcess {

    private KofProcess() {}

    static final Type RESULT = new Type.ClassType("kof.process", "Result", List.of());
    static final Type STRING_LIST = new Type.ClassType("kof", "List", List.of(BuiltinTypes.STRING));

    static boolean isResult(Type t) {
        return RESULT.equals(t);
    }

    /** Fields exposed by the process result object. */
    static Type fieldType(String name) {
        return switch (name) {
            case "stdout", "stderr" -> BuiltinTypes.STRING;
            case "exitCode" -> Type.PrimitiveType.INT;
            default -> Type.UnknownType.UNKNOWN;
        };
    }

    static boolean isField(String name) {
        return "stdout".equals(name) || "stderr".equals(name) || "exitCode".equals(name);
    }

    record ProcessCall(String function, Type returnType, List<Type> parameterTypes) {
    }

    /** process.run(program, args...) — the only entry point for now. */
    static ProcessCall runCall(List<Type> argTypes) {
        // [String, String, ...] or [String] — at least the program
        if (argTypes.isEmpty()) return null;
        Type head = argTypes.get(0);
        if (!BuiltinTypes.isString(head)) return null;
        for (int i = 1; i < argTypes.size(); i++) {
            if (!BuiltinTypes.isString(argTypes.get(i))) return null;
        }
        return new ProcessCall("kof_process_run", RESULT, argTypes);
    }
}