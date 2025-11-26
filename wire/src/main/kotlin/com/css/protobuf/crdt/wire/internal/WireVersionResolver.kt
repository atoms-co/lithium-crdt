package com.css.protobuf.crdt.wire.internal

import com.css.protobuf.crdt.data.Version
import com.css.protobuf.crdt.resolver.version.VersionResolver

internal interface WireVersionResolver : VersionResolver<Version> {
    override val minVersion: Version get() = MIN_VERSION

    override fun incrementNextIfNeeded(
        previous: Version?,
        next: Version
    ): Version {
        val previousTimestamp = previous?.takeIf { it.actor_id != 0L }?.timestamp ?: return next
        return if (previousTimestamp >= next.timestamp) {
            next.copy(timestamp = previousTimestamp + 1)
        } else {
            next
        }
    }

    override fun compare(o1: Version, o2: Version): Int = o1.compareTo(o2)

    override fun isIncluded(versionVector: Map<Long, Long>, version: Version): Boolean {
        val includedVersion = versionVector[version.actor_id] ?: return false
        return includedVersion >= version.actor_version
    }

    override fun Version.minus(duration: Long): Version {
        return copy(timestamp = timestamp - duration)
    }

    companion object {
        private val MIN_VERSION = Version(
            timestamp = Long.MIN_VALUE,
            actor_id = Long.MIN_VALUE,
            actor_version = Long.MIN_VALUE,
        )
    }
}

operator fun Version.compareTo(other: Version): Int {
    return timestamp.compareTo(other.timestamp).takeIf { it != 0 }
        ?: actor_id.compareTo(other.actor_id).takeIf { it != 0 }
        ?: actor_version.compareTo(other.actor_version)
}
