package com.css.protobuf.crdt.resolver.decoder

import com.css.protobuf.crdt.resolver.ChangeEvent
import com.css.protobuf.crdt.resolver.NodeMergeChangeProvider

/**
 * Marker interface for map field decoders.
 *
 * Maps can navigate by key (string_key, int_key, etc.). The next path component
 * should be a key to identify the map entry.
 *
 * Implementations should expect exactly one more path component (the key) and then decode.
 */
internal interface MapChangeDecoder<K, T, N, V, C> : CrdtPathChangeDecoder<Map<K, T>, N, V, C> {
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
                        throw IllegalArgumentException("Failed to decode map at path $pathComponents", e)
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
