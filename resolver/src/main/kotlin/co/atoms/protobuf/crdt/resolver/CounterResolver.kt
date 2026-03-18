package co.atoms.protobuf.crdt.resolver

import co.atoms.protobuf.crdt.resolver.decoder.SingleValueChangeDecoder
import co.atoms.protobuf.crdt.resolver.delta.SingleValueDeltaResolver
import co.atoms.protobuf.crdt.resolver.incoming.partial.CounterIncomingChangeResolver
import co.atoms.protobuf.crdt.resolver.local.CounterLocalResolver
import co.atoms.protobuf.crdt.resolver.version.VersionTreeResolver

class CounterResolver<T, N, V, C>(
    override val decoder: (ByteArray) -> T,
    override val encoder: (T) -> ByteArray,
    override val toLong: (T?) -> Long,
    override val fromLong: (Long) -> T,
    override val versionTreeResolver: VersionTreeResolver<N, V, C>,
) : CrdtResolver<T, N, V, C>,
    CounterLocalResolver<T, N, V, C>,
    CounterIncomingChangeResolver<T, N, V, C>,
    SingleValueDeltaResolver<T, N, V, C>,
    SingleValueChangeDecoder<T, N, V, C>

fun <N, V, C> LongCounterResolver(
    decoder: (ByteArray) -> Long,
    encoder: (Long) -> ByteArray,
    versionTreeResolver: VersionTreeResolver<N, V, C>,
) = CounterResolver(
    decoder = decoder,
    encoder = encoder,
    toLong = { it ?: 0L },
    fromLong = { it },
    versionTreeResolver = versionTreeResolver,
)

fun <N, V, C> IntCounterResolver(
    decoder: (ByteArray) -> Int,
    encoder: (Int) -> ByteArray,
    versionTreeResolver: VersionTreeResolver<N, V, C>,
) = CounterResolver(
    decoder = decoder,
    encoder = encoder,
    toLong = { it?.toLong() ?: 0L },
    fromLong = { it.toInt() },
    versionTreeResolver = versionTreeResolver,
)
