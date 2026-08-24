package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.Disabled;


/**
 * Fase 5 — kof.db: JDBC por interoperabilidade JVM, API idiomática Kof
 * (db.connect/execute/query/query&lt;T&gt;/close + transaction { }).
 *
 * Os testes usam H2 em memória (dependência test-scope); o subprocesso roda
 * com o jar do H2 no classpath.
 */
class KofDbE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    private String runJvm(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        String h2 = findH2Jar();
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir + ":" + h2, "Default.Main");
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
    void executeAndQueryRowsAsJson(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("jdbc:h2:mem:test1;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table users(id int, name varchar(50))")
                db.execute(db, "insert into users values (?, ?)", 1, "Mel")
                db.execute(db, "insert into users values (?, ?)", 2, "Kof")
                var rows = db.query(db, "select * from users order by id")
                println(rows.size)
                println(rows.get(0))
                println(rows.get(1))
            }
            """);
        runJvm(source, tempDir.resolve("out"),
                "2\n{\"id\":1,\"name\":\"Mel\"}\n{\"id\":2,\"name\":\"Kof\"}");
    }

    @Test
    void typedQueryBindsRecord(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record User(Int id, String name)

            main() {
                var db = db.connect("jdbc:h2:mem:test2;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table users(id int, name varchar(50))")
                db.execute(db, "insert into users values (?, ?)", 7, "Ada")
                var users = db.query<User>(db, "select * from users where id = ?", 7)
                println(users.size)
                println(users.get(0).id)
                println(users.get(0).name)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "1\n7\nAda");
    }

    @Test
    void typedQueryAllRows(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record User(Int id, String name)

            main() {
                var db = db.connect("jdbc:h2:mem:test3;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table users(id int, name varchar(50))")
                db.execute(db, "insert into users values (1, 'A')")
                db.execute(db, "insert into users values (2, 'B')")
                var users = db.query<User>(db, "select * from users order by id")
                var total = 0
                for (var u in users) {
                    total = total + u.id
                }
                println(total)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "3");
    }

    @Test
    void transactionCommits(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("jdbc:h2:mem:test4;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table t(x int)")
                transaction {
                    db.execute(db, "insert into t values (1)")
                    db.execute(db, "insert into t values (2)")
                }
                var rows = db.query(db, "select count(*) as n from t")
                println(rows.get(0))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "{\"n\":2}");
    }

    @Test
    void transactionRollsBackOnFailure(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("jdbc:h2:mem:test5;DB_CLOSE_DELAY=-1")
                db.execute(db, "create table t(x int)")
                try {
                    transaction {
                        db.execute(db, "insert into t values (1)")
                        throw "boom"
                    }
                } catch (String e) {
                    println("caught")
                }
                var rows = db.query(db, "select count(*) as n from t")
                println(rows.get(0))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "caught\n{\"n\":0}");
    }

    @Test
    void connectWithCredentials(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("jdbc:h2:mem:test6;DB_CLOSE_DELAY=-1", "sa", "")
                db.execute(db, "create table t(x int)")
                db.execute(db, "insert into t values (42)")
                var rows = db.query(db, "select x from t")
                println(rows.get(0))
                db.close(db)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "{\"x\":42}");
    }

    @Disabled("WIP: regressão do query nativo sob investigação (alinhamento/alloc)")
    @Test
    void nativeSqliteRoundtrip(@TempDir Path tempDir) throws IOException {
        assumeTrue(isLinux(), "Native SQLite requires Linux + libsqlite3");
        Path source = tempDir.resolve("Native.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("sqlite:%s/kof.db")
                db.execute(db, "create table if not exists u(id int, name varchar)")
                db.execute(db, "insert into u values (?, ?)", 7, "Nativa")
                var rows = db.query(db, "select id, name from u where id = ?", 7)
                for (var r in rows) {
                    println(r)
                }
                db.close(db)
            }
            """.formatted(tempDir));
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Native compile should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = tempDir.resolve("out/Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals("{\"id\":7,\"name\":\"Nativa\"}", output, "Native SQLite query output");
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    @Test
    void nativeSupportsSqliteAndJsReportsDb001(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var db = db.connect("sqlite:/tmp/kof-db-test.db")
            }
            """);
        // Native: kof.db agora compila — SQLite via link direto de
        // libsqlite3 (sem JDBC driver); URLs não-sqlite falham em runtime.
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(nativeResult.success(),
                nativeResult.diagnostics().getDiagnostics().toString());

        Path jsSource = tempDir.resolve("MainJs.kf");
        Files.writeString(jsSource, """
            main() {
                var db = db.connect("sqlite:/tmp/x.db")
            }
            """);
        CompilationResult jsResult = driver.compile(jsSource, tempDir.resolve("js-out"), Target.JS);
        assertFalse(jsResult.success());
        assertTrue(jsResult.diagnostics().getDiagnostics().toString().contains("DB001"),
                jsResult.diagnostics().getDiagnostics().toString());
    }
}