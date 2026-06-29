package co.atoms.lithium.crdt.resolver.descriptor

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

    /**
     * Checks whether a field's value is empty.
     *
     * A value is considered empty when it is:
     *  - null (the field has no value), or
     *  - an empty list or map, or
     *  - a basic value (text, number, true/false, or a fixed choice) that is
     *    still at its starting default, such as "" for text or 0 for a number.
     *
     * Otherwise the value is treated as set, and this returns false.
     *
     * The default implementation only detects `null`, since this generic interface
     * cannot inspect the shape of an arbitrary value. Implementations that need
     * empty-collection or default-scalar detection (e.g. Wire, protoc) should override.
     *
     * @return true if the value is empty, false if it holds a real value.
     */
    fun isAbsent(value: V?): Boolean = value == null

    companion object {
        const val MAX_TOMBSTONE_DEFAULT = 1024
    }
}
