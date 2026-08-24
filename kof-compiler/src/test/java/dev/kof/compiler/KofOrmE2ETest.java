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
        return runJvmWithExtra(source, outDir, null, expected);
    }

    private String runJvmWithExtra(Path source, Path outDir, String extraJar, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        String h2 = findH2Jar();
        String cp = outDir + java.io.File.pathSeparator + h2
                + (extraJar != null ? java.io.File.pathSeparator + extraJar : "");
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-Dfile.encoding=UTF-8",
                    "-Dstdout.encoding=UTF-8", "--enable-native-access=ALL-UNNAMED",
                    "-cp", cp, "Default.Main");
            // stderr separado: drivers (Mongo/SQLite) podem logar avisos de
            // inicialização no stderr — o stdout é o output do programa Kof
            pb.redirectError(java.io.File.createTempFile("kof-orm-stderr", ".txt"));
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
        return findDriverJar("h2", "H2");
    }

    private static String findDriverJar(String marker, String label) {
        String cp = System.getProperty("java.class.path");
        for (String entry : cp.split(java.io.File.pathSeparator)) {
            if (entry.contains(marker) && entry.endsWith(".jar")) return entry;
        }
        throw new IllegalStateException(label + " jar not found on test classpath");
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
    void whereWithOperator(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:orm8;DB_CLOSE_DELAY=-1\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    orm.save(db, User(0, \"Mel\", \"mel@kof.dev\", 30))\n"
                + "                    orm.save(db, User(0, \"Ana\", \"ana@kof.dev\", 25))\n"
                + "                    orm.save(db, User(0, \"Leo\", \"leo@kof.dev\", 40))\n"
                + "                    var adultos = orm.where<User>(db, \"age\", \">\", 25)\n"
                + "                    println(adultos.size)\n"
                + "                    var jovens = orm.where<User>(db, \"age\", \"<=\", 25)\n"
                + "                    println(jovens.size)\n"
                + "                    var ana = orm.where<User>(db, \"name\", \"LIKE\", \"A%\")\n"
                + "                    println(ana.size)\n"
                + "                    println(ana.get(0).name)\n"
                + "                    db.close(db)\n"
                + "                }\n"
                + "                ");
        runJvm(source, tempDir.resolve("out"), "2\n1\n1\nAna");
    }

    @Test
    void saveAllBatch(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:orm9;DB_CLOSE_DELAY=-1\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    var l = new List<User>()\n"
                + "                    l.add(User(0, \"Mel\", \"mel@kof.dev\", 30))\n"
                + "                    l.add(User(0, \"Ana\", \"ana@kof.dev\", 25))\n"
                + "                    orm.saveAll<User>(db, l)\n"
                + "                    println(orm.count<User>(db))\n"
                + "                    var mel = orm.where<User>(db, \"email\", \"mel@kof.dev\")\n"
                + "                    println(mel.size)\n"
                + "                    db.close(db)\n"
                + "                }\n"
                + "                ");
        runJvm(source, tempDir.resolve("out"), "2\n1");
    }

    @Test
    void pagePagination(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:orm10;DB_CLOSE_DELAY=-1\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    var l = new List<User>()\n"
                + "                    l.add(User(0, \"A\", \"a@kof.dev\", 20))\n"
                + "                    l.add(User(0, \"B\", \"b@kof.dev\", 21))\n"
                + "                    l.add(User(0, \"C\", \"c@kof.dev\", 22))\n"
                + "                    orm.saveAll<User>(db, l)\n"
                + "                    var p1 = orm.page<User>(db, 2, 0)\n"
                + "                    println(p1.size)\n"
                + "                    var p2 = orm.page<User>(db, 2, 2)\n"
                + "                    println(p2.size)\n"
                + "                    println(p2.get(0).name)\n"
                + "                    db.close(db)\n"
                + "                }\n"
                + "                ");
        runJvm(source, tempDir.resolve("out"), "2\n1\nC");
    }

    @Test
    void countWhereAndDeleteAll(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + "\n"
                + "                main() {\n"
                + "                    var db = db.connect(\"jdbc:h2:mem:orm11;DB_CLOSE_DELAY=-1\")\n"
                + "                    orm.create<User>(db)\n"
                + "                    orm.save(db, User(0, \"Mel\", \"mel@kof.dev\", 30))\n"
                + "                    orm.save(db, User(0, \"Ana\", \"ana@kof.dev\", 25))\n"
                + "                    println(orm.count<User>(db, \"age\", 30))\n"
                + "                    println(orm.count<User>(db))\n"
                + "                    orm.deleteAll<User>(db)\n"
                + "                    println(orm.count<User>(db))\n"
                + "                    db.close(db)\n"
                + "                }\n"
                + "                ");
        runJvm(source, tempDir.resolve("out"), "1\n2\n0");
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
    void sqliteDialectViaJdbc(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, ENTITY_SRC + """
                main() {
                    var db = db.connect("jdbc:sqlite:%s")
                    orm.create<User>(db)
                    var mel = orm.save(db, User(0, "Mel", "mel@kof.dev", 30))
                    var u = orm.find<User>(db, mel.id)
                    println(u.name)
                    println(orm.count<User>(db))
                    db.close(db)
                }
                """.formatted(tempDir.resolve("orm.db")));
        runJvmWithExtra(source, tempDir.resolve("out"), findDriverJar("sqlite-jdbc", "SQLite"),
                "Mel\n1");
    }

    @Test
    void mongoCrud(@TempDir Path tempDir) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(mongoAvailable(),
                "MongoDB not reachable on localhost:27017 (start it: docker run -d -p 27017:27017 mongo:7)");
        int port = 27017;
        {
            Path source = tempDir.resolve("Main.kf");
            Files.writeString(source, """
                entity User {
                    id: Long
                    name: String
                    email: String unique
                    age: Int
                }
                main() {
                    var db = db.connect("mongodb://localhost:%d/kof_test_%d")
                    orm.create<User>(db)
                    orm.save(db, User(1, "Mel", "mel@kof.dev", 30))
                    var u = orm.find<User>(db, 1)
                    println(u.name)
                    var adultos = orm.where<User>(db, "age", 30)
                    println(adultos.size)
                    println(orm.count<User>(db))
                    var l = new List<User>()
                    l.add(User(2, "Ana", "ana@kof.dev", 25))
                    l.add(User(3, "Leo", "leo@kof.dev", 40))
                    orm.saveAll<User>(db, l)
                    println(orm.count<User>(db))
                    var jovens = orm.where<User>(db, "age", "<=", 25)
                    println(jovens.size)
                    var pg = orm.page<User>(db, 2, 1)
                    println(pg.size)
                    var leo = orm.where<User>(db, "name", "LIKE", "L%%")
                    println(leo.size)
                    println(orm.count<User>(db, "age", 25))
                    orm.deleteAll<User>(db)
                    println(orm.count<User>(db))
                    db.close(db)
                }
                """.formatted(port, System.nanoTime()));
            runJvmWithExtra(source, tempDir.resolve("out"), mongoClasspath(), "Mel\n1\n1\n3\n1\n2\n1\n1\n0");
        }
    }

    private static boolean mongoAvailable() {
        try (java.net.Socket s = new java.net.Socket("localhost", 27017)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String mongoClasspath() {
        StringBuilder cp = new StringBuilder();
        String classpath = System.getProperty("java.class.path");
        for (String entry : classpath.split(java.io.File.pathSeparator)) {
            if ((entry.contains("mongodb") || entry.contains("bson") || entry.contains("slf4j"))
                    && entry.endsWith(".jar")) {
                if (cp.length() > 0) cp.append(java.io.File.pathSeparator);
                cp.append(entry);
            }
        }
        return cp.toString();
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