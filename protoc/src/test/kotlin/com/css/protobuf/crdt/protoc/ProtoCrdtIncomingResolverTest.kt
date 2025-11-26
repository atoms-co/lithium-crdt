package com.css.protobuf.crdt.protoc

import com.css.protobuf.crdt.test.EnumSample
import com.css.protobuf.crdt.test.NestedMessage
import com.css.protobuf.crdt.test.NestedMessageWithId
import com.css.protobuf.crdt.test.TestMessage
import com.css.protobuf.crdt.data.Version
import com.css.protobuf.crdt.resolver.version.ResolutionStrategy
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for CrdtMessageIncomingResolver - resolveConflict operations.
 *
 * These tests focus on conflict resolution between local and incoming values,
 * handling version-based last-write-wins semantics.
 */
class ProtoCrdtIncomingResolverTest {
    private val provider = CrdtMessageResolverProvider()
    private val resolver = provider.getOrCreateResolverFor(TestMessage.getDefaultInstance())

    @Test
    fun resolver_mergeProtos_withPrimitiveTypesOnly_lastWriteWins() {
        val protoV0 = TestMessage.newBuilder()
            .setInt32Value(5)
            .setInt64Value(10)
            .setStringValue("USD")
            .setEnumValue(EnumSample.VALUE2)
            .putEnumMapValue("2", EnumSample.VALUE1)
            .build()

        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = protoV0,
            timestamp = 0
        )
        val result1 = delta1.mergeResult

        // Device A: Update multiple fields
        val protoV2 = protoV0.toBuilder()
            .setInt64Value(20)
            .setStringValue("XXX")
            .setOneOfValue1("1234")
            .build()

        val delta2 = resolver.applyLocalWrite(
            currentValue = result1.value,
            currentNode = result1.node,
            currentActors = delta1.actors,
            newValue = protoV2,
            timestamp = 1,
        )
        val result2 = delta2.mergeResult

        assertThat(result2.resolution).isTrue()
        assertThat(result2.value).isEqualTo(protoV2)

        // Device B: Update different fields
        val protoV3 = protoV0.toBuilder()
            .setStringValue("ARG")
            .setOneOfValue2(NestedMessage.newBuilder().setStringValue("1234").build())
            .build()

        val delta3 = resolver.applyLocalWrite(
            currentValue = result1.value,
            currentNode = result1.node,
            currentActors = null,
            newValue = protoV3,
            timestamp = 2,
        )
        val result3 = delta3.mergeResult

        // Resolve conflict - fields merge based on version vectors
        val conflictDelta = resolver.resolveConflict(
            localValue = result2.value,
            localNode = result2.node,
            localActors = delta2.actors,
            incomingValue = result3.value,
            incomingNode = result3.node!!,
            incomingVersionVector = delta3.actors.versionVectorMap,
        )
        val conflict = conflictDelta.mergeResult

        assertThat(conflict.resolution).isEqualTo(ResolutionStrategy.MERGED_VALUES)
        val merged = conflict.value
        assertThat(merged?.int32Value).isEqualTo(5) // unchanged
        assertThat(merged?.int64Value).isEqualTo(20) // from result2 (v2)
        assertThat(merged?.stringValue).isEqualTo("ARG") // from result3 (v3, higher version)
        assertThat(merged?.oneOfValue1).isEmpty() // oneOf cleared by result3
        assertThat(merged?.oneOfValue2)
            .isEqualTo(NestedMessage.newBuilder().setStringValue("1234").build()) // from result3

        // Test resolving with itself
        val conflict2Delta = resolver.resolveConflict(
            localValue = conflict.value,
            localNode = conflict.node,
            localActors = conflictDelta.actors,
            incomingValue = conflict.value,
            incomingNode = conflict.node!!,
            incomingVersionVector = conflictDelta.actors.versionVectorMap,
        )
        val conflict2 = conflict2Delta.mergeResult

