# Keep CRDT protobuf option annotations for Wire runtime reflection
# These annotations are accessed at runtime by the protobuf-crdt library resolvers

# Preserve runtime annotations in bytecode for reflection access
-keepattributes RuntimeVisibleAnnotations

# Keep CRDT annotation interfaces and enums
-keep @interface co.atoms.protobuf.crdt.data.options.** { *; }
-keep enum co.atoms.protobuf.crdt.data.options.** { *; }

# Keep classes annotated with any CRDT option
-keep @co.atoms.protobuf.crdt.data.options.* class * { *; }

# Keep members annotated with CRDT options (preserves $annotations methods)
-keepclassmembers class * {
    @co.atoms.protobuf.crdt.data.options.** *;
}

# Wire runtime - CRDT uses Wire reflection to read @WireField and access adapters
-keep class com.squareup.wire.** { *; }
-keep @interface com.squareup.wire.** { *; }

# Keep Wire message classes and builders - accessed via reflection by createRuntimeMessageAdapter
-keep class * extends com.squareup.wire.Message { *; }
-keep class * extends com.squareup.wire.Message$Builder { *; }

# Keep Wire enum classes - ADAPTER field accessed via reflection by ProtoAdapter.get()
-keep class * implements com.squareup.wire.WireEnum { *; }

# Keep Wire OneOf key classes - accessed via reflection for boxed oneofs
-keep class * extends com.squareup.wire.OneOf$Key { *; }
