package co.atoms.lithium.crdt.resolver.incoming.partial

import co.atoms.lithium.crdt.resolver.ChangeEvent
import co.atoms.lithium.crdt.resolver.NodeMergeResult
import co.atoms.lithium.crdt.resolver.ResolutionDeltaContext
import co.atoms.lithium.crdt.resolver.incoming.BooleanMapCrdtIncomingResolver
import co.atoms.lithium.crdt.resolver.incoming.IntMapCrdtIncomingResolver
import co.atoms.lithium.crdt.resolver.incoming.LongMapCrdtIncomingResolver
import co.atoms.lithium.crdt.resolver.incoming.MapCrdtIncomingResolver
import co.atoms.lithium.crdt.resolver.incoming.StringMapCrdtIncomingResolver
import co.atoms.lithium.crdt.resolver.version.ResolutionStrategy
import co.atoms.lithium.crdt.resolver.version.ResolutionStrategy.INCOMING
import co.atoms.lithium.crdt.resolver.version.ResolutionStrategy.LOCAL
import co.atoms.lithium.crdt.resolver.version.ResolutionStrategy.MERGED_VALUES
import co.atoms.lithium.crdt.resolver.version.ResolutionStrategy.NO_CHANGE
import co.atoms.lithium.crdt.resolver.withPath

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
interface MapCrdtIncomingChangeResolver<K, V, Node, Version, C> :
    CrdtIncomingChangeResolver<Map<K, V>, Node, Version, C>,
    MapCrdtIncomingResolver<K, V, Node, Version, C> {
    override val valueIncomingResolver: CrdtIncomingChangeResolver<V, Node, Version, C>

    fun CrdtIncomingChangeResolver<V, Node, Version, C>.applyChanges(
        keyDepth: Int,
        localValueMap: Map<K, V>,
        localMapNode: Node?,
        localVersion: Version,
        localEntries: Map<K, Node>,
        changesByKey: Map<K, List<ChangeEvent<*, Node, C>>>,
        context: ResolutionDeltaContext<Node, C>,
        withPath: (K, () -> Unit) -> Unit,
        versionNode: (Version, Map<K, Node>) -> Node,
    ): NodeMergeResult<Map<K, V>, Node, ResolutionStrategy> = with(versionTreeResolver) {
        val valueDepth = keyDepth + 1
        val resultMap = localValueMap.toMutableMap()
        val resultEntries = localEntries.toMutableMap()

        var resolution: ResolutionStrategy = NO_CHANGE

        // Use version entries if available, otherwise use value keys
        changesByKey.forEach { (key, changes) ->
            withPath(key) {
                val first = changes.first()
                val localValueNode = localEntries[key]
                val localValue = localValueMap[key]

                resolution += if (first.pathComponents.size == valueDepth) {
                    @Suppress("UNCHECKED_CAST")
                    resolveKeyValue(
                        key = key,
                        localValue = localValue,
                        localValueNode = localValueNode,
                        localVersion = localValueNode?.versionValue ?: localVersion,
                        incomingValue = first.value as? V,
                        incomingValueNode = first.versionNode,
                        incomingVersion = first.versionNode?.versionValue ?: minVersion,
                        resultEntries = resultEntries,
                        resultMap = resultMap,
                        context = context,
                    )
                } else {
                    applyChangesForKeyValue(
                        key = key,
                        valueDepth = valueDepth,
                        localValue = localValue,
                        localValueNode = localValueNode,
                        localVersion = localValueNode?.versionValue ?: localVersion,
                        changes = changes,
                        resultEntries = resultEntries,
                        resultMap = resultMap,
                        context = context,
                    )
                }
            }
        }

        return NodeMergeResult(
            resolution = resolution,
            value = resultMap,
            node = when (resolution) {
                NO_CHANGE,
                LOCAL -> localMapNode ?: createVersionNode(localVersion)
                INCOMING,
                MERGED_VALUES -> versionNode(
                    // Use highest version when keys from both maps were merged
                    resultEntries.values.maxVersion(localVersion),
                    resultEntries
                )
            },
        )
    }

    private fun CrdtIncomingChangeResolver<V, Node, Version, C>.applyChangesForKeyValue(
        key: K,
        valueDepth: Int,
        localValue: V?,
        localValueNode: Node?,
        localVersion: Version,
        changes: List<ChangeEvent<*, Node, C>>,
        resultEntries: MutableMap<K, Node>,
        resultMap: MutableMap<K, V>,
        context: ResolutionDeltaContext<Node, C>,
    ): ResolutionStrategy = with(versionTreeResolver) {
        val result = applyChanges(
            depth = valueDepth,
            localValue = localValue,
            localNode = localValueNode,
            localVersion = localVersion,
            changes = changes,
            context = context,
        )

        val resultValue = result.value
        if (resultValue != null) {
            resultMap[key] = resultValue
        } else {
            resultMap.remove(key)
        }
        // Store version for this key, creating one if needed
        resultEntries[key] = result.node ?: createVersionNode(version = localVersion)

        return result.resolution
    }
}

