package co.atoms.protobuf.crdt.wire.delta

import co.atoms.protobuf.crdt.data.DistributedDocument
import co.atoms.protobuf.crdt.data.DocumentDelta
import co.atoms.protobuf.crdt.test.TestContactInfo
import co.atoms.protobuf.crdt.test.TestEntry
import co.atoms.protobuf.crdt.test.TestRecord
import co.atoms.protobuf.crdt.test.TestRecordStatus
import co.atoms.protobuf.crdt.test.TestSnapshot
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

/**
 * Delta sync tests using a multi-field record model.
 *
 * Exercises nested messages (contact), ID-based repeated lists (entries),
 * REPLACE-strategy messages (snapshot), maps (tags), and enums (status)
 * to verify that deltas carry only what the peer is missing.
 */
class DeltaRecordTest {
    private val harness = DeltaTestHarness(adapter = TestRecord.ADAPTER)
    private val deltaOps = harness.deltaOps

    // ---------------------------------------------------------------
    // Scenario 1: Status change — smallest possible delta
    // ---------------------------------------------------------------

    @Test
    fun `status update produces a minimal delta`() {
        val record = TestRecord(
            record_id = "REC-001",
            status = TestRecordStatus.RECORD_DRAFT,
            contact = TestContactInfo(name = "Alice", phone = "555-0100"),
            entries = listOf(
                TestEntry(entry_id = "e-1", label = "Alpha", count = 1, amount = 1299),
            ),
            quantity = 1299,
            total = 1299,
        )
        val (local, remote) = harness.sharedHistory(
            "rec-1", record,
            remoteEdits = listOf(record.copy(status = TestRecordStatus.RECORD_ACTIVE)),
        )

        val delta = deltaOps.computeDelta(remote.doc, local.doc.version_vector)!!
        val applied = deltaOps.applyDelta(
            local.doc, delta, localActorId = 1L,
        ) as DeltaApplicationResult.Applied

        assertThat(applied.updatedValue.status).isEqualTo(TestRecordStatus.RECORD_ACTIVE)
        assertThat(applied.updatedValue.contact).isEqualTo(record.contact)
        assertThat(applied.updatedValue.entries).hasSize(1)

        assertThat(delta.changes).hasSize(1)
    }

    // ---------------------------------------------------------------
    // Scenario 2: Add a new entry
    // ---------------------------------------------------------------

    @Test
    fun `adding an entry produces a delta with only the new entry`() {
        val record = TestRecord(
            record_id = "REC-002",
            status = TestRecordStatus.RECORD_DRAFT,
            entries = listOf(
                TestEntry(entry_id = "e-1", label = "Alpha", count = 1, amount = 499),
            ),
        )
        val newEntry = TestEntry(entry_id = "e-2", label = "Beta", count = 2, amount = 650)
        val (local, remote) = harness.sharedHistory(
            "rec-2", record,
            remoteEdits = listOf(record.copy(entries = record.entries + newEntry)),
        )

        val applied = harness.computeAndApply(local, remote)

        assertThat(applied.updatedValue.entries).hasSize(2)
        val added = applied.updatedValue.entries.first { it.entry_id == "e-2" }
        assertThat(added.label).isEqualTo("Beta")
        assertThat(added.count).isEqualTo(2)
    }

    // ---------------------------------------------------------------
    // Scenario 3: Modify a field on an existing entry
    // ---------------------------------------------------------------

    @Test
    fun `changing entry count sends only the modified fields`() {
        val entries = listOf(
            TestEntry(entry_id = "e-1", label = "Alpha", count = 1, amount = 1299),
            TestEntry(entry_id = "e-2", label = "Beta", count = 1, amount = 299),
        )
        val record = TestRecord(record_id = "REC-003", entries = entries)

        val updated = entries.map {
            if (it.entry_id == "e-1") it.copy(count = 3) else it
        }
        val (local, remote) = harness.sharedHistory(
            "rec-3", record,
            remoteEdits = listOf(record.copy(entries = updated)),
        )

        val applied = harness.computeAndApply(local, remote)

        assertThat(applied.updatedValue.entries.first { it.entry_id == "e-1" }.count).isEqualTo(3)
        assertThat(applied.updatedValue.entries.first { it.entry_id == "e-2" }.count).isEqualTo(1)
    }

