# CI Workflows

This directory contains CI workflow definitions for the protobuf-crdt library using the CSS CI DSL.

**Repository:** https://github.com/csscompany-enterprise/protobuf-crdt

**CSS CI DSL Documentation:** https://csscompany.atlassian.net/wiki/spaces/CDLBS/pages/2327969872/CSS+CI+DSL

## Available Workflows

### build-and-test-ci.yaml

Builds and tests the library. This workflow is designed for pull request validation and continuous integration.

**Inputs:**
- `ref` (optional): Git ref to build - branch or SHA (default: `origin/master`)

**Secrets:** None required (uses public repositories)

**Steps:**
1. Checkout repository
2. Setup Java 17+
3. Run `./gradlew clean build`
4. Run `./gradlew test`

**Usage:**
```bash
# Run interactive (recommended: use --sha HEAD to read yaml from current branch)
css ci trigger ci/build-and-test-ci.yaml --sha HEAD

# Build from a specific branch
css ci trigger ci/build-and-test-ci.yaml --sha HEAD --input ref=origin/feature-branch

# Build from a specific commit SHA
css ci trigger ci/build-and-test-ci.yaml --sha HEAD --input ref=abc123def456
```

### publish-ci.yaml

Publishes the library artifacts to Artifactory. Use this workflow for releases and deployments.

**Inputs:**
- `ref` (optional): Git ref to release - branch or SHA (default: `origin/master`)
- `bump` (optional): Version bump type - `major`, `minor`, `patch`, or `none` (default: `patch`)
- `version` (optional): Explicit version override (skips auto-increment if provided)

**Secrets:**
- `artifactory-username`: Artifactory username
- `artifactory-password`: Artifactory password

**Steps:**
1. Checkout repository
2. Setup Java 17+
3. Run `./gradlew clean build`
4. Run `./gradlew publish`

**Post Actions:**
- On success: Notifies `#android` Slack channel with published version and commit link
- On failure: Notifies `#android` Slack channel with failure details

**Usage:**
```bash
# Run interactive (recommended: use --sha HEAD to read yaml from current branch)
css ci trigger ci/publish-ci.yaml --sha HEAD

# Release from master with patch bump (default)
css ci trigger ci/publish-ci.yaml --sha HEAD --input ref=origin/master

# Release from a specific branch
css ci trigger ci/publish-ci.yaml --sha HEAD --input ref=origin/release-branch

# Minor release (auto-increments minor: 1.0.5 → 1.1.0)
css ci trigger ci/publish-ci.yaml --sha HEAD --input bump=minor

# Major release (auto-increments major: 1.5.3 → 2.0.0)
css ci trigger ci/publish-ci.yaml --sha HEAD --input bump=major

# Publish without incrementing version
css ci trigger ci/publish-ci.yaml --sha HEAD --input bump=none

# Publish with explicit version override (skips auto-increment)
css ci trigger ci/publish-ci.yaml --sha HEAD --input version=1.2.3
```

**Important:** Always use `--sha HEAD` to ensure CI reads the pipeline yaml from your current branch. Without this flag, CI may use a cached or default branch version of the yaml file.

**Note:** When using a version bump with a SHA ref (e.g., `ref=abc123def456`), the bump will fail because there's no branch to push the version commit to. Use `bump=none` with explicit SHA refs, or use branch names for automatic version bumping.

**Version Management:**

The version is managed via three properties in `gradle.properties`:
- `version.major` - Major version (breaking changes)
- `version.minor` - Minor version (new features, backwards compatible)
- `version.patch` - Patch version (bug fixes)

### Automatic Version Bumping

The publish workflow automatically increments the version, commits, and pushes it back to the repository:

1. Fetches and rebases onto the latest branch state
2. Runs `scripts/bump-version.sh` to update `gradle.properties`
3. Commits the version bump
4. Pushes the commit back to the branch

**Version bump types:**
- **Default (patch)**: `1.0.0` → `1.0.1`
- **Minor bump**: `1.0.5` → `1.1.0` (resets patch to 0)
- **Major bump**: `1.5.3` → `2.0.0` (resets minor and patch to 0)
- **none**: Skips version bump entirely (publishes current version)

All five modules (`crdt-resolver`, `crdt-protoc`, `crdt-protoc-data`, `crdt-wire`, `crdt-wire-data`) are always 
published with the 
same version.

## Published Artifacts

All artifacts are published to Artifactory under the group `com.css.internal.shared.storage.crdt`:

| Artifact ID | Description | Target Platform |
|-------------|-------------|-----------------|
| `crdt-resolver` | Core conflict resolution algorithms | All platforms |
| `crdt-protoc` | Protoc CRDT implementation | Java backend |
| `crdt-protoc-data` | Protoc-generated protobuf classes | Java backend |
| `crdt-wire` | Wire CRDT implementation | Android/Kotlin |
| `crdt-wire-data` | Wire-generated protobuf classes | Android/Kotlin |

## Troubleshooting

### Build Failures

If the build fails:
1. Check Java version is 17+ in the CI logs
2. Review Gradle output for dependency resolution issues
3. Verify the branch exists and is accessible

### Publishing Failures

If publishing fails:
1. Verify `artifactory-username` and `artifactory-password` secrets are configured correctly
2. Check Artifactory URL is accessible from CI environment
3. Confirm write permissions to the repository
4. Review version number for conflicts (if using explicit version)

### Test Failures

If tests fail:
1. Review test output in CI logs
2. Run tests locally with `./gradlew test --rerun-tasks`
3. Check for environment-specific issues (CI vs local)
