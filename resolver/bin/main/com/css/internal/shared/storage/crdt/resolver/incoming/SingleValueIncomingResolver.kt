package com.css.internal.shared.storage.crdt.resolver.incoming

import com.css.internal.shared.storage.crdt.resolver.NodeMergeResult
import com.css.internal.shared.storage.crdt.resolver.ResolutionDeltaContext
import com.css.internal.shared.storage.crdt.resolver.version.ResolutionStrategy
import com.css.internal.shared.storage.crdt.resolver.version.ResolutionStrategy.INCOMING
import com.css.internal.shared.storage.crdt.resolver.version.ResolutionStrategy.LOCAL
import com.css.internal.shared.storage.crdt.resolver.version.ResolutionStrategy.NO_CHANGE

/**
 * Last-write-wins CRDT resolver for primitive values.
 *
 * Simple version-based resolution: higher version wins, equal versions keep local.
 */
interface SingleValueIncomingResolver<T, N, V, C> : CrdtIncomingResolver<T, N, V, C> {
    override fun resolveConflict(
        localValue: T?,
        localNode: N?,
        localVersion: V,
        incomingValue: T?,
        incomingNode: N?,
        incomingVersion: V,
        context: ResolutionDeltaContext<N, C>,
    ): NodeMergeResult<T, N, ResolutionStrategy> =
        with(versionTreeResolver) {
            return when {
                incomingVersion > localVersion ->
                    NodeMergeResult(
                        value = incomingValue,
                        node = incomingNode ?: createVersionNode(incomingVersion),
                        resolution = INCOMING,
                    )
                        .also {
                            context.addChange(
                                newValue = incomingValue,
                                encoder = encoder,
                                versionNode = it.node ?: createVersionNode(incomingVersion),
                            )
                        }
                else ->
                    NodeMergeResult(
                        value = localValue,
                        node = localNode ?: createVersionNode(localVersion),
                        resolution = if (localVersion == incomingVersion) NO_CHANGE else LOCAL,
                    )
            }
        }
}

/**
 * Last-write-wins resolver that ignores incoming null values.
 *
 * Treats incoming null as "unset" and always retains local value. Non-null incoming values use standard last-write-wins
 * logic.
 */
interface OptionalAnyValueIncomingResolver<T, N, V, C> : CrdtIncomingResolver<T, N, V, C> {
    val valueIncomingResolver: CrdtIncomingResolver<T, N, V, C>

    override fun resolveConflict(
        localValue: T?,
        localNode: N?,
        localVersion: V,
        incomingValue: T?,
        incomingNode: N?,
        incomingVersion: V,
        context: ResolutionDeltaContext<N, C>,
    ): NodeMergeResult<T, N, ResolutionStrategy> =
        with(versionTreeResolver) {
            return when {
                // Optional values are ignored
                incomingValue == null ->
                    NodeMergeResult(
                        value = localValue,
                        node = localNode ?: createVersionNode(localVersion),
                        resolution = if (localValue == null) NO_CHANGE else LOCAL,
                    )

                else ->
                    valueIncomingResolver.resolveConflict(
                        localValue = localValue,
                        localNode = localNode,
                        localVersion = localVersion,
                        incomingValue = incomingValue,
                        incomingNode = incomingNode,
                        incomingVersion = incomingVersion,
                        context = context,
                    )
            }
        }
}
