package com.css.protobuf.crdt.wire

import com.css.protobuf.crdt.test.NestedMessage
import com.css.protobuf.crdt.test.TestMessage
import com.css.protobuf.crdt.data.Version
import com.css.protobuf.crdt.data.VersionNode
import com.css.protobuf.crdt.resolver.NodeMergeResult
import com.css.protobuf.crdt.wire.internal.WireVersionTreeResolver
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Tests for CrdtMessageLocalResolver - applyLocalWrite operations.
 *
 * These tests focus on local write operations that update values with new versions,
 * without conflict resolution.
 */
class WireCrdtLocalResolverTest {
    private val adapter = WireCrdtResolverProvider().messageResolver(adapter = TestMessage.ADAPTER)
    private val versionTreeResolver = WireVersionTreeResolver

    private val sampleProtoMessage = TestMessage(
        int32Value = 100,
        stringValue = "sample"
    )

    @Test
    fun crdtResolver_localMerge() {
        val protoV0 =
            TestMessage(
                int32Value = 5,
                int64Value = 10,
                stringValue = "USD",
                enumValue = com.css.protobuf.crdt.test.EnumSample.VALUE2,
                enumMapValue = mapOf("2" to com.css.protobuf.crdt.test.EnumSample.VALUE1),
            )

        var delta =
            adapter.applyLocalWrite(
                currentValue = null,
                currentNode = null,
                currentActors = null,
                newValue = protoV0,
                timestamp = 2,
            )
        var result = delta.mergeResult

        assertThat(result.resolution).isTrue()
        assertThat(result.value).isEqualTo(protoV0)
        assertThat(result.node).isEqualTo(VersionNode(version = Version(2, delta.actors.local_actor, 1)))

        delta =
            adapter.applyLocalWrite(
                currentValue = protoV0,
                currentNode = VersionNode(version = Version(1, 2)),
                currentActors = null,
                newValue = protoV0,
                timestamp = 2,
            )
        result = delta.mergeResult

        assertThat(result.resolution).isFalse()
        assertThat(result.value).isEqualTo(protoV0)
        assertThat(result.node).isEqualTo(VersionNode(version = Version(1, 2)))
    }

    @Test
    fun crdtResolver_localMerge_sampleMessage() = with(versionTreeResolver) {
        // 1️⃣ First insert
        val delta1 =
            adapter.applyLocalWrite(
                currentValue = null,
                currentNode = null,
                currentActors = null,
                newValue = sampleProtoMessage,
                timestamp = 100,
            )
        val result1 = delta1.mergeResult

        assertThat(result1.resolution).isTrue()
        assertThat(result1.value).isEqualTo(sampleProtoMessage)
        val v1 = Version(100, delta1.actors.local_actor, 1)
        assertThat(result1.node?.version).isEqualTo(v1)

        // 2️⃣ Re-insert same message - no change
        val delta2 =
            adapter.applyLocalWrite(
                currentValue = sampleProtoMessage,
                currentNode = result1.node,
                currentActors = null,
                newValue = sampleProtoMessage,
                timestamp = 200,
            )
        val result2 = delta2.mergeResult

        assertThat(result2.resolution).isFalse()
        assertThat(result2.value).isEqualTo(sampleProtoMessage)
        assertThat(result2.node).isEqualTo(result1.node)

        // 3️⃣ Change a primitive field (stringValue)
        val updatedMessage = sampleProtoMessage.copy(stringValue = "updated string")

        val delta3 =
            adapter.applyLocalWrite(
                currentValue = sampleProtoMessage,
                currentNode = result1.node,
                currentActors = null,
                newValue = updatedMessage,
                timestamp = 200,
            )
        val result3 = delta3.mergeResult
        val v2 = Version(200, delta3.actors.local_actor, 1)

        assertThat(result3.resolution).isEqualTo(true)
        assertThat(result3.value).isEqualTo(updatedMessage)
        assertThat(result3.node?.version).isEqualTo(v1)
        assertThat(result3.node?.maxVersion(v1)).isEqualTo(v2)

        // 4️⃣ Remove the field (simulate deletion)
        val removedMessage = sampleProtoMessage.copy(stringValue = "")

        val delta4 =
            adapter.applyLocalWrite(
                currentValue = updatedMessage,
                currentNode = result3.node,
                currentActors = null,
                newValue = removedMessage,
                timestamp = 300,
            )
        val result4 = delta4.mergeResult
        val v3 = Version(300, delta4.actors.local_actor, 1)

        assertThat(result4.resolution).isEqualTo(true)
        assertThat(result4.value).isEqualTo(removedMessage)
        assertThat(result4.node?.version).isEqualTo(v1)
        assertThat(result4.node?.maxVersion(v1)).isEqualTo(v3)
    }

