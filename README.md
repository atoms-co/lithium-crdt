# protobuf-crdt

A Protocol Buffer CRDT library for distributed conflict-free synchronization. Enables field-level Last-Write-Wins conflict resolution for distributed systems without coordination overhead.

## Quick Start

```bash
# 1. Setup local configuration (optional - defaults work for VPN users)
cp local.properties.example local.properties

# 2. Build
./gradlew build

# 3. Run tests
./gradlew test

# 4. Publish to local Maven for testing
./gradlew publishToMavenLocal
```

## Using in Your Project

### Android/Gradle Project

```kotlin
dependencies {
    implementation("com.css.internal.shared.storage.crdt:crdt-wire:1.0.0")
    implementation("com.css.internal.shared.storage.crdt:crdt-data:1.0.0")
}
```

### Java Backend (Bazel)

```python
maven_install(
    artifacts = [
        "com.css.internal.shared.storage.crdt:crdt-protoc:1.0.0",
        "com.css.internal.shared.storage.crdt:crdt-protoc-data:1.0.0",
    ],
    repositories = ["https://artifactory.cssvpn.com/artifactory/monorepo-local"],
)
```

## Architecture

This library implements field-level CRDT conflict resolution through:

- **Separated Version Architecture**: Version metadata stored separately from business data
- **Dual Implementations**: Wire (Android/Kotlin) and Protoc (Java/backend)
- **Shared Resolution Logic**: Platform-agnostic algorithms in the resolver module
- **Zero Data Duplication**: O(1) field access, O(m) space overhead where m = modified fields

See [CLAUDE.md](CLAUDE.md) for comprehensive architecture documentation.

## Modules

| Module | Description | For |
|--------|-------------|-----|
| `resolver` | Core conflict resolution algorithms | All platforms |
| `data` | Wire-generated protobuf classes | Android/Kotlin |
| `wire` | Wire CRDT implementation | Android/Kotlin |
| `protoc` | Protoc CRDT implementation | Java backend |
| `protoc-data` | Protoc-generated protobuf classes | Java backend |

## Development

### Building
```bash
./gradlew build
```

### Running Tests
```bash
./gradlew test
```

### Publishing
```bash
# Publish to local Maven
./gradlew publishToMavenLocal

# Publish to Artifactory
./gradlew publish
```

### Configuration

The library uses the same Artifactory authentication as the Android project:

- **VPN Mode** (default): No configuration needed
- **No-VPN Mode**: Set `css.buildWithoutVpn=true` in `local.properties` and configure `~/.netrc`
- **Offline Mode**: Set `css.airplaneMode=true` in `local.properties`

See [BUILD_SETUP.md](BUILD_SETUP.md) for detailed configuration instructions.

## Documentation

- [CLAUDE.md](CLAUDE.md) - Comprehensive architecture and development guide
- [BUILD_SETUP.md](BUILD_SETUP.md) - Build system setup and configuration
- [local.properties.example](local.properties.example) - Local configuration template
- Module READMEs: [data](data/README.md), [resolver](resolver/README.md), [wire](wire/README.md), [protoc](protoc/README.md)

## Requirements

- Java 17+ (LTS)
- Gradle 9.2.1 (via wrapper)
- Kotlin 2.2.21

## License

Internal CSS library - not for external distribution.
