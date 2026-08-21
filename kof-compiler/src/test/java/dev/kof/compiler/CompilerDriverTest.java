package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompilerDriverTest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void compilesRecordToJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Point.kf");
        Files.writeString(source, "record Point(int x, int y)");
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
        assertTrue(Files.exists(tempDir.resolve("out/Point.class")), "Class file should exist");
    }

    @Test
    void compilesRecordToNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Point.kf");
        Files.writeString(source, "record Point(int x, int y)");
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Native compilation should succeed");
    }

    @Test
    void compilesFunctionWithPrintln(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                println("Hello, Kof!")
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void compilesFunctionWithVariables(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var nome = "Mel"
                var idade = 26
                println(nome)
                println(idade)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void compilesPackageAndImport(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            package com.example

            import java.util.ArrayList

            fun main() {
                println("Package and import work!")
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void compilesWithoutSemicolons(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                println("No semicolons!")
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed without semicolons");
    }

    @Test
    void failsOnInvalidSyntax(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Bad.kf");
        Files.writeString(source, "class {{{ invalid");
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "Compilation should fail on invalid syntax");
        assertTrue(result.diagnostics().hasErrors(), "Should have error diagnostics");
    }

    @Test
    void compilesClassWithFieldsAndMethods(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            public class User {
                String name
                public fun getName(): String {
                    return name
                }
                public constructor(String name) {
                    this.name = name
                }
            }
            fun main() {
                var user = new User("Mel")
                println(user.getName())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
        assertTrue(Files.exists(tempDir.resolve("out/User.class")), "User class should exist");
        assertTrue(Files.exists(tempDir.resolve("out/Default/Main.class")), "Main class should exist");
    }

    @Test
    void compilesClassWithExpressionBodyMethod(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            public class Calculator {
                public fun add(Int a, Int b): Int = a + b
            }
            fun main() {
                var c = new Calculator()
                println(c.add(2, 3))
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
        assertTrue(Files.exists(tempDir.resolve("out/Calculator.class")), "Calculator class should exist");
    }

    @Test
    void compilesRecordInstantiation(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record Point(Int x, Int y)
            fun main() {
                var p = Point(10, 20)
                println(p.x())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
        assertTrue(Files.exists(tempDir.resolve("out/Point.class")), "Point class should exist");
    }

    @Test
    void compilesClassWithNestedScopes(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var x = 10
                if (x > 5) {
                    var y = 20
                    println(y)
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void compilesForLoop(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                for (var i = 0; i < 5; i++) {
                    println(i)
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void compilesWhileLoop(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var i = 0
                while (i < 5) {
                    println(i)
                    i = i + 1
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void compilesIfElse(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var x = 10
                if (x > 5) {
                    println("greater")
                } else {
                    println("smaller")
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void compilesArithmetic(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var x = 10
                var y = 20
                println(x + y)
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void compilesClassWithDefaultConstructor(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            public class Empty {
                public fun getValue(): Int = 42
            }
            fun main() {
                var e = new Empty()
                println(e.getValue())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
    }

    // ── Architectural isolation tests ──────────────────────────────

    @Test
    void irNodesHasNoAsmDependency() throws Exception {
        // Verify that IRNodes.java does not import org.objectweb.asm
        Path irNodes = Path.of("src/main/java/dev/kof/compiler/IRNodes.java");
        String content = Files.readString(irNodes);
        assertFalse(content.contains("org.objectweb.asm"), "IRNodes must not depend on ASM");
    }

    @Test
    void compilerDriverHasNoAsmDependency() throws Exception {
        Path compilerDriver = Path.of("src/main/java/dev/kof/compiler/CompilerDriver.java");
        String content = Files.readString(compilerDriver);
        assertFalse(content.contains("org.objectweb.asm"), "CompilerDriver must not depend on ASM");
    }

    @Test
    void typeSystemHasNoAsmDependency() throws Exception {
        Path type = Path.of("src/main/java/dev/kof/compiler/Type.java");
        String content = Files.readString(type);
        assertFalse(content.contains("org.objectweb.asm"), "Type must not depend on ASM");
    }

    @Test
    void semanticAnalyzerHasNoAsmDependency() throws Exception {
        Path sa = Path.of("src/main/java/dev/kof/compiler/SemanticAnalyzer.java");
        String content = Files.readString(sa);
        assertFalse(content.contains("org.objectweb.asm"), "SemanticAnalyzer must not depend on ASM");
    }

    @Test
    void symbolTableHasNoAsmDependency() throws Exception {
        Path st = Path.of("src/main/java/dev/kof/compiler/SymbolTable.java");
        String content = Files.readString(st);
        assertFalse(content.contains("org.objectweb.asm"), "SymbolTable must not depend on ASM");
    }

    @Test
    void nativeBackendHasNoJvmTypeMapperDependency() throws Exception {
        Path nb = Path.of("src/main/java/dev/kof/compiler/NativeBackend.java");
        String content = Files.readString(nb);
        assertFalse(content.contains("JvmTypeMapper"), "NativeBackend must not use JvmTypeMapper");
    }

    // ── IR representation tests ────────────────────────────────────

    @Test
    void irFieldUsesTypeNotDescriptor() {
        IRField field = new IRField("x", Type.PrimitiveType.INT, 0, null);
        assertEquals(Type.PrimitiveType.INT, field.type());
        assertEquals("x", field.name());
    }

    @Test
    void irMethodUsesTypeNotDescriptor() {
        IRMethod method = new IRMethod("add", Type.PrimitiveType.INT,
                List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT), 0, List.of(),
                List.of(), List.of());
        assertEquals(Type.PrimitiveType.INT, method.returnType());
        assertEquals(2, method.parameterTypes().size());
    }

    @Test
    void kofLoadLiteralCreation() {
        KofLoadLiteral intLit = KofLoadLiteral.ofInt(42);
        assertEquals(Type.PrimitiveType.INT, intLit.type());
        assertEquals(42, intLit.value());

        KofLoadLiteral strLit = KofLoadLiteral.ofString("hello");
        assertEquals("hello", strLit.value());

        KofLoadLiteral nullLit = KofLoadLiteral.ofNull();
        assertNull(nullLit.value());
    }

    @Test
    void kofCallSemanticRepresentation() {
        Type ownerType = new Type.ClassType("com.example", "Calculator", List.of());
        KofCall call = new KofCall(ownerType, "add",
                List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT),
                Type.PrimitiveType.INT, KofCallKind.INSTANCE);
        assertEquals("add", call.methodName());
        assertEquals(KofCallKind.INSTANCE, call.kind());
    }

    @Test
    void labelIdCreation() {
        LabelId.reset();
        LabelId a = LabelId.create();
        LabelId b = LabelId.create();
        assertNotEquals(a.id(), b.id());
    }

    @Test
    void accessFlagsAreSemantic() {
        assertTrue((AccessFlags.PUBLIC & 0x0001) != 0);
        assertTrue((AccessFlags.STATIC & 0x0008) != 0);
        assertTrue((AccessFlags.FINAL & 0x0010) != 0);
    }

    // ── End-to-end JVM tests ───────────────────────────────────────

    @Test
    void compilesRecordInstantiationWithAccessor(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record Point(Int x, Int y)
            fun main() {
                var p = Point(10, 20)
                println(p.x())
                println(p.y())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void compilesClassWithConstructor(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            public class User {
                String name
                public constructor(String name) { this.name = name }
                public fun getName(): String { return name }
            }
            fun main() {
                var user = new User("Mel")
                println(user.getName())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void compilesNestedControlFlow(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            fun main() {
                var x = 10
                if (x > 5) {
                    var y = 20
                    if (y > 15) {
                        println("nested")
                    }
                }
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
    }

    @Test
    void compilesClassWithFieldAssignment(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            public class Counter {
                Int value
                public constructor(Int v) { this.value = v }
                public fun getValue(): Int { return value }
                public fun increment() { this.value = this.value + 1 }
            }
            fun main() {
                var c = new Counter(10)
                c.increment()
                println(c.getValue())
            }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed");
    }
}
