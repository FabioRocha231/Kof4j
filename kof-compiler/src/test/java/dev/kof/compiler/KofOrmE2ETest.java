package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof.orm — o ORM da própria linguagem: {@code entity} define o schema em
 * compile-time (o compilador conhece campos, tipos e constraints — nunca
 * reflection para descobrir schema) e {@code orm} fala SQL via kof.db.
 *
 * Fluxo: entity → record gerado + schema registrado; orm.create/save/find/
 * all/delete/count sobre JDBC (H2 nos testes).
 */
class KofOrmE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String ENTITY_SRC = """
            entity User {
                id: Long generated
                name: String
                email: String unique
                age: Int
            }
            """;

    private String runJvm(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        String h2 = findH2Jar();
        try {
            java.nio.file.Files.walk(outDir).filter(Files::isRegularFile)
                    .forEach(f -> { try {
                        java.nio.file.Files.copy(f, Path.of("/tmp/orm-keeper").resolve(outDir.getFileName()).resolve(outDir.relativize(f).toString()),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception ignored) {} });
            ProcessBuilder pb = new ProcessBuilder("java", "-Dfile.encoding=UTF-8",
                    "-Dstdout.encoding=UTF-8", "-cp", outDir + java.io.File.pathSeparator + h2, "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    private static String findH2Jar() {
        String cp = System.getProperty("java.class.path");
        for (String entry : cp.split(java.io.File.pathSeparator)) {
            if (entry.contains("h2") && entry.endsWith(".jar")) return entry;
        }
        throw new IllegalStateException("H2 jar not found on test classpath");
    }

    @Test
    void createSaveFindAllDelete(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:orm1;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    var mel = orm.save(db, User(0, "Mel", "mel@kof.dev", 30))
                    println(mel.id)
                    var u = orm.find<User>(db, mel.id)
                    println(u.name)
                    var all = orm.all<User>(db)
                    println(all.size)
                    println(orm.count<User>(db))
                    orm.delete<User>(db, mel.id)
                    println(orm.count<User>(db))
                    db.close(db)
                }
                """);
        runJvm(source, tempDir.resolve("out"), "1\nMel\n1\n1\n0");
    }

    @Test
    void saveUpdatesExistingRow(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:orm2;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    var mel = orm.save(db, User(0, "Mel", "mel@kof.dev", 30))
                    mel = orm.save(db, User(mel.id, "Melissa", "mel@kof.dev", 31))
                    var u = orm.find<User>(db, mel.id)
                    println(u.name + " " + u.age)
                    db.close(db)
                }
                """);
        runJvm(source, tempDir.resolve("out"), "Melissa 31");
    }

    @Test
    void uniqueConstraintRejected(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:h2:mem:orm3;DB_CLOSE_DELAY=-1")
                    orm.create<User>(db)
                    orm.save(db, User(0, "Mel", "same@kof.dev", 30))
                    try {
                        orm.save(db, User(0, "Kof", "same@kof.dev", 1))
                        println("no-error")
                    } catch (Throwable e) {
                        println("rejected")
                    }
                    db.close(db)
                }
                """);
        runJvm(source, tempDir.resolve("out"), "rejected");
    }

    @Test
    void entityWithoutGeneratedUsesFirstFieldAsPk(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                entity Product {
                    code: String unique
                    price: Double
                }
                main() {
                    var db = db.connect("jdbc:h2:mem:orm4;DB_CLOSE_DELAY=-1")
                    orm.create<Product>(db)
                    orm.save(db, Product("P1", 19.99))
                    var p = orm.find<Product>(db, "P1")
                    println(p.price)
                    db.close(db)
                }
                """);
        runJvm(source, tempDir.resolve("out"), "19.99");
    }

    @Test
    void whereFiltersByField(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:orm6;DB_CLOSE_DELAY=-1\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    orm.save(db, User(0, \"Mel\", \"mel@kof.dev\", 30))\n"
                + "                    orm.save(db, User(0, \"Ana\", \"ana@kof.dev\", 25))\n"
                + "                    var adultos = orm.where<User>(db, \"age\", 30)\n"
                + "                    println(adultos.size)\n"
                + "                    println(adultos.get(0).name)\n"
                + "                    db.close(db)\n"
                + "                }\n"
                + "                ");
        runJvm(source, tempDir.resolve("out"), "1\nMel");
    }

    @Test
    void migrateAppliesOnce(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:orm7;DB_CLOSE_DELAY=-1\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    orm.migrate(db, \"add_city\", \"ALTER TABLE \\\"user\\\" ADD COLUMN city VARCHAR(255)\")\n"
                + "                    orm.migrate(db, \"add_city\", \"ALTER TABLE \\\"user\\\" ADD COLUMN city VARCHAR(255)\")\n"
                + "                    orm.migrate(db, \"v2\", \"ALTER TABLE \\\"user\\\" ADD COLUMN country VARCHAR(255)\")\n"
                + "                    println(\"ok\")\n"
                + "                    db.close(db)\n"
                + "                }\n"
                + "                ");
        runJvm(source, tempDir.resolve("out"), "ok");
    }

    @Test
    void nativeAndJsReportOrm001(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("sqlite:/tmp/orm-test.db")
                    orm.create<User>(db)
                }
                """);
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("native-out"), Target.NATIVE);
        assertFalse(nativeResult.success());
        assertTrue(nativeResult.diagnostics().getDiagnostics().toString().contains("ORM001"),
                "Native should report ORM001: " + nativeResult.diagnostics().getDiagnostics());

        CompilationResult jsResult = driver.compile(source, tempDir.resolve("js-out"), Target.JS);
        assertFalse(jsResult.success());
        assertTrue(jsResult.diagnostics().getDiagnostics().toString().contains("ORM001"),
                "JS should report ORM001: " + jsResult.diagnostics().getDiagnostics());
    }

    @Test
    void unknownEntityReportsOrm002(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var db = db.connect("jdbc:h2:mem:orm5;DB_CLOSE_DELAY=-1")
                    orm.create<Ghost>(db)
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success());
        assertTrue(result.diagnostics().getDiagnostics().toString().contains("ORM002"),
                "Should report ORM002: " + result.diagnostics().getDiagnostics());
    }
}