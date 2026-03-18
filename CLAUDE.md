# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Conflict-Free Replicated Data Type (CRDT) library for Protocol Buffer messages, enabling distributed synchronization across devices without coordination. It provides field-level Last-Write-Wins (LWW) conflict resolution that operates directly on protobuf structures with a parallel version tree, avoiding the data duplication and mapping overhead of traditional CRDT libraries.

**Key Innovation:** Complete separation between version management and business data—the library maintains a parallel tree of version nodes matching the protobuf message structure without duplicating field values, enabling O(1) field access and O(m) space overhead where m = modified field count.

## Module Architecture

The repository is organized into 7 modules with clear separation of concerns:

### Core Modules

- **`resolver/`** - Platform-agnostic CRDT conflict resolution algorithms with zero external dependencies beyond Kotlin stdlib. Contains the core logic for field-level LWW semantics, version tree traversal, map/collection strategies, and tombstone cleanup policies. All resolution logic is shared by both Wire and Protoc implementations.

- **`data/`** - Pure protobuf schema definitions (`*.proto` files in `src/main/proto/`). Defines `VersionNode`, `VersionSequence`, `DistributedDocument`, and `Actors` message structures. This is the single source of truth for all proto schemas. Publishes a proto JAR artifact for consumption by build systems like Bazel.

### Data Generation Modules

- **`wire-data/`** - Wire-generated Kotlin classes from the `data` module schemas. Provides idiomatic Kotlin data classes for Android/Kotlin projects.

- **`protoc-data/`** - Java protobuf class generation from the `data` module schemas. Enables backend services to use the same proto definitions without depending on Wire.

### Platform-Specific Implementations

- **`wire/`** - CRDT resolver for Square Wire protobuf library (Kotlin/Android). Uses `@WireField` annotations for compile-time field metadata, providing zero-reflection overhead. Entry point: `WireCrdtResolverProvider`

- **`protoc/`** - CRDT resolver for standard Google protobuf (Java/backend). Uses `getDescriptor()` for runtime field introspection via descriptors. Entry point: `CrdtMessageResolverProvider`

### Testing Support

- **`fixtures/`** - Shared test fixtures and utilities used across test suites

- **`wire/test/`** - Test-specific Wire message definitions

- **`protoc/test/`** - Test-specific protoc message definitions

## Build System

This repository uses **Gradle 9.2.1 with Kotlin DSL**. All dependencies are managed through Gradle version catalogs defined in `settings.gradle.kts`.

### Common Development Commands

#### Building Modules
```bash
# Build all modules
./gradlew build

# Build specific module
./gradlew :resolver:build
./gradlew :data:build
./gradlew :wire-data:build
./gradlew :wire:build
./gradlew :protoc:build
./gradlew :protoc-data:build

# Clean build
./gradlew clean build
```

#### Running Tests
```bash
# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :resolver:test
./gradlew :wire:test
./gradlew :protoc:test

# Run tests with output
./gradlew test --info
```

#### Proto Schema Compilation
```bash
# Wire compilation for data module
./gradlew :wire-data:generateWireProtos

# Wire compilation for wire-test module
./gradlew :wire-test:generateWireTestProtos

# Protoc compilation for protoc-data module
./gradlew :protoc-data:generateProto

# Clean and regenerate all protos
./gradlew clean :wire-data:generateWireProtos :protoc-data:generateProto
```

#### Publishing to Maven Repository
```bash
# Publish all modules to local Maven repository (~/.m2/repository)
./gradlew publishToMavenLocal

# Publish specific module to local Maven
./gradlew :resolver:publishToMavenLocal
./gradlew :wire:publishToMavenLocal

# Publish to Maven Central (requires credentials)
./gradlew publish

# Dry-run to see what would be published
./gradlew publish --dry-run
```

#### Viewing Dependencies
```bash
# View all project dependencies
./gradlew :resolver:dependencies

# View dependency tree for specific configuration
./gradlew :wire:dependencies --configuration runtimeClasspath
```

#### CI

CI runs via GitHub Actions. See `.github/workflows/` for workflow definitions and `ci/README.md` for documentation.

- **CI build & test**: Runs automatically on push to `master` and on pull requests.
- **Publish**: Triggered by pushing a version tag (`v*`).

### Maven Publishing Configuration

The library publishes 5 artifacts to Maven Central:

