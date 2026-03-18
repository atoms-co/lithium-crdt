package co.atoms.protobuf.crdt.wire.delta

import co.atoms.protobuf.crdt.data.Actors
import co.atoms.protobuf.crdt.data.CrdtData
import co.atoms.protobuf.crdt.data.DataChange
import co.atoms.protobuf.crdt.data.DistributedDocument
import co.atoms.protobuf.crdt.data.DocumentDelta
import co.atoms.protobuf.crdt.data.PathComponent
import co.atoms.protobuf.crdt.data.VersionChange
import co.atoms.protobuf.crdt.data.VersionNode
import co.atoms.protobuf.crdt.resolver.ChangeEvent
import co.atoms.protobuf.crdt.resolver.version.ApplyChangesResult
import co.atoms.protobuf.crdt.wire.WireCrdtMessageResolver
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import okio.ByteString
import okio.ByteString.Companion.toByteString

// TODO: Extract to resolver module when protoc delta operations are implemented.
//  Currently Wire-specific due to DistributedDocument/Actors dependencies from wire-data.
sealed class DeltaApplicationResult<out T> {
    data class Applied<T>(
        val document: DistributedDocument,
        val updatedValue: T,
        val updatedActors: Actors,
    ) : DeltaApplicationResult<T>()

    data class Unchanged<T>(
        val document: DistributedDocument,
        val currentValue: T?,
        val currentActors: Actors,
    ) : DeltaApplicationResult<T>()

    data class Rejected(
        val reason: String? = null,
    ) : DeltaApplicationResult<Nothing>()
}

private val DistributedDocument.encodedPayload: ByteArray?
    get() = data_?.data_?.toByteArray()?.takeIf { it.isNotEmpty() }

/**
 * High-level facade for delta-based CRDT synchronization.
 *
 * Composes [WireCrdtMessageResolver] (change computation / application) with proto
 * wire-format serialization behind two operations:
 * - [computeDelta]: determine what a peer is missing and produce a compact [DocumentDelta]
 * - [applyDelta]: apply an incoming [DocumentDelta] to local state
 */
