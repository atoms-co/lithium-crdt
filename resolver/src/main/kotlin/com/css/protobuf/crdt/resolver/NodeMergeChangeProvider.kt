package com.css.protobuf.crdt.resolver

interface ChangeEvent<T, out N, out C> {
    val pathComponents: List<C>
    val value: T?
    val versionNode: N
    fun encoded(): ByteArray?
}

data class NodeMergeChangeProvider<T, out N, out C>(
    private val encoder: (T) -> ByteArray,
    override val pathComponents: List<C>,
    override val value: T?,
    override val versionNode: N,
) : ChangeEvent<T, N, C> {
    override fun encoded() = try {
        value?.let(encoder)
    } catch (e: Throwable) {
        throw IllegalArgumentException("$this", e)
    }
}
