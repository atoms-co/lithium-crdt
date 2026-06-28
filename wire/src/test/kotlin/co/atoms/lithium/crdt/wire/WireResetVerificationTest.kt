package co.atoms.lithium.crdt.wire

import co.atoms.lithium.crdt.test.TestMessage
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

/**
 * Verification scratch tests: confirm that resetting a field to its default is
 * already tracked as a real versioned write (and survives a merge), versus the
 * never-set case which is the actual bug.
 */
class WireResetVerificationTest {
    private val adapter = WireCrdtResolverProvider().messageResolver(adapter = TestMessage.ADAPTER)

    @Test
    fun `reset to default is a real write and survives the collapse optimization`() {
        // Base: two fields set so the parent stays low.
        val base = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage(stringValue = "hello", int32Value = 1),
            timestamp = 100,
        )

        // Reset stringValue (field 13) back to default.
        val reset = adapter.applyLocalWrite(
            currentValue = base.mergeResult.value,
            currentNode = base.mergeResult.node,
            currentActors = base.actors,
            newValue = TestMessage(stringValue = "", int32Value = 1),
            timestamp = 300,
        )

        // The reset must leave an explicit node for field 13 (not collapsed away).
        val field13 = reset.mergeResult.node?.struct?.fields?.get(13)
        assertThat(field13).isNotNull()
        assertThat(field13?.version?.timestamp).isEqualTo(300)
    }

    @Test
    fun `reset wins over an older value on merge`() {
        val base = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage(stringValue = "hello", int32Value = 1),
            timestamp = 100,
        )

        // Device A resets stringValue at a later time.
        val deviceA = adapter.applyLocalWrite(
            currentValue = base.mergeResult.value,
            currentNode = base.mergeResult.node,
            currentActors = base.actors,
            newValue = TestMessage(stringValue = "", int32Value = 1),
            timestamp = 300,
        )

        // Device B keeps the original "hello" (base, untouched).
        // Merge: A's reset (newer write) should win -> stringValue cleared.
        val merged = adapter.resolveConflict(lhs = base, rhs = deviceA).mergeResult
        assertThat(merged.value?.stringValue).isEqualTo("")

        // And order-independent.
        val mergedSwapped = adapter.resolveConflict(lhs = deviceA, rhs = base).mergeResult
        assertThat(mergedSwapped.value?.stringValue).isEqualTo("")
    }
}