        assertThat(conflict2.resolution).isEqualTo(ResolutionStrategy.NO_CHANGE)
    }

    @Test
    fun `repeated field with IDs - conflict resolution`() {
        val v1 = Version.newBuilder().setTimestamp(1L).setActorId(100L).build()
        val v2 = Version.newBuilder().setTimestamp(2L).setActorId(200L).build()
        val v3 = Version.newBuilder().setTimestamp(3L).setActorId(300L).build()

        // Initial state: 2 items with IDs
        val item1 = NestedMessageWithId.newBuilder()
            .setId("item1")
            .setStringValue("Product 1")
            .setIntValue(100)
            .build()
        val item2 = NestedMessageWithId.newBuilder()
            .setId("item2")
            .setStringValue("Product 2")
            .setIntValue(200)
            .build()

        val initial = TestMessage.newBuilder()
            .addNestedListWithIdValue(item1)
            .addNestedListWithIdValue(item2)
            .build()

        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1
        )
        val result1 = delta1.mergeResult

        // Device A: Modify item1 and add item3
        val modifiedItem1 = item1.toBuilder().setStringValue("Updated Product 1").build()
        val item3 = NestedMessageWithId.newBuilder()
            .setId("item3")
            .setStringValue("Product 3")
            .setIntValue(300)
            .build()

        val deviceA = TestMessage.newBuilder()
            .addNestedListWithIdValue(item3)
            .addNestedListWithIdValue(modifiedItem1)
            .addNestedListWithIdValue(item2)
            .build()

        val deltaA = resolver.applyLocalWrite(
            currentValue = result1.value,
            currentNode = result1.node,
            currentActors = delta1.actors,
            newValue = deviceA,
            timestamp = 2
        )
        val resultA = deltaA.mergeResult

        // Device B: Modify item2 and reorder (item2 first)
        val modifiedItem2 = item2.toBuilder().setIntValue(250).build()
        val deviceB = TestMessage.newBuilder()
            .addNestedListWithIdValue(modifiedItem2)
            .addNestedListWithIdValue(item1)
            .build()

        val deltaB = resolver.applyLocalWrite(
            currentValue = result1.value,
            currentNode = result1.node,
            currentActors = deltaA.actors,
            newValue = deviceB,
            timestamp = 3
        )
        val resultB = deltaB.mergeResult

        // Resolve conflict - element content merges
        val conflictDelta = resolver.resolveConflict(
            localValue = resultA.value,
            localNode = resultA.node,
            localActors = deltaA.actors,
            incomingValue = resultB.value,
            incomingNode = resultB.node!!,
            incomingVersionVector = deltaB.actors.versionVectorMap,
        )
        val conflict = conflictDelta.mergeResult

        assertThat(conflict.resolution).isEqualTo(ResolutionStrategy.MERGED_VALUES)
        assertThat(conflict.value?.nestedListWithIdValueList).hasSize(3)
        assertThat(conflict.value?.nestedListWithIdValueList)
            .containsExactly(
                item3,
                modifiedItem2.toBuilder().setStringValue("Product 2").build(),
                modifiedItem1
            )
    }

    @Test
    fun `optional fields - conflict resolution`() {
        val initial = TestMessage.newBuilder().setStringValue("base").build()

        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1
        )
        val result1 = delta1.mergeResult

        // Device A: Set primitive optional value
        val deviceA = initial.toBuilder().setPrimitiveOptionalValue(100).build()
        val deltaA = resolver.applyLocalWrite(
            currentValue = result1.value,
            currentNode = result1.node,
            currentActors = delta1.actors,
            newValue = deviceA,
            timestamp = 2
        )
        val resultA = deltaA.mergeResult

        // Device B: Set nested optional value
        val deviceB = initial.toBuilder()
            .setNestedOptionalValue(
                NestedMessage.newBuilder().setStringValue("device_b").setIntValue(200).build()
            )
            .build()
        val deltaB = resolver.applyLocalWrite(
            currentValue = result1.value,
            currentNode = result1.node,
            currentActors = null,
            newValue = deviceB,
            timestamp = 3
        )
        val resultB = deltaB.mergeResult

        // Resolve conflict - both optional values should be present
        val conflictDelta = resolver.resolveConflict(
            localValue = resultA.value,
            localNode = resultA.node,
            localActors = deltaA.actors,
            incomingValue = resultB.value,
            incomingNode = resultB.node!!,
            incomingVersionVector = deltaB.actors.versionVectorMap,
        )
        val conflict = conflictDelta.mergeResult

        assertThat(conflict.resolution).isEqualTo(ResolutionStrategy.MERGED_VALUES)
        assertThat(conflict.value?.primitiveOptionalValue).isEqualTo(100)
        assertThat(conflict.value?.nestedOptionalValue)
            .isEqualTo(NestedMessage.newBuilder().setStringValue("device_b").setIntValue(200).build())
    }

    @Test
    fun testConflictResolution_lastWriteWins() {
        val protoV0 = TestMessage.newBuilder()
            .setInt32Value(5)
            .setInt64Value(10)
            .setStringValue("USD")
            .setEnumValue(EnumSample.VALUE2)
            .putEnumMapValue("2", EnumSample.VALUE1)
            .build()

        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = protoV0,
            timestamp = 1
        )
        val result1 = delta1.mergeResult

        // Device A: Update multiple fields
        val protoV2 = protoV0.toBuilder()
            .setInt64Value(20)
            .setStringValue("XXX")
            .setOneOfValue1("1234")
            .build()

        val delta2 = resolver.applyLocalWrite(
            currentValue = result1.value,
            currentNode = result1.node,
            currentActors = delta1.actors,
            newValue = protoV2,
            timestamp = 2,
        )
        val result2 = delta2.mergeResult

        assertThat(result2.resolution).isTrue()
        assertThat(result2.value).isEqualTo(protoV2)

        // Device B: Update different fields
        val protoV3 = protoV0.toBuilder()
            .setStringValue("ARG")
            .setOneOfValue2(NestedMessage.newBuilder().setStringValue("1234").build())
            .build()

        val delta3 = resolver.applyLocalWrite(
            currentValue = result1.value,
            currentNode = result1.node,
            currentActors = null,
            newValue = protoV3,
            timestamp = 3,
        )
        val result3 = delta3.mergeResult

        // Resolve conflict - fields merge based on version vectors
        val conflictDelta = resolver.resolveConflict(
            localValue = result2.value,
            localNode = result2.node,
            localActors = delta2.actors,
            incomingValue = result3.value,
            incomingNode = result3.node!!,
            incomingVersionVector = delta3.actors.versionVectorMap,
        )
        val conflict = conflictDelta.mergeResult

        assertThat(conflict.resolution).isEqualTo(ResolutionStrategy.MERGED_VALUES)
        val merged = conflict.value
        assertThat(merged?.int32Value).isEqualTo(5) // unchanged
        assertThat(merged?.int64Value).isEqualTo(20) // from result2 (v2)
        assertThat(merged?.stringValue).isEqualTo("ARG") // from result3 (v3, higher version)
        assertThat(merged?.oneOfValue1).isEmpty() // oneOf cleared by result3
        assertThat(merged?.oneOfValue2)
            .isEqualTo(NestedMessage.newBuilder().setStringValue("1234").build()) // from result3
    }

    @Test
    fun testConflictResolution_noChange() {
        val v1 = Version.newBuilder().setTimestamp(1L).setActorId(100L).build()

        val proto = TestMessage.newBuilder().setStringValue("test").setInt32Value(123).build()

        val delta = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = proto,
            timestamp = 1
        )
        val result = delta.mergeResult

        // Resolve conflict with itself
        val conflictDelta = resolver.resolveConflict(
            localValue = result.value,
            localNode = result.node,
            localActors = delta.actors,
            incomingValue = result.value,
            incomingNode = result.node!!,
            incomingVersionVector = delta.actors.versionVectorMap,
        )
        val conflict = conflictDelta.mergeResult

        assertThat(conflict.resolution).isEqualTo(ResolutionStrategy.NO_CHANGE)
        assertThat(conflict.value).isEqualTo(proto)
        assertThat(conflict.node).isEqualTo(result.node)
    }

    @Test
    fun testEnumConflictResolution() {
        val v1 = Version.newBuilder().setTimestamp(1L).setActorId(100L).build()
        val v2 = Version.newBuilder().setTimestamp(2L).setActorId(200L).build()
        val v3 = Version.newBuilder().setTimestamp(3L).setActorId(300L).build()

        // Start with a message containing all enum types
        val initial = TestMessage.newBuilder()
            .setEnumValue(EnumSample.VALUE1)
            .addEnumListValue(EnumSample.VALUE1)
            .putEnumMapValue("key1", EnumSample.VALUE1)
            .build()

        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1
        )
        val result1 = delta1.mergeResult

        // Device A: Update single enum and add to list
        val deviceA = initial.toBuilder()
            .setEnumValue(EnumSample.VALUE2)
            .addEnumListValue(EnumSample.VALUE2)
            .build()

        val deltaA = resolver.applyLocalWrite(
            currentValue = result1.value,
            currentNode = result1.node,
            currentActors = delta1.actors,
            newValue = deviceA,
            timestamp = 2
        )
        val resultA = deltaA.mergeResult

        // Device B: Update map enum value
        val deviceB = initial.toBuilder()
            .putEnumMapValue("key1", EnumSample.UNKNOWN)
            .putEnumMapValue("key2", EnumSample.VALUE2)
            .build()

        val deltaB = resolver.applyLocalWrite(
            currentValue = result1.value,
            currentNode = result1.node,
            currentActors = deltaA.actors,
            newValue = deviceB,
            timestamp = 3
        )
        val resultB = deltaB.mergeResult

        // Resolve conflict - field-level merge
        val conflictDelta = resolver.resolveConflict(
            localValue = resultA.value,
            localNode = resultA.node,
            localActors = deltaA.actors,
            incomingValue = resultB.value,
            incomingNode = resultB.node!!,
            incomingVersionVector = deltaB.actors.versionVectorMap,
        )
        val conflict = conflictDelta.mergeResult

        assertThat(conflict.resolution).isEqualTo(ResolutionStrategy.MERGED_VALUES)

        val merged = conflict.value
        // Device A's single enum value (v2)
        assertThat(merged?.enumValue).isEqualTo(EnumSample.VALUE2)
        // Device A's list (v2)
        assertThat(merged?.enumListValueList).containsExactly(EnumSample.VALUE1, EnumSample.VALUE2)
        // Device B's map (v3, higher version)
        assertThat(merged?.enumMapValueMap).containsEntry("key1", EnumSample.UNKNOWN)
        assertThat(merged?.enumMapValueMap).containsEntry("key2", EnumSample.VALUE2)
    }
}
