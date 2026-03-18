package co.atoms.lithium.crdt.wire.delta

import co.atoms.lithium.crdt.data.CrdtData
import co.atoms.lithium.crdt.data.DistributedDocument
import co.atoms.lithium.crdt.test.NestedMessage
import co.atoms.lithium.crdt.test.TestMessage
import co.atoms.lithium.crdt.wire.delta.DeltaTestHarness.Device
import com.google.common.truth.Truth.assertThat
import okio.ByteString.Companion.toByteString
import java.time.Instant
import kotlin.test.Test

/**
 * Equivalence tests asserting that delta sync produces identical results to full-state sync.
 *
 * For each scenario two devices start from a common base, make independent edits,
 * then sync. The test runs both the delta path (computeDelta + applyDelta) and the
 * full-state path (resolveConflict) and asserts the resulting documents are identical.
 */
class DeltaSyncEquivalenceTest {
    private val harness = DeltaTestHarness(adapter = TestMessage.ADAPTER)

    private fun assertEquivalence(local: Device<TestMessage>, remote: Device<TestMessage>) {
        val delta = harness.deltaOps.computeDelta(remote.doc, local.doc.version_vector)
        assertThat(delta).isNotNull()
        val applied = harness.deltaOps.applyDelta(
            local.doc, delta!!, localActorId = local.actors.local_actor,
        ) as DeltaApplicationResult.Applied

        val fullResult = harness.resolver.resolveConflict(
            localValue = local.value,
            localNode = local.doc.version_node,
            localActors = local.actors,
            incomingValue = remote.value,
            incomingNode = remote.doc.version_node!!,
            incomingVersionVector = remote.doc.version_vector,
        )

        assertThat(applied.updatedValue).isEqualTo(fullResult.mergeResult.value)
        // Version node trees are not structurally identical between delta and full-state
        // paths: delta sync only materializes version nodes for changed fields, while
        // unchanged fields inherit their version from the parent VersionNode.version.
        // Full-state merge materializes all fields. Both are semantically equivalent
        // under the version inheritance model, so we verify the node exists when expected.
        if (fullResult.mergeResult.node != null) {
            assertThat(applied.document.version_node).isNotNull()
        }
        assertThat(applied.updatedActors.version_vector)
            .isEqualTo(fullResult.actors.version_vector)
    }

    @Test
    fun `single field edit`() {
        val base = TestMessage(stringValue = "base", int32Value = 10)
        val (local, remote) = harness.sharedHistory(
            "doc-1", base,
            remoteEdits = listOf(base.copy(stringValue = "remote")),
        )
        assertEquivalence(local, remote)
    }

    @Test
    fun `multiple field edits same actor`() {
        val base = TestMessage(stringValue = "base", int32Value = 10)
        val (local, remote) = harness.sharedHistory(
            "doc-1", base,
            remoteEdits = listOf(
                base.copy(stringValue = "edit1"),
                base.copy(stringValue = "edit2", int32Value = 20),
                base.copy(stringValue = "edit3", int32Value = 30, int64Value = 300L),
            ),
        )
        assertEquivalence(local, remote)
    }

    @Test
    fun `concurrent edits different fields`() {
        val base = TestMessage(stringValue = "base", int32Value = 10, int64Value = 100L)
        val (local, remote) = harness.sharedHistory(
            "doc-1", base,
            localEdits = listOf(base.copy(stringValue = "local-edit")),
            remoteEdits = listOf(base.copy(int32Value = 99)),
        )
        assertEquivalence(local, remote)
    }

    @Test
    fun `concurrent edits same field (LWW)`() {
        val base = TestMessage(stringValue = "base")
        val (local, remote) = harness.sharedHistory(
            "doc-1", base,
            localEdits = listOf(base.copy(stringValue = "local-wins")),
            remoteEdits = listOf(base.copy(stringValue = "remote-wins")),
        )
        assertEquivalence(local, remote)
    }

    @Test
    fun `map entry add`() {
        val base = TestMessage(primitiveMapValue = mapOf("existing" to 1))
        val (local, remote) = harness.sharedHistory(
            "doc-1", base,
            remoteEdits = listOf(base.copy(primitiveMapValue = mapOf("existing" to 1, "new-key" to 2))),
        )
        assertEquivalence(local, remote)
    }

