package co.atoms.protobuf.crdt.resolver

import co.atoms.protobuf.crdt.resolver.version.ApplyChangesResult
import co.atoms.protobuf.crdt.resolver.version.ResolutionStrategy

/**
 * Combined interface for full CRDT message operations.
 *
 * This interface unifies all CRDT capabilities required for distributed document synchronization:
 * - **Local writes**: Apply user changes with automatic versioning via [CrdtMessageLocalResolver]
 * - **Conflict resolution**: Merge divergent states from other replicas via [CrdtMessageIncomingResolver]
 * - **Delta generation**: Compute minimal changesets for efficient sync via [CrdtMessageDeltaResolver]
 * - **Change decoding**: Reconstruct changes from serialized format via [CrdtMessagePathChangeDecoder]
 *
 * ## Type Parameters
 * @param T The message type (e.g., a protobuf message)
 * @param N The version node type tracking field-level version metadata
 * @param V The version type (comparable for ordering, e.g., timestamp + actor ID)
 * @param C The path component type for navigating message field hierarchies
 * @param A The actor state type tracking version vectors for all replicas
 *
 * @see MessageResolver for the full implementation with builder support
 */
interface CrdtMessageResolver<T, N, V, C, A> :
    CrdtMessageLocalResolver<T, N, V, C, A>,
    CrdtMessageIncomingResolver<T, N, V, C, A>,
    CrdtMessageDeltaResolver<T, N, V, C>,
    CrdtMessagePathChangeDecoder<T, N, V, C>

/**
 * Interface for applying local write operations to CRDT documents.
 *
 * Local writes represent user-initiated changes on this device. Unlike conflict resolution
 * which merges two complete states, local writes apply a new value with a freshly generated
 * version, producing a delta of changes that can be synchronized to other replicas.
 *
 * @param T The message type
 * @param N The version node type
 * @param V The version type
 * @param C The path component type
 * @param A The actor state type
 */
interface CrdtMessageLocalResolver<T, N, V, C, A> {
    /**
     * Apply a local write operation, generating a new version and computing the delta.
     *
     * This method handles user-initiated modifications by:
     * 1. Incrementing the local actor's version counter (only if the value actually changes)
     * 2. Generating a new version using the provided timestamp and actor ID
     * 3. Computing field-by-field differences between current and new values
     * 4. Returning the changes as a delta that can be transmitted to other replicas
     *
     * The version is only applied to fields that actually changed. Unchanged fields
     * retain their existing versions, preserving causality information.
     *
     * @param currentValue The current value stored locally (null for new documents)
     * @param currentNode The current version node tracking field versions (null for new documents)
     * @param currentActors The current actor state with version vector (null initializes new actor state)
     * @param newValue The new value to write (null to delete/tombstone the document)
     * @param timestamp The timestamp component for the new version (typically wall clock time)
     * @return Result containing:
     *         - `mergeResult.resolution`: true if any field changed, false if values were identical
     *         - `mergeResult.value`: The written value (same as newValue)
     *         - `mergeResult.node`: Updated version node with new versions for changed fields
     *         - `changes`: List of [ChangeEvent]s representing the delta for synchronization
     *         - `actors`: Updated actor state (version incremented only if changes occurred)
     */
    fun applyLocalWrite(
        currentValue: T?,
        currentNode: N?,
        currentActors: A?,
        newValue: T?,
        timestamp: Long,
    ): ResolverDeltaResult<T, N, V, Boolean, C, A>
}

/**
 * Interface for resolving conflicts with incoming state from other replicas.
 *
 * This interface handles synchronization scenarios where complete state snapshots
 * arrive from other devices. It supports both full-state conflict resolution
 * ([resolveConflict]) and delta-based change application ([applyChanges]).
 *
 * @param T The message type
 * @param N The version node type
 * @param V The version type
 * @param C The path component type
 * @param A The actor state type
 */
