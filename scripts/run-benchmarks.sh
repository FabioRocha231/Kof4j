#!/usr/bin/env bash
# Run the full benchmark suite (jvm + native + js) and update baselines.
# Usage: scripts/run-benchmarks.sh [--iterations N] [--quick]
set -euo pipefail
cd "$(dirname "$0")/.."

JAR="kof-cli/target/kof-cli-*.jar"
JAR=$(ls $JAR | grep -v original | head -1)
if [ -z "$JAR" ]; then
    echo "building kof-cli first..."
    mvn -q package -pl kof-cli -am -DskipTests
    JAR=$(ls $JAR | grep -v original | head -1)
fi

VERSION=$(cat VERSION)
EXTRA=()
if [ "${1:-}" = "--quick" ]; then EXTRA+=(--quick); shift; fi
if [ "${1:-}" = "--iterations" ]; then EXTRA+=(--iterations "$2"); shift 2; fi

for TARGET in jvm native js; do
    echo "=== $TARGET ==="
    java -jar "$JAR" bench --target "$TARGET" "${EXTRA[@]}" \
        --update-baseline "benchmarks/baselines/$TARGET-$VERSION.json" || true
done