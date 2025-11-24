package com.css.internal.shared.storage.crdt.wire.internal

import com.css.internal.shared.storage.crdt.data.options.CrdtIdFieldOption
import com.css.internal.shared.storage.crdt.data.options.CrdtMaxTombstonesOption
import com.css.internal.shared.storage.crdt.data.options.CrdtMergeStrategyOption
import com.css.internal.shared.storage.crdt.data.options.CrdtTombstoneTtlOption
import com.css.internal.shared.storage.crdt.data.options.FieldMergeStrategy
import com.css.internal.shared.storage.crdt.resolver.descriptor.CollectionType
import com.css.internal.shared.storage.crdt.resolver.descriptor.KeyType
import com.css.internal.shared.storage.crdt.resolver.descriptor.MessageBuilder
import com.css.internal.shared.storage.crdt.resolver.descriptor.MessageFieldDescriptor
import com.css.internal.shared.storage.crdt.resolver.descriptor.MessageFieldDescriptor.Companion.MAX_TOMBSTONE_DEFAULT
import com.css.internal.shared.storage.crdt.resolver.descriptor.MessageFieldMergeStrategy
import com.css.internal.shared.storage.crdt.resolver.descriptor.MessageFieldMergeStrategy.INT_COUNTER
import com.css.internal.shared.storage.crdt.resolver.descriptor.MessageFieldMergeStrategy.LONG_COUNTER
import com.css.internal.shared.storage.crdt.resolver.descriptor.MessageFieldMergeStrategy.MERGE
import com.css.internal.shared.storage.crdt.resolver.descriptor.MessageFieldMergeStrategy.REPLACE
import com.css.internal.shared.storage.crdt.resolver.descriptor.ValueType
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

internal class WireFieldDescriptor<M : Message<M, B>, B : Message.Builder<M, B>>(
    private val actual: FieldOrOneOfBinding<M, B>,
    fieldAnnotations: Array<Annotation>,
    fieldMessageFields: () -> Map<Int, FieldOrOneOfBinding<Any, Any>>,
    private val parentMessageType: Class<M>,
    private val wireField: WireField,
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
        val listIdField = if (actual.isMessage) {
            fieldAnnotations.firstNotNullOfOrNull<CrdtIdFieldOption>()?.value ?: 0
        } else 0
        if (listIdField > 0) {
            val idField = fieldMessageFields()[listIdField]?.apply {
                assert(!isMessage) {
                    "List $tag id field $listIdField, message is not a valid key type: $this"
                }
                assert(!isMap) {
                    "List $tag id field $listIdField, map is not a valid key type: $this"
                }
                assert(label != WireField.Label.OPTIONAL) {
                    "List $tag id field $listIdField, cannot be optional: $this"
                }
            } ?: error("List $tag id field $listIdField does not exist")

            CollectionType.RepeatedId(
                mapType = CollectionType.Map(
                    keyType = idField.singleAdapter.type?.toKeyType()
                        ?: error("Unsupported map key type: ${idField.singleAdapter.type}"),
                    maxTombstone = fieldAnnotations.firstNotNullOfOrNull<CrdtMaxTombstonesOption>()?.value
                        ?: MAX_TOMBSTONE_DEFAULT,
                    tombstoneTtl = fieldAnnotations.firstNotNullOfOrNull<CrdtTombstoneTtlOption>()?.value
                ),
                idTag = listIdField,
                repeatedKeyTransformer = { idField[it] ?: error("Unsupported field id: $idField $actual") }
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
        @Suppress("UNCHECKED_CAST")
        (actual.singleAdapter as ProtoAdapter<Any>).decode(it)
    }

    override fun set(builder: MessageBuilder<M, WireMessageConstructorBuilder<M, B>>, value: Any?) {
        builder.setter.set(wireField, value)
    }

    override fun get(message: M): Any? = actual[message]

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
