package co.atoms.protobuf.crdt.protoc

import co.atoms.protobuf.crdt.data.options.FieldOptions.crdtIdField
import co.atoms.protobuf.crdt.data.options.FieldOptions.crdtIdFieldPath
import co.atoms.protobuf.crdt.data.options.FieldOptions.crdtMergeStrategy
import co.atoms.protobuf.crdt.data.options.FieldOptions.FieldMergeStrategy
import co.atoms.protobuf.crdt.data.options.FieldOptions.crdtMaxTombstones
import co.atoms.protobuf.crdt.data.options.FieldOptions.crdtTombstoneTtl
import co.atoms.protobuf.crdt.resolver.descriptor.CollectionType
import co.atoms.protobuf.crdt.resolver.descriptor.KeyType
import co.atoms.protobuf.crdt.resolver.descriptor.MessageBuilder
import co.atoms.protobuf.crdt.resolver.descriptor.MessageFieldDescriptor
import co.atoms.protobuf.crdt.resolver.descriptor.MessageFieldDescriptor.Companion.MAX_TOMBSTONE_DEFAULT
import co.atoms.protobuf.crdt.resolver.descriptor.MessageFieldMergeStrategy
import co.atoms.protobuf.crdt.resolver.descriptor.MessageFieldMergeStrategy.MERGE
import co.atoms.protobuf.crdt.resolver.descriptor.MessageFieldMergeStrategy.REPLACE
import co.atoms.protobuf.crdt.resolver.descriptor.ValueType
import co.atoms.protobuf.crdt.resolver.descriptor.ValueType.OPTIONAL
import co.atoms.protobuf.crdt.resolver.descriptor.ValueType.REQUIRED
import com.google.protobuf.ByteString
import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.Descriptors
import com.google.protobuf.Descriptors.EnumDescriptor
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType.BOOLEAN
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType.BYTE_STRING
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType.DOUBLE
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType.ENUM
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType.FLOAT
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType.INT
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType.LONG
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType.MESSAGE
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType.STRING
import com.google.protobuf.DynamicMessage
import com.google.protobuf.MapEntry
import com.google.protobuf.Message
import com.google.protobuf.Message.Builder
import com.google.protobuf.WireFormat
import java.io.ByteArrayOutputStream

