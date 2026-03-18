package co.atoms.lithium.crdt.protoc

import co.atoms.lithium.crdt.data.Counter
import co.atoms.lithium.crdt.data.Version
import co.atoms.lithium.crdt.data.VersionCount
import co.atoms.lithium.crdt.data.VersionNode
import co.atoms.lithium.crdt.data.VersionNode.BoolMap
import co.atoms.lithium.crdt.data.VersionNode.Int32Map
import co.atoms.lithium.crdt.data.VersionNode.Int64Map
import co.atoms.lithium.crdt.data.VersionNode.Repeated
import co.atoms.lithium.crdt.data.VersionNode.StringMap
import co.atoms.lithium.crdt.data.VersionNode.Struct
import co.atoms.lithium.crdt.resolver.version.VersionNodeAdapter

internal interface ProtocVersionNodeAdapter : VersionNodeAdapter<VersionNode, Version>, ProtocVersionResolver {
    override val VersionNode.versionValue: Version?
        get() = this@versionValue.version.takeIf { it.actorId != 0L }
    override val VersionNode.entries: List<VersionNode>
        get() = repeated?.entriesList ?: listOf()
    override val VersionNode.fields: Map<Int, VersionNode>
        get() = struct?.fieldsMap ?: mapOf()
    override val VersionNode.stringMap: Map<String, VersionNode>
        get() = stringMap?.entriesMap ?: mapOf()
    override val VersionNode.intMap: Map<Int, VersionNode>
        get() = int32Map?.entriesMap ?: mapOf()
    override val VersionNode.longMap: Map<Long, VersionNode>
        get() = int64Map?.entriesMap ?: mapOf()
    override val VersionNode.booleanMap: Map<Boolean, VersionNode>
        get() = boolMap?.entriesMap ?: mapOf()
    override val VersionNode.counterActors: Int
        get() = counter?.actorCountMap?.size ?: 0
    override val VersionNode.counterValue: Long
        get() = counter?.actorCountMap?.entries?.sumOf { it.value.value } ?: 0L

    override fun createVersionNode(version: Version): VersionNode {
        return VersionNode.newBuilder().setVersion(version).build()
    }
    override fun createVersionNodeRepeated(version: Version, entries: List<VersionNode>): VersionNode {
        return VersionNode.newBuilder().setVersion(version).run {
            if (entries.isNotEmpty()) {
                setRepeated(Repeated.newBuilder().addAllEntries(entries).build())
            }
            build()
        }
    }
    override fun createVersionNodeStruct(version: Version, fields: Map<Int, VersionNode>): VersionNode {
        return VersionNode.newBuilder().setVersion(version).run {
            if (fields.isNotEmpty()) {
                setStruct(Struct.newBuilder().putAllFields(fields).build())
            }
            build()
        }
    }
    override fun createVersionNodeBoolMap(version: Version, entries: Map<Boolean, VersionNode>): VersionNode {
        return VersionNode.newBuilder().setVersion(version).run {
            if (entries.isNotEmpty()) {
                setBoolMap(BoolMap.newBuilder().putAllEntries(entries).build())
            }
            build()
        }
    }
    override fun createVersionNodeIntMap(version: Version, entries: Map<Int, VersionNode>): VersionNode {
        return VersionNode.newBuilder().setVersion(version).run {
            if (entries.isNotEmpty()) {
                setInt32Map(Int32Map.newBuilder().putAllEntries(entries).build())
            }
            build()
        }
    }
    override fun createVersionNodeLongMap(version: Version, entries: Map<Long, VersionNode>): VersionNode {
        return VersionNode.newBuilder().setVersion(version).run {
            if (entries.isNotEmpty()) {
                setInt64Map(Int64Map.newBuilder().putAllEntries(entries).build())
            }
            build()
        }
    }
    override fun createVersionNodeStringMap(version: Version, entries: Map<String, VersionNode>): VersionNode {
        return VersionNode.newBuilder().setVersion(version).run {
            if (entries.isNotEmpty()) {
                setStringMap(StringMap.newBuilder().putAllEntries(entries).build())
            }
            build()
        }
    }

    override fun createVersionNodeCounter(version: Version, value: Long): VersionNode {
        return VersionNode.newBuilder()
            .setVersion(version)
            .setCounter(
                Counter.newBuilder()
                    .putActorCount(
                        version.actorId,
                        VersionCount.newBuilder()
                            .setVersion(version.actorVersion)
                            .setValue(value)
                            .build()
                    ).build()
            ).build()
    }

    override fun VersionNode.mergeCounter(other: VersionNode): VersionNode {
        val otherActorCount = other.counter?.actorCountMap ?: mapOf()
        if (otherActorCount.isEmpty()) {
            return this
        }

        val actorCount = counter?.actorCountMap ?: mapOf()
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
        return toBuilder()
            .setVersion(if (version > otherVersion) version else other.version)
            .setCounter(
                counter.toBuilder()
                    .putAllActorCount(counter.actorCountMap.merge(other.counter.actorCountMap))
                    .build()
            ).build()
    }

    override fun VersionNode.plus(value: Long, version: Version): VersionNode {
        return VersionNode.newBuilder()
            .setVersion(version)
            .setCounter(
                counter.toBuilder().putActorCount(
                    version.actorId,
                    VersionCount.newBuilder()
                        .setVersion(version.actorVersion)
                        .setValue((counter.actorCountMap[version.actorId]?.value ?: 0L) + value)
                        .build()
                ).build()
            ).build()
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
