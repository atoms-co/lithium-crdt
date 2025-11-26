package com.css.protobuf.crdt.protoc

import com.css.protobuf.crdt.test.EnumSample
import com.css.protobuf.crdt.test.NestedMessage
import com.css.protobuf.crdt.test.TestMessage
import com.css.protobuf.crdt.data.Version
import com.css.protobuf.crdt.data.VersionNode
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for CrdtMessageLocalResolver - applyLocalWrite operations.
 *
 * These tests focus on local write operations that update values with new versions,
 * without conflict resolution.
 */
class ProtoCrdtLocalResolverTest {
    private val provider = CrdtMessageResolverProvider()
    private val resolver = provider.getOrCreateResolverFor(TestMessage.getDefaultInstance())

    @Test
    fun crdtResolver_localMerge() {
        val protoV0 = TestMessage.newBuilder()
            .setInt32Value(5)
            .setInt64Value(10)
            .setStringValue("USD")
            .setEnumValue(EnumSample.VALUE2)
            .putEnumMapValue("2", EnumSample.VALUE1)
            .build()

        val delta = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = protoV0,
            timestamp = 1,
        )
        val result = delta.mergeResult

        assertThat(result.resolution).isTrue()
        assertThat(result.value).isEqualTo(protoV0)
        assertThat(result.node).isEqualTo(
            VersionNode.newBuilder()
                .setVersion(
                    Version.newBuilder()
                        .setTimestamp(1L)
                        .setActorId(delta.actors.localActor)
                        .setActorVersion(1)
                        .build()
                )
                .build()
        )

        val delta2 = resolver.applyLocalWrite(
            currentValue = protoV0,
            currentNode = VersionNode.newBuilder()
                .setVersion(
                    Version.newBuilder()
                        .setTimestamp(1L)
                        .setActorId(delta.actors.localActor).build()
                )
                .build(),
            currentActors = delta.actors,
            newValue = protoV0,
            timestamp = 1,
        )
        val result2 = delta2.mergeResult

