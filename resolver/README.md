# CRDT Resolver

Field-level CRDT conflict resolution for Protocol Buffer messages enabling leaderless peer-to-peer synchronization across distributed devices without coordination overhead or data duplication.

## Historical Context

### The Distributed Synchronization Problem

Restaurant environments require 3-5 devices (POS terminals, tablets, kiosks) to maintain consistent order state despite:
- Network partitions between devices
- Arbitrary message delays and reordering
- Devices running different app versions
- Need for immediate local updates without coordination

### Traditional Approaches Rejected

**Dedicated On-Site Server**
- Additional hardware per location
- Single point of failure
- Depends on reliable local networking
- Installation and maintenance overhead

**Leader-Based Coordination (Raft, Paxos)**
- Problematic with small device counts (3-5 nodes)
- Failed leader elections with low node counts
- Split-brain scenarios during network partitions
- Orphaned devices unable to rejoin cluster
- Extended periods without consensus during failures

**Version Wrapped CRDTs (Automerge, Yjs, Ditto)**
- Maintain separate data representation from application types
- Require bidirectional mapping layers
- Redundant storage of same data
- Full traversal required for every operation
- Complex adapters and abstractions
- For a protobuf message with F fields at depth D: **O(F × D) memory overhead** and **O(F) computational complexity per read/write**

### Evolution to Current Solution

**Initial Implementation (RFC - CrdtDB & Local Sync Order Management)**
- Emulated Ditto's strategy of encapsulating field data and version data together
- A/B tested against Ditto behind an abstraction layer
- Both showed poor performance in production

**Current Solution**
- Operates directly on protobuf structures with parallel version tree
- **~90% reduction in DB operation latency** vs initial implementation and Ditto
- O(1) access to application data
- O(m) version tracking overhead where m = modified fields (not total field count)
- Zero data duplication or mapping layers

### Cross-Platform Strategy: Wire and Protoc Implementations

The resolver module provides platform-agnostic conflict resolution algorithms, with two implementations adapting these algorithms to different protobuf ecosystems:

**Wire Implementation (crdt/wire):**
- Targets Kotlin/Android development with superior ergonomics
- Uses `@WireField` annotations for compile-time metadata
- Zero reflection overhead for field introspection
- Optimal for mobile performance

**Protoc Implementation (crdt/protoc):**
- Targets Java backend services and cross-platform compatibility
- Uses `getDescriptor()` for runtime field introspection
- Standard protobuf library used by most Java services
- Enables backend integration without forcing Wire adoption

**Shared Resolution Logic:**
Both implementations delegate to `MessageFieldResolverProvider` in this module, ensuring identical conflict resolution semantics across all platforms. The only difference is how field metadata is extracted (annotations vs descriptors).

**Why Not a Single Implementation?**
- Wire's Kotlin focus makes it less ergonomic for pure Java environments
- Backend services already standardized on Google protobuf
- Forcing a single library would create adoption friction
- Maintaining both provides flexibility while sharing core algorithms

### Design Rationale

**Protocol Buffers as Data Layer**
- Forward/backward compatibility for version heterogeneity across devices
- Type safety prevents field type conflicts through explicit schema
- Cross-platform consistency (mobile clients + backend services)
- Efficient binary encoding minimizes bandwidth in poor network conditions

**Last Write Wins (LWW) Merge Strategy**
- Intuitive: Recent changes align with real-world expectations
- Recoverable: Undesirable resolutions correctable through business processes
- Explainable: Non-technical staff can understand conflict outcomes
- Sufficient: Most order management conflicts naturally resolved by recency

**Core Innovation**
Eliminate architectural bottleneck by operating directly on protobuf structures with parallel version tree, avoiding all mapping, duplication, and container overhead present in traditional CRDT libraries.

---

## Architecture Overview

