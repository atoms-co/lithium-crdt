package com.css.internal.shared.storage.crdt.protoc

import com.google.protobuf.ByteString
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.Descriptors
import com.google.protobuf.Message

/**
 * Static encoder functions for protobuf primitive types.
 *
 * These are reused across all field descriptors to avoid creating redundant lambda instances.
 */
internal object ProtoValueEncoders {
    val FLOAT_ENCODER: (Any) -> ByteArray = { value ->
        val buffer = ByteArray(4)
        val output = CodedOutputStream.newInstance(buffer)
        output.writeFloatNoTag(value as Float)
        buffer
    }

    val DOUBLE_ENCODER: (Double) -> ByteArray = { value ->
        val buffer = ByteArray(8)
        val output = CodedOutputStream.newInstance(buffer)
        output.writeDoubleNoTag(value)
        buffer
    }

    val BOOLEAN_ENCODER: (Boolean) -> ByteArray = { value ->
        val buffer = ByteArray(1)
        val output = CodedOutputStream.newInstance(buffer)
        output.writeBoolNoTag(value)
        buffer
    }

    val INT_ENCODER: (Int) -> ByteArray = { value ->
        val buffer = ByteArray(4)
        val output = CodedOutputStream.newInstance(buffer)
        output.writeInt32NoTag(value)
        buffer
    }

    val LONG_ENCODER: (Long) -> ByteArray = { value ->
        val buffer = ByteArray(8)
        val output = CodedOutputStream.newInstance(buffer)
        output.writeInt64NoTag(value)
        buffer
    }

    val STRING_ENCODER: (String) -> ByteArray = { value ->
        ByteString.copyFromUtf8(value).toByteArray()
    }

    val BYTE_STRING_ENCODER: (ByteString) -> ByteArray = { value ->
        value.toByteArray()
    }

    val ENUM_ENCODER: (Descriptors.EnumValueDescriptor) -> ByteArray = { value ->
        val enumNumber = value.number
        val buffer = ByteArray(4)
        val output = CodedOutputStream.newInstance(buffer)
        output.writeEnumNoTag(enumNumber)
        buffer
    }

    val MESSAGE_ENCODER: (Message) -> ByteArray = { value ->
        value.toByteArray()
    }
}
