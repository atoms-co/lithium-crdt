package co.atoms.lithium.crdt.resolver.decoder

import co.atoms.lithium.crdt.resolver.ChangeEvent
import co.atoms.lithium.crdt.resolver.NodeMergeChangeProvider

/**
 * Marker interface for repeated (list) field decoders.
 *
 * Lists can navigate by index (repeated_index). The next path component
 * should be an index to identify the list element.
 *
 * Implementations should expect exactly one more path component (the index) and then decode.
 */
internal interface RepeatedChangeDecoder<T, N, V, C> : CrdtPathChangeDecoder<List<T>, N, V, C> {
    val valueChangeDecoder: CrdtPathChangeDecoder<T, N, V, C>

    override fun decodeChange(
        depth: Int,
        encodedValue: ByteArray?,
        pathComponents: List<C>,
        versionNode: N,
    ): ChangeEvent<*, N, C> {
        if (depth == pathComponents.size) {
            return NodeMergeChangeProvider(
                encoder = encoder,
                pathComponents = pathComponents,
                value = encodedValue?.let {
                    try {
                        decoder(it)
                    } catch (e: Exception) {
                        throw IllegalArgumentException("Failed to decode repeated type at path $pathComponents", e)
                    }
                },
                versionNode = versionNode
            )
        }

        return valueChangeDecoder.decodeChange(
            depth = depth + 1,
            encodedValue = encodedValue,
            pathComponents = pathComponents,
            versionNode = versionNode,
        )
    }
}
