package co.atoms.protobuf.crdt.resolver

import co.atoms.protobuf.crdt.resolver.decoder.SingleValueChangeDecoder
import co.atoms.protobuf.crdt.resolver.delta.SingleValueDeltaResolver
import co.atoms.protobuf.crdt.resolver.incoming.partial.SingleValueIncomingChangeResolver
import co.atoms.protobuf.crdt.resolver.local.SingleValueLocalResolver
import co.atoms.protobuf.crdt.resolver.version.VersionTreeResolver

class SingleValueResolver<T, N, V, C>(
    override val decoder: (ByteArray) -> T,
    override val encoder: (T) -> ByteArray,
    override val versionTreeResolver: VersionTreeResolver<N, V, C>,
) : CrdtResolver<T, N, V, C>,
    SingleValueLocalResolver<T, N, V, C>,
    SingleValueIncomingChangeResolver<T, N, V, C>,
    SingleValueDeltaResolver<T, N, V, C>,
    SingleValueChangeDecoder<T, N, V, C>
