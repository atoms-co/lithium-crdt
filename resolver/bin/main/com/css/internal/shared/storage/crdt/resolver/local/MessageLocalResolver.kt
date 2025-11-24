package com.css.internal.shared.storage.crdt.resolver.local

import com.css.internal.shared.storage.crdt.resolver.NodeMergeResult
import com.css.internal.shared.storage.crdt.resolver.ResolutionDeltaContext
import com.css.internal.shared.storage.crdt.resolver.descriptor.MessageBuilder
import com.css.internal.shared.storage.crdt.resolver.descriptor.MessageFieldResolver
import com.css.internal.shared.storage.crdt.resolver.descriptor.RuntimeMessageAdapter
import com.css.internal.shared.storage.crdt.resolver.internal.OneOf
import com.css.internal.shared.storage.crdt.resolver.internal.resolved
import com.css.internal.shared.storage.crdt.resolver.internal.setValue
import com.css.internal.shared.storage.crdt.resolver.withPath

/**
 * CRDT Local Write resolver for Wire protobuf messages.
 *
 * Handles local modifications to Wire messages by tracking field-level versions.
 * Each field gets its own version when modified, enabling fine-grained sync
 * and conflict resolution.
 *
 * Key behaviors:
 * - Only changed fields get new versions
 * - OneOf fields ensure only one field per group has a value
 * - Nested messages and collections are handled recursively
 * - Supports creation, updates, and deletion (tombstones)
 */
interface MessageLocalResolver<M, S, B : MessageBuilder<M, S>, N, V, C> :
    CrdtLocalResolver<M, N, V, C>,
    RuntimeMessageAdapter<M, S, B, N, V, C> {
    override fun applyLocalWrite(
        currentValue: M?,
        currentNode: N?,
        currentVersion: V,
        newValue: M?,
        newVersion: V,
        context: ResolutionDeltaContext<N, C>,
    ): NodeMergeResult<M, N, Boolean> = with(versionTreeResolver) {
        // Fast path: no change
        if (currentValue == newValue) {
            return NodeMergeResult(
                resolution = false,
                value = currentValue,
                node = currentNode ?: createVersionNode(currentVersion),
            )
        }

        return when {
            // Document deletion (tombstone with version)
            newValue == null ||
                // Document creation
                currentValue == null -> {
                // Ensure new version is after current to maintain causality
                val version = newVersion.ensureAfter(previous = currentVersion)
                val node = createVersionNode(version = version)
                context.addChange(
                    newValue = newValue,
                    versionNode = node,
                    encoder = encoder
                )
                NodeMergeResult(
                    resolution = true,
                    value = newValue,
                    node = node,
                )
            }
            // Field-by-field update
            else -> processFieldByField(
                currentValue = currentValue,
                currentNode = currentNode,
                currentVersion = currentVersion,
                newValue = newValue,
                newVersion = newVersion,
                context = context,
            )
        }
    }

    /**
     * Process each protobuf field independently, updating versions only for changed fields.
     *
     * This enables efficient sync as unchanged fields retain their versions.
     */
    private fun processFieldByField(
        currentValue: M,
        currentNode: N?,
        currentVersion: V,
        newValue: M,
        newVersion: V,
        context: ResolutionDeltaContext<N, C>,
    ): NodeMergeResult<M, N, Boolean> = with(versionTreeResolver) {
        val existingFields = currentNode?.fields

        // Copy-on-write for field versions
        val fieldVersions = existingFields?.toMutableMap() ?: mutableMapOf()
        val oneOfResults = mutableMapOf<String, MutableList<OneOf<M, B, Boolean, N, V, C>>>()

        val builder = newBuilder()
        var resolution = false

        // Process each field with type-specific resolver
        fields.forEach {
            val field = it.value
            val fieldBinding = field.binding
            val fieldNode = fieldVersions[fieldBinding.tag]

            // Delegate to field-specific resolver
            val fieldResult = context.withPath(versionTreeResolver.createPathComponentField(fieldBinding.tag)) {
                field.setValue(
                    currentValue = currentValue,
                    fieldNode = fieldNode,
                    // Use field's version if available, otherwise inherit
                    currentVersion = fieldNode?.versionValue ?: currentVersion,
                    newValue = newValue,
                    newVersion = newVersion,
                    context = context,
                )
            }

            val oneOfName = fieldBinding.oneOfName
            if (oneOfName != null) {
                // Collect OneOf fields for group resolution
                val parentVersion = if (fieldResult.resolution) {
                    // Changed fields get new version
                    newVersion.ensureAfter(previous = currentVersion)
                } else {
                    currentVersion
                }
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
                resolution = builder.setValue(
                    fieldBinding = fieldBinding,
                    fieldResult = fieldResult,
                    fieldVersions = fieldVersions
                ) || resolution
            }
        }

        // Resolve OneOf groups - only the field being set keeps its value
        resolved(oneOfResults) { (fieldBinding, fieldResult) ->
            resolution = builder.setValue(
                fieldBinding = fieldBinding,
                fieldResult = fieldResult,
                fieldVersions = fieldVersions
            ) || resolution
        }

        // Calculate final version min version, current if not
        val version = fieldVersions.values.minVersion(currentVersion)
        // Optimization: remove leaf nodes that would inherit parent version
        fieldVersions.entries.removeAll { it.value.versionValue == version && it.value.isLeaf() }

        return NodeMergeResult(
            resolution = resolution,
            value = try {
                builder.build()
            } catch (e: Throwable) {
                throw IllegalArgumentException("$this", e)
            },
            node = createVersionNodeStruct(
                version = version,
                fields = fieldVersions,
            )
        )
    }
}

/**
 * Processes a single field for local write.
 *
 * Extracts field values and delegates to the appropriate resolver
 * based on field type (primitive, message, collection).
 */
private fun <M, B, N, V, C> MessageFieldResolver<M, B, Any, N, V, C>.setValue(
    currentValue: M,
    fieldNode: N?,
    currentVersion: V,
    newValue: M,
    newVersion: V,
    context: ResolutionDeltaContext<N, C>,
): NodeMergeResult<Any, N, Boolean> {
    val fieldBinding = binding
    val currentFieldValue = fieldBinding[currentValue]
    val newFieldValue = fieldBinding[newValue]

    // Delegate to field-specific resolver (primitive, message, or collection resolver)
    return localResolver.applyLocalWrite(
        currentValue = currentFieldValue,
        currentNode = fieldNode,
        currentVersion = currentVersion,
        newValue = newFieldValue,
        newVersion = newVersion,
        context = context,
    )
}
