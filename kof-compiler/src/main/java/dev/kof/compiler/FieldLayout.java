package dev.kof.compiler;


public record FieldLayout(
        String name,
        Type type,
        int offset,
        int size
) {

    public static int sizeOf(Type type) {
        return switch (type) {
            case Type.PrimitiveType pt -> switch (pt.name()) {
                case "byte", "Byte" -> 8;
                case "short", "Short" -> 8;
                case "int", "Int", "boolean", "bool", "Bool" -> 8;
                case "long", "Long" -> 8;
                case "float", "Float" -> 8;
                case "double", "Double" -> 8;
                case "char", "Char" -> 8;
                default -> 8;
            };
            default -> 8;
        };
    }


    public static int naturalSize(Type type) {
        return switch (type) {
            case Type.PrimitiveType pt -> switch (pt.name()) {
                case "byte", "Byte" -> 1;
                case "short", "Short" -> 2;
                case "int", "Int", "boolean", "bool", "Bool" -> 4;
                case "long", "Long" -> 8;
                case "float", "Float" -> 4;
                case "double", "Double" -> 8;
                case "char", "Char" -> 4;
                default -> 8;
            };
            default -> 8;
        };
    }
}
