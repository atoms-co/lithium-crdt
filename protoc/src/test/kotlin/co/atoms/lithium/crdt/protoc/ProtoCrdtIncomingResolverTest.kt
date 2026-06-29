package co.atoms.lithium.crdt.protoc

import co.atoms.lithium.crdt.test.EnumSample
import co.atoms.lithium.crdt.test.NestedMessage
import co.atoms.lithium.crdt.test.NestedMessageWithId
import co.atoms.lithium.crdt.test.TestMessage
import co.atoms.lithium.crdt.data.Actors
import co.atoms.lithium.crdt.data.PathComponent
import co.atoms.lithium.crdt.data.Version
import co.atoms.lithium.crdt.data.VersionNode
import co.atoms.lithium.crdt.resolver.ResolverDeltaResult
import co.atoms.lithium.crdt.resolver.version.ResolutionStrategy
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
            incomingNode = requireNotNull(result3.node),
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
            incomingNode = requireNotNull(conflict.node),
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
            incomingNode = requireNotNull(resultB.node),
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
            incomingNode = requireNotNull(resultB.node),
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
            incomingNode = requireNotNull(result3.node),
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
            incomingNode = requireNotNull(result.node),
            incomingVersionVector = delta.actors.versionVectorMap,
        )
        val conflict = conflictDelta.mergeResult

        assertThat(conflict.resolution).isEqualTo(ResolutionStrategy.NO_CHANGE)
        assertThat(conflict.value).isEqualTo(proto)
        assertThat(conflict.node).isEqualTo(result.node)
    }

    @Test
    fun `enum map conflict resolution - change events can be encoded`() {
        // Regression test: enum map values returned from get() must be EnumValueDescriptors,
        // not raw Integers, otherwise encoded() on the resulting ChangeEvents will throw
        // ClassCastException (Integer cannot be cast to EnumValueDescriptor).
        val initial = TestMessage.newBuilder()
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

        // Device A: keep same map
        val deltaA = resolver.applyLocalWrite(
            currentValue = result1.value,
            currentNode = result1.node,
            currentActors = delta1.actors,
            newValue = initial,
            timestamp = 2
        )
        val resultA = deltaA.mergeResult

        // Device B: change enum map value
        val deviceB = initial.toBuilder()
            .putEnumMapValue("key1", EnumSample.VALUE2)
            .build()

        val deltaB = resolver.applyLocalWrite(
            currentValue = result1.value,
            currentNode = result1.node,
            currentActors = null,
            newValue = deviceB,
            timestamp = 3
        )
        val resultB = deltaB.mergeResult

        // Resolve conflict — this produces ChangeEvents for the enum map field
        val conflictDelta = resolver.resolveConflict(
            localValue = resultA.value,
            localNode = resultA.node,
            localActors = deltaA.actors,
            incomingValue = resultB.value,
            incomingNode = requireNotNull(resultB.node),
            incomingVersionVector = deltaB.actors.versionVectorMap,
        )

        // The critical assertion: encoded() must not throw ClassCastException
        for (change in conflictDelta.changes) {
            change.encoded() // throws if enum map values are raw Integers instead of EnumValueDescriptors
        }

        assertThat(conflictDelta.mergeResult.value?.enumMapValueMap)
            .containsEntry("key1", EnumSample.VALUE2)
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
            incomingNode = requireNotNull(resultB.node),
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

    @Test
    fun `unset field does not overwrite populated field when parent version is higher`() {
        // Device A sets stringValue (field 13) earlier; Device B sets only int32Value (field 3)
        // later, so its never-set stringValue would inherit B's higher parent version.
        val deviceA = write(TestMessage.newBuilder().setStringValue("hello").build(), timestamp = 1)
        val deviceB = write(TestMessage.newBuilder().setInt32Value(42).build(), timestamp = 5)

        val merged = merge(deviceA, deviceB).mergeResult

        assertThat(merged.value?.int32Value).isEqualTo(42)
        assertThat(merged.value?.stringValue).isEqualTo("hello")
    }

    @Test
    fun `unset field does not overwrite populated field regardless of merge order`() {
        val deviceA = write(TestMessage.newBuilder().setStringValue("hello").build(), timestamp = 1)
        val deviceB = write(TestMessage.newBuilder().setInt32Value(42).build(), timestamp = 5)

        val merged = merge(deviceB, deviceA).mergeResult

        assertThat(merged.value?.int32Value).isEqualTo(42)
        assertThat(merged.value?.stringValue).isEqualTo("hello")
    }

    @Test
    fun `reset to default is a real write and survives the collapse optimization`() {
        val base = write(
            TestMessage.newBuilder().setStringValue("hello").setInt32Value(1).build(),
            timestamp = 100,
        )
        // Reset stringValue back to default (""), keeping int32Value.
        val reset = write(
            TestMessage.newBuilder().setInt32Value(1).build(),
            timestamp = 300,
            previous = base,
        )

        val field13 = reset.mergeResult.node?.struct?.fieldsMap?.get(13)
        assertThat(field13).isNotNull()
        assertThat(field13?.version?.timestamp).isEqualTo(300)
    }

    @Test
    fun `reset wins over an older value on merge`() {
        val base = write(
            TestMessage.newBuilder().setStringValue("hello").setInt32Value(1).build(),
            timestamp = 100,
        )
        val deviceA = write(
            TestMessage.newBuilder().setInt32Value(1).build(),
            timestamp = 300,
            previous = base,
        )

        assertThat(merge(base, deviceA).mergeResult.value?.stringValue).isEqualTo("")
        assertThat(merge(deviceA, base).mergeResult.value?.stringValue).isEqualTo("")
    }

    @Test
    fun `merged parent version is the floor of the two sides`() {
        val a = write(TestMessage.newBuilder().setStringValue("a").build(), timestamp = 10)
        val b = write(TestMessage.newBuilder().setInt32Value(1).build(), timestamp = 5)

        val merged = merge(a, b).mergeResult

        assertThat(merged.value?.stringValue).isEqualTo("a")
        assertThat(merged.value?.int32Value).isEqualTo(1)
        val node = requireNotNull(merged.node)
        // Parent baseline is the floor (5), not the max (10).
        assertThat(node.version.timestamp).isEqualTo(5)
        // The field at the floor collapses into the parent; the higher field keeps its leaf.
        assertThat(node.struct.fieldsMap.containsKey(3)).isFalse()
        assertThat(node.struct.fieldsMap[13]?.version?.timestamp).isEqualTo(10)
    }

    @Test
    fun `clean local win preserves the winning version and does not apply the floor`() {
        val base = write(
            TestMessage.newBuilder().setStringValue("old").setInt32Value(1).build(),
            timestamp = 5,
        )
        val local = write(
            TestMessage.newBuilder().setStringValue("new").setInt32Value(1).build(),
            timestamp = 20,
            previous = base,
        )

        val merged = merge(local, base).mergeResult

        assertThat(merged.value?.stringValue).isEqualTo("new")
        assertThat(merged.node).isEqualTo(local.mergeResult.node)
    }

    @Test
    fun `nested message merge applies the floor independently at each level`() {
        val a = write(
            TestMessage.newBuilder()
                .setNestedValue(NestedMessage.newBuilder().setStringValue("n").build())
                .build(),
            timestamp = 10,
        )
        val b = write(
            TestMessage.newBuilder()
                .setNestedValue(NestedMessage.newBuilder().setIntValue(7).build())
                .build(),
            timestamp = 4,
        )

        val merged = merge(a, b).mergeResult

        assertThat(merged.value?.nestedValue?.stringValue).isEqualTo("n")
        assertThat(merged.value?.nestedValue?.intValue).isEqualTo(7)
        val node = requireNotNull(merged.node)
        assertThat(node.version.timestamp).isEqualTo(4)
        assertThat(node.struct.fieldsMap[16]?.version?.timestamp).isEqualTo(4)
    }

    @Test
    fun `reset leaf at the parent floor is protected from collapse`() {
        val base = write(
            TestMessage.newBuilder().setStringValue("hello").setInt32Value(1).build(),
            timestamp = 100,
        )
        val deviceA = write(
            TestMessage.newBuilder().setInt32Value(1).build(),
            timestamp = 150,
            previous = base,
        )
        val deviceB = write(
            TestMessage.newBuilder().setStringValue("hello").setInt32Value(2).build(),
            timestamp = 200,
            previous = base,
        )

        val merged = merge(deviceA, deviceB).mergeResult

        // The reset wins over the stale "hello" -> stringValue stays cleared.
        assertThat(merged.value?.stringValue).isEqualTo("")
        // And the reset keeps an explicit node (not collapsed), preserving its version.
        val resetNode = merged.node?.struct?.fieldsMap?.get(13)
        assertThat(resetNode).isNotNull()
        assertThat(resetNode?.version?.timestamp).isEqualTo(150)
    }

    @Test
    fun `equal timestamps converge via deterministic actor tie-break regardless of order`() {
        val a = write(TestMessage.newBuilder().setStringValue("from-a").build(), timestamp = 50)
        val b = write(TestMessage.newBuilder().setStringValue("from-b").build(), timestamp = 50)

        val ab = merge(a, b).mergeResult
        val ba = merge(b, a).mergeResult

        assertThat(ab.value).isEqualTo(ba.value)
        assertThat(ab.node).isEqualTo(ba.node)
    }

    @Test
    fun `merge converges regardless of order when a field is equal-valued at different inherited versions`() {
        val a = write(
            TestMessage.newBuilder().setStringValue("same").setInt32Value(1).build(),
            timestamp = 10,
        )
        val b = write(
            TestMessage.newBuilder().setStringValue("same").setInt64Value(2).build(),
            timestamp = 5,
        )
        val c = write(
            TestMessage.newBuilder().setStringValue("same").setBoolValue(true).build(),
            timestamp = 7,
        )

        val abThenC = merge(merge(a, b), c).mergeResult
        val aThenBC = merge(a, merge(b, c)).mergeResult

        assertThat(abThenC.value).isEqualTo(aThenBC.value)
        assertThat(abThenC.node).isEqualTo(aThenBC.node)
    }

    private fun write(
        value: TestMessage,
        timestamp: Long,
        previous: ResolverDeltaResult<TestMessage, VersionNode, Version, *, PathComponent, Actors>? = null,
    ) = resolver.applyLocalWrite(
        currentValue = previous?.mergeResult?.value,
        currentNode = previous?.mergeResult?.node,
        currentActors = previous?.actors,
        newValue = value,
        timestamp = timestamp,
    )

    private fun merge(
        local: ResolverDeltaResult<TestMessage, VersionNode, Version, *, PathComponent, Actors>,
        incoming: ResolverDeltaResult<TestMessage, VersionNode, Version, *, PathComponent, Actors>,
    ) = resolver.resolveConflict(
        localValue = local.mergeResult.value,
        localNode = local.mergeResult.node,
        localActors = local.actors,
        incomingValue = incoming.mergeResult.value,
        incomingNode = requireNotNull(incoming.mergeResult.node),
        incomingVersionVector = incoming.actors.versionVectorMap,
    )
}
