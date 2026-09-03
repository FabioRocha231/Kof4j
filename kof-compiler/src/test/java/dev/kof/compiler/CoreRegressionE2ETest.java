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
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
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

    // B8 — default parameters (compile-time lowering, no runtime machinery)
    @Test
    void defaultParameters(@TempDir Path tempDir) throws IOException {
        runBoth("""
                greet(String name = "world") {
                    println("hello " + name)
                }

                Int add(Int a, Int b = 10) {
                    return a + b
                }

                main() {
                    greet("Mel")
                    greet()
                    println(add(5))
                    println(add(5, 7))
                }
                """, "hello Mel\nhello world\n15\n12", tempDir, "b8");
    }

    // F2 — main(args: List<String>) receives the program arguments (JVM)
    @Test
    void mainArgsList(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, """
                main(args: List<String>) {
                    println(args.size)
                    println(args.get(0))
                }
                """);
        Path outJvm = tempDir.resolve("out");
        CompilationResult rjvm = driver.compile(src, outJvm, Target.JVM);
        assertTrue(rjvm.success(), "JVM compile failed: " + rjvm.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outJvm.toString(),
                    "Default.Main", "arquivo.txt", "segundo");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
            assertEquals("2\narquivo.txt", output);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    // F4 — process.run abstracts the OS process on every backend
    @Test
    void processRun(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var p = process.run("echo", "hello", "world")
                    println(p.stdout.trim())
                    println(p.exitCode)
                }
                """, "hello world\n0", tempDir, "f4-process");
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

    // known-bugs #10 — `!` (logical NOT) as an expression VALUE must negate
    // (constant folding used bitwise `~` → `!true` was true, JVM+Native+JS)
    @Test
    void logicalNotAsExpressionValue(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var a = !true
                    var b = !false
                    var c = !(1 > 2)
                    println(a)
                    println(b)
                    println(c)
                    println(!(2 > 3))
                    println(!false && true)
                }
                """, "false\ntrue\ntrue\ntrue\ntrue", tempDir, "logical-not");
    }

    // known-bugs #2/#3 — compound assignment operand order: `a -= 2` must be
    // `a - 2` (was `2 - a` → -8); `s += "x"` in a loop must not crash the
    // compiler (old path pushed the RHS twice → stack imbalance at frame merge)
    @Test
    void compoundAssignmentOrderAndStringInLoop(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var a = 10; a -= 2; println(a)
                    var b = 10; b /= 2; println(b)
                    var c = 10; c %= 3; println(c)
                    var d = 10; d *= 3; println(d)
                    var e = 10; e += 5; println(e)
                    var s = ""
                    var i = 0
                    while (i < 10) { s += "x"; i = i + 1 }
                    println(s.length)
                    var acc = 0
                    for (var j = 0; j < 5; j++) { acc += j }
                    println(acc)
                }
                """, "8\n5\n1\n30\n15\n10\n10", tempDir, "compound-order");
    }

    // known-bugs #5/#24 — FP→Int/Long casts and Double→Float narrowing were
    // missing conversion ops → invalid bytecode (ClassFormatError). Now D2I/
    // F2I/D2L/F2L (truncate toward zero) and D2F are emitted.
    @Test
    void fpToIntAndDoubleToFloatConversions(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var d = 3.9
                    println(d as Int)
                    println(d as Long)
                    var f = 2.7f
                    println(f as Int)
                    println(-3.9 as Int)
                    var g: Float = 3.4
                    println(g)
                    var h = d as Float
                    println(h)
                }
                """, "3\n3\n2\n-3\n3.4\n3.9", tempDir, "fp-casts");
    }
}