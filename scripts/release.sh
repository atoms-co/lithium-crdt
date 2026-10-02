#!/bin/bash
set -euo pipefail

# Release script.
# Master rejects direct pushes, so a release is two steps:
#   1. ./scripts/release.sh [major|minor|patch]
#      Bumps gradle.properties on a release branch and opens a PR.
#   2. After that PR squash-merges, ./scripts/release.sh tag
#      Tags the merged origin/master commit and pushes only the tag.
#      The publish workflow runs on the tag.
#
# Usage: ./scripts/release.sh [major|minor|patch|tag]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
GRADLE_PROPERTIES="$PROJECT_DIR/gradle.properties"
REPO="atoms-co/lithium-crdt"

BUMP_TYPE="${1:-patch}"

cd "$PROJECT_DIR"

usage() {
    echo "Usage: $0 [major|minor|patch]"
    echo "       $0 tag"
}

confirm() {
    read -p "Continue? [y/N] " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Aborted."
        return 1
    fi
}

require_clean() {
    if [ -n "$(git status --porcelain)" ]; then
        echo "Error: working tree is not clean. Commit or stash changes first."
        exit 1
    fi
}

version_from_properties() {
    local file="$1"
    local major minor patch
    major=$(grep "^version.major=" "$file" | cut -d'=' -f2)
    minor=$(grep "^version.minor=" "$file" | cut -d'=' -f2)
    patch=$(grep "^version.patch=" "$file" | cut -d'=' -f2)
    echo "$major.$minor.$patch"
}

version_from_ref() {
    local ref="$1"
    git show "$ref:gradle.properties" | awk -F= '
        $1 == "version.major" { major = $2 }
        $1 == "version.minor" { minor = $2 }
        $1 == "version.patch" { patch = $2 }
        END { print major "." minor "." patch }
    '
}

open_bump_pr() {
    if [[ ! "$BUMP_TYPE" =~ ^(major|minor|patch)$ ]]; then
        usage
        exit 1
    fi

    local branch
    branch=$(git rev-parse --abbrev-ref HEAD)
    if [ "$branch" != "master" ]; then
        echo "Error: must be on master branch (currently on '$branch')"
        exit 1
    fi

    require_clean

    git fetch origin master
    local local_sha remote_sha
    local_sha=$(git rev-parse HEAD)
    remote_sha=$(git rev-parse origin/master)
    if [ "$local_sha" != "$remote_sha" ]; then
        echo "Error: local master is not up to date with origin/master. Pull first."
        exit 1
    fi

    "$SCRIPT_DIR/bump-version.sh" "$BUMP_TYPE" --quiet

    local version release_branch
    version=$(version_from_properties "$GRADLE_PROPERTIES")
    release_branch="release/v$version"

    echo ""
    echo "Ready to open a PR for v$version ($BUMP_TYPE bump)"
    echo "Branch: $release_branch"
    echo "The tag is pushed only after this PR squash-merges."
    echo ""
    if ! confirm; then
        git checkout -- gradle.properties
        exit 1
    fi

    git checkout -b "$release_branch"
    git add gradle.properties
    git commit -m "Bump version to $version"
    git push -u origin "$release_branch"

    gh pr create --repo "$REPO" --base master --head "$release_branch" \
        --title "Bump version to $version" \
        --body "$(cat <<EOF
## Summary
Bump the published version to \`$version\`.

Master rejects direct pushes. After this PR squash-merges, tag that master commit and push only the tag:

\`\`\`bash
./scripts/release.sh tag
\`\`\`

That tag triggers the publish workflow.
EOF
)"

    echo ""
    echo "PR opened for v$version."
    echo "After it squash-merges, from an up-to-date master run:"
    echo "  ./scripts/release.sh tag"
}

push_tag() {
    require_clean
    git fetch origin master --tags

    local master_sha version tag existing
    master_sha=$(git rev-parse origin/master)
    version=$(version_from_ref "origin/master")
    tag="v$version"

    if git rev-parse --verify --quiet "refs/tags/$tag" >/dev/null; then
        existing=$(git rev-parse "refs/tags/$tag^{}")
        echo "Error: $tag already exists at $existing."
        echo "Refusing to move it. origin/master is $master_sha."
        exit 1
    fi
    if git ls-remote --exit-code --tags origin "refs/tags/$tag" >/dev/null 2>&1; then
        echo "Error: $tag already exists on origin. Refusing to move it."
        git ls-remote origin "refs/tags/$tag"
        exit 1
    fi

    echo ""
    echo "Ready to tag origin/master as $tag"
    echo "Commit: $master_sha"
    echo "This pushes the tag only. It does not push the master branch."
    echo ""
    if ! confirm; then
        exit 1
    fi

    git tag "$tag" "$master_sha"
    git push origin "refs/tags/$tag"

    echo ""
    echo "Pushed $tag at $master_sha — publish workflow triggered."
    echo "Monitor: https://github.com/$REPO/actions/workflows/publish.yml"
}

case "$BUMP_TYPE" in
    tag)
        push_tag
        ;;
    major|minor|patch)
        open_bump_pr
        ;;
    *)
        usage
        exit 1
        ;;
esac
