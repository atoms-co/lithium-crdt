package co.atoms.lithium.crdt.resolver

import co.atoms.lithium.crdt.resolver.decoder.SingleValueChangeDecoder
import co.atoms.lithium.crdt.resolver.delta.SingleValueDeltaResolver
import co.atoms.lithium.crdt.resolver.incoming.partial.SingleValueIncomingChangeResolver
import co.atoms.lithium.crdt.resolver.local.SingleValueLocalResolver
import co.atoms.lithium.crdt.resolver.version.VersionTreeResolver

class SingleValueResolver<T, N, V, C>(
    override val decoder: (ByteArray) -> T,
    override val encoder: (T) -> ByteArray,
    override val versionTreeResolver: VersionTreeResolver<N, V, C>,
) : CrdtResolver<T, N, V, C>,
    SingleValueLocalResolver<T, N, V, C>,
    SingleValueIncomingChangeResolver<T, N, V, C>,
    SingleValueDeltaResolver<T, N, V, C>,
    SingleValueChangeDecoder<T, N, V, C>
