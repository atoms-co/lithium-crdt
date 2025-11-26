# protobuf-crdt

A Protocol Buffer CRDT library for distributed conflict-free synchronization. Enables field-level Last-Write-Wins conflict resolution for distributed systems without coordination overhead.

## Quick Start

```bash
# 1. Build
./gradlew build

# 2. Run tests
./gradlew test

# 3. Publish to local Maven for testing
./gradlew publishToMavenLocal
```

## Using in Your Project

### Android/Gradle Project

```kotlin
dependencies {
    implementation("com.css.protobuf.crdt:crdt-wire:1.0.0")
    implementation("com.css.protobuf.crdt:crdt-wire-data:1.0.0")
}
```

### Java Backend (Bazel)

```python
maven_install(
    artifacts = [
        "com.css.protobuf.crdt:crdt-protoc:1.0.0",
        "com.css.protobuf.crdt:crdt-protoc-data:1.0.0",
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
| `wire-data` | Wire-generated protobuf classes | Android/Kotlin |
| `wire` | Wire CRDT implementation | Android/Kotlin |
| `protoc-data` | Protoc-generated protobuf classes | Java backend |
| `protoc` | Protoc CRDT implementation | Java backend |

## Documentation

- Module READMEs: [wire-data](wire-data/README.md), [resolver](resolver/README.md), [wire](wire/README.md), [protoc](protoc/README.md)

## Requirements

- Java 17+ (LTS)
- Gradle 9.2.1 (via wrapper)
- Kotlin 2.2.21

## License

Internal CSS library - not for external distribution.