| Artifact ID | Module | Description |
|-------------|--------|-------------|
| `crdt-resolver` | resolver | Core algorithms (no protobuf deps) |
| `crdt-data` | data | Wire-generated data classes |
| `crdt-wire` | wire | Wire CRDT implementation |
| `crdt-protoc` | protoc | Protoc CRDT implementation |
| `crdt-protoc-data` | protoc-data | Protoc-generated data classes |

**Group ID:** `co.atoms.protobuf.crdt`

#### Maven Central Configuration

Publishing credentials are provided via environment variables (set as GitHub Actions secrets):

| Variable | Description |
|----------|-------------|
| `MAVEN_CENTRAL_USERNAME` | Sonatype OSSRH username |
| `MAVEN_CENTRAL_PASSWORD` | Sonatype OSSRH password/token |
| `GPG_SIGNING_KEY` | ASCII-armored GPG private key |
| `GPG_SIGNING_KEY_PASSWORD` | Passphrase for the GPG key |

For local publishing, credentials can also be set in `~/.gradle/gradle.properties`:
```properties
mavenCentralUsername=your-username
mavenCentralPassword=your-token
gpgSigningKey=your-ascii-armored-key
gpgSigningKeyPassword=your-passphrase
```

#### Consuming from Gradle (Android Projects)

```kotlin
// In settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// In app/build.gradle.kts
dependencies {
    // For Android/Kotlin projects
    implementation("co.atoms.protobuf.crdt:crdt-wire:1.0.0")
    implementation("co.atoms.protobuf.crdt:crdt-data:1.0.0")
}
```

#### Consuming from Bazel (Java Backend)

```python
# In WORKSPACE
load("@rules_jvm_external//:defs.bzl", "maven_install")

maven_install(
    artifacts = [
        "co.atoms.protobuf.crdt:crdt-protoc:1.0.0",
        "co.atoms.protobuf.crdt:crdt-protoc-data:1.0.0",
        "co.atoms.protobuf.crdt:crdt-resolver:1.0.0",
    ],
    repositories = [
        "https://repo1.maven.org/maven2",
    ],
)

# In BUILD
java_library(
    name = "my_service",
    srcs = ["MyService.java"],
    deps = [
        "@maven//:co_atoms_protobuf_crdt_crdt_protoc",
        "@maven//:co_atoms_protobuf_crdt_crdt_protoc_data",
    ],
)
```

## Architecture Principles

### Separated Version Architecture

The critical architectural insight is complete separation between version management and business data:

- **Data Layer**: Clean protobuf domain objects (e.g., `Order { customer: String, status: Enum }`)
- **Version Layer**: Parallel `VersionNode` tree tracking field-level modification timestamps
- **Benefit**: O(1) direct field access without wrapper objects or marshalling overhead

Traditional CRDT libraries embed versions within values (e.g., `LWWRegister<String>`), creating O(F x D) overhead for F fields at depth D. This library achieves O(m) overhead where m = modified field count.

### Field-Level Version Granularity

Each protobuf field tracks its own version independently, enabling surgical conflict resolution. When two devices modify different fields and sync, both changes merge correctly rather than one device's entire message overwriting the other's.

**Version Structure:**
- Component 0: Network-synchronized timestamp (primary ordering)
- Component 1: Random actor identifier (deterministic tie-breaking)
- Comparison: Lexicographic comparison provides deterministic LWW semantics

### Dual Resolution Paths

The library separates local writes from incoming conflict resolution:

- **Local Writes** (`MessageLocalResolver`): Apply user-initiated changes with new version, optimized for UI responsiveness. Version provided by caller, applied only to modified fields. Returns boolean indicating if value changed.

- **Incoming Resolution** (`MessageIncomingResolver`): Merge state from another device with full version history. Both sides provide complete `VersionNode` structures. Returns `ResolutionStrategy` enum (NO_CHANGE, LOCAL, INCOMING, MERGED_VALUES).

### Collection Strategies

**Maps**: Per-key version tracking with recursive value resolution. Tracks versions at both map level (creation/deletion) and per-key level (individual entries). Merge uses union of keys with per-key LWW conflict resolution.

**Identity-Based Lists** (`RepeatedIdResolver`): Transforms lists to maps internally using caller-provided key extraction. Enables element-level conflict resolution within lists. Suitable for entities with stable identities (e.g., order items).

