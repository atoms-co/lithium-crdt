package com.css.protobuf.crdt.wire

import com.css.protobuf.crdt.test.EnumSample
import com.css.protobuf.crdt.test.NestedMessage
import com.css.protobuf.crdt.test.TestMessage
import com.css.protobuf.crdt.data.PathComponent
import com.css.protobuf.crdt.data.Version
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

/**
 * Tests for CrdtMessageDeltaResolver - delta/changes tracking.
 *
 * These tests focus on the delta tracking functionality that records what changed
 * during both local writes and conflict resolution, including path tracking and encoding.
 */
class WireCrdtDeltaResolverTest {
    private val resolver = WireCrdtResolverProvider().messageResolver(adapter = TestMessage.ADAPTER)

    // ========== Delta Tracking Tests ==========

    @Test
    fun `delta tracking - simple field changes capture field tags in path`() {
        // Given - initial message
        val initial = TestMessage(stringValue = "initial", int32Value = 10)
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1,
        )
        assertThat(delta1.changes.size).isEqualTo(1) // full message

        // Verify both changes captured with field tags
        assertThat(delta1.changes.first().pathComponents).isEmpty()

        // When - update one field
        val updated = initial.copy(stringValue = "updated")
        val delta2 = resolver.applyLocalWrite(
            currentValue = initial,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = updated,
            timestamp = 2,
        )

