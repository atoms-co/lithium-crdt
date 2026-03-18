package co.atoms.lithium.crdt.protoc

import co.atoms.lithium.crdt.data.Version
import co.atoms.lithium.crdt.resolver.version.VersionResolver

internal interface ProtocVersionResolver : VersionResolver<Version> {
    override val minVersion: Version get() = MIN_VERSION

    override fun incrementNextIfNeeded(
        previous: Version?,
        next: Version
    ): Version {
        val previousTimestamp = previous?.takeIf { it.actorId != 0L }?.timestamp ?: return next
        return if (previousTimestamp >= next.timestamp) {
            next.toBuilder().setTimestamp(previousTimestamp + 1).build()
        } else {
            next
        }
    }

    override fun compare(o1: Version, o2: Version): Int = o1.compareTo(o2)

    override fun isIncluded(versionVector: Map<Long, Long>, version: Version): Boolean {
        val includedVersion = versionVector[version.actorId] ?: return false
        return includedVersion >= version.actorVersion
    }

    override fun Version.minus(duration: Long): Version {
        return toBuilder()
            .setTimestamp(timestamp - duration)
            .build()
    }

    companion object {
        private val MIN_VERSION = Version.newBuilder()
            .setTimestamp(Long.MIN_VALUE)
            .setActorId(Long.MIN_VALUE)
            .setActorVersion(Long.MIN_VALUE)
            .build()
    }
}

operator fun Version.compareTo(other: Version): Int {
    return timestamp.compareTo(other.timestamp).takeIf { it != 0 }
        ?: actorId.compareTo(other.actorId).takeIf { it != 0 }
        ?: actorVersion.compareTo(other.actorVersion)
}
