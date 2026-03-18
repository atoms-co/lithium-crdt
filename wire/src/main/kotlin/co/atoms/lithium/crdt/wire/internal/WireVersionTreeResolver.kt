package co.atoms.lithium.crdt.wire.internal

import co.atoms.lithium.crdt.data.PathComponent
import co.atoms.lithium.crdt.data.Version
import co.atoms.lithium.crdt.data.VersionNode
import co.atoms.lithium.crdt.resolver.version.VersionTreeResolver

internal object WireVersionTreeResolver :
    VersionTreeResolver<VersionNode, Version, PathComponent>,
    WireVersionResolver,
    WireVersionNodeAdapter,
    WirePathComponentAdapter
