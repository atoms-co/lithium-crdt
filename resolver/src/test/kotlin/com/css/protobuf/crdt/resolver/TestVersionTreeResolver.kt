package com.css.protobuf.crdt.resolver

import com.css.protobuf.crdt.resolver.delta.PathComponentAdapter
import com.css.protobuf.crdt.resolver.version.VersionNodeAdapter
import com.css.protobuf.crdt.resolver.version.VersionResolver
import com.css.protobuf.crdt.resolver.version.VersionTreeResolver

object TestVersionTreeResolver :
    VersionTreeResolver<VersionNode, Version, String>,
    VersionNodeAdapter<VersionNode, Version>,
    VersionResolver<Version>,
    PathComponentAdapter<String> {
    override val String.fieldNumber: Int? get() = toIntOrNull()
    override val String.booleanKey: Boolean? get() = toBooleanStrictOrNull()
    override val String.intKey: Int? get() = toIntOrNull()
    override val String.longKey: Long? get() = toLongOrNull()
    override val String.stringKey: String get() = this
    override val String.repeatedIndex: Int? get() = toIntOrNull()
    override val VersionNode.counterValue: Long get() = counter?.actorCount?.entries?.sumOf { it.value.value } ?: 0L
    override val VersionNode.counterActors: Int get() = counter?.actorCount?.size ?: 0
    override val VersionNode.versionValue: Version?
        get() = this.version.takeIf { it.actorId != 0L }
    override val VersionNode.entries: List<VersionNode>
        get() = repeated
    override val VersionNode.fields: Map<Int, VersionNode>
        get() = int_map
    override val VersionNode.stringMap: Map<String, VersionNode>
        get() = string_map
    override val VersionNode.intMap: Map<Int, VersionNode>
        get() = int_map
    override val VersionNode.longMap: Map<Long, VersionNode>
        get() = long_map
    override val VersionNode.booleanMap: Map<Boolean, VersionNode>
        get() = bool_map
    override val minVersion: Version get() = Version(
        actorId = Long.MIN_VALUE,
        actorVersion = Long.MIN_VALUE,
        timestamp = Long.MIN_VALUE
    )

    override fun createVersionNode(version: Version) = VersionNode(version)

    override fun createVersionNodeRepeated(version: Version, entries: List<VersionNode>) =
        VersionNode(version, repeated = entries)

    override fun createVersionNodeStruct(version: Version, fields: Map<Int, VersionNode>) =
        VersionNode(version, int_map = fields)

    override fun createVersionNodeBoolMap(version: Version, entries: Map<Boolean, VersionNode>) =
        VersionNode(version, bool_map = entries)

    override fun createVersionNodeIntMap(version: Version, entries: Map<Int, VersionNode>) =
        VersionNode(version, int_map = entries)

    override fun createVersionNodeLongMap(version: Version, entries: Map<Long, VersionNode>) =
        VersionNode(version, long_map = entries)

    override fun createVersionNodeStringMap(version: Version, entries: Map<String, VersionNode>) =
        VersionNode(version, string_map = entries)

    override fun createVersionNodeCounter(version: Version, value: Long): VersionNode {
        return VersionNode(
            version = version,
            counter = Counter(
                mapOf(
                    version.actorId to VersionCount(
                        version = version.actorVersion,
                        value = value
                    )
                )
            )
        )
    }

    override fun VersionNode.plus(value: Long, version: Version): VersionNode {
        return copy(
            version = version,
            counter = (counter ?: Counter()).run {
                copy(
                    actorCount = actorCount + (version.actorId to VersionCount(
                        version = version.actorVersion,
                        value = (actorCount[version.actorId]?.value ?: 0L) + value
                    ))
                )
            }
        )
    }

    override fun VersionNode.mergeCounter(other: VersionNode): VersionNode {
        val otherActorCount = other.counter?.actorCount ?: mapOf()
        if (otherActorCount.isEmpty()) {
            return this
        }

        val actorCount = counter?.actorCount ?: mapOf()
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

        return copy(
            version = if (version > other.version) version else other.version,
            counter = (counter ?: Counter()).run {
                copy(
                    actorCount = actorCount.merge(other.counter?.actorCount ?: mapOf())
                )
            }
        )
    }

    override fun incrementNextIfNeeded(previous: Version?, next: Version): Version {
        val previousTimestamp = previous?.takeIf { it.actorId != 0L }?.timestamp ?: return next
        return if (previousTimestamp >= next.timestamp) {
            next.copy(timestamp = previousTimestamp + 1)
        } else {
            next
        }
    }

    override fun isIncluded(versionVector: Map<Long, Long>, version: Version): Boolean {
        val includedVersion = versionVector[version.actorId] ?: return false
        return includedVersion >= version.actorVersion
    }

    override fun Version.minus(duration: Long): Version {
        return copy(timestamp = timestamp - duration)
    }

    override fun compare(o1: Version, o2: Version): Int = o1.compareTo(o2)

    override fun createPathComponentField(field: Int): String = field.toString()
    override fun createPathComponentBooleanKey(key: Boolean): String = key.toString()
    override fun createPathComponentIntKey(key: Int): String = key.toString()
    override fun createPathComponentLongKey(key: Long): String = key.toString()
    override fun createPathComponentStringKey(key: String): String = key
    override fun createPathComponentRepeatedIndex(index: Int): String = index.toString()
}

data class VersionNode(
    val version: Version,
    val repeated: List<VersionNode> = listOf(),
    val bool_map: Map<Boolean, VersionNode> = mapOf(),
    val int_map: Map<Int, VersionNode> = mapOf(),
    val long_map: Map<Long, VersionNode> = mapOf(),
    val string_map: Map<String, VersionNode> = mapOf(),
    val counter: Counter? = null,
)

data class Counter(
    val actorCount: Map<Long, VersionCount> = mapOf()
)

data class VersionCount(
    val version: Long,
    val value: Long,
)

data class Version(
    val actorId: Long = 0L,
    val actorVersion: Long = 0L,
    val timestamp: Long = 0L,
) : Comparable<Version> {
    override fun compareTo(other: Version): Int {
        return timestamp.compareTo(other.timestamp).takeIf { it != 0 }
            ?: actorId.compareTo(other.actorId).takeIf { it != 0 }
            ?: actorVersion.compareTo(other.actorVersion)
    }
}

fun Map<Long, VersionCount>.merge(other: Map<Long, VersionCount>): Map<Long, VersionCount> {
    if (other.isEmpty()) {
        return this
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
