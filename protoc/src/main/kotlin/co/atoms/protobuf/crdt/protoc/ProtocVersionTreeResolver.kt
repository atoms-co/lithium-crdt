package co.atoms.protobuf.crdt.protoc

import co.atoms.protobuf.crdt.data.PathComponent
import co.atoms.protobuf.crdt.data.Version
import co.atoms.protobuf.crdt.data.VersionNode
import co.atoms.protobuf.crdt.resolver.version.VersionTreeResolver

internal object ProtocVersionTreeResolver :
    VersionTreeResolver<VersionNode, Version, PathComponent>,
    ProtocVersionNodeAdapter,
    ProtocVersionResolver,
    ProtocPathComponentAdapter
