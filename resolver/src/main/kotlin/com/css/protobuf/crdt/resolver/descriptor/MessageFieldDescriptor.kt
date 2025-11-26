package com.css.protobuf.crdt.resolver.descriptor

interface MessageFieldDescriptor<in M, in B, V> {
    val collectionType: CollectionType?
    /** Decoder for the value or the collection - reconstructs from encoded bytes */
    val decoder: (ByteArray) -> Any
    /** Encoder for the value or the collection */
    val encoder: (Any) -> ByteArray
    val mergeStrategy: MessageFieldMergeStrategy
    val oneOfName: String?
    val tag: Int
    /** Decoder for the list or map value if a collection, otherwise the value */
    val valueDecoder: (ByteArray) -> Any
    /** Encoder for the list or map value if a collection, otherwise the value */
    val valueEncoder: (Any) -> ByteArray
    val valueType: ValueType
    val typeId: Any get() = collectionType to valueTypeId
    val valueTypeId: Any
    operator fun get(message: M): V?
    fun set(builder: B, value: V?)

    companion object {
        const val MAX_TOMBSTONE_DEFAULT = 1024
    }
}
