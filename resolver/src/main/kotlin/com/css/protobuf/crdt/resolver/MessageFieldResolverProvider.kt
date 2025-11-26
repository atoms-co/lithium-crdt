package com.css.protobuf.crdt.resolver

import com.css.protobuf.crdt.resolver.decoder.CrdtPathChangeDecoder
import com.css.protobuf.crdt.resolver.delta.CrdtDeltaResolver
import com.css.protobuf.crdt.resolver.descriptor.CollectionType
import com.css.protobuf.crdt.resolver.descriptor.KeyType
import com.css.protobuf.crdt.resolver.descriptor.MessageFieldDescriptor
import com.css.protobuf.crdt.resolver.descriptor.MessageFieldMergeStrategy
import com.css.protobuf.crdt.resolver.descriptor.MessageFieldMergeStrategy.INT_COUNTER
import com.css.protobuf.crdt.resolver.descriptor.MessageFieldMergeStrategy.LONG_COUNTER
import com.css.protobuf.crdt.resolver.descriptor.MessageFieldResolver
import com.css.protobuf.crdt.resolver.descriptor.ValueType
import com.css.protobuf.crdt.resolver.incoming.partial.CrdtIncomingChangeResolver
import com.css.protobuf.crdt.resolver.local.CrdtLocalResolver
import com.css.protobuf.crdt.resolver.version.VersionTreeResolver
import java.util.concurrent.ConcurrentHashMap

object MessageFieldResolverProvider {
    private val resolverCache = ConcurrentHashMap<Any, CrdtResolver<*, *, *, *>>()

    fun <M, B, N, V, D : MessageFieldDescriptor<M, B, Any>, C> messageFieldResolver(
        descriptor: D,
        versionTreeResolver: VersionTreeResolver<N, V, C>,
        messageResolverFactory: () -> CrdtResolver<Any, N, V, C>,
    ): MessageFieldResolver<M, B, Any, N, V, C> {
        val valueResolver = descriptor.toResolver(
            valueResolver = if (descriptor.valueType == ValueType.MESSAGE &&
                descriptor.mergeStrategy != MessageFieldMergeStrategy.REPLACE
            ) {
                messageResolverFactory()
            } else {
                @Suppress("UNCHECKED_CAST")
                val resolver = resolverCache.getOrPut(
                    SingleCacheKey(
                        type = descriptor.valueTypeId,
                        optional = false,
                    )
                ) {
                    when (descriptor.mergeStrategy) {
                        INT_COUNTER -> {
                            IntCounterResolver(
                                encoder = descriptor.valueEncoder,
                                decoder = descriptor.valueDecoder as (ByteArray) -> Int,
                                versionTreeResolver = versionTreeResolver,
                            )
                        }
                        LONG_COUNTER -> {
                            LongCounterResolver(
                                encoder = descriptor.valueEncoder,
                                decoder = descriptor.valueDecoder as (ByteArray) -> Long,
                                versionTreeResolver = versionTreeResolver,
                            )
                        }
                        else -> {
                            SingleValueResolver(
                                encoder = if (descriptor.mergeStrategy == MessageFieldMergeStrategy.REPLACE) {
                                    descriptor.encoder
                                } else {
                                    descriptor.valueEncoder
                                },
                                decoder = if (descriptor.mergeStrategy == MessageFieldMergeStrategy.REPLACE) {
                                    descriptor.decoder
                                } else {
                                    descriptor.valueDecoder
                                },
                                versionTreeResolver = versionTreeResolver,
                            )
                        }
                    }
                } as CrdtResolver<Any, N, V, C>
                if (descriptor.valueType == ValueType.OPTIONAL) {
                    @Suppress("UNCHECKED_CAST")
                    resolverCache.getOrPut(
                        SingleCacheKey(
                            type = descriptor.typeId,
                            optional = true,
                        )
                    ) {
                        OptionalAnyValueResolver(
                            decoder = if (descriptor.mergeStrategy == MessageFieldMergeStrategy.REPLACE) {
                                descriptor.decoder
                            } else {
                                descriptor.valueDecoder
                            },
                            encoder = descriptor.valueEncoder,
                            valueResolver = resolver,
                            versionTreeResolver = versionTreeResolver,
                        )
                    } as OptionalAnyValueResolver<Any, N, V, C>
                } else {
                    resolver
                }
            },
            versionTreeResolver = versionTreeResolver,
        )

        @Suppress("UNCHECKED_CAST")
        return MessageFieldResolver(
            binding = descriptor,
            localResolver = valueResolver as CrdtLocalResolver<Any, N, V, C>,
            incomingResolver = valueResolver as CrdtIncomingChangeResolver<Any, N, V, C>,
            deltaResolver = valueResolver as CrdtDeltaResolver<Any, N, V, C>,
            changeDecoder = valueResolver as CrdtPathChangeDecoder<Any, N, V, C>,
        )
    }

