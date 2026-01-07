# CI Workflows

This directory previously contained internal CI workflow definitions. The project now uses **GitHub Actions** for CI/CD.

## GitHub Actions Workflows

All workflows are located in `.github/workflows/`:

### build-and-test.yml

Automatically builds and tests the library on every push and pull request.

**Triggers:**
- Push to `master` or `main` branch
- Pull requests targeting `master` or `main`

**Steps:**
1. Checkout repository
2. Setup Java 17 (Temurin)
3. Run `./gradlew clean build`
4. Run `./gradlew test`
5. Upload test results as artifacts

### publish.yml

Publishes the library artifacts to Maven Central. This workflow is manually triggered.

**Triggers:**
- Manual dispatch via GitHub Actions UI or `gh` CLI

**Inputs:**
- `bump` (optional): Version bump type - `major`, `minor`, `patch`, or `none` (default: `patch`)
- `version` (optional): Explicit version override (skips auto-increment if provided)

**Required Secrets:**
- `OSSRH_USERNAME`: Maven Central (Sonatype OSSRH) username
- `OSSRH_PASSWORD`: Maven Central (Sonatype OSSRH) password or token
- `GPG_SIGNING_KEY`: GPG private key for artifact signing (ASCII-armored)
- `GPG_PASSPHRASE`: Passphrase for the GPG key
- `GPG_KEY_ID`: GPG key ID (short form)

**Steps:**
1. Checkout repository
2. Setup Java 17 (Temurin)
3. Bump version (if requested)
4. Run `./gradlew clean build`
5. Run `./gradlew test`
6. Sign and publish to Maven Central
7. Push version bump commit and tag
8. Create GitHub Release

## Usage

### Running Workflows via GitHub UI

1. Go to the repository's **Actions** tab
2. Select the workflow you want to run
3. Click **Run workflow**
4. Fill in the inputs and click **Run workflow**

### Running Workflows via `gh` CLI

```bash
# Run build and test (typically automatic, but can be manually triggered)
gh workflow run build-and-test.yml

# Publish with patch version bump (default)
gh workflow run publish.yml

# Publish with minor version bump
gh workflow run publish.yml -f bump=minor

# Publish with major version bump
gh workflow run publish.yml -f bump=major

# Publish without version bump
gh workflow run publish.yml -f bump=none

# Publish with explicit version
gh workflow run publish.yml -f version=2.0.0
```

### Monitoring Workflow Runs

```bash
# List recent workflow runs
gh run list

# Watch a running workflow
gh run watch

# View workflow run details
gh run view <run-id>

# View workflow run logs
gh run view <run-id> --log
```

## Version Management

The version is managed via three properties in `gradle.properties`:
- `version.major` - Major version (breaking changes)
- `version.minor` - Minor version (new features, backwards compatible)
- `version.patch` - Patch version (bug fixes)

### Automatic Version Bumping

The publish workflow automatically increments the version, commits, tags, and pushes:

1. Runs `scripts/bump-version.sh` to update `gradle.properties`
2. Commits the version bump
3. Creates an annotated tag (e.g., `v1.2.3`)
4. Pushes the commit and tag
5. Creates a GitHub Release

**Version bump types:**
- **patch** (default): `1.0.0` → `1.0.1`
- **minor**: `1.0.5` → `1.1.0` (resets patch to 0)
- **major**: `1.5.3` → `2.0.0` (resets minor and patch to 0)
- **none**: Skips version bump entirely (publishes current version)

## Published Artifacts

All artifacts are published to Maven Central under the group `com.css.protobuf.crdt`:

| Artifact ID | Description | Target Platform |
|-------------|-------------|-----------------|
| `crdt-resolver` | Core conflict resolution algorithms | All platforms |
| `crdt-protoc` | Protoc CRDT implementation | Java backend |
| `crdt-protoc-data` | Protoc-generated protobuf classes | Java backend |
| `crdt-wire` | Wire CRDT implementation | Android/Kotlin |
| `crdt-wire-data` | Wire-generated protobuf classes | Android/Kotlin |

## Setting Up Secrets

To enable Maven Central publishing, configure these repository secrets in GitHub:

### 1. Sonatype OSSRH Credentials

1. Create an account at https://central.sonatype.org/
2. Create a user token in your Sonatype account settings
3. Add `OSSRH_USERNAME` and `OSSRH_PASSWORD` secrets

### 2. GPG Signing Key

```bash
# Generate a GPG key (if you don't have one)
gpg --full-generate-key

# List keys to get the key ID
gpg --list-secret-keys --keyid-format SHORT

# Export the private key (ASCII-armored)
gpg --armor --export-secret-keys YOUR_KEY_ID > private-key.asc

# The contents of private-key.asc goes into GPG_SIGNING_KEY secret
# Your key passphrase goes into GPG_PASSPHRASE secret
# The short key ID goes into GPG_KEY_ID secret
```

### 3. Upload Public Key to Keyserver

```bash
# Upload to Ubuntu keyserver (used by Maven Central)
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
```

## Troubleshooting

### Build Failures

1. Check Java version is 17+ in the workflow logs
2. Review Gradle output for dependency resolution issues
3. Ensure all tests pass locally with `./gradlew test`

### Publishing Failures

1. Verify all secrets are configured correctly
2. Check Sonatype OSSRH credentials are valid
3. Ensure GPG key is uploaded to a public keyserver
4. Review the staging repository in Sonatype Nexus if artifacts fail validation
5. Check that artifact metadata (POM) meets Maven Central requirements

### GPG Signing Issues

1. Verify the GPG key is not expired
2. Ensure the passphrase is correct
3. Check that the key ID matches the private key

### Version Conflicts

If publishing fails due to version conflicts:
1. Check if the version already exists on Maven Central
2. Use `bump=none` with an explicit `version` input to override
