package com.css.internal.shared.storage.crdt.resolver.incoming

import com.css.internal.shared.storage.crdt.resolver.NodeMergeResult
import com.css.internal.shared.storage.crdt.resolver.ResolutionDeltaContext
import com.css.internal.shared.storage.crdt.resolver.version.ResolutionStrategy
import com.css.internal.shared.storage.crdt.resolver.version.ResolutionStrategy.INCOMING
import com.css.internal.shared.storage.crdt.resolver.version.ResolutionStrategy.LOCAL
import com.css.internal.shared.storage.crdt.resolver.version.ResolutionStrategy.MERGED_VALUES
import com.css.internal.shared.storage.crdt.resolver.version.ResolutionStrategy.NO_CHANGE
import com.css.internal.shared.storage.crdt.resolver.withPath

/**
 * Base interface for CRDT map conflict resolution.
 *
 * Provides per-key conflict resolution for maps, allowing different keys to have
 * different versions and merge independently. This enables fine-grained updates
 * where only changed keys need new versions.
 *
 * @param K Map key type (String, Int, Long, or Boolean)
 * @param V Map value type
 */
interface MapCrdtIncomingResolver<K, V, Node, Version, C> : CrdtIncomingResolver<Map<K, V>, Node, Version, C> {
    val valueIncomingResolver: CrdtIncomingResolver<V, Node, Version, C>

    @Suppress("CyclomaticComplexMethod")
    fun CrdtIncomingResolver<V, Node, Version, C>.resolveConflict(
        localValueMap: Map<K, V>,
        localMapNode: Node?,
        localVersion: Version,
        localEntries: Map<K, Node>,
        incomingValueMap: Map<K, V>,
        incomingMapNode: Node?,
        incomingVersion: Version,
        incomingEntries: Map<K, Node>,
        context: ResolutionDeltaContext<Node, C>,
        withPath: (K, () -> Unit) -> Unit,
        versionNode: (Version, Map<K, Node>) -> Node,
    ): NodeMergeResult<Map<K, V>, Node, ResolutionStrategy> = with(versionTreeResolver) {
        // Fast path: identical maps
        if (
            (localMapNode != null && incomingMapNode != null && localMapNode == incomingMapNode) ||
            (localMapNode == null && incomingMapNode == null && localVersion == incomingVersion)
        ) {
            return NodeMergeResult(
                resolution = NO_CHANGE,
                value = localValueMap.toMutableMap(),
                node = localMapNode ?: createVersionNode(localVersion),
            )
        }

        // Using an ordered map for cases where we are resolving a repeated field with IDs and keep order for
        // protobuf maps that are represented as a repeated field of map entries which technically are ordered.
        val resultMap = LinkedHashMap<K, V>(maxOf(localValueMap.size, incomingValueMap.size))
        val resultEntries = localEntries.toMutableMap()

        var resolution: ResolutionStrategy = NO_CHANGE
        fun resolveKey(key: K, localValueNode: Node?, incomingValueNode: Node?) {
            withPath(key) {
                resolution += resolveKeyValue(
                    key = key,
                    localValue = localValueMap[key],
                    localValueNode = localValueNode,
                    // Use key-specific version if available, otherwise map version
                    localVersion = localValueNode?.versionValue ?: localVersion,
                    incomingValue = incomingValueMap[key],
                    incomingValueNode = incomingValueNode,
                    incomingVersion = incomingValueNode?.versionValue ?: incomingVersion,
                    resultMap = resultMap,
                    resultEntries = resultEntries,
                    context = context,
                )
            }
        }

        // Process the later version map first to ensure ordered repeated storage is reflected.
        val localKeys = localEntries.keys.takeIf { it.isNotEmpty() } ?: localValueMap.keys
        val incomingKeys = incomingEntries.keys.takeIf { it.isNotEmpty() } ?: incomingValueMap.keys
        // Sets assumed coming from `LinkedHashMap` and default addition is `LinkedHashSet`
        val mergedKeys = if (localVersion > incomingVersion) {
            localKeys + incomingKeys
        } else {
            incomingKeys + localKeys
        }

        // Use version entries if available, otherwise use value keys
        mergedKeys.forEach { key ->
            resolveKey(
                key = key,
                localValueNode = localEntries[key],
                incomingValueNode = incomingEntries[key],
            )
        }

        return NodeMergeResult(
            resolution = resolution,
            value = resultMap,
            node = when (resolution) {
                NO_CHANGE,
                LOCAL -> localMapNode ?: createVersionNode(localVersion)
                INCOMING -> incomingMapNode ?: createVersionNode(incomingVersion)
                MERGED_VALUES -> versionNode(
                    // Use highest version when keys from both maps were merged
                    localVersion.coerceAtLeast(incomingVersion),
                    resultEntries
                )
            },
        )
    }

    fun CrdtIncomingResolver<V, Node, Version, C>.resolveKeyValue(
        key: K,
        localValue: V?,
        localValueNode: Node?,
        localVersion: Version,
        incomingValue: V?,
        incomingValueNode: Node?,
        incomingVersion: Version,
        resultEntries: MutableMap<K, Node>,
        resultMap: MutableMap<K, V>,
        context: ResolutionDeltaContext<Node, C>,
    ): ResolutionStrategy = with(versionTreeResolver) {
        val result = if (incomingValue == null && incomingValueNode == null && localValue != null) {
            NodeMergeResult(
                resolution = LOCAL,
                value = localValue,
                node = localValueNode,
            )
        } else if (localValue == null && localValueNode == null && incomingValue != null) {
            NodeMergeResult(
                resolution = INCOMING,
                value = incomingValue,
                node = incomingValueNode,
            ).also {
                context.addChange(
                    newValue = it.value,
                    versionNode = it.node ?: createVersionNode(incomingVersion),
                    encoder = this@resolveKeyValue.encoder
                )
            }
        } else {
            resolveConflict(
                localValue = localValue,
                localNode = localValueNode,
                localVersion = localVersion,
                incomingValue = incomingValue,
                incomingNode = incomingValueNode,
                incomingVersion = incomingVersion,
                context = context,
            )
        }

        val resultValue = result.value
        if (resultValue != null) {
            resultMap[key] = resultValue
        }
        // Store version for this key, creating one if needed
        resultEntries[key] = result.node ?: createVersionNode(
            version = result.resolution.resolve(
                local = localVersion,
                incoming = incomingVersion
            )
        )

        return result.resolution
    }
}

