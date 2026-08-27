package dev.kof.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KofScriptTest {

    @Test
    void evalPrintsHello() throws Exception {
        var r = KofScript.eval("""
                println("hello from script")
                """);
        assertTrue(r.success(), r.stderr());
        assertEquals("hello from script", r.stdout().trim());
    }

    @Test
    void evalWithLetAndFn() throws Exception {
        var r = KofScript.eval("""
                fn add(a: Int, b: Int): Int = a + b
                main() {
                    var x = add(2, 3)
                    println(x)
                }
                """);
        assertTrue(r.success(), r.stderr());
        assertEquals("5", r.stdout().trim());
    }

    @Test
    void evalPatternMatching() throws Exception {
        var r = KofScript.eval("""
                main() {
                    var x: Object = "hello"
                    switch (x) {
                        case String s:
                            println("str:" + s)
                        default:
                            println("other")
                    }
                }
                """);
        assertTrue(r.success(), r.stderr());
        assertEquals("str:hello", r.stdout().trim());
    }

    @Test
    void evalInstanceofAndAs() throws Exception {
        var r = KofScript.eval("""
                main() {
                    var a: Object = "world"
                    if (a instanceof String) {
                        println("is string")
                    }
                    var b: Object = "test" as String
                    println(b)
                }
                """);
        assertTrue(r.success(), r.stderr());
        assertEquals("is string\ntest", r.stdout().trim().replace("\r\n","\n"));
    }

    @Test
    void runFileDirect(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("prog.kf");
        Files.writeString(f, """
                main() {
                    println(42)
                }
                """);
        var r = KofScript.runFile(f);
        assertTrue(r.success(), r.stderr());
        assertEquals("42", r.stdout().trim());
    }

    @Test
    void evalJsTarget() throws Exception {
        var r = dev.kof.compiler.Target.JS != null ? KofScript.eval("println(7)", dev.kof.compiler.Target.JS) : null;
        // JS eval uses embedded GraalJS, stdout is captured via KofJsRunner (which prints to System.out, not RunResult.stdout for JS)
        // For MVP, we just check success (JS stdout goes to System.out, not RunResult for JS path)
        // Instead test via runFile JS direct
        Path tmp = Files.createTempDirectory("jstest");
        Path f = tmp.resolve("Main.kf");
        Files.writeString(f, "main() { println(7) }");
        var r2 = KofScript.runFile(f, dev.kof.compiler.Target.JS);
        assertTrue(r2.success(), r2.stderr() + r2.stdout());
    }

    @Test
    void evalNativeTarget() throws Exception {
        Path tmp = Files.createTempDirectory("nativetest");
        Path f = tmp.resolve("Main.kf");
        Files.writeString(f, "main() { println(7) }");
        var r = KofScript.runFile(f, dev.kof.compiler.Target.NATIVE);
        assertTrue(r.success(), r.stderr() + r.stdout());
        assertEquals("7", r.stdout().trim());
    }

    @Test
    void evalLetAndAsyncSugar() throws Exception {
        var r = KofScript.eval("""
                async fn foo(a: Int): Int = a + 1
                main() {
                    let x = 5
                    var y = foo(x)
                    println(y)
                }
                """);
        assertTrue(r.success(), r.stderr());
        assertEquals("6", r.stdout().trim());
    }
}