        assertThat(result2.resolution).isFalse()
        assertThat(result2.value).isEqualTo(protoV0)
        assertThat(result2.node).isEqualTo(
            VersionNode.newBuilder()
                .setVersion(Version.newBuilder().setTimestamp(1L).setActorId(delta.actors.localActor).build())
                .build()
        )
    }

    @Test
    fun crdtResolver_localMerge_stringFieldUpdate() {
        val initial = TestMessage.newBuilder()
            .setStringValue("initial value")
            .setInt32Value(123)
            .build()

        // First insert
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1L,
        )
        val result1 = delta1.mergeResult

        assertThat(result1.resolution).isTrue()
        assertThat(result1.value).isEqualTo(initial)
        assertThat(result1.node).isEqualTo(
            VersionNode.newBuilder()
                .setVersion(
                    Version.newBuilder()
                        .setTimestamp(1L)
                        .setActorId(delta1.actors.localActor)
                        .setActorVersion(1)
                        .build()
                )
                .build()
        )

        // Re-insert same message - no change
        val delta2 = resolver.applyLocalWrite(
            currentValue = initial,
            currentNode = result1.node,
            currentActors = delta1.actors,
            newValue = initial,
            timestamp = 2L,
        )
        val result2 = delta2.mergeResult

        assertThat(result2.resolution).isFalse()
        assertThat(result2.value).isEqualTo(initial)

        // Change a primitive field (stringValue)
        val updated = initial.toBuilder().setStringValue("updated string").build()

        val delta3 = resolver.applyLocalWrite(
            currentValue = initial,
            currentNode = result1.node,
            currentActors = delta2.actors,
            newValue = updated,
            timestamp = 2L,
        )
        val result3 = delta3.mergeResult

        assertThat(result3.resolution).isTrue()
        assertThat(result3.value).isEqualTo(updated)
        assertThat(result3.node?.version).isEqualTo(
            Version.newBuilder()
                .setTimestamp(1L)
                .setActorId(delta3.actors.localActor)
                .setActorVersion(1)
                .build()
        )
    }

    @Test
    fun crdtResolver_localMerge_map_retainsUnchangedKeys() {

        // Step 1: Insert initial map {"k1":1, "k2":2}
        val initial = TestMessage.newBuilder()
            .putPrimitiveMapValue("k1", 1)
            .putPrimitiveMapValue("k2", 2)
            .build()

        val delta = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1L,
        )

        val res1 = delta.mergeResult
        assertThat(res1.resolution).isTrue()
        assertThat(res1.value).isEqualTo(initial)
        val node1 = res1.node!!

        // Step 2: Update k2 and add k3
        val updated = TestMessage.newBuilder()
            .putPrimitiveMapValue("k1", 1)
            .putPrimitiveMapValue("k2", 22)
            .putPrimitiveMapValue("k3", 3)
            .build()

        val delta2 = resolver.applyLocalWrite(
            currentValue = initial,
            currentNode = node1,
            currentActors = delta.actors,
            newValue = updated,
            timestamp = 2L
        )

        val res2 = delta2.mergeResult

        assertThat(res2.resolution).isTrue()
        assertThat(res2.value).isEqualTo(updated)
        val node2 = res2.node!!

        // Confirm all 3 keys tracked
        assertThat(node2.struct.fieldsMap[19]?.stringMap?.entriesMap?.keys)
            .containsExactly("k1", "k2", "k3")

        // Step 3: Remove k2
        val removed = TestMessage.newBuilder()
            .putPrimitiveMapValue("k1", 1)
            .putPrimitiveMapValue("k3", 3)
            .build()

        val delta3 = resolver.applyLocalWrite(
            currentValue = updated,
            currentNode = node2,
            currentActors = delta2.actors,
            newValue = removed,
            timestamp = 3
        )

        val res3 = delta3.mergeResult

        assertThat(res3.resolution).isTrue()
        assertThat(res3.value).isEqualTo(removed)
        val node3 = res3.node!!

        // k2 tombstone retained, all keys still tracked
        assertThat(node3.struct.fieldsMap[19]?.stringMap?.entriesMap?.keys)
            .containsExactly("k1", "k2", "k3")

        // Step 4: Write same map again (no change)
        val delta4 = resolver.applyLocalWrite(
            currentValue = removed,
            currentNode = node3,
            currentActors = delta3.actors,
            newValue = removed,
            timestamp = 4,
        )
        val res4 = delta4.mergeResult

        assertThat(res4.resolution).isFalse()
        assertThat(res4.value).isEqualTo(removed)
        val node4 = res4.node!!

        // All keys still tracked
        assertThat(node4.struct.fieldsMap[19]?.stringMap?.entriesMap?.keys)
            .containsExactly("k1", "k2", "k3")
    }

    @Test
    fun `repeated field operations - transaction line items scenario`() {
        // Step 1: Create initial transaction with 2 line items
        val lineItem1 = NestedMessage.newBuilder()
            .setStringValue("Product A")
            .setIntValue(100)
            .build()
        val lineItem2 = NestedMessage.newBuilder()
            .setStringValue("Product B")
            .setIntValue(200)
            .build()

        val initial = TestMessage.newBuilder()
            .addNestedListValue(lineItem1)
            .addNestedListValue(lineItem2)
            .build()

        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1
        )
        val result1 = delta1.mergeResult

        assertThat(result1.resolution).isTrue()
        assertThat(result1.value!!.nestedListValueList).hasSize(2)
        assertThat(result1.value!!.nestedListValueList)
            .containsExactly(lineItem1, lineItem2)
            .inOrder()

        // Step 2: Add a third line item
        val lineItem3 = NestedMessage.newBuilder()
            .setStringValue("Product C")
            .setIntValue(300)
            .build()

        val withAddedItem = TestMessage.newBuilder()
            .addNestedListValue(lineItem1)
            .addNestedListValue(lineItem2)
            .addNestedListValue(lineItem3)
            .build()

        val delta2 = resolver.applyLocalWrite(
            currentValue = result1.value,
            currentNode = result1.node,
            currentActors = delta1.actors,
            newValue = withAddedItem,
            timestamp = 2
        )
        val result2 = delta2.mergeResult

        assertThat(result2.resolution).isTrue()
        assertThat(result2.value!!.nestedListValueList).hasSize(3)
        assertThat(result2.value!!.nestedListValueList)
            .containsExactly(lineItem1, lineItem2, lineItem3)
            .inOrder()

        // Step 3: Modify middle item (change quantity)
        val modifiedLineItem2 = lineItem2.toBuilder().setIntValue(250).build()
        val withModifiedItem = TestMessage.newBuilder()
            .addNestedListValue(lineItem1)
            .addNestedListValue(modifiedLineItem2)
            .addNestedListValue(lineItem3)
            .build()

        val delta3 = resolver.applyLocalWrite(
            currentValue = result2.value,
            currentNode = result2.node,
            currentActors = delta2.actors,
            newValue = withModifiedItem,
            timestamp = 3
        )
        val result3 = delta3.mergeResult

        assertThat(result3.resolution).isTrue()
        assertThat(result3.value!!.getNestedListValue(1)).isEqualTo(modifiedLineItem2)

        // Step 4: Remove last item
        val withRemovedItem = TestMessage.newBuilder()
            .addNestedListValue(lineItem1)
            .addNestedListValue(modifiedLineItem2)
            .build()

        val delta4 = resolver.applyLocalWrite(
            currentValue = result3.value,
            currentNode = result3.node,
            currentActors = delta3.actors,
            newValue = withRemovedItem,
            timestamp = 4
        )
        val result4 = delta4.mergeResult

        assertThat(result4.resolution).isTrue()
        assertThat(result4.value!!.nestedListValueList).hasSize(2)
        assertThat(result4.value!!.nestedListValueList)
            .containsExactly(lineItem1, modifiedLineItem2)
            .inOrder()

        // Verify version tracking for list field (tag 22)
        assertThat(result4.node!!.struct.fieldsMap.containsKey(22)).isTrue()
    }

    @Test
    fun `repeated field - empty list operations`() {
        // Start with populated list
        val item1 = NestedMessage.newBuilder()
            .setStringValue("Item 1")
            .setIntValue(100)
            .build()
        val initial = TestMessage.newBuilder().addNestedListValue(item1).build()

        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1
        )
        val result1 = delta1.mergeResult

        // Clear the list
        val cleared = TestMessage.newBuilder().build()

        val delta2 = resolver.applyLocalWrite(
            currentValue = result1.value,
            currentNode = result1.node,
            currentActors = delta1.actors,
            newValue = cleared,
            timestamp = 2
        )
        val result2 = delta2.mergeResult

        assertThat(result2.resolution).isTrue()
        assertThat(result2.value?.nestedListValueList).isEmpty()

        // Add back to empty list
        val item2 = NestedMessage.newBuilder()
            .setStringValue("New Item")
            .setIntValue(500)
            .build()
        val restored = TestMessage.newBuilder().addNestedListValue(item2).build()

        val delta3 = resolver.applyLocalWrite(
            currentValue = result2.value,
            currentNode = result2.node,
            currentActors = delta2.actors,
            newValue = restored,
            timestamp = 3
        )
        val result3 = delta3.mergeResult

        assertThat(result3.resolution).isTrue()
        assertThat(result3.value?.nestedListValueList).containsExactly(item2)
    }

    @Test
    fun `optional fields - setting and ignoring values`() {
        // Step 1: Start with no optional values set
        val initial = TestMessage.newBuilder().setStringValue("base_data").build()

        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1
        )
        val result1 = delta1.mergeResult

        assertThat(result1.value?.hasPrimitiveOptionalValue()).isFalse()
        assertThat(result1.value?.hasNestedOptionalValue()).isFalse()

        // Step 2: Set optional primitive value
        val withPrimitive = initial.toBuilder().setPrimitiveOptionalValue(42).build()

        val delta2 = resolver.applyLocalWrite(
            currentValue = result1.value,
            currentNode = result1.node,
            currentActors = delta1.actors,
            newValue = withPrimitive,
            timestamp = 2
        )
        val result2 = delta2.mergeResult

        assertThat(result2.resolution).isTrue()
        assertThat(result2.value?.primitiveOptionalValue).isEqualTo(42)
        assertThat(result2.value?.hasNestedOptionalValue()).isFalse()

        // Step 3: Set optional nested value
        val optionalNested = NestedMessage.newBuilder()
            .setStringValue("optional_data")
            .setIntValue(100)
            .build()
        val withNested = withPrimitive.toBuilder().setNestedOptionalValue(optionalNested).build()

        val delta3 = resolver.applyLocalWrite(
            currentValue = result2.value,
            currentNode = result2.node,
            currentActors = delta2.actors,
            newValue = withNested,
            timestamp = 3
        )
        val result3 = delta3.mergeResult

        assertThat(result3.resolution).isTrue()
        assertThat(result3.value?.primitiveOptionalValue).isEqualTo(42)
        assertThat(result3.value?.nestedOptionalValue).isEqualTo(optionalNested)

        // Step 4: Ignores null optional values (field-level merge)
        val delta4 = resolver.applyLocalWrite(
            currentValue = result3.value,
            currentNode = result3.node,
            currentActors = delta3.actors,
            newValue = withNested.toBuilder()
                .setNestedOptionalValue(NestedMessage.newBuilder().setIntValue(120).build())
                .build(),
            timestamp = 4
        )
        val result4 = delta4.mergeResult

        assertThat(result4.resolution).isTrue()
        assertThat(result4.value?.primitiveOptionalValue).isEqualTo(42)
        assertThat(result4.value?.nestedOptionalValue?.stringValue).isEqualTo("optional_data")
        assertThat(result4.value?.nestedOptionalValue?.intValue).isEqualTo(120)

        // Verify field-level version tracking for optional fields (tags 24, 25)
        assertThat(result4.node!!.struct.fieldsMap.containsKey(24)).isTrue()
        assertThat(result4.node!!.struct.fieldsMap.containsKey(25)).isTrue()
    }

    @Test
    fun testEnumValue_singleEnum() {
        // Test single enum field (enumValue)
        val initial = TestMessage.newBuilder().setEnumValue(EnumSample.VALUE1).build()

        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1
        )
        val result1 = delta1.mergeResult

        assertThat(result1.resolution).isTrue()
        assertThat(result1.value?.enumValue).isEqualTo(EnumSample.VALUE1)

        // Update enum value
        val updated = initial.toBuilder().setEnumValue(EnumSample.VALUE2).build()

        val delta2 = resolver.applyLocalWrite(
            currentValue = result1.value,
            currentNode = result1.node,
            currentActors = delta1.actors,
            newValue = updated,
            timestamp = 2
        )
        val result2 = delta2.mergeResult

        assertThat(result2.resolution).isTrue()
        assertThat(result2.value?.enumValue).isEqualTo(EnumSample.VALUE2)

        // Verify field-level version tracking for enum field (tag 15)
        assertThat(result2.node!!.struct.fieldsMap.containsKey(15)).isTrue()
    }

    @Test
    fun testEnumList_repeatedEnum() {
        // Test repeated enum field (enumList)
        val initial = TestMessage.newBuilder()
            .addEnumListValue(EnumSample.VALUE1)
            .addEnumListValue(EnumSample.VALUE2)
            .build()

        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1
        )
        val result1 = delta1.mergeResult

        assertThat(result1.resolution).isTrue()
        assertThat(result1.value?.enumListValueList)
            .containsExactly(EnumSample.VALUE1, EnumSample.VALUE2)
            .inOrder()

        // Add another enum to the list
        val withAddedEnum = TestMessage.newBuilder()
            .addEnumListValue(EnumSample.VALUE1)
            .addEnumListValue(EnumSample.VALUE2)
            .addEnumListValue(EnumSample.UNKNOWN)
            .build()

        val delta2 = resolver.applyLocalWrite(
            currentValue = result1.value,
            currentNode = result1.node,
            currentActors = delta1.actors,
            newValue = withAddedEnum,
            timestamp = 2
        )
        val result2 = delta2.mergeResult

        assertThat(result2.resolution).isTrue()
        assertThat(result2.value?.enumListValueList)
            .containsExactly(EnumSample.VALUE1, EnumSample.VALUE2, EnumSample.UNKNOWN)
            .inOrder()

        // Clear and add different enums
        val cleared = TestMessage.newBuilder().addEnumListValue(EnumSample.VALUE2).build()

        val delta3 = resolver.applyLocalWrite(
            currentValue = result2.value,
            currentNode = result2.node,
            currentActors = delta2.actors,
            newValue = cleared,
            timestamp = 3
        )
        val result3 = delta3.mergeResult

        assertThat(result3.resolution).isTrue()
        assertThat(result3.value?.enumListValueList).containsExactly(EnumSample.VALUE2)

        // Verify field-level version tracking for repeated enum field (tag 29)
        assertThat(result3.node!!.struct.fieldsMap.containsKey(29)).isTrue()
    }

    @Test
    fun testEnumMapValue_mapWithEnumValues() {
        // Test map with enum values (enumMapValue)
        val initial = TestMessage.newBuilder()
            .putEnumMapValue("key1", EnumSample.VALUE1)
            .putEnumMapValue("key2", EnumSample.VALUE2)
            .build()

        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1
        )
        val result1 = delta1.mergeResult

        assertThat(result1.resolution).isTrue()
        assertThat(result1.value?.enumMapValueMap)
            .containsExactly(
                "key1", EnumSample.VALUE1,
                "key2", EnumSample.VALUE2
            )

        // Update one enum value and add a new key
        val updated = TestMessage.newBuilder()
            .putEnumMapValue("key1", EnumSample.UNKNOWN)
            .putEnumMapValue("key2", EnumSample.VALUE2)
            .putEnumMapValue("key3", EnumSample.VALUE1)
            .build()

        val delta2 = resolver.applyLocalWrite(
            currentValue = result1.value,
            currentNode = result1.node,
            currentActors = delta1.actors,
            newValue = updated,
            timestamp = 2
        )
        val result2 = delta2.mergeResult

        assertThat(result2.resolution).isTrue()
        assertThat(result2.value?.enumMapValueMap)
            .containsExactly(
                "key1", EnumSample.UNKNOWN,
                "key2", EnumSample.VALUE2,
                "key3", EnumSample.VALUE1
            )

        // Remove a key
        val removed = TestMessage.newBuilder()
            .putEnumMapValue("key1", EnumSample.UNKNOWN)
            .putEnumMapValue("key3", EnumSample.VALUE1)
            .build()

        val delta3 = resolver.applyLocalWrite(
            currentValue = result2.value,
            currentNode = result2.node,
            currentActors = delta2.actors,
            newValue = removed,
            timestamp = 3
        )
        val result3 = delta3.mergeResult

        assertThat(result3.resolution).isTrue()
        assertThat(result3.value?.enumMapValueMap)
            .containsExactly(
                "key1", EnumSample.UNKNOWN,
                "key3", EnumSample.VALUE1
            )

        // Verify field-level version tracking for enum map field (tag 21)
        assertThat(result3.node!!.struct.fieldsMap.containsKey(21)).isTrue()
        // Verify all keys are tracked
        assertThat(result3.node!!.struct.fieldsMap[21]?.stringMap?.entriesMap?.keys)
            .containsExactly("key1", "key2", "key3")
    }
}
