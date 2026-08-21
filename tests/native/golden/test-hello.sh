#!/bin/bash
set -e

KOF_JAR="kof-cli/target/kof-cli-0.1.0-SNAPSHOT.jar"
TEST_DIR="tests/golden"
OUT_DIR="/tmp/kof-native-test"

echo "=== KofNative Golden Test ==="

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

echo "--- Compiling Point.kf to native ---"
java -jar "$KOF_JAR" build "$TEST_DIR" --target native --output "$OUT_DIR"

echo "--- Checking binary ---"
file "$OUT_DIR/Point"

echo "--- Running native ---"
OUTPUT=$("$OUT_DIR/Point")
echo "Output: $OUTPUT"

if echo "$OUTPUT" | grep -q "OK"; then
    echo "--- NATIVE PASS ---"
else
    echo "--- NATIVE FAIL ---"
    exit 1
fi

echo "--- Verifying JVM still works ---"
rm -rf "$OUT_DIR-jvm"
java -jar "$KOF_JAR" build "$TEST_DIR" --target jvm --output "$OUT_DIR-jvm"
if [ -f "$OUT_DIR-jvm/Point.class" ]; then
    echo "--- JVM PASS ---"
else
    echo "--- JVM FAIL ---"
    exit 1
fi

echo "=== ALL TESTS PASSED ==="
