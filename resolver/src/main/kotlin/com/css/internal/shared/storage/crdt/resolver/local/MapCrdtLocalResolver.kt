package com.css.internal.shared.storage.crdt.resolver.local

import com.css.internal.shared.storage.crdt.resolver.NodeMergeResult
import com.css.internal.shared.storage.crdt.resolver.ResolutionDeltaContext
import com.css.internal.shared.storage.crdt.resolver.descriptor.CollectionType
import com.css.internal.shared.storage.crdt.resolver.withPath

/**
 * Base interface for CRDT map local write operations.
 *
 * Handles local updates to maps by tracking versions per key, enabling
 * fine-grained conflict resolution when syncing with remote changes.
 *
 * @param K Map key type (String, Int, Long, or Boolean)
 * @param V Map value type
 */
interface MapCrdtLocalResolver<K, V, Node, Version, C> :
    CrdtLocalResolver<Map<K, V>, Node, Version, C> {
    val config: CollectionType.Map
    val valueLocalResolver: CrdtLocalResolver<V, Node, Version, C>

    /**
     * Applies a local write to a map, updating only changed keys.
     *
     * @param currentMapValue Current map state
     * @param currentNode Version tracking for the map
     * @param currentVersion Map-level version (inherited by keys without specific versions)
     * @param newMapValue New map state being written
     * @param newVersion Version to apply to changed keys
     * @param existingEntries Current per-key version tracking
     * @param versionNode Factory to create typed Node
     */
    fun applyLocalWrite(
        currentMapValue: Map<K, V>,
        currentNode: Node?,
        currentVersion: Version,
        newMapValue: Map<K, V>,
        newVersion: Version,
        existingEntries: Map<K, Node>?,
        context: ResolutionDeltaContext<Node, C>,
        withPath: (K, () -> Unit) -> Unit,
        versionNode: (Version, Map<K, Node>) -> Node,
    ): NodeMergeResult<Map<K, V>, Node, Boolean> = with(versionTreeResolver) {
        with(valueLocalResolver) {
            // Fast path: no changes
            if (currentMapValue == newMapValue) {
                return NodeMergeResult(
                    resolution = false,
                    value = currentMapValue.toMutableMap(),
                    node = currentNode ?: createVersionNode(currentVersion),
                )
            }

            val entries = existingEntries?.toMutableMap() ?: mutableMapOf()

            // Using an ordered map for cases where we are resolving a repeated field with IDs and keep order for
            // protobuf maps that are represented as a repeated field of map entries which technically are ordered.
            val resultMap = LinkedHashMap<K, V>(maxOf(currentMapValue.size, newMapValue.size))

            var resolution = false
            var tombstoneCreated = false
            fun resolveKey(key: K, currentValue: V?, newValue: V?) = withPath(key) {
                val keyResolution = applyKeyValue(
                    key = key,
                    currentValue = currentMapValue[key],
                    currentVersion = currentNode?.versionValue ?: currentVersion,
                    newValue = newValue,
                    newVersion = newVersion,
                    resultMap = resultMap,
                    entries = entries,
                    context = context,
                )
                resolution = keyResolution || resolution
                // Tombstone created when we're deleting an existing entry (currentValue exists, newValue is null)
                if (keyResolution && (currentValue != null && newValue == null)) {
                    tombstoneCreated = true
                }
            }

            // Process all keys in new map (additions and updates)
            newMapValue.forEach { (key, newValue) ->
                resolveKey(
                    key = key,
                    currentValue = currentMapValue[key],
                    newValue = newValue
                )
            }

            // Process removed keys (in current but not in new)
            currentMapValue.forEach { (key, currentValue) ->
                if (key !in newMapValue) {
                    resolveKey(
                        key = key,
                        currentValue = currentValue,
                        newValue = null
                    )
                }
            }

            return if (resolution) {
                val newVersion = newVersion.ensureAfter(currentVersion)

                // Cleanup tombstones only if a tombstone was created in this write
                if (tombstoneCreated) {
                    cleanupTombstones(
                        entries = entries,
                        liveKeys = resultMap.keys,
                        newVersion = newVersion,
                        context = context,
                    )
                }

                NodeMergeResult(
                    resolution = true,
                    value = resultMap,
                    node = versionNode(newVersion, entries)
                )
            } else {
                NodeMergeResult(
                    resolution = false,
                    value = resultMap,
                    node = currentNode ?: createVersionNode(currentVersion)
                )
            }
        }
    }

    /**
     * Cleans up tombstones based on TTL and max tombstone limits.
     *
     * Respects TTL first (if defined), then max tombstone count. Updates live data
     * versions to stay within TTL window when the window shifts.
     *
     * @param entries Mutable map of all entries (live data and tombstones)
     * @param liveKeys Keys that have live data (not tombstones)
     * @param newVersion Version from the write that triggered cleanup
     * @param context Resolution context
     */
    private fun cleanupTombstones(
        entries: MutableMap<K, Node>,
        liveKeys: Set<K>,
        newVersion: Version,
        context: ResolutionDeltaContext<Node, C>,
    ) = with(versionTreeResolver) {
        if (entries.isEmpty()) return

        val maxTombstones = config.maxTombstone
        val ttl = config.tombstoneTtl

        if (ttl == null && entries.size <= maxTombstones) return

        // TTL-based cleanup (if TTL is defined)
        if (ttl != null && ttl > 0) {
            // Calculate TTL window: [maxVersion - ttl, maxVersion]
            val windowStart = newVersion - ttl

            // Collect keys to remove to avoid ConcurrentModificationException
            val keysToRemove = mutableListOf<K>()

            entries.forEach { (key, _) ->
                if (key in liveKeys) {
                    // Update live data versions that fall outside the window
                    val node = entries[key]
                    val version = node?.versionValue
                    if (version != null && version < windowStart) {
                        // Update version to window start timestamp with new write's actor info
                        entries[key] = createVersionNode(windowStart)
                    }
                } else {
                    // Mark tombstones outside the window for removal
                    val version = entries[key]?.versionValue
                    if (version != null && version < windowStart) {
                        keysToRemove.add(key)
                    }
                }
            }

            // Remove tombstones after iteration
            keysToRemove.forEach { entries.remove(it) }
        }

        // Identify tombstones (entries without live data)
        val tombstoneEntries = entries.entries.filter { it.key !in liveKeys }
        val toRemoveCount = tombstoneEntries.size - maxTombstones

        if (toRemoveCount > 0) {
            val tombstoneEntries = tombstoneEntries.sortedWith(
                compareBy(comparator = versionTreeResolver) { it.value?.versionValue ?: minVersion }
            )
            // Remove oldest tombstones FIFO until we're at the limit
            tombstoneEntries.take(toRemoveCount).forEach { (key, _) ->
                entries.remove(key)
            }
        }
    }

    /**
     * Applies a local write to a single key-value pair.
     *
     * Handles the special case where unchanged values still need version tracking
     * for proper conflict resolution later.
     */
    private fun CrdtLocalResolver<V, Node, Version, C>.applyKeyValue(
        currentVersion: Version,
        newVersion: Version,
        key: K,
        newValue: V?,
        currentValue: V?,
        resultMap: MutableMap<K, V>,
        entries: MutableMap<K, Node>,
        context: ResolutionDeltaContext<Node, C>,
    ): Boolean = with(versionTreeResolver) {
        return if (currentValue == newValue) {
            // Value unchanged, but ensure version tracking exists
            newValue?.let {
                resultMap[key] = newValue
                // Initialize version for previously untracked keys
                // This ensures initial values have proper version info for conflict resolution
                if (!entries.contains(key)) {
                    entries[key] = createVersionNode(currentVersion)
                }
            }
            false
        } else {
            // Value changed - delegate to value resolver
            val result = applyLocalWrite(
                currentValue = currentValue,
                currentNode = entries[key],
                currentVersion = currentVersion,
                newValue = newValue,
                newVersion = newVersion,
                context = context,
            )

            val resultValue = result.value
            if (resultValue != null) {
                resultMap[key] = resultValue
            }

            val resultNode = result.node
            if (resultNode != null) {
                entries[key] = resultNode
            }
            result.resolution
        }
    }
}