```mermaid
graph TB
    subgraph "Application Layer"
        A[Protobuf Message] 
        B[Local Write]
        C[Incoming Sync]
    end
    
    subgraph "Resolver Layer"
        D[CrdtResolver Interface]
        E[MessageLocalResolver]
        F[MessageIncomingResolver]
        G[VersionTreeResolver]
    end
    
    subgraph "Field Resolution"
        H[AnyValueResolver]
        I[MapResolver<K,V>]
        J[RepeatedResolver]
        K[RepeatedIdResolver]
    end
    
    subgraph "Version Tracking"
        L[VersionNode Tree]
        M[Version Comparator]
        N[ResolutionStrategy]
    end
    
    B --> E
    C --> F
    E --> D
    F --> D
    D --> G
    
    E --> H
    E --> I
    E --> J
    E --> K
    
    F --> H
    F --> I
    F --> J
    F --> K
    
    G --> L
    G --> M
    F --> N
    
    L -.parallel structure.-> A
    
    style A fill:#e1f5ff
    style L fill:#fff4e1
```

### Component Responsibilities

**CrdtResolver Interface**
- Unified interface combining local writes and conflict resolution
- Generic over value type `T`, version node type `N`, and version type `V`
- Splits into `CrdtLocalResolver` and `CrdtIncomingResolver`

**MessageLocalResolver**
- Processes local writes field-by-field
- Updates versions only for changed fields (unchanged fields retain versions)
- Handles document creation, updates, and deletion (tombstones)
- Enforces OneOf field constraints (only one field per group has value)

**MessageIncomingResolver**
- Resolves conflicts between local and incoming state
- Merges both values and their version structures recursively
- Returns `ResolutionStrategy` indicating how conflict was resolved

**Field-Specific Resolvers**
- `AnyValueResolver`: Primitive scalar values (LWW by version comparison)
- `MapResolver<K,V>`: Per-key version tracking with recursive value resolution
- `RepeatedResolver`: Position-based list tracking
- `RepeatedIdResolver`: Identity-based list tracking (transforms list to map internally)

**VersionTreeResolver**
- Provides version node manipulation and comparison
- Maintains parallel tree structure mirroring protobuf field hierarchy
- Adapter pattern for version implementation flexibility

---

## Key Concepts

### 1. Parallel Version Tree Structure

The library maintains a **parallel tree** of version nodes matching the protobuf message structure without duplicating field values.

```mermaid
graph TB
    subgraph "Protobuf Message"
        M[Order Message]
        M1[customer_id: String]
        M2[items: List]
        M3[status: Enum]
        M --> M1
        M --> M2
        M --> M3
    end
    
    subgraph "Version Node Tree"
        V[VersionNode]
        V1[version: V1]
        V2[Map of field versions]
        V3[version: V2]
        V --> V1
        V --> V2
        V --> V3
    end
    
    M1 -.field 1.-> V1
    M2 -.field 2.-> V2
    M3 -.field 3.-> V3
    
    style M fill:#e1f5ff
    style V fill:#fff4e1
```

**Benefits:**
- **O(1) field access**: Read protobuf fields directly without traversal
- **O(m) space overhead**: Only modified fields have version nodes (m = modified field count)
- **No marshalling**: Zero serialization cost between CRDT layer and application
- **Type safety**: Compile-time checking through protobuf generated code

### 2. Field-Level Version Granularity

Each protobuf field tracks its own version independently, enabling surgical conflict resolution.

```mermaid
sequenceDiagram
    participant D1 as Device 1
    participant D2 as Device 2
    
    Note over D1,D2: Initial State: Order{customer="Alice", status=PENDING} @ V0
    
    D1->>D1: Update customer="Bob" @ V1
    D2->>D2: Update status=COMPLETED @ V2
    
    Note over D1: Order{customer="Bob"@V1, status=PENDING@V0}
    Note over D2: Order{customer="Alice"@V0, status=COMPLETED@V2}
    
    D1->>D2: Sync to Device 2
    
    Note over D2: Merge:<br/>customer: V1 > V0 → "Bob"<br/>status: V2 > V0 → COMPLETED
    Note over D2: Result: Order{customer="Bob"@V1, status=COMPLETED@V2}
```

