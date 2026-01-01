#!/bin/bash

# release.sh - Automates the release process for Casio G-Shock Smart Sync

# Normal release process (new release)
if [ -z "$1" ] || [[ "$1" == -* ]]; then
    echo "Usage: ./release.sh <version_name>"
    echo ""
    echo "Examples:"
    echo "  ./release.sh 25.4              # Create new release"
    exit 1
fi

# Check if gh CLI is installed
if ! command -v gh &> /dev/null; then
    echo "⚠️  Warning: GitHub CLI (gh) is not installed. GitHub release will not be created."
    echo "   Install it from: https://cli.github.com/"
    GH_AVAILABLE=false
else
    GH_AVAILABLE=true
fi

VERSION_NAME=${1#v}
# Calculate version code by removing dots (e.g., 25.4 -> 254)
VERSION_CODE=$(echo $VERSION_NAME | sed 's/\.//g')

echo "🚀 Preparing release for version $VERSION_NAME (Code: $VERSION_CODE)..."

# 1. Update app/build.gradle
echo "📝 Updating app/build.gradle..."
sed -i "s/versionCode = .*/versionCode = $VERSION_CODE/" app/build.gradle
sed -i "s/versionName = .*/versionName = \"$VERSION_NAME\"/" app/build.gradle

<<<<<<< HEAD
# 2. Update F-Droid Metadata (Fastlane)
=======
# 1.5 Update README.md with the latest release link at line 146
echo "📝 Updating README.md with latest release link..."
sed -i "146s|https://github.com/izivkov/CasioGShockSmartSync/releases/tag/v.*|https://github.com/izivkov/CasioGShockSmartSync/releases/tag/v$VERSION_NAME)|" README.md

# 2. Create latest.txt version metadata
echo "📄 Creating latest.txt with version $VERSION_NAME..."
echo "$VERSION_NAME" > latest.txt

# 3. Update F-Droid Metadata (Fastlane)
>>>>>>> main
CHANGELOG_PATH="fastlane/metadata/android/en-US/changelogs/${VERSION_NAME}.txt"
echo "📂 Creating F-Droid changelog at $CHANGELOG_PATH..."

if [ -f "RELEASE_NOTES.md" ]; then
    # Extract the first few lines or a summary from RELEASE_NOTES.md
    # For now, we'll take the content under "Key Features & Improvements"
    echo "Companion Device Pairing: Modernized for Android 11-14+, added multi-watch support, and a new scrollable paired devices list on the connection screen." > "$CHANGELOG_PATH"
else
    echo "New release $VERSION_NAME" > "$CHANGELOG_PATH"
fi

# 4. Git Operations
echo "💾 Committing changes..."
<<<<<<< HEAD
git add app/build.gradle "$CHANGELOG_PATH" gradle.properties release.sh .github/workflows/build-apk.yml
=======
git add app/build.gradle "$CHANGELOG_PATH" gradle.properties release.sh .github/workflows/build-apk.yml README.md latest.txt
>>>>>>> main
git commit -m "Release v$VERSION_NAME"

echo "🏷️ Tagging release..."
git tag -a "v$VERSION_NAME" -m "Release version $VERSION_NAME"

echo "📤 Pushing to GitHub..."
CURRENT_BRANCH=$(git branch --show-current)
git push origin "$CURRENT_BRANCH"
git push origin "v$VERSION_NAME"

# Create GitHub Release
if [ "$GH_AVAILABLE" = true ]; then
    echo "🎁 Creating GitHub release v$VERSION_NAME..."
    if [ -f "$CHANGELOG_PATH" ]; then
        gh release create "v$VERSION_NAME" --title "Release v$VERSION_NAME" --notes-file "$CHANGELOG_PATH"
    else
        gh release create "v$VERSION_NAME" --title "Release v$VERSION_NAME" --notes "New release $VERSION_NAME"
    fi
fi

# 4. Update master branch for F-Droid
if [ "$CURRENT_BRANCH" != "master" ]; then
    echo "🔄 Merging $CURRENT_BRANCH into master..."
    git checkout master
    git pull origin master
    git merge "$CURRENT_BRANCH" --no-edit
    git push origin master
    git checkout "$CURRENT_BRANCH"
fi

echo "✅ Release process initiated! The GitHub Action will now build and upload the APK."
