package com.css.internal.shared.storage.crdt.resolver.incoming.partial

import com.css.internal.shared.storage.crdt.resolver.ChangeEvent
import com.css.internal.shared.storage.crdt.resolver.NodeMergeResult
import com.css.internal.shared.storage.crdt.resolver.ResolutionDeltaContext
import com.css.internal.shared.storage.crdt.resolver.incoming.CounterIncomingResolver
import com.css.internal.shared.storage.crdt.resolver.version.ResolutionStrategy

/**
 * Counter CRDT resolver for processing incoming change events.
 *
 * ## Change Validation
 *
 * Counters are scalar values, so this resolver validates that:
 * - Only a single change is provided for the value path
 * - The change path depth matches the expected depth
 *
 * After validation, delegates to the conflict resolution logic which handles:
 * - Single vs multi-actor node optimization
 * - Per-actor version vector merging
 * - Structural equality-based resolution strategy
 */
interface CounterIncomingChangeResolver<T, N, V, C> :
    CrdtIncomingChangeResolver<T, N, V, C>,
    CounterIncomingResolver<T, N, V, C> {
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
