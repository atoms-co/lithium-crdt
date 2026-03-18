package co.atoms.protobuf.crdt.resolver.delta

import co.atoms.protobuf.crdt.resolver.ResolutionDeltaContext
import co.atoms.protobuf.crdt.resolver.version.VersionTreeResolver

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