**Without field-level versions**, the entire message would be compared, causing one device's changes to completely overwrite the other's. Field-level tracking merges both updates correctly.

### 3. Resolution Strategy Taxonomy

The `ResolutionStrategy` enum captures how conflicts were resolved:
- **NO_CHANGE** - Values identical, no update needed
- **LOCAL** - Local version newer, kept current value
- **INCOMING** - Incoming version newer, adopted incoming value
- **MERGED_VALUES** - Set union, counter max, field-level merge, etc.

**Strategy Composition:**
Strategies compose to track mixed resolutions across nested structures:
- Identical strategies remain unchanged
- Different non-merged strategies become MERGED_VALUES
- MERGED_VALUES is absorbing (once merged, always merged)

This composition enables tracking whether a complex nested message resolution involved any actual merging or was purely one-sided.

### 4. Local Write vs Incoming Resolution

The module splits write operations into two distinct paths:

**Local Writes**
- Apply user-initiated changes with a new version
- Version provided by caller, applied to modified fields
- Returns boolean indicating whether value changed
- Optimized for quick acknowledgment of UI changes
- Simpler logic since no conflict resolution needed

**Incoming Resolution**
- Merge state from another device with full version history
- Both sides bring complete VersionNode structures
- Returns ResolutionStrategy indicating merge outcome
- Handles complex recursive merging of nested structures
- Must be deterministic and commutative across all devices

This separation enables different optimization paths, clearer semantic boundaries, and type-safe return values appropriate to each operation's purpose.

### 5. Map and Collection Strategies

**Map Resolver** (per-key version tracking)
- Maps track versions at both map level and per-key level
- Map-level version: Creation/deletion timestamp of entire map
- Key-level versions: Individual entry updates tracked independently
- Merge: Union of keys, per-key LWW conflict resolution
- Tombstones: Keys with null values preserve deletion events
- Type-safe key resolvers for Boolean, Int, Long, and String map keys

**Tombstone Cleanup** (configurable retention policies)
- **Purpose:** Prevent unbounded growth of deletion markers in maps and ID-based lists
- **TTL-based cleanup:** Remove tombstones outside the time window `[max_version - ttl, max_version]`
- **Count-based cleanup:** When exceeding `maxTombstones`, remove oldest tombstones (FIFO)
- **Live data versioning:** Update versions of live data to stay within TTL window, preventing incorrect deletion
- **Configuration options:**
  - `crdt_max_tombstones` - Maximum number of tombstones to retain (default: 1024)
  - `crdt_tombstone_ttl` - Time-to-live in milliseconds (default: null, no TTL)
- **Cleanup triggers:** Performed during local writes when creating new tombstones
- **Guarantees:** Cleanup maintains CRDT convergence properties by preserving recent deletion history

**RepeatedIdResolver** (identity-based lists)
- Transforms lists to maps internally using caller-provided key extraction function
- Delegates to MapResolver for CRDT semantics (per-item versioning)
- Converts back to list for application consumption
- Preserves item order by maintaining insertion sequence
- Enables element-level conflict resolution within lists
- Suitable for lists of entities with stable identities (e.g., order items with IDs)

**RepeatedResolver** (position-based lists)
- Simplest strategy for lists without stable identities
- Last write wins for entire list (treats list as atomic value)
- No element-level version tracking
- Suitable for small, frequently rewritten collections where element identity doesn't matter


---

## Integration Architecture

### Resolver Creation Pipeline

The module follows a layered initialization pattern:

1. **Version Type Definition** - Define version representation (e.g., timestamp + device ID hybrid logical clocks)
2. **Version Comparator** - Implement comparison logic with tie-breaking for deterministic ordering
3. **Version Node Structure** - Define tree structure holding versions and field-level children
4. **Version Node Adapter** - Bridge between version node implementation and resolver algorithms
5. **Version Tree Resolver** - Combines comparator and adapter, provides tree manipulation utilities
6. **Message-Specific Resolver** - Binds protobuf message types with field descriptors

