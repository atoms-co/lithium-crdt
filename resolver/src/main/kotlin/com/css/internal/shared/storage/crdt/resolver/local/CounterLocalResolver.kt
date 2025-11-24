package com.css.internal.shared.storage.crdt.resolver.local

import com.css.internal.shared.storage.crdt.resolver.NodeMergeResult
import com.css.internal.shared.storage.crdt.resolver.ResolutionDeltaContext

/**
 * Counter CRDT resolver for local write operations.
 *
 * ## Counter CRDT Semantics
 *
 * Counter nodes use per-actor version vectors to track contributions from multiple writers:
 * - **Single-actor optimization**: When only one actor has written to a counter, the counter
 *   structure is unnecessary. Instead, a simple version node is created where the version
 *   represents the actor's state, and the value is stored in parallel data.
 * - **Multi-actor counters**: When 2+ actors contribute, a counter map structure is created
 *   to track each actor's version and value. The total counter value is the sum of all actors.
 *
 * ## Node Creation Strategy
 *
 * - **currentNode exists**: Uses setCounter() to add/update the current actor's entry in the
 *   counter map. This preserves other actors' contributions during merges.
 * - **currentNode is null**: Creates a simple version node (no counter structure) since only
 *   one actor has written. The version represents this actor's state.
 *
 * ## Version Monotonicity
 *
 * Uses ensureAfter() to guarantee versions never go backwards, maintaining causal ordering.
 */
interface CounterLocalResolver<T, N, V, C> : CrdtLocalResolver<T, N, V, C> {
    val toLong: (T?) -> Long

    override fun applyLocalWrite(
        currentValue: T?,
        currentNode: N?,
        currentVersion: V,
        newValue: T?,
        newVersion: V,
        context: ResolutionDeltaContext<N, C>,
    ): NodeMergeResult<T, N, Boolean> = with(versionTreeResolver) {
        val newValueLong = toLong(newValue)
        val difference = newValueLong - toLong(currentValue)

        // Fast path: no change
        if (difference == 0L) {
            return NodeMergeResult(
                resolution = false,
                value = currentValue,
                node = currentNode ?: createVersionNode(version = currentVersion),
            )
        }

        val finalVersion = newVersion.ensureAfter(currentNode?.versionValue ?: currentVersion)

        // If currentNode exists: update counter map (multi-actor case)
        // If currentNode is null: create simple version node (single-actor optimization)
        val node = currentNode?.plus(
            value = difference,
            version = finalVersion,
        ) ?: createVersionNode(version = finalVersion)

        context.addChange(
            newValue = newValue,
            encoder = encoder,
            versionNode = node,
        )

        return NodeMergeResult(
            resolution = true,
            value = newValue,
            node = node,
        )
    }
}