    // ---------------------------------------------------------------
    // Scenario 4: Concurrent edits — different parts of the record
    // ---------------------------------------------------------------

    @Test
    fun `concurrent edits to different parts merge without data loss`() {
        val record = TestRecord(
            record_id = "REC-004",
            status = TestRecordStatus.RECORD_DRAFT,
            contact = TestContactInfo(name = "Bob", phone = "555-0200"),
            entries = listOf(
                TestEntry(entry_id = "e-1", label = "Alpha", count = 1, amount = 1499),
            ),
            notes = "original notes",
        )

        val (local, remote) = harness.sharedHistory(
            "rec-4", record,
            localEdits = listOf(record.copy(notes = "updated by device A")),
            remoteEdits = listOf(
                record.copy(
                    contact = record.contact!!.copy(phone = "555-9999"),
                    status = TestRecordStatus.RECORD_REVIEW,
                ),
            ),
        )

        val applied = harness.computeAndApply(local, remote)

        assertThat(applied.updatedValue.notes).isEqualTo("updated by device A")
        assertThat(applied.updatedValue.contact!!.phone).isEqualTo("555-9999")
        assertThat(applied.updatedValue.status).isEqualTo(TestRecordStatus.RECORD_REVIEW)
    }

    // ---------------------------------------------------------------
    // Scenario 5: Snapshot (REPLACE strategy — atomic swap)
    // ---------------------------------------------------------------

    @Test
    fun `snapshot captured atomically via REPLACE strategy`() {
        val record = TestRecord(
            record_id = "REC-005",
            status = TestRecordStatus.RECORD_REVIEW,
            total = 2500,
        )
        val snap = TestSnapshot(
            type = "FINALIZED",
            amount = 2500,
            adjustment = 500,
            reference_id = "ref-abc-123",
        )
        val (local, remote) = harness.sharedHistory(
            "rec-5", record,
            remoteEdits = listOf(
                record.copy(status = TestRecordStatus.RECORD_CLOSED, snapshot = snap),
            ),
        )

        val applied = harness.computeAndApply(local, remote)

        assertThat(applied.updatedValue.status).isEqualTo(TestRecordStatus.RECORD_CLOSED)
        assertThat(applied.updatedValue.snapshot).isEqualTo(snap)
        assertThat(applied.updatedValue.snapshot!!.reference_id).isEqualTo("ref-abc-123")
    }

    // ---------------------------------------------------------------
    // Scenario 6: Tag map additions
    // ---------------------------------------------------------------

    @Test
    fun `tag map additions merge per key`() {
        val record = TestRecord(
            record_id = "REC-006",
            tags = mapOf("source" to "import"),
        )
        val (local, remote) = harness.sharedHistory(
            "rec-6", record,
            localEdits = listOf(record.copy(tags = record.tags + ("owner" to "Jane"))),
            remoteEdits = listOf(record.copy(tags = record.tags + ("region" to "US-WEST"))),
        )

        val applied = harness.computeAndApply(local, remote)

        assertThat(applied.updatedValue.tags).containsEntry("source", "import")
        assertThat(applied.updatedValue.tags).containsEntry("owner", "Jane")
        assertThat(applied.updatedValue.tags).containsEntry("region", "US-WEST")
    }

    // ---------------------------------------------------------------
    // Scenario 7: Multi-step lifecycle
    // ---------------------------------------------------------------

    @Test
    fun `full lifecycle produces correct deltas at each step`() {
        val draft = TestRecord(
            record_id = "REC-007",
            status = TestRecordStatus.RECORD_DRAFT,
            contact = TestContactInfo(name = "Carol"),
        )

        val active = draft.copy(
            status = TestRecordStatus.RECORD_ACTIVE,
            entries = listOf(
                TestEntry(entry_id = "e1", label = "First", count = 3, amount = 399),
                TestEntry(entry_id = "e2", label = "Second", count = 1, amount = 299),
            ),
            quantity = 1496,
            adjustment = 120,
            total = 1616,
        )

        val (_, deviceAfterActivate) = harness.sharedHistory(
            "rec-7", draft,
            remoteEdits = listOf(active),
        )

        val deltaToEmpty = deltaOps.computeDelta(deviceAfterActivate.doc, emptyMap())
        assertThat(deltaToEmpty).isNotNull()
        val applied = deltaOps.applyDelta(
            null, deltaToEmpty!!, localActorId = 0L,
        ) as DeltaApplicationResult.Applied
        assertThat(applied.updatedValue.entries).hasSize(2)
        assertThat(applied.updatedValue.status).isEqualTo(TestRecordStatus.RECORD_ACTIVE)
        assertThat(applied.updatedValue.total).isEqualTo(1616)
    }

