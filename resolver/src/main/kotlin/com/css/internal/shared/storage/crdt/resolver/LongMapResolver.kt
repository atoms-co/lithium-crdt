package com.css.internal.shared.storage.crdt.resolver

import com.css.internal.shared.storage.crdt.resolver.decoder.CrdtPathChangeDecoder
import com.css.internal.shared.storage.crdt.resolver.decoder.MapChangeDecoder
import com.css.internal.shared.storage.crdt.resolver.delta.CrdtDeltaResolver
import com.css.internal.shared.storage.crdt.resolver.delta.LongMapCrdtDeltaResolver
import com.css.internal.shared.storage.crdt.resolver.descriptor.CollectionType
import com.css.internal.shared.storage.crdt.resolver.incoming.partial.CrdtIncomingChangeResolver
import com.css.internal.shared.storage.crdt.resolver.incoming.partial.LongMapCrdtIncomingChangeResolver
import com.css.internal.shared.storage.crdt.resolver.local.CrdtLocalResolver
import com.css.internal.shared.storage.crdt.resolver.local.LongMapCrdtLocalResolver
import com.css.internal.shared.storage.crdt.resolver.version.VersionTreeResolver

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
