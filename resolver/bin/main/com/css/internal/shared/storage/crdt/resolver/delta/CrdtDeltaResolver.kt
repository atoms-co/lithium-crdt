package com.css.internal.shared.storage.crdt.resolver.delta

import com.css.internal.shared.storage.crdt.resolver.ResolutionDeltaContext
import com.css.internal.shared.storage.crdt.resolver.version.VersionTreeResolver

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
