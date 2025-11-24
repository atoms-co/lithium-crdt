package com.css.internal.shared.storage.crdt.resolver

import com.css.internal.shared.storage.crdt.resolver.decoder.CrdtPathChangeDecoder
import com.css.internal.shared.storage.crdt.resolver.decoder.MapChangeDecoder
import com.css.internal.shared.storage.crdt.resolver.delta.CrdtDeltaResolver
import com.css.internal.shared.storage.crdt.resolver.delta.StringMapCrdtDeltaResolver
import com.css.internal.shared.storage.crdt.resolver.descriptor.CollectionType
import com.css.internal.shared.storage.crdt.resolver.incoming.partial.CrdtIncomingChangeResolver
import com.css.internal.shared.storage.crdt.resolver.incoming.partial.StringMapCrdtIncomingChangeResolver
import com.css.internal.shared.storage.crdt.resolver.local.CrdtLocalResolver
import com.css.internal.shared.storage.crdt.resolver.local.StringMapCrdtLocalResolver
import com.css.internal.shared.storage.crdt.resolver.version.VersionTreeResolver

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
