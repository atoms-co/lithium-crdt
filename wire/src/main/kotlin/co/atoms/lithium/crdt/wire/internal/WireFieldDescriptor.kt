package co.atoms.lithium.crdt.wire.internal

import co.atoms.lithium.crdt.data.options.CrdtIdFieldOption
import co.atoms.lithium.crdt.data.options.CrdtIdFieldPathOption
import co.atoms.lithium.crdt.data.options.CrdtMaxTombstonesOption
import co.atoms.lithium.crdt.data.options.CrdtMergeStrategyOption
import co.atoms.lithium.crdt.data.options.CrdtTombstoneTtlOption
import co.atoms.lithium.crdt.data.options.FieldMergeStrategy
import co.atoms.lithium.crdt.wire.WireCrdtResolverProvider
import co.atoms.lithium.crdt.resolver.descriptor.CollectionType
import co.atoms.lithium.crdt.resolver.descriptor.KeyType
import co.atoms.lithium.crdt.resolver.descriptor.MessageBuilder
import co.atoms.lithium.crdt.resolver.descriptor.MessageFieldDescriptor
import co.atoms.lithium.crdt.resolver.descriptor.MessageFieldDescriptor.Companion.MAX_TOMBSTONE_DEFAULT
import co.atoms.lithium.crdt.resolver.descriptor.MessageFieldMergeStrategy
import co.atoms.lithium.crdt.resolver.descriptor.MessageFieldMergeStrategy.INT_COUNTER
import co.atoms.lithium.crdt.resolver.descriptor.MessageFieldMergeStrategy.LONG_COUNTER
import co.atoms.lithium.crdt.resolver.descriptor.MessageFieldMergeStrategy.MERGE
import co.atoms.lithium.crdt.resolver.descriptor.MessageFieldMergeStrategy.REPLACE
import co.atoms.lithium.crdt.resolver.descriptor.ValueType
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ProtoReader
import com.squareup.wire.ProtoWriter
import com.squareup.wire.WireField
import com.squareup.wire.internal.FieldOrOneOfBinding
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.reflect.KClass
import okio.Buffer
import okio.ByteString.Companion.toByteString

