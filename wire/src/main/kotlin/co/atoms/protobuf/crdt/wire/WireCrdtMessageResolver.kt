package co.atoms.protobuf.crdt.wire

import co.atoms.protobuf.crdt.data.Actors
import co.atoms.protobuf.crdt.data.PathComponent
import co.atoms.protobuf.crdt.data.Version
import co.atoms.protobuf.crdt.data.VersionNode
import co.atoms.protobuf.crdt.resolver.CrdtMessageResolver

interface WireCrdtMessageResolver<M> : CrdtMessageResolver<M, VersionNode, Version, PathComponent, Actors>