/**
 * String-keyed map CRDT resolver.
 *
 * Stores per-key versions in Node.string_map field.
 */
interface StringMapCrdtIncomingResolver<V, Node, Version, C> :
    MapCrdtIncomingResolver<String, V, Node, Version, C> {
    override fun resolveConflict(
        localValue: Map<String, V>?,
        localNode: Node?,
        localVersion: Version,
        incomingValue: Map<String, V>?,
        incomingNode: Node?,
        incomingVersion: Version,
        context: ResolutionDeltaContext<Node, C>,
    ) = with(versionTreeResolver) {
        valueIncomingResolver.resolveConflict(
            localValueMap = localValue ?: mapOf(),
            localMapNode = localNode,
            localVersion = localNode?.versionValue ?: localVersion,
            localEntries = localNode?.stringMap ?: mapOf(),
            incomingValueMap = incomingValue ?: mapOf(),
            incomingMapNode = incomingNode,
            incomingVersion = incomingNode?.versionValue ?: incomingVersion,
            incomingEntries = incomingNode?.stringMap ?: mapOf(),
            context = context,
            withPath = { key, perform ->
                context.withPath(versionTreeResolver.createPathComponentStringKey(key), perform)
            },
        ) { version, entries ->
            createVersionNodeStringMap(
                version = version,
                entries = entries
            )
        }
    }
}

/**
 * Int-keyed map CRDT resolver.
 *
 * Stores per-key versions in Node.int_map field.
 */
interface IntMapCrdtIncomingResolver<V, Node, Version, C> :
    MapCrdtIncomingResolver<Int, V, Node, Version, C> {
    override fun resolveConflict(
        localValue: Map<Int, V>?,
        localNode: Node?,
        localVersion: Version,
        incomingValue: Map<Int, V>?,
        incomingNode: Node?,
        incomingVersion: Version,
        context: ResolutionDeltaContext<Node, C>,
    ) = with(versionTreeResolver) {
        valueIncomingResolver.resolveConflict(
            localValueMap = localValue ?: mapOf(),
            localMapNode = localNode,
            localVersion = localNode?.versionValue ?: localVersion,
            localEntries = localNode?.intMap ?: mapOf(),
            incomingValueMap = incomingValue ?: mapOf(),
            incomingMapNode = incomingNode,
            incomingVersion = incomingNode?.versionValue ?: incomingVersion,
            incomingEntries = incomingNode?.intMap ?: mapOf(),
            context = context,
            withPath = { key, perform ->
                context.withPath(versionTreeResolver.createPathComponentIntKey(key), perform)
            },
        ) { version, entries ->
            createVersionNodeIntMap(
                version = version,
                entries = entries
            )
        }
    }
}

/**
 * Long-keyed map CRDT resolver.
 *
 * Stores per-key versions in Node.int64_map field.
 */
interface LongMapCrdtIncomingResolver<V, Node, Version, C> :
    MapCrdtIncomingResolver<Long, V, Node, Version, C> {
    override fun resolveConflict(
        localValue: Map<Long, V>?,
        localNode: Node?,
        localVersion: Version,
        incomingValue: Map<Long, V>?,
        incomingNode: Node?,
        incomingVersion: Version,
        context: ResolutionDeltaContext<Node, C>,
    ) = with(versionTreeResolver) {
        valueIncomingResolver.resolveConflict(
            localValueMap = localValue ?: mapOf(),
            localMapNode = localNode,
            localVersion = localNode?.versionValue ?: localVersion,
            localEntries = localNode?.longMap ?: mapOf(),
            incomingValueMap = incomingValue ?: mapOf(),
            incomingMapNode = incomingNode,
            incomingVersion = incomingNode?.versionValue ?: incomingVersion,
            incomingEntries = incomingNode?.longMap ?: mapOf(),
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
 * Boolean-keyed map CRDT resolver.
 *
 * Stores per-key versions in Node.bool_map field.
 * Note: Boolean maps can only have up to 2 entries (true/false).
 */
interface BooleanMapCrdtIncomingResolver<V, Node, Version, C> :
    MapCrdtIncomingResolver<Boolean, V, Node, Version, C> {
    override fun resolveConflict(
        localValue: Map<Boolean, V>?,
        localNode: Node?,
        localVersion: Version,
        incomingValue: Map<Boolean, V>?,
        incomingNode: Node?,
        incomingVersion: Version,
        context: ResolutionDeltaContext<Node, C>,
    ) = with(versionTreeResolver) {
        valueIncomingResolver.resolveConflict(
            localValueMap = localValue ?: mapOf(),
            localMapNode = localNode,
            localVersion = localNode?.versionValue ?: localVersion,
            localEntries = localNode?.booleanMap ?: mapOf(),
            incomingValueMap = incomingValue ?: mapOf(),
            incomingMapNode = incomingNode,
            incomingVersion = incomingNode?.versionValue ?: incomingVersion,
            incomingEntries = incomingNode?.booleanMap ?: mapOf(),
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