/**
 * String-keyed map CRDT resolver.
 *
 * Stores per-key versions in Node.string_map field.
 */
interface StringMapCrdtIncomingChangeResolver<V, Node, Version, C> :
    StringMapCrdtIncomingResolver<V, Node, Version, C>,
    MapCrdtIncomingChangeResolver<String, V, Node, Version, C> {
    override fun applyChanges(
        depth: Int,
        localValue: Map<String, V>?,
        localNode: Node?,
        localVersion: Version,
        changes: List<ChangeEvent<*, Node, C>>,
        context: ResolutionDeltaContext<Node, C>
    ) = with(versionTreeResolver) {
        valueIncomingResolver.applyChanges(
            keyDepth = depth,
            localValueMap = localValue ?: mapOf(),
            localMapNode = localNode,
            localVersion = localNode?.versionValue ?: localVersion,
            localEntries = localNode?.stringMap ?: mapOf(),
            changesByKey = changes.groupBy {
                it.pathComponents[depth].stringKey ?: throw IllegalArgumentException("No stringKey for change: $it")
            },
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
interface IntMapCrdtIncomingChangeResolver<V, Node, Version, C> :
    IntMapCrdtIncomingResolver<V, Node, Version, C>,
    MapCrdtIncomingChangeResolver<Int, V, Node, Version, C> {
    override fun applyChanges(
        depth: Int,
        localValue: Map<Int, V>?,
        localNode: Node?,
        localVersion: Version,
        changes: List<ChangeEvent<*, Node, C>>,
        context: ResolutionDeltaContext<Node, C>
    ) = with(versionTreeResolver) {
        valueIncomingResolver.applyChanges(
            keyDepth = depth,
            localValueMap = localValue ?: mapOf(),
            localMapNode = localNode,
            localVersion = localNode?.versionValue ?: localVersion,
            localEntries = localNode?.intMap ?: mapOf(),
            changesByKey = changes.groupBy {
                it.pathComponents[depth].intKey ?: throw IllegalArgumentException("No intKey for change: $it")
            },
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
interface LongMapCrdtIncomingChangeResolver<V, Node, Version, C> :
    LongMapCrdtIncomingResolver<V, Node, Version, C>,
    MapCrdtIncomingChangeResolver<Long, V, Node, Version, C> {
    override fun applyChanges(
        depth: Int,
        localValue: Map<Long, V>?,
        localNode: Node?,
        localVersion: Version,
        changes: List<ChangeEvent<*, Node, C>>,
        context: ResolutionDeltaContext<Node, C>
    ) = with(versionTreeResolver) {
        valueIncomingResolver.applyChanges(
            keyDepth = depth,
            localValueMap = localValue ?: mapOf(),
            localMapNode = localNode,
            localVersion = localNode?.versionValue ?: localVersion,
            localEntries = localNode?.longMap ?: mapOf(),
            changesByKey = changes.groupBy {
                it.pathComponents[depth].longKey ?: throw IllegalArgumentException("No longKey for change: $it")
            },
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
interface BooleanMapCrdtIncomingChangeResolver<V, Node, Version, C> :
    BooleanMapCrdtIncomingResolver<V, Node, Version, C>,
    MapCrdtIncomingChangeResolver<Boolean, V, Node, Version, C> {
    override fun applyChanges(
        depth: Int,
        localValue: Map<Boolean, V>?,
        localNode: Node?,
        localVersion: Version,
        changes: List<ChangeEvent<*, Node, C>>,
        context: ResolutionDeltaContext<Node, C>
    ) = with(versionTreeResolver) {
        valueIncomingResolver.applyChanges(
            keyDepth = depth,
            localValueMap = localValue ?: mapOf(),
            localMapNode = localNode,
            localVersion = localNode?.versionValue ?: localVersion,
            localEntries = localNode?.booleanMap ?: mapOf(),
            changesByKey = changes.groupBy {
                it.pathComponents[depth].booleanKey ?: throw IllegalArgumentException("No booleanKey for change: $it")
            },
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
