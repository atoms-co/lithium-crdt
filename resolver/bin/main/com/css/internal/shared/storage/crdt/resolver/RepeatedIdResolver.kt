package com.css.internal.shared.storage.crdt.resolver

import com.css.internal.shared.storage.crdt.resolver.decoder.CrdtPathChangeDecoder
import com.css.internal.shared.storage.crdt.resolver.decoder.RepeatedIdChangeDecoder
import com.css.internal.shared.storage.crdt.resolver.delta.CrdtDeltaResolver
import com.css.internal.shared.storage.crdt.resolver.delta.RepeatedIdCrdtDeltaResolver
import com.css.internal.shared.storage.crdt.resolver.incoming.partial.CrdtIncomingChangeResolver
import com.css.internal.shared.storage.crdt.resolver.incoming.partial.RepeatedIdCrdtIncomingChangeResolver
import com.css.internal.shared.storage.crdt.resolver.local.CrdtLocalResolver
import com.css.internal.shared.storage.crdt.resolver.local.RepeatedIdCrdtLocalResolver
import com.css.internal.shared.storage.crdt.resolver.version.VersionTreeResolver

class RepeatedIdResolver<K, T, N, V, C> private constructor(
    override val decoder: (ByteArray) -> List<T>,
    override val encoder: (List<T>) -> ByteArray,
    override val keyTransformer: (T) -> K,
    override val mapDeltaResolver: CrdtDeltaResolver<Map<K, T>, N, V, C>,
    override val mapIncomingResolver: CrdtIncomingChangeResolver<Map<K, T>, N, V, C>,
    override val mapLocalResolver: CrdtLocalResolver<Map<K, T>, N, V, C>,
    override val mapPathChangeDecoder: CrdtPathChangeDecoder<Map<K, T>, N, V, C>,
    override val versionTreeResolver: VersionTreeResolver<N, V, C>,
) : CrdtResolver<List<T>, N, V, C>,
    RepeatedIdCrdtIncomingChangeResolver<K, T, N, V, C>,
    RepeatedIdCrdtLocalResolver<K, T, N, V, C>,
    RepeatedIdCrdtDeltaResolver<K, T, N, V, C>,
    RepeatedIdChangeDecoder<K, T, N, V, C> {
    constructor(
        decoder: (ByteArray) -> List<T>,
        encoder: (List<T>) -> ByteArray,
        keyTransformer: (T) -> K,
        mapResolver: CrdtResolver<Map<K, T>, N, V, C>,
        versionTreeResolver: VersionTreeResolver<N, V, C>,
    ) : this(
        decoder = decoder,
        encoder = encoder,
        keyTransformer = keyTransformer,
        mapDeltaResolver = mapResolver,
        mapIncomingResolver = mapResolver,
        mapLocalResolver = mapResolver,
        mapPathChangeDecoder = mapResolver,
        versionTreeResolver = versionTreeResolver,
    )
}
