package co.atoms.lithium.crdt.resolver

import co.atoms.lithium.crdt.resolver.decoder.CrdtPathChangeDecoder
import co.atoms.lithium.crdt.resolver.decoder.MapChangeDecoder
import co.atoms.lithium.crdt.resolver.delta.BooleanMapCrdtDeltaResolver
import co.atoms.lithium.crdt.resolver.delta.CrdtDeltaResolver
import co.atoms.lithium.crdt.resolver.descriptor.CollectionType
import co.atoms.lithium.crdt.resolver.incoming.partial.BooleanMapCrdtIncomingChangeResolver
import co.atoms.lithium.crdt.resolver.incoming.partial.CrdtIncomingChangeResolver
import co.atoms.lithium.crdt.resolver.local.BooleanMapCrdtLocalResolver
import co.atoms.lithium.crdt.resolver.local.CrdtLocalResolver
import co.atoms.lithium.crdt.resolver.version.VersionTreeResolver

class BooleanMapResolver<T, N, V, C> private constructor(
    override val config: CollectionType.Map,
    override val decoder: (ByteArray) -> Map<Boolean, T>,
    override val encoder: (Map<Boolean, T>) -> ByteArray,
    override val valueChangeDecoder: CrdtPathChangeDecoder<T, N, V, C>,
    override val valueDeltaResolver: CrdtDeltaResolver<T, N, V, C>,
    override val valueLocalResolver: CrdtLocalResolver<T, N, V, C>,
    override val valueIncomingResolver: CrdtIncomingChangeResolver<T, N, V, C>,
    override val versionTreeResolver: VersionTreeResolver<N, V, C>,
) : CrdtResolver<Map<Boolean, T>, N, V, C>,
    BooleanMapCrdtIncomingChangeResolver<T, N, V, C>,
    BooleanMapCrdtLocalResolver<T, N, V, C>,
    BooleanMapCrdtDeltaResolver<T, N, V, C>,
    MapChangeDecoder<Boolean, T, N, V, C> {
    constructor(
        config: CollectionType.Map,
        decoder: (ByteArray) -> Map<Boolean, T>,
        encoder: (Map<Boolean, T>) -> ByteArray,
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
