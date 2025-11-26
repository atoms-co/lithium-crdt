package com.css.protobuf.crdt.resolver

import com.css.protobuf.crdt.resolver.decoder.CrdtPathChangeDecoder
import com.css.protobuf.crdt.resolver.delta.CrdtDeltaResolver
import com.css.protobuf.crdt.resolver.incoming.partial.CrdtIncomingChangeResolver
import com.css.protobuf.crdt.resolver.local.CrdtLocalResolver

/**
 * Core CRDT resolver interface for any data type.
 */
interface CrdtResolver<T, N, V, C> :
    CrdtLocalResolver<T, N, V, C>,
    CrdtIncomingChangeResolver<T, N, V, C>,
    CrdtDeltaResolver<T, N, V, C>,
    CrdtPathChangeDecoder<T, N, V, C>
