package com.css.protobuf.crdt.resolver.delta

import com.css.protobuf.crdt.resolver.ResolutionDeltaContext
import com.css.protobuf.crdt.resolver.version.VersionTreeResolver

interface CrdtDeltaResolver<T, N, V, C> {
    val encoder: (T) -> ByteArray
    val versionTreeResolver: VersionTreeResolver<N, V, C>

    fun changeDelta(
        value: T?,
        node: N?,
        version: V,
        versionVector: Map<Long, Long>,
        context: ResolutionDeltaContext<N, C>,
    )
}
