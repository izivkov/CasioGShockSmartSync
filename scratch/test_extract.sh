#!/bin/bash
VERSION_NAME="41.0"
GITHUB_NOTES_FILE="test_github_notes.md"
CHANGELOG_PATH="test_fdroid_changelog.txt"

awk "/# Release Notes - Casio G-Shock Smart Sync v$VERSION_NAME/{flag=1;next} /---/{flag=0} flag" RELEASE_NOTES.md > "$GITHUB_NOTES_FILE"

grep "^*" "$GITHUB_NOTES_FILE" | sed 's/^* //' | head -n 5 | tr '\n' ' ' | sed 's/  */ /g' | cut -c1-450 > "$CHANGELOG_PATH"

echo "--- GITHUB NOTES ---"
cat "$GITHUB_NOTES_FILE"
echo "--- FDROID CHANGELOG ---"
cat "$CHANGELOG_PATH"
echo ""

rm "$GITHUB_NOTES_FILE" "$CHANGELOG_PATH"
