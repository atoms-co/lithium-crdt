package co.atoms.lithium.crdt.resolver.incoming

import co.atoms.lithium.crdt.resolver.NodeMergeResult
import co.atoms.lithium.crdt.resolver.ResolutionDeltaContext
import co.atoms.lithium.crdt.resolver.descriptor.MessageBuilder
import co.atoms.lithium.crdt.resolver.descriptor.RuntimeMessageAdapter
import co.atoms.lithium.crdt.resolver.internal.OneOf
import co.atoms.lithium.crdt.resolver.internal.resolved
import co.atoms.lithium.crdt.resolver.internal.setValue
import co.atoms.lithium.crdt.resolver.version.ResolutionStrategy
import co.atoms.lithium.crdt.resolver.version.ResolutionStrategy.INCOMING
import co.atoms.lithium.crdt.resolver.version.ResolutionStrategy.LOCAL
import co.atoms.lithium.crdt.resolver.version.ResolutionStrategy.MERGED_VALUES
import co.atoms.lithium.crdt.resolver.version.ResolutionStrategy.NO_CHANGE
import co.atoms.lithium.crdt.resolver.version.VersionTreeResolver
import co.atoms.lithium.crdt.resolver.withPath

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
interface MessageIncomingResolver<M, S, B : MessageBuilder<M, S>, N, V, C> :
    CrdtIncomingResolver<M, N, V, C>,
    RuntimeMessageAdapter<M, S, B, N, V, C> {

    override fun resolveConflict(
        localValue: M?,
        localNode: N?,
        localVersion: V,
        incomingValue: M?,
        incomingNode: N?,
        incomingVersion: V,
        context: ResolutionDeltaContext<N, C>,
    ): NodeMergeResult<M, N, ResolutionStrategy> = with(versionTreeResolver) {
        return when {
            // Fast path: no change
            localValue == incomingValue && localNode == incomingNode && localVersion == incomingVersion ->
                NodeMergeResult(
                    value = localValue,
                    node = localNode ?: createVersionNode(localVersion),
                    resolution = NO_CHANGE
                )
            // Fast path: local is null, check versions
            localValue == null -> emptyValueMerge(
                node = localNode,
                version = localVersion,
                resolution = LOCAL,
                otherValue = incomingValue,
                otherNode = incomingNode,
                otherVersion = incomingNode.maxVersion(incomingVersion),
                otherResolution = INCOMING,
            ).also {
                if (it.resolution == INCOMING) {
                    context.addChange(
                        newValue = it.value,
                        versionNode = it.node ?: incomingNode ?: createVersionNode(incomingVersion),
                        encoder = encoder
                    )
                }
            }
            // Fast path: incoming is null, check versions
            incomingValue == null ->
                emptyValueMerge(
                    node = incomingNode,
                    version = incomingVersion,
                    resolution = INCOMING,
                    otherValue = localValue,
                    otherNode = localNode,
                    otherVersion = localNode.maxVersion(localVersion),
                    otherResolution = LOCAL,
                ).also {
                    if (it.resolution == INCOMING) {
                        context.addChange(
                            newValue = it.value,
                            versionNode = it.node ?: createVersionNode(incomingVersion),
                            encoder = encoder
                        )
                    }
                }
            // Field-by-field merge required
            else -> processFieldByField(
                localValue = localValue,
                localNode = localNode,
                localVersion = localVersion,
                incomingValue = incomingValue,
                incomingNode = incomingNode,
                incomingVersion = incomingVersion,
                context = context,
            )
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
        incomingValue: M,
        incomingNode: N?,
        incomingVersion: V,
        context: ResolutionDeltaContext<N, C>,
    ): NodeMergeResult<M, N, ResolutionStrategy> = with(versionTreeResolver) {
        val builder = newBuilder()

        // Extract per-field version information
        val localFields: Map<Int, N> = localNode?.fields ?: mapOf()
        val incomingFields: Map<Int, N> = incomingNode?.fields ?: mapOf()

        var resolution: ResolutionStrategy = NO_CHANGE
        // No order required for field merging
        val resultFields = HashMap<Int, N>(fields.size)
        // Group OneOf fields for special handling
        val oneOfResults = mutableMapOf<String, MutableList<OneOf<M, B, ResolutionStrategy, N, V, C>>>()

        // Process each field with type-specific resolver
        fields.forEach {
            val field = it.value
            val fieldBinding = field.binding
            val oneOfName = field.binding.oneOfName
            val localFieldNode = localFields[fieldBinding.tag]
            val incomingFieldNode = incomingFields[fieldBinding.tag]
            val childLocalVersion = localFieldNode?.versionValue
            val childIncomingVersion = incomingFieldNode?.versionValue

            // Delegate to field-specific resolver (primitive, message, or collection)
            val fieldResult = context.withPath(versionTreeResolver.createPathComponentField(fieldBinding.tag)) {
                field.incomingResolver.resolveConflict(
                    localValue = fieldBinding[localValue],
                    localNode = localFieldNode,
                    // Use field's version if available, otherwise inherit from message
                    localVersion = childLocalVersion ?: localVersion,
                    incomingValue = fieldBinding[incomingValue],
                    incomingNode = incomingFieldNode,
                    incomingVersion = childIncomingVersion ?: incomingVersion,
                    context = context,
                )
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
                INCOMING -> incomingNode ?: createVersionNode(incomingVersion)
                MERGED_VALUES -> {
                    val version = resultFields.values.minVersion(localVersion.coerceAtLeast(incomingVersion))
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

/**
 * Handles merge when one value is null (deletion or non-existence).
 *
 * The non-null value wins if its version is higher, otherwise null wins
 * (representing deletion or continued non-existence).
 */
private fun <T, N, V, C> VersionTreeResolver<N, V, C>.emptyValueMerge(
    node: N?,
    version: V,
    resolution: ResolutionStrategy,
    otherValue: T?,
    otherNode: N?,
    otherVersion: V,
    otherResolution: ResolutionStrategy,
): NodeMergeResult<T, N, ResolutionStrategy> = if (version < otherVersion) {
    // Other version is newer
    NodeMergeResult(
        value = otherValue,
        node = otherNode ?: createVersionNode(otherVersion),
        resolution = otherResolution
    )
} else {
    // Our version is newer or equal
    NodeMergeResult(
        value = null,
        node = node ?: createVersionNode(version),
        resolution = resolution
    )
}
