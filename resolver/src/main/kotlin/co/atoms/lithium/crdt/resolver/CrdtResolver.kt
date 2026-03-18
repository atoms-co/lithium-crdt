package co.atoms.lithium.crdt.resolver

import co.atoms.lithium.crdt.resolver.decoder.CrdtPathChangeDecoder
import co.atoms.lithium.crdt.resolver.delta.CrdtDeltaResolver
import co.atoms.lithium.crdt.resolver.incoming.partial.CrdtIncomingChangeResolver
import co.atoms.lithium.crdt.resolver.local.CrdtLocalResolver

/**
 * Core CRDT resolver interface for any data type.
 */
interface CrdtResolver<T, N, V, C> :
    CrdtLocalResolver<T, N, V, C>,
    CrdtIncomingChangeResolver<T, N, V, C>,
    CrdtDeltaResolver<T, N, V, C>,
    CrdtPathChangeDecoder<T, N, V, C>