    // ---------------------------------------------------------------
    // Scenario 8: Concurrent entry edits on different entries
    // ---------------------------------------------------------------

    @Test
    fun `concurrent edits to different entries merge independently`() {
        val record = TestRecord(
            record_id = "REC-008",
            entries = listOf(
                TestEntry(entry_id = "e-1", label = "Alpha", count = 1, amount = 1299),
                TestEntry(entry_id = "e-2", label = "Beta", count = 1, amount = 499),
                TestEntry(entry_id = "e-3", label = "Gamma", count = 1, amount = 299),
            ),
        )

        val localEntries = record.entries.map {
            if (it.entry_id == "e-1") it.copy(description = "updated locally") else it
        }
        val remoteEntries = record.entries.map {
            if (it.entry_id == "e-3") it.copy(count = 2) else it
        }

        val (local, remote) = harness.sharedHistory(
            "rec-8", record,
            localEdits = listOf(record.copy(entries = localEntries)),
            remoteEdits = listOf(record.copy(entries = remoteEntries)),
        )

        val applied = harness.computeAndApply(local, remote)

        val merged = applied.updatedValue.entries.associateBy { it.entry_id }
        assertThat(merged["e-1"]!!.description).isEqualTo("updated locally")
        assertThat(merged["e-2"]!!.count).isEqualTo(1)
        assertThat(merged["e-3"]!!.count).isEqualTo(2)
    }

    // ---------------------------------------------------------------
    // Scenario 9: Entry attributes (nested map inside repeated entry)
    // ---------------------------------------------------------------

    @Test
    fun `entry attribute changes propagate through nested map`() {
        val record = TestRecord(
            record_id = "REC-009",
            entries = listOf(
                TestEntry(
                    entry_id = "e-1", label = "Alpha", count = 1, amount = 1299,
                    attributes = mapOf("color" to "red"),
                ),
            ),
        )
        val updatedEntries = listOf(
            record.entries[0].copy(attributes = mapOf("color" to "blue", "size" to "large")),
        )
        val (local, remote) = harness.sharedHistory(
            "rec-9", record,
            remoteEdits = listOf(record.copy(entries = updatedEntries)),
        )

        val applied = harness.computeAndApply(local, remote)

        val entry = applied.updatedValue.entries.first()
        assertThat(entry.attributes).containsEntry("color", "blue")
        assertThat(entry.attributes).containsEntry("size", "large")
    }

    // ---------------------------------------------------------------
    // Scenario 10: Delta is smaller than full document
    // ---------------------------------------------------------------

    @Test
    fun `delta payload is smaller than full document for single-field edit`() {
        val record = TestRecord(
            record_id = "REC-010",
            status = TestRecordStatus.RECORD_DRAFT,
            contact = TestContactInfo(name = "David", phone = "555-0300", email = "david@example.com"),
            entries = listOf(
                TestEntry(entry_id = "e1", label = "Alpha", count = 1, amount = 2499),
                TestEntry(entry_id = "e2", label = "Beta", count = 1, amount = 899),
                TestEntry(entry_id = "e3", label = "Gamma", count = 2, amount = 1200),
            ),
            tags = mapOf("group" to "12", "assignee" to "Emily", "priority" to "high"),
            quantity = 5798,
            adjustment = 464,
            total = 6262,
            notes = "Requires review before closing",
        )
        val (local, remote) = harness.sharedHistory(
            "rec-10", record,
            remoteEdits = listOf(record.copy(notes = "Reviewed and approved")),
        )

        val delta = deltaOps.computeDelta(remote.doc, local.doc.version_vector)!!
        val deltaBytes = DocumentDelta.ADAPTER.encode(delta)
        val fullDocBytes = DistributedDocument.ADAPTER.encode(remote.doc)

        assertThat(deltaBytes.size).isLessThan(fullDocBytes.size)
    }

