package com.css.protobuf.crdt.resolver.local

import com.css.protobuf.crdt.resolver.NodeMergeResult
import com.css.protobuf.crdt.resolver.ResolutionDeltaContext
import com.css.protobuf.crdt.resolver.withPath

/**
 * CRDT resolver for local writes to lists/repeated fields.
 *
 * Handles list updates by tracking versions per element, allowing fine-grained
 * conflict resolution when lists are later merged with remote changes.
 *
 * Key behaviors:
 * - Each list element gets its own version tracking
 * - Supports lists that grow, shrink, or have elements modified
 * - Delegates element-level resolution to the provided valueLocalResolver
 *
 * @param V The list element type
 * @param valueLocalResolver Resolver for individual list elements (e.g., primitives, messages)
 */
interface RepeatedCrdtLocalResolver<T, N, V, C> : CrdtLocalResolver<List<T>, N, V, C> {
    val valueLocalResolver: CrdtLocalResolver<T, N, V, C>

    /**
     * Applies a local write to a list, tracking versions per element.
     *
     * @param currentValue Current list state
     * @param currentNode Version tracking for current list (contains per-element versions)
     * @param currentVersion Fallback version if no element-specific version exists
     * @param newValue New list state being written
     * @param newVersion Version to apply to changed elements
     * @return Result with updated list and version tracking
     */
    override fun applyLocalWrite(
        currentValue: List<T>?,
        currentNode: N?,
        currentVersion: V,
        newValue: List<T>?,
        newVersion: V,
        context: ResolutionDeltaContext<N, C>,
    ): NodeMergeResult<List<T>, N, Boolean> = with(versionTreeResolver) {
        // Fast path: unchanged list
        if (currentValue == newValue) {
            return NodeMergeResult(
                resolution = false,
                value = currentValue ?: listOf(),
                node = currentNode ?: createVersionNode(currentVersion)
            )
        }

        val existingEntries = currentNode?.entries ?: listOf()

        // Calculate max size to handle lists that grow, shrink, or have gaps
        // This ensures we process all positions that have ever had data
        val maxSize = maxOf(existingEntries.size, currentValue?.size ?: 0, newValue?.size ?: 0)

        val resultEntries = ArrayList<N>(maxSize)
        val resultList = ArrayList<T>(maxSize)
        var resolution = false

        // Process each index position independently
        repeat(maxSize) { index ->
            val existingNode = existingEntries.getOrNull(index)

            // Delegate to element resolver for type-specific handling
            val result = context.withPath(versionTreeResolver.createPathComponentRepeatedIndex(index)) {
                valueLocalResolver
                    .applyLocalWrite(
                        currentValue = currentValue?.getOrNull(index),
                        currentNode = existingNode,
                        // Use element's version if available, otherwise fall back to parent version
                        currentVersion = currentNode?.versionValue ?: currentVersion,
                        newValue = newValue?.getOrNull(index),
                        newVersion = newVersion,
                        context = context,
                    )
            }

            val resultValue = result.value
            val resultNode = result.node

            // Only add non-null values to result list
            // This handles list shrinking when elements are removed
            if (resultValue != null) {
                resultList.add(resultValue)
            }

            // Track version for this position even if value is null
            // This preserves tombstones for proper conflict resolution
            if (resultNode != null) {
                resultEntries.add(resultNode)
            }

            resolution = result.resolution || resolution
        }

        return NodeMergeResult(
            resolution = resolution,
            value = resultList,
            node = if (resolution) {
                // Only create new node if something changed
                createVersionNodeRepeated(
                    version = resultEntries.maxVersion(currentVersion),
                    entries = resultEntries
                )
            } else {
                currentNode ?: createVersionNode(currentVersion)
            }
        )
    }
}
