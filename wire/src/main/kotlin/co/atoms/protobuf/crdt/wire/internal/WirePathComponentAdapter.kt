package co.atoms.protobuf.crdt.wire.internal

import co.atoms.protobuf.crdt.data.PathComponent
import co.atoms.protobuf.crdt.resolver.delta.PathComponentAdapter

internal interface WirePathComponentAdapter : PathComponentAdapter<PathComponent> {
    // Factory methods
    override fun createPathComponentField(field: Int) = PathComponent(field_number = field)
    override fun createPathComponentBooleanKey(key: Boolean) = PathComponent(bool_key = key)
    override fun createPathComponentIntKey(key: Int) = PathComponent(int32_key = key)
    override fun createPathComponentLongKey(key: Long) = PathComponent(int64_key = key)
    override fun createPathComponentStringKey(key: String) = PathComponent(string_key = key)
    override fun createPathComponentRepeatedIndex(index: Int) = PathComponent(repeated_index = index)

    // Getter methods
    override val PathComponent.fieldNumber: Int? get() = field_number
    override val PathComponent.booleanKey: Boolean? get() = bool_key
    override val PathComponent.intKey: Int? get() = int32_key
    override val PathComponent.longKey: Long? get() = int64_key
    override val PathComponent.stringKey: String? get() = string_key
    override val PathComponent.repeatedIndex: Int? get() = repeated_index
}
