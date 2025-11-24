package com.css.internal.shared.storage.crdt.protoc

import com.css.internal.shared.storage.crdt.data.PathComponent
import com.css.internal.shared.storage.crdt.resolver.delta.PathComponentAdapter

internal interface ProtocPathComponentAdapter : PathComponentAdapter<PathComponent> {
    // Factory methods
    override fun createPathComponentField(field: Int): PathComponent =
        PathComponent.newBuilder().setFieldNumber(field).build()
    override fun createPathComponentBooleanKey(key: Boolean): PathComponent =
        PathComponent.newBuilder().setBoolKey(key).build()
    override fun createPathComponentIntKey(key: Int): PathComponent =
        PathComponent.newBuilder().setInt32Key(key).build()
    override fun createPathComponentLongKey(key: Long): PathComponent =
        PathComponent.newBuilder().setInt64Key(key).build()
    override fun createPathComponentStringKey(key: String): PathComponent =
        PathComponent.newBuilder().setStringKey(key).build()
    override fun createPathComponentRepeatedIndex(index: Int): PathComponent =
        PathComponent.newBuilder().setRepeatedIndex(index).build()

    // Getter methods
    override val PathComponent.fieldNumber: Int?
        get() = if (hasFieldNumber()) fieldNumber else null

    override val PathComponent.booleanKey: Boolean?
        get() = if (hasBoolKey()) boolKey else null

    override val PathComponent.intKey: Int?
        get() = if (hasInt32Key()) int32Key else null

    override val PathComponent.longKey: Long?
        get() = if (hasInt64Key()) int64Key else null

    override val PathComponent.stringKey: String?
        get() = if (hasStringKey()) stringKey else null

    override val PathComponent.repeatedIndex: Int?
        get() = if (hasRepeatedIndex()) repeatedIndex else null
}
