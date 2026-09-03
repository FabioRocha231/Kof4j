package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof deps — package manager MVP (TIER 1.4): gerencia o arquivo kofdeps e
 * resolve para ~/.kof/deps. Testes locais (sem rede): init/add/remove/list e
 * resolução de um jar já presente no cache.
 */
class DepsTest {

    private static String[] withDir(String dir, String... extra) {
        String[] out = new String[extra.length + 2];
        out[0] = "deps";
        System.arraycopy(extra, 0, out, 1, extra.length);
        out[out.length - 1] = dir;
        return out;
    }

    @Test
    void initAddListRemove(@TempDir Path dir) throws Exception {
        assertEquals(0, Deps.run(withDir(dir.toString(), "init")));
        Path file = dir.resolve(Deps.DEPS_FILE);
        assertTrue(Files.exists(file), "kofdeps criado");

        assertEquals(0, Deps.run(withDir(dir.toString(), "add", "com.example:lib:1.0")));
        assertEquals(0, Deps.run(withDir(dir.toString(), "add", "com.example:other:2.0")));
        // duplicata é inofensiva
        assertEquals(0, Deps.run(withDir(dir.toString(), "add", "com.example:lib:1.0")));

        String listed = captureList(dir);
        assertTrue(listed.contains("com.example:lib:1.0"), listed);
        assertTrue(listed.contains("com.example:other:2.0"), listed);

        assertEquals(0, Deps.run(withDir(dir.toString(), "remove", "com.example:lib:1.0")));
        listed = captureList(dir);
        assertFalse(listed.contains("com.example:lib:1.0"), listed);
        assertTrue(listed.contains("com.example:other:2.0"), listed);
    }

    @Test
    void invalidFormatRejected(@TempDir Path dir) throws Exception {
        Deps.run(withDir(dir.toString(), "init"));
        assertNotEquals(0, Deps.run(withDir(dir.toString(), "add", "not-a-maven-dep")));
        // nada foi escrito
        assertEquals("", Files.readString(dir.resolve(Deps.DEPS_FILE)).trim());
    }

    @Test
    void classpathIncludesResolvedJar(@TempDir Path dir) throws Exception {
        Deps.run(withDir(dir.toString(), "init"));
        Deps.run(withDir(dir.toString(), "add", "com.example:lib:1.0"));

        // jar "já baixado" no cache (sem rede): resolve deve achar e o
        // classpath incluir o caminho
        Path jar = Path.of(System.getProperty("user.home"), ".kof", "deps",
                "com", "example", "lib", "1.0", "lib-1.0.jar");
        Files.createDirectories(jar.getParent());
        Files.write(jar, new byte[]{1, 2, 3});
        try {
            String cp = Deps.classpath(dir);
            assertTrue(cp.contains("lib-1.0.jar"), "classpath: " + cp);
        } finally {
            Files.deleteIfExists(jar);
        }
    }

    @Test
    void normalizeValidatesGav() {
        assertEquals("g:a:v", Deps.normalize("g:a:v"));
        assertEquals("com.h2database:h2:2.2.224", Deps.normalize("com.h2database:h2:2.2.224"));
        assertNull(Deps.normalize("g:a"));
        assertNull(Deps.normalize("g:a:v:x"));
        assertNull(Deps.normalize(""));
    }

    private static String captureList(Path dir) {
        // captura stdout de Deps.run("list") — redireciona System.out
        java.io.PrintStream original = System.out;
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        try {
            System.setOut(new java.io.PrintStream(buf));
            Deps.run(withDir(dir.toString(), "list"));
        } finally {
            System.setOut(original);
        }
        return buf.toString(java.nio.charset.StandardCharsets.UTF_8);
    }
}