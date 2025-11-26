package com.css.protobuf.crdt.wire.internal

import com.css.protobuf.crdt.data.Counter
import com.css.protobuf.crdt.data.Version
import com.css.protobuf.crdt.data.VersionCount
import com.css.protobuf.crdt.data.VersionNode
import com.css.protobuf.crdt.data.VersionNode.BoolMap
import com.css.protobuf.crdt.data.VersionNode.Int32Map
import com.css.protobuf.crdt.data.VersionNode.Int64Map
import com.css.protobuf.crdt.data.VersionNode.Repeated
import com.css.protobuf.crdt.data.VersionNode.StringMap
import com.css.protobuf.crdt.data.VersionNode.Struct
import com.css.protobuf.crdt.resolver.version.VersionNodeAdapter

internal interface WireVersionNodeAdapter : VersionNodeAdapter<VersionNode, Version>, WireVersionResolver {
    override val VersionNode.versionValue: Version?
        get() = this@versionValue.version?.takeIf { it.actor_id != 0L }
    override val VersionNode.entries: List<VersionNode>
        get() = repeated?.entries ?: listOf()
    override val VersionNode.fields: Map<Int, VersionNode>
        get() = struct?.fields ?: mapOf()
    override val VersionNode.stringMap: Map<String, VersionNode>
        get() = string_map?.entries ?: mapOf()
    override val VersionNode.intMap: Map<Int, VersionNode>
        get() = int32_map?.entries ?: mapOf()
    override val VersionNode.longMap: Map<Long, VersionNode>
        get() = int64_map?.entries ?: mapOf()
    override val VersionNode.booleanMap: Map<Boolean, VersionNode>
        get() = bool_map?.entries ?: mapOf()
    override val VersionNode.counterActors: Int
        get() = counter?.actor_count?.size ?: 0
    override val VersionNode.counterValue: Long
        get() = counter?.actor_count?.entries?.sumOf { it.value.value_ } ?: 0L

    override fun createVersionNode(version: Version) = VersionNode(version = version)
    override fun createVersionNodeRepeated(version: Version, entries: List<VersionNode>): VersionNode {
        return VersionNode(
            version = version,
            repeated = entries.takeIf { it.isNotEmpty() }?.let { Repeated(entries = it) }
        )
    }
    override fun createVersionNodeStruct(version: Version, fields: Map<Int, VersionNode>): VersionNode {
        return VersionNode(
            version = version,
            struct = Struct(fields = fields)
        )
    }
    override fun createVersionNodeBoolMap(version: Version, entries: Map<Boolean, VersionNode>): VersionNode {
        return VersionNode(
            version = version,
            bool_map = entries.takeIf { it.isNotEmpty() }?.let { BoolMap(entries = it) }
        )
    }
    override fun createVersionNodeIntMap(version: Version, entries: Map<Int, VersionNode>): VersionNode {
        return VersionNode(
            version = version,
            int32_map = entries.takeIf { it.isNotEmpty() }?.let { Int32Map(entries = it) }
        )
    }
    override fun createVersionNodeLongMap(version: Version, entries: Map<Long, VersionNode>): VersionNode {
        return VersionNode(
            version = version,
            int64_map = entries.takeIf { it.isNotEmpty() }?.let { Int64Map(entries = it) }
        )
    }
    override fun createVersionNodeStringMap(version: Version, entries: Map<String, VersionNode>): VersionNode {
        return VersionNode(
            version = version,
            string_map = entries.takeIf { it.isNotEmpty() }?.let { StringMap(entries = it) }
        )
    }

    override fun createVersionNodeCounter(version: Version, value: Long): VersionNode {
        return VersionNode(
            version = version,
            counter = Counter(
                actor_count = mapOf(
                    version.actor_id to VersionCount(version = version.actor_version, value_ = value)
                )
            )
        )
    }

    override fun VersionNode.mergeCounter(other: VersionNode): VersionNode {
        val otherActorCount = other.counter?.actor_count ?: mapOf()
        if (otherActorCount.isEmpty()) {
            return this
        }

        val actorCount = counter?.actor_count ?: mapOf()
        if (actorCount.isEmpty()) {
            return other
        }

        if (actorCount.size == 1 && otherActorCount.size == 1 &&
            actorCount.keys == otherActorCount.keys) {
            val count = actorCount.values.first()
            val otherCount = otherActorCount.values.first()
            return if (count.version >= otherCount.version) {
                this
            } else {
                other
            }
        }

        val version = version ?: minVersion
        val otherVersion = other.version ?: minVersion
        return copy(
            version = if (version > otherVersion) version else other.version,
            counter = (counter ?: Counter()).run {
                copy(
                    actor_count = actor_count.merge(other.counter?.actor_count ?: mapOf())
                )
            }
        )
    }

    override fun VersionNode.plus(value: Long, version: Version): VersionNode {
        return copy(
            version = version,
            counter = (counter ?: Counter()).run {
                copy(
                    actor_count = actor_count + (version.actor_id to VersionCount(
                        version = version.actor_version,
                        value_ = (actor_count[version.actor_id]?.value_ ?: 0L) + value
                    ))
                )
            }
        )
    }
}

fun Map<Long, VersionCount>.merge(other: Map<Long, VersionCount>): Map<Long, VersionCount> {
    if (other.isEmpty()) {
        return this
    }

    if (isEmpty()) {
        return other
    }

    return toMutableMap().apply {
        other.forEach { entry ->
            this[entry.key] = this[entry.key]?.let { previous ->
                if (previous.version >= entry.value.version) {
                    previous
                } else {
                    entry.value
                }
            } ?: entry.value
        }
    }
}
