# protobuf-crdt

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/co.atoms.protobuf.crdt/crdt-resolver.svg)](https://search.maven.org/search?q=g:co.atoms.protobuf.crdt)
[![Build Status](https://github.com/atoms-co/protobuf-crdt/workflows/CI/badge.svg)](https://github.com/atoms-co/protobuf-crdt/actions)

A Conflict-Free Replicated Data Type (CRDT) library for Protocol Buffer messages, enabling distributed synchronization across devices without coordination.

## Why protobuf-crdt?

Traditional CRDT libraries wrap your data in special containers, creating mapping overhead and data duplication. **protobuf-crdt** takes a different approach: it maintains a parallel version tree alongside your existing protobuf messages, giving you:

- **O(1) field access** - Read protobuf fields directly, no traversal or unwrapping
- **O(m) space overhead** - Only modified fields have version metadata (not all fields)
- **Field-level conflict resolution** - Two devices editing different fields merge correctly
- **Zero data duplication** - Version tree is separate from your business data
- **Type safety** - Compile-time checking through protobuf generated code

This architecture achieved **~90% reduction in database operation latency** compared to traditional CRDT approaches in production environments.

## Features

- **Field-Level Last-Write-Wins (LWW)** - Each field tracks its own version independently
- **Dual Platform Support** - Wire (Android/Kotlin) and Protoc (Java/backend) implementations
- **Collection Strategies** - Per-key map tracking, identity-based lists, or atomic list replacement
- **Delta Synchronization** - Efficient incremental sync with automatic fallback to full-state
- **Tombstone Cleanup** - Configurable retention policies prevent unbounded growth
- **Schema Evolution** - Forward/backward compatibility through protobuf semantics

## Quick Start

### Installation

**Gradle (Android/Kotlin)**

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("co.atoms.protobuf.crdt:crdt-wire:1.0.0")
    implementation("co.atoms.protobuf.crdt:crdt-data:1.0.0")
}
```

**Gradle (Java Backend)**

```kotlin
dependencies {
    implementation("co.atoms.protobuf.crdt:crdt-protoc:1.0.0")
    implementation("co.atoms.protobuf.crdt:crdt-protoc-data:1.0.0")
}
```

**Maven**

```xml
<dependency>
    <groupId>co.atoms.protobuf.crdt</groupId>
    <artifactId>crdt-protoc</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Basic Usage

```kotlin
// 1. Create a resolver for your protobuf message type
val resolverProvider = WireCrdtResolverProvider()
val orderResolver = resolverProvider.getResolver(Order::class)

// 2. Apply a local write
val (updatedOrder, updatedVersionNode, changes) = orderResolver.applyLocalWrite(
    currentValue = order,
    currentNode = versionNode,
    actors = actors,
    newValue = order.copy(status = OrderStatus.COMPLETED),
    timestamp = clock.now()
)

// 3. Resolve conflicts from another device
val (resolvedOrder, resolvedNode, strategy) = orderResolver.resolveConflict(
    localValue = localOrder,
    localNode = localVersionNode,
    incomingValue = incomingOrder,
    incomingNode = incomingVersionNode
)

// strategy tells you how the conflict was resolved:
// - NO_CHANGE: Values were identical
// - LOCAL: Local version was newer
// - INCOMING: Incoming version was newer
// - MERGED_VALUES: Different fields merged from both sides
```

## How It Works

The library maintains a parallel tree of `VersionNode` objects that mirror your protobuf message structure:

```
Protobuf Message                    Version Tree
┌─────────────────────┐             ┌─────────────────────┐
│ Order               │             │ VersionNode         │
│   customer: "Alice" │ ◄─────────► │   field[1]: v1.2    │
│   status: PENDING   │             │   field[2]: v1.0    │
│   items: [...]      │             │   field[3]: {...}   │
└─────────────────────┘             └─────────────────────┘
```

When two devices modify different fields and sync:

```
Device A: order.customer = "Bob"     @ version 2
Device B: order.status = COMPLETED   @ version 3

After sync on both devices:
  order.customer = "Bob"      (from Device A, version 2)
  order.status = COMPLETED    (from Device B, version 3)
```

Field-level versioning means both changes are preserved, rather than one device's entire message overwriting the other's.

## Modules

| Module | Artifact | Description |
|--------|----------|-------------|
| `resolver` | `crdt-resolver` | Platform-agnostic conflict resolution algorithms |
| `wire-data` | `crdt-data` | Wire-generated version data classes |
| `wire` | `crdt-wire` | Wire CRDT implementation for Android/Kotlin |
| `protoc-data` | `crdt-protoc-data` | Protoc-generated version data classes |
| `protoc` | `crdt-protoc` | Protoc CRDT implementation for Java backends |

## Documentation

- **[Resolver Architecture](resolver/README.md)** - Comprehensive documentation of conflict resolution algorithms, version semantics, and collection strategies
- **[Data Structures](data/README.md)** - Version node architecture and protobuf schema definitions
- **[Wire Implementation](wire/README.md)** - Wire-specific implementation details
- **[Protoc Implementation](protoc/README.md)** - Protoc-specific implementation details

## Requirements

- Java 17+ (LTS)
- Kotlin 2.0+
- Gradle 9.0+ (included via wrapper)

## Building from Source

```bash
# Clone the repository
git clone https://github.com/atoms-co/protobuf-crdt.git
cd protobuf-crdt

# Build all modules
./gradlew build

# Run tests
./gradlew test

# Install to local Maven repository
./gradlew publishToMavenLocal
```

### Project Structure

```
protobuf-crdt/
├── resolver/       # Core algorithms (no protobuf deps)
├── data/           # Proto schema definitions
├── wire-data/      # Wire-generated data classes
├── wire/           # Wire CRDT implementation
├── protoc-data/    # Protoc-generated data classes
├── protoc/         # Protoc CRDT implementation
└── fixtures/       # Shared test utilities
```

## Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details.

### Development Workflow

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Run tests (`./gradlew test`)
5. Commit your changes (`git commit -m 'Add amazing feature'`)
6. Push to the branch (`git push origin feature/amazing-feature`)
7. Open a Pull Request

### Code Style

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- All public APIs should have KDoc documentation
- Tests are required for new functionality

## Use Cases

This library is particularly well-suited for:

- **Mobile apps** requiring offline-first architecture with eventual consistency
- **Distributed systems** with peer-to-peer synchronization
- **Multi-device applications** where users access data from multiple devices
- **Collaborative editing** scenarios with field-level granularity
- **IoT deployments** with intermittent connectivity

## Performance

| Operation | Complexity |
|-----------|-----------|
| Field read | O(1) |
| Field write | O(1) |
| Message merge | O(m) where m = modified fields |
| Map merge | O(k) where k = distinct keys |

The library has been tested in production on low-end Android devices (min SDK 24) handling network partitions and message reordering.

## Comparison with Alternatives

| Solution | Approach | Trade-offs |
|----------|----------|------------|
| **protobuf-crdt** | Parallel version tree | Requires protobuf, field-level granularity |
| **Automerge** | Embedded CRDT types | Rich data types, separate data representation |
| **Yjs** | CRDT for collaboration | Optimized for text, JavaScript ecosystem |
| **Ditto** | Commercial solution | Full-featured, proprietary |

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Built on [Protocol Buffers](https://protobuf.dev/) by Google
- Wire implementation uses [Square Wire](https://square.github.io/wire/)
- Inspired by research in Conflict-Free Replicated Data Types (CRDTs)

---

**Questions?** Open an issue or start a discussion. We'd love to hear how you're using protobuf-crdt!