    @Test
    fun crdtResolver_localMerge_map_retainsUnchangedKeys() {
        with(versionTreeResolver) {
            // Step 1: Insert initial map {"k1":1, "k2":2}
            val initial = TestMessage(primitiveMapValue = mapOf("k1" to 1, "k2" to 2))
            val res1 =
                adapter.applyLocalWrite(
                    currentValue = null,
                    currentNode = null,
                    currentActors = null,
                    newValue = initial,
                    timestamp = 1,
                ).mergeResult
            assertThat(res1.resolution).isTrue()
            assertThat(res1.value).isEqualTo(initial)
            val node1 = res1.node

            // Step 2: Update k2 and add k3
            val updated = initial.copy(primitiveMapValue = mapOf("k1" to 1, "k2" to 22, "k3" to 3))
            val res2 =
                adapter.applyLocalWrite(
                    currentValue = initial,
                    currentNode = node1,
                    currentActors = null,
                    newValue = updated,
                    timestamp = 2,
                ).mergeResult
            assertThat(res2.resolution).isTrue()
            assertThat(res2.value).isEqualTo(updated)
            val node2 = res2.node
            assertNotNull(node2)
            // ✅ Confirm all 3 keys tracked
            assertThat(node2.struct?.fields?.get(19)?.string_map?.entries?.keys).containsExactly("k1", "k2", "k3")

            // Step 3: Remove k2
            val removed = initial.copy(primitiveMapValue = mapOf("k1" to 1, "k3" to 3))
            val res3 =
                adapter.applyLocalWrite(
                    currentValue = updated,
                    currentNode = node2,
                    currentActors = null,
                    newValue = removed,
                    timestamp = 3,
                ).mergeResult
            assertThat(res3.resolution).isTrue()
            assertThat(res3.value).isEqualTo(removed)
            val node3 = res3.node
            assertNotNull(node3)
            // ✅ k2 tombstone retained, all keys still tracked
            assertThat(node3.struct?.fields?.get(19)?.string_map?.entries?.keys).containsExactly("k1", "k2", "k3")

            // Step 4: Write same map again (no change)
            val res4 =
                adapter.applyLocalWrite(
                    currentValue = removed,
                    currentNode = node3,
                    currentActors = null,
                    newValue = removed,
                    timestamp = 4,
                ).mergeResult
            assertThat(res4.resolution).isFalse()
            assertThat(res4.value).isEqualTo(removed)
            val node4 = res4.node
            assertNotNull(node4)
            // ✅ All keys still tracked
            assertThat(node4.struct?.fields?.get(19)?.string_map?.entries?.keys).containsExactly("k1", "k2", "k3")
        }
    }

    @Test
    fun `repeated field operations - transaction line items scenario`() {
        // Step 1: Create initial transaction with 2 line items
        val lineItem1 = NestedMessage(stringValue = "Product A", intValue = 100)
        val lineItem2 = NestedMessage(stringValue = "Product B", intValue = 200)
        val initial = TestMessage(nestedListValue = listOf(lineItem1, lineItem2))

        val result1 = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 100,
        ).mergeResult

        assertThat(result1.resolution).isTrue()
        assertThat(result1.value?.nestedListValue).hasSize(2)
        assertThat(result1.value?.nestedListValue).containsExactly(lineItem1, lineItem2).inOrder()

        // Step 2: Add a third line item
        val lineItem3 = NestedMessage(stringValue = "Product C", intValue = 300)
        val withAddedItem = initial.copy(nestedListValue = listOf(lineItem1, lineItem2, lineItem3))

        val result2 = adapter.applyLocalWrite(
            currentValue = result1.value,
            currentNode = result1.node,
            currentActors = null,
            newValue = withAddedItem,
            timestamp = 200,
        ).mergeResult

        assertThat(result2.resolution).isTrue()
        assertThat(result2.value?.nestedListValue).hasSize(3)
        assertThat(result2.value?.nestedListValue).containsExactly(lineItem1, lineItem2, lineItem3).inOrder()

        // Step 3: Modify middle item (change quantity)
        val modifiedLineItem2 = lineItem2.copy(intValue = 250)
        val withModifiedItem = withAddedItem.copy(
            nestedListValue = listOf(lineItem1, modifiedLineItem2, lineItem3)
        )

        val result3 = adapter.applyLocalWrite(
            currentValue = result2.value,
            currentNode = result2.node,
            currentActors = null,
            newValue = withModifiedItem,
            timestamp = 300,
        ).mergeResult

        assertThat(result3.resolution).isTrue()
        assertThat(result3.value?.nestedListValue?.get(1)).isEqualTo(modifiedLineItem2)

