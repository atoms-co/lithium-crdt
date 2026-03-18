package co.atoms.lithium.crdt.wire

import co.atoms.lithium.crdt.test.BadPathMessageLeafContainer
import co.atoms.lithium.crdt.test.BadPathMissingFieldContainer
import co.atoms.lithium.crdt.test.BadPathNonMessageContainer
import co.atoms.lithium.crdt.test.DeepIdContainer
import co.atoms.lithium.crdt.test.DeepIdEvent
import co.atoms.lithium.crdt.test.DeepIdInfo
import co.atoms.lithium.crdt.test.DeepIdWrapper
import co.atoms.lithium.crdt.test.NestedMessageWithId
import co.atoms.lithium.crdt.test.SinglePathIdContainer
import co.atoms.lithium.crdt.test.TestMessage
import co.atoms.lithium.crdt.test.TestEvent
import co.atoms.lithium.crdt.test.TestEventContainer
import co.atoms.lithium.crdt.test.TestEventInfo
import co.atoms.lithium.crdt.resolver.descriptor.CollectionType
import co.atoms.lithium.crdt.resolver.descriptor.KeyType
import co.atoms.lithium.crdt.resolver.descriptor.MessageFieldMergeStrategy
import co.atoms.lithium.crdt.resolver.descriptor.ValueType
import co.atoms.lithium.crdt.test.FieldDescriptorEncodingFixtures
import co.atoms.lithium.crdt.test.FieldDescriptorEncodingFixtures.assertBytesEqual
import co.atoms.lithium.crdt.wire.internal.WireFieldDescriptor
import co.atoms.lithium.crdt.wire.internal.WireMessageBuilder
import co.atoms.lithium.crdt.wire.internal.WireMessageConstructorBuilder
import co.atoms.lithium.crdt.wire.internal.annotationsFor
import co.atoms.lithium.crdt.wire.internal.methodsByName
import com.google.common.truth.Truth.assertThat
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.WireField
import com.squareup.wire.internal.FieldOrOneOfBinding
import okio.ByteString.Companion.toByteString
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Tests for WireFieldDescriptor to verify correct field metadata extraction.
 *
 * This test verifies that WireFieldDescriptor correctly implements MessageFieldDescriptor
 * by extracting field characteristics from Wire annotations and runtime adapters.
 *
 * These tests mirror ProtoMessageFieldDescriptorTest to ensure both implementations
 * conform to the same interface contract.
 */
class WireFieldDescriptorTest {
    private val provider = WireCrdtResolverProvider()

    private val adapter: com.squareup.wire.internal.RuntimeMessageAdapter<TestMessage, Nothing> =
        provider.getOrCreateWireRuntimeAdapter(TestMessage.ADAPTER)

    private fun getFieldDescriptor(fieldName: String): WireFieldDescriptor<TestMessage, Nothing> {
        val field = TestMessage::class.java.declaredFields.find { it.name == fieldName }
            ?: throw IllegalArgumentException("Field $fieldName not found")

        val wireField = field.getAnnotation(WireField::class.java)
            ?: throw IllegalArgumentException("Field $fieldName has no @WireField annotation")

        val binding = adapter.fields[wireField.tag]
            ?: throw IllegalArgumentException("No binding found for tag ${wireField.tag}")

        val methods = TestMessage::class.java.methodsByName()
        return WireFieldDescriptor(
            actual = binding,
            fieldAnnotations = methods.annotationsFor(field),
            fieldMessageFields = {
                @Suppress("UNCHECKED_CAST")
                provider.getOrCreateWireRuntimeAdapter<Nothing, Nothing>(
                    adapter = binding.singleAdapter
                ).fields as Map<Int, FieldOrOneOfBinding<Any, Any>>
            },
            parentMessageType = TestMessage::class.java,
            wireField = wireField,
        )
    }

    @Test
    fun testPrimitiveField() {
        // Test a basic primitive field (stringValue)
        val descriptor = getFieldDescriptor("stringValue")

        assertThat(descriptor.tag).isEqualTo(13)
        assertThat(descriptor.collectionType).isNull()
        assertThat(descriptor.valueType).isEqualTo(ValueType.REQUIRED)
        assertThat(descriptor.oneOfName).isNull()
    }

    @Test
    fun testOptionalPrimitiveField() {
        // Test optional primitive field (primitiveOptionalValue)
        val descriptor = getFieldDescriptor("primitiveOptionalValue")

        assertThat(descriptor.tag).isEqualTo(24)
        assertThat(descriptor.collectionType).isNull()
        assertThat(descriptor.valueType).isEqualTo(ValueType.OPTIONAL)
        assertThat(descriptor.oneOfName).isNull()
    }

