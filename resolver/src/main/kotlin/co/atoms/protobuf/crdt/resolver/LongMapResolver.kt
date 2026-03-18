package co.atoms.protobuf.crdt.resolver

import co.atoms.protobuf.crdt.resolver.decoder.CrdtPathChangeDecoder
import co.atoms.protobuf.crdt.resolver.decoder.MapChangeDecoder
import co.atoms.protobuf.crdt.resolver.delta.CrdtDeltaResolver
import co.atoms.protobuf.crdt.resolver.delta.LongMapCrdtDeltaResolver
import co.atoms.protobuf.crdt.resolver.descriptor.CollectionType
import co.atoms.protobuf.crdt.resolver.incoming.partial.CrdtIncomingChangeResolver
import co.atoms.protobuf.crdt.resolver.incoming.partial.LongMapCrdtIncomingChangeResolver
import co.atoms.protobuf.crdt.resolver.local.CrdtLocalResolver
import co.atoms.protobuf.crdt.resolver.local.LongMapCrdtLocalResolver
import co.atoms.protobuf.crdt.resolver.version.VersionTreeResolver

class LongMapResolver<T, N, V, C> private constructor(
    override val config: CollectionType.Map,
    override val decoder: (ByteArray) -> Map<Long, T>,
    override val encoder: (Map<Long, T>) -> ByteArray,
    override val valueChangeDecoder: CrdtPathChangeDecoder<T, N, V, C>,
    override val valueDeltaResolver: CrdtDeltaResolver<T, N, V, C>,
    override val valueIncomingResolver: CrdtIncomingChangeResolver<T, N, V, C>,
    override val valueLocalResolver: CrdtLocalResolver<T, N, V, C>,
    override val versionTreeResolver: VersionTreeResolver<N, V, C>,
) : CrdtResolver<Map<Long, T>, N, V, C>,
    LongMapCrdtIncomingChangeResolver<T, N, V, C>,
    LongMapCrdtLocalResolver<T, N, V, C>,
    LongMapCrdtDeltaResolver<T, N, V, C>,
    MapChangeDecoder<Long, T, N, V, C> {
    constructor(
        config: CollectionType.Map,
        decoder: (ByteArray) -> Map<Long, T>,
        encoder: (Map<Long, T>) -> ByteArray,
        valueResolver: CrdtResolver<T, N, V, C>,
        versionTreeResolver: VersionTreeResolver<N, V, C>,
    ) : this(
        config = config,
        decoder = decoder,
        encoder = encoder,
        valueChangeDecoder = valueResolver,
        valueDeltaResolver = valueResolver,
        valueLocalResolver = valueResolver,
        valueIncomingResolver = valueResolver,
        versionTreeResolver = versionTreeResolver,
    )
}
