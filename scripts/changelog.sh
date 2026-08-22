#!/usr/bin/env bash
#
# changelog.sh — generates the changelog section for the next release from
# commits since the last release tag, grouped by conventional commit prefix.
#
# Usage:
#   scripts/changelog.sh [since-tag]
#
# Without arguments it uses the most recent tag matching kof-* (or 'kof-0.0.0'
# if no tag exists yet). Output is printed to stdout; the release pipeline
# appends it to CHANGELOG.md.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SINCE="${1:-}"
if [ -z "$SINCE" ]; then
    SINCE="$(git tag -l 'kof-*' --sort=-v:refname | head -1 || true)"
    if [ -z "$SINCE" ]; then
        SINCE="$(git rev-list --max-parents=0 HEAD)"
        SINCE="$SINCE"
    fi
fi

NEXT_VERSION="$(cat "$ROOT/VERSION")"

GROUPED="$(git log --pretty=format:'%s' "$SINCE..HEAD" 2>/dev/null \
    | grep -E '^(feat|fix|docs|refactor|test|build|tooling|chore)(\([^)]*\))?!?:' || true)"

echo "## [$NEXT_VERSION] - $(date +%Y-%m-%d)"
echo ""
if [ -z "$GROUPED" ]; then
    echo "No changes recorded."
    echo ""
    exit 0
fi

section() {
    local label="$1" prefix="$2"
    local items
    items="$(git log --pretty=format:'%s' "$SINCE..HEAD" 2>/dev/null | grep -E "^$prefix" || true)"
    if [ -n "$items" ]; then
        echo "### $label"
        echo ""
        printf '%s\n' "$items" | sed -E "s/^$prefix[^:]*: *//" | sed 's/^/  - /'
        echo ""
    fi
}

section "Features" "feat"
section "Bugfixes" "fix"
section "Documentation" "docs"
section "Refactoring" "refactor"
section "Tests" "test"
section "Build" "build"
section "Tooling" "tooling"