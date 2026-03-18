package co.atoms.lithium.crdt.protoc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ProtoValueEncodersTest {

    @Nested
    inner class IntEncoder {

        @ParameterizedTest
        @ValueSource(ints = [0, 1, 127, 128, 16383, 16384, 2097151, 2097152, 268435455, 268435456, 597000000, Int.MAX_VALUE, -1, Int.MIN_VALUE])
        fun `round-trips all varint sizes`(value: Int) {
            val encoded = ProtoValueEncoders.INT_ENCODER(value)
            val decoded = CodedInputStream.newInstance(encoded).readInt32()
            assertEquals(value, decoded)
        }

        @Test
        fun `produces correct size for timestamp nanos`() {
            // These are the actual values that caused OutOfSpaceException in production
            val nanosValues = listOf(597_000_000, 609_000_000, 797_000_000)
            for (nanos in nanosValues) {
                val encoded = ProtoValueEncoders.INT_ENCODER(nanos)
                assertEquals(CodedOutputStream.computeInt32SizeNoTag(nanos), encoded.size)
                val decoded = CodedInputStream.newInstance(encoded).readInt32()
                assertEquals(nanos, decoded)
            }
        }

        @Test
        fun `buffer size matches computed size at varint boundaries`() {
            val boundaryValues = listOf(
                0 to 1,              // 1-byte varint
                127 to 1,            // max 1-byte
                128 to 2,            // min 2-byte
                16383 to 2,          // max 2-byte
                16384 to 3,          // min 3-byte
                2097151 to 3,        // max 3-byte
                2097152 to 4,        // min 4-byte
                268435455 to 4,      // max 4-byte
                268435456 to 5,      // min 5-byte (this is where the old bug triggered)
                Int.MAX_VALUE to 5,  // max positive 5-byte
            )
            for ((value, expectedSize) in boundaryValues) {
                val encoded = ProtoValueEncoders.INT_ENCODER(value)
                assertEquals(expectedSize, encoded.size, "Wrong size for value $value")
            }
        }
    }

    @Nested
    inner class LongEncoder {

        @ParameterizedTest
        @ValueSource(longs = [0, 1, 127, 128, 268435456, 597000000, 4294967296, 1771541073335548000, Long.MAX_VALUE, -1, Long.MIN_VALUE])
        fun `round-trips all varint sizes`(value: Long) {
            val encoded = ProtoValueEncoders.LONG_ENCODER(value)
            val decoded = CodedInputStream.newInstance(encoded).readInt64()
            assertEquals(value, decoded)
        }

        @Test
        fun `produces correct size for large timestamps`() {
            // Epoch millis and nanos that appear in CRDT version timestamps
            val timestamps = listOf(1771541073335L, 1771541073_335548000L)
            for (ts in timestamps) {
                val encoded = ProtoValueEncoders.LONG_ENCODER(ts)
                assertEquals(CodedOutputStream.computeInt64SizeNoTag(ts), encoded.size)
                val decoded = CodedInputStream.newInstance(encoded).readInt64()
                assertEquals(ts, decoded)
            }
        }
    }

    @Nested
    inner class EnumEncoder {

        @Test
        fun `round-trips all defined enum values`() {
            val testDescriptor = co.atoms.lithium.crdt.test.TestMessage.getDescriptor()
                .findFieldByName("enumValue").enumType

            for (enumValue in testDescriptor.values) {
                val encoded = ProtoValueEncoders.ENUM_ENCODER(enumValue)
                val decodedNumber = CodedInputStream.newInstance(encoded).readEnum()
                assertEquals(enumValue.number, decodedNumber)
            }
        }
    }

    @Nested
    inner class FixedSizeEncoders {

        @Test
        fun `FLOAT_ENCODER round-trips`() {
            val values = listOf(0.0f, 1.0f, -1.0f, Float.MAX_VALUE, Float.MIN_VALUE, 3.14f)
            for (value in values) {
                val encoded = ProtoValueEncoders.FLOAT_ENCODER(value)
                assertEquals(4, encoded.size)
                val decoded = CodedInputStream.newInstance(encoded).readFloat()
                assertEquals(value, decoded)
            }
        }

        @Test
        fun `DOUBLE_ENCODER round-trips`() {
            val values = listOf(0.0, 1.0, -1.0, Double.MAX_VALUE, Double.MIN_VALUE, 3.14159)
            for (value in values) {
                val encoded = ProtoValueEncoders.DOUBLE_ENCODER(value)
                assertEquals(8, encoded.size)
                val decoded = CodedInputStream.newInstance(encoded).readDouble()
                assertEquals(value, decoded)
            }
        }

        @Test
        fun `BOOLEAN_ENCODER round-trips`() {
            for (value in listOf(true, false)) {
                val encoded = ProtoValueEncoders.BOOLEAN_ENCODER(value)
                assertEquals(1, encoded.size)
                val decoded = CodedInputStream.newInstance(encoded).readBool()
                assertEquals(value, decoded)
            }
        }
    }
}
