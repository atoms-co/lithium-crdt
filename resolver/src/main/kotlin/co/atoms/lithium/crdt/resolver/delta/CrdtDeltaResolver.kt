package co.atoms.lithium.crdt.resolver.delta

import co.atoms.lithium.crdt.resolver.ResolutionDeltaContext
import co.atoms.lithium.crdt.resolver.version.VersionTreeResolver

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
