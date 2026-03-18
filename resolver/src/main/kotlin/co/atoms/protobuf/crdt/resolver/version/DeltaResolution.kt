package co.atoms.protobuf.crdt.resolver.version

/**
 * Result of applying incremental changes via [applyChanges].
 *
 * This enum represents the outcome of delta-based CRDT synchronization, where changes
 * computed from a baseline state are applied incrementally. Unlike [ResolutionStrategy]
 * which handles full-state conflict resolution, ApplyChangesResult specifically indicates
 * whether incremental changes could be applied and what happened.
 *
 * ## Delta Sync Workflow
 *
 * Delta sync optimizes synchronization by transmitting only changes since a known baseline:
 * 1. Sender computes changes from their baseline state
 * 2. Sender transmits changes along with baseline actor versions
 * 3. Receiver validates that local state is at or ahead of the baseline
 * 4. If valid, changes are applied incrementally; if invalid, fallback to full-state sync
 *
 * ## Baseline Validation
 *
 * The baseline actors map represents the state from which changes were computed.
 * For delta sync to be safe, the local state must include all data the sender had
 * when computing the changes. This is verified by comparing version vectors:
 *
 * - **Safe**: Local version >= baseline version for all actors in baseline
 * - **Unsafe**: Baseline contains actors/versions unknown to local (divergence)
 *
 * ## Usage Example
 *
 * ```kotlin
 * val result = resolver.applyChanges(
 *     localValue = localDoc,
 *     localNode = localVersionNode,
 *     localActors = localActors,
 *     incomingChanges = changes,
 *     incomingBaselineActors = baselineActors
 * )
 *
 * when (result.mergeResult.resolution) {
 *     ApplyChangesResult.REJECTED -> {
 *         // Baseline validation failed - fall back to full-state sync
 *         val fullResult = resolver.resolveConflict(...)
 *     }
 *     ApplyChangesResult.UNCHANGED -> {
 *         // Local state unchanged (already had all changes or no changes to apply)
 *     }
 *     ApplyChangesResult.APPLIED -> {
 *         // Changes were successfully applied
 *         persist(result.mergeResult.value, result.mergeResult.node, result.actors)
 *     }
 * }
 * ```
 *
 * @see ResolutionStrategy for full-state conflict resolution outcomes
 * @see CrdtMessageResolver.applyChanges for the delta sync entry point
 */
enum class ApplyChangesResult {
    /**
     * Changes were rejected due to baseline validation failure.
     *
     * This occurs when the incoming baseline contains actors or versions that the
     * local state doesn't have. This indicates the sender computed changes from a
     * state that includes data we've never seen, making incremental application unsafe.
     *
     * **Causes:**
     * - Baseline has an actor ID unknown to local (sender has seen data we haven't)
     * - Baseline has a higher version for an actor than local (sender is ahead of us)
     *
     * **Recovery:**
     * When this result is returned, the caller should fall back to full-state
     * conflict resolution using [CrdtMessageResolver.resolveConflict], which can
     * safely merge diverged states with complete version information.
     *
     * **Example:**
     * ```
     * Local version vector:   {actor1: 5, actor2: 3}
     * Baseline version vector: {actor1: 5, actor2: 3, actor999: 2}  // Unknown actor!
     * Result: REJECTED (actor999 is unknown to local)
     * ```
     */
    REJECTED,

    /**
     * No changes were needed - local state already up to date.
     *
     * This indicates that the operation completed successfully but the local
     * state did not need to be modified. This can occur when:
     *
     * - The incoming changes list is empty
     * - All incoming changes are already included in the local state (local is ahead)
     * - The local version vector already contains all the incoming actor versions
     *
     * **Behavior:**
     * - `mergeResult.value` returns the original local value
     * - `mergeResult.node` returns the original local version node
     * - `actors` returns the original or minimally updated actors state
     */
    UNCHANGED,

    /**
     * Changes were successfully applied to local state.
     *
     * This indicates that the operation completed successfully and the local
     * state was updated with the incoming changes. The merge followed LWW (last-write-wins)
     * semantics at the field level.
     *
     * **Behavior:**
     * - `mergeResult.value` contains the updated document with changes applied
     * - `mergeResult.node` contains the updated version tree
     * - `actors` contains the merged version vector (max of local and incoming per actor)
     */
    APPLIED,
}

/**
 * Converts a [ResolutionStrategy] to its corresponding [ApplyChangesResult].
 *
 * This mapping is used internally when the delta sync operation delegates to
 * the underlying merge logic and needs to translate the result.
 *
 * **Mapping:**
 * - [ResolutionStrategy.NO_CHANGE], [ResolutionStrategy.LOCAL] → [ApplyChangesResult.UNCHANGED]
 * - [ResolutionStrategy.INCOMING], [ResolutionStrategy.MERGED_VALUES] → [ApplyChangesResult.APPLIED]
 */
fun ResolutionStrategy.toApplyChangesResult(): ApplyChangesResult = when (this) {
    ResolutionStrategy.NO_CHANGE,
    ResolutionStrategy.LOCAL -> ApplyChangesResult.UNCHANGED
    ResolutionStrategy.INCOMING,
    ResolutionStrategy.MERGED_VALUES -> ApplyChangesResult.APPLIED
}