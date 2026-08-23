#!/bin/bash
# Kof Golden Tests — compiles each case for a target, runs it and compares
# stdout against the expected output.
#
# Layout:
#   tests/golden/<case>/Main.kf        — the program
#   tests/golden/<case>/expected.txt   — expected stdout
#
# Usage:
#   tests/run-golden.sh [--target jvm|native] [case...]
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="$(cat "$ROOT/VERSION")"
KOF_JAR="$ROOT/kof-cli/target/kof-cli-$VERSION.jar"
GOLDEN_DIR="$ROOT/tests/golden"
TARGETS=("$@")

if [ ! -f "$KOF_JAR" ]; then
    echo "ERROR: jar not found: $KOF_JAR"
    echo "Run: mvn clean install -DskipTests"
    exit 1
fi

PASS=0
FAIL=0

for case_dir in "$GOLDEN_DIR"/*/; do
    case_name="$(basename "$case_dir")"
    if [ $# -gt 0 ]; then
        case "$case_name" in "$1") ;; *) continue ;; esac
    fi
    for target in jvm native; do
        OUT_DIR=$(mktemp -d)
        echo "--- [$case_name] $target ---"
        if ! java -jar "$KOF_JAR" build "$case_dir" --target "$target" --output "$OUT_DIR" >/dev/null 2>&1; then
            echo "FAIL: compilation"
            FAIL=$((FAIL + 1))
            rm -rf "$OUT_DIR"
            continue
        fi
        OUTPUT=""
        if [ "$target" = "jvm" ]; then
            OUTPUT=$(java -cp "$OUT_DIR" Default.Main 2>&1 || true)
        else
            if [ -x "$OUT_DIR/Default/Main" ]; then
                OUTPUT=$("$OUT_DIR/Default/Main" 2>&1 || true)
            else
                echo "FAIL: binary not found"
                FAIL=$((FAIL + 1))
                rm -rf "$OUT_DIR"
                continue
            fi
        fi
        EXPECTED="$(cat "$case_dir/expected.txt")"
        if [ "$OUTPUT" = "$EXPECTED" ]; then
            echo "PASS [$case_name/$target]"
            PASS=$((PASS + 1))
        else
            echo "FAIL [$case_name/$target]"
            echo "  expected: $EXPECTED"
            echo "  got:      $OUTPUT"
            FAIL=$((FAIL + 1))
        fi
        rm -rf "$OUT_DIR"
    done
done

echo ""
echo "=== GOLDEN TESTS: $PASS passed, $FAIL failed ==="
[ "$FAIL" -eq 0 ]