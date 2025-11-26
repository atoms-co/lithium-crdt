# Scripts

Utility scripts for managing the protobuf-crdt project.

## bump-version.sh

Increments the version in `gradle.properties` following semantic versioning.

**Usage:**
```bash
./scripts/bump-version.sh [major|minor|patch]
```

**Examples:**
```bash
# Increment patch version (1.0.0 → 1.0.1)
./scripts/bump-version.sh patch

# Increment minor version (1.0.5 → 1.1.0, resets patch to 0)
./scripts/bump-version.sh minor

# Increment major version (1.5.3 → 2.0.0, resets minor and patch to 0)
./scripts/bump-version.sh major
```

**Default:** If no argument is provided, defaults to `patch`.

**What it does:**
1. Reads current version from `gradle.properties`
2. Increments the specified version component
3. Updates `gradle.properties` with new version
4. Displays next steps for committing and publishing

**Note:** This script only updates the local file. You must commit and push the changes:

```bash
./scripts/bump-version.sh minor
git add gradle.properties
git commit -m "Bump version to 1.1.0"
git push
```

**CI Integration:** The publish-ci workflow automatically runs this script based on the `bump` input parameter, so manual bumps are optional for CI-based releases.

**Example CI trigger:**
```bash
css ci trigger publish-ci --input branch=master --input bump=minor
```