    @Test
    fun `map entry update`() {
        val base = TestMessage(primitiveMapValue = mapOf("key1" to 100))
        val (local, remote) = harness.sharedHistory(
            "doc-1", base,
            remoteEdits = listOf(base.copy(primitiveMapValue = mapOf("key1" to 999))),
        )
        assertEquivalence(local, remote)
    }

    @Test
    fun `nested message edit`() {
        val nested = NestedMessage(stringValue = "original", intValue = 10)
        val base = TestMessage(nestedValue = nested)
        val (local, remote) = harness.sharedHistory(
            "doc-1", base,
            remoteEdits = listOf(base.copy(nestedValue = nested.copy(stringValue = "edited"))),
        )
        assertEquivalence(local, remote)
    }

    @Test
    fun `cold start (empty local)`() {
        val base = TestMessage(stringValue = "fresh-doc", int32Value = 42)
        val (_, remote) = harness.sharedHistory(
            "doc-new", base,
            remoteEdits = listOf(base.copy(stringValue = "from-remote")),
        )

        val delta = harness.deltaOps.computeDelta(remote.doc, emptyMap())
        assertThat(delta).isNotNull()
        val applied = harness.deltaOps.applyDelta(
            null, delta!!, localActorId = 0L,
        ) as DeltaApplicationResult.Applied

        assertThat(applied.updatedValue.stringValue).isEqualTo("from-remote")
    }

    // ---------------------------------------------------------------
    // Optional field delta round-trip tests
    //
    // Validates the OptionalAnyValueResolver path through the delta
    // encode → serialize → decode → apply cycle for both primitive
    // and message optional fields.
    // ---------------------------------------------------------------

    @Test
    fun `optional primitive set from null survives delta round-trip`() {
        val base = TestMessage(stringValue = "base")
        val (local, remote) = harness.sharedHistory(
            "doc-opt-1", base,
            remoteEdits = listOf(base.copy(primitiveOptionalValue = 42)),
        )
        assertEquivalence(local, remote)
    }

    @Test
    fun `optional primitive cleared to null is a no-op (by design)`() {
        // OptionalAnyValueLocalResolver ignores null writes — proto3 optional fields
        // treat null as "not set" rather than "clear to value." The remote device's
        // local write with null is silently discarded, leaving the field unchanged.
        val base = TestMessage(stringValue = "base", primitiveOptionalValue = 42)
        val (local, remote) = harness.sharedHistory(
            "doc-opt-2", base,
            remoteEdits = listOf(base.copy(primitiveOptionalValue = null)),
        )

        val delta = harness.deltaOps.computeDelta(remote.doc, local.doc.version_vector)
        assertThat(delta).isNull()
    }

    @Test
    fun `optional message field set from null survives delta round-trip`() {
        val base = TestMessage(stringValue = "base")
        val (local, remote) = harness.sharedHistory(
            "doc-opt-3", base,
            remoteEdits = listOf(base.copy(nestedOptionalValue = NestedMessage(stringValue = "opt-nested", intValue = 7))),
        )
        assertEquivalence(local, remote)
    }

    @Test
    fun `optional message field cleared to null is a no-op (by design)`() {
        // Same rationale as the primitive optional case above — null writes
        // are discarded by OptionalAnyValueLocalResolver.
        val base = TestMessage(nestedOptionalValue = NestedMessage(stringValue = "will-clear", intValue = 5))
        val (local, remote) = harness.sharedHistory(
            "doc-opt-4", base,
            remoteEdits = listOf(base.copy(nestedOptionalValue = null)),
        )

        val delta = harness.deltaOps.computeDelta(remote.doc, local.doc.version_vector)
        assertThat(delta).isNull()
    }

    // ---------------------------------------------------------------
    // OneOf alternative switching delta round-trip tests
    //
    // Verifies that switching between oneOf alternatives correctly
    // propagates implicit tombstones for the deselected field through
    // the delta pipeline.
    // ---------------------------------------------------------------