interface CrdtMessageIncomingResolver<T, N, V, C, A> {
    /**
     * Resolve a conflict between local and incoming states using full-state comparison.
     *
     * This method performs field-by-field Last-Write-Wins conflict resolution between
     * two complete document states. It handles arbitrary divergence between replicas,
     * including scenarios where both sides have made independent changes.
     *
     * ## Resolution Process
     *
     * 1. Short-circuit if version vectors are identical (no actual conflict)
     * 2. Recursively compare each field's version in local vs incoming
     * 3. For each field, keep the value with the higher version
     * 4. Merge version vectors (max of each actor's version)
     * 5. Return the merged result with appropriate [ResolutionStrategy]
     *
     * ## When to Use
     *
     * Use this method when:
     * - Receiving a full document state from another replica
     * - The sender and receiver may have diverged arbitrarily
     * - You need guaranteed correctness regardless of message ordering
     *
     * For more efficient delta-based sync when baselines match, see [applyChanges].
     *
     * @param localValue The current local value (null if document doesn't exist locally)
     * @param localNode The current version node (null if document doesn't exist locally)
     * @param localActors The local actor state with version vector
     * @param incomingValue The complete incoming value from another replica
     * @param incomingNode The complete version node tree from the incoming replica
     * @param incomingVersionVector The version vector of the incoming replica
     * @return Result containing:
     *         - `mergeResult.resolution`: How the conflict was resolved (NO_CHANGE, LOCAL, INCOMING, MERGED_VALUES)
     *         - `mergeResult.value`: The resulting merged value
     *         - `mergeResult.node`: The resulting merged version node tree
     *         - `changes`: Delta of changes applied (for downstream propagation)
     *         - `actors`: Merged version vector (max of each actor across both replicas)
     */
    fun resolveConflict(
        localValue: T?,
        localNode: N?,
        localActors: A?,
        incomingValue: T?,
        incomingNode: N,
        incomingVersionVector: Map<Long, Long>,
    ): ResolverDeltaResult<T, N, V, ResolutionStrategy, C, A>

    /**
     * Apply a list of incoming changes to the local state, merging version vectors.
     *
     * This method enables delta-based synchronization where only the changes since a baseline
     * are transmitted, rather than the full document state. It is optimized for scenarios where
     * changes are generated from a known baseline state that matches the receiver's state.
     *
     * ## Baseline Actors Requirement
     *
     * **CRITICAL:** The `incomingBaselineActors` must represent the version vector of the state
     * from which the changes were derived. The changes can only be safely applied when the local
     * state is at or ahead of this baseline. If the local state has diverged (contains changes
     * the baseline doesn't know about, or is missing changes the baseline has), the method will
     * short-circuit and return the local state unchanged.
     *
     * This requirement exists because delta changes are computed relative to a specific baseline:
     * - The sender reads the current state at baseline version B
     * - The sender makes modifications, generating changes C
     * - The sender transmits (B, C) to the receiver
     * - The receiver can only apply C if their local state includes everything in B
     *
     * ### Why Baseline Matching is Essential for Nested Fields
     *
     * Delta changes only contain the modified leaf values with their paths—they don't include
     * the full parent message context. This creates a critical problem when a parent message
     * field has been set to null locally.
     *
     * **The maxVersion Problem:**
     *
     * When resolving a message field against null, the resolver compares the `maxVersion` of
     * the entire message subtree against the null's version. If the message's maxVersion is
     * greater, the entire message (with all its children) wins. If the null's version is
     * greater, the deletion wins.
     *
     * With delta sync, we might receive a change for a single child field that has a version
     * newer than the null. But we're missing all the sibling data.
     *
     * **Example using Version{timestamp, actor_id, actor_version}:**
     *
     * ```
     * // Device A (actor_id=100) created the customer at t=1000
     * // Device B (actor_id=200) updated email at t=1010
     * // Device C (actor_id=300) updated phone at t=1005
     *
     * Baseline state: order {
     *   customer {
     *     name: "Alice"  @ Version{t=1000, actor=100, v=1}
     *     email: "a@x.com" @ Version{t=1010, actor=200, v=1}  ← maxVersion (highest timestamp)
     *     phone: "555"   @ Version{t=1005, actor=300, v=1}
     *   }
     * }
     *
     * // Device D (actor_id=400) deletes customer at t=1008
     * Local state: order { customer: null @ Version{t=1008, actor=400, v=1} }
     *
     * // Device B (actor_id=200) updates name at t=1020 (doesn't know about deletion)
     * Incoming delta: path=[customer, name], value="Bob" @ Version{t=1020, actor=200, v=2}
     * ```
     *
     * The incoming change has `name @ t=1020` which is newer than `null @ t=1008`.
     * In a full resolution, the message's maxVersion would be t=1020, which beats t=1008,
     * so the message wins and we'd preserve all children:
     * `customer { name: "Bob"@t=1020, email: "a@x.com"@t=1010, phone: "555"@t=1005 }`
     *
     * But with only the delta, we only have the `name` field. If we apply it, we'd incorrectly
     * create: `customer { name: "Bob" }` — losing `email` and `phone` entirely.
     *
     * The delta doesn't tell us:
     * - That sibling fields `email` and `phone` exist and should be preserved
     * - What their values and versions are
     * - Whether the incoming message as a whole should win against the null
     *
     * **With baseline validation:**
     * - If `incomingBaselineActors` doesn't match local state, we detect divergence
     * - We reject the changes and the caller falls back to full [resolveConflict]
     * - Full resolution has both complete states with all child data to merge correctly
     *
     * ### Divergence Consequences
     *
     * If the receiver's state has diverged from B, applying changes could result in:
     * - **Lost updates**: Changes made locally after B would be silently overwritten
     * - **Orphaned nested changes**: Changes to child fields of deleted parents can't be applied
     * - **Duplicate application**: Changes already incorporated would be reapplied incorrectly
     * - **Inconsistent state**: The merge assumes a starting point that doesn't match reality
     *
     * When baseline mismatch is detected, callers should fall back to full-state
     * [resolveConflict] which handles arbitrary divergence correctly by comparing complete
     * version trees.
     *
     * ## Resolution Flow
     *
     * 1. Compare local version vector against incoming baseline
     * 2. If local is behind or diverged from baseline → return [ApplyChangesResult.REJECTED]
     * 3. If changes would result in no version vector change → return [ApplyChangesResult.UNCHANGED]
     * 4. Otherwise, apply changes field-by-field and merge version vectors → return [ApplyChangesResult.APPLIED]
     *
     * @param localValue The current local value (may be null for new documents)
     * @param localNode The current version node tracking field-level versions
     * @param localActors The local actor state containing the version vector
     * @param incomingChanges List of change events to apply, each with path, value, and version
     * @param incomingBaselineActors The version vector of the state from which changes were derived.
     *        Local state must be at or ahead of this baseline for changes to be applied.
     * @return Result containing the updated value, node, version vector, and [ApplyChangesResult]:
     *         - [ApplyChangesResult.REJECTED]: Baseline validation failed; changes rejected.
     *           Callers should fall back to [resolveConflict] for full-state resolution.
     *         - [ApplyChangesResult.UNCHANGED]: Local state unchanged (empty changes or already included).
     *         - [ApplyChangesResult.APPLIED]: Changes were successfully applied to local state.
     */
    fun applyChanges(
        localValue: T?,
        localNode: N?,
        localActors: A?,
        incomingChanges: List<ChangeEvent<*, N, C>>,
        incomingBaselineActors: Map<Long, Long>,
    ): ResolverDeltaResult<T, N, V, ApplyChangesResult, C, A>
}

