package co.atoms.protobuf.crdt.resolver

import co.atoms.protobuf.crdt.resolver.decoder.CrdtPathChangeDecoder
import co.atoms.protobuf.crdt.resolver.decoder.MapChangeDecoder
import co.atoms.protobuf.crdt.resolver.delta.CrdtDeltaResolver
import co.atoms.protobuf.crdt.resolver.delta.IntMapCrdtDeltaResolver
import co.atoms.protobuf.crdt.resolver.descriptor.CollectionType
import co.atoms.protobuf.crdt.resolver.incoming.partial.CrdtIncomingChangeResolver
import co.atoms.protobuf.crdt.resolver.incoming.partial.IntMapCrdtIncomingChangeResolver
import co.atoms.protobuf.crdt.resolver.local.CrdtLocalResolver
import co.atoms.protobuf.crdt.resolver.local.IntMapCrdtLocalResolver
import co.atoms.protobuf.crdt.resolver.version.VersionTreeResolver

class IntMapResolver<T, N, V, C> private constructor(
    override val config: CollectionType.Map,
    override val decoder: (ByteArray) -> Map<Int, T>,
    override val encoder: (Map<Int, T>) -> ByteArray,
    override val valueChangeDecoder: CrdtPathChangeDecoder<T, N, V, C>,
    override val valueDeltaResolver: CrdtDeltaResolver<T, N, V, C>,
    override val valueIncomingResolver: CrdtIncomingChangeResolver<T, N, V, C>,
    override val valueLocalResolver: CrdtLocalResolver<T, N, V, C>,
    override val versionTreeResolver: VersionTreeResolver<N, V, C>,
) : CrdtResolver<Map<Int, T>, N, V, C>,
    IntMapCrdtIncomingChangeResolver<T, N, V, C>,
    IntMapCrdtLocalResolver<T, N, V, C>,
    IntMapCrdtDeltaResolver<T, N, V, C>,
    MapChangeDecoder<Int, T, N, V, C> {
    constructor(
        config: CollectionType.Map,
        decoder: (ByteArray) -> Map<Int, T>,
        encoder: (Map<Int, T>) -> ByteArray,
        valueResolver: CrdtResolver<T, N, V, C>,
        versionTreeResolver: VersionTreeResolver<N, V, C>,
    ) : this(
        config = config,
        decoder = decoder,
        encoder = encoder,
        valueChangeDecoder = valueResolver,
        valueDeltaResolver = valueResolver,
        valueIncomingResolver = valueResolver,
        valueLocalResolver = valueResolver,
        versionTreeResolver = versionTreeResolver,
    )
}
