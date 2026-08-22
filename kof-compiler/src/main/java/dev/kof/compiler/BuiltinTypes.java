package dev.kof.compiler;

import java.util.List;

/**
 * BuiltinTypes — centralized references to Kof builtin types.
 *
 * This eliminates scattered "java.lang.String" ClassType construction.
 * All backends and compiler phases should reference these constants
 * instead of creating their own ClassType instances.
 */
public final class BuiltinTypes {

    private BuiltinTypes() {}

    /** Kof String type — backed by java.lang.String on JVM, KofString on Native. */
    public static final Type STRING = new Type.ClassType("java.lang", "String", List.of());

    /** Kof String array type — used for main() parameter. */
    public static final Type STRING_ARRAY = new Type.ArrayType(STRING);

    /** Checks if a type is the Kof String type. */
    public static boolean isString(Type type) {
        if (type instanceof Type.ClassType ct) {
            return "java.lang".equals(ct.packageName()) && "String".equals(ct.name());
        }
        return false;
    }

    /** Checks if a type is a Kof reference type (ClassType or ArrayType). */
    public static boolean isReferenceType(Type type) {
        return type instanceof Type.ClassType || type instanceof Type.ArrayType;
    }
}
