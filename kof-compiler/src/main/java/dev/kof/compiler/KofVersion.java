package dev.kof.compiler;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


public final class KofVersion {

    public static final int TOOLING_API = 21;

    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = KofVersion.class.getResourceAsStream("/dev/kof/version.properties")) {
            if (in != null) {
                PROPS.load(in);
            }
        } catch (IOException e) {

        }
    }

    private KofVersion() {}

    public static String version() {
        return prop("kof.version", "unknown");
    }

    public static String compiler() {
        return prop("kof.compiler.version", version());
    }

    public static String runtime() {
        return prop("kof.runtime.version", version());
    }

    public static String stdlib() {
        return prop("kof.stdlib.version", version());
    }

    public static int toolingApi() {
        try {
            return Integer.parseInt(prop("kof.tooling.api", "21"));
        } catch (NumberFormatException e) {
            return TOOLING_API;
        }
    }

    public static String os() {
        String os = System.getProperty("os.name", "unknown").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac") || os.contains("darwin")) return "macos";
        if (os.contains("linux")) return "linux";
        return os.replaceAll("[^a-z0-9]", "");
    }

    public static String arch() {
        String arch = System.getProperty("os.arch", "unknown").toLowerCase();
        return switch (arch) {
            case "amd64", "x86_64" -> "x86_64";
            case "aarch64", "arm64" -> "arm64";
            default -> arch.replaceAll("[^a-z0-9]", "");
        };
    }

    public static String target() {
        return os() + "-" + arch();
    }

    private static String prop(String key, String fallback) {
        String v = PROPS.getProperty(key);
        return v == null || v.isBlank() ? fallback : v;
    }
}