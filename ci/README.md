# CI Workflows

This directory contains documentation for the protobuf-crdt CI/CD pipelines.

**Repository:** https://github.com/atoms-co/protobuf-crdt

CI/CD is managed via GitHub Actions. Workflow definitions are in `.github/workflows/`.

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

Publishes library artifacts to Maven Central. Triggered by pushing a version tag (`v*`).

**Steps:**
1. Checkout repository
2. Setup Java 17 (Temurin)
3. Setup Gradle
4. Run `./gradlew publish`

**Required GitHub Secrets:**

| Secret | Description |
|--------|-------------|
| `MAVEN_CENTRAL_USERNAME` | Sonatype OSSRH username |
| `MAVEN_CENTRAL_PASSWORD` | Sonatype OSSRH password/token |
| `GPG_SIGNING_KEY` | ASCII-armored GPG private key |
| `GPG_SIGNING_KEY_PASSWORD` | Passphrase for the GPG key |

**Usage:**
```bash
# Tag and push to trigger publish
git tag v1.0.0
git push origin v1.0.0
```

## Published Artifacts

All artifacts are published to Maven Central under the group `co.atoms.protobuf.crdt`:

| Artifact ID | Description | Target Platform |
|-------------|-------------|-----------------|
| `crdt-resolver` | Core conflict resolution algorithms | All platforms |
| `crdt-protoc` | Protoc CRDT implementation | Java backend |
| `crdt-protoc-data` | Protoc-generated protobuf classes | Java backend |
| `crdt-wire` | Wire CRDT implementation | Android/Kotlin |
| `crdt-wire-data` | Wire-generated protobuf classes | Android/Kotlin |

## Version Management

The version is managed via three properties in `gradle.properties`:
- `version.major` - Major version (breaking changes)
- `version.minor` - Minor version (new features, backwards compatible)
- `version.patch` - Patch version (bug fixes)

Use `scripts/bump-version.sh` to increment versions:
```bash
./scripts/bump-version.sh patch   # 1.0.0 → 1.0.1
./scripts/bump-version.sh minor   # 1.0.5 → 1.1.0
./scripts/bump-version.sh major   # 1.5.3 → 2.0.0
```

All five modules are always published with the same version.

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
