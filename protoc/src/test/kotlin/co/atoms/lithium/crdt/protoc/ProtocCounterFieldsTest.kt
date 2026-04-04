package co.atoms.lithium.crdt.protoc

import co.atoms.lithium.crdt.test.TestMessage
import co.atoms.lithium.crdt.data.Version
import co.atoms.lithium.crdt.resolver.version.ResolutionStrategy.MERGED_VALUES
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for Counter CRDT fields in TestMessage (protoc version).
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
class ProtocCounterFieldsTest {
    private val provider = CrdtMessageResolverProvider()
    private val resolver = provider.getOrCreateResolverFor(TestMessage.getDefaultInstance())

    // ========== Field 31: counterValue (int64) ==========

    @Test
    fun `counterValue - single actor local write creates simple version node`() {
        // Given - first write with counter value 10
        val message1 = TestMessage.newBuilder()
            .setCounterValue(10L)
            .build()

        // When - apply local write
        val delta1 = resolver.applyLocalWrite(
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
        assertThat(result1.node?.version).isEqualTo(Version.newBuilder()
            .setTimestamp(1000L)
            .setActorId(delta1.actors.localActor)
            .setActorVersion(1L)
            .build())
    }

    @Test
    fun `counterValue - increment updates counter value`() {
        // Given - existing counter value 10
        val message1 = TestMessage.newBuilder()
            .setCounterValue(10L)
            .build()
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = message1,
            timestamp = 1000L,
        )

        // When - increment to 25
        val message2 = TestMessage.newBuilder()
            .setCounterValue(25L)
            .build()
        val delta2 = resolver.applyLocalWrite(
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
        assertThat(result2.node?.struct?.fieldsMap?.get(31)?.version)
            .isEqualTo(Version.newBuilder()
                .setTimestamp(2000L)
                .setActorId(delta2.actors.localActor)
                .setActorVersion(2L)
                .build())
    }

    @Test
    fun `counterValue - null defaults to 0`() {
        // Given - message with no counter value set (defaults to 0)
        val message1 = TestMessage.newBuilder().build()

        // When - apply local write
        val delta1 = resolver.applyLocalWrite(
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
        val message1 = TestMessage.newBuilder()
            .setCounterValue(10L)
            .build()
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = message1,
            timestamp = 1000L,
        )

        // And - actor 2 writes counter value 15 (independent write)
        val message2 = TestMessage.newBuilder()
            .setCounterValue(15L)
            .build()
        val delta2 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = message2,
            timestamp = 1100L,
        )

        // When - merge actor 1 and actor 2
        val delta3 = resolver.resolveConflict(
            localValue = delta1.mergeResult.value,
            localNode = delta1.mergeResult.node,
            localActors = delta1.actors,
            incomingValue = delta2.mergeResult.value,
            incomingNode = requireNotNull(delta2.mergeResult.node),
            incomingVersionVector = delta2.actors.versionVectorMap,
        )
        val result3 = delta3.mergeResult

        // Then - counter values are summed (not replaced)
        assertThat(result3.resolution).isEqualTo(MERGED_VALUES)
        assertThat(result3.value?.counterValue).isEqualTo(25L) // 10 + 15
        // Verify node counter sum matches value
        with(ProtocVersionTreeResolver) {
            assertThat(result3.node?.struct?.fieldsMap?.get(31)?.counterValue).isEqualTo(25L)
        }
    }

    @Test
    fun `counterValue - three consecutive writes then merge preserves accumulated value`() {
        // Write 1: Create message with counter=10
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage.newBuilder().setCounterValue(10L).build(),
            timestamp = 1000L,
        )

        // Write 2: Increment counter to 25
        val delta2 = resolver.applyLocalWrite(
            currentValue = delta1.mergeResult.value,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = TestMessage.newBuilder().setCounterValue(25L).build(),
            timestamp = 2000L,
        )

        // Write 3: Increment counter to 40 (triggers plus() on simple node)
        val delta3 = resolver.applyLocalWrite(
            currentValue = delta2.mergeResult.value,
            currentNode = delta2.mergeResult.node,
            currentActors = delta2.actors,
            newValue = TestMessage.newBuilder().setCounterValue(40L).build(),
            timestamp = 3000L,
        )