    @Test
    fun testMessageField() {
        // Test message field (nestedValue)
        val descriptor = getFieldDescriptor("nestedValue")

        assertThat(descriptor.tag).isEqualTo(16)
        assertThat(descriptor.collectionType).isNull()
        assertThat(descriptor.valueType).isEqualTo(ValueType.MESSAGE)
        assertThat(descriptor.oneOfName).isNull()
    }

    @Test
    fun testOptionalMessageField() {
        // Test optional message field (nestedOptionalValue)
        val descriptor = getFieldDescriptor("nestedOptionalValue")

        assertThat(descriptor.tag).isEqualTo(25)
        assertThat(descriptor.collectionType).isNull()
        assertThat(descriptor.valueType).isEqualTo(ValueType.MESSAGE)
        assertThat(descriptor.oneOfName).isNull()
    }

    @Test
    fun testMessageFieldWithReplaceOnConflict() {
        // Test message field with crdt_replace_on_conflict option (nestedBinaryValue)
        val descriptor = getFieldDescriptor("nestedBinaryValue")

        assertThat(descriptor.tag).isEqualTo(27)
        assertThat(descriptor.collectionType).isNull()
        assertThat(descriptor.valueType).isEqualTo(ValueType.MESSAGE)
        assertThat(descriptor.mergeStrategy).isEqualTo(MessageFieldMergeStrategy.REPLACE)
        assertThat(descriptor.oneOfName).isNull()
    }

    @Test
    fun testOneOfField() {
        // Test oneof fields (oneOfValue1 and oneOfValue2)
        val descriptor1 = getFieldDescriptor("oneOfValue1")

        assertThat(descriptor1.tag).isEqualTo(17)
        assertThat(descriptor1.collectionType).isNull()
        assertThat(descriptor1.valueType).isEqualTo(ValueType.REQUIRED)
        assertThat(descriptor1.oneOfName).isEqualTo("oneOfValue")

        val descriptor2 = getFieldDescriptor("oneOfValue2")

        assertThat(descriptor2.tag).isEqualTo(18)
        assertThat(descriptor2.oneOfName).isEqualTo("oneOfValue")
        assertThat(descriptor2.valueType).isEqualTo(ValueType.MESSAGE)
    }

    @Test
    fun testPrimitiveMapField() {
        // Test map with primitive values (primitiveMapValue)
        val descriptor = getFieldDescriptor("primitiveMapValue")

        assertThat(descriptor.tag).isEqualTo(19)
        assertThat(descriptor.collectionType).isInstanceOf(CollectionType.Map::class.java)
        assertThat((descriptor.collectionType as CollectionType.Map).keyType).isEqualTo(KeyType.STRING)
        assertThat(descriptor.valueType).isEqualTo(ValueType.REQUIRED)
        assertThat(descriptor.oneOfName).isNull()
    }

    @Test
    fun testNestedMessageMapField() {
        // Test map with message values (nestedMapValue)
        val descriptor = getFieldDescriptor("nestedMapValue")

        assertThat(descriptor.tag).isEqualTo(20)
        assertThat(descriptor.collectionType).isInstanceOf(CollectionType.Map::class.java)
        assertThat((descriptor.collectionType as CollectionType.Map).keyType).isEqualTo(KeyType.STRING)
        assertThat(descriptor.valueType).isEqualTo(ValueType.MESSAGE)
    }

    @Test
    fun testRepeatedMessageField() {
        // Test repeated message field without ID (nestedListValue)
        val descriptor = getFieldDescriptor("nestedListValue")

        assertThat(descriptor.tag).isEqualTo(22)
        assertThat(descriptor.collectionType).isInstanceOf(CollectionType.Repeated::class.java)
        assertThat(descriptor.valueType).isEqualTo(ValueType.MESSAGE)
    }

    @Test
    fun testRepeatedMessageFieldWithId() {
        // Test repeated message field with ID (nestedListWithIdValue)
        val descriptor = getFieldDescriptor("nestedListWithIdValue")

        assertThat(descriptor.tag).isEqualTo(23)
        assertThat(descriptor.collectionType).isInstanceOf(CollectionType.RepeatedId::class.java)
        assertThat((descriptor.collectionType as CollectionType.RepeatedId).mapType.keyType).isEqualTo(KeyType.STRING)
        assertThat(descriptor.valueType).isEqualTo(ValueType.MESSAGE)
        assertThat(descriptor.collectionType.repeatedKeyTransformer).isNotNull()
    }

