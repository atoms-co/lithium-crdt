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
# Run interactive
css ci trigger android/internal/shared/storage/crdt/protobuf-crdt/build-and-test-ci.yaml

# Build from a specific branch
css ci trigger android/internal/shared/storage/crdt/protobuf-crdt/build-and-test-ci.yaml --input ref=origin/feature-branch

# Build from a specific commit SHA
css ci trigger android/internal/shared/storage/crdt/protobuf-crdt/build-and-test-ci.yaml --input ref=abc123def456
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
# Run interactive
css ci trigger android/internal/shared/storage/crdt/protobuf-crdt/publish-ci.yaml

# Release from a specific branch
css ci trigger android/internal/shared/storage/crdt/protobuf-crdt/publish-ci.yaml --input ref=origin/release-branch

# Release from a specific commit SHA
css ci trigger android/internal/shared/storage/crdt/protobuf-crdt/publish-ci.yaml --input ref=abc123def456

# Minor release (auto-increments minor: 1.0.5 → 1.1.0)
css ci trigger android/internal/shared/storage/crdt/protobuf-crdt/publish-ci.yaml --input bump=minor

# Major release (auto-increments major: 1.5.3 → 2.0.0)
css ci trigger android/internal/shared/storage/crdt/protobuf-crdt/publish-ci.yaml --input bump=major

# Publish without incrementing version
css ci trigger android/internal/shared/storage/crdt/protobuf-crdt/publish-ci.yaml --input bump=none

# Publish with explicit version override (skips auto-increment)
css ci trigger android/internal/shared/storage/crdt/protobuf-crdt/publish-ci.yaml --input version=1.2.3
```

**Version Management:**

The version is managed via three properties in `gradle.properties`:
- `version.major` - Major version (breaking changes)
- `version.minor` - Minor version (new features, backwards compatible)
- `version.patch` - Patch version (bug fixes)

### Automatic Version Bumping

The publish workflow automatically increments the version and commits it back to the repository:

1. **Default (patch)**: `1.0.0` → `1.0.1`
2. **Minor bump**: `1.0.5` → `1.1.0` (resets patch to 0)
3. **Major bump**: `1.5.3` → `2.0.0` (resets minor and patch to 0)

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
