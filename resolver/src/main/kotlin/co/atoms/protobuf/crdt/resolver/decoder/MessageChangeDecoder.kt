package co.atoms.protobuf.crdt.resolver.decoder

import co.atoms.protobuf.crdt.resolver.ChangeEvent
import co.atoms.protobuf.crdt.resolver.NodeMergeChangeProvider
import co.atoms.protobuf.crdt.resolver.descriptor.MessageFieldResolver

/**
 * Decoder for message types that can navigate through nested field structures.
 *
 * Message decoders navigate by field number. They delegate to field-level change decoders
 * for continued navigation or final decoding.
 *
 * ## Usage Pattern
 *
 * Message resolvers (e.g., WireMessageResolver, ProtocMessageResolver) implement this interface
 * and provide the necessary vals. The default implementation handles field navigation.
 *
 * ```kotlin
 * internal class WireMessageResolver<M, B>(
 *     override val encoder: (M) -> ByteArray,
 *     override val decoder: (ByteArray) -> M,
 *     // ... other params
 * ) : MessageChangeDecoder<M, B, VersionNode, Version, PathComponent> {
 *     override val fields: Map<Int, MessageFieldResolver<M, B, Any, VersionNode, Version, PathComponent>>
 *         by lazy { /* field initialization */ }
 *     override val versionTreeResolver: VersionTreeResolver<VersionNode, Version, PathComponent> = ...
 * }
 * ```
 *
 * @param M The message type
 * @param B The builder type
 * @param N The node type
 * @param V The version type
 * @param C The path component type
 */
interface MessageChangeDecoder<M, B, N, V, C> : CrdtPathChangeDecoder<M, N, V, C> {
    /**
     * Map of field resolvers by field tag/number
     */
    val fields: Map<Int, MessageFieldResolver<M, B, Any, N, V, C>>

    override fun decodeChange(
        depth: Int,
        encodedValue: ByteArray?,
        pathComponents: List<C>,
        versionNode: N,
    ): ChangeEvent<*, N, C> = with(versionTreeResolver) {
        if (depth == pathComponents.size) {
            val decodedValue = encodedValue?.let { decoder(it) }
            return NodeMergeChangeProvider(
                encoder = encoder,
                pathComponents = pathComponents,
                value = decodedValue,
                versionNode = versionNode
            )
        }

        val component = pathComponents[depth]

        val fieldNumber = component.fieldNumber
            ?: throw IllegalArgumentException(
                "Expected field_number in path component at $pathComponents, got: $component"
            )

        val field = fields[fieldNumber]
            ?: throw IllegalArgumentException(
                "No field found for field number $fieldNumber at path $pathComponents"
            )

        // Delegate to the field's change decoder
        return field.changeDecoder.decodeChange(
            depth = depth + 1,
            encodedValue = encodedValue,
            pathComponents = pathComponents,
            versionNode = versionNode
        )
    }
}