    @Test
    fun testRepeatedPrimitiveField() {
        // Test repeated primitive field (primitiveListValue)
        val descriptor = getFieldDescriptor("primitiveListValue")

        assertThat(descriptor.tag).isEqualTo(30)
        assertThat(descriptor.collectionType).isEqualTo(CollectionType.Repeated)
        assertThat(descriptor.valueType).isEqualTo(ValueType.REQUIRED)
    }

    @Test
    fun testRepeatedEnumField() {
        // Test repeated enum field (enumListValue)
        val descriptor = getFieldDescriptor("enumListValue")

        assertThat(descriptor.tag).isEqualTo(29)
        assertThat(descriptor.collectionType).isEqualTo(CollectionType.Repeated)
        assertThat(descriptor.valueType).isEqualTo(ValueType.REQUIRED)
    }

    @Test
    fun testMapKeyTypes() {
        // Test different map key types

        // String key
        val stringKeyDescriptor = getFieldDescriptor("primitiveMapValue")
        assertThat((stringKeyDescriptor.collectionType as CollectionType.Map).keyType).isEqualTo(KeyType.STRING)
    }

    @Test
    fun testEnumField() {
        // Test enum field (enumValue)
        val descriptor = getFieldDescriptor("enumValue")

        assertThat(descriptor.tag).isEqualTo(15)
        assertThat(descriptor.collectionType).isNull()
        assertThat(descriptor.valueType).isEqualTo(ValueType.REQUIRED)
    }

    @Test
    fun testBooleanField() {
        // Test boolean field (boolValue)
        val descriptor = getFieldDescriptor("boolValue")

        assertThat(descriptor.tag).isEqualTo(12)
        assertThat(descriptor.collectionType).isNull()
        assertThat(descriptor.valueType).isEqualTo(ValueType.REQUIRED)
    }

    @Test
    fun testInt32Field() {
        // Test int32 field (int32Value)
        val descriptor = getFieldDescriptor("int32Value")

        assertThat(descriptor.tag).isEqualTo(3)
        assertThat(descriptor.collectionType).isNull()
        assertThat(descriptor.valueType).isEqualTo(ValueType.REQUIRED)
    }

    @Test
    fun testInt64Field() {
        // Test int64 field (int64Value)
        val descriptor = getFieldDescriptor("int64Value")

        assertThat(descriptor.tag).isEqualTo(4)
        assertThat(descriptor.collectionType).isNull()
        assertThat(descriptor.valueType).isEqualTo(ValueType.REQUIRED)
    }

    @Test
    fun testToString() {
        // Test toString method
        val descriptor = getFieldDescriptor("stringValue")

        val toString = descriptor.toString()
        assertThat(toString).isNotNull()
        // Should contain field name
        assertThat(toString).contains("stringValue")
    }

    @Test
    fun testMapEncodingProducesExpectedBytes() {
        // Test that encoding a map produces the expected wire format bytes
        val descriptor = getFieldDescriptor("primitiveMapValue")

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
        val descriptor = getFieldDescriptor("primitiveMapValue")

        // Decode the expected bytes
        val decoded = descriptor.decoder(FieldDescriptorEncodingFixtures.MAP_STRING_INT32_EXPECTED_BYTES) as Map<*, *>

        // Verify it matches the input
        assertThat(decoded).isEqualTo(FieldDescriptorEncodingFixtures.MAP_STRING_INT32_INPUT)
    }

    @Test
    fun testRepeatedInt32EncodingProducesExpectedBytes() {
        // Test that encoding a repeated int32 field produces the expected wire format bytes
        val descriptor = getFieldDescriptor("primitiveListValue")

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
        val descriptor = getFieldDescriptor("primitiveListValue")

        // Decode the expected bytes
        val decoded = descriptor.decoder(FieldDescriptorEncodingFixtures.REPEATED_INT32_EXPECTED_BYTES) as List<*>

        // Verify it matches the input
        assertThat(decoded).isEqualTo(FieldDescriptorEncodingFixtures.REPEATED_INT32_INPUT)
    }

    @Test
    fun testNonPackedRepeatedInt32Field() {
        // Test repeated int32 field with [packed = false] in proto3
        val descriptor = getFieldDescriptor("nonPackedInt32")
        assertThat(descriptor.tag).isEqualTo(36)
        assertThat(descriptor.collectionType).isEqualTo(CollectionType.Repeated)
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
        assertThat(decoded).isEqualTo(input)
    }

