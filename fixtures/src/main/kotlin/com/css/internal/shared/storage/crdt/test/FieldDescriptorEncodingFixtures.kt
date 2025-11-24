package com.css.internal.shared.storage.crdt.test

/**
 * Shared test fixtures for verifying consistent encoding/decoding between Wire and Protoc implementations.
 *
 * These fixtures ensure that both implementations produce and consume the same byte format for
 * map and repeated fields.
 */
object FieldDescriptorEncodingFixtures {

    /**
     * Test case for a map field with string keys and int32 values.
     *
     * Input: map<string, int32> with entries: {"a" -> 1, "b" -> 2}
     * Field tag: 19 (primitiveMapValue)
     *
     * Expected format for each entry:
     * - Tag (field 19, wire type 2 = LENGTH_DELIMITED): 0x9A 0x01 = (19 << 3) | 2
     * - Length of entry message
     * - Entry message containing:
     *   - Field 1 (key): tag 0x0A (field 1, wire type 2), length, "a" or "b"
     *   - Field 2 (value): tag 0x10 (field 2, wire type 0), value 1 or 2
     */
    val MAP_STRING_INT32_INPUT = mapOf("a" to 1, "b" to 2)

    /**
     * Expected bytes for MAP_STRING_INT32_INPUT
     *
     * Entry 1: {"a" -> 1}
     * - 0x9A, 0x01: tag for field 19, wire type 2 (LENGTH_DELIMITED)
     * - 0x05: length of entry (5 bytes: 3 for key field + 2 for value field)
     * - 0x0A: tag for field 1 (key), wire type 2
     * - 0x01: length of string "a" (1 byte)
     * - 0x61: "a" in ASCII
     * - 0x10: tag for field 2 (value), wire type 0 (VARINT)
     * - 0x01: value 1
     *
     * Entry 2: {"b" -> 2}
     * - 0x9A, 0x01: tag for field 19, wire type 2
     * - 0x05: length of entry (5 bytes: 3 for key field + 2 for value field)
     * - 0x0A: tag for field 1 (key), wire type 2
     * - 0x01: length of string "b" (1 byte)
     * - 0x62: "b" in ASCII
     * - 0x10: tag for field 2 (value), wire type 0
     * - 0x02: value 2
     */
    val MAP_STRING_INT32_EXPECTED_BYTES = byteArrayOf(
        // Entry 1: {"a" -> 1}
        0x9A.toByte(), 0x01, // tag 19, wire type 2 (LENGTH_DELIMITED)
        0x05, // length = 5
        0x0A, // key tag (field 1, wire type 2)
        0x01, // key length = 1
        0x61, // "a"
        0x10, // value tag (field 2, wire type 0)
        0x01, // value = 1
        // Entry 2: {"b" -> 2}
        0x9A.toByte(), 0x01, // tag 19, wire type 2
        0x05, // length = 5
        0x0A, // key tag (field 1, wire type 2)
        0x01, // key length = 1
        0x62, // "b"
        0x10, // value tag (field 2, wire type 0)
        0x02, // value = 2
    )

    /**
     * Test case for a repeated field with int32 values.
     *
     * Input: repeated int32 with values: [10, 20, 30]
     * Field tag: 30 (primitiveListValue)
     *
     * Expected format for each element:
     * - Tag (field 30, wire type 0 = VARINT): 0xF0 0x01 = (30 << 3) | 0
     * - Value (varint encoded)
     */
    val REPEATED_INT32_INPUT = listOf(10, 20, 30)

    /**
     * Expected bytes for REPEATED_INT32_INPUT (packed encoding)
     *
     * - 0xF2, 0x01: tag for field 30, wire type 2 (LENGTH_DELIMITED)
     * - 0x03: length = 3
     * - 0x0A, 0x14, 0x1E: values 10, 20, 30
     */
    val REPEATED_INT32_EXPECTED_BYTES = byteArrayOf(
        0xF2.toByte(), 0x01, // tag 30, wire type 2 (LENGTH_DELIMITED)
        0x03, // length = 3
        0x0A, // value = 10
        0x14, // value = 20
        0x1E // value = 30
    )

