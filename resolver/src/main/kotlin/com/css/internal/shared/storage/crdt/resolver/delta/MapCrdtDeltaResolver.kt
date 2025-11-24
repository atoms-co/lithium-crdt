package com.css.internal.shared.storage.crdt.resolver.delta

import com.css.internal.shared.storage.crdt.resolver.ResolutionDeltaContext
import com.css.internal.shared.storage.crdt.resolver.withPath

interface MapCrdtDeltaResolver<K, V, Node, Version, C> :
    CrdtDeltaResolver<Map<K, V>, Node, Version, C> {
    val valueDeltaResolver: CrdtDeltaResolver<V, Node, Version, C>

    fun changeDelta(
        valueMap: Map<K, V>,
        node: Node?,
        entries: Map<K, Node>,
        version: Version,
        versionVector: Map<Long, Long>,
        context: ResolutionDeltaContext<Node, C>,
        withPath: (K, () -> Unit) -> Unit,
        versionNode: (Version, Map<K, Node>) -> Node,
    ) {
        with(versionTreeResolver) {
            // Process the later version map first to ensure ordered repeated storage is reflected.
            val keys = entries.keys.takeIf { it.isNotEmpty() } ?: valueMap.keys

            // Use version entries if available, otherwise use value keys
            keys.forEach { key ->
                val entryNode = entries[key]
                withPath(key) {
                    valueDeltaResolver.changeDelta(
                        value = valueMap[key],
                        node = entryNode,
                        // Use key-specific version if available, otherwise map version
                        version = entryNode?.versionValue ?: version,
                        versionVector = versionVector,
                        context = context,
                    )
                }
            }
        }
    }
}

interface StringMapCrdtDeltaResolver<V, Node, Version, C> :
    MapCrdtDeltaResolver<String, V, Node, Version, C> {
    override fun changeDelta(
        value: Map<String, V>?,
        node: Node?,
        version: Version,
        versionVector: Map<Long, Long>,
        context: ResolutionDeltaContext<Node, C>
    ) {
        with(versionTreeResolver) {
            changeDelta(
                valueMap = value ?: mapOf(),
                node = node,
                entries = node?.stringMap ?: mapOf(),
                version = node?.versionValue ?: version,
                versionVector = versionVector,
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
}

interface IntMapCrdtDeltaResolver<V, Node, Version, C> :
    MapCrdtDeltaResolver<Int, V, Node, Version, C> {
    override fun changeDelta(
        value: Map<Int, V>?,
        node: Node?,
        version: Version,
        versionVector: Map<Long, Long>,
        context: ResolutionDeltaContext<Node, C>
    ) {
        with(versionTreeResolver) {
            changeDelta(
                valueMap = value ?: mapOf(),
                node = node,
                entries = node?.intMap ?: mapOf(),
                version = node?.versionValue ?: version,
                versionVector = versionVector,
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
}

interface LongMapCrdtDeltaResolver<V, Node, Version, C> :
    MapCrdtDeltaResolver<Long, V, Node, Version, C> {
    override fun changeDelta(
        value: Map<Long, V>?,
        node: Node?,
        version: Version,
        versionVector: Map<Long, Long>,
        context: ResolutionDeltaContext<Node, C>
    ) {
        with(versionTreeResolver) {
            changeDelta(
                valueMap = value ?: mapOf(),
                node = node,
                entries = node?.longMap ?: mapOf(),
                version = node?.versionValue ?: version,
                versionVector = versionVector,
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
}

interface BooleanMapCrdtDeltaResolver<V, Node, Version, C> :
    MapCrdtDeltaResolver<Boolean, V, Node, Version, C> {
    override fun changeDelta(
        value: Map<Boolean, V>?,
        node: Node?,
        version: Version,
        versionVector: Map<Long, Long>,
        context: ResolutionDeltaContext<Node, C>
    ) {
        with(versionTreeResolver) {
            changeDelta(
                valueMap = value ?: mapOf(),
                node = node,
                entries = node?.booleanMap ?: mapOf(),
                version = node?.versionValue ?: version,
                versionVector = versionVector,
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
}