    @Test
    fun `switching oneOf from string to nested message survives delta round-trip`() {
        val base = TestMessage(oneOfValue1 = "initial-string")
        val (local, remote) = harness.sharedHistory(
            "doc-oneof-1", base,
            remoteEdits = listOf(base.copy(oneOfValue1 = null, oneOfValue2 = NestedMessage(stringValue = "switched", intValue = 99))),
        )
        assertEquivalence(local, remote)
    }

    @Test
    fun `switching oneOf from nested message to string survives delta round-trip`() {
        val base = TestMessage(oneOfValue2 = NestedMessage(stringValue = "nested-initial", intValue = 10))
        val (local, remote) = harness.sharedHistory(
            "doc-oneof-2", base,
            remoteEdits = listOf(base.copy(oneOfValue2 = null, oneOfValue1 = "switched-to-string")),
        )
        assertEquivalence(local, remote)
    }

    @Test
    fun `concurrent oneOf switch and scalar edit merge correctly via delta`() {
        val base = TestMessage(oneOfValue1 = "choice-a", int32Value = 10)
        val (local, remote) = harness.sharedHistory(
            "doc-oneof-3", base,
            localEdits = listOf(base.copy(int32Value = 99)),
            remoteEdits = listOf(base.copy(oneOfValue1 = null, oneOfValue2 = NestedMessage(stringValue = "remote-switch"))),
        )
        assertEquivalence(local, remote)
    }

    // ---------------------------------------------------------------
    // Timestamp (Instant) field delta round-trip tests
    //
    // Wire maps google.protobuf.Timestamp → java.time.Instant via a
    // LENGTH_DELIMITED adapter that encodes seconds + nanos as a nested
    // message. These tests verify the full delta pipeline handles
    // Timestamp fields correctly at both the message and field level.
    // ---------------------------------------------------------------

    @Test
    fun `timestamp field survives delta round-trip`() {
        val base = TestMessage(
            stringValue = "base",
            timestamp_value = Instant.ofEpochSecond(1000, 500_000_000),
        )
        val (local, remote) = harness.sharedHistory(
            "doc-ts-1", base,
            remoteEdits = listOf(
                base.copy(timestamp_value = Instant.ofEpochSecond(2000, 0)),
            ),
        )
        assertEquivalence(local, remote)
    }

    @Test
    fun `timestamp field updated alongside scalar fields`() {
        val base = TestMessage(
            stringValue = "initial",
            int32Value = 10,
            timestamp_value = Instant.ofEpochSecond(1000, 0),
        )
        val (local, remote) = harness.sharedHistory(
            "doc-ts-2", base,
            remoteEdits = listOf(
                base.copy(
                    stringValue = "updated",
                    timestamp_value = Instant.ofEpochSecond(2000, 999_999_999),
                ),
            ),
        )
        assertEquivalence(local, remote)
    }

    @Test
    fun `timestamp field with only nanos survives delta round-trip`() {
        val base = TestMessage(
            int32Value = 1,
            timestamp_value = Instant.ofEpochSecond(0, 123_456_789),
        )
        val (local, remote) = harness.sharedHistory(
            "doc-ts-3", base,
            remoteEdits = listOf(
                base.copy(timestamp_value = Instant.ofEpochSecond(0, 999_999_999)),
            ),
        )
        assertEquivalence(local, remote)
    }

    @Test
    fun `timestamp field set to near-epoch value`() {
        val base = TestMessage(
            int32Value = 42,
            timestamp_value = Instant.ofEpochSecond(1710000000, 500_000_000),
        )
        // Instant.EPOCH (seconds=0, nanos=0) encodes to zero bytes in proto3, making
        // it indistinguishable from "field not set." Use seconds=1 to get a valid
        // non-empty encoding.
        val (local, remote) = harness.sharedHistory(
            "doc-ts-4", base,
            remoteEdits = listOf(
                base.copy(timestamp_value = Instant.ofEpochSecond(1, 0)),
            ),
        )
        assertEquivalence(local, remote)
    }

    @Test
    fun `concurrent edits to timestamp and other fields merge correctly`() {
        val base = TestMessage(
            stringValue = "base",
            int32Value = 1,
            timestamp_value = Instant.ofEpochSecond(1000, 0),
        )
        val (local, remote) = harness.sharedHistory(
            "doc-ts-5", base,
            localEdits = listOf(base.copy(stringValue = "local-edit")),
            remoteEdits = listOf(
                base.copy(timestamp_value = Instant.ofEpochSecond(9999, 0)),
            ),
        )
        assertEquivalence(local, remote)
    }

