package co.atoms.protobuf.crdt.resolver

import co.atoms.protobuf.crdt.resolver.decoder.SingleValueChangeDecoder
import co.atoms.protobuf.crdt.resolver.delta.CrdtDeltaResolver
import co.atoms.protobuf.crdt.resolver.delta.OptionalAnyValueDeltaResolver
import co.atoms.protobuf.crdt.resolver.incoming.partial.CrdtIncomingChangeResolver
import co.atoms.protobuf.crdt.resolver.incoming.partial.OptionalAnyValueIncomingChangeResolver
import co.atoms.protobuf.crdt.resolver.local.CrdtLocalResolver
import co.atoms.protobuf.crdt.resolver.local.OptionalAnyValueLocalResolver
import co.atoms.protobuf.crdt.resolver.version.VersionTreeResolver

class OptionalAnyValueResolver<T, N, V, C> private constructor(
    override val decoder: (ByteArray) -> T,
    override val encoder: (T) -> ByteArray,
    override val valueDeltaResolver: CrdtDeltaResolver<T, N, V, C>,
    override val valueIncomingResolver: CrdtIncomingChangeResolver<T, N, V, C>,
    override val valueLocalResolver: CrdtLocalResolver<T, N, V, C>,
    override val versionTreeResolver: VersionTreeResolver<N, V, C>,
) : CrdtResolver<T, N, V, C>,
    OptionalAnyValueLocalResolver<T, N, V, C>,
    OptionalAnyValueIncomingChangeResolver<T, N, V, C>,
    OptionalAnyValueDeltaResolver<T, N, V, C>,
    SingleValueChangeDecoder<T, N, V, C> {
    constructor(
        decoder: (ByteArray) -> T,
        encoder: (T) -> ByteArray,
        valueResolver: CrdtResolver<T, N, V, C>,
        versionTreeResolver: VersionTreeResolver<N, V, C>,
    ) : this(
        decoder = decoder,
        encoder = encoder,
        valueDeltaResolver = valueResolver,
        valueLocalResolver = valueResolver,
        valueIncomingResolver = valueResolver,
        versionTreeResolver = versionTreeResolver,
    )
}
