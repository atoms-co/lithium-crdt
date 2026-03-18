package co.atoms.lithium.crdt.wire.internal

import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.WireField
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import okio.ByteString

/** Copied from internal Wire implementation but removed mutability casting and improved caching of metadata */
internal class WireMessageConstructorBuilder<M : Message<M, B>, B : Message.Builder<M, B>>(
    private val messageBuilderMetadata: Metadata,
) : Message.Builder<M, B>() {
    private val fieldValueMap: MutableMap<Int, Pair<WireField, Any?>>
    private val repeatedFieldValueMap: MutableMap<Int, Pair<WireField, List<*>>>
    private val mapFieldKeyValueMap: MutableMap<Int, Pair<WireField, Map<*, *>>>

    init {
        val fieldCount = messageBuilderMetadata.protoFields.size
        fieldValueMap = LinkedHashMap(fieldCount)
        repeatedFieldValueMap = LinkedHashMap(fieldCount)
        mapFieldKeyValueMap = LinkedHashMap(fieldCount)
    }

    fun set(
        field: WireField,
        value: Any?,
    ) {
        when {
            field.isMap -> {
                mapFieldKeyValueMap[field.tag] = field to ((value as? Map<*, *>) ?: emptyMap<Any, Any>())
            }
            field.label.isRepeated -> {
                repeatedFieldValueMap[field.tag] = field to ((value as? List<*>) ?: emptyList<Any>())
            }
            else -> {
                fieldValueMap[field.tag] = field to value
                if (value != null && field.oneofName.isNotEmpty()) {
                    clobberOtherIsOneOfs(field)
                }
            }
        }
    }

    private fun clobberOtherIsOneOfs(field: WireField) {
        fieldValueMap.entries.removeIf { entry ->
            val otherField = entry.value.first
            otherField.oneofName == field.oneofName && otherField.tag != field.tag
        }
    }

    fun get(field: WireField): Any? {
        return if (field.isMap) {
            mapFieldKeyValueMap[field.tag]?.second ?: mapOf<Any, Any>()
        } else if (field.label.isRepeated) {
            repeatedFieldValueMap[field.tag]?.second ?: listOf<Any>()
        } else {
            val value = fieldValueMap[field.tag]?.second
            // Proto3 singular fields have non-nullable types with default parameters, we need to pass
            // the identity value to please the constructor.
            if (value == null && field.label == WireField.Label.OMIT_IDENTITY) {
                ProtoAdapter.get(field.adapter).identity
            } else {
                value
            }
        }
    }

    override fun build(): M {
        val protoFields = messageBuilderMetadata.protoFields
        // Retrieve constructor explicitly since `Constructor#getParameterCount` was introduced in JDK
        // 1.8 and may not be available. ByteString is for `unknown_fields`.
        val args = (0..protoFields.size).map { index ->
            when {
                index == protoFields.size -> buildUnknownFields()
                else -> get(protoFields[index].wireField)
            }
        }
        @Suppress("UNCHECKED_CAST", "SpreadOperator")
        return messageBuilderMetadata.constructor.newInstance(*args.toTypedArray()) as M
    }

    class ProtoField(
        val field: Field,
        val wireField: WireField,
    )

    class Metadata(
        messageType: Class<*>,
        val protoFields: List<ProtoField> = messageType.declaredProtoFields(),
        parameterTypes: Array<Class<*>> = protoFields.map { it.field.type }.toTypedArray(),
        @Suppress("SpreadOperator")
        val constructor: Constructor<*> = messageType.getDeclaredConstructor(*parameterTypes, ByteString::class.java)
    )
}

private fun Class<*>.declaredProtoFields(): List<WireMessageConstructorBuilder.ProtoField> = declaredFields
    .mapNotNull { field ->
        field.declaredAnnotations.firstNotNullOfOrNull<WireField>()?.let {
            WireMessageConstructorBuilder.ProtoField(
                field,
                it
            )
        }
    }
    .sortedBy { it.wireField.schemaIndex }

private val WireField.isMap: Boolean
    get() = keyAdapter.isNotEmpty()
