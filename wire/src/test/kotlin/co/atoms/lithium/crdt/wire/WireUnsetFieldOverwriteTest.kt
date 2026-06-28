package co.atoms.lithium.crdt.wire

import co.atoms.lithium.crdt.test.TestMessage
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

/**
 * Reproduces data loss where an unset field on one device overwrites a populated
 * field on another during merge.
 *
 * A plain proto3 scalar that was never set has no explicit version node, so it
 * inherits the parent message version. When the device that never touched the
 * field happens to have a higher parent version (e.g. it was created later, or
 * its parent version was inflated by a prior merge), the inherited version wins
 * last-write-wins and the empty value clobbers the real value on the other side.
 */
class WireUnsetFieldOverwriteTest {
    private val adapter = WireCrdtResolverProvider().messageResolver(adapter = TestMessage.ADAPTER)

    @Test
    fun `unset field does not overwrite populated field when parent version is higher`() {
        // Device A: created earlier, sets stringValue (field 13).
        val deviceA = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage(stringValue = "hello"),
            timestamp = 1,
        )

        // Device B: created later (higher parent version), only sets int32Value (field 3).
        // stringValue is never set on B, so it inherits B's higher parent version.
        val deviceB = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage(int32Value = 42),
            timestamp = 5,
        )

        val merged = adapter.resolveConflict(lhs = deviceA, rhs = deviceB).mergeResult

        // int32Value was genuinely set on B and should be present.
        assertThat(merged.value?.int32Value).isEqualTo(42)
        // stringValue was never set on B, so A's real write must survive.
        assertThat(merged.value?.stringValue).isEqualTo("hello")
    }

    @Test
    fun `unset field does not overwrite populated field regardless of merge order`() {
        val deviceA = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage(stringValue = "hello"),
            timestamp = 1,
        )
        val deviceB = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage(int32Value = 42),
            timestamp = 5,
        )

        // Swap local/incoming: B (higher parent version) as local, A as incoming.
        val merged = adapter.resolveConflict(lhs = deviceB, rhs = deviceA).mergeResult

        assertThat(merged.value?.int32Value).isEqualTo(42)
        assertThat(merged.value?.stringValue).isEqualTo("hello")
    }
}
