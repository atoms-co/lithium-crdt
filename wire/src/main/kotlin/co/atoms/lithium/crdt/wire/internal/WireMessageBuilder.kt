package co.atoms.lithium.crdt.wire.internal

import co.atoms.lithium.crdt.resolver.descriptor.MessageBuilder
import com.squareup.wire.Message

internal class WireMessageBuilder<M : Message<M, B>, B : Message.Builder<M, B>>(
    override val setter: WireMessageConstructorBuilder<M, B>
) : MessageBuilder<M, WireMessageConstructorBuilder<M, B>> {
    override fun build(): M = setter.build()
}
