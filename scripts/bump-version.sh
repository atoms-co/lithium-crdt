#!/bin/bash
set -e

# Script to bump version in gradle.properties
# Usage: ./scripts/bump-version.sh [major|minor|patch] [--quiet]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
GRADLE_PROPERTIES="$PROJECT_DIR/gradle.properties"

# Parse arguments
BUMP_TYPE="patch"
QUIET=false

for arg in "$@"; do
    case "$arg" in
        --quiet|-q)
            QUIET=true
            ;;
        major|minor|patch)
            BUMP_TYPE="$arg"
            ;;
        *)
            echo "Error: Invalid argument '$arg'"
            echo "Usage: $0 [major|minor|patch] [--quiet]"
            exit 1
            ;;
    esac
done

# Read current version components
MAJOR=$(grep "^version.major=" "$GRADLE_PROPERTIES" | cut -d'=' -f2)
MINOR=$(grep "^version.minor=" "$GRADLE_PROPERTIES" | cut -d'=' -f2)
PATCH=$(grep "^version.patch=" "$GRADLE_PROPERTIES" | cut -d'=' -f2)

echo "Current version: $MAJOR.$MINOR.$PATCH"

# Increment version based on bump type
case "$BUMP_TYPE" in
    major)
        MAJOR=$((MAJOR + 1))
        MINOR=0
        PATCH=0
        ;;
    minor)
        MINOR=$((MINOR + 1))
        PATCH=0
        ;;
    patch)
        PATCH=$((PATCH + 1))
        ;;
esac

NEW_VERSION="$MAJOR.$MINOR.$PATCH"
echo "New version: $NEW_VERSION"

# Update gradle.properties
sed -i.bak "s/^version.major=.*/version.major=$MAJOR/" "$GRADLE_PROPERTIES"
sed -i.bak "s/^version.minor=.*/version.minor=$MINOR/" "$GRADLE_PROPERTIES"
sed -i.bak "s/^version.patch=.*/version.patch=$PATCH/" "$GRADLE_PROPERTIES"

# Remove backup file
rm -f "$GRADLE_PROPERTIES.bak"

echo "✓ Version bumped to $NEW_VERSION in gradle.properties"

if [ "$QUIET" = false ]; then
    echo ""
    echo "Next steps:"
    echo "  1. Review the changes: git diff gradle.properties"
    echo "  2. Commit the version bump: git add gradle.properties && git commit -m \"Bump version to $NEW_VERSION\""
    echo "  3. Push to remote: git push"
    echo "  4. Trigger publish: css ci trigger publish-ci --branch=master"
fi
