package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class IRStatisticsTest {

    @Test
    void reportsOptimizationReduction(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var x = 2 + 3
                var y = x * 4
                if (false) {
                    println("dead")
                }
                println(x + y)
            }
            """);
        final IRStatistics[] stats = new IRStatistics[1];
        CompilerDriver driver = new CompilerDriver();
        driver.setIRObserver(s -> stats[0] = s);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success());
        assertNotNull(stats[0], "observer should have been invoked");
        assertTrue(stats[0].opsAfter() < stats[0].opsBefore(), "optimizer should remove ops");
        assertTrue(stats[0].opsRemoved() > 0);
        assertTrue(stats[0].reductionPct() > 0);
        assertEquals(1, stats[0].classes());
        assertTrue(stats[0].methods().stream()
                .anyMatch(m -> "main".equals(m.methodName()) && m.opsAfter() < m.opsBefore()));
    }

    @Test
    void observerInvokedEvenWithOptimizationDisabled(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, "main() { println(\"ok\") }");
        final IRStatistics[] stats = new IRStatistics[1];
        CompilerDriver driver = new CompilerDriver();
        driver.setOptimizationEnabled(false);
        driver.setIRObserver(s -> stats[0] = s);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(result.success());
        assertNotNull(stats[0]);
        assertEquals(stats[0].opsBefore(), stats[0].opsAfter(),
                "no optimization enabled: before == after");
    }
}