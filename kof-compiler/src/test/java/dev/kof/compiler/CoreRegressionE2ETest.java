package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Core regressions (B10, B2, B3, B4, B9, B5, B7, implicit construction).
 * Every case is compiled to JVM and KofJS and executed; the observable
 * behavior must match on both targets.
 */
class CoreRegressionE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path outDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    private String runJs(Path outDir) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exitCode = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, exitCode, "JS exit code, output: " + out);
        return out.toString().trim();
    }

    private void runBoth(String source, String expected, Path tempDir, String name) throws IOException {
        Path src = tempDir.resolve(name + ".kf");
        Files.writeString(src, source);
        Path outJvm = tempDir.resolve(name + "-jvm");
        Path outJs = tempDir.resolve(name + "-js");
        CompilationResult rjvm = driver.compile(src, outJvm, Target.JVM);
        assertTrue(rjvm.success(), "JVM compile failed: " + rjvm.diagnostics().getDiagnostics());
        CompilationResult rjs = driver.compile(src, outJs, Target.JS);
        assertTrue(rjs.success(), "JS compile failed: " + rjs.diagnostics().getDiagnostics());
        assertEquals(expected, runJvm(outJvm), name + " JVM output mismatch");
        assertEquals(expected, runJs(outJs), name + " JS output mismatch");
    }

    // B10 — primary constructor fields accessible inside methods (all targets)
    @Test
    void primaryConstructorFieldsInMethods(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Connection(String host, Int porta) {
                    hostInfo(): String {
                        return host + ":" + porta
                    }
                }

                main() {
                    var c = Connection("localhost", 8080)
                    println(c.host)
                    println(c.porta)
                    println(c.hostInfo())
                    assert(c.hostInfo() == "localhost:8080")
                }
                """, "localhost\n8080\nlocalhost:8080", tempDir, "b10");
    }

    // B10 — record methods accessing components
    @Test
    void recordMethodsAccessComponents(@TempDir Path tempDir) throws IOException {
        runBoth("""
                record Token(String kind, String text) {
                    label(): String {
                        return kind + "(" + text + ")"
                    }
                }

                main() {
                    var t = Token("identifier", "hello")
                    println(t.kind())
                    println(t.label())
                }
                """, "identifier\nidentifier(hello)", tempDir, "b10-record");
    }

    // B2 — ++/-- on fields, locals, prefix and postfix
    @Test
    void incrementsOnFieldsAndLocals(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Counter {
                    Int value = 0

                    increment() {
                        value++
                    }

                    decrement() {
                        value--
                    }

                    get(): Int {
                        return value
                    }
                }

                main() {
                    var c = Counter()
                    c.increment()
                    c.increment()
                    c.decrement()
                    println(c.get())
                    var x = 1
                    var y = x++
                    println(x)
                    println(y)
                    var z = 5
                    println(++z)
                    println(z--)
                    println(z)
                }
                """, "1\n2\n1\n6\n6\n5", tempDir, "b2");
    }

    // B3 — records inside typed lists keep their type through for-in
    @Test
    void recordsInListsKeepType(@TempDir Path tempDir) throws IOException {
        runBoth("""
                record Token(String kind, String text)

                main() {
                    var tokens = listOf(
                        Token("identifier", "hello"),
                        Token("string", "world")
                    )
                    for (var token in tokens) {
                        println(token.kind())
                        println(token.text())
                    }
                }
                """, "identifier\nhello\nstring\nworld", tempDir, "b3");
    }

    // B4 — empty listOf() typed later by usage
    @Test
    void emptyListOfLaterTyped(@TempDir Path tempDir) throws IOException {
        runBoth("""
                record Token(String kind, String text)

                main() {
                    var tokens: List<Token> = listOf()
                    tokens.add(Token("identifier", "hello"))
                    println(tokens.get(0).kind())
                }
                """, "identifier", tempDir, "b4");
    }

    // B9 — generics propagate through json.decode and get
    @Test
    void genericsThroughDecodeAndGet(@TempDir Path tempDir) throws IOException {
        runBoth("""
                record User(String name, Int age)

                main() {
                    var raw = "[{\\"name\\":\\"Mel\\",\\"age\\":26}]"
                    var users = json.decode<List<User>>(raw)
                    println(users.get(0).name)
                    var l = json.decode<List<Int>>("[10, 20]")
                    println(l.get(1))
                    var s = json.decode<List<String>>("[\\"a\\",\\"b\\"]")
                    println(s.get(0).length)
                }
                """, "Mel\n20\n1", tempDir, "b9");
    }

    // implicit construction without `new` + retrocompat with `new`
    @Test
    void implicitConstructionAndNew(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class User(String name, Int age) {
                    greeting(): String {
                        return name + ":" + age
                    }
                }

                main() {
                    var a = User("Mel", 26)
                    var b = new User("Kof", 30)
                    println(a.greeting())
                    println(b.greeting())
                }
                """, "Mel:26\nKof:30", tempDir, "ctor");
    }

    // user class named Color must win over the KofUi builtin helper
    @Test
    void userClassPrecedenceOverBuiltin(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Color {
                    Int value
                    constructor(Int value) {
                        this.value = value
                    }
                    Int red() { return (this.value >> 16) & 0xFF }
                }

                main() {
                    var c = Color(0xFF6750A4)
                    println(c.red())
                }
                """, "103", tempDir, "color-class");
    }

    // B5 — bare return in void functions
    @Test
    void bareReturn(@TempDir Path tempDir) throws IOException {
        runBoth("""
                void maybe(Bool condition) {
                    if (condition) {
                        return
                    }
                    println("not-returned")
                }

                main() {
                    maybe(true)
                    maybe(false)
                }
                """, "not-returned", tempDir, "b5");
    }

    // B7 — field initializers on JVM and JS
    @Test
    void fieldInitializers(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Theme {
                    Int primary = 0xFF282A36
                    String name = "Dracula"
                }

                main() {
                    var t = Theme()
                    println(t.name)
                    println(t.primary == 0xFF282A36)
                    var t2 = new Theme()
                    println(t2.name)
                }
                """, "Dracula\ntrue\nDracula", tempDir, "b7");
    }

    // user classes extendable with explicit constructors still work
    @Test
    void explicitConstructorStillWorks(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Counter {
                    Int value = 0
                    constructor(Int start) {
                        this.value = start
                    }
                    inc() {
                        value = value + 1
                    }
                    get(): Int {
                        return value
                    }
                }

                main() {
                    var c = Counter(10)
                    c.inc()
                    println(c.get())
                }
                """, "11", tempDir, "explicit-ctor");
    }
}