package co.atoms.lithium.crdt.resolver.descriptor

/**
 * Runtime adapter providing field-level metadata for proto messages in CRDT operations.
 *
 * Exposes message structure and builder creation for conflict resolution and
 * field-wise updates without compile-time type information.
 *
 * @param M Message type
 * @param S Builder setter type
 * @param B Builder type
 */
interface RuntimeMessageAdapter<M, S, B : MessageBuilder<M, S>, N, V, C> {
    val fields: Map<Int, MessageFieldResolver<M, B, Any, N, V, C>>
    fun newBuilder(): B
}
