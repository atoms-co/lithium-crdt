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

`master` is protected, so the version bump goes through a pull request like any other change.

1. Bump the version in `gradle.properties` in a PR and merge it.
2. Run `./scripts/release.sh`. It reads the version from `gradle.properties` on `origin/master`, tags that commit and pushes the tag.

Pushing the tag triggers the publish workflow, which builds whatever commit the tag points to. Only tag after the bump is merged, and don't use `git push origin master --tags`.

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
