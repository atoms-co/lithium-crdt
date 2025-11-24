package com.css.internal.shared.storage.crdt.resolver

import com.css.internal.shared.storage.crdt.resolver.decoder.CrdtPathChangeDecoder
import com.css.internal.shared.storage.crdt.resolver.delta.CrdtDeltaResolver
import com.css.internal.shared.storage.crdt.resolver.incoming.partial.CrdtIncomingChangeResolver
import com.css.internal.shared.storage.crdt.resolver.local.CrdtLocalResolver

/**
 * Core CRDT resolver interface for any data type.
 */
interface CrdtResolver<T, N, V, C> :
    CrdtLocalResolver<T, N, V, C>,
    CrdtIncomingChangeResolver<T, N, V, C>,
    CrdtDeltaResolver<T, N, V, C>,
    CrdtPathChangeDecoder<T, N, V, C>
