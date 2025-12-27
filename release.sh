#!/bin/bash

# release.sh - Automates the release process for Casio G-Shock Smart Sync

if [ -z "$1" ]; then
    echo "Usage: ./release.sh <version_name>"
    echo "Example: ./release.sh 25.4"
    exit 1
fi

VERSION_NAME=${1#v}
# Calculate version code by removing dots (e.g., 25.4 -> 254)
VERSION_CODE=$(echo $VERSION_NAME | sed 's/\.//g')

echo "🚀 Preparing release for version $VERSION_NAME (Code: $VERSION_CODE)..."

# 1. Update app/build.gradle
echo "📝 Updating app/build.gradle..."
sed -i "s/versionCode = .*/versionCode = $VERSION_CODE/" app/build.gradle
sed -i "s/versionName = .*/versionName = \"$VERSION_NAME\"/" app/build.gradle

# 2. Update F-Droid Metadata (Fastlane)
CHANGELOG_PATH="fastlane/metadata/android/en-US/changelogs/${VERSION_NAME}.txt"
echo "📂 Creating F-Droid changelog at $CHANGELOG_PATH..."

if [ -f "RELEASE_NOTES.md" ]; then
    # Extract the first few lines or a summary from RELEASE_NOTES.md
    # For now, we'll take the content under "Key Features & Improvements"
    echo "Companion Device Pairing: Modernized for Android 11-14+, added multi-watch support, and a new scrollable paired devices list on the connection screen." > "$CHANGELOG_PATH"
else
    echo "New release $VERSION_NAME" > "$CHANGELOG_PATH"
fi

# 3. Git Operations
echo "💾 Committing changes..."
git add app/build.gradle "$CHANGELOG_PATH" gradle.properties release.sh .github/workflows/build-apk.yml
git commit -m "Release v$VERSION_NAME"

echo "🏷️ Tagging release..."
git tag -a "v$VERSION_NAME" -m "Release version $VERSION_NAME"

echo "📤 Pushing to GitHub..."
CURRENT_BRANCH=$(git branch --show-current)
git push origin "$CURRENT_BRANCH"
git push origin "v$VERSION_NAME"

# 4. Update master branch for F-Droid
if [ "$CURRENT_BRANCH" != "master" ]; then
    echo "🔄 Merging $CURRENT_BRANCH into master..."
    git checkout master
    git merge "$CURRENT_BRANCH" --no-edit
    git push origin master
    git checkout "$CURRENT_BRANCH"
fi

echo "✅ Release process initiated! The GitHub Action will now build and upload the APK."
