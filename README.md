<div align="center">
  
# lithium-crdt

**A lightweight structured CRDT implementation with near-zero memory overhead**


[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/co.atoms.lithium.crdt/crdt-resolver.svg)](https://search.maven.org/search?q=g:co.atoms.lithium.crdt)
[![Build Status](https://github.com/atoms-co/lithium-crdt/workflows/CI/badge.svg)](https://github.com/atoms-co/lithium-crdt/actions)

[Blog Post](https://techblog.atoms.co/p/protocol-buffer-crdts-outperforming) · [Documentation](#documentation) · [Try It](#try-it-interactively)

</div>

---

## Why?

**Traditional CRDT implementation wrap your data field-by-field**. You define a `User` and they give you back `LWWRegister<LWWMap<String, LWWRegister<String>>>`. Every field access becomes a traversal and every field read requires unwrapping. 

**lithium-crdt keeps your data clean**. You define `User` in protobuf, you work with `User` objects directly. The version metadata lives in a parallel tree that's only consulted during sync. For us, this approach lead to [~90% reduction in db latency and 4x reduction in memory overhead](https://techblog.atoms.co/p/protocol-buffer-crdts-outperforming) compared to traditional CRDT libraries in production.

```kotlin
// Your proto stays clean
message Order {
  string customer = 1;
  OrderStatus status = 2;
  repeated Item items = 3;
}

// Use it directly, no wrappers, no unwrapping
val order = Order(customer = "Alice", status = PENDING)

// Sync just works
val merged = resolver.resolveConflict(
    localOrder, 
    localNode,        // Your VersionNode for this order
    incomingOrder, 
    incomingNode      // Their VersionNode for this order
)
```



## Features

<div style="width: 100%">

| Feature | Description | Implementation |
|---------|-------------|----------------|
| **O(1) field access** | Read protobuf fields directly, no traversal or unwrapping | Direct field access on generated protobuf classes |
| **O(m) space overhead** | Only modified fields are version tracked  | [`VersionNode`](data/src/main/proto/co/atoms/lithium/crdt/data/version_node.proto) parallel tree |
| **Field-level merging** | Different fields edited on different devices merge correctly | [Conflict resolution algorithms](resolver/README.md) |
| **Type safety** | Compile-time checking through protobuf schemas | Generated protobuf types |
| **Multi-platform** | Android (Wire) and JVM (Protoc) implementations | [`wire/`](wire/) and [`protoc/`](protoc/) |
| **Delta sync** | Efficient incremental synchronization | [`VersionChange`](data/src/main/proto/co/atoms/lithium/crdt/data/version_change.proto) tracking |

</div>

## Quick Start

### Installation

**Gradle (Kotlin/Android)**
```kotlin
dependencies {
    implementation("co.atoms.lithium.crdt:crdt-wire:1.0.0")
}
```

**Gradle (Java/JVM)**
```kotlin
dependencies {
    implementation("co.atoms.lithium.crdt:crdt-protoc:1.0.0")
}
```

**Maven**
```xml
<dependency>
    <groupId>co.atoms.lithium.crdt</groupId>
    <artifactId>crdt-protoc</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Usage

Define your protobuf message (you probably already have this):

```protobuf
syntax = "proto3";

message Order {
  string customer = 1;
  OrderStatus status = 2;
  int64 total = 3;
}
```

Then use it in your code:

```kotlin
// Create a resolver for your message type
val resolverProvider = WireCrdtResolverProvider() // or CrdtMessageResolverProvider() for Protoc
val orderResolver = resolverProvider.getResolver(Order::class)

// Load your order and its version metadata from the database
val (order, versionNode) = db.loadOrder(orderId)

// Apply local changes
val (updatedOrder, updatedVersionNode, _) = orderResolver.applyLocalWrite(
    currentValue = order,
    currentNode = versionNode,  // The parallel version tree for this order
    actors = actors,
    newValue = order.copy(status = COMPLETED),
    timestamp = clock.now()
)
db.save(updatedOrder, updatedVersionNode)

// When receiving changes from another device, resolve conflicts
val (mergedOrder, mergedNode, strategy) = orderResolver.resolveConflict(
    localValue = localOrder,
    localNode = localVersionNode,     // Your version metadata
    incomingValue = incomingOrder,
    incomingNode = incomingVersionNode // Their version metadata
)

when (strategy) {
    NO_CHANGE -> { /* both sides identical */ }
    LOCAL -> { /* local was newer */ }
    INCOMING -> { /* incoming was newer */ }
    MERGED_VALUES -> { /* different fields merged from both sides */ }
}
```

## Try It Interactively

Run our interactive demo app. It simulates three devices editing the same document, syncing independently, and merging only the fields that changed.

```bash
git clone https://github.com/atoms-co/lithium-crdt.git
cd lithium-crdt
./gradlew :examples:interactive-demo:run
```

What you'll see right away:

- **Different fields edited on different nodes both survive sync**
- **The same field resolves deterministically by version**
- **The parallel `VersionNode` tree** that makes field-level merging possible
- **Counter fields** that add across actors instead of last-write-wins

See the full demo guide in [`examples/interactive-demo/README.md`](examples/interactive-demo/README.md).

## How It Works

Two parallel structures:

```
Your Protobuf Message              Version Metadata
┌─────────────────────┐            ┌─────────────────────┐
│ Order               │            │ VersionNode         │
│   customer: "Alice" │ ◄────────► │   field[1]: v1.2    │
│   status: PENDING   │            │   field[2]: v1.0    │
│   total: 100        │            │   field[3]: v1.1    │
└─────────────────────┘            └─────────────────────┘
```

When two devices edit different fields:

```
Device A: order.customer = "Bob"     @ timestamp 2
Device B: order.status = COMPLETED   @ timestamp 3

After sync on both devices:
  customer = "Bob"      (Device A won on field 1, timestamp 2)
  status = COMPLETED    (Device B won on field 2, timestamp 3)
  total = 100           (unchanged)
```

Both changes survive. No last-write-wins on the entire message. Just the fields that actually changed.

**[Read the deep dive →](https://techblog.atoms.co/p/protocol-buffer-crdts-outperforming)**

## Documentation

- **[Conflict Resolution Algorithms](resolver/README.md)** - How field-level LWW, maps, and lists are resolved
- **[Version Architecture](data/README.md)** - The version tree structure explained
- **[Wire Implementation](wire/README.md)** - Android/Kotlin details
- **[Protoc Implementation](protoc/README.md)** - Java/JVM backend details

## Contributing

Contributions welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

**Found a bug?** [Open an issue →](https://github.com/atoms-co/lithium-crdt/issues)

## License

Apache License 2.0 - See [LICENSE](LICENSE)

Built with [Protocol Buffers](https://protobuf.dev/) and [Wire by Square](https://square.github.io/wire/)

---

**Questions?** Open an [issue](https://github.com/atoms-co/lithium-crdt/issues) or [discussion](https://github.com/atoms-co/lithium-crdt/discussions). We'd love to hear how you're using lithium-crdt!
