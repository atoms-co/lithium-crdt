package com.css.protobuf.crdt.resolver

import com.css.protobuf.crdt.resolver.version.ResolutionStrategy

interface CrdtMessageResolver<T, N, V, C, A> :
    CrdtMessageLocalResolver<T, N, V, C, A>,
    CrdtMessageIncomingResolver<T, N, V, C, A>,
    CrdtMessageDeltaResolver<T, N, V, C>,
    CrdtMessagePathChangeDecoder<T, N, V, C>

interface CrdtMessageLocalResolver<T, N, V, C, A> {
    /**
     * Apply a local write operation with a new version vector.
     *
     * For local writes, you provide a version that gets applied to this
     * specific location. The resolver handles updating the VersionNode structure
     * appropriately, creating a new node if the value changed.
     *
     * @param currentValue The current value stored locally
     * @param currentNode The current node (contains version + structure info)
     * @param newValue The new value being written locally
     * @return Result indicating whether the value was updated and the final VersionNode
     */
    fun applyLocalWrite(
        currentValue: T?,
        currentNode: N?,
        currentActors: A?,
        newValue: T?,
        timestamp: Long,
    ): ResolverDeltaResult<T, N, V, Boolean, C, A>
}

interface CrdtMessageIncomingResolver<T, N, V, C, A> {
    /**
     * Resolve a conflict between local and incoming values with their VersionNodes.
     *
     * For conflict resolution, both values come with full VersionNode structures
     * that may contain child version information. The resolver merges both the
     * values and their version structures according to CRDT semantics.
     *
     * @param localValue The current local value
     * @param localNode The current node (contains version + structure info)
     * @param incomingValue The incoming value from another source
     * @param incomingNode The VersionNode of the incoming value (contains version + children)
     * @return Result of the conflict resolution with the winning/merged value and VersionNode
     */
    fun resolveConflict(
        localValue: T?,
        localNode: N?,
        localActors: A?,
        incomingValue: T?,
        incomingNode: N,
        incomingVersionVector: Map<Long, Long>,
    ): ResolverDeltaResult<T, N, V, ResolutionStrategy, C, A>

    fun applyChanges(
        localValue: T?,
        localNode: N?,
        localActors: A?,
        incomingChanges: List<ChangeEvent<*, N, C>>,
        incomingBaselineActors: Map<Long, Long>,
    ): ResolverDeltaResult<T, N, V, ResolutionStrategy, C, A>
}

interface CrdtMessageDeltaResolver<T, N, V, C> {
    fun changeDelta(
        value: T?,
        node: N,
        versionVector: Map<Long, Long>,
    ): List<ChangeEvent<*, N, C>>
}

/**
 * Base interface for decoders that reconstruct ChangeEvent instances from wire format.
 *
 * This is the reverse operation of encoding changes for transmission over the wire.
 * Given path components, encoded bytes, and version information, decoders navigate through
 * the data structure to reconstruct the change.
 *
 * ## Wire Format
 *
 * Changes are transmitted as:
 * - **pathComponents**: List of path components describing the field path
 * - **encodedValue**: The value encoded as bytes (null for deletions)
 * - **version**: Version information for the change
 *
 * ## Decoder Hierarchy
 *
 * Different decoder implementations handle different field types:
 * - **SingleValueChangeDecoder**: For primitive/single value fields
 * - **MapChangeDecoder**: For map fields (navigates by key)
 * - **RepeatedChangeDecoder**: For repeated fields (navigates by index)
 * - **MessageChangeDecoder**: For message fields (navigates by field number)
 *
 * @param V The version type (e.g., Version, Long)
 * @param C The path component type (e.g., PathComponent)
 */
interface CrdtMessagePathChangeDecoder<T, N, V, C> {
    /**
     * Decodes a change from its wire representation back into a ChangeEvent.
     *
     * @param pathComponents Remaining path components to navigate (empty if at target)
     * @param encodedValue The encoded value as bytes (null for deletions)
     * @param versionNode The version information for this change
     * @return A ChangeEvent with the decoded value and metadata
     * @throws IllegalArgumentException if the path is invalid or decoding fails
     */
    fun decodeChange(
        encodedValue: ByteArray?,
        pathComponents: List<C>,
        versionNode: N,
    ): ChangeEvent<*, N, C>
}