internal class ProtoMessageFieldDescriptor(internal val fieldDescriptor: FieldDescriptor) :
    MessageFieldDescriptor<Message, MessageBuilder<Message, Builder>, Any> {
    override val tag: Int = fieldDescriptor.number
    val isMapField = fieldDescriptor.isMapField
    val isRepeatedField = !isMapField && fieldDescriptor.isRepeated

    val valueDescriptor =
        if (isMapField) {
            // For maps, get the key and value fields (fields 1 and 2 of the map entry message)
            // Cache these for use in get/set operations
            fieldDescriptor.messageType.findFieldByNumber(2)
        } else fieldDescriptor

    // Cache map entry field descriptors for performance
    var mapKeyField: FieldDescriptor? = null

    override val collectionType: CollectionType? =
        when {
            isMapField -> {
                // For maps, get the key and value fields (fields 1 and 2 of the map entry message)
                // Cache these for use in get/set operations
                createMapConfig(
                    keyDescriptor = fieldDescriptor.messageType.findFieldByNumber(1).also {
                        mapKeyField = it
                    },
                    fieldDescriptor = fieldDescriptor
                )
            }
            isRepeatedField -> {
                // For repeated message fields, check if there's an ID field path option
                val options = fieldDescriptor.options
                // Prefer the new path option, fall back to legacy single field option
                val idPath = options.getExtension(crdtIdFieldPath).takeIf { it.isNotEmpty() }
                    ?: options.getExtension(crdtIdField).takeIf { it > 0 }?.let { listOf(it) }

                if (idPath != null && idPath.isNotEmpty()) {
                    val fieldPath = resolveFieldPath(fieldDescriptor.messageType, idPath)
                    val leafField = fieldPath.last()

                    // Validate that the leaf ID field is not a message, map, or optional
                    require(leafField.javaType != MESSAGE) {
                        "List $tag id field path $idPath, message is not a valid key type: $leafField"
                    }
                    require(!leafField.isMapField) {
                        "List $tag id field path $idPath, map is not a valid key type: $leafField"
                    }

                    CollectionType.RepeatedId(
                        mapType = createMapConfig(
                            keyDescriptor = leafField,
                            fieldDescriptor = fieldDescriptor
                        ),
                        idPath = idPath,
                        repeatedKeyTransformer = { value ->
                            var current: Any = value
                            for (field in fieldPath) {
                                current = checkNotNull(
                                    (current as Message).getField(field)
                                ) { "Null value in ID path at field ${field.number}: $fieldDescriptor" }
                            }
                            current
                        }
                    )
                } else CollectionType.Repeated
            }
            else -> null
        }

    override val mergeStrategy: MessageFieldMergeStrategy =
        fieldDescriptor.options.run {
            if (hasExtension(crdtMergeStrategy)) {
                getExtension(crdtMergeStrategy).toMessageFieldMergeStrategy(javaType = valueDescriptor.javaType)
            } else {
                MERGE
            }
        }
    override val valueType: ValueType =
        when {
            valueDescriptor.javaType == MESSAGE -> ValueType.MESSAGE
            collectionType == null && valueDescriptor.toProto().proto3Optional -> OPTIONAL
            else -> REQUIRED
        }
    private val isMessageValue = valueDescriptor.javaType == MESSAGE

    private val enumValueDescriptor: EnumDescriptor? =
        if (valueDescriptor.javaType == ENUM) {
            valueDescriptor.enumType
        } else null

    // Determine oneOf name
    // In proto3, synthetic oneofs are used for optional fields
    // We check if it's a real oneof by verifying it has more than one field
    override val oneOfName: String? = fieldDescriptor.containingOneof?.takeIf { it.fieldCount > 1 }?.name

    @Suppress("UNCHECKED_CAST")
    override val valueEncoder: (Any) -> ByteArray =
        when (valueDescriptor.javaType) {
            FLOAT -> ProtoValueEncoders.FLOAT_ENCODER
            DOUBLE -> ProtoValueEncoders.DOUBLE_ENCODER
            BOOLEAN -> ProtoValueEncoders.BOOLEAN_ENCODER
            INT -> ProtoValueEncoders.INT_ENCODER
            LONG -> ProtoValueEncoders.LONG_ENCODER
            STRING -> ProtoValueEncoders.STRING_ENCODER
            BYTE_STRING -> ProtoValueEncoders.BYTE_STRING_ENCODER
            ENUM -> ProtoValueEncoders.ENUM_ENCODER
            MESSAGE -> ProtoValueEncoders.MESSAGE_ENCODER
        }
            as (Any) -> ByteArray

    @Suppress("UNCHECKED_CAST")
    override val valueDecoder: (ByteArray) -> Any =
        when (valueDescriptor.javaType) {
            FLOAT -> ProtoValueDecoders.FLOAT_DECODER
            DOUBLE -> ProtoValueDecoders.DOUBLE_DECODER
            BOOLEAN -> ProtoValueDecoders.BOOLEAN_DECODER
            INT -> ProtoValueDecoders.INT_DECODER
            LONG -> ProtoValueDecoders.LONG_DECODER
            STRING -> ProtoValueDecoders.STRING_DECODER
            BYTE_STRING -> ProtoValueDecoders.BYTE_STRING_DECODER
            ENUM -> ProtoValueDecoders.enumDecoder(
                enumDescriptor = enumValueDescriptor ?: error("No enum valueDescriptor")
            )
            MESSAGE ->
                ProtoValueDecoders.messageDecoder(
                    DynamicMessage.getDefaultInstance(valueDescriptor.messageType).parserForType
                )
        }

    private val isPacked: Boolean =
        isRepeatedField &&
            valueDescriptor.javaType in setOf(INT, LONG, FLOAT, DOUBLE, BOOLEAN, ENUM) &&
            (
                if (fieldDescriptor.options.hasPacked()) fieldDescriptor.options.packed
                else fieldDescriptor.file.toProto().syntax == "proto3"
                )

    override val encoder: (Any) -> ByteArray =
        when {
            isMapField -> { value ->
                @Suppress("UNCHECKED_CAST") val map = value as Map<*, *>
                // Encode map as repeated map entry messages (with field tags)
                // This matches Wire's encodeWithTag behavior for maps
                val buffer = ByteArrayOutputStream()
                val output = CodedOutputStream.newInstance(buffer)

                map.entries.forEach { (key, mapValue) ->
                    // Each map entry is encoded as a length-delimited message with tag
                    val entryDescriptor = fieldDescriptor.messageType
                    val entry =
                        DynamicMessage.newBuilder(entryDescriptor)
                            .setField(mapKeyField, key)
                            .apply {
                                if (mapValue != null) {
                                    setField(
                                        valueDescriptor,
                                        if (enumValueDescriptor != null && mapValue is Int) {
                                            enumValueDescriptor.findValueByNumber(mapValue)
                                        } else {
                                            mapValue
                                        },
                                    )
                                }
                            }
                            .build()

                    // Write tag + length-delimited entry
                    output.writeTag(
                        fieldDescriptor.number,
                        WireFormat.WIRETYPE_LENGTH_DELIMITED,
                    )
                    output.writeUInt32NoTag(entry.serializedSize)
                    entry.writeTo(output)
                }

                output.flush()
                buffer.toByteArray()
            }

            isRepeatedField -> { value ->
                @Suppress("UNCHECKED_CAST") val list = value as List<*>
                // Encode repeated values with field tags
                // This matches Wire's encodeWithTag behavior for lists
                val buffer = ByteArrayOutputStream()
                val output = CodedOutputStream.newInstance(buffer)
                if (isPacked) {
                    // Write tag and length for packed repeated field
                    output.writeTag(fieldDescriptor.number, WireFormat.WIRETYPE_LENGTH_DELIMITED)
                    val packedBuffer = ByteArrayOutputStream()
                    val packedOutput = CodedOutputStream.newInstance(packedBuffer)
                    list.forEach { item ->
                        if (item != null) {
                            // Write value without tag
                            when (valueDescriptor.javaType) {
                                INT -> packedOutput.writeInt32NoTag(item as Int)
                                LONG -> packedOutput.writeInt64NoTag(item as Long)
                                FLOAT -> packedOutput.writeFloatNoTag(item as Float)
                                DOUBLE -> packedOutput.writeDoubleNoTag(item as Double)
                                BOOLEAN -> packedOutput.writeBoolNoTag(item as Boolean)
                                ENUM -> packedOutput.writeEnumNoTag((item as Descriptors.EnumValueDescriptor).number)
                                else -> error("Unsupported packed type")
                            }
                        }
                    }
                    packedOutput.flush()
                    val packedBytes = packedBuffer.toByteArray()
                    output.writeUInt32NoTag(packedBytes.size)
                    output.writeRawBytes(packedBytes)
                } else {
                    list.forEach { item ->
                        if (item != null) {
                            when (valueDescriptor.javaType) {
                                INT -> {
                                    output.writeTag(
                                        fieldDescriptor.number,
                                        WireFormat.WIRETYPE_VARINT,
                                    )
                                    output.writeInt32NoTag(item as Int)
                                }

                                LONG -> {
                                    output.writeTag(
                                        fieldDescriptor.number,
                                        WireFormat.WIRETYPE_VARINT,
                                    )
                                    output.writeInt64NoTag(item as Long)
                                }

                                FLOAT -> {
                                    output.writeTag(
                                        fieldDescriptor.number,
                                        WireFormat.WIRETYPE_FIXED32,
                                    )
                                    output.writeFloatNoTag(item as Float)
                                }

                                DOUBLE -> {
                                    output.writeTag(
                                        fieldDescriptor.number,
                                        WireFormat.WIRETYPE_FIXED64,
                                    )
                                    output.writeDoubleNoTag(item as Double)
                                }

                                BOOLEAN -> {
                                    output.writeTag(
                                        fieldDescriptor.number,
                                        WireFormat.WIRETYPE_VARINT,
                                    )
                                    output.writeBoolNoTag(item as Boolean)
                                }

                                STRING -> {
                                    output.writeTag(
                                        fieldDescriptor.number,
                                        WireFormat.WIRETYPE_LENGTH_DELIMITED,
                                    )
                                    output.writeUInt32NoTag((item as String).toByteArray(Charsets.UTF_8).size)
                                    output.writeStringNoTag(item)
                                }

                                BYTE_STRING -> {
                                    val bytes = item as ByteString
                                    output.writeTag(
                                        fieldDescriptor.number,
                                        WireFormat.WIRETYPE_LENGTH_DELIMITED,
                                    )
                                    output.writeUInt32NoTag(bytes.size())
                                    output.writeBytesNoTag(bytes)
                                }

                                ENUM -> {
                                    output.writeTag(
                                        fieldDescriptor.number,
                                        WireFormat.WIRETYPE_VARINT,
                                    )
                                    output.writeEnumNoTag(
                                        (item as Descriptors.EnumValueDescriptor).number
                                    )
                                }

                                MESSAGE -> {
                                    val msg = item as Message
                                    output.writeTag(
                                        fieldDescriptor.number,
                                        WireFormat.WIRETYPE_LENGTH_DELIMITED,
                                    )
                                    output.writeUInt32NoTag(msg.serializedSize)
                                    msg.writeTo(output)
                                }
                            }
                        }
                    }
                }

                output.flush()
                buffer.toByteArray()
            }
            else -> valueEncoder
        }

    override val decoder: (ByteArray) -> Any =
        when {
            isMapField -> { bytes ->
                // Decode map entries from repeated length-delimited messages (with field tags)
                // This matches Wire's ProtoAdapter.decode() behavior
                val input = CodedInputStream.newInstance(bytes)
                val map = mutableMapOf<Any, Any?>()
                val entryDescriptor = fieldDescriptor.messageType

                while (!input.isAtEnd) {
                    // Read and verify tag
                    val tag = input.readTag()
                    require(WireFormat.getTagFieldNumber(tag) == fieldDescriptor.number) {
                        "Expected tag ${fieldDescriptor.number}, got ${WireFormat.getTagFieldNumber(tag)}"
                    }

                    // Each entry is a length-delimited message
                    val length = input.readUInt32()
                    val oldLimit = input.pushLimit(length)

                    val entry = DynamicMessage.parseFrom(entryDescriptor, input)
                    val key = entry.getField(mapKeyField)
                    val value =
                        if (entry.hasField(valueDescriptor)) {
                            entry.getField(valueDescriptor)
                        } else null

                    map[key] = value
                    input.popLimit(oldLimit)
                }

                map
            }

            isRepeatedField -> { bytes ->
                // Decode repeated values with field tags
                // This matches Wire's ProtoAdapter.decode() behavior
                val input = CodedInputStream.newInstance(bytes)
                val list = mutableListOf<Any>()

                while (!input.isAtEnd) {
                    // Read and skip tag
                    val tag = input.readTag()
                    require(WireFormat.getTagFieldNumber(tag) == fieldDescriptor.number) {
                        "Expected tag ${fieldDescriptor.number}, got ${WireFormat.getTagFieldNumber(tag)}"
                    }
                    if (isPacked && WireFormat.getTagWireType(tag) == WireFormat.WIRETYPE_LENGTH_DELIMITED) {
                        val length = input.readUInt32()
                        val oldLimit = input.pushLimit(length)
                        while (!input.isAtEnd) {
                            val item = when (valueDescriptor.javaType) {
                                INT -> input.readInt32()
                                LONG -> input.readInt64()
                                FLOAT -> input.readFloat()
                                DOUBLE -> input.readDouble()
                                BOOLEAN -> input.readBool()
                                ENUM -> enumValueDescriptor?.findValueByNumber(input.readEnum())
                                else -> null
                            }
                            list.add(item ?: error("Unsupported packed type"))
                        }
                        input.popLimit(oldLimit)
                    } else {
                        val item =
                            when (valueDescriptor.javaType) {
                                INT -> input.readInt32()
                                LONG -> input.readInt64()
                                FLOAT -> input.readFloat()
                                DOUBLE -> input.readDouble()
                                BOOLEAN -> input.readBool()
                                STRING -> {
                                    input.readUInt32() // read and discard length
                                    input.readString()
                                }

                                BYTE_STRING -> {
                                    input.readUInt32() // read and discard length
                                    input.readBytes()
                                }

                                ENUM -> enumValueDescriptor?.findValueByNumber(input.readEnum())
                                    ?: error("Unsupported enum type")
                                MESSAGE -> {
                                    val length = input.readUInt32()
                                    val oldLimit = input.pushLimit(length)
                                    val msg = DynamicMessage.parseFrom(valueDescriptor.messageType, input)
                                    input.popLimit(oldLimit)
                                    msg
                                }
                            }
                        list.add(item)
                    }
                }

                list
            }

            else -> valueDecoder
        }

    override val valueTypeId: Any = when (val valueJavaType = valueDescriptor.javaType) {
        ENUM -> valueDescriptor.enumType.fullName
        MESSAGE -> valueType to valueDescriptor.messageType.fullName
        INT,
        LONG,
        FLOAT,
        DOUBLE,
        BOOLEAN,
        STRING,
        BYTE_STRING -> valueJavaType
    } to mergeStrategy

    /**
     * Resolves a path of field numbers to a list of field descriptors.
     *
     * This supports nested ID fields where the ID is located within a nested message.
     * For example, a path of [1, 2] would resolve to [field 1 of root, field 2 of field 1's type].
     *
     * @param rootType the message type to start resolving from
     * @param path the list of field numbers forming the path
     * @return list of field descriptors for each step in the path
     * @throws IllegalArgumentException if any field in the path doesn't exist
     */
    private fun resolveFieldPath(
        rootType: Descriptors.Descriptor,
        path: List<Int>
    ): List<FieldDescriptor> {
        val result = mutableListOf<FieldDescriptor>()
        var currentType = rootType

        for ((index, fieldNum) in path.withIndex()) {
            val field = checkNotNull(currentType.findFieldByNumber(fieldNum)) {
                "ID field path element $fieldNum not found in message ${currentType.fullName} " +
                    "(path: $path, index: $index)"
            }
            result.add(field)

            // If not the last field and it's a message type, traverse into it
            if (index < path.size - 1) {
                require(field.javaType == MESSAGE) {
                    "ID field path element $fieldNum in ${currentType.fullName} is not a message type, " +
                        "cannot traverse further (path: $path, index: $index)"
                }
                currentType = field.messageType
            }
        }

        return result
    }

    override fun set(builder: MessageBuilder<Message, Builder>, value: Any?) {
        // Handle null values - clear the field instead of setting null
        if (value == null) {
            builder.setter.clearField(fieldDescriptor)
            return
        }

        if (isMapField) {
            // We know this is a map field
            val map = value as Map<*, *>
            if (map.isEmpty()) {
                builder.setter.clearField(fieldDescriptor)
                return
            }

            val entries = ArrayList<Any>(map.size)
            val entryDescriptor = fieldDescriptor.messageType

            for ((key, mapValue) in map) {
                // Create a new MapEntry message for each key-value pair using DynamicMessage
                val entryBuilder = DynamicMessage.newBuilder(entryDescriptor).setField(mapKeyField, key)

                // Only set the value field if it's not null
                if (mapValue != null) {
                    entryBuilder.setField(
                        valueDescriptor,
                        if (enumValueDescriptor != null && mapValue is Int) {
                            enumValueDescriptor.findValueByNumber(mapValue)
                        } else {
                            mapValue
                        },
                    )
                }
                entries.add(entryBuilder.build())
            }

            builder.setter.setField(fieldDescriptor, entries)
        } else {
            builder.setter.setField(fieldDescriptor, value)
        }
    }

    override fun get(message: Message): Any? {
        if (
            collectionType == null &&
            !message.hasField(fieldDescriptor) &&
            (valueType != REQUIRED || isMessageValue || oneOfName != null)
        ) {
            return null
        }

        val value = message.getField(fieldDescriptor)

        // For map fields, protobuf's getField() returns a List<MapEntry>, but our CRDT resolver
        // expects a Map. We need to convert the List representation to a Map.
        if (isMapField) {
            @Suppress("UNCHECKED_CAST") val entries = value as List<MapEntry<Any, Any>>
            if (entries.isEmpty()) {
                return emptyMap<Any, Any>()
            }

            val map = LinkedHashMap<Any, Any>(entries.size)
            for (entry in entries) {
                val mapValue = entry.value
                map[entry.key] = if (enumValueDescriptor != null && mapValue is Int) {
                    enumValueDescriptor.findValueByNumber(mapValue)
                } else {
                    mapValue
                }
            }
            return map
        }

        return value
    }

    override fun toString(): String {
        return "${fieldDescriptor.name} = $tag; (${fieldDescriptor.javaType})"
    }
}

