package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;

class NativeDebugTest2 {
    @Test
    void debugNativeCompilation(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, "main() { println(\"Hello, Kof!\") }");
        Path outDir = tempDir.resolve("out");
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        System.out.println("Success: " + result.success());
        System.out.println("Diagnostics:");
        for (var d : result.diagnostics().getDiagnostics()) {
            System.out.println("  " + d);
        }
        // Check if files exist
        Files.walk(outDir).forEach(p -> {
            try { if (Files.isRegularFile(p)) System.out.println("  FILE: " + p + " (" + Files.size(p) + " bytes)"); } catch (Exception e) {}
        });
    }
}
