package com.css.protobuf.crdt.resolver.version

/**
 * Enumeration of strategies used to resolve conflicts between local and incoming CRDT values.
 *
 * This enum tracks the outcome of conflict resolution operations, indicating which value
 * was selected or whether values were merged. It's used throughout the CRDT resolver to
 * communicate resolution decisions and can be combined to represent complex multi-field
 * resolution outcomes.
 *
 * **Resolution Hierarchy:**
 * - NO_CHANGE: Neutral element (no conflict detected)
 * - LOCAL: Local value won
 * - INCOMING: Incoming value won
 * - MERGED_VALUES: Both contributed to result
 *
 * **Combining Strategies:**
 * The `plus` operator implements a commutative, associative operation useful for
 * aggregating resolution outcomes across multiple fields:
 * - NO_CHANGE is the identity element
 * - Different strategies combine to MERGED_VALUES
 * - Same strategies remain unchanged
 *
 * @see VersionTreeResolver.resolve for usage in version-based resolution
 */
enum class ResolutionStrategy {
    /**
     * No conflict detected; values are identical.
     *
     * This indicates that local and incoming values are equal, so no update is necessary.
     * Acts as the identity element in strategy combination.
     */
    NO_CHANGE,

    /**
     * Local value was kept (local version was newer or preferred).
     *
     * This indicates the conflict was resolved in favor of the local state,
     * typically because the local version timestamp was greater.
     */
    LOCAL,

    /**
     * Incoming value was adopted (incoming version was newer or preferred).
     *
     * This indicates the conflict was resolved in favor of the incoming state,
     * typically because the incoming version timestamp was greater.
     */
    INCOMING,

    /**
     * Values were merged into a new result.
     *
     * This indicates both local and incoming values contributed to the final result,
     * such as through:
     * - Set union (combining elements from both sets)
     * - Counter maximum (taking the larger counter value)
     * - Multi-value register (preserving both values)
     * - Combination of different strategies across multiple fields
     */
    MERGED_VALUES;

    /**
     * Combines two resolution strategies into a single aggregate strategy.
     *
     * This operator is commutative and associative, making it suitable for reducing
     * multiple resolution outcomes into a single result.
     *
     * **Combination Rules:**
     * - `NO_CHANGE + X = X` (identity)
     * - `X + X = X` (idempotent)
     * - `LOCAL + INCOMING = MERGED_VALUES` (conflict)
     * - `any + MERGED_VALUES = MERGED_VALUES` (absorbing)
     *
     * **Example:**
     * ```
     * val field1 = ResolutionStrategy.LOCAL
     * val field2 = ResolutionStrategy.INCOMING
     * val field3 = ResolutionStrategy.NO_CHANGE
     * val overall = field1 + field2 + field3  // MERGED_VALUES
     * ```
     *
     * @param other The strategy to combine with this one
     * @return The combined resolution strategy
     */
    operator fun plus(other: ResolutionStrategy): ResolutionStrategy {
        return when {
            this == NO_CHANGE -> other
            other == NO_CHANGE -> this
            this == other -> this
            else -> MERGED_VALUES
        }
    }
}