private fun createMapConfig(keyDescriptor: FieldDescriptor, fieldDescriptor: FieldDescriptor) = CollectionType.Map(
    keyType = keyDescriptor.javaType.javaTypeToKeyType()
        ?: error("Key type not supported $keyDescriptor ${keyDescriptor.javaType}"),
    maxTombstone = fieldDescriptor.options.run {
        if (hasExtension(crdtMaxTombstones)) {
            getExtension(crdtMaxTombstones)
        } else {
            MAX_TOMBSTONE_DEFAULT
        }
    },
    tombstoneTtl = fieldDescriptor.options.run {
        if (hasExtension(crdtTombstoneTtl)) {
            getExtension(crdtTombstoneTtl)
        } else {
            null
        }
    }
)

fun FieldMergeStrategy.toMessageFieldMergeStrategy(javaType: JavaType) =
    when (this) {
        FieldMergeStrategy.MERGE -> MERGE
        FieldMergeStrategy.REPLACE -> REPLACE
        FieldMergeStrategy.COUNTER -> when (javaType) {
            INT -> MessageFieldMergeStrategy.INT_COUNTER
            LONG -> MessageFieldMergeStrategy.LONG_COUNTER
            else -> error("Unsupported counter type: $javaType")
        }
        FieldMergeStrategy.UNRECOGNIZED -> MERGE
    }

/**
 * Converts a protobuf Java type to a CRDT key type.
 *
 * Only primitive types that can serve as map keys or list IDs are supported.
 *
 * @return the corresponding key type, or null if the type cannot be used as a key
 */
private fun JavaType.javaTypeToKeyType(): KeyType? {
    return when (this) {
        STRING -> KeyType.STRING
        INT -> KeyType.INT
        LONG -> KeyType.LONG
        BOOLEAN -> KeyType.BOOL
        else -> null
    }
}
