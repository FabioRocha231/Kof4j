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


    public static boolean isSet(Type type) {
        if (type instanceof Type.ClassType ct) {
            return "kof".equals(ct.packageName()) && "Set".equals(ct.name());
        }
        return false;
    }
}
