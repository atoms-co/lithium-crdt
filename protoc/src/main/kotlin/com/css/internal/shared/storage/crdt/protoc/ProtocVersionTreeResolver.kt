package com.css.internal.shared.storage.crdt.protoc

import com.css.internal.shared.storage.crdt.data.PathComponent
import com.css.internal.shared.storage.crdt.data.Version
import com.css.internal.shared.storage.crdt.data.VersionNode
import com.css.internal.shared.storage.crdt.resolver.version.VersionTreeResolver

internal object ProtocVersionTreeResolver :
    VersionTreeResolver<VersionNode, Version, PathComponent>,
    ProtocVersionNodeAdapter,
    ProtocVersionResolver,
    ProtocPathComponentAdapter
