package com.css.protobuf.crdt.resolver.incoming.partial

import com.css.protobuf.crdt.resolver.ChangeEvent
import com.css.protobuf.crdt.resolver.NodeMergeResult
import com.css.protobuf.crdt.resolver.ResolutionDeltaContext
import com.css.protobuf.crdt.resolver.descriptor.MessageBuilder
import com.css.protobuf.crdt.resolver.descriptor.RuntimeMessageAdapter
import com.css.protobuf.crdt.resolver.incoming.MessageIncomingResolver
import com.css.protobuf.crdt.resolver.internal.OneOf
import com.css.protobuf.crdt.resolver.internal.resolved
import com.css.protobuf.crdt.resolver.internal.setValue
import com.css.protobuf.crdt.resolver.version.ResolutionStrategy
import com.css.protobuf.crdt.resolver.version.ResolutionStrategy.INCOMING
import com.css.protobuf.crdt.resolver.version.ResolutionStrategy.LOCAL
import com.css.protobuf.crdt.resolver.version.ResolutionStrategy.MERGED_VALUES
import com.css.protobuf.crdt.resolver.version.ResolutionStrategy.NO_CHANGE
import com.css.protobuf.crdt.resolver.withPath

/**
 * CRDT conflict resolver for protobuf messages.
 *
 * Provides field-level Conflict-free Replicated Data Type semantics for messages
 * using Last-Write-Wins resolution with version vectors. Each field can have its own
 * version, allowing fine-grained updates and efficient sync.
 *
 * Key features:
 * - Field-level versioning and conflict resolution
 * - OneOf field group handling (ensures only one field per group has a value)
 * - Recursive resolution for nested messages
 * - Support for collections (maps, lists) with element-level resolution
 */
interface MessageIncomingChangeResolver<M, S, B : MessageBuilder<M, S>, N, V, C> :
    CrdtIncomingChangeResolver<M, N, V, C>,
    MessageIncomingResolver<M, S, B, N, V, C>,
    RuntimeMessageAdapter<M, S, B, N, V, C> {

    override fun applyChanges(
        depth: Int,
        localValue: M?,
        localNode: N?,
        localVersion: V,
        changes: List<ChangeEvent<*, N, C>>,
        context: ResolutionDeltaContext<N, C>
    ): NodeMergeResult<M, N, ResolutionStrategy> = with(versionTreeResolver) {
        if (changes.isEmpty()) {
            return NodeMergeResult(
                value = localValue,
                node = localNode ?: createVersionNode(localVersion),
                resolution = NO_CHANGE
            )
        }

        val firstChange = changes.first()
        if (depth == 0 && firstChange.pathComponents.isEmpty()) {
            @Suppress("UNCHECKED_CAST")
            resolveConflict(
                localValue = localValue,
                localNode = localNode,
                localVersion = localVersion,
                incomingValue = firstChange.value as? M,
                incomingNode = firstChange.versionNode,
                incomingVersion = firstChange.versionNode.versionValue ?: minVersion,
                context = context,
            )
        } else {
            processFieldByField(
                depth = depth + 1,
                localValue = localValue,
                localNode = localNode,
                localVersion = localVersion,
                changes = changes.groupBy {
                    it.pathComponents[depth].fieldNumber ?: throw IllegalArgumentException("No field number for change")
                },
                context = context,
            )
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun processFieldByField(
        depth: Int,
        localValue: M?,
        localNode: N?,
        localVersion: V,
        changes: Map<Int, List<ChangeEvent<*, N, C>>>,
        context: ResolutionDeltaContext<N, C>
    ): NodeMergeResult<M, N, ResolutionStrategy> = with(versionTreeResolver) {
        val builder = newBuilder()

        // Extract per-field version information
        val localFields: Map<Int, N> = localNode?.fields ?: mapOf()

        var resolution: ResolutionStrategy = NO_CHANGE
        // No order required for field merging
        val resultFields = HashMap<Int, N>(fields.size)
        // Group OneOf fields for special handling
        val oneOfResults = mutableMapOf<String, MutableList<OneOf<M, B, ResolutionStrategy, N, V, C>>>()

        // Process each field with type-specific resolver
        fields.forEach { entry ->
            val fieldChanges = changes[entry.key]

            val field = entry.value
            val fieldBinding = field.binding
            val oneOfName = field.binding.oneOfName
            val localFieldNode = localFields[fieldBinding.tag]
            val localFieldValue = localValue?.let { fieldBinding[it] }
            var incomingVersion = versionTreeResolver.minVersion

            // Delegate to field-specific resolver (primitive, message, or collection)
            val fieldResult = if (fieldChanges.isNullOrEmpty()) {
                NodeMergeResult(
                    resolution = NO_CHANGE,
                    value = localFieldValue,
                    node = localFieldNode ?: createVersionNode(localVersion)
                )
            } else {
                val childLocalVersion = localFieldNode?.versionValue
                context.withPath(versionTreeResolver.createPathComponentField(fieldBinding.tag)) {
                    val firstChange = fieldChanges.first()
                    incomingVersion = firstChange.versionNode?.versionValue ?: incomingVersion
                    if (firstChange.pathComponents.size == depth) {
                        field.incomingResolver.resolveConflict(
                            localValue = localFieldValue,
                            localNode = localFieldNode,
                            localVersion = childLocalVersion ?: localVersion,
                            incomingValue = firstChange.value,
                            incomingNode = firstChange.versionNode,
                            incomingVersion = incomingVersion,
                            context = context,
                        )
                    } else {
                        field.incomingResolver.applyChanges(
                            depth = depth,
                            localValue = localFieldValue,
                            localNode = localFieldNode,
                            localVersion = childLocalVersion ?: localVersion,
                            changes = fieldChanges,
                            context = context,
                        ).also {
                            if (it.resolution == INCOMING) {
                                it.node?.versionValue?.let { version ->
                                    incomingVersion = version
                                }
                            }
                        }
                    }
                }
            }

            if (oneOfName != null) {
                // Collect OneOf fields for group resolution
                val parentVersion = fieldResult.resolution.resolve(
                    local = localVersion,
                    incoming = incomingVersion
                )
                oneOfResults.getOrPut(oneOfName) {
                    mutableListOf()
                }.add(
                    OneOf(
                        binding = fieldBinding,
                        result = fieldResult,
                        parentVersion = parentVersion,
                        version = fieldResult.node.maxVersion(parentVersion)
                    )
                )
            } else {
                // Regular field - apply immediately
                resolution += builder.setValue(
                    fieldBinding = fieldBinding,
                    fieldResult = fieldResult,
                    fieldVersions = resultFields
                )
            }
        }

        // Resolve OneOf groups - only highest versioned field keeps its value
        resolved(oneOfResults) { (fieldBinding, fieldResult) ->
            resolution += builder.setValue(
                fieldBinding = fieldBinding,
                fieldResult = fieldResult,
                fieldVersions = resultFields
            )
        }

        return NodeMergeResult(
            resolution = resolution,
            value = try {
                builder.build()
            } catch (e: Throwable) {
                throw IllegalArgumentException("$this $builder", e)
            },
            node = when (resolution) {
                NO_CHANGE,
                LOCAL -> localNode ?: createVersionNode(localVersion)
                INCOMING,
                MERGED_VALUES -> {
                    val version = resultFields.values.minVersion(localVersion)
                    // Optimization: remove leaf nodes with same version as parent
                    // They inherit the parent version implicitly
                    resultFields.entries.removeAll { it.value.versionValue == version && it.value.isLeaf() }
                    createVersionNodeStruct(
                        version = version,
                        fields = resultFields
                    )
                }
            }
        )
    }
}
