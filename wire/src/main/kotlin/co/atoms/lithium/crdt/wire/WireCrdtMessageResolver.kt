package co.atoms.lithium.crdt.wire

import co.atoms.lithium.crdt.data.Actors
import co.atoms.lithium.crdt.data.PathComponent
import co.atoms.lithium.crdt.data.Version
import co.atoms.lithium.crdt.data.VersionNode
import co.atoms.lithium.crdt.resolver.CrdtMessageResolver

interface WireCrdtMessageResolver<M> : CrdtMessageResolver<M, VersionNode, Version, PathComponent, Actors>