    // ---------------------------------------------------------------
    // Scenario 11: Entry removal — tombstone propagation
    // ---------------------------------------------------------------

    @Test
    fun `removing an entry produces a delta carrying the tombstone`() {
        val record = TestRecord(
            record_id = "REC-011",
            entries = listOf(
                TestEntry(entry_id = "e-1", label = "Alpha", count = 1, amount = 500),
                TestEntry(entry_id = "e-2", label = "Beta", count = 2, amount = 750),
                TestEntry(entry_id = "e-3", label = "Gamma", count = 1, amount = 300),
            ),
        )
        val (local, remote) = harness.sharedHistory(
            "rec-11", record,
            remoteEdits = listOf(
                record.copy(entries = record.entries.filter { it.entry_id != "e-2" }),
            ),
        )

        val delta = deltaOps.computeDelta(remote.doc, local.doc.version_vector)
        assertThat(delta).isNotNull()

        val applied = deltaOps.applyDelta(
            local.doc, delta!!, localActorId = 1L,
        ) as DeltaApplicationResult.Applied

        assertThat(applied.updatedValue.entries.map { it.entry_id })
            .containsExactly("e-1", "e-3")
        assertThat(applied.updatedValue.entries).hasSize(2)
    }

    @Test
    fun `removing all entries produces a delta that clears the list`() {
        val record = TestRecord(
            record_id = "REC-012",
            entries = listOf(
                TestEntry(entry_id = "e-1", label = "Alpha", count = 1, amount = 500),
                TestEntry(entry_id = "e-2", label = "Beta", count = 1, amount = 250),
            ),
        )
        val (local, remote) = harness.sharedHistory(
            "rec-12", record,
            remoteEdits = listOf(record.copy(entries = emptyList())),
        )

        val applied = harness.computeAndApply(local, remote)

        assertThat(applied.updatedValue.entries).isEmpty()
    }

    @Test
    fun `concurrent entry removal and entry modification merge correctly`() {
        val record = TestRecord(
            record_id = "REC-013",
            entries = listOf(
                TestEntry(entry_id = "e-1", label = "Alpha", count = 1, amount = 500),
                TestEntry(entry_id = "e-2", label = "Beta", count = 1, amount = 750),
                TestEntry(entry_id = "e-3", label = "Gamma", count = 1, amount = 300),
            ),
        )
        val (local, remote) = harness.sharedHistory(
            "rec-13", record,
            localEdits = listOf(
                record.copy(
                    entries = record.entries.map {
                        if (it.entry_id == "e-1") it.copy(count = 10) else it
                    },
                ),
            ),
            remoteEdits = listOf(
                record.copy(entries = record.entries.filter { it.entry_id != "e-2" }),
            ),
        )

        val applied = harness.computeAndApply(local, remote)

        val merged = applied.updatedValue.entries.associateBy { it.entry_id }
        assertThat(merged).doesNotContainKey("e-2")
        assertThat(merged["e-1"]!!.count).isEqualTo(10)
        assertThat(merged["e-3"]!!.count).isEqualTo(1)
    }

    @Test
    fun `removing an entry then adding a new one produces correct delta`() {
        val record = TestRecord(
            record_id = "REC-014",
            entries = listOf(
                TestEntry(entry_id = "e-1", label = "Alpha", count = 1, amount = 500),
                TestEntry(entry_id = "e-2", label = "Beta", count = 1, amount = 750),
            ),
        )
        val (local, remote) = harness.sharedHistory(
            "rec-14", record,
            remoteEdits = listOf(
                record.copy(
                    entries = listOf(
                        record.entries[0],
                        TestEntry(entry_id = "e-3", label = "Replacement", count = 1, amount = 900),
                    ),
                ),
            ),
        )

        val applied = harness.computeAndApply(local, remote)

        val ids = applied.updatedValue.entries.map { it.entry_id }
        assertThat(ids).contains("e-1")
        assertThat(ids).contains("e-3")
        assertThat(ids).doesNotContain("e-2")
    }

