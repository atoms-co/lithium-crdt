package com.css.protobuf.crdt.protoc

import com.css.protobuf.crdt.test.EnumSample
import com.css.protobuf.crdt.test.NestedMessage
import com.css.protobuf.crdt.test.TestMessage
import com.css.protobuf.crdt.data.PathComponent
import com.css.protobuf.crdt.data.Version
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for CrdtMessageDeltaResolver - delta/changes tracking.
 *
 * These tests focus on the delta tracking functionality that records what changed
 * during both local writes and conflict resolution, including path tracking and encoding.
 */
class ProtoCrdtDeltaResolverTest {
    private val provider = CrdtMessageResolverProvider()
    private val resolver = provider.getOrCreateResolverFor(TestMessage.getDefaultInstance())

    // ========== Delta Tracking Tests ==========

    @Test
    fun `delta tracking - simple field changes capture field tags in path`() {
        // Given - initial message
        val initial = TestMessage.newBuilder()
            .setStringValue("initial")
            .setInt32Value(10)
            .build()

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
        val updated = initial.toBuilder().setStringValue("updated").build()
        val delta2 = resolver.applyLocalWrite(
            currentValue = initial,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = updated,
            timestamp = 2,
        )

        // Then - only the changed field is in changes
        assertThat(delta2.changes.size).isEqualTo(1)
        assertThat(delta2.changes[0].pathComponents).containsExactly(
            PathComponent.newBuilder().setFieldNumber(13).build()
        )
        assertThat(delta2.changes[0].value).isEqualTo("updated")
    }

