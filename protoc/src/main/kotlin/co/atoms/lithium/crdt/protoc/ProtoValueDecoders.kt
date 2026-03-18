package co.atoms.lithium.crdt.protoc

import com.google.protobuf.ByteString
import com.google.protobuf.CodedInputStream
import com.google.protobuf.Descriptors
import com.google.protobuf.Message

/**
 * Static decoder functions for protobuf primitive types.
 *
 * These are the inverse operations of [ProtoValueEncoders], converting encoded ByteArrays
 * back to their original typed values.
 *
 * **Use Cases:**
 * - Reconstructing values from version history
 * - Deserializing transmitted deltas
 * - Reading values from persistent storage
 *
 * **Thread-safety:** All decoders are stateless and thread-safe.
 */
internal object ProtoValueDecoders {
    val FLOAT_DECODER: (ByteArray) -> Float = { bytes ->
        CodedInputStream.newInstance(bytes).readFloat()
    }

    val DOUBLE_DECODER: (ByteArray) -> Double = { bytes ->
        CodedInputStream.newInstance(bytes).readDouble()
    }

    val BOOLEAN_DECODER: (ByteArray) -> Boolean = { bytes ->
        CodedInputStream.newInstance(bytes).readBool()
    }

    val INT_DECODER: (ByteArray) -> Int = { bytes ->
        CodedInputStream.newInstance(bytes).readInt32()
    }

    val LONG_DECODER: (ByteArray) -> Long = { bytes ->
        CodedInputStream.newInstance(bytes).readInt64()
    }

    val STRING_DECODER: (ByteArray) -> String = { bytes ->
        ByteString.copyFrom(bytes).toStringUtf8()
    }

    val BYTE_STRING_DECODER: (ByteArray) -> ByteString = { bytes ->
        ByteString.copyFrom(bytes)
    }

    /**
     * Creates an enum decoder for a specific enum type.
     *
     * @param enumDescriptor the protobuf enum descriptor
     * @return decoder that converts encoded bytes to EnumValueDescriptor
     */
    fun enumDecoder(
        enumDescriptor: Descriptors.EnumDescriptor
    ): (ByteArray) -> Descriptors.EnumValueDescriptor = { bytes ->
        val enumNumber = CodedInputStream.newInstance(bytes).readEnum()
        enumDescriptor.findValueByNumber(enumNumber)
            ?: throw IllegalArgumentException("Unknown enum value $enumNumber for ${enumDescriptor.fullName}")
    }

    /**
     * Creates a message decoder for a specific message type.
     *
     * @param T the message type
     * @param parser the protobuf parser for the message type
     * @return decoder that converts encoded bytes to the message instance
     */
    fun <T : Message> messageDecoder(parser: com.google.protobuf.Parser<T>): (ByteArray) -> T = { bytes ->
        parser.parseFrom(bytes)
    }
}