class WireDocumentDeltaOperations<M : Message<M, B>, B : Message.Builder<M, B>>(
    private val adapter: ProtoAdapter<M>,
    private val messageResolver: WireCrdtMessageResolver<M>,
) {

    /**
     * Computes the minimal delta a peer needs to catch up.
     *
     * Walks the document's version tree, finds field-level changes the peer hasn't
     * seen (based on [peerVersionVector]), and serializes them into a [DocumentDelta].
     *
     * @return `null` when the peer is already up to date (no changes needed)
     */
    fun computeDelta(
        document: DistributedDocument,
        peerVersionVector: Map<Long, Long>,
    ): DocumentDelta? {
        val value = decodeValue(document) ?: return null
        val versionNode = document.version_node ?: return null

        val changes = messageResolver.changeDelta(value, versionNode, peerVersionVector)
        if (changes.isEmpty()) return null

        return toDocumentDelta(
            documentId = document.id,
            baselineVersionVector = peerVersionVector,
            changes = changes,
            crdtTypeName = requireNotNull(document.data_?.crdt_type_name?.takeIf { it.isNotEmpty() }) {
                "Cannot compute delta for document ${document.id}: CrdtData.crdt_type_name is missing"
            },
        )
    }

    /**
     * Applies an incoming [DocumentDelta] to the local document.
     *
     * Validates the delta's baseline against local state, then applies field-level
     * changes using LWW conflict resolution.
     *
     * @param localActorId the local device's actor identity, preserved through the merge
     *   so callers can use [DeltaApplicationResult.Applied.updatedActors] for subsequent writes.
     *   Required when [document] is non-null; ignored when [document] is null (cold start).
     */
    fun applyDelta(
        document: DistributedDocument?,
        delta: DocumentDelta,
        localActorId: Long,
    ): DeltaApplicationResult<M> {
        val localValue = document?.let { decodeValue(it) }
        val localNode = document?.version_node
        val localActors = document?.let {
            Actors(local_actor = localActorId, version_vector = it.version_vector)
        }

        val incomingChanges = fromDocumentDelta(delta)

        val result = messageResolver.applyChanges(
            localValue = localValue,
            localNode = localNode,
            localActors = localActors,
            incomingChanges = incomingChanges,
            incomingBaselineActors = delta.baseline_version_vector,
        )

        return when (result.mergeResult.resolution) {
            ApplyChangesResult.REJECTED -> DeltaApplicationResult.Rejected(
                reason = "Baseline version vector diverged from local state",
            )

            ApplyChangesResult.UNCHANGED -> {
                val doc = requireNotNull(document) {
                    "UNCHANGED resolution requires non-null document"
                }
                DeltaApplicationResult.Unchanged(
                    document = doc,
                    currentValue = localValue,
                    currentActors = result.actors,
                )
            }

            ApplyChangesResult.APPLIED -> {
                val updatedValue = requireNotNull(result.mergeResult.value) {
                    "Resolver returned null value for APPLIED resolution on document ${delta.document_id}"
                }
                val updatedActors = result.actors
                val updatedDoc = rebuildDocument(
                    original = document,
                    delta = delta,
                    updatedValue = updatedValue,
                    updatedNode = result.mergeResult.node,
                    updatedActors = updatedActors,
                )
                DeltaApplicationResult.Applied(
                    document = updatedDoc,
                    updatedValue = updatedValue,
                    updatedActors = updatedActors,
                )
            }
        }
    }

    private fun toDocumentDelta(
        documentId: String,
        baselineVersionVector: Map<Long, Long>,
        changes: List<ChangeEvent<*, VersionNode, PathComponent>>,
        crdtTypeName: String,
    ): DocumentDelta {
        val dataChanges = changes.map { change ->
            DataChange(
                data_ = change.encoded()?.toByteString() ?: ByteString.EMPTY,
                version_change = VersionChange(
                    node = change.versionNode,
                    path = change.pathComponents,
                ),
            )
        }
        return DocumentDelta(
            document_id = documentId,
            changes = dataChanges,
            crdt_type_name = crdtTypeName,
            baseline_version_vector = baselineVersionVector,
        )
    }

    private fun fromDocumentDelta(
        delta: DocumentDelta,
    ): List<ChangeEvent<*, VersionNode, PathComponent>> {
        return delta.changes.map { dataChange ->
            val versionChange = requireNotNull(dataChange.version_change) {
                "DataChange missing version_change in delta for document ${delta.document_id}"
            }
            val versionNode = requireNotNull(versionChange.node) {
                "VersionChange missing node in delta for document ${delta.document_id}"
            }
            // Proto3 treats empty encoding as "cleared to default," so an empty ByteString
            // is semantically equivalent to a tombstone (null value). Sub-messages whose
            // all-default instances encode to zero bytes are conflated with deletion, which
            // is correct under proto3 wire semantics where "set to default" == "unset."
            val encodedValue = dataChange.data_.takeIf { it.size > 0 }?.toByteArray()
            messageResolver.decodeChange(
                encodedValue = encodedValue,
                pathComponents = versionChange.path,
                versionNode = versionNode,
            )
        }
    }

    private fun decodeValue(document: DistributedDocument): M? {
        val bytes = document.encodedPayload ?: return null
        try {
            return adapter.decode(bytes)
        } catch (e: Exception) {
            val hex = bytes.take(64).joinToString("") { "%02x".format(it) }
            val suffix = if (bytes.size > 64) "…" else ""
            throw IllegalStateException(
                "Failed to decode ${adapter.type?.simpleName ?: "message"} " +
                    "from CrdtData.data_ (${bytes.size} bytes, " +
                    "doc=${document.id}, " +
                    "type=${document.data_?.crdt_type_name}): " +
                    "hex=$hex$suffix",
                e,
            )
        }
    }

    private fun rebuildDocument(
        original: DistributedDocument?,
        delta: DocumentDelta,
        updatedValue: M,
        updatedNode: VersionNode?,
        updatedActors: Actors,
    ): DistributedDocument {
        val encodedData = CrdtData(
            data_ = adapter.encode(updatedValue).toByteString(),
            crdt_type_name = original?.data_?.crdt_type_name ?: delta.crdt_type_name,
        )
        val versionVector = updatedActors.version_vector

        return original?.copy(
            data_ = encodedData,
            version_node = updatedNode,
            version_vector = versionVector,
        ) ?: DistributedDocument(
            id = delta.document_id,
            data_ = encodedData,
            version_node = updatedNode,
            version_vector = versionVector,
        )
    }
}
