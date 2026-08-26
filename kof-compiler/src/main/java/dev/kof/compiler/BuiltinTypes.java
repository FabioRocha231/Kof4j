package dev.kof.compiler;

import java.util.List;


public final class BuiltinTypes {

    private BuiltinTypes() {}


    public static final Type STRING = new Type.ClassType("java.lang", "String", List.of());


    public static final Type STRING_ARRAY = new Type.ArrayType(STRING);


    public static boolean isString(Type type) {
        if (type instanceof Type.ClassType ct) {
            return "java.lang".equals(ct.packageName()) && "String".equals(ct.name());
        }
        return false;
    }


    public static boolean isReferenceType(Type type) {
        return type instanceof Type.ClassType || type instanceof Type.ArrayType;
    }


    public static final Type LIST = new Type.ClassType("kof", "List", List.of());


    public static boolean isList(Type type) {
        if (type instanceof Type.ClassType ct) {
            return "kof".equals(ct.packageName()) && "List".equals(ct.name());
        }
        return false;
    }


    public static final Type MAP = new Type.ClassType("kof", "Map", List.of());


    public static boolean isMap(Type type) {
        if (type instanceof Type.ClassType ct) {
            return "kof".equals(ct.packageName()) && "Map".equals(ct.name());
        }
        return false;
    }


    public static final Type SET = new Type.ClassType("kof", "Set", List.of());


    /**
     * Enums declarados na compilação corrente: em runtime o valor do enum É
     * o nome (String) — mapeado para java/lang/String em todos os backends.
     */
    private static final java.util.Set<String> ENUM_NAMES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void resetEnums() {
        ENUM_NAMES.clear();
    }

    public static void registerEnum(String name) {
        if (name != null) ENUM_NAMES.add(name);
    }

    public static boolean isEnumName(String name) {
        return name != null && ENUM_NAMES.contains(name);
    }

    public static boolean isEnumType(Type type) {
        return type instanceof Type.ClassType ct
                && ct.packageName().isEmpty() && isEnumName(ct.name());
    }


    public static boolean isSet(Type type) {
        if (type instanceof Type.ClassType ct) {
            return "kof".equals(ct.packageName()) && "Set".equals(ct.name());
        }
        return false;
    }
}
