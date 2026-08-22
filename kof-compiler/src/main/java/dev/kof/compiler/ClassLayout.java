package dev.kof.compiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * ClassLayout — centralized field layout calculation for objects.
 *
 * This replaces the ad-hoc computeFieldOffset in NativeBackend.
 * Both JVM and Native backends can use this for consistent field layout.
 *
 * The layout is:
 *   [header: 8 bytes] [field_0] [field_1] ... [field_n] [padding to 16-byte alignment]
 *
 * Header contains:
 *   - type_id (4 bytes)
 *   - flags (4 bytes)
 */
public class ClassLayout {

    public static final int HEADER_SIZE = 16;
    public static final int ALIGNMENT = 16;
    public static final int METHOD_TABLE_OFFSET = 8;

    private final String className;
    private final List<FieldLayout> fields;
    private final int totalSize;

    private ClassLayout(String className, List<FieldLayout> fields, int totalSize) {
        this.className = className;
        this.fields = Collections.unmodifiableList(fields);
        this.totalSize = totalSize;
    }

    public String className() { return className; }
    public List<FieldLayout> fields() { return fields; }
    public int totalSize() { return totalSize; }

    /**
     * Returns the offset of a field by name, or -1 if not found.
     */
    public int fieldOffset(String name) {
        for (FieldLayout f : fields) {
            if (f.name().equals(name)) return f.offset();
        }
        return -1;
    }

    /**
     * Returns the size of a field by name, or -1 if not found.
     */
    public int fieldSize(String name) {
        for (FieldLayout f : fields) {
            if (f.name().equals(name)) return f.size();
        }
        return -1;
    }

    /**
     * Builds a ClassLayout from an IRClass.
     */
    public static ClassLayout build(IRClass clazz) {
        List<FieldLayout> fields = new ArrayList<>();
        int offset = HEADER_SIZE;
        for (IRField field : clazz.fields()) {
            int size = FieldLayout.sizeOf(field.type());
            fields.add(new FieldLayout(field.name(), field.type(), offset, size));
            offset += size;
        }
        int totalSize = align(offset, ALIGNMENT);
        return new ClassLayout(clazz.name(), fields, totalSize);
    }

    /**
     * Builds a ClassLayout with inheritance support.
     * Walks the superclass chain to include inherited fields.
     * superclassResolver maps superName to IRClass (null if not found).
     */
    public static ClassLayout buildWithSuper(IRClass clazz, Function<String, IRClass> superclassResolver) {
        List<FieldLayout> allFields = new ArrayList<>();
        int offset = HEADER_SIZE;

        List<String> hierarchy = new ArrayList<>();
        String current = clazz.superName();
        while (current != null && !current.isEmpty() && !"java/lang/Object".equals(current)) {
            hierarchy.add(0, current);
            IRClass superClazz = superclassResolver.apply(current);
            if (superClazz == null) break;
            current = superClazz.superName();
        }

        for (String superName : hierarchy) {
            IRClass superClazz = superclassResolver.apply(superName);
            if (superClazz == null) continue;
            for (IRField field : superClazz.fields()) {
                int size = FieldLayout.sizeOf(field.type());
                allFields.add(new FieldLayout(field.name(), field.type(), offset, size));
                offset += size;
            }
        }

        for (IRField field : clazz.fields()) {
            int size = FieldLayout.sizeOf(field.type());
            allFields.add(new FieldLayout(field.name(), field.type(), offset, size));
            offset += size;
        }

        int totalSize = align(offset, ALIGNMENT);
        return new ClassLayout(clazz.name(), allFields, totalSize);
    }

    /**
     * Builds a ClassLayout from a list of IRFields with a given class name.
     */
    public static ClassLayout build(String className, List<IRField> fieldList) {
        List<FieldLayout> fields = new ArrayList<>();
        int offset = HEADER_SIZE;
        for (IRField field : fieldList) {
            int size = FieldLayout.sizeOf(field.type());
            fields.add(new FieldLayout(field.name(), field.type(), offset, size));
            offset += size;
        }
        int totalSize = align(offset, ALIGNMENT);
        return new ClassLayout(className, fields, totalSize);
    }

    /**
     * Aligns value up to the next multiple of alignment.
     */
    public static int align(int value, int alignment) {
        return (value + alignment - 1) & ~(alignment - 1);
    }

    public static void clearCache() {
    }
}