    fun <N, V, C> MessageFieldDescriptor<*, *, *>.toResolver(
        valueResolver: CrdtResolver<Any, N, V, C>,
        versionTreeResolver: VersionTreeResolver<N, V, C>,
    ): CrdtResolver<Any, N, V, C> {
        val collectionType = collectionType?.takeIf {
            mergeStrategy != MessageFieldMergeStrategy.REPLACE
        } ?: return valueResolver

        @Suppress("UNCHECKED_CAST")
        return resolverCache.getOrPut(typeId) {
            when (collectionType) {
                is CollectionType.Map -> {
                    collectionType.toAnyMapResolver(
                        encoder = encoder,
                        decoder = decoder,
                        valueResolver = valueResolver,
                        versionTreeResolver = versionTreeResolver,
                    )
                }
                is CollectionType.RepeatedId -> {
                    RepeatedIdResolver(
                        decoder = decoder as (ByteArray) -> List<Any>,
                        encoder = encoder,
                        keyTransformer = collectionType.repeatedKeyTransformer,
                        mapResolver = collectionType.mapType.toAnyMapResolver(
                            encoder = encoder,
                            decoder = decoder,
                            valueResolver = valueResolver,
                            versionTreeResolver = versionTreeResolver,
                        ),
                        versionTreeResolver = versionTreeResolver,
                    )
                }
                is CollectionType.Repeated -> {
                    RepeatedResolver(
                        decoder = decoder as (ByteArray) -> List<Any>,
                        encoder = encoder,
                        valueResolver = valueResolver,
                        versionTreeResolver = versionTreeResolver,
                    )
                }
            }
        } as CrdtResolver<Any, N, V, C>
    }

    @Suppress("UNCHECKED_CAST")
    fun <N, V, C> CollectionType.Map.toAnyMapResolver(
        encoder: (Any) -> ByteArray,
        decoder: (ByteArray) -> Any,
        valueResolver: CrdtResolver<Any, N, V, C>,
        versionTreeResolver: VersionTreeResolver<N, V, C>,
    ): CrdtResolver<Map<Any, Any>, N, V, C> =
        when (keyType) {
            KeyType.BOOL ->
                BooleanMapResolver(
                    config = this,
                    decoder = decoder as (ByteArray) -> Map<Boolean, Any>,
                    encoder = encoder,
                    valueResolver = valueResolver,
                    versionTreeResolver = versionTreeResolver,
                )
            KeyType.INT ->
                IntMapResolver(
                    config = this,
                    decoder = decoder as (ByteArray) -> Map<Int, Any>,
                    encoder = encoder,
                    valueResolver = valueResolver,
                    versionTreeResolver = versionTreeResolver,
                )
            KeyType.LONG ->
                LongMapResolver(
                    config = this,
                    decoder = decoder as (ByteArray) -> Map<Long, Any>,
                    encoder = encoder,
                    valueResolver = valueResolver,
                    versionTreeResolver = versionTreeResolver,
                )
            KeyType.STRING ->
                StringMapResolver(
                    config = this,
                    decoder = decoder as (ByteArray) -> Map<String, Any>,
                    encoder = encoder,
                    valueResolver = valueResolver,
                    versionTreeResolver = versionTreeResolver,
                )
        } as CrdtResolver<Map<Any, Any>, N, V, C>

    private data class SingleCacheKey(
        private val type: Any,
        private val optional: Boolean,
    )
}
