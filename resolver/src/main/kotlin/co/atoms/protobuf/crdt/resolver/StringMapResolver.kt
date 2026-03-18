package co.atoms.protobuf.crdt.resolver

import co.atoms.protobuf.crdt.resolver.decoder.CrdtPathChangeDecoder
import co.atoms.protobuf.crdt.resolver.decoder.MapChangeDecoder
import co.atoms.protobuf.crdt.resolver.delta.CrdtDeltaResolver
import co.atoms.protobuf.crdt.resolver.delta.StringMapCrdtDeltaResolver
import co.atoms.protobuf.crdt.resolver.descriptor.CollectionType
import co.atoms.protobuf.crdt.resolver.incoming.partial.CrdtIncomingChangeResolver
import co.atoms.protobuf.crdt.resolver.incoming.partial.StringMapCrdtIncomingChangeResolver
import co.atoms.protobuf.crdt.resolver.local.CrdtLocalResolver
import co.atoms.protobuf.crdt.resolver.local.StringMapCrdtLocalResolver
import co.atoms.protobuf.crdt.resolver.version.VersionTreeResolver

class StringMapResolver<T, N, V, C> private constructor(
    override val config: CollectionType.Map,
    override val decoder: (ByteArray) -> Map<String, T>,
    override val encoder: (Map<String, T>) -> ByteArray,
    override val valueChangeDecoder: CrdtPathChangeDecoder<T, N, V, C>,
    override val valueDeltaResolver: CrdtDeltaResolver<T, N, V, C>,
    override val valueIncomingResolver: CrdtIncomingChangeResolver<T, N, V, C>,
    override val valueLocalResolver: CrdtLocalResolver<T, N, V, C>,
    override val versionTreeResolver: VersionTreeResolver<N, V, C>,
) : CrdtResolver<Map<String, T>, N, V, C>,
    StringMapCrdtIncomingChangeResolver<T, N, V, C>,
    StringMapCrdtLocalResolver<T, N, V, C>,
    StringMapCrdtDeltaResolver<T, N, V, C>,
    MapChangeDecoder<String, T, N, V, C> {

    constructor(
        config: CollectionType.Map,
        decoder: (ByteArray) -> Map<String, T>,
        encoder: (Map<String, T>) -> ByteArray,
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