    // ===============================================================
    // Edge cases
    // ===============================================================

    @Test
    fun `no-op write produces no delta`() {
        val record = TestRecord(
            record_id = "REC-E1",
            status = TestRecordStatus.RECORD_DRAFT,
            contact = TestContactInfo(name = "Alice"),
        )
        val (local, remote) = harness.sharedHistory("edge-1", record, remoteEdits = listOf(record))

        val delta = deltaOps.computeDelta(remote.doc, local.doc.version_vector)
        assertThat(delta).isNull()
    }

    @Test
    fun `clearing a field sends the default value in the delta`() {
        val record = TestRecord(record_id = "REC-E2", notes = "important note")
        val (local, remote) = harness.sharedHistory(
            "edge-2", record,
            remoteEdits = listOf(record.copy(notes = "")),
        )

        val delta = deltaOps.computeDelta(remote.doc, local.doc.version_vector)
        assertThat(delta).isNotNull()

        val applied = deltaOps.applyDelta(
            local.doc, delta!!, localActorId = 1L,
        ) as DeltaApplicationResult.Applied
        assertThat(applied.updatedValue.notes).isEmpty()
    }

    @Test
    fun `same field edited by both devices resolves via LWW`() {
        val record = TestRecord(record_id = "REC-E3", notes = "original")

        val (local, remote) = harness.sharedHistory(
            "edge-3", record,
            localEdits = listOf(record.copy(notes = "local says this")),
            remoteEdits = listOf(record.copy(notes = "remote says that")),
        )

        val applied = harness.computeAndApply(local, remote)

        assertThat(applied.updatedValue.notes).isAnyOf("local says this", "remote says that")
    }

    @Test
    fun `replacing an existing REPLACE field overwrites atomically`() {
        val record = TestRecord(
            record_id = "REC-E4",
            snapshot = TestSnapshot(type = "DRAFT", amount = 1000),
        )
        val newSnap = TestSnapshot(
            type = "FINAL", amount = 1000, adjustment = 200, reference_id = "ref-new",
        )
        val (local, remote) = harness.sharedHistory(
            "edge-4", record,
            remoteEdits = listOf(record.copy(snapshot = newSnap)),
        )

        val applied = harness.computeAndApply(local, remote)

        assertThat(applied.updatedValue.snapshot!!.type).isEqualTo("FINAL")
        assertThat(applied.updatedValue.snapshot!!.adjustment).isEqualTo(200)
        assertThat(applied.updatedValue.snapshot!!.reference_id).isEqualTo("ref-new")
    }

    @Test
    fun `all-default record produces no delta because write is a no-op`() {
        val record = TestRecord()
        val (_, remote) = harness.sharedHistory("edge-5", record, remoteEdits = emptyList())

        val delta = deltaOps.computeDelta(remote.doc, emptyMap())
        assertThat(delta).isNull()
    }

    @Test
    fun `single entry change in large list produces small delta`() {
        val entries = (1..20).map { i ->
            TestEntry(entry_id = "e-$i", label = "Entry #$i", count = 1, amount = 100L * i)
        }
        val record = TestRecord(record_id = "REC-E6", entries = entries)

        val updated = entries.map {
            if (it.entry_id == "e-15") it.copy(count = 99) else it
        }
        val (local, remote) = harness.sharedHistory(
            "edge-6", record,
            remoteEdits = listOf(record.copy(entries = updated)),
        )

        val delta = deltaOps.computeDelta(remote.doc, local.doc.version_vector)!!
        val fullDocSize = DistributedDocument.ADAPTER.encode(remote.doc).size
        val deltaSize = DocumentDelta.ADAPTER.encode(delta).size

        assertThat(deltaSize).isLessThan(fullDocSize)

        val applied = deltaOps.applyDelta(
            local.doc, delta, localActorId = 1L,
        ) as DeltaApplicationResult.Applied
        assertThat(applied.updatedValue.entries.first { it.entry_id == "e-15" }.count).isEqualTo(99)
        assertThat(applied.updatedValue.entries.first { it.entry_id == "e-1" }.count).isEqualTo(1)
    }

