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

**What `./scripts/release.sh patch` does:**
1. Checks that you are on `master`, the tree is clean, and `HEAD` matches `origin/master`
2. Increments `gradle.properties` (`patch` by default, or `minor` / `major`)
3. Commits that change on `release/vX.Y.Z`, pushes the branch, and opens a PR into `master`
4. Does not create or push a tag

**What `./scripts/release.sh tag` does:**
1. Fetches `origin/master`
2. Reads `version.major`, `version.minor`, and `version.patch` from that commit’s `gradle.properties`
3. Builds one tag name from those numbers, such as `v1.1.5`
4. If that tag already exists, prints an error and exits
5. If it does not, tags that `master` commit and pushes only the tag

The tag push starts the publish workflow. This command does not increment the version and does not look up the bump PR. A bump that has not merged leaves the previous version in `gradle.properties`, so the tag name already exists and the command exits. Tag the merged `master` commit, not the PR branch: a squash merge changes the SHA.
