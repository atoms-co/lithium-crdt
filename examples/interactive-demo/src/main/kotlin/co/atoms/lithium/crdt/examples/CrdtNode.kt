package co.atoms.lithium.crdt.examples

import co.atoms.lithium.crdt.data.Actors
import co.atoms.lithium.crdt.data.PathComponent
import co.atoms.lithium.crdt.data.Version
import co.atoms.lithium.crdt.data.VersionNode
import co.atoms.lithium.crdt.resolver.version.ResolutionStrategy
import co.atoms.lithium.crdt.resolver.ResolverDeltaResult
import co.atoms.lithium.crdt.wire.WireCrdtMessageResolver
import co.atoms.lithium.crdt.wire.WireCrdtResolverProvider

class CrdtNode(val name: String) {

    companion object {
        private val resolverProvider = WireCrdtResolverProvider()
        val resolver: WireCrdtMessageResolver<CollaborativeDocument> =
            resolverProvider.messageResolver(adapter = CollaborativeDocument.ADAPTER)

        /** Default empty document used as baseline so all subsequent writes
         *  go through the field-by-field comparison path, enabling per-field
         *  version tracking instead of whole-document versioning. */
        private val EMPTY_DOCUMENT = CollaborativeDocument(
            title = "",
            content = "",
            author = "",
            priority = 0,
            status = "",
            tags = emptyMap(),
        )
    }

    var document: CollaborativeDocument? = null
        private set

    var versionNode: VersionNode? = null
        private set

    var actors: Actors? = null
        private set

    var online: Boolean = true

    val eventLog: MutableList<EventLogEntry> = mutableListOf()

    init {
        val delta = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = EMPTY_DOCUMENT,
            timestamp = 0L,
        )
        document = delta.mergeResult.value
        versionNode = delta.mergeResult.node
        actors = delta.actors
    }

    fun applyLocalWrite(newDocument: CollaborativeDocument): Boolean {
        val timestamp = System.currentTimeMillis()

        val delta: ResolverDeltaResult<CollaborativeDocument, VersionNode, Version, Boolean, PathComponent, Actors> =
            resolver.applyLocalWrite(
                currentValue = document,
                currentNode = versionNode,
                currentActors = actors,
                newValue = newDocument,
                timestamp = timestamp,
            )

        val changed = delta.mergeResult.resolution
        document = delta.mergeResult.value
        versionNode = delta.mergeResult.node
        actors = delta.actors

        if (changed) {
            eventLog.add(
                EventLogEntry(
                    type = "LOCAL_WRITE",
                    description = "Applied local write on $name",
                    timestamp = timestamp,
                )
            )
        }

        return changed
    }

    fun syncFrom(other: CrdtNode): ResolutionStrategy {
        val incomingNode = other.versionNode
            ?: return ResolutionStrategy.NO_CHANGE

        val incomingActors = other.actors
            ?: return ResolutionStrategy.NO_CHANGE

        val delta: ResolverDeltaResult<CollaborativeDocument, VersionNode, Version, ResolutionStrategy, PathComponent, Actors> =
            resolver.resolveConflict(
                localValue = document,
                localNode = versionNode,
                localActors = actors,
                incomingValue = other.document,
                incomingNode = incomingNode,
                incomingVersionVector = incomingActors.version_vector,
            )

        val strategy = delta.mergeResult.resolution
        document = delta.mergeResult.value
        versionNode = delta.mergeResult.node
        actors = delta.actors

        val timestamp = System.currentTimeMillis()
        eventLog.add(
            EventLogEntry(
                type = "SYNC",
                description = "Synced $name from ${other.name} -> $strategy",
                timestamp = timestamp,
                resolution = strategy.toString(),
            )
        )

        return strategy
    }

    fun reset() {
        val delta = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = EMPTY_DOCUMENT,
            timestamp = 0L,
        )
        document = delta.mergeResult.value
        versionNode = delta.mergeResult.node
        actors = delta.actors
        online = true
        eventLog.clear()
    }
}

data class EventLogEntry(
    val type: String,
    val description: String,
    val timestamp: Long,
    val resolution: String? = null,
)