    @Test
    fun `setting enum back to default value propagates`() {
        val record = TestRecord(record_id = "REC-E7", status = TestRecordStatus.RECORD_REVIEW)
        val (local, remote) = harness.sharedHistory(
            "edge-7", record,
            remoteEdits = listOf(record.copy(status = TestRecordStatus.RECORD_UNKNOWN)),
        )

        val delta = deltaOps.computeDelta(remote.doc, local.doc.version_vector)
        assertThat(delta).isNotNull()

        val applied = deltaOps.applyDelta(
            local.doc, delta!!, localActorId = 1L,
        ) as DeltaApplicationResult.Applied
        assertThat(applied.updatedValue.status).isEqualTo(TestRecordStatus.RECORD_UNKNOWN)
    }

    @Test
    fun `multiple edits from same actor collapse to final state`() {
        val record = TestRecord(record_id = "REC-E8", notes = "v0")

        val (local, remote) = harness.sharedHistory(
            "edge-8", record,
            remoteEdits = listOf(
                record.copy(notes = "v1"),
                record.copy(notes = "v2"),
                record.copy(notes = "v3-final"),
            ),
        )

        val delta = deltaOps.computeDelta(remote.doc, local.doc.version_vector)!!

        assertThat(delta.changes).hasSize(1)

        val applied = deltaOps.applyDelta(
            local.doc, delta, localActorId = 1L,
        ) as DeltaApplicationResult.Applied
        assertThat(applied.updatedValue.notes).isEqualTo("v3-final")
    }

    @Test
    fun `applying the same delta twice returns UNCHANGED on second apply`() {
        val record = TestRecord(record_id = "REC-E9", notes = "original")

        val (local, remote) = harness.sharedHistory(
            "edge-9", record,
            remoteEdits = listOf(record.copy(notes = "changed")),
        )

        val delta = deltaOps.computeDelta(remote.doc, local.doc.version_vector)!!

        val first = deltaOps.applyDelta(
            local.doc, delta, localActorId = 1L,
        ) as DeltaApplicationResult.Applied

        val second = deltaOps.applyDelta(first.document, delta, localActorId = 1L)
        assertThat(second).isInstanceOf(DeltaApplicationResult.Unchanged::class.java)
    }

    @Test
    fun `overwriting an existing tag propagates new value`() {
        val record = TestRecord(
            record_id = "REC-E10",
            tags = mapOf("assignee" to "Alice", "priority" to "low"),
        )
        val (local, remote) = harness.sharedHistory(
            "edge-10", record,
            remoteEdits = listOf(record.copy(tags = mapOf("assignee" to "Bob", "priority" to "low"))),
        )

        val applied = harness.computeAndApply(local, remote)

        assertThat(applied.updatedValue.tags["assignee"]).isEqualTo("Bob")
        assertThat(applied.updatedValue.tags["priority"]).isEqualTo("low")
    }

    @Test
    fun `clearing a nested message field propagates via delta`() {
        val record = TestRecord(
            record_id = "REC-E11",
            contact = TestContactInfo(name = "Alice", phone = "555-1234"),
        )
        val (local, remote) = harness.sharedHistory(
            "edge-11", record,
            remoteEdits = listOf(record.copy(contact = null)),
        )

        val applied = harness.computeAndApply(local, remote)

        assertThat(applied.updatedValue.contact).isNull()
    }

    @Test
    fun `adding a new entry and modifying existing in same edit`() {
        val record = TestRecord(
            record_id = "REC-E12",
            entries = listOf(
                TestEntry(entry_id = "e-1", label = "Alpha", count = 1, amount = 1299),
            ),
        )
        val (local, remote) = harness.sharedHistory(
            "edge-12", record,
            remoteEdits = listOf(
                record.copy(
                    entries = listOf(
                        record.entries[0].copy(count = 5),
                        TestEntry(entry_id = "e-2", label = "Beta", count = 1, amount = 199),
                    ),
                ),
            ),
        )

        val applied = harness.computeAndApply(local, remote)

        assertThat(applied.updatedValue.entries).hasSize(2)
        assertThat(applied.updatedValue.entries.first { it.entry_id == "e-1" }.count).isEqualTo(5)
        assertThat(applied.updatedValue.entries.first { it.entry_id == "e-2" }.label).isEqualTo("Beta")
    }