    @Test
    fun testEncodingRoundTrip() {
        // Test that encoding and decoding produces the same value for maps
        val mapDescriptor = getFieldDescriptor("primitiveMapValue")

        val mapEncoded = mapDescriptor.encoder(FieldDescriptorEncodingFixtures.MAP_STRING_INT32_INPUT)
        val mapDecoded = mapDescriptor.decoder(mapEncoded) as Map<*, *>
        assertThat(mapDecoded).isEqualTo(FieldDescriptorEncodingFixtures.MAP_STRING_INT32_INPUT)

        // Test that encoding and decoding produces the same value for repeated fields
        val listDescriptor = getFieldDescriptor("primitiveListValue")

        val listEncoded = listDescriptor.encoder(FieldDescriptorEncodingFixtures.REPEATED_INT32_INPUT)
        val listDecoded = listDescriptor.decoder(listEncoded) as List<*>
        assertThat(listDecoded).isEqualTo(FieldDescriptorEncodingFixtures.REPEATED_INT32_INPUT)
    }

    @Test
    fun testEnumMapField() {
        // Test map with enum values (enumMapValue) - should be REQUIRED, not MESSAGE
        val descriptor = getFieldDescriptor("enumMapValue")

        assertThat(descriptor.tag).isEqualTo(21)
        assertThat(descriptor.collectionType).isInstanceOf(CollectionType.Map::class.java)
        assertThat((descriptor.collectionType as CollectionType.Map).keyType).isEqualTo(KeyType.STRING)
        assertThat(descriptor.valueType).isEqualTo(ValueType.REQUIRED) // Enums are primitives, should be REQUIRED
        assertThat(descriptor.oneOfName).isNull()
    }

    @Test
    fun testOptionalValueTypeOnlyForPrimitiveOptionals() {
        // Verify that OPTIONAL value type ONLY occurs for primitive optional fields

        // Primitive optional - should be OPTIONAL
        val primitiveOptionalDesc = getFieldDescriptor("primitiveOptionalValue")
        assertThat(primitiveOptionalDesc.valueType).isEqualTo(ValueType.OPTIONAL)
        assertThat(primitiveOptionalDesc.collectionType).isNull()

        // Optional message - should be MESSAGE, not OPTIONAL
        val messageOptionalDesc = getFieldDescriptor("nestedOptionalValue")
        assertThat(messageOptionalDesc.valueType).isEqualTo(ValueType.MESSAGE)
        assertThat(messageOptionalDesc.collectionType).isNull()

        // Regular primitive - should be REQUIRED, not OPTIONAL
        val regularPrimitiveDesc = getFieldDescriptor("stringValue")
        assertThat(regularPrimitiveDesc.valueType).isEqualTo(ValueType.REQUIRED)
        assertThat(regularPrimitiveDesc.collectionType).isNull()

        // Regular message - should be MESSAGE, not OPTIONAL
        val regularMessageDesc = getFieldDescriptor("nestedValue")
        assertThat(regularMessageDesc.valueType).isEqualTo(ValueType.MESSAGE)
        assertThat(regularMessageDesc.collectionType).isNull()
    }

    @Test
    fun testMapFieldsNeverHaveOptionalValueType() {
        // Verify that map fields NEVER have OPTIONAL value type, regardless of value type

        // Map with primitive values - should be REQUIRED
        val primitiveMapDesc = getFieldDescriptor("primitiveMapValue")
        assertThat(primitiveMapDesc.collectionType).isInstanceOf(CollectionType.Map::class.java)
        assertThat(primitiveMapDesc.valueType).isEqualTo(ValueType.REQUIRED)

        // Map with message values - should be MESSAGE
        val messageMapDesc = getFieldDescriptor("nestedMapValue")
        assertThat(messageMapDesc.collectionType).isInstanceOf(CollectionType.Map::class.java)
        assertThat(messageMapDesc.valueType).isEqualTo(ValueType.MESSAGE)

        // Map with enum values - should be REQUIRED
        val enumMapDesc = getFieldDescriptor("enumMapValue")
        assertThat(enumMapDesc.collectionType).isInstanceOf(CollectionType.Map::class.java)
        assertThat(enumMapDesc.valueType).isEqualTo(ValueType.REQUIRED)
    }