/**
 * Interface for computing delta changes relative to a version vector.
 *
 * This interface enables efficient synchronization by computing only the changes
 * that a target replica doesn't yet have, based on version vector comparison.
 *
 * @param T The message type
 * @param N The version node type
 * @param V The version type
 * @param C The path component type
 */
interface CrdtMessageDeltaResolver<T, N, V, C> {
    /**
     * Compute the changes needed to synchronize a target replica from a given version vector.
     *
     * This method traverses the document's version node tree and collects all field changes
     * whose versions are newer than the corresponding actor version in the provided version
     * vector. The result is a minimal set of [ChangeEvent]s that, when applied to a replica
     * at the given version vector state, will bring it up to date.
     *
     * ## Delta Computation
     *
     * For each field in the document:
     * 1. Extract the field's version (actor ID + actor version)
     * 2. Compare against the target's known version for that actor
     * 3. If field version > target version for that actor, include in delta
     *
     * ## Usage Pattern
     *
     * ```kotlin
     * // On sender: compute what receiver needs
     * val receiverVersionVector = mapOf(1L to 5L, 2L to 3L)
     * val changes = resolver.changeDelta(myValue, myNode, receiverVersionVector)
     *
     * // Transmit (receiverVersionVector, changes) to receiver
     *
     * // On receiver: apply the delta
     * resolver.applyChanges(localValue, localNode, localActors, changes, receiverVersionVector)
     * ```
     *
     * @param value The current document value (may be null for deleted documents)
     * @param node The current version node tree containing field-level versions
     * @param versionVector The target's current version vector; changes newer than this are included
     * @return List of [ChangeEvent]s representing all changes the target doesn't have.
     *         Each event includes the field path, encoded value, and version information.
     *         Empty list if target is already up to date.
     */
    fun changeDelta(
        value: T?,
        node: N,
        versionVector: Map<Long, Long>,
    ): List<ChangeEvent<*, N, C>>
}

/**
 * Base interface for decoders that reconstruct ChangeEvent instances from serialized format.
 *
 * This is the reverse operation of encoding changes for network transmission.
 * Given path components, encoded bytes, and version information, decoders navigate through
 * the data structure to reconstruct the change.
 *
 * ## Serialized Format
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
     * Decodes a change from its serialized representation back into a ChangeEvent.
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
