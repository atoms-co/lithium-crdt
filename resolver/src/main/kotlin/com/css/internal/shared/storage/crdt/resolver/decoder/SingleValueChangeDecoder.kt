package com.css.internal.shared.storage.crdt.resolver.decoder

import com.css.internal.shared.storage.crdt.resolver.ChangeEvent
import com.css.internal.shared.storage.crdt.resolver.NodeMergeChangeProvider

/**
 * Marker interface for single value field decoders.
 *
 * These fields don't have nested structure, so they should only be decoded when
 * pathComponents is empty (we've reached the target field).
 *
 * Implementations should check that pathComponents is empty and decode the value directly.
 */
internal interface SingleValueChangeDecoder<T, N, V, C> : CrdtPathChangeDecoder<T, N, V, C> {
    override fun decodeChange(
        depth: Int,
        encodedValue: ByteArray?,
        pathComponents: List<C>,
        versionNode: N,
    ): ChangeEvent<T, N, C> {
        require(depth == pathComponents.size) {
            "Single value field cannot navigate further. Full path: $pathComponents"
        }

        val decodedValue = encodedValue?.let { bytes ->
            try {
                decoder(bytes)
            } catch (e: Exception) {
                throw IllegalArgumentException("Failed to decode single value at path $pathComponents", e)
            }
        }

        return NodeMergeChangeProvider(
            encoder = encoder,
            pathComponents = pathComponents,
            value = decodedValue,
            versionNode = versionNode
        )
    }
}
