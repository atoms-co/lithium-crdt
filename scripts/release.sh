#!/bin/bash
set -e

# Tags origin/master with the version in its gradle.properties and pushes the tag,
# which triggers the publish workflow. Bump the version in a PR and merge it first.
# Usage: ./scripts/release.sh

git fetch origin --tags

props=$(git show origin/master:gradle.properties)
major=$(echo "$props" | grep '^version\.major=' | cut -d= -f2)
minor=$(echo "$props" | grep '^version\.minor=' | cut -d= -f2)
patch=$(echo "$props" | grep '^version\.patch=' | cut -d= -f2)
version="$major.$minor.$patch"

if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Error: could not read version from gradle.properties on origin/master (got '$version')"
    exit 1
fi

if git rev-parse -q --verify "refs/tags/v$version" >/dev/null; then
    echo "Error: tag v$version already exists. Bump the version in a PR first."
    exit 1
fi

echo "Release v$version from origin/master ($(git rev-parse --short origin/master))?"
read -p "Continue? [y/N] " -n 1 -r
echo ""
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 1
fi

git tag "v$version" origin/master
git push origin "v$version"

echo "Pushed v$version. Monitor: https://github.com/atoms-co/lithium-crdt/actions/workflows/publish.yml"