/**
 * String-keyed map local resolver.
 *
 * Stores per-key versions in Node.string_map field.
 */
interface StringMapCrdtLocalResolver<V, Node, Version, C> :
    MapCrdtLocalResolver<String, V, Node, Version, C> {
    override fun applyLocalWrite(
        currentValue: Map<String, V>?,
        currentNode: Node?,
        currentVersion: Version,
        newValue: Map<String, V>?,
        newVersion: Version,
        context: ResolutionDeltaContext<Node, C>,
    ): NodeMergeResult<Map<String, V>, Node, Boolean> = with(versionTreeResolver) {
        return applyLocalWrite(
            currentMapValue = currentValue ?: mapOf(),
            currentNode = currentNode,
            currentVersion = currentVersion,
            newMapValue = newValue ?: mapOf(),
            newVersion = newVersion,
            existingEntries = currentNode?.stringMap,
            context = context,
            withPath = { key, perform ->
                context.withPath(versionTreeResolver.createPathComponentStringKey(key), perform)
            }
        ) { version, entries ->
            createVersionNodeStringMap(
                version = version,
                entries = entries
            )
        }
    }
}

/**
 * Int-keyed map local resolver.
 *
 * Stores per-key versions in Node.int_map field.
 */
interface IntMapCrdtLocalResolver<V, Node, Version, C> :
    MapCrdtLocalResolver<Int, V, Node, Version, C> {
    override fun applyLocalWrite(
        currentValue: Map<Int, V>?,
        currentNode: Node?,
        currentVersion: Version,
        newValue: Map<Int, V>?,
        newVersion: Version,
        context: ResolutionDeltaContext<Node, C>,
    ): NodeMergeResult<Map<Int, V>, Node, Boolean> = with(versionTreeResolver) {
        return applyLocalWrite(
            currentMapValue = currentValue ?: mapOf(),
            currentNode = currentNode,
            currentVersion = currentVersion,
            newMapValue = newValue ?: mapOf(),
            newVersion = newVersion,
            existingEntries = currentNode?.intMap,
            context = context,
            withPath = { key, perform ->
                context.withPath(versionTreeResolver.createPathComponentIntKey(key), perform)
            }
        ) { version, entries ->
            createVersionNodeIntMap(
                version = version,
                entries = entries
            )
        }
    }
}

