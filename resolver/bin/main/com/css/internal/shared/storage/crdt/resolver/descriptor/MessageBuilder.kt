package com.css.internal.shared.storage.crdt.resolver.descriptor

interface MessageBuilder<M, S> {
    val setter: S
    fun build(): M
}
