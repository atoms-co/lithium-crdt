package co.atoms.lithium.crdt.resolver.decoder

import co.atoms.lithium.crdt.resolver.ChangeEvent

internal interface RepeatedIdChangeDecoder<K, T, N, V, C> : CrdtPathChangeDecoder<List<T>, N, V, C> {
    val mapPathChangeDecoder: CrdtPathChangeDecoder<Map<K, T>, N, V, C>

    override fun decodeChange(
        depth: Int,
        encodedValue: ByteArray?,
        pathComponents: List<C>,
        versionNode: N,
    ): ChangeEvent<*, N, C> {
        return mapPathChangeDecoder.decodeChange(
            depth = depth,
            encodedValue = encodedValue,
            pathComponents = pathComponents,
            versionNode = versionNode,
        )
    }
}
