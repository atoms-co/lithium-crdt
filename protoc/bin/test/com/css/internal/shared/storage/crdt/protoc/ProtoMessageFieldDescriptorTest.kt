package com.css.internal.shared.storage.crdt.protoc

import com.css.android.internal.shared.storage.crdt.test.TestMessage
import com.css.internal.shared.storage.crdt.resolver.descriptor.CollectionType
import com.css.internal.shared.storage.crdt.resolver.descriptor.KeyType
import com.css.internal.shared.storage.crdt.resolver.descriptor.MessageFieldMergeStrategy
import com.css.internal.shared.storage.crdt.resolver.descriptor.ValueType
import com.css.internal.shared.storage.crdt.test.FieldDescriptorEncodingFixtures
import com.css.internal.shared.storage.crdt.test.FieldDescriptorEncodingFixtures.assertBytesEqual
import com.google.protobuf.Descriptors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProtoMessageFieldDescriptorTest {

    private val testMessageDescriptor: Descriptors.Descriptor = TestMessage.getDescriptor()

    @Test
    fun testPrimitiveField() {
        // Test a basic primitive field (stringValue)
        val fieldDescriptor = testMessageDescriptor.findFieldByName("stringValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        assertEquals(13, descriptor.tag)
        assertNull(descriptor.collectionType)
        assertEquals(ValueType.REQUIRED, descriptor.valueType)
        assertNull(descriptor.oneOfName)
    }

    @Test
    fun testOptionalPrimitiveField() {
        // Test optional primitive field (primitiveOptionalValue)
        val fieldDescriptor = testMessageDescriptor.findFieldByName("primitiveOptionalValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        assertEquals(24, descriptor.tag)
        assertNull(descriptor.collectionType)
        assertEquals(ValueType.OPTIONAL, descriptor.valueType)
        assertNull(descriptor.oneOfName)
    }

    @Test
    fun testMessageField() {
        // Test message field (nestedValue)
        val fieldDescriptor = testMessageDescriptor.findFieldByName("nestedValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        assertEquals(16, descriptor.tag)
        assertNull(descriptor.collectionType)
        assertEquals(ValueType.MESSAGE, descriptor.valueType)
        assertNull(descriptor.oneOfName)
    }

    @Test
    fun testOptionalMessageField() {
        // Test optional message field (nestedOptionalValue)
        val fieldDescriptor = testMessageDescriptor.findFieldByName("nestedOptionalValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        assertEquals(25, descriptor.tag)
        assertNull(descriptor.collectionType)
        assertEquals(ValueType.MESSAGE, descriptor.valueType)
        assertNull(descriptor.oneOfName)
    }

    @Test
    fun testMessageFieldWithReplaceOnConflict() {
        // Test message field with crdt_replace_on_conflict option (nestedBinaryValue)
        val fieldDescriptor = testMessageDescriptor.findFieldByName("nestedBinaryValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        assertEquals(27, descriptor.tag)
        assertNull(descriptor.collectionType)
        assertEquals(ValueType.MESSAGE, descriptor.valueType)
        assertNull(descriptor.oneOfName)
        assertEquals(MessageFieldMergeStrategy.REPLACE, descriptor.mergeStrategy)
    }

    @Test
    fun testOneOfField() {
        // Test oneof fields (oneOfValue1 and oneOfValue2)
        val fieldDescriptor1 = testMessageDescriptor.findFieldByName("oneOfValue1")
        val descriptor1 = ProtoMessageFieldDescriptor(fieldDescriptor1)

        assertEquals(17, descriptor1.tag)
        assertNull(descriptor1.collectionType)
        assertEquals(ValueType.REQUIRED, descriptor1.valueType)
        assertEquals("oneOfValue", descriptor1.oneOfName)

        val fieldDescriptor2 = testMessageDescriptor.findFieldByName("oneOfValue2")
        val descriptor2 = ProtoMessageFieldDescriptor(fieldDescriptor2)

        assertEquals(18, descriptor2.tag)
        assertEquals("oneOfValue", descriptor2.oneOfName)
        assertEquals(ValueType.MESSAGE, descriptor2.valueType)
    }

    @Test
    fun testPrimitiveMapField() {
        // Test map with primitive values (primitiveMapValue)
        val fieldDescriptor = testMessageDescriptor.findFieldByName("primitiveMapValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        assertEquals(19, descriptor.tag)
        assertTrue(descriptor.collectionType is CollectionType.Map)
        assertEquals(KeyType.STRING, (descriptor.collectionType as CollectionType.Map).keyType)
        assertEquals(ValueType.REQUIRED, descriptor.valueType)
        assertNull(descriptor.oneOfName)
    }

    @Test
    fun testNestedMessageMapField() {
        // Test map with message values (nestedMapValue)
        val fieldDescriptor = testMessageDescriptor.findFieldByName("nestedMapValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        assertEquals(20, descriptor.tag)
        assertTrue(descriptor.collectionType is CollectionType.Map)
        assertEquals(KeyType.STRING, (descriptor.collectionType as CollectionType.Map).keyType)
        assertEquals(ValueType.MESSAGE, descriptor.valueType)
    }

    @Test
    fun testRepeatedMessageField() {
        // Test repeated message field without ID (nestedListValue)
        val fieldDescriptor = testMessageDescriptor.findFieldByName("nestedListValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        assertEquals(22, descriptor.tag)
        assertEquals(CollectionType.Repeated, descriptor.collectionType)
        assertEquals(ValueType.MESSAGE, descriptor.valueType)
    }

    @Test
    fun testRepeatedMessageFieldWithId() {
        // Test repeated message field with ID (nestedListWithIdValue)
        val fieldDescriptor = testMessageDescriptor.findFieldByName("nestedListWithIdValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        assertEquals(23, descriptor.tag)
        assertTrue(descriptor.collectionType is CollectionType.RepeatedId)
        assertEquals(KeyType.STRING, (descriptor.collectionType as CollectionType.RepeatedId).mapType.keyType)
        assertEquals(ValueType.MESSAGE, descriptor.valueType)
        assertNotNull((descriptor.collectionType as CollectionType.RepeatedId).repeatedKeyTransformer)
    }

    @Test
    fun testMapKeyTypes() {
        // Test different map key types

        // String key
        val stringKeyMap = testMessageDescriptor.findFieldByName("primitiveMapValue")
        val stringKeyDescriptor = ProtoMessageFieldDescriptor(stringKeyMap)
        assertEquals(KeyType.STRING, (stringKeyDescriptor.collectionType as CollectionType.Map).keyType)
    }

    @Test
    fun testGetAndSetOperations() {
        // Test get and set operations on a field
        val fieldDescriptor = testMessageDescriptor.findFieldByName("stringValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        val testMessage = TestMessage.newBuilder().setStringValue("test value").build()

        // Test get
        val value = descriptor.get(testMessage)
        assertEquals("test value", value)

        // Test set
        val builder = TestMessage.newBuilder()
        val protoBuilder = ProtoMessageBuilder(builder)
        descriptor.set(protoBuilder, "new value")
        val newMessage = protoBuilder.build() as TestMessage
        assertEquals("new value", newMessage.stringValue)
    }

    @Test
    fun testEnumField() {
        // Test enum field (enumValue)
        val fieldDescriptor = testMessageDescriptor.findFieldByName("enumValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        assertEquals(15, descriptor.tag)
        assertNull(descriptor.collectionType)
        assertEquals(ValueType.REQUIRED, descriptor.valueType)
    }

    @Test
    fun testBooleanField() {
        // Test boolean field (boolValue)
        val fieldDescriptor = testMessageDescriptor.findFieldByName("boolValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        assertEquals(12, descriptor.tag)
        assertNull(descriptor.collectionType)
        assertEquals(ValueType.REQUIRED, descriptor.valueType)
    }

    @Test
    fun testInt32Field() {
        // Test int32 field (int32Value)
        val fieldDescriptor = testMessageDescriptor.findFieldByName("int32Value")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        assertEquals(3, descriptor.tag)
        assertNull(descriptor.collectionType)
        assertEquals(ValueType.REQUIRED, descriptor.valueType)
    }

    @Test
    fun testInt64Field() {
        // Test int64 field (int64Value)
        val fieldDescriptor = testMessageDescriptor.findFieldByName("int64Value")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        assertEquals(4, descriptor.tag)
        assertNull(descriptor.collectionType)
        assertEquals(ValueType.REQUIRED, descriptor.valueType)
    }

    @Test
    fun testToString() {
        // Test toString method
        val fieldDescriptor = testMessageDescriptor.findFieldByName("stringValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        val toString = descriptor.toString()
        assertNotNull(toString)
        // Should contain field name and type information
        assertEquals("stringValue = 13; (STRING)", toString)
    }

    @Test
    fun testEnumMapField() {
        // Test map with enum values (enumMapValue) - should be REQUIRED, not MESSAGE
        val fieldDescriptor = testMessageDescriptor.findFieldByName("enumMapValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        assertEquals(21, descriptor.tag)
        assertTrue(descriptor.collectionType is CollectionType.Map)
        assertEquals(KeyType.STRING, (descriptor.collectionType as CollectionType.Map).keyType)
        assertEquals(ValueType.REQUIRED, descriptor.valueType) // Enums are primitives, should be REQUIRED
        assertNull(descriptor.oneOfName)
    }

    @Test
    fun testOptionalValueTypeOnlyForPrimitiveOptionals() {
        // Verify that OPTIONAL value type ONLY occurs for primitive optional fields

        // Primitive optional - should be OPTIONAL
        val primitiveOptional = testMessageDescriptor.findFieldByName("primitiveOptionalValue")
        val primitiveOptionalDesc = ProtoMessageFieldDescriptor(primitiveOptional)
        assertEquals(ValueType.OPTIONAL, primitiveOptionalDesc.valueType)
        assertNull(primitiveOptionalDesc.collectionType)

        // Optional message - should be MESSAGE, not OPTIONAL
        val messageOptional = testMessageDescriptor.findFieldByName("nestedOptionalValue")
        val messageOptionalDesc = ProtoMessageFieldDescriptor(messageOptional)
        assertEquals(ValueType.MESSAGE, messageOptionalDesc.valueType)
        assertNull(messageOptionalDesc.collectionType)

        // Regular primitive - should be REQUIRED, not OPTIONAL
        val regularPrimitive = testMessageDescriptor.findFieldByName("stringValue")
        val regularPrimitiveDesc = ProtoMessageFieldDescriptor(regularPrimitive)
        assertEquals(ValueType.REQUIRED, regularPrimitiveDesc.valueType)
        assertNull(regularPrimitiveDesc.collectionType)

        // Regular message - should be MESSAGE, not OPTIONAL
        val regularMessage = testMessageDescriptor.findFieldByName("nestedValue")
        val regularMessageDesc = ProtoMessageFieldDescriptor(regularMessage)
        assertEquals(ValueType.MESSAGE, regularMessageDesc.valueType)
        assertNull(regularMessageDesc.collectionType)
    }

    @Test
    fun testMapFieldsNeverHaveOptionalValueType() {
        // Verify that map fields NEVER have OPTIONAL value type, regardless of value type

        // Map with primitive values - should be REQUIRED
        val primitiveMap = testMessageDescriptor.findFieldByName("primitiveMapValue")
        val primitiveMapDesc = ProtoMessageFieldDescriptor(primitiveMap)
        assertTrue(primitiveMapDesc.collectionType is CollectionType.Map)
        assertEquals(ValueType.REQUIRED, primitiveMapDesc.valueType)

        // Map with message values - should be MESSAGE
        val messageMap = testMessageDescriptor.findFieldByName("nestedMapValue")
        val messageMapDesc = ProtoMessageFieldDescriptor(messageMap)
        assertTrue(messageMapDesc.collectionType is CollectionType.Map)
        assertEquals(ValueType.MESSAGE, messageMapDesc.valueType)

        // Map with enum values - should be REQUIRED
        val enumMap = testMessageDescriptor.findFieldByName("enumMapValue")
        val enumMapDesc = ProtoMessageFieldDescriptor(enumMap)
        assertTrue(enumMapDesc.collectionType is CollectionType.Map)
        assertEquals(ValueType.REQUIRED, enumMapDesc.valueType)
    }

    @Test
    fun testMapEncodingProducesExpectedBytes() {
        // Test that encoding a map produces the expected wire format bytes
        val fieldDescriptor = testMessageDescriptor.findFieldByName("primitiveMapValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        // Encode the test input
        val encoded = descriptor.encoder(FieldDescriptorEncodingFixtures.MAP_STRING_INT32_INPUT)

        // Verify it matches the expected bytes
        assertBytesEqual(
            FieldDescriptorEncodingFixtures.MAP_STRING_INT32_EXPECTED_BYTES,
            encoded,
            "Map encoding did not match expected bytes"
        )
    }

    @Test
    fun testMapDecodingFromExpectedBytes() {
        // Test that decoding the expected bytes produces the correct map
        val fieldDescriptor = testMessageDescriptor.findFieldByName("primitiveMapValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        // Decode the expected bytes
        val decoded = descriptor.decoder(FieldDescriptorEncodingFixtures.MAP_STRING_INT32_EXPECTED_BYTES) as Map<*, *>

        // Verify it matches the input
        assertEquals(FieldDescriptorEncodingFixtures.MAP_STRING_INT32_INPUT, decoded)
    }

    @Test
    fun testRepeatedInt32EncodingProducesExpectedBytes() {
        // Test that encoding a repeated int32 field produces the expected wire format bytes
        val fieldDescriptor = testMessageDescriptor.findFieldByName("primitiveListValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        // Encode the test input
        val encoded = descriptor.encoder(FieldDescriptorEncodingFixtures.REPEATED_INT32_INPUT)

        // Verify it matches the expected bytes
        assertBytesEqual(
            FieldDescriptorEncodingFixtures.REPEATED_INT32_EXPECTED_BYTES,
            encoded,
            "Repeated int32 encoding did not match expected bytes"
        )
    }

    @Test
    fun testRepeatedInt32DecodingFromExpectedBytes() {
        // Test that decoding the expected bytes produces the correct list
        val fieldDescriptor = testMessageDescriptor.findFieldByName("primitiveListValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        // Decode the expected bytes
        val decoded = descriptor.decoder(FieldDescriptorEncodingFixtures.REPEATED_INT32_EXPECTED_BYTES) as List<*>

        // Verify it matches the input
        assertEquals(FieldDescriptorEncodingFixtures.REPEATED_INT32_INPUT, decoded)
    }

    @Test
    fun testEncodingRoundTrip() {
        // Test that encoding and decoding produces the same value for maps
        val mapFieldDescriptor = testMessageDescriptor.findFieldByName("primitiveMapValue")
        val mapDescriptor = ProtoMessageFieldDescriptor(mapFieldDescriptor)

        val mapEncoded = mapDescriptor.encoder(FieldDescriptorEncodingFixtures.MAP_STRING_INT32_INPUT)
        val mapDecoded = mapDescriptor.decoder(mapEncoded) as Map<*, *>
        assertEquals(FieldDescriptorEncodingFixtures.MAP_STRING_INT32_INPUT, mapDecoded)

        // Test that encoding and decoding produces the same value for repeated fields
        val listFieldDescriptor = testMessageDescriptor.findFieldByName("primitiveListValue")
        val listDescriptor = ProtoMessageFieldDescriptor(listFieldDescriptor)

        val listEncoded = listDescriptor.encoder(FieldDescriptorEncodingFixtures.REPEATED_INT32_INPUT)
        val listDecoded = listDescriptor.decoder(listEncoded) as List<*>
        assertEquals(FieldDescriptorEncodingFixtures.REPEATED_INT32_INPUT, listDecoded)
    }

    @Test
    fun testNonPackedRepeatedInt32Field() {
        // Test repeated int32 field with [packed = false] in proto3
        val fieldDescriptor = testMessageDescriptor.findFieldByName("nonPackedInt32")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)
        assertEquals(36, descriptor.tag)
        assertEquals(CollectionType.Repeated, descriptor.collectionType)
        // The expected encoding for [10, 20, 30] in unpacked form (each value with its own tag, tag 36)
        val expectedUnpackedBytes = byteArrayOf(
            0xA0.toByte(), 0x02, 0x0A, // tag 36, wire type 0, value 10
            0xA0.toByte(), 0x02, 0x14, // tag 36, wire type 0, value 20
            0xA0.toByte(), 0x02, 0x1E // tag 36, wire type 0, value 30
        )
        val input = listOf(10, 20, 30)
        val encoded = descriptor.encoder(input)
        assertBytesEqual(
            expectedUnpackedBytes,
            encoded,
            "nonPackedInt32 encoding (unpacked) did not match expected bytes"
        )
        val decoded = descriptor.decoder(expectedUnpackedBytes) as List<*>
        assertEquals(input, decoded)
    }

    @Test
    fun testTypeIdForPrimitiveField() {
        // Test typeId for primitive field (string)
        val fieldDescriptor = testMessageDescriptor.findFieldByName("stringValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        // typeId should be (JavaType, MergeStrategy) for primitives
        assertNotNull(descriptor.typeId)
    }

    @Test
    fun testTypeIdForMessageField() {
        // Test typeId for message field
        val fieldDescriptor = testMessageDescriptor.findFieldByName("nestedValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        // typeId should include the message type's full name
        assertNotNull(descriptor.typeId)
    }

    @Test
    fun testTypeIdForMapField() {
        // Test typeId for map field
        val fieldDescriptor = testMessageDescriptor.findFieldByName("primitiveMapValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        // typeId should include key type information
        assertNotNull(descriptor.typeId)
    }

    @Test
    fun testTypeIdForRepeatedField() {
        // Test typeId for repeated field with ID
        val fieldDescriptor = testMessageDescriptor.findFieldByName("nestedListWithIdValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        // typeId should include the ID tag
        assertNotNull(descriptor.typeId)
    }

    @Test
    fun testValueEncoderForString() {
        // Test valueEncoder for string values
        val fieldDescriptor = testMessageDescriptor.findFieldByName("stringValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        val encoded = descriptor.valueEncoder("test")
        assertNotNull(encoded)
        // Should be able to decode it back
        val decoded = descriptor.valueDecoder(encoded)
        assertEquals("test", decoded)
    }

    @Test
    fun testValueEncoderForInt32() {
        // Test valueEncoder for int32 values
        val fieldDescriptor = testMessageDescriptor.findFieldByName("int32Value")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        val encoded = descriptor.valueEncoder(42)
        assertNotNull(encoded)
        // Should be able to decode it back
        val decoded = descriptor.valueDecoder(encoded)
        assertEquals(42, decoded)
    }

    @Test
    fun testValueEncoderForBoolean() {
        // Test valueEncoder for boolean values
        val fieldDescriptor = testMessageDescriptor.findFieldByName("boolValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        val encoded = descriptor.valueEncoder(true)
        assertNotNull(encoded)
        // Should be able to decode it back
        val decoded = descriptor.valueDecoder(encoded)
        assertEquals(true, decoded)
    }

    @Test
    fun testValueDecoderForString() {
        // Test valueDecoder for string values
        val fieldDescriptor = testMessageDescriptor.findFieldByName("stringValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        val encoded = descriptor.valueEncoder("hello")
        val decoded = descriptor.valueDecoder(encoded)
        assertEquals("hello", decoded)
    }

    @Test
    fun testValueDecoderForInt64() {
        // Test valueDecoder for int64 values
        val fieldDescriptor = testMessageDescriptor.findFieldByName("int64Value")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        val encoded = descriptor.valueEncoder(123456789L)
        val decoded = descriptor.valueDecoder(encoded)
        assertEquals(123456789L, decoded)
    }

    @Test
    fun testMapCollectionTypeWithDefaultTombstoneOptions() {
        // Test that Map collectionType has default maxTombstone and null ttl
        val fieldDescriptor = testMessageDescriptor.findFieldByName("primitiveMapValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        assertTrue(descriptor.collectionType is CollectionType.Map)
        val mapType = descriptor.collectionType as CollectionType.Map
        assertEquals(KeyType.STRING, mapType.keyType)
        assertEquals(1024, mapType.maxTombstone) // Default value
        assertNull(mapType.tombstoneTtl)
    }

    @Test
    fun testMapCollectionTypeForNestedMessageMap() {
        // Test that Map collectionType is correctly set for nested message maps
        val fieldDescriptor = testMessageDescriptor.findFieldByName("nestedMapValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        assertTrue(descriptor.collectionType is CollectionType.Map)
        val mapType = descriptor.collectionType as CollectionType.Map
        assertEquals(KeyType.STRING, mapType.keyType)
        assertEquals(1024, mapType.maxTombstone)
        assertNull(mapType.tombstoneTtl)
    }

    @Test
    fun testRepeatedIdCollectionTypeWithCustomTombstoneOptions() {
        // Test that RepeatedId collectionType has custom maxTombstone and ttl
        val fieldDescriptor = testMessageDescriptor.findFieldByName("nestedListWithIdValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        assertTrue(descriptor.collectionType is CollectionType.RepeatedId)
        val repeatedIdType = descriptor.collectionType as CollectionType.RepeatedId

        // Verify idTag
        assertEquals(45, repeatedIdType.idTag)

        // Verify mapType has custom options
        assertEquals(KeyType.STRING, repeatedIdType.mapType.keyType)
        assertEquals(10, repeatedIdType.mapType.maxTombstone) // Custom value
        assertEquals(500L, repeatedIdType.mapType.tombstoneTtl) // Custom value

        // Verify transformer exists
        assertNotNull(repeatedIdType.repeatedKeyTransformer)
    }

    @Test
    fun testRepeatedCollectionTypeHasNoTombstoneOptions() {
        // Test that simple Repeated (not RepeatedId) doesn't have tombstone options
        val fieldDescriptor = testMessageDescriptor.findFieldByName("nestedListValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        assertEquals(CollectionType.Repeated, descriptor.collectionType)
    }

    @Test
    fun testEnumMapCollectionTypeWithDefaultOptions() {
        // Test that enum map has Map collectionType with default options
        val fieldDescriptor = testMessageDescriptor.findFieldByName("enumMapValue")
        val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

        assertTrue(descriptor.collectionType is CollectionType.Map)
        val mapType = descriptor.collectionType as CollectionType.Map
        assertEquals(KeyType.STRING, mapType.keyType)
        assertEquals(1024, mapType.maxTombstone)
        assertNull(mapType.tombstoneTtl)
    }
}
