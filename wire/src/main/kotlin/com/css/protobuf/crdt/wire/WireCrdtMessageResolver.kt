package com.css.protobuf.crdt.wire

import com.css.protobuf.crdt.data.Actors
import com.css.protobuf.crdt.data.PathComponent
import com.css.protobuf.crdt.data.Version
import com.css.protobuf.crdt.data.VersionNode
import com.css.protobuf.crdt.resolver.CrdtMessageResolver

interface WireCrdtMessageResolver<M> : CrdtMessageResolver<M, VersionNode, Version, PathComponent, Actors>