**Position-Based Lists** (`RepeatedResolver`): Last-write-wins for entire list. No element-level tracking. Suitable for small, frequently rewritten collections.

**Tombstone Cleanup**: Maps and ID-based lists support configurable retention policies via protobuf field options (`crdt_max_tombstones`, `crdt_tombstone_ttl`) to prevent unbounded growth of deletion markers.

## Code Conventions

### Module-Specific Patterns

**resolver/**: Pure Kotlin with generic interfaces. No protobuf dependencies. Uses interface-based design for maximum reusability (`CrdtResolver`, `MessageFieldDescriptor`, `VersionNodeAdapter`).

**wire/**: Kotlin with Wire protobuf. Uses annotations (`@WireField`) for field introspection. Package structure: `co.atoms.protobuf.crdt.wire` with internal implementation classes in `.internal` subpackage.

**protoc/**: Kotlin/Java with Google protobuf. Uses descriptor reflection (`FieldDescriptor`, `Descriptor`). Package structure: `co.atoms.protobuf.crdt.protoc`.

**data/**: Proto schema definitions in `src/main/proto/co/atoms/protobuf/crdt/data/`. Follow proto3 syntax.

**wire-data/**: Wire-generated classes in `src/main/kotlin/`.

**protoc-data/**: Protoc-generated classes in `src/main/java/`.

### Critical Implementation Details

**Version Node Inheritance**: The `Struct.fields` map only contains entries differing from base version, not every field. Fields without explicit entries inherit the base version from parent `VersionNode.version`. This provides massive memory savings for typical usage where documents have many fields but only few update over time.

**OneOf Field Constraints**: Wire and protoc implementations must enforce protobuf OneOf invariant—only one field in a group can have value. Setting any OneOf field automatically tombstones other fields in same group at version tree level.

**Resolver Caching**: Both `WireCrdtResolverProvider` and `CrdtMessageResolverProvider` use `ConcurrentHashMap` caching to amortize expensive field introspection (annotations or reflection) across operations.

## Schema Evolution

The library uses proto3 with careful evolution strategy for cross-device compatibility:

- Always add new fields as optional
- Reserve deleted field numbers to prevent reuse
- Maintain backward/forward compatibility for version heterogeneity across devices
- Both Wire and protoc must handle schema changes identically

## Testing Strategy

Tests are organized by module:

- **resolver/test**: Unit tests for core algorithms (JUnit Jupiter, MockK)
- **wire/test**: Wire-specific integration tests with test message definitions
- **protoc/test**: Protoc-specific integration tests
- **Cross-platform compatibility tests**: Verify Wire and protoc produce byte-identical serialization

## Performance Characteristics

**Complexity Analysis:**
- Field read: O(1) - Direct protobuf field access
- Field write: O(1) - Single field version update
- Message merge: O(m) where m = modified fields
- Map merge: O(k) where k = distinct keys
- List merge (ID-based): O(n) where n = list size

**Production Metrics:**
- ~90% reduction in DB operation latency vs commercial CRDT solutions (Ditto) and initial implementation
- Optimized for low-end Android devices (min SDK 24)
- Tested in production environments with network partitions

## Design Rationale

**Why Protocol Buffers?**
- Forward/backward compatibility for version heterogeneity
- Type safety prevents field type conflicts
- Cross-platform consistency (mobile + backend)
- Efficient binary encoding for bandwidth constraints

**Why LWW Strategy?**
- Intuitive: Recent changes align with real-world expectations
- Recoverable: Undesirable resolutions correctable through business processes
- Explainable: Non-technical staff can understand outcomes
- Sufficient: Most conflicts naturally resolved by recency

**Why Dual Implementations (Wire + Protoc)?**
- Wire provides superior Kotlin ergonomics for Android
- Protoc is standard for Java backend services
- Maintaining both avoids forcing single library across diverse environments
- Shared resolution logic ensures identical conflict semantics

## Related Documentation

Each module has detailed README.md files:

- `data/README.md` - Version architecture and data structures
- `resolver/README.md` - Comprehensive conflict resolution algorithms (440+ lines)
- `wire/README.md` - Wire-specific implementation details
- `protoc/README.md` - Protoc-specific implementation details
- `protoc-data/README.md` - Cross-platform data format compatibility
