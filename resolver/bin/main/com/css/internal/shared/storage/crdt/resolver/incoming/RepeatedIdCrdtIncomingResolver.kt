package com.css.internal.shared.storage.crdt.resolver.incoming

import com.css.internal.shared.storage.crdt.resolver.NodeMergeResult
import com.css.internal.shared.storage.crdt.resolver.ResolutionDeltaContext
import com.css.internal.shared.storage.crdt.resolver.version.ResolutionStrategy

/**
 * CRDT resolver for handling incoming changes to repeated fields containing identifiable items.
 *
 * This resolver transforms lists into maps using item IDs, delegates conflict resolution to a map
 * resolver, then converts the result back to a list. This approach enables fine-grained conflict
 * resolution at the individual item level rather than treating the entire list as a single unit.
 *
 * ## Conflict Resolution Strategy
 * 1. **Transform**: Convert `List<V>` to `Map<K, V>` using the key transformer
 * 2. **Handle Duplicates**: Items with identical keys use the last occurrence (standard associateBy behavior)
 * 3. **Delegate**: Use the underlying map resolver for item-by-item conflict resolution
 * 4. **Convert**: Transform the resolved map back to `List<V>` preserving item order
 *
 * ## Duplicate Key Handling
 * When multiple items have the same key, the **last occurrence wins** (standard `associateBy` behavior).
 * This provides robust handling of scenarios where:
 * - Writers accidentally create duplicate entries
 * - Data processing results in temporary duplicates
 * - Import operations contain overlapping data
 *
 * The last-wins strategy ensures deterministic behavior while being forgiving of input data quality issues.
 *
 * @param K The type of the identifier for each list item
 * @param V The type of the list items
 * @property keyTransformer Function that extracts the identifier from each list item.
 *                         If duplicate keys exist, the last occurrence will be used.
 * @property mapIncomingResolver The underlying map resolver that handles per-item conflict resolution.
 *                      This determines the actual merge strategy for individual items.
 *
 * @see MapCrdtIncomingResolver for the underlying map-based conflict resolution
 * @see CrdtIncomingResolver for the base resolver interface
 */
interface RepeatedIdCrdtIncomingResolver<K, V, Node, Version, C> :
    CrdtIncomingResolver<List<V>, Node, Version, C> {
    val keyTransformer: (V) -> K
    val mapIncomingResolver: CrdtIncomingResolver<Map<K, V>, Node, Version, C>

    override fun resolveConflict(
        localValue: List<V>?,
        localNode: Node?,
        localVersion: Version,
        incomingValue: List<V>?,
        incomingNode: Node?,
        incomingVersion: Version,
        context: ResolutionDeltaContext<Node, C>,
    ): NodeMergeResult<List<V>, Node, ResolutionStrategy> {
        val localMap = localValue?.associateBy { keyTransformer(it) }
        val incomingMap = incomingValue?.associateBy { keyTransformer(it) }
        return mapIncomingResolver.resolveConflict(
            localValue = localMap,
            localNode = localNode,
            localVersion = localVersion,
            incomingValue = incomingMap,
            incomingNode = incomingNode,
            incomingVersion = incomingVersion,
            context = context,
        ).run {
            NodeMergeResult(
                node = node,
                resolution = resolution,
                value = value?.values?.toMutableList(),
            )
        }
    }
}
