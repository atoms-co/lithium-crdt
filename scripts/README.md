# Scripts

Utility scripts for managing the lithium-crdt project.

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

**Note:** This script only updates the local file. Publish through `release.sh` so the bump lands on master through a PR. Master rejects direct pushes.

## release.sh

Opens a version-bump PR, then tags the merged master commit. The publish workflow runs on tags matching `v*`.

**Usage:**
```bash
# On an up-to-date master: bump, push a release branch, open a PR
./scripts/release.sh [major|minor|patch]

# After that PR squash-merges: tag origin/master and push only the tag
./scripts/release.sh tag
```

**What the bump step does:**
1. Checks that you are on `master`, the tree is clean, and `HEAD` matches `origin/master`
2. Bumps `gradle.properties`
3. Pushes `release/vX.Y.Z` and opens a PR into `master`

**What the tag step does:**
1. Reads the version already on `origin/master`
2. Creates `vX.Y.Z` on that commit
3. Pushes the tag only

Tag the merged master commit, not the PR branch. A squash merge changes the SHA. If the tag already exists, the script refuses to move it.
