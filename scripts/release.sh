#!/bin/bash
set -e

# Release script: bumps version, commits, tags, and pushes to trigger the publish workflow.
# Usage: ./scripts/release.sh [major|minor|patch]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
GRADLE_PROPERTIES="$PROJECT_DIR/gradle.properties"

BUMP_TYPE="${1:-patch}"

if [[ ! "$BUMP_TYPE" =~ ^(major|minor|patch)$ ]]; then
    echo "Usage: $0 [major|minor|patch]"
    exit 1
fi

cd "$PROJECT_DIR"

# Ensure we're on master and clean
BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$BRANCH" != "master" ]; then
    echo "Error: must be on master branch (currently on '$BRANCH')"
    exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
    echo "Error: working tree is not clean. Commit or stash changes first."
    exit 1
fi

git fetch origin master
LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse origin/master)
if [ "$LOCAL" != "$REMOTE" ]; then
    echo "Error: local master is not up to date with origin. Pull first."
    exit 1
fi

# Bump version
"$SCRIPT_DIR/bump-version.sh" "$BUMP_TYPE" --quiet

MAJOR=$(grep "^version.major=" "$GRADLE_PROPERTIES" | cut -d'=' -f2)
MINOR=$(grep "^version.minor=" "$GRADLE_PROPERTIES" | cut -d'=' -f2)
PATCH=$(grep "^version.patch=" "$GRADLE_PROPERTIES" | cut -d'=' -f2)
VERSION="$MAJOR.$MINOR.$PATCH"

echo ""
echo "Ready to release v$VERSION ($BUMP_TYPE bump)"
echo ""
read -p "Continue? [y/N] " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    git checkout -- gradle.properties
    echo "Aborted."
    exit 1
fi

# Commit, tag, and push
git add gradle.properties
git commit -m "Bump version to $VERSION"
git tag "v$VERSION"
git push origin master --tags

echo ""
echo "Released v$VERSION — publish workflow triggered."
echo "Monitor: https://github.com/atoms-co/lithium-crdt/actions/workflows/publish.yml"
