package co.atoms.protobuf.crdt.protoc

import co.atoms.protobuf.crdt.data.Actors
import co.atoms.protobuf.crdt.data.PathComponent
import co.atoms.protobuf.crdt.data.Version
import co.atoms.protobuf.crdt.data.VersionNode
import co.atoms.protobuf.crdt.resolver.CrdtResolver
import co.atoms.protobuf.crdt.resolver.MessageFieldResolverProvider
import co.atoms.protobuf.crdt.resolver.MessageResolver
import co.atoms.protobuf.crdt.resolver.descriptor.MessageBuilder
import co.atoms.protobuf.crdt.resolver.descriptor.MessageFieldResolver
import co.atoms.protobuf.crdt.resolver.incoming.MessageIncomingResolver
import co.atoms.protobuf.crdt.resolver.local.MessageLocalResolver
import co.atoms.protobuf.crdt.resolver.version.VersionTreeResolver
import com.google.protobuf.Descriptors
import com.google.protobuf.Message
import com.google.protobuf.Message.Builder

/**
 * CRDT resolver for protobuf messages that handles conflict resolution and local updates.
 *
 * This resolver manages CRDT operations for protobuf messages by providing:
 * - **Field-level version tracking:** Each field maintains its own version vector for fine-grained conflict resolution
 * - **Multiple merge strategies:** Supports last-write-wins (LWW) and field-level merge strategies based on protobuf
 *   field options
 * - **Collection support:** Handles repeated fields, maps, and nested messages with element-level tracking
 * - **ID-based list resolution:** For repeated message fields with `crdt_id_field` option, provides consistent element
 *   identity across replicas
 *
 * **Architecture:** This class implements multiple resolver interfaces to handle different aspects of CRDT operations:
 * - [CrdtResolver]: Main interface for applying local writes and merging incoming changes
 * - [MessageLocalResolver]: Handles local update operations
 * - [MessageIncomingResolver]: Handles incoming updates from other replicas
 * - [VersionTreeResolver]: Manages version vector operations
 *
 * **Field Resolution:** The resolver uses lazy initialization for field metadata to handle recursive message
 * references. Each field is resolved according to its type and options:
 * - Primitive fields: Last-write-wins based on version vectors
 * - Optional fields: Special handling to distinguish null from unset
 * - Message fields: Recursive resolution with field-level merging
 * - Maps: Per-entry version tracking with tombstones for deletions
 * - Repeated fields: Position-based or ID-based tracking depending on configuration
 *
 * **Protobuf Field Options:**
 * - `crdt_replace_on_conflict`: Treat message field as atomic (last-write-wins)
 * - `crdt_id_field`: Specify ID field number for repeated message elements
 *
 * **Nullability:** Field values can be null to represent unset/cleared fields. The resolver properly handles null
 * values during merge operations, distinguishing between explicit null (field cleared) and absent values (field never
 * set).
 *
 * **Thread-safety:** This class is thread-safe when used with immutable protobuf messages. The lazy initialization of
 * field metadata uses Kotlin's lazy delegate for safe concurrent access.
 *
 * **Usage:** Instances are created and cached by [CrdtMessageResolverProvider]. Users should not instantiate this class
 * directly.
 *
 * @see CrdtMessageResolverProvider
 * @see ProtoMessageFieldDescriptor
 */
internal class ProtocMessageResolver(
    override val decoder: (ByteArray) -> Message,
    override val encoder: (Message) -> ByteArray = { it.toByteArray() },
    private val builderFactory: () -> Builder,
    private val fieldDescriptors: List<Descriptors.FieldDescriptor>,
    private val fieldResolverProvider: MessageFieldResolverProvider,
    private val resolverProvider: CrdtMessageResolverProvider,
    override val versionTreeResolver: VersionTreeResolver<VersionNode, Version, PathComponent>,
) : MessageResolver<Message, Builder, VersionNode, Version, PathComponent, Actors> {
    override val fields: Map<
        Int,
        MessageFieldResolver<Message, MessageBuilder<Message, Builder>, Any, VersionNode, Version, PathComponent>
        > by lazy {
        val messageBuilder = builderFactory()

        fieldDescriptors.associate { fieldDescriptor ->
            val descriptor = ProtoMessageFieldDescriptor(fieldDescriptor)

            fieldResolverProvider.messageFieldResolver(
                descriptor = descriptor,
                versionTreeResolver = versionTreeResolver,
            ) {
                // For map fields, get the MapEntry builder; otherwise use the message builder
                val defaultInstanceForType =
                    if (descriptor.isMapField) {
                        messageBuilder.newBuilderForField(fieldDescriptor)
                    } else {
                        messageBuilder
                    }
                        .newBuilderForField(descriptor.valueDescriptor)
                        .defaultInstanceForType

                @Suppress("UNCHECKED_CAST")
                resolverProvider.getOrCreateMessageResolver(defaultInstanceForType)
                    as CrdtResolver<Any, VersionNode, Version, PathComponent>
            }.let {
                it.binding.tag to it
            }
        }
    }

    override fun newBuilder(): MessageBuilder<Message, Builder> = ProtoMessageBuilder(builderFactory())

    override val Actors.versionVector: Map<Long, Long> get() = versionVectorMap

    override fun merge(localActors: Actors?, versionVector: Map<Long, Long>): Actors {
        return localActors.merge(versionVector)
    }

    override fun incrementLocalActor(actors: Actors?): Actors {
        return actors.incrementLocalActor()
    }

    override fun List<Version>.toVersionVector(): Map<Long, Long> {
        return mutableMapOf<Long, Long>().also { versionVector ->
            forEach {
                versionVector.compute(it.actorId) { _, value ->
                    value?.coerceAtLeast(it.actorVersion) ?: it.actorVersion
                }
            }
        }
    }

    override fun localVersion(actors: Actors, timestamp: Long): Version {
        return actors.localVersion(timestamp)
    }
}

private fun Actors.localVersion(timestamp: Long) = Version.newBuilder()
    .setTimestamp(timestamp)
    .setActorId(localActor)
    .setActorVersion(versionVectorMap[localActor] ?: 0L)
    .build()
