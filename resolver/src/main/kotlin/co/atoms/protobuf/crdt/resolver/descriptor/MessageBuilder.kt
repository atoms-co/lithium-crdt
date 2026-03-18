package co.atoms.protobuf.crdt.resolver.descriptor

interface MessageBuilder<M, S> {
    val setter: S
    fun build(): M
}
