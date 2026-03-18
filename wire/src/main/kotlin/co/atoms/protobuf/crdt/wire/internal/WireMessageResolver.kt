package co.atoms.protobuf.crdt.wire.internal

import co.atoms.protobuf.crdt.data.Actors
import co.atoms.protobuf.crdt.data.PathComponent
import co.atoms.protobuf.crdt.data.Version
import co.atoms.protobuf.crdt.data.VersionNode
import co.atoms.protobuf.crdt.data.incrementLocalActor
import co.atoms.protobuf.crdt.data.merge
import co.atoms.protobuf.crdt.resolver.CrdtResolver
import co.atoms.protobuf.crdt.resolver.MessageFieldResolverProvider
import co.atoms.protobuf.crdt.resolver.MessageResolver
import co.atoms.protobuf.crdt.resolver.descriptor.MessageBuilder
import co.atoms.protobuf.crdt.resolver.descriptor.MessageFieldResolver
import co.atoms.protobuf.crdt.resolver.version.VersionTreeResolver
import co.atoms.protobuf.crdt.wire.WireCrdtMessageResolver
import co.atoms.protobuf.crdt.wire.WireCrdtResolverProvider
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.internal.FieldOrOneOfBinding
import com.squareup.wire.internal.RuntimeMessageAdapter

/**
 * Adapter that implements all required interfaces for CRDT operations on Wire messages. Handles both local writes
 * and incoming conflict resolution.
 */
internal class WireMessageResolver<M : Message<M, B>, B : Message.Builder<M, B>>(
    override val decoder: (ByteArray) -> M,
    override val encoder: (M) -> ByteArray,
    private val messageType: Class<M>,
    protoAdapter: ProtoAdapter<M>,
    private val resolverProvider: WireCrdtResolverProvider,
    override val versionTreeResolver: VersionTreeResolver<VersionNode, Version, PathComponent>,
) :
    MessageResolver<M, WireMessageConstructorBuilder<M, B>, VersionNode, Version, PathComponent, Actors>,
    WireCrdtMessageResolver<M> {

    // Runtime adapter provides reflection-based field access
    private val adapter: RuntimeMessageAdapter<M, B> =
        resolverProvider.getOrCreateWireRuntimeAdapter(protoAdapter)

    private val builderMetadata = WireMessageConstructorBuilder.Metadata(messageType)

    /**
     * Lazy initialization of field metadata to avoid circular dependencies when messages have recursive references.
     */
    override val fields: Map<
        Int,
        MessageFieldResolver<
            M,
            MessageBuilder<M, WireMessageConstructorBuilder<M, B>>,
            Any,
            VersionNode,
            Version,
            PathComponent
            >
        > by lazy {
        buildMap {
            val methods = messageType.methodsByName()
            builderMetadata.protoFields.forEach { protoField ->
                val wireField = protoField.wireField
                adapter.fields[wireField.tag]?.let { binding ->
                    this[wireField.tag] = MessageFieldResolverProvider.messageFieldResolver(
                        descriptor = WireFieldDescriptor(
                            actual = binding,
                            fieldAnnotations = methods.annotationsFor(protoField.field),
                            fieldMessageFields = {
                                @Suppress("UNCHECKED_CAST")
                                resolverProvider.getOrCreateWireRuntimeAdapter<Nothing, Nothing>(
                                    adapter = binding.singleAdapter
                                ).fields as Map<Int, FieldOrOneOfBinding<Any, Any>>
                            },
                            parentMessageType = messageType,
                            wireField = wireField,
                            resolverProvider = resolverProvider,
                        ),
                        messageResolverFactory = {
                            @Suppress("UNCHECKED_CAST")
                            resolverProvider.getOrCreateMessageResolver(
                                messageType = binding.singleAdapter.type?.java
                                    ?: throw IllegalStateException("No type for adapter ${binding.singleAdapter}"),
                                adapter = binding.singleAdapter,
                            ) as CrdtResolver<Any, VersionNode, Version, PathComponent>
                        },
                        versionTreeResolver = versionTreeResolver,
                    )
                }
            }
        }
    }

    override fun newBuilder(): MessageBuilder<M, WireMessageConstructorBuilder<M, B>> {
        return WireMessageBuilder(WireMessageConstructorBuilder(builderMetadata))
    }

    override fun toString(): String = "WireMessageAdapter($messageType)"

    override val Actors.versionVector: Map<Long, Long> get() = version_vector

    override fun merge(localActors: Actors?, versionVector: Map<Long, Long>): Actors {
        return localActors.merge(versionVector)
    }

    override fun incrementLocalActor(actors: Actors?): Actors {
        return actors.incrementLocalActor()
    }

    override fun List<Version>.toVersionVector(): Map<Long, Long> {
        return mutableMapOf<Long, Long>().also { versionVector ->
            forEach {
                versionVector.compute(it.actor_id) { _, value ->
                    value?.coerceAtLeast(it.actor_version) ?: it.actor_version
                }
            }
        }
    }

    override fun localVersion(actors: Actors, timestamp: Long): Version {
        val localActor = actors.local_actor
        return Version(
            timestamp = timestamp,
            actor_id = actors.local_actor,
            actor_version = actors.version_vector[localActor] ?: 0L
        )
    }
}