    @Test
    fun testGetAndSetOperations() {
        // Test get and set operations on a field
        val descriptor = getFieldDescriptor("stringValue")

        val testMessage = TestMessage(stringValue = "test value")

        // Test get
        val value = descriptor.get(testMessage)
        assertThat(value).isEqualTo("test value")

        // Test set
        val builder = WireMessageBuilder<TestMessage, Nothing>(WireMessageConstructorBuilder(
            messageBuilderMetadata = WireMessageConstructorBuilder.Metadata(TestMessage::class.java))
        )
        descriptor.set(builder, "new value")
        val newMessage = builder.build()
        assertThat(newMessage.stringValue).isEqualTo("new value")
    }

    @Test
    fun testTypeIdForPrimitiveField() {
        // Test typeId for primitive field (string)
        val descriptor = getFieldDescriptor("stringValue")

        // typeId should be (JavaType, MergeStrategy) for primitives
        assertThat(descriptor.typeId).isNotNull()
    }

    @Test
    fun testTypeIdForMessageField() {
        // Test typeId for message field
        val descriptor = getFieldDescriptor("nestedValue")

        // typeId should include the message type's full name
        assertThat(descriptor.typeId).isNotNull()
    }

    @Test
    fun testTypeIdForMapField() {
        // Test typeId for map field
        val descriptor = getFieldDescriptor("primitiveMapValue")

        // typeId should include key type information
        assertThat(descriptor.typeId).isNotNull()
    }

    @Test
    fun testTypeIdForRepeatedField() {
        // Test typeId for repeated field with ID
        val descriptor = getFieldDescriptor("nestedListWithIdValue")

        // typeId should include the ID tag
        assertThat(descriptor.typeId).isNotNull()
    }

    @Test
    fun testMapCollectionTypeWithDefaultTombstoneOptions() {
        // Test that Map collectionType has default maxTombstone and null ttl
        val descriptor = getFieldDescriptor("primitiveMapValue")

        assertThat(descriptor.collectionType).isInstanceOf(CollectionType.Map::class.java)
        val mapType = descriptor.collectionType as CollectionType.Map
        assertThat(mapType.keyType).isEqualTo(KeyType.STRING)
        assertThat(mapType.maxTombstone).isEqualTo(1024) // Default value
        assertThat(mapType.tombstoneTtl).isNull()
    }

    @Test
    fun testMapCollectionTypeForNestedMessageMap() {
        // Test that Map collectionType is correctly set for nested message maps
        val descriptor = getFieldDescriptor("nestedMapValue")

        assertThat(descriptor.collectionType).isInstanceOf(CollectionType.Map::class.java)
        val mapType = descriptor.collectionType as CollectionType.Map
        assertThat(mapType.keyType).isEqualTo(KeyType.STRING)
        assertThat(mapType.maxTombstone).isEqualTo(1024)
        assertThat(mapType.tombstoneTtl).isNull()
    }

    @Test
    fun testRepeatedIdCollectionTypeWithCustomTombstoneOptions() {
        // Test that RepeatedId collectionType has custom maxTombstone and ttl
        val descriptor = getFieldDescriptor("nestedListWithIdValue")

        assertThat(descriptor.collectionType).isInstanceOf(CollectionType.RepeatedId::class.java)
        val repeatedIdType = descriptor.collectionType as CollectionType.RepeatedId

        // Verify idPath
        assertThat(repeatedIdType.idPath).isEqualTo(listOf(45))

        // Verify mapType has custom options
        assertThat(repeatedIdType.mapType.keyType).isEqualTo(KeyType.STRING)
        assertThat(repeatedIdType.mapType.maxTombstone).isEqualTo(10) // Custom value
        assertThat(repeatedIdType.mapType.tombstoneTtl).isEqualTo(500L) // Custom value

        // Verify transformer exists
        assertThat(repeatedIdType.repeatedKeyTransformer).isNotNull()
    }

    @Test
    fun testRepeatedCollectionTypeHasNoTombstoneOptions() {
        // Test that simple Repeated (not RepeatedId) doesn't have tombstone options
        val descriptor = getFieldDescriptor("primitiveListValue")

        assertThat(descriptor.collectionType).isEqualTo(CollectionType.Repeated)
    }

