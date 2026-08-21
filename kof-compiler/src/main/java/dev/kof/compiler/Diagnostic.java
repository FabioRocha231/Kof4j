package dev.kof.compiler;

import java.util.Locale;

public record Diagnostic(Severity severity, String file, int line, int column, int length, String message, String code) {

    public enum Severity { ERROR, WARNING, NOTE, INFO }

    static Diagnostic error(String file, int line, int column, int length, String message, String code) {
        return new Diagnostic(Severity.ERROR, file, line, column, length, message, code);
    }

    static Diagnostic warning(String file, int line, int column, int length, String message, String code) {
        return new Diagnostic(Severity.WARNING, file, line, column, length, message, code);
    }

    static Diagnostic note(String file, int line, int column, int length, String message, String code) {
        return new Diagnostic(Severity.NOTE, file, line, column, length, message, code);
    }

    static Diagnostic info(String file, int line, int column, int length, String message, String code) {
        return new Diagnostic(Severity.INFO, file, line, column, length, message, code);
    }

    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append(file).append(':').append(line).append(':').append(column).append(": ");
        sb.append(severity.name().toLowerCase(Locale.ROOT)).append(": ").append(message);
        if (code != null && !code.isEmpty()) {
            sb.append(" [").append(code).append(']');
        }
        return sb.toString();
    }
}