    // ===============================================================
    // Full sync fallback — delta rejected, recover via resolveConflict
    //
    // When the delta's baseline_version_vector contains actors unknown
    // to the local device, applyDelta returns Rejected. Callers must
    // fall back to full-state resolveConflict. These tests verify
    // the complete fallback flow and that string/bytes fields survive
    // the full-state merge path (which uses the fixed decoder).
    // ===============================================================

    private fun rejectDelta(
        local: DeltaTestHarness.Device<TestRecord>,
        remote: DeltaTestHarness.Device<TestRecord>,
    ): DeltaApplicationResult.Rejected {
        val delta = deltaOps.computeDelta(remote.doc, local.doc.version_vector)!!
        val stale = delta.copy(
            baseline_version_vector = delta.baseline_version_vector + (999L to 100L),
        )
        val result = deltaOps.applyDelta(local.doc, stale, localActorId = local.actors.local_actor)
        assertThat(result).isInstanceOf(DeltaApplicationResult.Rejected::class.java)
        return result as DeltaApplicationResult.Rejected
    }

    private fun fullSyncFallback(
        local: DeltaTestHarness.Device<TestRecord>,
        remote: DeltaTestHarness.Device<TestRecord>,
    ): TestRecord {
        val result = harness.resolver.resolveConflict(
            localValue = local.value,
            localNode = local.doc.version_node,
            localActors = local.actors,
            incomingValue = remote.value,
            incomingNode = remote.doc.version_node!!,
            incomingVersionVector = remote.doc.version_vector,
        )
        return result.mergeResult.value!!
    }

    @Test
    fun `full sync fallback preserves string field after delta rejection`() {
        val record = TestRecord(
            record_id = "REC-FB1",
            notes = "original notes",
            contact = TestContactInfo(name = "Alice", phone = "555-1234"),
        )
        val (local, remote) = harness.sharedHistory(
            "fb-1", record,
            remoteEdits = listOf(record.copy(notes = "updated by remote")),
        )

        rejectDelta(local, remote)
        val merged = fullSyncFallback(local, remote)

        assertThat(merged.notes).isEqualTo("updated by remote")
        assertThat(merged.contact?.name).isEqualTo("Alice")
        assertThat(merged.contact?.phone).isEqualTo("555-1234")
    }

    @Test
    fun `full sync fallback merges concurrent string edits after rejection`() {
        val record = TestRecord(
            record_id = "REC-FB2",
            notes = "original",
            contact = TestContactInfo(name = "Alice", phone = "555-0100"),
            status = TestRecordStatus.RECORD_DRAFT,
        )
        val (local, remote) = harness.sharedHistory(
            "fb-2", record,
            localEdits = listOf(record.copy(notes = "local notes")),
            remoteEdits = listOf(
                record.copy(contact = record.contact!!.copy(phone = "555-9999")),
            ),
        )

        rejectDelta(local, remote)
        val merged = fullSyncFallback(local, remote)

        assertThat(merged.notes).isEqualTo("local notes")
        assertThat(merged.contact?.phone).isEqualTo("555-9999")
        assertThat(merged.contact?.name).isEqualTo("Alice")
    }

    @Test
    fun `full sync fallback preserves nested message strings after rejection`() {
        val record = TestRecord(
            record_id = "REC-FB3",
            contact = TestContactInfo(name = "Bob", phone = "555-0200", email = "bob@example.com"),
        )
        val (local, remote) = harness.sharedHistory(
            "fb-3", record,
            remoteEdits = listOf(
                record.copy(contact = record.contact!!.copy(email = "bob@newdomain.com")),
            ),
        )

        rejectDelta(local, remote)
        val merged = fullSyncFallback(local, remote)

        assertThat(merged.contact?.name).isEqualTo("Bob")
        assertThat(merged.contact?.phone).isEqualTo("555-0200")
        assertThat(merged.contact?.email).isEqualTo("bob@newdomain.com")
    }