internal class WireFieldDescriptor<M : Message<M, B>, B : Message.Builder<M, B>>(
    private val actual: FieldOrOneOfBinding<M, B>,
    fieldAnnotations: Array<Annotation>,
    fieldMessageFields: () -> Map<Int, FieldOrOneOfBinding<Any, Any>>,
    private val parentMessageType: Class<M>,
    private val wireField: WireField,
    private val resolverProvider: WireCrdtResolverProvider? = null,
) : MessageFieldDescriptor<M, MessageBuilder<M, WireMessageConstructorBuilder<M, B>>, Any> {
    override val oneOfName = wireField.oneofName.takeIf { it.isNotBlank() }
    override val tag: Int = actual.tag

    override val collectionType = if (actual.isMap) {
        CollectionType.Map(
            keyType = actual.keyAdapter.type?.toKeyType()
                ?: error("Unsupported map key type: ${actual.keyAdapter.type}"),
            maxTombstone = fieldAnnotations.firstNotNullOfOrNull<CrdtMaxTombstonesOption>()?.value
                ?: MAX_TOMBSTONE_DEFAULT,
            tombstoneTtl = fieldAnnotations.firstNotNullOfOrNull<CrdtTombstoneTtlOption>()?.value
        )
    } else if (actual.label.isRepeated) {
        // Prefer the new path option, fall back to legacy single field option
        val idPath = if (actual.isMessage) {
            fieldAnnotations.firstNotNullOfOrNull<CrdtIdFieldPathOption>()?.value?.toList()
                ?: fieldAnnotations.firstNotNullOfOrNull<CrdtIdFieldOption>()?.value
                    ?.takeIf { it > 0 }?.let { listOf(it) }
        } else null

        if (idPath != null && idPath.isNotEmpty()) {
            // Resolve the field path to get accessor chain and leaf field info
            val fieldAccessors = resolveFieldPath(
                rootFieldsProvider = fieldMessageFields,
                path = idPath,
                resolverProvider = resolverProvider
            )
            val leafField = fieldAccessors.last()

            // Validate that the leaf ID field is not a message, map, or optional
            require(!leafField.isMessage) {
                "List $tag id field path $idPath, message is not a valid key type: $leafField"
            }
            require(!leafField.isMap) {
                "List $tag id field path $idPath, map is not a valid key type: $leafField"
            }
            require(leafField.label != WireField.Label.OPTIONAL) {
                "List $tag id field path $idPath, cannot be optional: $leafField"
            }

            CollectionType.RepeatedId(
                mapType = CollectionType.Map(
                    keyType = leafField.singleAdapter.type?.toKeyType()
                        ?: error("Unsupported map key type: ${leafField.singleAdapter.type}"),
                    maxTombstone = fieldAnnotations.firstNotNullOfOrNull<CrdtMaxTombstonesOption>()?.value
                        ?: MAX_TOMBSTONE_DEFAULT,
                    tombstoneTtl = fieldAnnotations.firstNotNullOfOrNull<CrdtTombstoneTtlOption>()?.value
                ),
                idPath = idPath,
                repeatedKeyTransformer = { value ->
                    var current: Any? = value
                    for (accessor in fieldAccessors) {
                        current = accessor[current!!]
                            ?: error("Null value in ID path: $idPath at field ${accessor.name}")
                    }
                    current ?: error("Null ID value for path $idPath in $actual")
                }
            )
        } else {
            CollectionType.Repeated
        }
    } else {
        null
    }

    override val mergeStrategy: MessageFieldMergeStrategy =
        fieldAnnotations.firstNotNullOfOrNull<CrdtMergeStrategyOption>()?.value?.toMessageFieldMergeStrategy(
            type = actual.singleAdapter.type
        ) ?: MERGE

    override val valueType: ValueType = if (actual.isMessage) {
        ValueType.MESSAGE
    } else if (collectionType == null && oneOfName == null && actual.label == WireField.Label.OPTIONAL) {
        ValueType.OPTIONAL
    } else {
        ValueType.REQUIRED
    }

    override val valueTypeId: Any = (actual.singleAdapter.type ?: "Unknown Type") to mergeStrategy

    override val encoder: (Any) -> ByteArray =
        if (collectionType != null) {
            {
                // For collections, encode with tags using a temporary writer
                // Wire's adapter knows how to encode maps/lists with repeated tags
                val buffer = Buffer()
                val writer = ProtoWriter(buffer)
                actual.adapter.encodeWithTag(writer, tag, it)
                buffer.readByteArray()
            }
        } else {
            {
                actual.adapter.encode(it)
            }
        }

    override val decoder: (ByteArray) -> Any = when (collectionType) {
        is CollectionType.Map -> {
            {
                val buffer = Buffer().write(it)
                val reader = ProtoReader(buffer)
                val token = reader.beginMessage()
                val result = mutableMapOf<Any, Any?>()
                try {
                    while (true) {
                        val fieldTag = reader.nextTag()
                        if (fieldTag == -1) break
                        if (fieldTag == tag) {
                            @Suppress("UNCHECKED_CAST")
                            val entry = (actual.adapter as ProtoAdapter<Map<Any, Any?>>).decode(reader)
                            result.putAll(entry)
                        } else {
                            reader.skip()
                        }
                    }
                } finally {
                    reader.endMessageAndGetUnknownFields(token)
                }
                result
            }
        }
        is CollectionType.RepeatedId,
        is CollectionType.Repeated -> {
            {
                val buffer = Buffer().write(it)
                val reader = ProtoReader(buffer)
                val token = reader.beginMessage()
                val result = mutableListOf<Any?>()
                try {
                    while (true) {
                        val fieldTag = reader.nextTag()
                        if (fieldTag == -1) break
                        if (fieldTag == tag) {
                            @Suppress("UNCHECKED_CAST")
                            val value = (actual.singleAdapter as ProtoAdapter<Any?>).decode(reader)
                            result.add(value)
                        } else {
                            reader.skip()
                        }
                    }
                } finally {
                    reader.endMessageAndGetUnknownFields(token)
                }
                result
            }
        }
        null -> {
            {
                actual.adapter.decode(it)
            }
        }
    }
    override val valueEncoder: (Any) -> ByteArray = {
        @Suppress("UNCHECKED_CAST")
        (actual.singleAdapter as ProtoAdapter<Any>).encode(it)
    }
    override val valueDecoder: (ByteArray) -> Any = {
        // ProtoAdapter.encode() for LENGTH_DELIMITED scalars (STRING, BYTES) produces
        // raw content without a varint length prefix, but ProtoAdapter.decode() expects
        // a varint prefix via ProtoReader.readString()/readBytes(). Decode these directly.
        // STRING and BYTES are the only LENGTH_DELIMITED scalar types in protobuf;
        // messages are handled by separate code paths.
        @Suppress("UNCHECKED_CAST")
        when (actual.singleAdapter) {
            ProtoAdapter.STRING -> String(it, Charsets.UTF_8)
            ProtoAdapter.BYTES -> it.toByteString()
            else -> (actual.singleAdapter as ProtoAdapter<Any>).decode(it)
        }
    }

    override fun set(builder: MessageBuilder<M, WireMessageConstructorBuilder<M, B>>, value: Any?) {
        builder.setter.set(wireField, value)
    }

    override fun get(message: M): Any? = actual[message]

    override fun isAbsent(value: Any?): Boolean {
        if (value == null) return true
        return when (collectionType) {
            is CollectionType.Map -> (value as Map<*, *>).isEmpty()
            is CollectionType.Repeated,
            is CollectionType.RepeatedId -> (value as List<*>).isEmpty()
            null -> when (valueType) {
                ValueType.REQUIRED -> {
                    // if oneOfName is set then the value cannot be absent
                    if (oneOfName != null) return false
                    // if default value, return value absent as true else false
                    value == actual.singleAdapter.identity
                }
                // reaching here means it was set (null was already handled above)
                ValueType.OPTIONAL,
                ValueType.MESSAGE -> false
            }
        }
    }

    override fun toString(): String = "$parentMessageType.${actual.name} = $tag;(${actual.adapter.type})"
}

