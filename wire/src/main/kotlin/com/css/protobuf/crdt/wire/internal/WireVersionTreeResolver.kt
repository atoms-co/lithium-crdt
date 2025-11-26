package com.css.protobuf.crdt.wire.internal

import com.css.protobuf.crdt.data.PathComponent
import com.css.protobuf.crdt.data.Version
import com.css.protobuf.crdt.data.VersionNode
import com.css.protobuf.crdt.resolver.version.VersionTreeResolver

internal object WireVersionTreeResolver :
    VersionTreeResolver<VersionNode, Version, PathComponent>,
    WireVersionResolver,
    WireVersionNodeAdapter,
    WirePathComponentAdapter
