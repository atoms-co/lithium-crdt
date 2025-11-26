package com.css.protobuf.crdt.resolver.incoming.partial

import com.css.protobuf.crdt.resolver.ChangeEvent
import com.css.protobuf.crdt.resolver.NodeMergeResult
import com.css.protobuf.crdt.resolver.ResolutionDeltaContext
import com.css.protobuf.crdt.resolver.incoming.RepeatedCrdtIncomingResolver
import com.css.protobuf.crdt.resolver.version.ResolutionStrategy
import com.css.protobuf.crdt.resolver.version.ResolutionStrategy.INCOMING
import com.css.protobuf.crdt.resolver.version.ResolutionStrategy.LOCAL
import com.css.protobuf.crdt.resolver.version.ResolutionStrategy.MERGED_VALUES
import com.css.protobuf.crdt.resolver.version.ResolutionStrategy.NO_CHANGE
import com.css.protobuf.crdt.resolver.withPath
import kotlin.math.max

/**
 * CRDT resolver for lists/repeated fields with per-element conflict resolution.
 *
 * Resolves list conflicts using a position-based approach:
 * - List size is determined by the higher-versioned list
 * - Each position is resolved independently using element versions
 * - Supports lists that grow or shrink between versions
 *
 * This approach works well for lists where position matters (ordered lists)
 * but may not be ideal for sets or bags where order doesn't matter.
 *
 * @param V The list element type
 * @param valueIncomingResolver Resolver for individual list elements
 */
interface RepeatedCrdtIncomingChangeResolver<E, N, V, C> :
    RepeatedCrdtIncomingResolver<E, N, V, C>,
    CrdtIncomingChangeResolver<List<E>, N, V, C> {
    override val valueIncomingResolver: CrdtIncomingChangeResolver<E, N, V, C>

    override fun applyChanges(
        depth: Int,
        localValue: List<E>?,
        localNode: N?,
        localVersion: V,
        changes: List<ChangeEvent<*, N, C>>,
        context: ResolutionDeltaContext<N, C>
    ): NodeMergeResult<List<E>, N, ResolutionStrategy> = with(versionTreeResolver) {
        val groupedByIndex = changes.groupBy {
            it.pathComponents.getOrNull(depth)?.repeatedIndex ?: -1
        }

        // List size determined by higher version (Last-Write-Wins for list length)
        // This means if one replica truncates a list, that wins if it has a higher version
        val localEntries = localNode?.entries

        val size = max(localValue?.size ?: 0, groupedByIndex.keys.maxOrNull() ?: 0)
        val resultEntries = ArrayList<N>(size)
        val resultList = ArrayList<E>(size)
        var resolution: ResolutionStrategy = NO_CHANGE
        val nextDepth = depth + 1

        // Process each index position independently
        repeat(size) { index ->
            val localEntryNode = localEntries?.getOrNull(index)
            val childLocalVersion = localEntryNode?.versionValue
            val incomingChange = groupedByIndex[index]
            val valueChange = incomingChange?.first()

            // Delegate to element resolver for type-specific conflict resolution
            val result = if (valueChange == null) {
                NodeMergeResult(
                    resolution = LOCAL,
                    value = localValue?.getOrNull(index),
                    node = localEntryNode
                )
            } else {
                context.withPath(versionTreeResolver.createPathComponentRepeatedIndex(index)) {
                    if (valueChange.pathComponents.size == nextDepth) {
                        @Suppress("UNCHECKED_CAST")
                        valueIncomingResolver.resolveConflict(
                            localValue = localValue?.getOrNull(index),
                            localNode = localEntryNode,
                            // Use element version if available, otherwise list version
                            localVersion = childLocalVersion ?: localVersion,
                            incomingValue = valueChange.value as? E,
                            incomingNode = valueChange.versionNode,
                            incomingVersion = valueChange.versionNode.versionValue ?: minVersion,
                            context = context,
                        )
                    } else {
                        valueIncomingResolver.applyChanges(
                            depth = nextDepth,
                            localValue = localValue?.getOrNull(index),
                            localNode = localEntryNode,
                            // Use element version if available, otherwise list version
                            localVersion = childLocalVersion ?: localVersion,
                            changes = incomingChange,
                            context = context,
                        )
                    }
                }
            }

            val resultValue = result.value
            if (resultValue != null) {
                resultList.add(resultValue)
                // Store element version for future conflict resolution
                resultEntries.add(result.node ?: createVersionNode(version = localVersion))
            }
            // Track overall resolution strategy
            resolution += result.resolution
        }

        return NodeMergeResult(
            resolution = resolution,
            value = resultList,
            node = when (resolution) {
                NO_CHANGE,
                LOCAL -> localNode ?: createVersionNode(localVersion)
                INCOMING,
                MERGED_VALUES -> createVersionNodeRepeated(
                    // Use highest version if child nodes to get the latest write version encapsulated
                    resultEntries.maxVersion(localVersion),
                    resultEntries
                )
            },
        )
    }
}
