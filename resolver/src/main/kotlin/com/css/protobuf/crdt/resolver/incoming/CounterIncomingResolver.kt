package com.css.protobuf.crdt.resolver.incoming

import com.css.protobuf.crdt.resolver.NodeMergeResult
import com.css.protobuf.crdt.resolver.ResolutionDeltaContext
import com.css.protobuf.crdt.resolver.version.ResolutionStrategy

/**
 * Counter CRDT resolver for incoming changes and conflict resolution.
 *
 * ## Merge Semantics
 *
 * Counters use per-actor version vectors that are merged by taking the highest version
 * for each actor. The merge strategy is determined by structural equality (data class):
 *
 * - **INCOMING**: merged == incomingNode (incoming has all the latest actor versions)
 * - **LOCAL**: merged == localNode (local has all the latest actor versions)
 * - **MERGED_VALUES**: merged is a new combination (both sides have unique updates)
 *
 * ## Single vs Multi-Actor Optimization
 *
 * - **Single-actor nodes**: Simple version nodes without counter structure. The version
 *   represents the actor's state, and the value is stored in parallel data.
 * - **Multi-actor nodes**: Counter map structure tracks each actor's version and value.
 *   Total value is the sum across all actors.
 *
 * During merge, single-actor nodes are converted to counter structures when combined
 * with other actors' contributions via createVersionNodeCounter().
 */
interface CounterIncomingResolver<T, N, V, C> : CrdtIncomingResolver<T, N, V, C> {
    val toLong: (T?) -> Long
    val fromLong: (Long) -> T

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
            // Fast path: incoming is effectively empty (no node and zero value)
            if (incomingNode == null && localNode == null && incomingVersion == localVersion) {
                return NodeMergeResult(
                    value = localValue,
                    node = createVersionNode(localVersion),
                    resolution = ResolutionStrategy.NO_CHANGE,
                )
            }

            val localValueLong = toLong(localValue)
            val incomingValueLong = toLong(incomingValue)
            // Ensure incoming has a counter node structure for merging
            val incomingNode = incomingNode?.takeIf { it.counterActors > 0 }
                ?: createVersionNodeCounter(incomingVersion, incomingValueLong)
            // Convert single-actor local to counter structure if needed for merging
            val localNode = localNode?.takeIf { it.counterActors > 0 }
                ?: createVersionNodeCounter(localVersion, localValueLong)

            // Merge per-actor version vectors, taking highest version for each actor
            // Uses structural equality (data class) to determine resolution strategy
            when (val merged = localNode?.mergeCounter(incomingNode)) {
                null,
                incomingNode -> {
                    // Incoming has all the latest actor versions
                    NodeMergeResult(
                        value = incomingValue,
                        node = incomingNode,
                        resolution = ResolutionStrategy.INCOMING,
                    )
                }
                localNode -> {
                    // Local has all the latest actor versions
                    NodeMergeResult(
                        value = localValue,
                        node = localNode,
                        resolution = ResolutionStrategy.LOCAL,
                    )
                }
                else -> {
                    // Both sides contributed unique updates, create new merged node
                    NodeMergeResult(
                        value = fromLong(merged.counterValue),
                        node = merged,
                        resolution = ResolutionStrategy.MERGED_VALUES,
                    )
                }
            }.also {
                // Track changes for incoming or merged resolutions
                if (it.resolution == ResolutionStrategy.INCOMING || it.resolution == ResolutionStrategy.MERGED_VALUES)
                    context.addChange(
                        newValue = it.value,
                        encoder = encoder,
                        versionNode = it.node ?: createVersionNodeCounter(incomingVersion, incomingValueLong),
                    )
            }
        }
}
