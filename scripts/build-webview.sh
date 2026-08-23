#!/usr/bin/env bash
#
# build-webview.sh — compiles the native kof-webview shell (Linux).
#
# Requires: gcc, pkg-config, libwebkit2gtk-4.1-dev (or -4.0) and GTK3.
# Output: bin/kof-webview next to the launcher.
#
# When the dependencies are missing the script prints a notice and exits 0
# (the distribution still works — kof run --target=js falls back to the
# system webview). CI installs the deps and builds the real binary.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/native/webview/kof-webview.c"
OUT="$ROOT/bin/kof-webview"

if ! command -v gcc >/dev/null 2>&1 || ! command -v pkg-config >/dev/null 2>&1; then
    echo "build-webview: gcc/pkg-config not found — skipping (system webview fallback)"
    exit 0
fi

if pkg-config --exists webkit2gtk-4.1; then
    PKG=webkit2gtk-4.1
elif pkg-config --exists webkit2gtk-4.0; then
    PKG=webkit2gtk-4.0
else
    echo "build-webview: libwebkit2gtk not found — skipping (system webview fallback)"
    exit 0
fi

echo "build-webview: compiling with $PKG"
gcc -O2 -o "$OUT" "$SRC" $(pkg-config --cflags --libs "$PKG" gtk+-3.0)
echo "build-webview: $OUT"
chmod +x "$OUT"