Each layer is generic and reusable, allowing different version implementations (vector clocks, Lamport timestamps, hybrid logical clocks) without changing resolution logic.

### Local Write Processing

When applying local writes, the resolver:
- Compares current and new values field-by-field
- Updates version nodes only for changed fields (unchanged fields retain existing versions)
- Returns a boolean indicating whether any change occurred
- Handles document creation (new version node), updates (field-level version updates), and deletion (tombstone with version)

The version provided by the caller is applied to modified fields, establishing causality for future conflict resolution.

### Incoming Conflict Resolution

When merging incoming state from another device, the resolver:
- Recursively compares local and incoming version nodes
- Applies field-level last-write-wins based on version comparison
- Merges collection keys (maps) or entire collections (lists) based on strategy
- Returns a `ResolutionStrategy` enum indicating the merge outcome (NO_CHANGE, LOCAL, INCOMING, or MERGED_VALUES)

Both sides provide complete version node trees, enabling deep recursive merge of nested messages and collections.

### Field Descriptor System

The `MessageFieldDescriptor` interface provides runtime introspection of protobuf fields:
- Field number (tag) for version node storage
- Value type classification (required, optional, or nested message)
- Collection type (map, repeated, or scalar)
- Map key type for type-safe resolver selection
- OneOf group membership for mutual exclusion enforcement
- Field accessors bridging generated protobuf code to generic resolvers

The `MessageFieldResolverProvider` factory creates appropriate field-specific resolvers based on descriptor metadata, selecting from scalar, map, list, or nested message strategies.

---

## Performance Characteristics

### Complexity Analysis

| Operation | Time | Space |
|-----------|------|-------|
| Field read | O(1) | - |
| Field write | O(1) | O(1) per modified field |
| Message merge | O(m) | O(m) where m = modified fields |
| Map merge | O(k) | O(k) where k = distinct keys |
| List merge (ID-based) | O(n) | O(n) where n = list size |

### Memory Overhead

**Traditional CRDT libraries:**
- O(F × D) for F fields at depth D
- Duplicate storage of values in CRDT representation
- Marshalling cost on every access

**This library:**
- O(m) where m = modified field count
- Zero value duplication (version tree is parallel structure)
- Zero marshalling (direct protobuf access)

### Production Metrics

- **~90% reduction** in database operation latency vs Ditto and initial implementation
- Tested on low-end Android devices (min SDK 24)
- Optimized for large document sizes typical in order management
- Handles network partitions and message reordering in production restaurant environments

---

## Related Work

| Solution | Approach | Limitations for Our Use Case |
|----------|----------|------------------------------|
| **Ditto** | Commercial distributed database with CRDT support | Proprietary, licensing costs, custom storage layer, performance issues on low-end devices, extremely large native binary |
| **Automerge** | Rust CRDT library with rich data types | Maintains separate document representation requiring mapping, no protobuf integration |
| **Yjs** | JavaScript CRDT for collaborative editing | Custom data structures, requires JavaScript runtime |
| **CouchDB/PouchDB** | Document databases with replication | Document-level granularity (not field-level), requires custom conflict handlers |
| **Operational Transformation** | Alternative to CRDTs | Requires central server for operation ordering, not suitable for peer-to-peer |

**Common problems overcome:**
- No schema enforcement
- No compile-time type checking
- No forward/backward compatibility guarantees
- Overhead of parsing JSON or custom data models on every access
- Data duplication between CRDT layer and application types

---

## Module Dependencies

This module has **zero external dependencies** beyond Kotlin standard library, enabling:
- Use in any Kotlin/JVM project
- Minimal binary size impact
- No transitive dependency conflicts
- Fast compilation times

Test dependencies only:
- JUnit Jupiter for testing
- Kotlin Test
- MockK for mocking

