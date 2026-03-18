package co.atoms.protobuf.crdt.wire.internal

import co.atoms.protobuf.crdt.data.PathComponent
import co.atoms.protobuf.crdt.data.Version
import co.atoms.protobuf.crdt.data.VersionNode
import co.atoms.protobuf.crdt.resolver.version.VersionTreeResolver

internal object WireVersionTreeResolver :
    VersionTreeResolver<VersionNode, Version, PathComponent>,
    WireVersionResolver,
    WireVersionNodeAdapter,
    WirePathComponentAdapter
