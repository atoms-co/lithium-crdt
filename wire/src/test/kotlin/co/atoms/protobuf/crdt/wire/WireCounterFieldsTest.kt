package co.atoms.protobuf.crdt.wire

import co.atoms.protobuf.crdt.test.TestMessage
import co.atoms.protobuf.crdt.data.Actors
import co.atoms.protobuf.crdt.data.PathComponent
import co.atoms.protobuf.crdt.data.Version
import co.atoms.protobuf.crdt.data.VersionNode
import co.atoms.protobuf.crdt.resolver.ResolverDeltaResult
import co.atoms.protobuf.crdt.resolver.version.ResolutionStrategy.MERGED_VALUES
import co.atoms.protobuf.crdt.wire.internal.WireVersionTreeResolver
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

/**
 * Tests for Counter CRDT fields in TestMessage.
 *
 * Verifies counter semantics for:
 * - Field 31: counterValue (int64 with COUNTER merge strategy)
 * - Field 34: counterMapValue (map<string, int32> with COUNTER merge strategy)
 * - Field 35: counterListValue (repeated int32 with COUNTER merge strategy)
 *
 * ## Counter Semantics
 * - Single-actor writes create simple version nodes (optimization)
 * - Multi-actor writes create counter structures with per-actor tracking
 * - Null values default to 0 (counters always have values)
 * - Counter values sum across actors (not last-write-wins)
 */
class WireCounterFieldsTest {
    private val adapter = WireCrdtResolverProvider().messageResolver(adapter = TestMessage.ADAPTER)

    // ========== Field 31: counterValue (int64) ==========

    @Test
    fun `counterValue - single actor local write creates simple version node`() {
        // Given - first write with counter value 10
        val message1 = TestMessage(counterValue = 10L)

        // When - apply local write
        val delta1 = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = message1,
            timestamp = 1000L,
        )
        val result1 = delta1.mergeResult

