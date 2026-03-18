package co.atoms.protobuf.crdt.protoc

import co.atoms.protobuf.crdt.resolver.descriptor.MessageBuilder
import com.google.protobuf.Message

/**
 * Message builder wrapper for protobuf messages used in CRDT operations.
 *
 * This class adapts the protobuf builder pattern to the CRDT resolver framework, providing a
 * unified interface for field-level updates during merge operations. It wraps a protobuf builder
 * and implements the [MessageBuilder] interface required by the CRDT framework.
 *
 * **Design Notes:**
 *
 * - The builder is mutable and maintains state across multiple field updates
 * - The [build] method creates an immutable message from the current builder state
 * - Type safety is maintained through generic constraints on M and S
 *
 * **Nullability:** The wrapped builder ([setter]) is always non-null. The [build] method
 * returns a non-null immutable message. Field values set on the builder may be null to represent
 * cleared/unset fields.
 *
 * **Usage Example:**
 *
 * ```kotlin
 * val protoBuilder = MyMessage.newBuilder()
 * val wrapper = ProtoMessageBuilder(protoBuilder)
 *
 * // Framework can now use setter to update fields
 * wrapper.setter.setField(fieldDescriptor, value)
 *
 * // Build the final immutable message
 * val result = wrapper.build()
 * ```
 *
 * @param setter the non-null protobuf builder instance to wrap
 * @see MessageBuilder
 */
internal class ProtoMessageBuilder(
    override val setter: Message.Builder
) : MessageBuilder<Message, Message.Builder> {
    /**
     * Builds the final immutable protobuf message from the current builder state.
     *
     * @return a non-null immutable message
     */
    override fun build(): Message = setter.build()
}
