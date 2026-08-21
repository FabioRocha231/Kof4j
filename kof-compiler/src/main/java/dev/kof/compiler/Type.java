package dev.kof.compiler;

import java.util.List;
import java.util.Map;

sealed interface Type {
    record PrimitiveType(String name, int sort) implements Type {
        static final PrimitiveType BOOL = new PrimitiveType("bool", 1);
        static final PrimitiveType BYTE = new PrimitiveType("byte", 5);
        static final PrimitiveType SHORT = new PrimitiveType("short", 9);
        static final PrimitiveType INT = new PrimitiveType("int", 10);
        static final PrimitiveType LONG = new PrimitiveType("long", 11);
        static final PrimitiveType FLOAT = new PrimitiveType("float", 6);
        static final PrimitiveType DOUBLE = new PrimitiveType("double", 7);
        static final PrimitiveType CHAR = new PrimitiveType("char", 2);
        static final PrimitiveType VOID = new PrimitiveType("void", 0);
    }

    record ClassType(String packageName, String name, List<Type> typeArguments) implements Type {
        String internalName() {
            if (packageName.isEmpty()) return name;
            return packageName.replace('.', '/') + "/" + name;
        }
    }

    record ArrayType(Type componentType) implements Type {
    }

    record TypeVariable(String name) implements Type {
    }

    record WildcardType(Type bound, boolean upper) implements Type {
    }

    record UnknownType() implements Type {
        static final UnknownType UNKNOWN = new UnknownType();
    }

    static Type of(String name) {
        return switch (name) {
            case "bool", "boolean", "Bool", "Boolean" -> PrimitiveType.BOOL;
            case "byte", "Byte" -> PrimitiveType.BYTE;
            case "short", "Short" -> PrimitiveType.SHORT;
            case "int", "Int" -> PrimitiveType.INT;
            case "long", "Long" -> PrimitiveType.LONG;
            case "float", "Float" -> PrimitiveType.FLOAT;
            case "double", "Double" -> PrimitiveType.DOUBLE;
            case "char", "Char" -> PrimitiveType.CHAR;
            case "void", "Void" -> PrimitiveType.VOID;
            case "string", "String" -> new ClassType("java.lang", "String", List.of());
            default -> new ClassType("", name, List.of());
        };
    }

    static boolean isPrimitive(Type type) {
        return type instanceof PrimitiveType && !(type instanceof PrimitiveType p && "void".equals(p.name()));
    }

    static boolean isVoid(Type type) {
        return type instanceof PrimitiveType p && "void".equals(p.name());
    }

    static boolean isUnknown(Type type) {
        return type instanceof UnknownType;
    }
}
