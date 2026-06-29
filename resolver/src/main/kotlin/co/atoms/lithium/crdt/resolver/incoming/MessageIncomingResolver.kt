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
        // Tags whose resolved value is absent (empty/default). Used below to protect
        // reset/tombstone nodes from the leaf-collapse optimization.
        val absentResultTags = HashSet<Int>()
        // Group OneOf fields for special handling
        val oneOfResults = mutableMapOf<String, MutableList<OneOf<M, B, ResolutionStrategy, N, V, C>>>()

        // Process each field with type-specific resolver
        fields.forEach {
            val field = it.value
            val fieldBinding = field.binding
            val oneOfName = field.binding.oneOfName
            val localFieldNode = localFields[fieldBinding.tag]
            val incomingFieldNode = incomingFields[fieldBinding.tag]
            val localFieldValue = fieldBinding[localValue]
            val incomingFieldValue = fieldBinding[incomingValue]

            // A field with no explicit node and an absent value was never written on that
            // side. It must not inherit the (possibly higher) parent version and overwrite
            // the other side's real write - treat it as the lowest version instead.
            val localNeverSet = localFieldNode == null && fieldBinding.isAbsent(localFieldValue)
            val incomingNeverSet = incomingFieldNode == null && fieldBinding.isAbsent(incomingFieldValue)
            // Never written on either side: nothing to merge, leave it unset (no node).
            if (localNeverSet && incomingNeverSet) return@forEach

            val childLocalVersion = localFieldNode?.versionValue
                ?: if (localNeverSet) minVersion else localVersion
            val childIncomingVersion = incomingFieldNode?.versionValue
                ?: if (incomingNeverSet) minVersion else incomingVersion

            // Delegate to field-specific resolver (primitive, message, or collection)
            val fieldResult = context.withPath(versionTreeResolver.createPathComponentField(fieldBinding.tag)) {
                field.incomingResolver.resolveConflict(
                    localValue = localFieldValue,
                    localNode = localFieldNode,
                    localVersion = childLocalVersion,
                    incomingValue = incomingFieldValue,
                    incomingNode = incomingFieldNode,
                    incomingVersion = childIncomingVersion,
                    context = context,
                )
            }

            if (fieldBinding.isAbsent(fieldResult.value)) {
                absentResultTags.add(fieldBinding.tag)
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
            if (fieldBinding.isAbsent(fieldResult.value)) {
                absentResultTags.add(fieldBinding.tag)
            }
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
                    val version = resultFields.values.minVersion(
                        if (localVersion < incomingVersion) localVersion else incomingVersion
                    )
                    // Optimization: a present-valued leaf at the parent version inherits it
                    // implicitly, so drop it. Keep absent-valued leaves (reset tombstones) so
                    // their version survives and isn't later mistaken for "never set".
                    resultFields.entries.removeAll { (tag, node) ->
                        node.isLeaf() && node.versionValue == version && tag !in absentResultTags
                    }
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
