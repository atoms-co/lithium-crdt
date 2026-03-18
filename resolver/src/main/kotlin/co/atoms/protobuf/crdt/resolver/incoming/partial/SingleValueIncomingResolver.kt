package co.atoms.protobuf.crdt.resolver.incoming.partial

import co.atoms.protobuf.crdt.resolver.ChangeEvent
import co.atoms.protobuf.crdt.resolver.NodeMergeResult
import co.atoms.protobuf.crdt.resolver.ResolutionDeltaContext
import co.atoms.protobuf.crdt.resolver.incoming.OptionalAnyValueIncomingResolver
import co.atoms.protobuf.crdt.resolver.incoming.SingleValueIncomingResolver
import co.atoms.protobuf.crdt.resolver.version.ResolutionStrategy

/**
 * Last-write-wins CRDT resolver for primitive values.
 *
 * Simple version-based resolution: higher version wins, equal versions keep local.
 */
interface SingleValueIncomingChangeResolver<T, N, V, C> :
    CrdtIncomingChangeResolver<T, N, V, C>,
    SingleValueIncomingResolver<T, N, V, C> {
    override fun applyChanges(
        depth: Int,
        localValue: T?,
        localNode: N?,
        localVersion: V,
        changes: List<ChangeEvent<*, N, C>>,
        context: ResolutionDeltaContext<N, C>,
    ): NodeMergeResult<T, N, ResolutionStrategy> = with(versionTreeResolver) {
        assert(changes.size == 1) {
            "There should only be one change for a single value path: $changes"
        }

        val change = changes.first()

        assert(change.pathComponents.size == depth) {
            "Change path has incorrect depth to pathComponents: $change"
        }

        @Suppress("UNCHECKED_CAST")
        return resolveConflict(
            localValue = localValue,
            localNode = localNode,
            localVersion = localVersion,
            incomingValue = change.value as? T,
            incomingNode = change.versionNode,
            incomingVersion = change.versionNode.versionValue ?: minVersion,
            context = context
        )
    }
}

/**
 * Last-write-wins resolver that ignores incoming null values.
 *
 * Treats incoming null as "unset" and always retains local value. Non-null incoming values use standard last-write-wins
 * logic.
 */
interface OptionalAnyValueIncomingChangeResolver<T, N, V, C> :
    CrdtIncomingChangeResolver<T, N, V, C>,
    OptionalAnyValueIncomingResolver<T, N, V, C> {
    override val valueIncomingResolver: CrdtIncomingChangeResolver<T, N, V, C>

    override fun applyChanges(
        depth: Int,
        localValue: T?,
        localNode: N?,
        localVersion: V,
        changes: List<ChangeEvent<*, N, C>>,
        context: ResolutionDeltaContext<N, C>,
    ): NodeMergeResult<T, N, ResolutionStrategy> {
        return valueIncomingResolver.applyChanges(
            depth = depth,
            localValue = localValue,
            localNode = localNode,
            localVersion = localVersion,
            changes = changes,
            context = context
        )
    }
}
