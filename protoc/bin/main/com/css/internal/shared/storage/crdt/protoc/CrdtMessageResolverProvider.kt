package com.css.internal.shared.storage.crdt.protoc

import com.css.internal.shared.storage.crdt.data.Actors
import com.css.internal.shared.storage.crdt.data.PathComponent
import com.css.internal.shared.storage.crdt.data.Version
import com.css.internal.shared.storage.crdt.data.VersionNode
import com.css.internal.shared.storage.crdt.resolver.CrdtMessageResolver
import com.css.internal.shared.storage.crdt.resolver.CrdtResolver
import com.css.internal.shared.storage.crdt.resolver.MessageFieldResolverProvider
import com.css.internal.shared.storage.crdt.resolver.version.VersionTreeResolver
import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Message
import java.util.concurrent.ConcurrentHashMap

/**
 * Factory for creating CRDT-aware protobuf message resolvers with caching.
 *
 * This provider manages the lifecycle of [ProtocMessageResolver] instances for protobuf messages, ensuring that:
 * - Resolvers are cached to avoid expensive reflection-based initialization
 * - Recursive message references are handled correctly through lazy initialization
 * - Multiple requests for the same message type return the same resolver instance
 *
 * Similar to WireCrdtResolverProvider, this class is the entry point for working with CRDT operations on protobuf
 * messages. It coordinates with [MessageFieldResolverProvider] to handle field-level resolution strategies.
 *
 * **Usage Example:**
 *
 * ```kotlin
 * val provider = CrdtMessageResolverProvider()
 * val resolver = provider.messageResolver(MyMessage.getDescriptor()) { MyMessage.newBuilder() }
 *
 * // Apply local writes with version vectors
 * val result = resolver.applyLocalWrite(oldValue, oldNode, oldVersion, newValue, newVersion)
 * ```
 *
 * **Thread-safety:** This class is thread-safe and can be shared across multiple threads. The internal cache uses
 * [ConcurrentHashMap] for safe concurrent access.
 *
 * **Nullability:** All methods return non-null values. The resolver handles null field values internally during merge
 * operations, but the resolver instances themselves are never null.
 *
 * @see ProtocMessageResolver
 * @see MessageFieldResolverProvider
 */
class CrdtMessageResolverProvider(
    private val versionTreeResolver: VersionTreeResolver<VersionNode, Version, PathComponent>,
) {
    constructor() : this(versionTreeResolver = ProtocVersionTreeResolver)

    // Cache resolvers by descriptor to avoid expensive reflection-based initialization
    private val messageResolvers =
        ConcurrentHashMap<Descriptor, CrdtResolver<Message, VersionNode, Version, PathComponent>>()
    private val fieldResolverProvider = MessageFieldResolverProvider

    fun <M : Message> getOrCreateResolverFor(
        message: M
    ): CrdtMessageResolver<M, VersionNode, Version, PathComponent, Actors> {
        @Suppress("UNCHECKED_CAST")
        return getOrCreateMessageResolver(message)
            as CrdtMessageResolver<M, VersionNode, Version, PathComponent, Actors>
    }

    /**
     * Gets or creates a CRDT resolver for the given protobuf message type.
     *
     * This simplified API accepts any message instance to determine the type. While callers typically should pass the
     * default instance (e.g., `MyMessage.getDefaultInstance()`), this method safely uses
     * [Message.getDefaultInstanceForType] internally to ensure it always works with the correct default instance for
     * builder creation.
     *
     * The resolver is cached per message descriptor, so multiple calls with the same message type (even different
     * instances) will return the same resolver instance.
     *
     * **Usage Example:**
     *
     * ```kotlin
     * val provider = ProtocCrdtResolverProvider()
     *
     * // Recommended: Pass the default instance
     * val resolver = provider.messageResolver(MyMessage.getDefaultInstance())
     *
     * // Also works: Pass any instance of the message type
     * val resolver2 = provider.messageResolver(myMessageInstance)
     *
     * // Apply local writes with version vectors
     * val result = resolver.applyLocalWrite(oldValue, oldNode, oldVersion, newValue, newVersion)
     * ```
     *
     * @param M the protobuf message type
     * @param message any instance of the message type (typically the default instance)
     * @return a non-null CRDT resolver for the specified message type
     * @throws IllegalStateException if the resolver cannot be created due to invalid message structure
     * @see Message.getDefaultInstance()
     */
    internal fun <M : Message> getOrCreateMessageResolver(message: M): ProtocMessageResolver {
        @Suppress("UNCHECKED_CAST")
        return messageResolvers.computeIfAbsent(message.descriptorForType) { descriptor ->
            val defaultInstance = message.defaultInstanceForType
            val parser = defaultInstance.parserForType
            createMessageResolver(
                decoder = { parser.parseFrom(it) },
                messageDescriptor = descriptor,
                builderFactory = { defaultInstance.toBuilder() }
            )
        } as ProtocMessageResolver
    }

    private fun <B : Message.Builder> createMessageResolver(
        decoder: (ByteArray) -> Message,
        messageDescriptor: Descriptor,
        builderFactory: () -> B,
    ): CrdtResolver<Message, VersionNode, Version, PathComponent> {
        return try {
            ProtocMessageResolver(
                decoder = decoder,
                encoder = { it.toByteArray() },
                builderFactory = builderFactory,
                fieldDescriptors = messageDescriptor.fields,
                fieldResolverProvider = fieldResolverProvider,
                resolverProvider = this,
                versionTreeResolver = versionTreeResolver,
            )
        } catch (e: Exception) {
            throw IllegalStateException("Failed to create ProtocMessageResolver for ${messageDescriptor.fullName}", e)
        }
    }
}