    @Test
    fun testEnumMapCollectionTypeWithDefaultOptions() {
        // Test that enum map has Map collectionType with default options
        val descriptor = getFieldDescriptor("enumMapValue")

        assertThat(descriptor.collectionType).isInstanceOf(CollectionType.Map::class.java)
        val mapType = descriptor.collectionType as CollectionType.Map
        assertThat(mapType.keyType).isEqualTo(KeyType.STRING)
        assertThat(mapType.maxTombstone).isEqualTo(1024)
        assertThat(mapType.tombstoneTtl).isNull()
    }

    // ==========================================================================
    // STRING / BYTES valueEncoder + valueDecoder round-trip tests
    //
    // ProtoAdapter.encode() for LENGTH_DELIMITED scalars (STRING, BYTES) produces
    // raw bytes without a varint length prefix. valueDecoder must handle this by
    // decoding directly rather than delegating to ProtoAdapter.decode(), which
    // expects the prefix.
    // ==========================================================================

    @Test
    fun testStringValueEncoderDecoderRoundTrip() {
        val descriptor = getFieldDescriptor("stringValue")

        val original = "hello world"
        val encoded = descriptor.valueEncoder(original)
        val decoded = descriptor.valueDecoder(encoded)

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun testStringValueEncoderProducesRawUtf8() {
        val descriptor = getFieldDescriptor("stringValue")

        val original = "test"
        val encoded = descriptor.valueEncoder(original)

        assertThat(encoded).isEqualTo(original.toByteArray(Charsets.UTF_8))
    }

    @Test
    fun testStringValueDecoderHandlesEmptyString() {
        val descriptor = getFieldDescriptor("stringValue")

        val encoded = descriptor.valueEncoder("")
        val decoded = descriptor.valueDecoder(encoded)

        assertThat(decoded).isEqualTo("")
    }

    @Test
    fun testStringValueDecoderHandlesMultibyteUtf8() {
        val descriptor = getFieldDescriptor("stringValue")

        val original = "café ñ 你好 🌍"
        val encoded = descriptor.valueEncoder(original)
        val decoded = descriptor.valueDecoder(encoded)

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun testStringValueDecoderHandlesLongString() {
        val descriptor = getFieldDescriptor("stringValue")

        val original = "a]".repeat(5000)
        val encoded = descriptor.valueEncoder(original)
        val decoded = descriptor.valueDecoder(encoded)

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun testBytesValueEncoderDecoderRoundTrip() {
        val descriptor = getFieldDescriptor("bytesValue")

        val original = byteArrayOf(0x01, 0x02, 0xFF.toByte(), 0x00, 0x7F).toByteString()
        val encoded = descriptor.valueEncoder(original)
        val decoded = descriptor.valueDecoder(encoded)

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun testBytesValueDecoderHandlesEmptyBytes() {
        val descriptor = getFieldDescriptor("bytesValue")

        val original = byteArrayOf().toByteString()
        val encoded = descriptor.valueEncoder(original)
        val decoded = descriptor.valueDecoder(encoded)

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun testOneOfStringValueEncoderDecoderRoundTrip() {
        val descriptor = getFieldDescriptor("oneOfValue1")

        val original = "oneof string value"
        val encoded = descriptor.valueEncoder(original)
        val decoded = descriptor.valueDecoder(encoded)

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun testStringValueDecoderIsConsistentWithEncoder() {
        val descriptor = getFieldDescriptor("stringValue")

        val inputs = listOf(
            "simple",
            "",
            "with spaces and\ttabs",
            "emoji 🎉🚀",
            "newlines\nand\rreturns",
            "null\u0000byte",
            "Ü".repeat(1000),
        )
        for (input in inputs) {
            val encoded = descriptor.valueEncoder(input)
            val decoded = descriptor.valueDecoder(encoded)
            assertThat(decoded).isEqualTo(input)
        }
    }

    // ==========================================================================
    // Timestamp (Instant) valueEncoder + valueDecoder round-trip tests
    //
    // Wire maps google.protobuf.Timestamp to java.time.Instant. The singleAdapter
    // for Timestamp is a LENGTH_DELIMITED message type. valueEncoder produces the
    // raw message content (seconds + nanos fields), and valueDecoder must be able
    // to reconstruct the Instant from those bytes.
    // ==========================================================================

    @Test
    fun testTimestampValueEncoderDecoderRoundTrip() {
        val descriptor = getFieldDescriptor("timestamp_value")

        val original = Instant.ofEpochSecond(1710000000, 123_456_789)
        val encoded = descriptor.valueEncoder(original)
        val decoded = descriptor.valueDecoder(encoded)

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun testTimestampValueDecoderHandlesEpochZero() {
        val descriptor = getFieldDescriptor("timestamp_value")

        val original = Instant.EPOCH
        val encoded = descriptor.valueEncoder(original)
        val decoded = descriptor.valueDecoder(encoded)

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun testTimestampValueDecoderHandlesSecondsOnlyNoNanos() {
        val descriptor = getFieldDescriptor("timestamp_value")

        val original = Instant.ofEpochSecond(1700000000, 0)
        val encoded = descriptor.valueEncoder(original)
        val decoded = descriptor.valueDecoder(encoded)

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun testTimestampValueDecoderHandlesNanosOnly() {
        val descriptor = getFieldDescriptor("timestamp_value")

        val original = Instant.ofEpochSecond(0, 999_999_999)
        val encoded = descriptor.valueEncoder(original)
        val decoded = descriptor.valueDecoder(encoded)

        assertThat(decoded).isEqualTo(original)
    }

    // ==========================================================================
    // Nested ID Field Path Tests
    // ==========================================================================

    private fun <M : Message<M, B>, B : Message.Builder<M, B>> getFieldDescriptorForMessage(
        messageClass: Class<M>,
        adapter: ProtoAdapter<M>,
        fieldName: String
    ): WireFieldDescriptor<M, B> {
        val field = messageClass.declaredFields.find { it.name == fieldName }
            ?: throw IllegalArgumentException("Field $fieldName not found in ${messageClass.simpleName}")

        val wireField = field.getAnnotation(WireField::class.java)
            ?: throw IllegalArgumentException("Field $fieldName has no @WireField annotation")

        val runtimeAdapter = provider.getOrCreateWireRuntimeAdapter<M, B>(adapter)
        val binding = runtimeAdapter.fields[wireField.tag]
            ?: throw IllegalArgumentException("No binding found for tag ${wireField.tag}")

        val methods = messageClass.methodsByName()
        return WireFieldDescriptor(
            actual = binding,
            fieldAnnotations = methods.annotationsFor(field),
            fieldMessageFields = {
                @Suppress("UNCHECKED_CAST")
                provider.getOrCreateWireRuntimeAdapter<Nothing, Nothing>(
                    adapter = binding.singleAdapter
                ).fields as Map<Int, FieldOrOneOfBinding<Any, Any>>
            },
            parentMessageType = messageClass,
            wireField = wireField,
            resolverProvider = provider,
        )
    }

    @Test
    fun testNestedIdFieldPath_TwoLevels() {
        // Test nested ID path: event_info.event_id (fields 1, 1)
        val descriptor = getFieldDescriptorForMessage(
            TestEventContainer::class.java,
            TestEventContainer.ADAPTER,
            "events"
        )

        assertThat(descriptor.collectionType).isInstanceOf(CollectionType.RepeatedId::class.java)
        val repeatedIdType = descriptor.collectionType as CollectionType.RepeatedId

        // Verify idPath is [1, 1] (event_info.event_id)
        assertThat(repeatedIdType.idPath).isEqualTo(listOf(1, 1))

        // Verify mapType configuration
        assertThat(repeatedIdType.mapType.keyType).isEqualTo(KeyType.STRING)
        assertThat(repeatedIdType.mapType.maxTombstone).isEqualTo(100)

        // Verify transformer can extract nested ID
        val testEvent = TestEvent(
            event_info = TestEventInfo(event_id = "test-event-123"),
            event_type = "TEST"
        )
        val extractedId = repeatedIdType.repeatedKeyTransformer(testEvent)
        assertThat(extractedId).isEqualTo("test-event-123")
    }

    @Test
    fun testNestedIdFieldPath_ThreeLevels() {
        // Test deeply nested ID path: wrapper.info.deep_id (fields 1, 1, 1)
        val descriptor = getFieldDescriptorForMessage(
            DeepIdContainer::class.java,
            DeepIdContainer.ADAPTER,
            "events"
        )

        assertThat(descriptor.collectionType).isInstanceOf(CollectionType.RepeatedId::class.java)
        val repeatedIdType = descriptor.collectionType as CollectionType.RepeatedId

        // Verify idPath is [1, 1, 1] (wrapper.info.deep_id)
        assertThat(repeatedIdType.idPath).isEqualTo(listOf(1, 1, 1))

        // Verify mapType configuration
        assertThat(repeatedIdType.mapType.keyType).isEqualTo(KeyType.STRING)

        // Verify transformer can extract deeply nested ID
        val deepEvent = DeepIdEvent(
            wrapper = DeepIdWrapper(
                info = DeepIdInfo(deep_id = "deep-id-456")
            ),
            data_ = "test data"
        )
        val extractedId = repeatedIdType.repeatedKeyTransformer(deepEvent)
        assertThat(extractedId).isEqualTo("deep-id-456")
    }

    @Test
    fun testNestedIdFieldPath_KeyTransformerExtractsCorrectValue() {
        // Test that the key transformer correctly extracts IDs from multiple events
        val descriptor = getFieldDescriptorForMessage(
            TestEventContainer::class.java,
            TestEventContainer.ADAPTER,
            "events"
        )

        val repeatedIdType = descriptor.collectionType as CollectionType.RepeatedId

        // Create multiple events with different IDs
        val events = listOf(
            TestEvent(event_info = TestEventInfo(event_id = "event-001"), event_type = "A"),
            TestEvent(event_info = TestEventInfo(event_id = "event-002"), event_type = "B"),
            TestEvent(event_info = TestEventInfo(event_id = "event-003"), event_type = "C"),
        )

        // Extract IDs using the transformer
        val extractedIds = events.map { repeatedIdType.repeatedKeyTransformer(it) }
        assertThat(extractedIds).containsExactly("event-001", "event-002", "event-003").inOrder()
    }

    // ==========================================================================
    // Error Path Tests for crdt_id_field_path
    // ==========================================================================

    @Test
    fun testNestedIdFieldPath_NonexistentField() {
        // Field 99 doesn't exist in BadPathMissingField → error() in resolveFieldPath
        assertFailsWith<IllegalStateException> {
            getFieldDescriptorForMessage(
                BadPathMissingFieldContainer::class.java,
                BadPathMissingFieldContainer.ADAPTER,
                "events"
            )
        }
    }

    @Test
    fun testNestedIdFieldPath_NonMessageIntermediate() {
        // Path [1, 1] where field 1 is a string → require(field.isMessage) fails
        assertFailsWith<IllegalArgumentException> {
            getFieldDescriptorForMessage(
                BadPathNonMessageContainer::class.java,
                BadPathNonMessageContainer.ADAPTER,
                "events"
            )
        }
    }

    @Test
    fun testNestedIdFieldPath_MessageLeaf() {
        // Path [1] where field 1 is TestEventInfo (a message) → require(!leafField.isMessage) fails
        assertFailsWith<IllegalArgumentException> {
            getFieldDescriptorForMessage(
                BadPathMessageLeafContainer::class.java,
                BadPathMessageLeafContainer.ADAPTER,
                "events"
            )
        }
    }

    @Test
    fun testNestedIdFieldPath_SingleElementBackwardCompat() {
        // Single-element crdt_id_field_path should behave identically to legacy crdt_id_field
        val descriptor = getFieldDescriptorForMessage(
            SinglePathIdContainer::class.java,
            SinglePathIdContainer.ADAPTER,
            "items"
        )

        assertThat(descriptor.collectionType).isInstanceOf(CollectionType.RepeatedId::class.java)
        val repeatedIdType = descriptor.collectionType as CollectionType.RepeatedId

        // Verify idPath is [45] — same field number as the legacy crdt_id_field test
        assertThat(repeatedIdType.idPath).isEqualTo(listOf(45))

        // Verify key type and tombstone options match
        assertThat(repeatedIdType.mapType.keyType).isEqualTo(KeyType.STRING)
        assertThat(repeatedIdType.mapType.maxTombstone).isEqualTo(10)
        assertThat(repeatedIdType.mapType.tombstoneTtl).isEqualTo(500L)

        // Verify key extraction works
        val item = NestedMessageWithId(id = "item-abc", stringValue = "hello", intValue = 42)
        val extractedId = repeatedIdType.repeatedKeyTransformer(item)
        assertThat(extractedId).isEqualTo("item-abc")
    }

    @Test
    fun testNestedIdFieldPath_NullIntermediateAtRuntime() {
        // When an intermediate message field is null in Wire, the transformer should throw
        val descriptor = getFieldDescriptorForMessage(
            TestEventContainer::class.java,
            TestEventContainer.ADAPTER,
            "events"
        )

        val repeatedIdType = descriptor.collectionType as CollectionType.RepeatedId

        // Create a TestEvent with event_info unset (null in Wire)
        val eventWithNullInfo = TestEvent(event_type = "TEST")

        assertFailsWith<IllegalStateException> {
            repeatedIdType.repeatedKeyTransformer(eventWithNullInfo)
        }
    }
}
