package dev.kof.compiler;

public enum Target {
    JVM,
    NATIVE,
    NATIVE_RISCV64,
    NATIVE_AARCH64,
    JS,
    ANDROID;

    public boolean isNative() {
        return this == NATIVE || this == NATIVE_RISCV64 || this == NATIVE_AARCH64;
    }

    public String nativeArch() {
        return switch (this) {
            case NATIVE -> "x86_64";
            case NATIVE_RISCV64 -> "riscv64";
            case NATIVE_AARCH64 -> "aarch64";
            default -> "unknown";
        };
    }
}