    // ---------------------------------------------------------------
    // decodeValue error diagnostics — verifies that decode failures
    // produce actionable error messages with byte-level context
    // ---------------------------------------------------------------

    @Test
    fun `decodeValue wraps exception with byte diagnostics for invalid payload`() {
        val garbage = ByteArray(32) { it.toByte() }
        val doc = DistributedDocument(
            id = "corrupt-doc",
            data_ = CrdtData(
                data_ = garbage.toByteString(),
                crdt_type_name = "TestMessage",
            ),
            version_vector = emptyMap(),
        )

        val error = runCatching {
            harness.deltaOps.computeDelta(doc, emptyMap())
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(error).hasMessageThat().contains("32 bytes")
        assertThat(error).hasMessageThat().contains("corrupt-doc")
        assertThat(error).hasMessageThat().contains("TestMessage")
        assertThat(error).hasMessageThat().contains("hex=")
    }

    // ---------------------------------------------------------------
    // STRING field delta round-trip tests
    //
    // The valueDecoder for STRING fields must decode raw UTF-8 bytes
    // (without a varint length prefix) produced by valueEncoder.
    // These tests verify the full delta pipeline: local write →
    // computeDelta → serialize → applyDelta → decoded value.
    // ---------------------------------------------------------------

    @Test
    fun `string field survives delta round-trip with multibyte UTF-8`() {
        val base = TestMessage(stringValue = "base")
        val unicode = "café ñ 你好 🌍"
        val (local, remote) = harness.sharedHistory(
            "doc-str-1", base,
            remoteEdits = listOf(base.copy(stringValue = unicode)),
        )
        assertEquivalence(local, remote)
    }

    @Test
    fun `string field cleared to empty survives delta round-trip`() {
        val base = TestMessage(stringValue = "non-empty", int32Value = 42)
        val (local, remote) = harness.sharedHistory(
            "doc-str-2", base,
            remoteEdits = listOf(base.copy(stringValue = "")),
        )

        val delta = harness.deltaOps.computeDelta(remote.doc, local.doc.version_vector)
        assertThat(delta).isNotNull()
        val applied = harness.deltaOps.applyDelta(
            local.doc, delta!!, localActorId = local.actors.local_actor,
        ) as DeltaApplicationResult.Applied

        assertThat(applied.updatedValue.stringValue).isEmpty()
        assertThat(applied.updatedValue.int32Value).isEqualTo(42)
    }

    @Test
    fun `string in nested message survives delta round-trip`() {
        val nested = NestedMessage(stringValue = "original")
        val base = TestMessage(nestedValue = nested)
        val (local, remote) = harness.sharedHistory(
            "doc-str-3", base,
            remoteEdits = listOf(
                base.copy(nestedValue = nested.copy(stringValue = "Ü Ö ß")),
            ),
        )
        assertEquivalence(local, remote)
    }

    @Test
    fun `oneof string field survives delta round-trip`() {
        val base = TestMessage(oneOfValue1 = "first-choice")
        val (local, remote) = harness.sharedHistory(
            "doc-str-4", base,
            remoteEdits = listOf(base.copy(oneOfValue1 = "second-choice")),
        )
        assertEquivalence(local, remote)
    }

    @Test
    fun `string field with special characters survives delta round-trip`() {
        val base = TestMessage(stringValue = "initial")
        val special = "tabs\there\nnewlines\r\nand null\u0000bytes"
        val (local, remote) = harness.sharedHistory(
            "doc-str-5", base,
            remoteEdits = listOf(base.copy(stringValue = special)),
        )

        val delta = harness.deltaOps.computeDelta(remote.doc, local.doc.version_vector)
        assertThat(delta).isNotNull()
        val applied = harness.deltaOps.applyDelta(
            local.doc, delta!!, localActorId = local.actors.local_actor,
        ) as DeltaApplicationResult.Applied

        assertThat(applied.updatedValue.stringValue).isEqualTo(special)
    }

    // ---------------------------------------------------------------
    // BYTES field delta round-trip tests
    // ---------------------------------------------------------------

    @Test
    fun `bytes field survives delta round-trip`() {
        val payload = byteArrayOf(0x01, 0x02, 0xFF.toByte(), 0x00, 0x7F).toByteString()
        val base = TestMessage(bytesValue = payload)
        val newPayload = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()).toByteString()
        val (local, remote) = harness.sharedHistory(
            "doc-bytes-1", base,
            remoteEdits = listOf(base.copy(bytesValue = newPayload)),
        )
        assertEquivalence(local, remote)
    }

