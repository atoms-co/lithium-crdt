package com.css.protobuf.crdt.resolver

import com.css.protobuf.crdt.resolver.decoder.CrdtPathChangeDecoder
import com.css.protobuf.crdt.resolver.decoder.MapChangeDecoder
import com.css.protobuf.crdt.resolver.delta.CrdtDeltaResolver
import com.css.protobuf.crdt.resolver.delta.IntMapCrdtDeltaResolver
import com.css.protobuf.crdt.resolver.descriptor.CollectionType
import com.css.protobuf.crdt.resolver.incoming.partial.CrdtIncomingChangeResolver
import com.css.protobuf.crdt.resolver.incoming.partial.IntMapCrdtIncomingChangeResolver
import com.css.protobuf.crdt.resolver.local.CrdtLocalResolver
import com.css.protobuf.crdt.resolver.local.IntMapCrdtLocalResolver
import com.css.protobuf.crdt.resolver.version.VersionTreeResolver

class IntMapResolver<T, N, V, C> private constructor(
    override val config: CollectionType.Map,
    override val decoder: (ByteArray) -> Map<Int, T>,
    override val encoder: (Map<Int, T>) -> ByteArray,
    override val valueChangeDecoder: CrdtPathChangeDecoder<T, N, V, C>,
    override val valueDeltaResolver: CrdtDeltaResolver<T, N, V, C>,
    override val valueIncomingResolver: CrdtIncomingChangeResolver<T, N, V, C>,
    override val valueLocalResolver: CrdtLocalResolver<T, N, V, C>,
    override val versionTreeResolver: VersionTreeResolver<N, V, C>,
) : CrdtResolver<Map<Int, T>, N, V, C>,
    IntMapCrdtIncomingChangeResolver<T, N, V, C>,
    IntMapCrdtLocalResolver<T, N, V, C>,
    IntMapCrdtDeltaResolver<T, N, V, C>,
    MapChangeDecoder<Int, T, N, V, C> {
    constructor(
        config: CollectionType.Map,
        decoder: (ByteArray) -> Map<Int, T>,
        encoder: (Map<Int, T>) -> ByteArray,
        valueResolver: CrdtResolver<T, N, V, C>,
        versionTreeResolver: VersionTreeResolver<N, V, C>,
    ) : this(
        config = config,
        decoder = decoder,
        encoder = encoder,
        valueChangeDecoder = valueResolver,
        valueDeltaResolver = valueResolver,
        valueIncomingResolver = valueResolver,
        valueLocalResolver = valueResolver,
        versionTreeResolver = versionTreeResolver,
    )
}
