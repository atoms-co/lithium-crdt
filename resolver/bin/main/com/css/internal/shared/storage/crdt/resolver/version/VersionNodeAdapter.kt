package com.css.internal.shared.storage.crdt.resolver.version

/**
 * Adapter interface for versioned tree nodes in CRDT conflict resolution.
 *
 * This interface provides an abstraction layer for working with hierarchical data structures
 * where each node carries version information. It supports various node types including:
 * - Leaf nodes (scalar values with versions)
 * - Repeated nodes (lists/arrays)
 * - Struct nodes (field-based structures)
 * - Map nodes with different key types (String, Int, Long, Boolean)
 *
 * The adapter pattern allows the CRDT resolver to work with different underlying node
 * implementations without coupling to specific data structures.
 *
 * @param N The node type representing versioned data in the tree
 * @param V The version type (typically Version for vector clocks)
 *
 * @see VersionTreeResolver for the primary consumer of this adapter
 */
interface VersionNodeAdapter<N, V> {
    /**
     * The version associated with this node, or null if no version is present.
     */
    val N.versionValue: V?

    /**
     * List of child nodes for repeated/array structures.
     * Empty if this is not a repeated node.
     */
    val N.entries: List<N>

    /**
     * Map of field number to child node for struct/message structures.
     * Empty if this is not a struct node.
     */
    val N.fields: Map<Int, N>

    /**
     * Map of string keys to child nodes for string-keyed map structures.
     * Empty if this is not a string map node.
     */
    val N.stringMap: Map<String, N>

    /**
     * Map of int32 keys to child nodes for integer-keyed map structures.
     * Empty if this is not an int32 map node.
     */
    val N.intMap: Map<Int, N>

    /**
     * Map of int64 keys to child nodes for long-keyed map structures.
     * Empty if this is not an int64 map node.
     */
    val N.longMap: Map<Long, N>

    /**
     * Map of boolean keys to child nodes for boolean-keyed map structures.
     * Empty if this is not a bool map node.
     */
    val N.booleanMap: Map<Boolean, N>

    /**
     * The total counter value, computed as the sum of all per-actor contributions.
     * Returns 0 if this is not a counter node.
     *
     * For single-actor counters, this is the single actor's value.
     * For multi-actor counters, this is the sum across all actors in the counter map.
     */
    val N.counterValue: Long

    /**
     * The number of actors that have contributed to this counter.
     * Returns 0 if this is not a counter node.
     *
     * - 0 actors: Not a counter node or empty counter
     * - 1 actor: Single-actor optimization (counter structure may not be needed)
     * - 2+ actors: Multi-actor counter requiring counter map structure
     */
    val N.counterActors: Int

    /**
     * Creates a simple leaf node with only a version.
     *
     * @param version The version to associate with this node
     * @return A new leaf node containing only the version
     */
    fun createVersionNode(version: V): N

    /**
     * Creates a repeated/array node with version and entries.
     *
     * @param version The version to associate with this node
     * @param entries The list of child nodes
     * @return A new repeated node containing the version and entries
     */
    fun createVersionNodeRepeated(version: V, entries: @JvmSuppressWildcards List<N>): N

    /**
     * Creates a struct/message node with version and numbered fields.
     *
     * @param version The version to associate with this node
     * @param fields Map of field numbers to child nodes
     * @return A new struct node containing the version and fields
     */
    fun createVersionNodeStruct(version: V, fields: @JvmSuppressWildcards Map<Int, N>): N

    /**
     * Creates a boolean-keyed map node with version and entries.
     *
     * @param version The version to associate with this node
     * @param entries Map of boolean keys to child nodes
     * @return A new bool map node containing the version and entries
     */
    fun createVersionNodeBoolMap(version: V, entries: @JvmSuppressWildcards Map<Boolean, N>): N

    /**
     * Creates an int32-keyed map node with version and entries.
     *
     * @param version The version to associate with this node
     * @param entries Map of int32 keys to child nodes
     * @return A new int32 map node containing the version and entries
     */
    fun createVersionNodeIntMap(version: V, entries: @JvmSuppressWildcards Map<Int, N>): N

