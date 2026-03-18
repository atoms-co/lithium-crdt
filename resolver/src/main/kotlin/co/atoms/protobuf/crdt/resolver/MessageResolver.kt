package co.atoms.protobuf.crdt.resolver

import co.atoms.protobuf.crdt.resolver.decoder.MessageChangeDecoder
import co.atoms.protobuf.crdt.resolver.delta.MessageDeltaResolver
import co.atoms.protobuf.crdt.resolver.descriptor.MessageBuilder
import co.atoms.protobuf.crdt.resolver.incoming.partial.MessageIncomingChangeResolver
import co.atoms.protobuf.crdt.resolver.local.MessageLocalResolver
import co.atoms.protobuf.crdt.resolver.version.ApplyChangesResult
import co.atoms.protobuf.crdt.resolver.version.ResolutionStrategy
import co.atoms.protobuf.crdt.resolver.version.resolutionStrategy
import co.atoms.protobuf.crdt.resolver.version.toApplyChangesResult

/**
 * Comprehensive CRDT resolver for message types with field-level conflict resolution.
 *
 * This interface combines multiple resolver capabilities to provide complete CRDT functionality
 * for protobuf-like message structures. It supports local writes, conflict resolution, delta
 * generation, change decoding, and incremental change application - all with fine-grained
 * field-level versioning.
 *
 * ## Key Capabilities
 *
 * ### Local Writes ([applyLocalWrite])
 * Processes local modifications by:
 * - Incrementing the local actor's version counter
 * - Generating a new version for the write operation
 * - Computing the delta changes to be synchronized with other replicas
 * - Only incrementing actor counters if the write actually changes the value
 *
 * ### Conflict Resolution ([resolveConflict])
 * Handles concurrent modifications by:
 * - Comparing version vectors to detect true conflicts
 * - Short-circuiting when vectors match (no actual conflict)
 * - Performing field-by-field resolution for genuine conflicts
 * - Merging version vectors from both replicas
 *
 * ### Change Deltas ([changeDelta])
 * Generates efficient synchronization deltas:
 * - Computes minimal changes needed to represent a value's state
 * - Uses version vectors to determine what changes are new
 * - Produces [ChangeEvent]s that can be transmitted to other replicas
 *
 * ### Change Application ([applyChanges])
 * Applies incoming changes incrementally:
 * - Processes lists of [ChangeEvent]s from remote replicas
 * - Merges incoming version vectors with local state
 * - Short-circuits when no new information is present
 * - Delegates to field-specific resolvers for fine-grained updates
 *
 * ### Change Decoding ([decodeChange])
 * Reconstructs change events from serialized form:
 * - Deserializes encoded values
 * - Associates them with path components and versions
 * - Enables network transmission of changes
 *
 * ## Type Parameters
 * @param M The message type (e.g., a protobuf message)
 * @param B The builder state type used during message construction
 * @param N The version node type that tracks field-level version metadata
 * @param V The version type (typically a comparable version for ordering)
 * @param C The path component type for navigating message field hierarchies
 * @param A The actor state type that tracks version vectors for all replicas
 *
 * ## Implementation Notes
 * - Implementations typically delegate to specialized resolvers for each field type
 * - Version nodes maintain a tree structure mirroring the message structure
 * - The actor state is only incremented when writes actually modify values
 * - Empty changes and no-op merges are optimized with early returns
 *
 * @see CrdtMessageResolver for the base CRDT operations
 * @see MessageIncomingChangeResolver for incremental change application
 * @see MessageDeltaResolver for delta generation
 */