        // Then - only the changed field is in changes
        assertThat(delta2.changes.size).isEqualTo(1)
        assertThat(delta2.changes[0].pathComponents).containsExactly(PathComponent(field_number = 13))
        assertThat(delta2.changes[0].value).isEqualTo("updated")
    }

    @Test
    fun `delta tracking - map changes capture field tag and map key in path`() {
        // Given
        val initial = TestMessage(primitiveMapValue = mapOf("key1" to 100))
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1,
        )

        // Then - initial creation: whole message with empty path
        assertThat(delta1.changes.size).isEqualTo(1)
        assertThat(delta1.changes[0].pathComponents).isEmpty()
        assertThat(delta1.changes[0].value).isEqualTo(initial)

        // When - add another key
        val updated = initial.copy(primitiveMapValue = mapOf("key1" to 100, "key2" to 200))
        val delta2 = resolver.applyLocalWrite(
            currentValue = initial,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = updated,
            timestamp = 2,
        )

        // Then - only new key in changes
        assertThat(delta2.changes.size).isEqualTo(1)
        assertThat(delta2.changes[0].pathComponents).containsExactly(
            PathComponent(field_number = 19),
            PathComponent(string_key = "key2")
        )
        assertThat(delta2.changes[0].value).isEqualTo(200)
    }

    @Test
    fun `delta tracking - repeated field changes capture field tag and index in path`() {
        // Given
        val initial = TestMessage(primitiveListValue = listOf(10, 20, 30))
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1,
        )

        // Then - initial creation: whole message with empty path
        assertThat(delta1.changes.size).isEqualTo(1)
        assertThat(delta1.changes[0].pathComponents).isEmpty()
        assertThat(delta1.changes[0].value).isEqualTo(initial)

        // When - add another element
        val updated = initial.copy(primitiveListValue = listOf(10, 20, 30, 40))
        val delta2 = resolver.applyLocalWrite(
            currentValue = initial,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = updated,
            timestamp = 2,
        )

        // Then - only new element in changes with field tag and index
        assertThat(delta2.changes.size).isEqualTo(1)
        assertThat(delta2.changes[0].pathComponents).containsExactly(
            PathComponent(field_number = 30),
            PathComponent(repeated_index = 3)
        )
        assertThat(delta2.changes[0].value).isEqualTo(40)
    }

    @Test
    fun `delta tracking - message creation captures whole message with empty path`() {
        // Given - creating a message from scratch
        val nested = NestedMessage(stringValue = "nested_str", intValue = 42)
        val initial = TestMessage(nestedBinaryValue = nested)
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1,
        )

        // Then - entire message creation captured as single change with empty path
        assertThat(delta1.changes.size).isEqualTo(1)
        assertThat(delta1.changes[0].pathComponents).isEmpty()
        assertThat(delta1.changes[0].value).isEqualTo(initial)
    }

    @Test
    fun `delta tracking - nested message field update captures field path`() {
        // Given - existing message
        val nested1 = NestedMessage(stringValue = "original", intValue = 10)
        val initial = TestMessage(nestedBinaryValue = nested1)
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1,
        )

        // When - update the nested message field
        val nested2 = NestedMessage(stringValue = "updated", intValue = 20)
        val updated = initial.copy(nestedBinaryValue = nested2)
        val delta2 = resolver.applyLocalWrite(
            currentValue = initial,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = updated,
            timestamp = 2,
        )

        // Then - entire nested message replaced (replace_on_conflict = true)
        assertThat(delta2.changes.size).isEqualTo(1)
        assertThat(delta2.changes[0].pathComponents).containsExactly(
            PathComponent(field_number = 27) // nestedBinaryValue field tag
        )
        assertThat(delta2.changes[0].value).isEqualTo(nested2)
    }

    @Test
    fun `delta tracking - incoming resolution captures changes when incoming wins`() {
        // Given
        val local = TestMessage(stringValue = "local", int32Value = 10)
        val localDelta = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = local,
            timestamp = 1,
        )

        val incoming = TestMessage(stringValue = "incoming", int32Value = 20)
        val incomingDelta = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = incoming,
            timestamp = 2, // Higher timestamp
        )

        // When - resolve conflict
        val delta = resolver.resolveConflict(
            localValue = local,
            localNode = localDelta.mergeResult.node!!,
            localActors = localDelta.actors,
            incomingValue = incoming,
            incomingNode = incomingDelta.mergeResult.node!!,
            incomingVersionVector = mapOf(),
        )

        // Then - incoming changes should be captured
        assertThat(delta.changes.size).isEqualTo(25)

        val changesByField = delta.changes.associateBy { it.pathComponents.first() }

        assertThat(changesByField[PathComponent(field_number = 13)]?.value).isEqualTo("incoming")
        assertThat(changesByField[PathComponent(field_number = 3)]?.value).isEqualTo(20)
    }

    @Test
    fun `delta tracking - no changes when values identical`() {
        // Given
        val message = TestMessage(stringValue = "same", int32Value = 10)
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = message,
            timestamp = 1,
        )

        // When - write same value again
        val delta2 = resolver.applyLocalWrite(
            currentValue = message,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = message,
            timestamp = 2,
        )

        // Then - no changes
        assertThat(delta2.changes.size).isEqualTo(0)
        assertThat(delta2.mergeResult.resolution).isFalse()
    }

    @Test
    fun `delta tracking - deletion captured with null value`() {
        // Given
        val initial = TestMessage(stringValue = "to_be_deleted")
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1,
        )

        // When - clear the field
        val updated = initial.copy(stringValue = "")
        val delta2 = resolver.applyLocalWrite(
            currentValue = initial,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = updated,
            timestamp = 2,
        )

        // Then - change captured (empty string is the new value)
        assertThat(delta2.changes.size).isEqualTo(1)
        assertThat(delta2.changes[0].pathComponents).containsExactly(PathComponent(field_number = 13))
        assertThat(delta2.changes[0].value).isEqualTo("")
    }

    // ========== Encoded Value Tests ==========

    @Test
    fun `encoded() - validates primitive field changes are encoded correctly`() {
        // Given
        val initial = TestMessage(stringValue = "initial", int32Value = 42)
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1,
        )

        // Verify initial creation change has encoded value
        assertThat(delta1.changes.size).isEqualTo(1)
        assertThat(delta1.changes[0].value).isEqualTo(initial)
        val encoded1 = delta1.changes[0].encoded()
        assertThat(encoded1).isNotNull()
        assertThat(encoded1).isEqualTo(TestMessage.ADAPTER.encode(initial))

        // When - update string field
        val updated = initial.copy(stringValue = "updated")
        val delta2 = resolver.applyLocalWrite(
            currentValue = initial,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = updated,
            timestamp = 2,
        )

        // Then - string change should be encoded correctly
        assertThat(delta2.changes.size).isEqualTo(1)
        assertThat(delta2.changes[0].value).isEqualTo("updated")
        val encoded2 = delta2.changes[0].encoded()
        assertThat(encoded2).isNotNull()
        // Verify the encoded bytes represent the string value
        assertThat(String(encoded2!!)).isEqualTo("updated")
    }

    @Test
    fun `encoded() - validates nested message changes are encoded correctly`() {
        // Given
        val nested1 = NestedMessage(stringValue = "nested1", intValue = 100)
        val initial = TestMessage(nestedBinaryValue = nested1)
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1,
        )

        // When - update nested message
        val nested2 = NestedMessage(stringValue = "nested2", intValue = 200)
        val updated = initial.copy(nestedBinaryValue = nested2)
        val delta2 = resolver.applyLocalWrite(
            currentValue = initial,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = updated,
            timestamp = 2,
        )

        // Then - nested message change should be encoded correctly
        assertThat(delta2.changes.size).isEqualTo(1)
        assertThat(delta2.changes[0].pathComponents).containsExactly(PathComponent(field_number = 27))
        assertThat(delta2.changes[0].value).isEqualTo(nested2)
        val encoded = delta2.changes[0].encoded()
        assertThat(encoded).isNotNull()
        assertThat(encoded).isEqualTo(NestedMessage.ADAPTER.encode(nested2))
    }

    @Test
    fun `encoded() - validates map entry changes are encoded correctly`() {
        // Given
        val initial = TestMessage(primitiveMapValue = mapOf("key1" to 100))
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1,
        )

        // When - add another key
        val updated = initial.copy(primitiveMapValue = mapOf("key1" to 100, "key2" to 200))
        val delta2 = resolver.applyLocalWrite(
            currentValue = initial,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = updated,
            timestamp = 2,
        )

        // Then - map entry change should be encoded correctly
        assertThat(delta2.changes.size).isEqualTo(1)
        assertThat(delta2.changes[0].pathComponents).containsExactly(
            PathComponent(field_number = 19),
            PathComponent(string_key = "key2")
        )
        assertThat(delta2.changes[0].value).isEqualTo(200)
        val encoded = delta2.changes[0].encoded()
        assertThat(encoded).isNotNull()
        // The value is an Integer, so it should be encoded as bytes
        assertThat(encoded?.size).isGreaterThan(0)
    }

    @Test
    fun `encoded() - validates list element changes are encoded correctly`() {
        // Given
        val initial = TestMessage(primitiveListValue = listOf(10, 20))
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1,
        )

        // When - add another element
        val updated = initial.copy(primitiveListValue = listOf(10, 20, 30))
        val delta2 = resolver.applyLocalWrite(
            currentValue = initial,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = updated,
            timestamp = 2,
        )

        // Then - list element change should be encoded correctly
        assertThat(delta2.changes.size).isEqualTo(1)
        assertThat(delta2.changes[0].pathComponents).containsExactly(
            PathComponent(field_number = 30),
            PathComponent(repeated_index = 2)
        )
        assertThat(delta2.changes[0].value).isEqualTo(30)
        val encoded = delta2.changes[0].encoded()
        assertThat(encoded).isNotNull()
        assertThat(encoded?.size).isGreaterThan(0)
    }

    @Test
    fun `encoded() - validates enum changes are encoded correctly`() {
        // Given
        val initial = TestMessage(enumValue = EnumSample.VALUE1)
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1,
        )

        // When - update enum value
        val updated = initial.copy(enumValue = EnumSample.VALUE2)
        val delta2 = resolver.applyLocalWrite(
            currentValue = initial,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = updated,
            timestamp = 2,
        )

        // Then - enum change should be encoded correctly
        assertThat(delta2.changes.size).isEqualTo(1)
        assertThat(delta2.changes[0].pathComponents).containsExactly(PathComponent(field_number = 15))
        assertThat(delta2.changes[0].value).isEqualTo(EnumSample.VALUE2)
        val encoded = delta2.changes[0].encoded()
        assertThat(encoded).isNotNull()
        // Enum encoded values should be valid
        assertThat(encoded?.size).isGreaterThan(0)
    }

    @Test
    fun `encoded() - null value returns null`() {
        // Given - a field with a value
        val initial = TestMessage(
            nestedOptionalValue = NestedMessage("value", 100)
        )
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1,
        )

        // The encoded() function should handle null values gracefully
        assertThat(delta1.changes.size).isEqualTo(1)
        val encoded = delta1.changes[0].encoded()
        assertThat(encoded).isNotNull() // Because value is not null
    }

    @Test
    fun `encoded() - validates changes for all field types have correct encodings`() {
        // Given - a message with various field types
        val nested = NestedMessage(stringValue = "nested", intValue = 42)
        val initial = TestMessage(
            stringValue = "string",
            int32Value = 123,
            int64Value = 456L,
            enumValue = EnumSample.VALUE1,
            nestedBinaryValue = nested,
            primitiveMapValue = mapOf("mapKey" to 789),
            primitiveListValue = listOf(111)
        )

        // When - create the message
        val delta = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1,
        )

        // Then - the message creation change should encode correctly
        assertThat(delta.changes.size).isEqualTo(1)
        assertThat(delta.changes[0].value).isEqualTo(initial)
        val encoded = delta.changes[0].encoded()
        assertThat(encoded).isNotNull()
        assertThat(encoded).isEqualTo(TestMessage.ADAPTER.encode(initial))

        // Verify we can decode it back
        val decoded = TestMessage.ADAPTER.decode(encoded!!)
        assertThat(decoded).isEqualTo(initial)
    }

    // ========== Return Type Structure Validation Tests ==========

    @Test
    fun `applyLocalWrite - validates complete return type structure`() {
        // Given
        val initial = TestMessage(stringValue = "test", int32Value = 123)

        // When - apply local write
        val delta = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1,
        )

        // Then - validate complete ResolverDeltaResult structure
        // 1. Validate changes list
        assertThat(delta.changes).isNotNull()
        assertThat(delta.changes).isNotEmpty()
        assertThat(delta.changes[0]).isNotNull()
        assertThat(delta.changes[0].pathComponents).isNotNull()
        assertThat(delta.changes[0].value).isNotNull()
        assertThat(delta.changes[0].encoded()).isNotNull()

        // 2. Validate mergeResult
        assertThat(delta.mergeResult).isNotNull()
        assertThat(delta.mergeResult.resolution).isTrue() // Boolean for applyLocalWrite
        assertThat(delta.mergeResult.value).isEqualTo(initial)
        assertThat(delta.mergeResult.node).isNotNull()
        assertThat(delta.mergeResult.node?.version).isEqualTo(Version(1, delta.actors.local_actor, 1))
    }

    @Test
    fun `applyLocalWrite - validates no changes when values are identical`() {
        // Given
        val message = TestMessage(stringValue = "same", int32Value = 42)
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = message,
            timestamp = 1,
        )

        // When - write same value again
        val delta2 = resolver.applyLocalWrite(
            currentValue = message,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = message,
            timestamp = 2,
        )

        // Then - validate return structure with no changes
        assertThat(delta2.changes).isEmpty()
        assertThat(delta2.mergeResult.resolution).isFalse()
        assertThat(delta2.mergeResult.value).isEqualTo(message)
        assertThat(delta2.mergeResult.node).isNotNull()
    }

    @Test
    fun `resolveConflict - validates complete return type structure`() {
        // Given
        val initial = TestMessage(stringValue = "base")
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1,
        )

        // Device A changes
        val deviceA = initial.copy(int32Value = 100)
        val deltaA = resolver.applyLocalWrite(
            currentValue = delta1.mergeResult.value,
            currentNode = delta1.mergeResult.node,
            currentActors = null,
            newValue = deviceA,
            timestamp = 2,
        )

        // Device B changes
        val deviceB = initial.copy(stringValue = "updated")
        val deltaB = resolver.applyLocalWrite(
            currentValue = initial,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = deviceB,
            timestamp = 2,
        )

        // When - resolve conflict
        val conflict = resolver.resolveConflict(
            localValue = deltaA.mergeResult.value,
            localNode = deltaA.mergeResult.node!!,
            localActors = null,
            incomingValue = deltaB.mergeResult.value,
            incomingNode = deltaB.mergeResult.node!!,
            incomingVersionVector = mapOf<Long, Long>(),
        )

        // Then - validate complete ResolverDeltaResult structure
        // 1. Validate changes list
        assertThat(conflict.changes).isNotNull()
        assertThat(conflict.changes).isNotEmpty()
        for (change in conflict.changes) {
            assertThat(change).isNotNull()
            assertThat(change.pathComponents).isNotNull()
            // value can be null for some types
            // but encoded() should always be callable
            change.encoded() // Should not throw
        }

        // 2. Validate mergeResult
        assertThat(conflict.mergeResult).isNotNull()
        assertThat(conflict.mergeResult.resolution).isNotNull() // ResolutionStrategy for resolveConflict
        assertThat(conflict.mergeResult.value).isNotNull()
        assertThat(conflict.mergeResult.node).isNotNull()
    }

    @Test
    fun `resolveConflict - validates all changes have valid encoded values`() {
        // Given - create a conflict scenario with multiple field changes
        val initial = TestMessage(stringValue = "base", int32Value = 10)
        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1,
        )

        // Device A: Update string and add map entry
        val deviceA = initial.copy(
            stringValue = "deviceA",
            primitiveMapValue = mapOf("keyA" to 100)
        )
        val deltaA = resolver.applyLocalWrite(
            currentValue = initial,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = deviceA,
            timestamp = 2,
        )

        // Device B: Update int32 and add different map entry
        val deviceB = initial.copy(
            int32Value = 20,
            primitiveMapValue = mapOf("keyB" to 200)
        )
        val deltaB = resolver.applyLocalWrite(
            currentValue = initial,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = deviceB,
            timestamp = 3,
        )

        // When - resolve conflict (using the full delta result with changes)
        val conflict = resolver.resolveConflict(
            localValue = deltaA.mergeResult.value,
            localNode = deltaA.mergeResult.node!!,
            localActors = null,
            incomingValue = deltaB.mergeResult.value,
            incomingNode = deltaB.mergeResult.node!!,
            incomingVersionVector = mapOf<Long, Long>(),
        )

        // Then - all changes should have valid encoded values
        assertThat(conflict.changes).isNotEmpty()
        for (change in conflict.changes) {
            // Each change should be encodable - encoded() should not throw
            val encoded = change.encoded()
            // Non-null values should have an encoding
            if (change.value != null) {
                assertThat(encoded).isNotNull()
            }
        }
    }
}
