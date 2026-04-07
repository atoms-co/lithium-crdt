# CI Workflows

This directory contains documentation for the lithium-crdt CI/CD pipelines.

**Repository:** https://github.com/atoms-co/lithium-crdt

CI/CD is managed via GitHub Actions. Workflow definitions are in [`.github/workflows/`](../.github/workflows/).

## Available Workflows

### ci.yml — Build & Test

Builds and tests the library. Runs automatically on:
- Push to `master`
- Pull requests targeting `master`

**Steps:**
1. Checkout repository
2. Setup Java 17 (Temurin)
3. Setup Gradle
4. Run `./gradlew clean build`
5. Run `./gradlew test`

### publish.yml — Publish to Maven Central

Publishes library artifacts to Maven Central. Triggered automatically when a version tag (`v*`) is pushed.

## Releasing a New Version

Releases are initiated manually by a maintainer using `scripts/release.sh`:

```bash
./scripts/release.sh patch   # or minor, major
```

The script ensures you're on a clean, up-to-date `master`, bumps the version, confirms before proceeding, then commits, tags, and pushes, ultimately triggering the publish workflow.

All five modules are always published with the same version.

## Published Artifacts

All artifacts are published to Maven Central under the group `co.atoms.lithium.crdt`:

| Artifact ID | Description | Target Platform |
|-------------|-------------|-----------------|
| `crdt-resolver` | Core conflict resolution algorithms | All platforms |
| `crdt-protoc` | Protoc CRDT implementation | Java backend |
| `crdt-protoc-data` | Protoc-generated protobuf classes | Java backend |
| `crdt-wire` | Wire CRDT implementation | Android/Kotlin |
| `crdt-wire-data` | Wire-generated protobuf classes | Android/Kotlin |


## Troubleshooting

### Build Failures

1. Check Java version is 17+ in the CI logs
2. Review Gradle output for dependency resolution issues
3. Verify the branch exists and is accessible

### Publishing Failures

1. Verify GitHub secrets are configured correctly in repo settings
2. Confirm Sonatype OSSRH credentials are valid
3. Check GPG key is correctly formatted (ASCII-armored)
4. Review version number for conflicts on Maven Central

### Test Failures

1. Review test output in CI logs
2. Run tests locally with `./gradlew test --rerun-tasks`
3. Check for environment-specific issues (CI vs local)
