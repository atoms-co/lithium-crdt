package com.css.protobuf.crdt.resolver.incoming

import com.css.protobuf.crdt.resolver.NodeMergeResult
import com.css.protobuf.crdt.resolver.ResolutionDeltaContext
import com.css.protobuf.crdt.resolver.version.ResolutionStrategy
import com.css.protobuf.crdt.resolver.version.ResolutionStrategy.INCOMING
import com.css.protobuf.crdt.resolver.version.ResolutionStrategy.LOCAL
import com.css.protobuf.crdt.resolver.version.ResolutionStrategy.MERGED_VALUES
import com.css.protobuf.crdt.resolver.version.ResolutionStrategy.NO_CHANGE
import com.css.protobuf.crdt.resolver.withPath

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
interface RepeatedCrdtIncomingResolver<E, N, V, C> : CrdtIncomingResolver<List<E>, N, V, C> {
    val valueIncomingResolver: CrdtIncomingResolver<E, N, V, C>

    @Suppress("CyclomaticComplexMethod")
    override fun resolveConflict(
        localValue: List<E>?,
        localNode: N?,
        localVersion: V,
        incomingValue: List<E>?,
        incomingNode: N?,
        incomingVersion: V,
        context: ResolutionDeltaContext<N, C>,
    ): NodeMergeResult<List<E>, N, ResolutionStrategy> = with(versionTreeResolver) {
        // Fast path: identical version nodes
        if (
            (localNode != null && incomingNode != null && localNode == incomingNode) ||
            (localNode == null && incomingNode == null && localVersion == incomingVersion)
        ) {
            return NodeMergeResult(
                resolution = NO_CHANGE,
                value = localValue?.toMutableList() ?: mutableListOf(),
                node = localNode ?: createVersionNode(localVersion),
            )
        }

        val localEntries = localNode?.entries
        val incomingEntries = incomingNode?.entries

        // List size determined by higher version (Last-Write-Wins for list length)
        // This means if one replica truncates a list, that wins if it has a higher version
        val size = if (localVersion > incomingVersion) {
            localValue?.size ?: 0
        } else {
            incomingValue?.size ?: 0
        }

        val resultEntries = ArrayList<N>(size)
        val resultList = ArrayList<E>(size)
        var resolution: ResolutionStrategy = NO_CHANGE

        // Process each index position independently
        repeat(size) { index ->
            val localEntryNode = localEntries?.getOrNull(index)
            val incomingEntryNode = incomingEntries?.getOrNull(index)
            val childLocalVersion = localEntryNode?.versionValue
            val incomingLocalVersion = incomingEntryNode?.versionValue

            // Delegate to element resolver for type-specific conflict resolution
            val result = context.withPath(versionTreeResolver.createPathComponentRepeatedIndex(index)) {
                valueIncomingResolver.resolveConflict(
                    localValue = localValue?.getOrNull(index),
                    localNode = localEntryNode,
                    // Use element version if available, otherwise list version
                    localVersion = childLocalVersion ?: localVersion,
                    incomingValue = incomingValue?.getOrNull(index),
                    incomingNode = incomingEntryNode,
                    incomingVersion = incomingLocalVersion ?: incomingVersion,
                    context = context,
                )
            }

            val resultValue = result.value
            if (resultValue != null) {
                resultList.add(resultValue)
                // Store element version for future conflict resolution
                resultEntries.add(
                    result.node ?: createVersionNode(
                        version = result.resolution.resolve(
                            local = localVersion,
                            incoming = incomingVersion,
                        )
                    )
                )
            }
            // Track overall resolution strategy
            resolution += result.resolution
        }

        return NodeMergeResult(
            resolution = resolution,
            value = resultList,
            node =
            when (resolution) {
                NO_CHANGE,
                LOCAL -> localNode ?: createVersionNode(localVersion)
                INCOMING -> incomingNode ?: createVersionNode(incomingVersion)
                MERGED_VALUES ->
                    // Create new node with merged version and element tracking
                    createVersionNodeRepeated(
                        version = localVersion.coerceAtLeast(incomingVersion),
                        entries = resultEntries
                    )
            },
        )
    }
}
