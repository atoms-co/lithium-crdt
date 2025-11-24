package com.css.internal.shared.storage.crdt.wire.internal

import com.css.internal.shared.storage.crdt.data.PathComponent
import com.css.internal.shared.storage.crdt.data.Version
import com.css.internal.shared.storage.crdt.data.VersionNode
import com.css.internal.shared.storage.crdt.resolver.version.VersionTreeResolver

internal object WireVersionTreeResolver :
    VersionTreeResolver<VersionNode, Version, PathComponent>,
    WireVersionResolver,
    WireVersionNodeAdapter,
    WirePathComponentAdapter
