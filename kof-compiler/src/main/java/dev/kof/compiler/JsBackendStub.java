package dev.kof.compiler;

import java.io.IOException;
import java.nio.file.Path;

/**
 * JsBackendStub — placeholder for the KofJS backend (in progress).
 *
 * The KofJS pipeline (Kof IR → JsIr → JsEmitter) is being implemented
 * separately; until it lands, the JS target reports its status instead of
 * emitting output.
 */
final class JsBackendStub implements Backend {

    @Override
    public void emit(IRModule module, Path outputDir) throws IOException {
        throw new IOException("KofJS backend is in progress (JsIr/JsEmitter pipeline not yet wired)");
    }
}