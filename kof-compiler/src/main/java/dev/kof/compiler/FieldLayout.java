package dev.kof.compiler;

/**
 * FieldLayout — represents the layout of a single field within an object.
 *
 * Calculated by ClassLayout at compile time.
 * Used by backends to generate field access code.
 */
public record FieldLayout(
        String name,
        Type type,
        int offset,
        int size
) {
    /**
     * Returns the size of a type in bytes for object layout.
     * All fields are padded to 8 bytes for alignment.
     */
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

    /**
     * Returns the natural size of a type (before padding).
     */
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