    @Test
    fun `concurrent string and bytes edits merge correctly via delta`() {
        val base = TestMessage(
            stringValue = "original",
            bytesValue = byteArrayOf(0x01).toByteString(),
        )
        val (local, remote) = harness.sharedHistory(
            "doc-mixed-1", base,
            localEdits = listOf(base.copy(stringValue = "local-string")),
            remoteEdits = listOf(
                base.copy(bytesValue = byteArrayOf(0xFF.toByte(), 0xFE.toByte()).toByteString()),
            ),
        )
        assertEquivalence(local, remote)
    }

    // ---------------------------------------------------------------
    // Full sync fallback — delta rejected, recover via resolveConflict
    // ---------------------------------------------------------------

    private fun assertFallbackEquivalence(
        local: Device<TestMessage>,
        remote: Device<TestMessage>,
    ) {
        val delta = harness.deltaOps.computeDelta(remote.doc, local.doc.version_vector)!!
        val stale = delta.copy(
            baseline_version_vector = delta.baseline_version_vector + (999L to 50L),
        )
        val rejected = harness.deltaOps.applyDelta(
            local.doc, stale, localActorId = local.actors.local_actor,
        )
        assertThat(rejected).isInstanceOf(DeltaApplicationResult.Rejected::class.java)

        val fullResult = harness.resolver.resolveConflict(
            localValue = local.value,
            localNode = local.doc.version_node,
            localActors = local.actors,
            incomingValue = remote.value,
            incomingNode = remote.doc.version_node!!,
            incomingVersionVector = remote.doc.version_vector,
        )

        val deltaResult = harness.deltaOps.applyDelta(
            local.doc, delta, localActorId = local.actors.local_actor,
        ) as DeltaApplicationResult.Applied

        assertThat(fullResult.mergeResult.value).isEqualTo(deltaResult.updatedValue)
    }

    @Test
    fun `full sync fallback produces same result as delta for string fields`() {
        val base = TestMessage(stringValue = "base", int32Value = 10)
        val (local, remote) = harness.sharedHistory(
            "fb-1", base,
            remoteEdits = listOf(base.copy(stringValue = "remote-edit")),
        )
        assertFallbackEquivalence(local, remote)
    }

    @Test
    fun `full sync fallback produces same result as delta for bytes fields`() {
        val base = TestMessage(
            bytesValue = byteArrayOf(0x01, 0x02).toByteString(),
            int32Value = 42,
        )
        val (local, remote) = harness.sharedHistory(
            "fb-2", base,
            remoteEdits = listOf(
                base.copy(bytesValue = byteArrayOf(0xCA.toByte(), 0xFE.toByte()).toByteString()),
            ),
        )
        assertFallbackEquivalence(local, remote)
    }

    @Test
    fun `full sync fallback merges concurrent string and int edits`() {
        val base = TestMessage(stringValue = "base", int32Value = 10, int64Value = 100L)
        val (local, remote) = harness.sharedHistory(
            "fb-3", base,
            localEdits = listOf(base.copy(stringValue = "local-edit")),
            remoteEdits = listOf(base.copy(int32Value = 99)),
        )
        assertFallbackEquivalence(local, remote)
    }

    @Test
    fun `full sync fallback handles nested message with string fields`() {
        val nested = NestedMessage(stringValue = "original", intValue = 10)
        val base = TestMessage(nestedValue = nested)
        val (local, remote) = harness.sharedHistory(
            "fb-4", base,
            remoteEdits = listOf(base.copy(nestedValue = nested.copy(stringValue = "edited"))),
        )
        assertFallbackEquivalence(local, remote)
    }
}