    @Test
    fun `full sync fallback preserves map string values after rejection`() {
        val record = TestRecord(
            record_id = "REC-FB4",
            tags = mapOf("source" to "import", "priority" to "high"),
        )
        val (local, remote) = harness.sharedHistory(
            "fb-4", record,
            localEdits = listOf(record.copy(tags = record.tags + ("owner" to "Jane"))),
            remoteEdits = listOf(record.copy(tags = record.tags + ("region" to "US-WEST"))),
        )

        rejectDelta(local, remote)
        val merged = fullSyncFallback(local, remote)

        assertThat(merged.tags).containsEntry("source", "import")
        assertThat(merged.tags).containsEntry("priority", "high")
        assertThat(merged.tags).containsEntry("owner", "Jane")
        assertThat(merged.tags).containsEntry("region", "US-WEST")
    }

    @Test
    fun `full sync fallback preserves entry list with string fields after rejection`() {
        val record = TestRecord(
            record_id = "REC-FB5",
            entries = listOf(
                TestEntry(entry_id = "e-1", label = "Alpha", count = 1, amount = 500),
                TestEntry(entry_id = "e-2", label = "Beta", count = 2, amount = 750),
            ),
        )
        val newEntry = TestEntry(entry_id = "e-3", label = "Gamma", count = 1, amount = 300)
        val (local, remote) = harness.sharedHistory(
            "fb-5", record,
            remoteEdits = listOf(record.copy(entries = record.entries + newEntry)),
        )

        rejectDelta(local, remote)
        val merged = fullSyncFallback(local, remote)

        assertThat(merged.entries).hasSize(3)
        assertThat(merged.entries.map { it.label }).containsExactly("Alpha", "Beta", "Gamma")
    }

    @Test
    fun `full sync fallback preserves REPLACE snapshot strings after rejection`() {
        val record = TestRecord(
            record_id = "REC-FB6",
            status = TestRecordStatus.RECORD_REVIEW,
        )
        val snap = TestSnapshot(
            type = "FINALIZED",
            amount = 2500,
            reference_id = "ref-xyz-789",
        )
        val (local, remote) = harness.sharedHistory(
            "fb-6", record,
            remoteEdits = listOf(
                record.copy(status = TestRecordStatus.RECORD_CLOSED, snapshot = snap),
            ),
        )

        rejectDelta(local, remote)
        val merged = fullSyncFallback(local, remote)

        assertThat(merged.status).isEqualTo(TestRecordStatus.RECORD_CLOSED)
        assertThat(merged.snapshot?.type).isEqualTo("FINALIZED")
        assertThat(merged.snapshot?.reference_id).isEqualTo("ref-xyz-789")
    }

    @Test
    fun `full sync fallback handles multi-field concurrent edits after rejection`() {
        val record = TestRecord(
            record_id = "REC-FB7",
            status = TestRecordStatus.RECORD_DRAFT,
            contact = TestContactInfo(name = "Carol", phone = "555-0300"),
            entries = listOf(
                TestEntry(entry_id = "e-1", label = "Alpha", count = 1, amount = 1299),
            ),
            tags = mapOf("team" to "backend"),
            notes = "initial notes",
        )
        val (local, remote) = harness.sharedHistory(
            "fb-7", record,
            localEdits = listOf(
                record.copy(
                    notes = "local updated notes",
                    tags = record.tags + ("assignee" to "Dave"),
                ),
            ),
            remoteEdits = listOf(
                record.copy(
                    status = TestRecordStatus.RECORD_ACTIVE,
                    contact = record.contact!!.copy(email = "carol@example.com"),
                    entries = record.entries + TestEntry(
                        entry_id = "e-2", label = "Beta", count = 3, amount = 899,
                    ),
                ),
            ),
        )

        rejectDelta(local, remote)
        val merged = fullSyncFallback(local, remote)

        assertThat(merged.notes).isEqualTo("local updated notes")
        assertThat(merged.tags).containsEntry("assignee", "Dave")
        assertThat(merged.status).isEqualTo(TestRecordStatus.RECORD_ACTIVE)
        assertThat(merged.contact?.email).isEqualTo("carol@example.com")
        assertThat(merged.contact?.name).isEqualTo("Carol")
        assertThat(merged.entries).hasSize(2)
        assertThat(merged.entries.first { it.entry_id == "e-2" }.label).isEqualTo("Beta")
    }
}
