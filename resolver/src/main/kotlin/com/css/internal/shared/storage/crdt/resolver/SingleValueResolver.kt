package com.css.internal.shared.storage.crdt.resolver

import com.css.internal.shared.storage.crdt.resolver.decoder.SingleValueChangeDecoder
import com.css.internal.shared.storage.crdt.resolver.delta.SingleValueDeltaResolver
import com.css.internal.shared.storage.crdt.resolver.incoming.partial.SingleValueIncomingChangeResolver
import com.css.internal.shared.storage.crdt.resolver.local.SingleValueLocalResolver
import com.css.internal.shared.storage.crdt.resolver.version.VersionTreeResolver

class SingleValueResolver<T, N, V, C>(
    override val decoder: (ByteArray) -> T,
    override val encoder: (T) -> ByteArray,
    override val versionTreeResolver: VersionTreeResolver<N, V, C>,
) : CrdtResolver<T, N, V, C>,
    SingleValueLocalResolver<T, N, V, C>,
    SingleValueIncomingChangeResolver<T, N, V, C>,
    SingleValueDeltaResolver<T, N, V, C>,
    SingleValueChangeDecoder<T, N, V, C>