        assertThat(delta3.mergeResult.value?.counterValue).isEqualTo(40L)

        // Merge with another actor who independently wrote counter=20
        val otherDelta = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage.newBuilder().setCounterValue(20L).build(),
            timestamp = 1500L,
        )

        val merged = resolver.resolveConflict(
            localValue = delta3.mergeResult.value,
            localNode = delta3.mergeResult.node,
            localActors = delta3.actors,
            incomingValue = otherDelta.mergeResult.value,
            incomingNode = requireNotNull(otherDelta.mergeResult.node),
            incomingVersionVector = otherDelta.actors.versionVectorMap,
        )

        // Should be 40 + 20 = 60
        assertThat(merged.mergeResult.value?.counterValue).isEqualTo(60L)
        with(ProtocVersionTreeResolver) {
            assertThat(merged.mergeResult.node?.struct?.fieldsMap?.get(31)?.counterValue)
                .isEqualTo(60L)
        }
    }

    @Test
    fun `counterValue - interleaved field updates preserve counter accumulation`() {
        // Write 1: Create message with counter=5 and a string field
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage.newBuilder()
                .setCounterValue(5L)
                .setStringValue("hello")
                .build(),
            timestamp = 1000L,
        )

        // Write 2: Update only counter to 15
        val delta2 = resolver.applyLocalWrite(
            currentValue = delta1.mergeResult.value,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = TestMessage.newBuilder()
                .setCounterValue(15L)
                .setStringValue("hello")
                .build(),
            timestamp = 2000L,
        )

        // Write 3: Update only counter to 30
        val delta3 = resolver.applyLocalWrite(
            currentValue = delta2.mergeResult.value,
            currentNode = delta2.mergeResult.node,
            currentActors = delta2.actors,
            newValue = TestMessage.newBuilder()
                .setCounterValue(30L)
                .setStringValue("hello")
                .build(),
            timestamp = 3000L,
        )

        // Write 4: Update only the string field (counter unchanged)
        val delta4 = resolver.applyLocalWrite(
            currentValue = delta3.mergeResult.value,
            currentNode = delta3.mergeResult.node,
            currentActors = delta3.actors,
            newValue = TestMessage.newBuilder()
                .setCounterValue(30L)
                .setStringValue("world")
                .build(),
            timestamp = 4000L,
        )

        // Merge with another actor who wrote counter=10
        val otherDelta = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage.newBuilder().setCounterValue(10L).build(),
            timestamp = 1500L,
        )

        val merged = resolver.resolveConflict(
            localValue = delta4.mergeResult.value,
            localNode = delta4.mergeResult.node,
            localActors = delta4.actors,
            incomingValue = otherDelta.mergeResult.value,
            incomingNode = requireNotNull(otherDelta.mergeResult.node),
            incomingVersionVector = otherDelta.actors.versionVectorMap,
        )

        // Should be 30 + 10 = 40
        assertThat(merged.mergeResult.value?.counterValue).isEqualTo(40L)
    }

    @Test
    fun `counterValue - writes after merge preserve counter structure`() {
        // Actor A: counter=10
        val deltaA = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage.newBuilder().setCounterValue(10L).build(),
            timestamp = 1000L,
        )

        // Actor B: counter=20
        val deltaB = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage.newBuilder().setCounterValue(20L).build(),
            timestamp = 1100L,
        )

        // Merge A+B = 30
        val merged = resolver.resolveConflict(
            localValue = deltaA.mergeResult.value,
            localNode = deltaA.mergeResult.node,
            localActors = deltaA.actors,
            incomingValue = deltaB.mergeResult.value,
            incomingNode = requireNotNull(deltaB.mergeResult.node),
            incomingVersionVector = deltaB.actors.versionVectorMap,
        )
        assertThat(merged.mergeResult.value?.counterValue).isEqualTo(30L)

        // Actor A continues from merged state: counter=45
        val deltaA2 = resolver.applyLocalWrite(
            currentValue = merged.mergeResult.value,
            currentNode = merged.mergeResult.node,
            currentActors = merged.actors,
            newValue = TestMessage.newBuilder().setCounterValue(45L).build(),
            timestamp = 2000L,
        )

        // Actor A increments again: counter=55
        val deltaA3 = resolver.applyLocalWrite(
            currentValue = deltaA2.mergeResult.value,
            currentNode = deltaA2.mergeResult.node,
            currentActors = deltaA2.actors,
            newValue = TestMessage.newBuilder().setCounterValue(55L).build(),
            timestamp = 3000L,
        )

        assertThat(deltaA3.mergeResult.value?.counterValue).isEqualTo(55L)

        // Merge with Actor C: counter=5
        val deltaC = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage.newBuilder().setCounterValue(5L).build(),
            timestamp = 1200L,
        )

        val finalMerge = resolver.resolveConflict(
            localValue = deltaA3.mergeResult.value,
            localNode = deltaA3.mergeResult.node,
            localActors = deltaA3.actors,
            incomingValue = deltaC.mergeResult.value,
            incomingNode = requireNotNull(deltaC.mergeResult.node),
            incomingVersionVector = deltaC.actors.versionVectorMap,
        )

        // Should be 55 + 5 = 60
        assertThat(finalMerge.mergeResult.value?.counterValue).isEqualTo(60L)
        with(ProtocVersionTreeResolver) {
            assertThat(finalMerge.mergeResult.node?.struct?.fieldsMap?.get(31)?.counterValue)
                .isEqualTo(60L)
        }
    }

    @Test
    fun `counterValue - three actors merge correctly`() {
        // Given - three actors with different counter values
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage.newBuilder().setCounterValue(10L).build(),
            timestamp = 1000L,
        )

        val delta2 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage.newBuilder().setCounterValue(20L).build(),
            timestamp = 1100L,
        )

        val delta3 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage.newBuilder().setCounterValue(30L).build(),
            timestamp = 1200L,
        )

        // When - merge all three actors
        val merged12 = resolver.resolveConflict(
            localValue = delta1.mergeResult.value,
            localNode = delta1.mergeResult.node,
            localActors = delta1.actors,
            incomingValue = delta2.mergeResult.value,
            incomingNode = requireNotNull(delta2.mergeResult.node),
            incomingVersionVector = delta2.actors.versionVectorMap,
        )
        val mergedAll = resolver.resolveConflict(
            localValue = merged12.mergeResult.value,
            localNode = merged12.mergeResult.node,
            localActors = merged12.actors,
            incomingValue = delta3.mergeResult.value,
            incomingNode = requireNotNull(delta3.mergeResult.node),
            incomingVersionVector = delta3.actors.versionVectorMap,
        )
        val result = mergedAll.mergeResult

        // Then - sums all three actor contributions
        assertThat(result.value?.counterValue).isEqualTo(60L) // 10 + 20 + 30
        // Verify node counter sum matches value
        with(ProtocVersionTreeResolver) {
            assertThat(result.node?.struct?.fieldsMap?.get(31)?.counterValue).isEqualTo(60L)
        }
    }

    // ========== Field 34: counterMapValue (map<string, int32>) ==========

    @Test
    fun `counterMapValue - single actor creates map entries`() {
        // Given - map with counter values
        val message1 = TestMessage.newBuilder()
            .putCounterMapValue("a", 10)
            .putCounterMapValue("b", 20)
            .build()

        // When - apply local write
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = message1,
            timestamp = 1000L,
        )
        val result1 = delta1.mergeResult

        // Then - map entries created
        assertThat(result1.resolution).isTrue()
        assertThat(result1.value?.counterMapValueMap).isEqualTo(mapOf("a" to 10, "b" to 20))
    }

    @Test
    fun `counterMapValue - single actor updates map`() {
        // Given - existing map
        val message1 = TestMessage.newBuilder()
            .putCounterMapValue("a", 10)
            .build()
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = message1,
            timestamp = 1000L,
        )

        // When - same actor updates map
        val message2 = TestMessage.newBuilder()
            .putCounterMapValue("a", 25)
            .putCounterMapValue("b", 15)
            .build()
        val delta2 = resolver.applyLocalWrite(
            currentValue = delta1.mergeResult.value,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = message2,
            timestamp = 2000L,
        )
        val result2 = delta2.mergeResult

        // Then - values updated (not summed for same actor)
        assertThat(result2.resolution).isTrue()
        assertThat(result2.value?.counterMapValueMap).isEqualTo(mapOf("a" to 25, "b" to 15))
    }

    @Test
    fun `counterMapValue - multi-actor merge sums values per key`() {
        // Given - actor 1 writes counters for keys "a" and "b"
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage.newBuilder()
                .putCounterMapValue("a", 10)
                .putCounterMapValue("b", 20)
                .build(),
            timestamp = 1000L,
        )

        // And - actor 2 writes counters for keys "b" and "c"
        val delta2 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage.newBuilder()
                .putCounterMapValue("b", 15)
                .putCounterMapValue("c", 30)
                .build(),
            timestamp = 1100L,
        )

        // When - merge both actors
        val delta3 = resolver.resolveConflict(
            localValue = delta1.mergeResult.value,
            localNode = delta1.mergeResult.node,
            localActors = delta1.actors,
            incomingValue = delta2.mergeResult.value,
            incomingNode = requireNotNull(delta2.mergeResult.node),
            incomingVersionVector = delta2.actors.versionVectorMap,
        )
        val result3 = delta3.mergeResult

        // Then - values sum for overlapping keys
        assertThat(result3.resolution).isEqualTo(MERGED_VALUES)
        assertThat(result3.value?.counterMapValueMap).isEqualTo(
            mapOf(
                "a" to 10, // Only actor 1
                "b" to 35, // 20 + 15
                "c" to 30, // Only actor 2
            )
        )
        // Verify node counter sums match values (only key "b" has multi-actor counter)
        with(ProtocVersionTreeResolver) {
            val field34Node = result3.node?.struct?.fieldsMap?.get(34)
            // Access the protobuf stringMap.entriesMap directly to get Map<String, VersionNode>
            val stringMapEntries = field34Node?.stringMap?.entriesMap ?: emptyMap()
            // Only key "b" has counter structure (multi-actor: 20 + 15 = 35)
            // Keys "a" and "c" are single-actor so they use the optimization (no counter)
            assertThat(stringMapEntries["b"]?.counterValue).isEqualTo(35L)
        }
    }

    @Test
    fun `counterMapValue - empty map`() {
        // Given - empty map
        val message1 = TestMessage.newBuilder().build()

        // When - apply local write
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = message1,
            timestamp = 1000L,
        )
        val result1 = delta1.mergeResult

        // Then - empty map created
        assertThat(result1.value?.counterMapValueMap).isEmpty()
    }

    // ========== Field 35: counterListValue (repeated int32) ==========

    @Test
    fun `counterListValue - single actor creates list entries`() {
        // Given - list with counter values
        val message1 = TestMessage.newBuilder()
            .addCounterListValue(10)
            .addCounterListValue(20)
            .addCounterListValue(30)
            .build()

        // When - apply local write
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = message1,
            timestamp = 1000L,
        )
        val result1 = delta1.mergeResult

        // Then - list entries created
        assertThat(result1.resolution).isTrue()
        assertThat(result1.value?.counterListValueList).isEqualTo(listOf(10, 20, 30))
    }

    @Test
    fun `counterListValue - single actor updates list`() {
        // Given - existing list
        val message1 = TestMessage.newBuilder()
            .addCounterListValue(10)
            .addCounterListValue(20)
            .build()
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = message1,
            timestamp = 1000L,
        )

        // When - same actor updates list
        val message2 = TestMessage.newBuilder()
            .addCounterListValue(25)
            .addCounterListValue(35)
            .addCounterListValue(40)
            .build()
        val delta2 = resolver.applyLocalWrite(
            currentValue = delta1.mergeResult.value,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = message2,
            timestamp = 2000L,
        )
        val result2 = delta2.mergeResult

        // Then - list updated (not summed for same actor)
        assertThat(result2.resolution).isTrue()
        assertThat(result2.value?.counterListValueList).isEqualTo(listOf(25, 35, 40))
    }

    @Test
    fun `counterListValue - multi-actor merge sums values per index`() {
        // Given - actor 1 writes list [10, 20, 30]
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage.newBuilder()
                .addCounterListValue(10)
                .addCounterListValue(20)
                .addCounterListValue(30)
                .build(),
            timestamp = 1000L,
        )

        // And - actor 2 writes list [5, 15, 25]
        val delta2 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage.newBuilder()
                .addCounterListValue(5)
                .addCounterListValue(15)
                .addCounterListValue(25)
                .build(),
            timestamp = 1100L,
        )

        // When - merge both actors
        val delta3 = resolver.resolveConflict(
            localValue = delta1.mergeResult.value,
            localNode = delta1.mergeResult.node,
            localActors = delta1.actors,
            incomingValue = delta2.mergeResult.value,
            incomingNode = requireNotNull(delta2.mergeResult.node),
            incomingVersionVector = delta2.actors.versionVectorMap,
        )
        val result3 = delta3.mergeResult

        // Then - values sum per index
        assertThat(result3.resolution).isEqualTo(MERGED_VALUES)
        assertThat(result3.value?.counterListValueList).isEqualTo(
            listOf(15, 35, 55) // [10+5, 20+15, 30+25]
        )
        // Verify node counter sums match values
        with(ProtocVersionTreeResolver) {
            val repeatedEntries = result3.node?.struct?.fieldsMap?.get(35)?.entries ?: emptyList()
            assertThat(repeatedEntries.size).isEqualTo(3)
            assertThat(repeatedEntries[0].counterValue).isEqualTo(15L)
            assertThat(repeatedEntries[1].counterValue).isEqualTo(35L)
            assertThat(repeatedEntries[2].counterValue).isEqualTo(55L)
        }
    }

    @Test
    fun `counterListValue - different list sizes use higher version for size`() {
        // Given - actor 1 writes list [10, 20, 30]
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage.newBuilder()
                .addCounterListValue(10)
                .addCounterListValue(20)
                .addCounterListValue(30)
                .build(),
            timestamp = 1000L,
        )

        // And - actor 2 writes list [5, 15] (shorter, but higher timestamp)
        val delta2 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = TestMessage.newBuilder()
                .addCounterListValue(5)
                .addCounterListValue(15)
                .build(),
            timestamp = 2000L,
        )

        // When - merge both actors
        val delta3 = resolver.resolveConflict(
            localValue = delta1.mergeResult.value,
            localNode = delta1.mergeResult.node,
            localActors = delta1.actors,
            incomingValue = delta2.mergeResult.value,
            incomingNode = requireNotNull(delta2.mergeResult.node),
            incomingVersionVector = delta2.actors.versionVectorMap,
        )
        val result3 = delta3.mergeResult

        // Then - higher timestamp wins for size, within that size counters sum
        assertThat(result3.value?.counterListValueList).isEqualTo(
            listOf(15, 35) // [10+5, 20+15], third element discarded
        )
        // Verify node counter sums match values
        with(ProtocVersionTreeResolver) {
            val repeatedEntries = result3.node?.struct?.fieldsMap?.get(35)?.entries ?: emptyList()
            assertThat(repeatedEntries.size).isEqualTo(2)
            assertThat(repeatedEntries[0].counterValue).isEqualTo(15L)
            assertThat(repeatedEntries[1].counterValue).isEqualTo(35L)
        }
    }

    @Test
    fun `counterListValue - empty list`() {
        // Given - empty list
        val message1 = TestMessage.newBuilder().build()

        // When - apply local write
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = message1,
            timestamp = 1000L,
        )
        val result1 = delta1.mergeResult

        // Then - empty list created
        assertThat(result1.value?.counterListValueList).isEmpty()
    }
}
