package co.atoms.lithium.crdt.resolver.descriptor

import co.atoms.lithium.crdt.resolver.decoder.CrdtPathChangeDecoder
import co.atoms.lithium.crdt.resolver.delta.CrdtDeltaResolver
import co.atoms.lithium.crdt.resolver.incoming.partial.CrdtIncomingChangeResolver
import co.atoms.lithium.crdt.resolver.local.CrdtLocalResolver

/**
 * Metadata for a single protobuf field with CRDT capabilities.
 *
 * Encapsulates all information needed to serialize, deserialize, and resolve conflicts
 * for a field. Delegates JSON serialization and CRDT resolution to the provided implementations.
 *
 * @param binding Field binding with reflection metadata
 * @param localResolver CRDT resolver for local writes
 * @param incomingResolver CRDT resolver for incoming conflicts
 * @param deltaResolver CRDT resolver for computing deltas
 * @param changeDecoder Decoder for reconstructing changes from wire format
 */
class MessageFieldResolver<M, B, F, N, V, C>(
    val binding: MessageFieldDescriptor<M, B, F>,
    val localResolver: CrdtLocalResolver<F, N, V, C>,
    val incomingResolver: CrdtIncomingChangeResolver<F, N, V, C>,
    val deltaResolver: CrdtDeltaResolver<F, N, V, C>,
    val changeDecoder: CrdtPathChangeDecoder<F, N, V, C>
) {
    override fun toString(): String = "MessageFieldResolver: $binding"
}
