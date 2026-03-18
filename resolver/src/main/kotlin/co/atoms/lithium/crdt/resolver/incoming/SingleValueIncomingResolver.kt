package co.atoms.lithium.crdt.resolver.incoming

import co.atoms.lithium.crdt.resolver.NodeMergeResult
import co.atoms.lithium.crdt.resolver.ResolutionDeltaContext
import co.atoms.lithium.crdt.resolver.version.ResolutionStrategy
import co.atoms.lithium.crdt.resolver.version.ResolutionStrategy.INCOMING
import co.atoms.lithium.crdt.resolver.version.ResolutionStrategy.LOCAL
import co.atoms.lithium.crdt.resolver.version.ResolutionStrategy.NO_CHANGE

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
                            if (incomingValue != localValue) {
                                context.addChange(
                                    newValue = incomingValue,
                                    encoder = encoder,
                                    versionNode = it.node ?: createVersionNode(incomingVersion),
                                )
                            }
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
