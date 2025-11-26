package com.css.protobuf.crdt.resolver.descriptor

sealed interface CollectionType {
    data class Map(
        val keyType: KeyType,
        val maxTombstone: Int,
        val tombstoneTtl: Long?
    ) : CollectionType

    object Repeated : CollectionType

    class RepeatedId(
        val idTag: Int,
        val mapType: Map,
        val repeatedKeyTransformer: (Any) -> Any,
    ) : CollectionType {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RepeatedId) return false

            if (idTag != other.idTag) return false
            if (mapType != other.mapType) return false

            return true
        }

        override fun hashCode(): Int {
            var result = idTag
            result = 31 * result + mapType.hashCode()
            return result
        }
    }
}
