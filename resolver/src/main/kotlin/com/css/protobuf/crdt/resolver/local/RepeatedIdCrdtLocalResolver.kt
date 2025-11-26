package com.css.protobuf.crdt.resolver.local

import com.css.protobuf.crdt.resolver.NodeMergeResult
import com.css.protobuf.crdt.resolver.ResolutionDeltaContext

/**
 * CRDT resolver for applying local writes to repeated fields containing identifiable items.
 *
 * This resolver handles local modifications to lists by transforming them into maps using item IDs,
 * applying changes through a map resolver, then converting back to lists. This enables granular
 * change tracking and optimized version tree updates for individual items within the list.
 *
 * ## Write Application Strategy
 * 1. **Transform**: Convert both current and new lists to maps using the key transformer
 * 2. **Handle Duplicates**: Items with identical keys use the last occurrence (standard associateBy behavior)
 * 3. **Delegate**: Use the underlying map resolver for item-level change tracking
 * 4. **Convert**: Transform the updated map back to `List<V>` maintaining change semantics
 *
 * ## Duplicate Key Handling
 * When multiple items have the same key, the **last occurrence wins** (standard `associateBy` behavior).
 * This is particularly useful for:
 * - **Data Cleanup**: Automatically deduplicates problematic input data
 * - **Robust Processing**: Handle writer mistakes without failing operations
 * - **Deterministic Behavior**: Consistent last-wins strategy across all operations
 *
 * @param K The type of the identifier for each list item
 * @param V The type of the list items
 * @property keyTransformer Function that extracts the identifier from each list item.
 *                         If duplicate keys exist, the last occurrence will be used.
 * @property mapLocalResolver The underlying map resolver that handles per-item local write application.
 *                      This determines how individual item changes are tracked and versioned.
 *
 * @see MapCrdtLocalResolver for the underlying map-based write application
 * @see CrdtLocalResolver for the base local resolver interface
 */
interface RepeatedIdCrdtLocalResolver<K, T, N, V, C> : CrdtLocalResolver<List<T>, N, V, C> {
    val keyTransformer: (T) -> K
    val mapLocalResolver: CrdtLocalResolver<Map<K, T>, N, V, C>

    override fun applyLocalWrite(
        currentValue: List<T>?,
        currentNode: N?,
        currentVersion: V,
        newValue: List<T>?,
        newVersion: V,
        context: ResolutionDeltaContext<N, C>,
    ): NodeMergeResult<List<T>, N, Boolean> {
        val currentMap = currentValue?.associateBy { keyTransformer(it) }
        val newMap = newValue?.associateBy { keyTransformer(it) }
        return mapLocalResolver.applyLocalWrite(
            currentValue = currentMap,
            currentNode = currentNode,
            currentVersion = currentVersion,
            newValue = newMap,
            newVersion = newVersion,
            context = context,
        ).run {
            NodeMergeResult(
                node = node,
                resolution = resolution,
                value = value?.values?.toList(),
            )
        }
    }
}
