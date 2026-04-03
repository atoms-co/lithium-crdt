package co.atoms.lithium.crdt.examples

import co.atoms.lithium.crdt.data.Actors
import co.atoms.lithium.crdt.data.Version
import co.atoms.lithium.crdt.data.VersionNode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val json = Json {
    prettyPrint = true
    encodeDefaults = true
}

@Serializable
data class NodeStateDto(
    val name: String,
    val online: Boolean,
    val document: DocumentDto?,
    val versionNode: VersionNodeDto?,
    val actors: ActorsDto?,
    val eventLog: List<EventLogEntryDto>,
)

@Serializable
data class DocumentDto(
    val title: String,
    val content: String,
    val author: String,
    val priority: Int,
    val status: String,
    val tags: Map<String, String>,
)

@Serializable
data class VersionNodeDto(
    val version: VersionDto?,
    val fields: Map<String, VersionNodeDto>?,
    val mapEntries: Map<String, VersionNodeDto>?,
)

@Serializable
data class VersionDto(
    val timestamp: Long,
    val actorId: Long,
    val actorVersion: Long,
)

@Serializable
data class ActorsDto(
    val localActor: Long,
    val versionVector: Map<String, Long>,
)

@Serializable
data class EventLogEntryDto(
    val type: String,
    val description: String,
    val timestamp: Long,
    val resolution: String? = null,
)

@Serializable
data class WriteRequest(
    val title: String? = null,
    val content: String? = null,
    val author: String? = null,
    val priority: Int? = null,
    val status: String? = null,
    val tags: Map<String, String>? = null,
)

@Serializable
data class SyncResultDto(
    val resolution: String,
    val source: String,
    val target: String,
)

@Serializable
data class SyncAllResultDto(
    val results: List<SyncResultDto>,
)

fun CrdtNode.toDto(): NodeStateDto = NodeStateDto(
    name = name,
    online = online,
    document = document?.toDto(),
    versionNode = versionNode?.toDto(),
    actors = actors?.toDto(),
    eventLog = eventLog.map { it.toDto() },
)

fun CollaborativeDocument.toDto(): DocumentDto = DocumentDto(
    title = title,
    content = content,
    author = author,
    priority = priority,
    status = status,
    tags = tags,
)

fun VersionNode.toDto(): VersionNodeDto = VersionNodeDto(
    version = version?.toDto(),
    fields = struct?.fields?.takeIf { it.isNotEmpty() }?.mapKeys { (k, _) ->
        fieldNumberToName(k)
    }?.mapValues { (_, v) -> v.toDto() },
    mapEntries = string_map?.entries?.takeIf { it.isNotEmpty() }?.mapValues { (_, v) ->
        v.toDto()
    },
)

fun Version.toDto(): VersionDto = VersionDto(
    timestamp = timestamp,
    actorId = actor_id,
    actorVersion = actor_version,
)

fun Actors.toDto(): ActorsDto = ActorsDto(
    localActor = local_actor,
    versionVector = version_vector.mapKeys { (k, _) -> k.toString() },
)

fun EventLogEntry.toDto(): EventLogEntryDto = EventLogEntryDto(
    type = type,
    description = description,
    timestamp = timestamp,
    resolution = resolution,
)

// Maps proto field numbers to human-readable names for CollaborativeDocument.
// VersionNode stores fields by proto number but the UI needs meaningful labels.
private fun fieldNumberToName(fieldNumber: Int): String = when (fieldNumber) {
    1 -> "title (1)"
    2 -> "content (2)"
    3 -> "author (3)"
    4 -> "priority (4)"
    5 -> "status (5)"
    6 -> "tags (6)"
    else -> "field_$fieldNumber"
}
