package co.atoms.lithium.crdt.protoc

import co.atoms.lithium.crdt.data.PathComponent
import co.atoms.lithium.crdt.data.Version
import co.atoms.lithium.crdt.data.VersionNode
import co.atoms.lithium.crdt.resolver.version.VersionTreeResolver

internal object ProtocVersionTreeResolver :
    VersionTreeResolver<VersionNode, Version, PathComponent>,
    ProtocVersionNodeAdapter,
    ProtocVersionResolver,
    ProtocPathComponentAdapter