/**
 * Long-keyed map local resolver.
 *
 * Stores per-key versions in Node.int64_map field.
 */
interface LongMapCrdtLocalResolver<V, Node, Version, C> :
    MapCrdtLocalResolver<Long, V, Node, Version, C> {
    override fun applyLocalWrite(
        currentValue: Map<Long, V>?,
        currentNode: Node?,
        currentVersion: Version,
        newValue: Map<Long, V>?,
        newVersion: Version,
        context: ResolutionDeltaContext<Node, C>,
    ): NodeMergeResult<Map<Long, V>, Node, Boolean> = with(versionTreeResolver) {
        return applyLocalWrite(
            currentMapValue = currentValue ?: mapOf(),
            currentNode = currentNode,
            currentVersion = currentVersion,
            newMapValue = newValue ?: mapOf(),
            newVersion = newVersion,
            existingEntries = currentNode?.longMap,
            context = context,
            withPath = { key, perform ->
                context.withPath(versionTreeResolver.createPathComponentLongKey(key), perform)
            },
        ) { version, entries ->
            createVersionNodeLongMap(
                version = version,
                entries = entries
            )
        }
    }
}

/**
 * Boolean-keyed map local resolver.
 *
 * Stores per-key versions in Node.bool_map field.
 * Limited to maximum 2 entries (true/false keys).
 */
interface BooleanMapCrdtLocalResolver<V, Node, Version, C> :
    MapCrdtLocalResolver<Boolean, V, Node, Version, C> {
    override fun applyLocalWrite(
        currentValue: Map<Boolean, V>?,
        currentNode: Node?,
        currentVersion: Version,
        newValue: Map<Boolean, V>?,
        newVersion: Version,
        context: ResolutionDeltaContext<Node, C>,
    ): NodeMergeResult<Map<Boolean, V>, Node, Boolean> = with(versionTreeResolver) {
        return applyLocalWrite(
            currentMapValue = currentValue ?: mapOf(),
            currentNode = currentNode,
            currentVersion = currentVersion,
            newMapValue = newValue ?: mapOf(),
            newVersion = newVersion,
            existingEntries = currentNode?.booleanMap,
            context = context,
            withPath = { key, perform ->
                context.withPath(versionTreeResolver.createPathComponentBooleanKey(key), perform)
            },
        ) { version, entries ->
            createVersionNodeBoolMap(
                version = version,
                entries = entries
            )
        }
    }
}
