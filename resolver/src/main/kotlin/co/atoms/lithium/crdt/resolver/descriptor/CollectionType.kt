package co.atoms.lithium.crdt.resolver.descriptor

sealed interface CollectionType {
    data class Map(
        val keyType: KeyType,
        val maxTombstone: Int,
        val tombstoneTtl: Long?
    ) : CollectionType

    object Repeated : CollectionType

    class RepeatedId(
        val idPath: List<Int>,
        val mapType: Map,
        val repeatedKeyTransformer: (Any) -> Any,
    ) : CollectionType {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RepeatedId) return false

            if (idPath != other.idPath) return false
            if (mapType != other.mapType) return false

            return true
        }

        override fun hashCode(): Int {
            var result = idPath.hashCode()
            result = 31 * result + mapType.hashCode()
            return result
        }
    }
}