        // Then - creates simple version node (single-actor optimization)
        assertThat(result1.resolution).isTrue()
        assertThat(result1.value?.counterValue).isEqualTo(10L)
        // Counter field is optimized at message level, not as separate struct field
        assertThat(result1.node?.version).isEqualTo(Version(1000L, delta1.actors.local_actor, 1L))
    }

    @Test
    fun `counterValue - increment updates counter value`() {
        // Given - existing counter value 10
        val message1 = TestMessage(counterValue = 10L)
        val delta1 = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = message1,
            timestamp = 1000L,
        )

        // When - increment to 25
        val message2 = TestMessage(counterValue = 25L)
        val delta2 = adapter.applyLocalWrite(
            currentValue = delta1.mergeResult.value,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = message2,
            timestamp = 2000L,
        )
        val result2 = delta2.mergeResult

        // Then - counter updated to 25 (not summed, local write replaces)
        assertThat(result2.resolution).isTrue()
        assertThat(result2.value?.counterValue).isEqualTo(25L)
        assertThat(result2.node?.struct?.fields?.get(31)?.version)
            .isEqualTo(Version(2000L, delta2.actors.local_actor, 2L))
    }

    @Test
    fun `counterValue - null defaults to 0`() {
        // Given - message with no counter value set (defaults to 0)
        val message1 = TestMessage()

        // When - apply local write
        val delta1 = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = message1,
            timestamp = 1000L,
        )
        val result1 = delta1.mergeResult

        // Then - counter value is 0
        assertThat(result1.value?.counterValue).isEqualTo(0L)
    }

    @Test
    fun `counterValue - multi-actor merge sums counter values`() {
        // Given - actor 1 writes counter value 10
        val message1 = TestMessage(counterValue = 10L)
        val delta1 = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = message1,
            timestamp = 1000L,
        )

        // And - actor 2 writes counter value 15 (independent write)
        val message2 = TestMessage(counterValue = 15L)
        val delta2 = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = message2,
            timestamp = 1100L,
        )

        // When - merge actor 1 and actor 2
        val delta3 = adapter.resolveConflict(lhs = delta1, rhs = delta2)
        val result3 = delta3.mergeResult

        // Then - counter values are summed (not replaced)
        assertThat(result3.resolution).isEqualTo(MERGED_VALUES)
        assertThat(result3.value?.counterValue).isEqualTo(25L) // 10 + 15
        // Verify node counter sums match values
        with(WireVersionTreeResolver) {
            assertThat(result3.node?.struct?.fields?.get(31)?.counterValue).isEqualTo(25L)
        }
    }

    @Test
    fun `counterValue - three actors merge correctly`() {
        // Given - three actors with different counter values
        val delta1 = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage(counterValue = 10L),
            timestamp = 1000L,
        )

        val delta2 = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage(counterValue = 20L),
            timestamp = 1100L,
        )

        val delta3 = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage(counterValue = 30L),
            timestamp = 1200L,
        )

        // When - merge all three actors
        val merged12 = adapter.resolveConflict(lhs = delta1, rhs = delta2)
        val mergedAll = adapter.resolveConflict(lhs = merged12, rhs = delta3)
        val result = mergedAll.mergeResult

        // Then - sums all three actor contributions
        assertThat(result.value?.counterValue).isEqualTo(60L) // 10 + 20 + 30
        // Verify node counter sums match values
        with(WireVersionTreeResolver) {
            assertThat(result.node?.struct?.fields?.get(31)?.counterValue).isEqualTo(60L)
        }
    }

    // ========== Field 34: counterMapValue (map<string, int32>) ==========

    @Test
    fun `counterMapValue - single actor creates map entries`() {
        // Given - map with counter values
        val message1 = TestMessage(
            counterMapValue = mapOf(
                "a" to 10,
                "b" to 20,
            )
        )

        // When - apply local write
        val delta1 = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = message1,
            timestamp = 1000L,
        )
        val result1 = delta1.mergeResult

        // Then - map entries created
        assertThat(result1.resolution).isTrue()
        assertThat(result1.value?.counterMapValue).isEqualTo(mapOf("a" to 10, "b" to 20))
    }

    @Test
    fun `counterMapValue - single actor updates map`() {
        // Given - existing map
        val message1 = TestMessage(counterMapValue = mapOf("a" to 10))
        val delta1 = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = message1,
            timestamp = 1000L,
        )

        // When - same actor updates map
        val message2 = TestMessage(counterMapValue = mapOf("a" to 25, "b" to 15))
        val delta2 = adapter.applyLocalWrite(
            currentValue = delta1.mergeResult.value,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = message2,
            timestamp = 2000L,
        )
        val result2 = delta2.mergeResult

        // Then - values updated (not summed for same actor)
        assertThat(result2.resolution).isTrue()
        assertThat(result2.value?.counterMapValue).isEqualTo(mapOf("a" to 25, "b" to 15))
    }

    @Test
    fun `counterMapValue - multi-actor merge sums values per key`() {
        // Given - actor 1 writes counters for keys "a" and "b"
        val delta1 = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage(counterMapValue = mapOf("a" to 10, "b" to 20)),
            timestamp = 1000L,
        )

        // And - actor 2 writes counters for keys "b" and "c"
        val delta2 = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage(counterMapValue = mapOf("b" to 15, "c" to 30)),
            timestamp = 1100L,
        )

        // When - merge both actors
        val delta3 = adapter.resolveConflict(lhs = delta1, rhs = delta2)
        val result3 = delta3.mergeResult

        // Then - values sum for overlapping keys
        assertThat(result3.resolution).isEqualTo(MERGED_VALUES)
        assertThat(result3.value?.counterMapValue).isEqualTo(
            mapOf(
                "a" to 10, // Only actor 1
                "b" to 35, // 20 + 15
                "c" to 30, // Only actor 2
            )
        )
        // Verify node counter sums match values (only key "b" has multi-actor counter)
        with(WireVersionTreeResolver) {
            val stringMapEntries = result3.node?.struct?.fields?.get(34)?.stringMap ?: emptyMap()
            // Only key "b" has counter structure (multi-actor: 20 + 15 = 35)
            // Keys "a" and "c" are single-actor so they use the optimization (no counter)
            assertThat(stringMapEntries["b"]?.counterValue).isEqualTo(35L)
            assertThat(stringMapEntries["a"]?.counter).isNull()
            assertThat(stringMapEntries["a"]?.version?.timestamp).isEqualTo(1000L)
            assertThat(stringMapEntries["a"]?.version?.actor_version).isEqualTo(1)
            assertThat(stringMapEntries["c"]?.counter).isNull()
            assertThat(stringMapEntries["c"]?.version?.timestamp).isEqualTo(1100L)
            assertThat(stringMapEntries["c"]?.version?.actor_version).isEqualTo(1)
        }
    }

    @Test
    fun `counterMapValue - empty map`() {
        // Given - empty map
        val message1 = TestMessage(counterMapValue = emptyMap())

        // When - apply local write
        val delta1 = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = message1,
            timestamp = 1000L,
        )
        val result1 = delta1.mergeResult

        // Then - empty map created
        assertThat(result1.value?.counterMapValue).isEmpty()
    }

    // ========== Field 35: counterListValue (repeated int32) ==========

    @Test
    fun `counterListValue - single actor creates list entries`() {
        // Given - list with counter values
        val message1 = TestMessage(
            counterListValue = listOf(10, 20, 30)
        )

        // When - apply local write
        val delta1 = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = message1,
            timestamp = 1000L,
        )
        val result1 = delta1.mergeResult

        // Then - list entries created
        assertThat(result1.resolution).isTrue()
        assertThat(result1.value?.counterListValue).isEqualTo(listOf(10, 20, 30))
    }

    @Test
    fun `counterListValue - single actor updates list`() {
        // Given - existing list
        val message1 = TestMessage(counterListValue = listOf(10, 20))
        val delta1 = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = message1,
            timestamp = 1000L,
        )

        // When - same actor updates list
        val message2 = TestMessage(counterListValue = listOf(25, 35, 40))
        val delta2 = adapter.applyLocalWrite(
            currentValue = delta1.mergeResult.value,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = message2,
            timestamp = 2000L,
        )
        val result2 = delta2.mergeResult

        // Then - list updated (not summed for same actor)
        assertThat(result2.resolution).isTrue()
        assertThat(result2.value?.counterListValue).isEqualTo(listOf(25, 35, 40))
    }

    @Test
    fun `counterListValue - multi-actor merge sums values per index`() {
        // Given - actor 1 writes list [10, 20, 30]
        val delta1 = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage(counterListValue = listOf(10, 20, 30)),
            timestamp = 1000L,
        )

        // And - actor 2 writes list [5, 15, 25]
        val delta2 = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage(counterListValue = listOf(5, 15, 25)),
            timestamp = 1100L,
        )

        // When - merge both actors
        val delta3 = adapter.resolveConflict(lhs = delta1, rhs = delta2)
        val result3 = delta3.mergeResult

        // Then - values sum per index
        assertThat(result3.resolution).isEqualTo(MERGED_VALUES)
        assertThat(result3.value?.counterListValue).isEqualTo(
            listOf(15, 35, 55) // [10+5, 20+15, 30+25]
        )
        // Verify node counter sums match values
        with(WireVersionTreeResolver) {
            val repeatedEntries = result3.node?.struct?.fields?.get(35)?.entries ?: emptyList()
            assertThat(repeatedEntries.size).isEqualTo(3)
            assertThat(repeatedEntries[0].counterValue).isEqualTo(15L)
            assertThat(repeatedEntries[1].counterValue).isEqualTo(35L)
            assertThat(repeatedEntries[2].counterValue).isEqualTo(55L)
        }
    }

    @Test
    fun `counterListValue - different list sizes use higher version for size`() {
        // Given - actor 1 writes list [10, 20, 30]
        val delta1 = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage(counterListValue = listOf(10, 20, 30)),
            timestamp = 1000L,
        )

        // And - actor 2 writes list [5, 15] (shorter, but higher timestamp)
        val delta2 = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage(counterListValue = listOf(5, 15)),
            timestamp = 2000L,
        )

        // When - merge both actors
        val delta3 = adapter.resolveConflict(lhs = delta1, rhs = delta2)
        val result3 = delta3.mergeResult

        // Then - higher timestamp wins for size, within that size counters sum
        assertThat(result3.value?.counterListValue).isEqualTo(
            listOf(15, 35) // [10+5, 20+15], third element discarded
        )
        // Verify node counter sums match values
        with(WireVersionTreeResolver) {
            val repeatedEntries = result3.node?.struct?.fields?.get(35)?.entries ?: emptyList()
            assertThat(repeatedEntries.size).isEqualTo(2)
            assertThat(repeatedEntries[0].counterValue).isEqualTo(15L)
            assertThat(repeatedEntries[1].counterValue).isEqualTo(35L)
        }
    }

    @Test
    fun `counterListValue - empty list`() {
        // Given - empty list
        val message1 = TestMessage(counterListValue = emptyList())

        // When - apply local write
        val delta1 = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = message1,
            timestamp = 1000L,
        )
        val result1 = delta1.mergeResult

        // Then - empty list created
        assertThat(result1.value?.counterListValue).isEmpty()
    }
}
