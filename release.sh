#!/bin/bash

# release.sh - Automates the release process for Casio G-Shock Smart Sync

# Check if building for existing tag
if [ "$1" = "--existing" ]; then
    if [ -z "$2" ]; then
        echo "Usage: ./release.sh --existing <tag_name>"
        echo "Example: ./release.sh --existing v25.4"
        exit 1
    fi
    
    TAG_NAME=${2#v}
    TAG_NAME="v$TAG_NAME"
    
    echo "🏷️  Building APK for existing tag: $TAG_NAME"
    
    # Check if tag exists
    if ! git rev-parse "$TAG_NAME" >/dev/null 2>&1; then
        echo "❌ Error: Tag $TAG_NAME does not exist"
        exit 1
    fi
    
    # Save current branch
    ORIGINAL_BRANCH=$(git branch --show-current)
    
    echo "📦 Checking out tag $TAG_NAME..."
    git checkout "$TAG_NAME"
    
    # Check if keystore exists for signing
    if [ -f "app/release.keystore" ]; then
        echo "🔑 Keystore found, building signed APK..."
        ./gradlew assembleRelease
    else
        echo "⚠️  No keystore found. Building unsigned release APK..."
        echo "   (To build signed APK, ensure app/release.keystore exists and set environment variables)"
        ./gradlew assembleRelease
    fi
    
    APK_PATH="app/build/outputs/apk/release/app-release.apk"
    
    if [ ! -f "$APK_PATH" ]; then
        echo "❌ Error: APK not found at $APK_PATH"
        git checkout "$ORIGINAL_BRANCH"
        exit 1
    fi
    
    echo "📤 Uploading APK to GitHub release $TAG_NAME..."
    
    # Check if gh CLI is installed
    if ! command -v gh &> /dev/null; then
        echo "❌ Error: GitHub CLI (gh) is not installed"
        echo "   Install it from: https://cli.github.com/"
        git checkout "$ORIGINAL_BRANCH"
        exit 1
    fi
    
    # Upload to existing release (will replace if already exists)
    gh release upload "$TAG_NAME" "$APK_PATH" --clobber
    
    echo "✅ APK uploaded successfully to release $TAG_NAME"
    
    # Return to original branch
    echo "🔄 Returning to original branch..."
    git checkout "$ORIGINAL_BRANCH"
    
    echo "✅ Done! APK has been uploaded to GitHub release without triggering F-Droid."
    exit 0
fi

# Normal release process (new release)
if [ -z "$1" ]; then
    echo "Usage: ./release.sh <version_name>"
    echo "       ./release.sh --existing <tag_name>"
    echo ""
    echo "Examples:"
    echo "  ./release.sh 25.4              # Create new release"
    echo "  ./release.sh --existing v25.4  # Build APK for existing tag"
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

# 1.5 Update README.md with the latest release link at line 146
echo "📝 Updating README.md with latest release link..."
sed -i "146s|https://github.com/izivkov/CasioGShockSmartSync/releases/tag/v.*|https://github.com/izivkov/CasioGShockSmartSync/releases/tag/v$VERSION_NAME)|" README.md

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
git add app/build.gradle "$CHANGELOG_PATH" gradle.properties release.sh .github/workflows/build-apk.yml README.md
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
    git pull origin master
    git merge "$CURRENT_BRANCH" --no-edit
    git push origin master
    git checkout "$CURRENT_BRANCH"
fi

echo "✅ Release process initiated! The GitHub Action will now build and upload the APK."
