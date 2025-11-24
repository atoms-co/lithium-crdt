package com.css.internal.shared.storage.crdt.resolver.delta

import com.css.internal.shared.storage.crdt.resolver.ResolutionDeltaContext
import com.css.internal.shared.storage.crdt.resolver.descriptor.MessageBuilder
import com.css.internal.shared.storage.crdt.resolver.descriptor.RuntimeMessageAdapter
import com.css.internal.shared.storage.crdt.resolver.withPath

interface MessageDeltaResolver<M, S, B : MessageBuilder<M, S>, N, V, C> :
    CrdtDeltaResolver<M, N, V, C>,
    RuntimeMessageAdapter<M, S, B, N, V, C> {

    override fun changeDelta(
        value: M?,
        node: N?,
        version: V,
        versionVector: Map<Long, Long>,
        context: ResolutionDeltaContext<N, C>
    ) {
        with(versionTreeResolver) {
            if (value == null) {
                val nodeVersion = node?.versionValue ?: version
                if (nodeVersion !in versionVector) {
                    context.addChange(
                        newValue = value,
                        versionNode = node ?: createVersionNode(nodeVersion),
                        encoder = encoder,
                    )
                }
            } else {
                processFieldByField(
                    localValue = value,
                    localNode = node,
                    localVersion = version,
                    versionVector = versionVector,
                    context = context,
                )
            }
        }
    }

    /**
     * Process each protobuf field independently with its own version tracking.
     *
     * This is the core of the CRDT implementation - each field can evolve
     * independently, allowing partial updates and efficient sync.
     */
    private fun processFieldByField(
        localValue: M,
        localNode: N?,
        localVersion: V,
        versionVector: Map<Long, Long>,
        context: ResolutionDeltaContext<N, C>,
    ) = with(versionTreeResolver) {
        // Extract per-field version information
        val localFields: Map<Int, N> = localNode?.fields ?: mapOf()

        // Process each field with type-specific resolver
        fields.forEach {
            val field = it.value
            val fieldBinding = field.binding
            val localFieldNode = localFields[fieldBinding.tag]
            val childLocalVersion = localFieldNode?.versionValue

            context.withPath(versionTreeResolver.createPathComponentField(fieldBinding.tag)) {
                field.deltaResolver.changeDelta(
                    value = fieldBinding[localValue],
                    node = localFieldNode,
                    // Use field's version if available, otherwise inherit from message
                    version = childLocalVersion ?: localVersion,
                    versionVector = versionVector,
                    context = context,
                )
            }
        }
    }
}