fun KClass<*>.toKeyType() = when (this) {
    String::class -> KeyType.STRING
    Int::class -> KeyType.INT
    Long::class -> KeyType.LONG
    Boolean::class -> KeyType.BOOL
    else -> null
}

inline fun <reified R : Annotation> Array<Annotation>.firstNotNullOfOrNull(): R? {
    return firstNotNullOfOrNull { it as? R }
}

fun Map<String, Method>.annotationsFor(field: Field): Array<Annotation> {
    return annotationsFor(field.name)
}

// Kind of hacky work around for efficient property annotation access through java reflection
// See: https://github.com/square/wire/pull/3427
fun Map<String, Method>.annotationsFor(propertyName: String): Array<Annotation> {
    return this["get${propertyName.replaceFirstChar { it.uppercaseChar() }}\$annotations"]
        ?.annotations ?: emptyArray()
}

fun Class<*>.methodsByName(): Map<String, Method> = methods.associateBy { it.name }

fun FieldMergeStrategy.toMessageFieldMergeStrategy(
    type: KClass<*>?,
) = when (this) {
    FieldMergeStrategy.MERGE -> MERGE
    FieldMergeStrategy.REPLACE -> REPLACE
    FieldMergeStrategy.COUNTER -> when (type) {
        Int::class -> INT_COUNTER
        Long::class -> LONG_COUNTER
        else -> throw IllegalArgumentException("Counter type is not long or int: $type")
    }
}

/**
 * Resolves a path of field numbers to a list of field bindings.
 *
 * This supports nested ID fields where the ID is located within a nested message.
 * For example, a path of [1, 2] would resolve to [field 1 of root, field 2 of field 1's type].
 *
 * @param rootFieldsProvider provides the fields of the root message type
 * @param path the list of field numbers forming the path
 * @param resolverProvider used to get field bindings for nested message types
 * @return list of field bindings for each step in the path
 * @throws IllegalArgumentException if any field in the path doesn't exist
 */
@Suppress("UNCHECKED_CAST")
private fun resolveFieldPath(
    rootFieldsProvider: () -> Map<Int, FieldOrOneOfBinding<Any, Any>>,
    path: List<Int>,
    resolverProvider: WireCrdtResolverProvider?,
): List<FieldOrOneOfBinding<Any, Any>> {
    val result = mutableListOf<FieldOrOneOfBinding<Any, Any>>()
    var currentFieldsProvider: () -> Map<Int, FieldOrOneOfBinding<Any, Any>> = rootFieldsProvider

    for ((index, fieldNum) in path.withIndex()) {
        val fields = currentFieldsProvider()
        val field = fields[fieldNum]
            ?: error("ID field path element $fieldNum not found (path: $path, index: $index, available: ${fields.keys})")
        result.add(field)

        // If not the last field and it's a message type, get its fields for the next iteration
        if (index < path.size - 1) {
            require(field.isMessage) {
                "ID field path element $fieldNum is not a message type, cannot traverse further (path: $path, index: $index)"
            }
            requireNotNull(resolverProvider) {
                "resolverProvider required to resolve nested ID field paths"
            }
            // Capture the adapter for the next level
            val nestedAdapter = field.singleAdapter
            currentFieldsProvider = {
                resolverProvider.getOrCreateWireRuntimeAdapter<Nothing, Nothing>(nestedAdapter)
                    .fields as Map<Int, FieldOrOneOfBinding<Any, Any>>
            }
        }
    }

    return result
}
