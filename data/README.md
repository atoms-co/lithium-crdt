# CRDT Data Module

This module contains the Protocol Buffer schema definitions (`.proto` files) for the CRDT library. It serves as the single source of truth for all protobuf message structures used across both Wire and Protoc implementations.

## Purpose

The `data` module publishes a proto JAR artifact containing raw `.proto` files that can be consumed by:

1. **wire-data** - Generates Kotlin classes using Square Wire for Android/Kotlin projects
2. **protoc-data** - Generates Java classes using Google protoc for backend services
3. **External projects** - Can consume the proto definitions directly via Bazel or other build systems

## Schema Files

Located in `src/main/proto/com/css/protobuf/crdt/data/`:

- `actors.proto` - Actor identification for version tracking
- `counter.proto` - Counter data structures
- `document.proto` - Distributed document wrapper
- `merge_options.proto` - Configuration for merge behavior
- `path_component.proto` - Field path representation
- `version.proto` - Version sequence and comparison
- `version_change.proto` - Version change tracking
- `version_count.proto` - Version counting utilities
- `version_node.proto` - Core version tree structure

## Maven Publication

**Group ID:** `com.css.protobuf.crdt`  
**Artifact ID:** `crdt-data`  

### Publishing

```bash
# Publish to local Maven repository
./gradlew :data:publishToMavenLocal

# Publish to Artifactory
./gradlew :data:publish
```

### Consuming in Bazel

```python
load(":rules_proto.bzl", "proto_library")

proto_library(
    name = "my_proto_lib",
    srcs = glob(["*.proto"]),
    deps = [
        "@vendor//:com_css_protobuf_crdt_crdt_data"
    ],
)
```

### Consuming in Gradle

```kotlin
dependencies {
    // Extract and use proto files
    implementation("com.css.protobuf.crdt:crdt-data:1.0.1")
}
```

## Architecture

This module has no code generation or compilation - it simply packages the raw `.proto` files into a JAR for distribution. The actual code generation happens in:

- `wire-data` module (Wire/Kotlin)
- `protoc-data` module (Protoc/Java)

Both modules reference these proto files as their source via:

```kotlin
sourcePath {
    srcDir(project(":data").file("src/main/proto"))
}
```

## Schema Evolution

When modifying proto files:

1. **Never reuse field numbers** - Mark deleted fields as `reserved`
2. **Always add optional fields** - Maintain backward compatibility
3. **Test both implementations** - Ensure Wire and Protoc handle changes identically
4. **Version bump** - Increment version in root `build.gradle.kts`

## Dependencies

This module has **zero dependencies** - it's a pure proto schema distribution package.

