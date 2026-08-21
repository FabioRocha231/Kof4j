package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;

class NativeDebugTest {
    @Test
    void debugNativeCompilation(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, "fun main() { println(\"Hello, Kof!\") }");
        Path outDir = tempDir.resolve("out");
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        System.out.println("Success: " + result.success());
        System.out.println("Diagnostics:");
        for (var d : result.diagnostics().getDiagnostics()) {
            System.out.println("  " + d);
        }
        Files.walk(outDir).forEach(p -> {
            try { if (Files.isRegularFile(p)) System.out.println("  FILE: " + p); } catch (Exception e) {}
        });
    }
}
