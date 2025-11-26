package com.css.protobuf.crdt.resolver.version

/**
 * Interface for resolving and comparing version values in CRDT conflict resolution.
 *
 * This interface combines version comparison (via Comparator) with version increment logic
 * to ensure monotonic progression of versions. It's a key component in Last-Write-Wins (LWW)
 * and other timestamp-based conflict resolution strategies.
 *
 * Implementations must ensure:
 * - Consistent ordering of versions (via compare method)
 * - Proper handling of version increments to avoid conflicts
 * - Thread-safety if used in concurrent contexts
 *
 * @param V The version type (e.g., Version for vector clocks, Long for Lamport timestamps)
 */
interface VersionResolver<V> : Comparator<V> {
    val minVersion: V

    /**
     * Increments the next version if it's not already greater than the previous version.
     *
     * This ensures monotonic version progression, preventing version conflicts when
     * the incoming version would be less than or equal to the current version.
     *
     * Typical behavior:
     * - If next > previous: return next unchanged
     * - If next <= previous: increment next to be greater than previous
     * - If previous is null: return next unchanged (no baseline to compare)
     *
     * **Example (vector clock):**
     * ```
     * previous = [5, 3, 2]
     * next = [4, 1, 1]  // Would conflict
     * result = [6, 1, 1]  // Incremented first component to ensure progress
     * ```
     *
     * @param previous The current/previous version, or null if none exists
     * @param next The proposed next version
     * @return The next version, potentially incremented to ensure it's greater than previous
     */
    fun incrementNextIfNeeded(previous: V?, next: V): V

    fun isIncluded(versionVector: Map<Long, Long>, version: V): Boolean

    /**
     * Subtracts a duration from a version's timestamp to calculate a TTL window start.
     *
     * Creates a new version with the same actor information but with the timestamp
     * reduced by the specified duration. Used to calculate the minimum valid timestamp
     * in a TTL window: windowStart = maxVersion - ttl.
     *
     * @param duration The duration in milliseconds to subtract from the timestamp
     * @return A new version with timestamp reduced by duration
     */
    operator fun V.minus(duration: Long): V
}
