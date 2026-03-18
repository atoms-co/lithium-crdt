package co.atoms.protobuf.crdt.wire

import co.atoms.protobuf.crdt.data.PathComponent
import co.atoms.protobuf.crdt.data.Version
import co.atoms.protobuf.crdt.data.VersionNode
import co.atoms.protobuf.crdt.resolver.CrdtResolver
import co.atoms.protobuf.crdt.resolver.version.VersionTreeResolver
import co.atoms.protobuf.crdt.wire.internal.WireMessageResolver
import co.atoms.protobuf.crdt.wire.internal.WireVersionTreeResolver
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
                // Cast through Any to avoid KotlinNothingValueException. Functions returning
                // Nothing are expected to never return normally, but this is only used as a
                // variance workaround in the cache — the actual return value is a valid message.
                @Suppress("UNCHECKED_CAST")
                val decoder: (ByteArray) -> Nothing =
                    { bytes: ByteArray -> (adapter as ProtoAdapter<Any>).decode(bytes) } as (ByteArray) -> Nothing
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
