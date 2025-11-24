package com.css.internal.shared.storage.crdt.resolver.delta

import com.css.internal.shared.storage.crdt.resolver.ResolutionDeltaContext
import com.css.internal.shared.storage.crdt.resolver.withPath

interface RepeatedCrdtDeltaResolver<E, N, V, C> :
    CrdtDeltaResolver<List<E>, N, V, C> {
    val valueDeltaResolver: CrdtDeltaResolver<E, N, V, C>

    @Suppress("CyclomaticComplexMethod")
    override fun changeDelta(
        value: List<E>?,
        node: N?,
        version: V,
        versionVector: Map<Long, Long>,
        context: ResolutionDeltaContext<N, C>
    ) {
        with(versionTreeResolver) {
            val nodeVersion = node?.versionValue ?: version
            if (nodeVersion !in versionVector) {
                context.addChange(
                    newValue = value,
                    versionNode = node ?: createVersionNode(nodeVersion),
                    encoder = encoder
                )
                return
            }

            val localEntries = node?.entries
            val size = value?.size ?: 0

            // Process each index position independently
            repeat(size) { index ->
                val localEntryNode = localEntries?.getOrNull(index)
                val childVersion = localEntryNode?.versionValue

                context.withPath(versionTreeResolver.createPathComponentRepeatedIndex(index)) {
                    valueDeltaResolver.changeDelta(
                        value = value?.getOrNull(index),
                        node = localEntryNode,
                        version = childVersion ?: nodeVersion,
                        versionVector = versionVector,
                        context = context,
                    )
                }
            }
        }
    }
}
