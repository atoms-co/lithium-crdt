package com.css.protobuf.crdt.protoc

import com.css.protobuf.crdt.data.PathComponent
import com.css.protobuf.crdt.data.Version
import com.css.protobuf.crdt.data.VersionNode
import com.css.protobuf.crdt.resolver.version.VersionTreeResolver

internal object ProtocVersionTreeResolver :
    VersionTreeResolver<VersionNode, Version, PathComponent>,
    ProtocVersionNodeAdapter,
    ProtocVersionResolver,
    ProtocPathComponentAdapter