        // Step 4: Remove last item
        val withRemovedItem = withModifiedItem.copy(
            nestedListValue = listOf(lineItem1, modifiedLineItem2)
        )

        val result4 = adapter.applyLocalWrite(
            currentValue = result3.value,
            currentNode = result3.node,
            currentActors = null,
            newValue = withRemovedItem,
            timestamp = 400,
        ).mergeResult

        assertThat(result4.resolution).isTrue()
        assertThat(result4.value?.nestedListValue).hasSize(2)
        assertThat(result4.value?.nestedListValue).containsExactly(lineItem1, modifiedLineItem2).inOrder()

        // Verify version tracking for list field (tag 22)
        assertThat(result4.node?.struct?.fields?.containsKey(22)).isTrue()
    }

    @Test
    fun `repeated field - empty list operations`() {
        // Start with populated list
        val item1 = NestedMessage(stringValue = "Item 1", intValue = 100)
        val initial = TestMessage(nestedListValue = listOf(item1))

        val result1 = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 100,
        ).mergeResult

        // Clear the list
        val cleared = initial.copy(nestedListValue = emptyList())

        val result2 = adapter.applyLocalWrite(
            currentValue = result1.value,
            currentNode = result1.node,
            currentActors = null,
            newValue = cleared,
            timestamp = 200,
        ).mergeResult

        assertThat(result2.resolution).isTrue()
        assertThat(result2.value?.nestedListValue).isEmpty()

        // Add back to empty list
        val item2 = NestedMessage(stringValue = "New Item", intValue = 500)
        val restored = cleared.copy(nestedListValue = listOf(item2))

        val result3 = adapter.applyLocalWrite(
            currentValue = result2.value,
            currentNode = result2.node,
            currentActors = null,
            newValue = restored,
            timestamp = 300,
        ).mergeResult

        assertThat(result3.resolution).isTrue()
        assertThat(result3.value?.nestedListValue).containsExactly(item2)
    }

    @Test
    fun `optional fields - setting and ignoring values`() {
        // Step 1: Start with no optional values set
        val initial = TestMessage(
            stringValue = "base_data",
            primitiveOptionalValue = null,
            nestedOptionalValue = null
        )

        val result1 = adapter.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 100,
        ).mergeResult

        assertThat(result1.value?.primitiveOptionalValue).isNull()
        assertThat(result1.value?.nestedOptionalValue).isNull()

        // Step 2: Set optional primitive value
        val withPrimitive = initial.copy(primitiveOptionalValue = 42)

        val result2 = adapter.applyLocalWrite(
            current = result1,
            newValue = withPrimitive,
            timestamp = 200,
        ).mergeResult

        assertThat(result2.resolution).isTrue()
        assertThat(result2.value?.primitiveOptionalValue).isEqualTo(42)
        assertThat(result2.value?.nestedOptionalValue).isNull()

        // Step 3: Set optional nested value
        val optionalNested = NestedMessage(stringValue = "optional_data", intValue = 100)
        val withNested = withPrimitive.copy(nestedOptionalValue = optionalNested)

        val result3 = adapter.applyLocalWrite(
            current = result2,
            newValue = withNested,
            timestamp = 300,
        ).mergeResult

        assertThat(result3.resolution).isTrue()
        assertThat(result3.value?.primitiveOptionalValue).isEqualTo(42)
        assertThat(result3.value?.nestedOptionalValue).isEqualTo(optionalNested)

        // Step 4: Ignores null optional values
        val result4 = adapter.applyLocalWrite(
            current = result3,
            newValue = withNested.copy(
                primitiveOptionalValue = null,
                nestedOptionalValue = NestedMessage(intValue = 120)
            ),
            timestamp = 400,
        ).mergeResult

        assertThat(result4.resolution).isTrue()
        assertThat(result4.value?.primitiveOptionalValue).isEqualTo(42)
        assertThat(result4.value?.nestedOptionalValue).isEqualTo(
            NestedMessage(
                stringValue = "optional_data",
                intValue = 120
            )
        )

        // Verify field-level version tracking for optional fields (tags 24, 25)
        assertThat(result4.node?.struct?.fields?.containsKey(24)).isTrue()
        assertThat(result4.node?.struct?.fields?.containsKey(25)).isTrue()
    }

    // Helper methods
    fun <T, R> WireCrdtMessageResolver<T>.applyLocalWrite(
        current: NodeMergeResult<T, VersionNode, R>,
        newValue: T?,
        timestamp: Long,
    ) = applyLocalWrite(
        currentValue = current.value,
        currentNode = current.node,
        currentActors = null,
        newValue = newValue,
        timestamp = timestamp,
    )
}