interface MessageResolver<M, B, N, V, C, A> :
    CrdtMessageResolver<M, N, V, C, A>,
    CrdtResolver<M, N, V, C>,
    MessageLocalResolver<M, B, MessageBuilder<M, B>, N, V, C>,
    MessageIncomingChangeResolver<M, B, MessageBuilder<M, B>, N, V, C>,
    MessageDeltaResolver<M, B, MessageBuilder<M, B>, N, V, C>,
    MessageChangeDecoder<M, MessageBuilder<M, B>, N, V, C>,
    MessageActorAdapter<A, V> {
    override fun applyLocalWrite(
        currentValue: M?,
        currentNode: N?,
        currentActors: A?,
        newValue: M?,
        timestamp: Long
    ): ResolverDeltaResult<M, N, V, Boolean, C, A> = with(versionTreeResolver) {
        val context = ResolutionDeltaContext<N, C>()
        val newActors = incrementLocalActor(currentActors)
        val result = applyLocalWrite(
            currentValue = currentValue,
            currentNode = currentNode,
            currentVersion = currentNode?.versionValue ?: minVersion,
            newValue = newValue,
            newVersion = localVersion(
                actors = newActors,
                timestamp = timestamp
            ),
            context = context,
        )
        return ResolverDeltaResult(
            changes = context.result,
            mergeResult = result,
            // If we make a change use the incremented actors else return previous
            actors = if (result.resolution) newActors else currentActors ?: merge(currentActors, mapOf())
        )
    }

    override fun resolveConflict(
        localValue: M?,
        localNode: N?,
        localActors: A?,
        incomingValue: M?,
        incomingNode: N,
        incomingVersionVector: Map<Long, Long>
    ): ResolverDeltaResult<M, N, V, ResolutionStrategy, C, A> = with(versionTreeResolver) {
        if (localActors?.versionVector == incomingVersionVector) {
            return ResolverDeltaResult(
                actors = localActors,
                changes = listOf(),
                mergeResult = NodeMergeResult(
                    resolution = ResolutionStrategy.NO_CHANGE,
                    value = localValue,
                    node = localNode,
                ),
            )
        }

        val context = ResolutionDeltaContext<N, C>()
        val result = resolveConflict(
            localValue = localValue,
            localNode = localNode,
            localVersion = localNode?.versionValue ?: minVersion,
            incomingValue = incomingValue,
            incomingNode = incomingNode,
            incomingVersion = incomingNode.versionValue ?: minVersion,
            context = context,
        )
        return ResolverDeltaResult(
            actors = merge(localActors, incomingVersionVector),
            changes = context.result,
            mergeResult = result,
        )
    }

    override fun changeDelta(
        value: M?,
        node: N,
        versionVector: Map<Long, Long>
    ): List<ChangeEvent<*, N, C>> = with(versionTreeResolver) {
        val context = ResolutionDeltaContext<N, C>()
        changeDelta(
            value = value,
            node = node,
            version = node.versionValue ?: minVersion,
            versionVector = versionVector,
            context = context,
        )
        return context.result
    }

    override fun decodeChange(
        encodedValue: ByteArray?,
        pathComponents: List<C>,
        versionNode: N
    ): ChangeEvent<*, N, C> {
        return decodeChange(
            depth = 0,
            encodedValue = encodedValue,
            pathComponents = pathComponents,
            versionNode = versionNode,
        )
    }

    override fun applyChanges(
        localValue: M?,
        localNode: N?,
        localActors: A?,
        incomingChanges: List<ChangeEvent<*, N, C>>,
        incomingBaselineActors: Map<Long, Long>,
    ): ResolverDeltaResult<M, N, V, ApplyChangesResult, C, A> = with(versionTreeResolver) {
        val deltaResolution = (localActors?.versionVector ?: mapOf()).resolutionStrategy(incomingBaselineActors)
        val shouldApply = when (deltaResolution) {
            ResolutionStrategy.NO_CHANGE,
            ResolutionStrategy.LOCAL -> true
            ResolutionStrategy.INCOMING,
            ResolutionStrategy.MERGED_VALUES -> false
        }

        // Baseline mismatch: local state has diverged from the baseline these changes were computed against.
        // Return REJECTED to signal the caller should fall back to full-state resolveConflict.
        if (!shouldApply) {
            return ResolverDeltaResult(
                actors = merge(localActors, mapOf()),
                changes = listOf(),
                mergeResult = NodeMergeResult(
                    resolution = ApplyChangesResult.REJECTED,
                    value = localValue,
                    node = localNode,
                ),
            )
        }

        val incomingVersions = incomingChanges.map { it.versionNode.maxVersion(minVersion) }.toVersionVector()
        val merged = merge(localActors, incomingVersions)

        if (merged.versionVector == localActors?.versionVector) {
            return ResolverDeltaResult(
                actors = localActors,
                changes = listOf(),
                mergeResult = NodeMergeResult(
                    resolution = ApplyChangesResult.UNCHANGED,
                    value = localValue,
                    node = localNode,
                ),
            )
        }

        val context = ResolutionDeltaContext<N, C>()
        val result = applyChanges(
            depth = 0,
            localValue = localValue,
            localNode = localNode,
            localVersion = localNode?.versionValue ?: minVersion,
            changes = incomingChanges,
            context = context,
        )
        return ResolverDeltaResult(
            actors = merged,
            changes = context.result,
            mergeResult = NodeMergeResult(
                resolution = result.resolution.toApplyChangesResult(),
                value = result.value,
                node = result.node,
            ),
        )
    }
}