    /**
     * Creates an int64-keyed map node with version and entries.
     *
     * @param version The version to associate with this node
     * @param entries Map of int64 keys to child nodes
     * @return A new int64 map node containing the version and entries
     */
    fun createVersionNodeLongMap(version: V, entries: @JvmSuppressWildcards Map<Long, N>): N

    /**
     * Creates a string-keyed map node with version and entries.
     *
     * @param version The version to associate with this node
     * @param entries Map of string keys to child nodes
     * @return A new string map node containing the version and entries
     */
    fun createVersionNodeStringMap(version: V, entries: @JvmSuppressWildcards Map<String, N>): N

    /**
     * Creates a counter node with initial value for a single actor.
     *
     * This creates a counter with one actor entry (the actor from the version).
     * The counter map will have one entry: `[version.actorId -> VersionCount(version, value)]`
     *
     * @param version The version containing the actor ID and actor version
     * @param value The initial counter value for this actor
     * @return A new counter node with a single actor contribution
     */
    fun createVersionNodeCounter(version: V, value: Long): N

    /**
     * Increments this counter node by a specific amount for the given actor.
     *
     * **Important**: This adds `value` to the current counter for the actor, it does NOT set
     * the counter to `value`. To increment from 10 to 25, call `plus(15, version)`, not
     * `plus(25, version)`.
     *
     * ## Semantics:
     * - Adds `value` to the current counter value for `version.actorId`
     * - If the actor doesn't exist in the counter, initializes to `value`
     * - Updates the actor's version to `version.actorVersion`
     *
     * ## Usage Example:
     * ```kotlin
     * // Current counter for actor 1: 10
     * // Want to increment to 25
     * val increment = 25 - 10  // Calculate difference: 15
     * val updated = counter.plus(increment, newVersion)  // Adds 15
     * // Result: counter for actor 1 is now 25
     * ```
     *
     * @param value The amount to add to this actor's counter (NOT the final value)
     * @param version The version containing the actor ID and actor version
     * @return A new counter node with the incremented value
     */
    fun N.plus(value: Long, version: V): N

    /**
     * Merges two counter nodes from different actors.
     *
     * Combines the per-actor contributions from both counter nodes. For each actor:
     * - If actor only exists in one node, include that actor's contribution
     * - If actor exists in both nodes, use last-write-wins based on version
     *
     * The result is a counter node with all unique actor contributions, where
     * conflicts for the same actor are resolved by taking the higher version.
     *
     * ## Example:
     * ```kotlin
     * // Node A: {actor1: (v=5, val=20)}
     * // Node B: {actor2: (v=3, val=15)}
     * // Merged: {actor1: (v=5, val=20), actor2: (v=3, val=15)}
     * // Total counter value: 20 + 15 = 35
     * ```
     *
     * @param other The other counter node to merge with this one
     * @return A new counter node containing the merged actor contributions
     */
    fun N.mergeCounter(other: N): N

    /**
     * Determines if this node is a leaf node (contains no child structures that require traversal).
     *
     * A node is considered a leaf if:
     * - All collection properties are empty (fields, entries, and all map types)
     * - Counter has ≤1 actor (single-actor optimization)
     *
     * ## Counter Leaf Optimization
     *
     * Counter nodes with ≤1 actor are treated as leaves because:
     * - **Single-actor counters are unnecessary**: The node's version alone represents the
     *   actor's state, and the value is stored in parallel data. No counter structure needed.
     * - **Multi-actor counters (2+) require traversal**: When multiple actors contribute,
     *   the counter map structure must be traversed to merge per-actor version vectors.
     *
     * This optimization avoids creating counter map structures for the common case where
     * only one actor has written to a counter value.
     *
     * @return true if this node has no children requiring traversal, false otherwise
     */
    fun N.isLeaf() =
        fields.isEmpty() &&
            entries.isEmpty() &&
            stringMap.isEmpty() &&
            intMap.isEmpty() &&
            longMap.isEmpty() &&
            booleanMap.isEmpty() &&
            counterActors < 2
}
