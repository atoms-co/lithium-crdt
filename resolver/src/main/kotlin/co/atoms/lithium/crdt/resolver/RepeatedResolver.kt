package co.atoms.lithium.crdt.resolver

import co.atoms.lithium.crdt.resolver.decoder.CrdtPathChangeDecoder
import co.atoms.lithium.crdt.resolver.decoder.RepeatedChangeDecoder
import co.atoms.lithium.crdt.resolver.delta.CrdtDeltaResolver
import co.atoms.lithium.crdt.resolver.delta.RepeatedCrdtDeltaResolver
import co.atoms.lithium.crdt.resolver.incoming.partial.CrdtIncomingChangeResolver
import co.atoms.lithium.crdt.resolver.incoming.partial.RepeatedCrdtIncomingChangeResolver
import co.atoms.lithium.crdt.resolver.local.CrdtLocalResolver
import co.atoms.lithium.crdt.resolver.local.RepeatedCrdtLocalResolver
import co.atoms.lithium.crdt.resolver.version.VersionTreeResolver

class RepeatedResolver<T, N, V, C> private constructor(
    override val decoder: (ByteArray) -> List<T>,
    override val encoder: (List<T>) -> ByteArray,
    override val valueDeltaResolver: CrdtDeltaResolver<T, N, V, C>,
    override val valueChangeDecoder: CrdtPathChangeDecoder<T, N, V, C>,
    override val valueIncomingResolver: CrdtIncomingChangeResolver<T, N, V, C>,
    override val valueLocalResolver: CrdtLocalResolver<T, N, V, C>,
    override val versionTreeResolver: VersionTreeResolver<N, V, C>,
) : CrdtResolver<List<T>, N, V, C>,
    RepeatedCrdtIncomingChangeResolver<T, N, V, C>,
    RepeatedCrdtLocalResolver<T, N, V, C>,
    RepeatedCrdtDeltaResolver<T, N, V, C>,
    RepeatedChangeDecoder<T, N, V, C> {
    constructor(
        decoder: (ByteArray) -> List<T>,
        encoder: (List<T>) -> ByteArray,
        valueResolver: CrdtResolver<T, N, V, C>,
        versionTreeResolver: VersionTreeResolver<N, V, C>,
    ) : this(
        decoder = decoder,
        encoder = encoder,
        valueDeltaResolver = valueResolver,
        valueChangeDecoder = valueResolver,
        valueLocalResolver = valueResolver,
        valueIncomingResolver = valueResolver,
        versionTreeResolver = versionTreeResolver,
    )
}
