package com.css.protobuf.crdt.resolver.delta

import com.css.protobuf.crdt.resolver.ResolutionDeltaContext

/**
 * Last-write-wins CRDT resolver for primitive values.
 *
 * Simple version-based resolution: higher version wins, equal versions keep local.
 */
interface SingleValueDeltaResolver<T, N, V, C> : CrdtDeltaResolver<T, N, V, C> {

    override fun changeDelta(
        value: T?,
        node: N?,
        version: V,
        versionVector: Map<Long, Long>,
        context: ResolutionDeltaContext<N, C>
    ) {
        with(versionTreeResolver) {
            if (version !in versionVector) {
                context.addChange(
                    newValue = value,
                    encoder = encoder,
                    versionNode = node ?: createVersionNode(version)
                )
            }
        }
    }
}

/**
 * Last-write-wins resolver that ignores incoming null values.
 *
 * Treats incoming null as "unset" and always retains local value. Non-null incoming values use standard last-write-wins
 * logic.
 */
interface OptionalAnyValueDeltaResolver<T, N, V, C> : CrdtDeltaResolver<T, N, V, C> {
    val valueDeltaResolver: CrdtDeltaResolver<T, N, V, C>

    override fun changeDelta(
        value: T?,
        node: N?,
        version: V,
        versionVector: Map<Long, Long>,
        context: ResolutionDeltaContext<N, C>
    ) = valueDeltaResolver.changeDelta(
        value = value,
        node = node,
        version = version,
        versionVector = versionVector,
        context = context
    )
}
