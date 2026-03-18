package co.atoms.lithium.crdt.wire.delta

import co.atoms.lithium.crdt.data.Actors
import co.atoms.lithium.crdt.data.CrdtData
import co.atoms.lithium.crdt.data.DistributedDocument
import co.atoms.lithium.crdt.data.VersionNode
import co.atoms.lithium.crdt.wire.WireCrdtMessageResolver
import co.atoms.lithium.crdt.wire.WireCrdtResolverProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import okio.ByteString.Companion.toByteString

/**
 * Shared test utility for delta sync tests.
 *
 * Sets up two simulated devices from a common base state, applies independent
 * edits, and provides helpers for computing and applying deltas.
 */
class DeltaTestHarness<M : Message<M, B>, B : Message.Builder<M, B>>(
    val provider: WireCrdtResolverProvider = WireCrdtResolverProvider(),
    private val adapter: ProtoAdapter<M>,
    private val crdtTypeName: String = adapter.type?.simpleName ?: "",
) {
    val resolver: WireCrdtMessageResolver<M> = provider.messageResolver(adapter)
    val deltaOps = WireDocumentDeltaOperations(adapter, resolver)

    data class Device<M>(
        val value: M,
        val node: VersionNode?,
        val actors: Actors,
        val doc: DistributedDocument,
    )

    /**
     * Creates two devices sharing a common initial write from [localActor], then
     * diverging: the local device applies [localEdits] and the remote device
     * applies [remoteEdits].
     */
    fun sharedHistory(
        docId: String,
        initial: M,
        localActor: Long = 1L,
        remoteActor: Long = 2L,
        localEdits: List<M> = emptyList(),
        remoteEdits: List<M> = emptyList(),
    ): Pair<Device<M>, Device<M>> {
        val createResult = resolver.applyLocalWrite(
            currentValue = null, currentNode = null,
            currentActors = Actors(local_actor = localActor, version_vector = emptyMap()),
            newValue = initial, timestamp = 1000L,
        )
        val baseValue = createResult.mergeResult.value!!
        val baseNode = createResult.mergeResult.node
        val baseVV = createResult.actors.version_vector

        val local = applyEdits(
            docId, baseValue, baseNode, createResult.actors, localEdits, 2000L,
        )
        val remote = applyEdits(
            docId, baseValue, baseNode,
            Actors(local_actor = remoteActor, version_vector = baseVV),
            remoteEdits, 3000L,
        )

        return local to remote
    }

    /**
     * Computes a delta from [remote] relative to [local]'s version vector, asserts it
     * is non-null, applies it to [local], and asserts the result is [DeltaApplicationResult.Applied].
     */
    @Suppress("UNCHECKED_CAST")
    fun computeAndApply(
        local: Device<M>,
        remote: Device<M>,
    ): DeltaApplicationResult.Applied<M> {
        val delta = requireNotNull(deltaOps.computeDelta(remote.doc, local.doc.version_vector)) {
            "Expected non-null delta from remote to local"
        }
        val result = deltaOps.applyDelta(local.doc, delta, localActorId = local.actors.local_actor)
        check(result is DeltaApplicationResult.Applied) {
            "Expected Applied but got ${result::class.simpleName}"
        }
        return result as DeltaApplicationResult.Applied<M>
    }

    private fun applyEdits(
        docId: String,
        baseValue: M,
        baseNode: VersionNode?,
        actors: Actors,
        edits: List<M>,
        startTimestamp: Long,
    ): Device<M> {
        var value = baseValue
        var node = baseNode
        var currentActors = actors

        for ((i, edit) in edits.withIndex()) {
            val result = resolver.applyLocalWrite(
                currentValue = value, currentNode = node,
                currentActors = currentActors, newValue = edit,
                timestamp = startTimestamp + i,
            )
            currentActors = result.actors
            value = result.mergeResult.value!!
            node = result.mergeResult.node
        }

        return Device(
            value = value, node = node, actors = currentActors,
            doc = DistributedDocument(
                id = docId,
                data_ = CrdtData(
                    data_ = adapter.encode(value).toByteString(),
                    crdt_type_name = crdtTypeName,
                ),
                version_node = node,
                version_vector = currentActors.version_vector,
            ),
        )
    }
}
