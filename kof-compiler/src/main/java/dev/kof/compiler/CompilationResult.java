package dev.kof.compiler;

import java.nio.file.Path;

public record CompilationResult(boolean success, DiagnosticCollector diagnostics, Path outputDir) {
}
