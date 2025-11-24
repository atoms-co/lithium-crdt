package com.css.internal.shared.storage.crdt.wire

import com.css.internal.shared.storage.crdt.data.Actors
import com.css.internal.shared.storage.crdt.data.PathComponent
import com.css.internal.shared.storage.crdt.data.Version
import com.css.internal.shared.storage.crdt.data.VersionNode
import com.css.internal.shared.storage.crdt.resolver.CrdtMessageResolver

interface WireCrdtMessageResolver<M> : CrdtMessageResolver<M, VersionNode, Version, PathComponent, Actors>