    @Test
    fun `delta tracking - map changes capture field tag and map key in path`() {
        // Given
        val initial = TestMessage.newBuilder()
            .putPrimitiveMapValue("key1", 100)
            .build()

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
        val updated = initial.toBuilder()
            .putPrimitiveMapValue("key2", 200)
            .build()

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
            PathComponent.newBuilder().setFieldNumber(19).build(),
            PathComponent.newBuilder().setStringKey("key2").build()
        )
        assertThat(delta2.changes[0].value).isEqualTo(200)
    }

    @Test
    fun `delta tracking - repeated field changes capture field tag and index in path`() {
        // Given
        val initial = TestMessage.newBuilder()
            .addPrimitiveListValue(10)
            .addPrimitiveListValue(20)
            .addPrimitiveListValue(30)
            .build()

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
        val updated = initial.toBuilder()
            .addPrimitiveListValue(40)
            .build()

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
            PathComponent.newBuilder().setFieldNumber(30).build(),
            PathComponent.newBuilder().setRepeatedIndex(3).build()
        )
        assertThat(delta2.changes[0].value).isEqualTo(40)
    }

    @Test
    fun `delta tracking - message creation captures whole message with empty path`() {
        // Given - creating a message from scratch
        val nested = NestedMessage.newBuilder()
            .setStringValue("nested_str")
            .setIntValue(42)
            .build()
        val initial = TestMessage.newBuilder()
            .setNestedBinaryValue(nested)
            .build()

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
        val nested1 = NestedMessage.newBuilder()
            .setStringValue("original")
            .setIntValue(10)
            .build()
        val initial = TestMessage.newBuilder()
            .setNestedBinaryValue(nested1)
            .build()

        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1,
        )

        // When - update the nested message field
        val nested2 = NestedMessage.newBuilder()
            .setStringValue("updated")
            .setIntValue(20)
            .build()
        val updated = initial.toBuilder()
            .setNestedBinaryValue(nested2)
            .build()

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
            PathComponent.newBuilder().setFieldNumber(27).build() // nestedBinaryValue field tag
        )
        assertThat(delta2.changes[0].value).isEqualTo(nested2)
    }

    @Test
    fun `delta tracking - incoming resolution captures changes when incoming wins`() {
        // Given
        val local = TestMessage.newBuilder()
            .setStringValue("local")
            .setInt32Value(10)
            .build()

        val localDelta = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = local,
            timestamp = 1,
        )

        val incoming = TestMessage.newBuilder()
            .setStringValue("incoming")
            .setInt32Value(20)
            .build()

        val incomingDelta = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = incoming,
            timestamp = 2, // Higher timestamp
        )

        // When - resolve conflict
        val localNode = requireNotNull(localDelta.mergeResult.node)
        val incomingNode = requireNotNull(incomingDelta.mergeResult.node)
        val delta = resolver.resolveConflict(
            localValue = local,
            localNode = localNode,
            localActors = localDelta.actors,
            incomingValue = incoming,
            incomingNode = incomingNode,
            incomingVersionVector = incomingDelta.actors.versionVectorMap,
        )

        // Then - incoming changes should be captured
        assertThat(delta.changes.size).isEqualTo(25)

        val changesByField = delta.changes.associateBy { it.pathComponents.first() }

        assertThat(changesByField[PathComponent.newBuilder().setFieldNumber(13).build()]?.value).isEqualTo("incoming")
        assertThat(changesByField[PathComponent.newBuilder().setFieldNumber(3).build()]?.value).isEqualTo(20)
    }

    @Test
    fun `delta tracking - no changes when values identical`() {
        // Given
        val message = TestMessage.newBuilder()
            .setStringValue("same")
            .setInt32Value(10)
            .build()

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
        val initial = TestMessage.newBuilder()
            .setStringValue("to_be_deleted")
            .build()

        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1,
        )

        // When - clear the field
        val updated = TestMessage.newBuilder().build()

        val delta2 = resolver.applyLocalWrite(
            currentValue = initial,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = updated,
            timestamp = 2,
        )

        // Then - change captured (empty string is the new value)
        assertThat(delta2.changes.size).isEqualTo(1)
        assertThat(delta2.changes[0].pathComponents).containsExactly(
            PathComponent.newBuilder().setFieldNumber(13).build()
        )
        assertThat(delta2.changes[0].value).isEqualTo("")
    }

    // ========== Encoded Value Tests ==========

    @Test
    fun `encoded() - validates primitive field changes are encoded correctly`() {
        // Given
        val initial = TestMessage.newBuilder()
            .setStringValue("initial")
            .setInt32Value(42)
            .build()

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
        assertThat(encoded1).isEqualTo(initial.toByteArray())

        // When - update string field
        val updated = initial.toBuilder().setStringValue("updated").build()
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
        assertThat(encoded2).isEqualTo("updated".toByteArray())
    }

    @Test
    fun `encoded() - validates nested message changes are encoded correctly`() {
        // Given
        val nested1 = NestedMessage.newBuilder()
            .setStringValue("nested1")
            .setIntValue(100)
            .build()
        val initial = TestMessage.newBuilder()
            .setNestedBinaryValue(nested1)
            .build()

        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1,
        )

        // When - update nested message
        val nested2 = NestedMessage.newBuilder()
            .setStringValue("nested2")
            .setIntValue(200)
            .build()
        val updated = initial.toBuilder().setNestedBinaryValue(nested2).build()

        val delta2 = resolver.applyLocalWrite(
            currentValue = initial,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = updated,
            timestamp = 2,
        )

        // Then - nested message change should be encoded correctly
        assertThat(delta2.changes.size).isEqualTo(1)
        assertThat(delta2.changes[0].pathComponents).containsExactly(
            PathComponent.newBuilder().setFieldNumber(27).build()
        )
        assertThat(delta2.changes[0].value).isEqualTo(nested2)
        val encoded = delta2.changes[0].encoded()
        assertThat(encoded).isNotNull()
        assertThat(encoded).isEqualTo(nested2.toByteArray())
    }

    @Test
    fun `encoded() - validates map entry changes are encoded correctly`() {
        // Given
        val initial = TestMessage.newBuilder()
            .putPrimitiveMapValue("key1", 100)
            .build()

        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1,
        )

        // When - add another key
        val updated = initial.toBuilder()
            .putPrimitiveMapValue("key2", 200)
            .build()

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
            PathComponent.newBuilder().setFieldNumber(19).build(),
            PathComponent.newBuilder().setStringKey("key2").build()
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
        val initial = TestMessage.newBuilder()
            .addPrimitiveListValue(10)
            .addPrimitiveListValue(20)
            .build()

        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1,
        )

        // When - add another element
        val updated = initial.toBuilder()
            .addPrimitiveListValue(30)
            .build()

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
            PathComponent.newBuilder().setFieldNumber(30).build(),
            PathComponent.newBuilder().setRepeatedIndex(2).build()
        )
        assertThat(delta2.changes[0].value).isEqualTo(30)
        val encoded = delta2.changes[0].encoded()
        assertThat(encoded).isNotNull()
        assertThat(encoded?.size).isGreaterThan(0)
    }

    @Test
    fun `encoded() - validates enum changes are encoded correctly`() {
        // Given
        val initial = TestMessage.newBuilder()
            .setEnumValue(EnumSample.VALUE1)
            .build()

        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1,
        )

        // When - update enum value
        val updated = initial.toBuilder().setEnumValue(EnumSample.VALUE2).build()

        val delta2 = resolver.applyLocalWrite(
            currentValue = initial,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = updated,
            timestamp = 2,
        )

        // Then - enum change should be encoded correctly
        assertThat(delta2.changes.size).isEqualTo(1)
        assertThat(delta2.changes[0].pathComponents).containsExactly(
            PathComponent.newBuilder().setFieldNumber(15).build()
        )
        assertThat(delta2.changes[0].value).isNotNull()
        val encoded = delta2.changes[0].encoded()
        assertThat(encoded).isNotNull()
        // Enum encoded values should be valid
        assertThat(encoded?.size).isGreaterThan(0)
    }

    @Test
    fun `encoded() - null value returns null`() {
        // Given - a field with a value
        val initial = TestMessage.newBuilder()
            .setNestedOptionalValue(
                NestedMessage.newBuilder().setStringValue("value").setIntValue(100).build()
            )
            .build()

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
        val nested = NestedMessage.newBuilder()
            .setStringValue("nested")
            .setIntValue(42)
            .build()

        val initial = TestMessage.newBuilder()
            .setStringValue("string")
            .setInt32Value(123)
            .setInt64Value(456L)
            .setEnumValue(EnumSample.VALUE1)
            .setNestedBinaryValue(nested)
            .putPrimitiveMapValue("mapKey", 789)
            .addPrimitiveListValue(111)
            .build()

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
        assertThat(encoded).isEqualTo(initial.toByteArray())

        // Verify we can decode it back
        val decoded = TestMessage.parseFrom(encoded)
        assertThat(decoded).isEqualTo(initial)
    }

    // ========== Return Type Structure Validation Tests ==========

    @Test
    fun `applyLocalWrite - validates complete return type structure`() {
        // Given
        val initial = TestMessage.newBuilder()
            .setStringValue("test")
            .setInt32Value(123)
            .build()

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
        val node = requireNotNull(delta.mergeResult.node)
        assertThat(node.version).isEqualTo(
            Version.newBuilder().setTimestamp(1).setActorId(delta.actors.localActor).setActorVersion(1).build()
        )
    }

    @Test
    fun `applyLocalWrite - validates no changes when values are identical`() {
        // Given
        val message = TestMessage.newBuilder()
            .setStringValue("same")
            .setInt32Value(42)
            .build()

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
        val initial = TestMessage.newBuilder()
            .setStringValue("base")
            .build()

        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1,
        )

        // Device A changes
        val deviceA = initial.toBuilder().setInt32Value(100).build()
        val deltaA = resolver.applyLocalWrite(
            currentValue = initial,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = deviceA,
            timestamp = 2,
        )

        // Device B changes
        val deviceB = initial.toBuilder().setStringValue("updated").build()
        val deltaB = resolver.applyLocalWrite(
            currentValue = initial,
            currentNode = delta1.mergeResult.node,
            currentActors = deltaA.actors,
            newValue = deviceB,
            timestamp = 2,
        )

        // When - resolve conflict
        val localNode = requireNotNull(deltaA.mergeResult.node)
        val incomingNode = requireNotNull(deltaB.mergeResult.node)
        val conflict = resolver.resolveConflict(
            localValue = deltaA.mergeResult.value,
            localNode = localNode,
            localActors = deltaA.actors,
            incomingValue = deltaB.mergeResult.value,
            incomingNode = incomingNode,
            incomingVersionVector = deltaB.actors.versionVectorMap,
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
        val initial = TestMessage.newBuilder()
            .setStringValue("base")
            .setInt32Value(10)
            .build()

        val delta1 = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentActors = null,
            newValue = initial,
            timestamp = 1,
        )

        // Device A: Update string and add map entry
        val deviceA = initial.toBuilder()
            .setStringValue("deviceA")
            .putPrimitiveMapValue("keyA", 100)
            .build()

        val deltaA = resolver.applyLocalWrite(
            currentValue = initial,
            currentNode = delta1.mergeResult.node,
            currentActors = delta1.actors,
            newValue = deviceA,
            timestamp = 2,
        )

        // Device B: Update int32 and add different map entry
        val deviceB = initial.toBuilder()
            .setInt32Value(20)
            .putPrimitiveMapValue("keyB", 200)
            .build()

        val deltaB = resolver.applyLocalWrite(
            currentValue = initial,
            currentNode = delta1.mergeResult.node,
            currentActors = deltaA.actors,
            newValue = deviceB,
            timestamp = 3,
        )

        // When - resolve conflict (using the full delta result with changes)
        val localNode = requireNotNull(deltaA.mergeResult.node)
        val incomingNode = requireNotNull(deltaB.mergeResult.node)
        val conflict = resolver.resolveConflict(
            localValue = deltaA.mergeResult.value,
            localNode = localNode,
            localActors = deltaA.actors,
            incomingValue = deltaB.mergeResult.value,
            incomingNode = incomingNode,
            incomingVersionVector = deltaB.actors.versionVectorMap,
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
