package co.atoms.lithium.crdt.wire.delta

import co.atoms.lithium.crdt.data.DistributedDocument
import co.atoms.lithium.crdt.test.TestMessage
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class DocumentDeltaOperationsTest {
    private val harness = DeltaTestHarness(adapter = TestMessage.ADAPTER)
    private val deltaOps = harness.deltaOps

    @Test
    fun `computeDelta returns null when peer is current`() {
        val (local, _) = harness.sharedHistory(
            "doc-1", TestMessage(stringValue = "hello"),
        )
        val delta = deltaOps.computeDelta(local.doc, local.doc.version_vector)
        assertThat(delta).isNull()
    }

    @Test
    fun `computeDelta returns delta when peer is behind`() {
        val initial = TestMessage(stringValue = "v1")
        val (local, remote) = harness.sharedHistory(
            "doc-1", initial,
            remoteEdits = listOf(initial.copy(stringValue = "v2")),
        )

        val delta = deltaOps.computeDelta(remote.doc, local.doc.version_vector)

        assertThat(delta).isNotNull()
        assertThat(delta!!.document_id).isEqualTo("doc-1")
        assertThat(delta.changes).isNotEmpty()
        assertThat(delta.baseline_version_vector).isEqualTo(local.doc.version_vector)
    }

    @Test
    fun `computeDelta returns null for empty document`() {
        val doc = DistributedDocument(id = "doc-empty")
        val delta = deltaOps.computeDelta(doc, emptyMap())
        assertThat(delta).isNull()
    }

    @Test
    fun `applyDelta APPLIED -- single field`() {
        val initial = TestMessage(stringValue = "original", int32Value = 10)
        val (local, remote) = harness.sharedHistory(
            "doc-1", initial,
            remoteEdits = listOf(initial.copy(stringValue = "remote-edit")),
        )

        val delta = deltaOps.computeDelta(remote.doc, local.doc.version_vector)
        assertThat(delta).isNotNull()

        val applied = deltaOps.applyDelta(
            local.doc, delta!!, localActorId = 1L,
        ) as DeltaApplicationResult.Applied

        assertThat(applied.updatedValue.stringValue).isEqualTo("remote-edit")
        assertThat(applied.updatedValue.int32Value).isEqualTo(10)
    }

    @Test
    fun `applyDelta APPLIED -- map entry add`() {
        val initial = TestMessage(primitiveMapValue = mapOf("key1" to 100))
        val (local, remote) = harness.sharedHistory(
            "doc-1", initial,
            remoteEdits = listOf(initial.copy(primitiveMapValue = mapOf("key1" to 100, "key2" to 200))),
        )

        val delta = deltaOps.computeDelta(remote.doc, local.doc.version_vector)
        assertThat(delta).isNotNull()

        val applied = deltaOps.applyDelta(
            local.doc, delta!!, localActorId = 1L,
        ) as DeltaApplicationResult.Applied

        assertThat(applied.updatedValue.primitiveMapValue).containsEntry("key1", 100)
        assertThat(applied.updatedValue.primitiveMapValue).containsEntry("key2", 200)
    }

    @Test
    fun `applyDelta UNCHANGED -- already seen`() {
        val initial = TestMessage(stringValue = "same")
        val (local, remote) = harness.sharedHistory(
            "doc-1", initial,
            remoteEdits = listOf(initial.copy(stringValue = "edited")),
        )

        val delta = deltaOps.computeDelta(remote.doc, local.doc.version_vector)
        assertThat(delta).isNotNull()

        val result = deltaOps.applyDelta(remote.doc, delta!!, localActorId = 2L)
        assertThat(result).isInstanceOf(DeltaApplicationResult.Unchanged::class.java)
    }

    @Test
    fun `applyDelta on null local doc`() {
        val initial = TestMessage(stringValue = "fresh", int32Value = 42)
        val (_, remote) = harness.sharedHistory(
            "doc-new", initial,
            remoteEdits = listOf(initial.copy(stringValue = "remote-fresh")),
        )

        val delta = deltaOps.computeDelta(remote.doc, emptyMap<Long, Long>())
        assertThat(delta).isNotNull()

        val applied = deltaOps.applyDelta(
            null, delta!!, localActorId = 0L,
        ) as DeltaApplicationResult.Applied

        assertThat(applied.document.id).isEqualTo("doc-new")
    }

    @Test
    fun `applyDelta preserves version vector after merge`() {
        val initial = TestMessage(stringValue = "base")
        val (local, remote) = harness.sharedHistory(
            "doc-1", initial,
            remoteEdits = listOf(initial.copy(stringValue = "remote")),
        )

        val delta = deltaOps.computeDelta(remote.doc, local.doc.version_vector)!!
        val applied = deltaOps.applyDelta(
            local.doc, delta, localActorId = 1L,
        ) as DeltaApplicationResult.Applied

        assertThat(applied.document.version_vector).containsKey(1L)
        assertThat(applied.document.version_vector).containsKey(2L)
    }
}
