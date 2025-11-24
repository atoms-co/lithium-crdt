package com.css.internal.shared.storage.crdt.wire

import com.css.internal.shared.storage.crdt.data.PathComponent
import com.css.internal.shared.storage.crdt.data.Version
import com.css.internal.shared.storage.crdt.data.VersionNode
import com.css.internal.shared.storage.crdt.resolver.CrdtResolver
import com.css.internal.shared.storage.crdt.resolver.version.VersionTreeResolver
import com.css.internal.shared.storage.crdt.wire.internal.WireMessageResolver
import com.css.internal.shared.storage.crdt.wire.internal.WireVersionTreeResolver
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.internal.RuntimeMessageAdapter
import com.squareup.wire.internal.createRuntimeMessageAdapter
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Factory for creating CRDT-aware Wire protobuf adapters.
 *
 * Bridges Square's Wire library with our CRDT system by creating adapters that:
 * - Handle conflict resolution between concurrent updates
 * - Manage version tracking at the field level
 */
class WireCrdtResolverProvider internal constructor(
    private val versionTreeResolver: VersionTreeResolver<VersionNode, Version, PathComponent>,
) {
    constructor() : this(WireVersionTreeResolver)

    // Cache adapters to avoid expensive reflection and initialization
    private val messageResolvers = ConcurrentHashMap<Any, WireMessageResolver<Nothing, Nothing>>()
    private val wireRuntimeAdapters = ConcurrentHashMap<Any, RuntimeMessageAdapter<*, *>>()

    @Suppress("UNCHECKED_CAST")
    fun <M : Message<M, B>, B : Message.Builder<M, B>> messageResolver(
        adapter: ProtoAdapter<M>
    ): WireCrdtMessageResolver<M> = getOrCreateMessageResolver(
        messageType = (adapter.type as KClass<M>).java,
        adapter = adapter
    ) as WireCrdtMessageResolver<M>

    @Suppress("UNCHECKED_CAST")
    internal fun <M : Message<M, B>, B : Message.Builder<M, B>> getOrCreateWireRuntimeAdapter(
        adapter: ProtoAdapter<*>,
    ): RuntimeMessageAdapter<M, B> {
        return wireRuntimeAdapters.getOrPut(adapter.type) {
            createRuntimeMessageAdapter(
                messageType = (adapter.type as KClass<M>).java,
                typeUrl = adapter.typeUrl,
                syntax = adapter.syntax,
            )
        } as RuntimeMessageAdapter<M, B>
    }

    internal fun getOrCreateMessageResolver(
        messageType: Class<*>,
        adapter: ProtoAdapter<*>
    ): CrdtResolver<*, VersionNode, Version, PathComponent> {
        return messageResolvers.getOrPut(messageType) {
            try {
                @Suppress("UNCHECKED_CAST")
                val encoder: (Any) -> ByteArray = { (adapter as ProtoAdapter<Any>).encode(it) }
                @Suppress("UNCHECKED_CAST")
                val decoder: (ByteArray) -> Nothing = { (adapter as ProtoAdapter<Nothing>).decode(it) }
                @Suppress("UNCHECKED_CAST")
                (WireMessageResolver(
                    // Cast to Nothing to work around variance issues in the cache
                    decoder = decoder,
                    encoder = encoder,
                    messageType = messageType as Class<Nothing>,
                    protoAdapter = adapter as ProtoAdapter<Nothing>,
                    resolverProvider = this,
                    versionTreeResolver = versionTreeResolver,
                ))
            } catch (e: Throwable) {
                throw IllegalStateException("Failed to create WireMessageAdapter for $messageType", e)
            }
        }
    }
}
