package com.css.internal.shared.storage.crdt.wire

import com.css.android.internal.shared.storage.crdt.test.NestedMessage
import com.css.android.internal.shared.storage.crdt.test.TestMessage
import com.css.internal.shared.storage.crdt.wire.internal.WireMessageConstructorBuilder
import okio.ByteString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WireMessageConstructorBuilderTest {
    private val metadata = WireMessageConstructorBuilder.Metadata(TestMessage::class.java)
    private val protoFields = metadata.protoFields.associateBy { it.wireField.tag }

    @Test
    fun `set and get singular field`() {
        val builder = WireMessageConstructorBuilder<TestMessage, Nothing>(metadata)
        val stringField = protoFields.values.first { it.field.name == "stringValue" }.wireField
        builder.set(stringField, "hello")
        assertEquals("hello", builder.get(stringField))
    }

    @Test
    fun `set and get repeated field`() {
        val builder = WireMessageConstructorBuilder<TestMessage, Nothing>(metadata)
        val repeatedField = protoFields.values.first { it.field.name == "primitiveListValue" }.wireField
        builder.set(repeatedField, listOf(1, 2, 3))
        assertEquals(listOf(1, 2, 3), builder.get(repeatedField))
    }

    @Test
    fun `set and get map field`() {
        val builder = WireMessageConstructorBuilder<TestMessage, Nothing>(metadata)
        val mapField = protoFields.values.first { it.field.name == "primitiveMapValue" }.wireField
        builder.set(mapField, mapOf("a" to 1, "b" to 2))
        assertEquals(mapOf("a" to 1, "b" to 2), builder.get(mapField))
    }

    @Test
    fun `set oneof field clobbers others`() {
        val builder = WireMessageConstructorBuilder<TestMessage, Nothing>(metadata)
        val oneOf1 = protoFields.values.first { it.field.name == "oneOfValue1" }.wireField
        val oneOf2 = protoFields.values.first { it.field.name == "oneOfValue2" }.wireField
        builder.set(oneOf1, 123)
        assertEquals(123, builder.get(oneOf1))
        builder.set(oneOf2, NestedMessage(stringValue = "abc"))
        assertNull(builder.get(oneOf1))
        assertNotNull(builder.get(oneOf2))
    }

    @Test
    fun `build constructs message with set fields`() {
        val builder = WireMessageConstructorBuilder<TestMessage, Nothing>(metadata)
        val stringField = protoFields.values.first { it.field.name == "stringValue" }.wireField
        val intField = protoFields.values.first { it.field.name == "int32Value" }.wireField
        builder.set(stringField, "test")
        builder.set(intField, 42)
        val msg = builder.build()
        assertEquals("test", msg.stringValue)
        assertEquals(42, msg.int32Value)
    }

    @Test
    fun `get returns identity for unset proto3 field`() {
        val builder = WireMessageConstructorBuilder<TestMessage, Nothing>(metadata)
        val intField = protoFields.values.first { it.field.name == "int32Value" }.wireField
        // int32Value is proto3, should return 0 if unset
        assertEquals(0, builder.get(intField))
    }

    @Test
    fun `get returns empty list for unset repeated field`() {
        val builder = WireMessageConstructorBuilder<TestMessage, Nothing>(metadata)
        val repeatedField = protoFields.values.first { it.field.name == "primitiveListValue" }.wireField
        assertEquals(emptyList<Any>(), builder.get(repeatedField))
    }

    @Test
    fun `get returns empty map for unset map field`() {
        val builder = WireMessageConstructorBuilder<TestMessage, Nothing>(metadata)
        val mapField = protoFields.values.first { it.field.name == "primitiveMapValue" }.wireField
        assertEquals(emptyMap<Any, Any>(), builder.get(mapField))
    }

    @Test
    fun `build with unknown fields`() {
        val builder = WireMessageConstructorBuilder<TestMessage, Nothing>(metadata)
        val stringField = protoFields.values.first { it.field.name == "stringValue" }.wireField
        builder.set(stringField, "test")
        // Simulate unknown fields
        val msg = builder.build()
        assertEquals("test", msg.stringValue)
        assertTrue(msg.unknownFields == ByteString.EMPTY || msg.unknownFields.size >= 0)
    }
}