    /**
     * Test case for a repeated field with string values.
     *
     * Input: repeated string with values: ["hello", "world"]
     * We'll use field tag 30 for testing purposes
     *
     * Expected format for each element:
     * - Tag (field 30, wire type 2 = LENGTH_DELIMITED): 0xF2 0x01 = (30 << 3) | 2
     * - Length (varint)
     * - String bytes
     */
    val REPEATED_STRING_INPUT = listOf("hello", "world")

    /**
     * Expected bytes for REPEATED_STRING_INPUT
     *
     * Element 1: "hello"
     * - 0xF2, 0x01: tag for field 30, wire type 2 (LENGTH_DELIMITED)
     * - 0x05: length = 5
     * - 0x68, 0x65, 0x6C, 0x6C, 0x6F: "hello"
     *
     * Element 2: "world"
     * - 0xF2, 0x01: tag for field 30, wire type 2
     * - 0x05: length = 5
     * - 0x77, 0x6F, 0x72, 0x6C, 0x64: "world"
     */
    val REPEATED_STRING_EXPECTED_BYTES = byteArrayOf(
        // Element 1: "hello"
        0xF2.toByte(), 0x01, // tag 30, wire type 2
        0x05, // length = 5
        0x68, 0x65, 0x6C, 0x6C, 0x6F, // "hello"
        // Element 2: "world"
        0xF2.toByte(), 0x01, // tag 30, wire type 2
        0x05, // length = 5
        0x77, 0x6F, 0x72, 0x6C, 0x64, // "world"
    )

    /**
     * Expected bytes for nonPackedInt32 (unpacked repeated int32, tag 100)
     *
     * Input: repeated int32 with values: [10, 20, 30]
     * Field tag: 100 (nonPackedInt32)
     *
     * Each value is encoded as:
     * - Tag (field 100, wire type 0 = VARINT): 0x80 0x06 = (100 << 3) | 0
     * - Value (varint encoded)
     *
     * So the bytes are:
     * 0x80 0x06 0x0A (10)
     * 0x80 0x06 0x14 (20)
     * 0x80 0x06 0x1E (30)
     */
    val NON_PACKED_INT32_EXPECTED_BYTES = byteArrayOf(
        0x80.toByte(), 0x06, 0x0A, // tag 100, wire type 0, value 10
        0x80.toByte(), 0x06, 0x14, // tag 100, wire type 0, value 20
        0x80.toByte(), 0x06, 0x1E // tag 100, wire type 0, value 30
    )

    /**
     * Expected bytes for nonPackedInt32 (unpacked repeated int32, tag 36)
     *
     * Input: repeated int32 with values: [10, 20, 30]
     * Field tag: 36 (nonPackedInt32)
     *
     * Each value is encoded as:
     * - Tag (field 36, wire type 0 = VARINT): 0xA0 0x02 = (36 << 3) | 0
     * - Value (varint encoded)
     *
     * So the bytes are:
     * 0xA0 0x02 0x0A (10)
     * 0xA0 0x02 0x14 (20)
     * 0xA0 0x02 0x1E (30)
     */
    val NON_PACKED_INT32_TAG36_EXPECTED_BYTES = byteArrayOf(
        0xA0.toByte(), 0x02, 0x0A, // tag 36, wire type 0, value 10
        0xA0.toByte(), 0x02, 0x14, // tag 36, wire type 0, value 20
        0xA0.toByte(), 0x02, 0x1E // tag 36, wire type 0, value 30
    )

    /**
     * Helper function to format byte arrays for debugging.
     */
    fun ByteArray.toHexString(): String {
        return joinToString(" ") { byte -> "%02X".format(byte) }
    }

    /**
     * Helper to compare byte arrays with detailed error messages.
     */
    fun assertBytesEqual(expected: ByteArray, actual: ByteArray, message: String = "") {
        if (!expected.contentEquals(actual)) {
            val prefix = if (message.isNotEmpty()) "$message\n" else ""
            throw AssertionError(
                "${prefix}Byte arrays differ:\n" +
                    "Expected: ${expected.toHexString()}\n" +
                    "Actual:   ${actual.toHexString()}\n" +
                    "Expected length: ${expected.size}, Actual length: ${actual.size}"
            )
        }
    }
}
