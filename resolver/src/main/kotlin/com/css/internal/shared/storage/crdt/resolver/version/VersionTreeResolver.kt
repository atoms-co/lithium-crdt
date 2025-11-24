package com.css.internal.shared.storage.crdt.resolver.version

import com.css.internal.shared.storage.crdt.resolver.delta.PathComponentAdapter
import com.css.internal.shared.storage.crdt.resolver.version.ResolutionStrategy.INCOMING
import com.css.internal.shared.storage.crdt.resolver.version.ResolutionStrategy.LOCAL
import com.css.internal.shared.storage.crdt.resolver.version.ResolutionStrategy.MERGED_VALUES
import com.css.internal.shared.storage.crdt.resolver.version.ResolutionStrategy.NO_CHANGE

/**
 * Unified interface combining version-based conflict resolution with tree node operations.
 *
 * This interface extends both [VersionNodeAdapter] and [VersionResolver] to provide a complete
 * framework for resolving conflicts in hierarchical CRDT structures. It includes utility methods
 * for version comparison, aggregation, and resolution strategy application.
 *
 * **Key Capabilities:**
 * - Version comparison and ordering
 * - Node tree traversal and manipulation
 * - Resolution strategy application
 * - Version aggregation (min/max) across node collections
 *
 * **Use Cases:**
 * - Last-Write-Wins (LWW) conflict resolution
 * - Multi-value register conflict resolution
 * - Hierarchical CRDT state merging
 * - Version vector management
 *
 * @param N The node type representing versioned data in the tree
 * @param V The version type (typically Version for vector clocks)
 *
 * @see VersionNodeAdapter for node structure operations
 * @see VersionResolver for version comparison and increment logic
 */
interface VersionTreeResolver<N, V, C> : VersionNodeAdapter<N, V>, VersionResolver<V>, PathComponentAdapter<C> {
    /**
     * Enables comparison operators for version values.
     *
     * This operator function allows using comparison operators (<, >, <=, >=)
     * directly on version objects.
     *
     * **Example:**
     * ```
     * val v1: V = ...
     * val v2: V = ...
     * if (v1 > v2) { ... }  // Uses this operator
     * ```
     *
     * @param other The version to compare with
     * @return Negative if this < other, 0 if equal, positive if this > other
     */
    operator fun V.compareTo(other: V): Int = compare(this@compareTo, other)

    operator fun Map<Long, Long>.contains(version: V): Boolean = isIncluded(this@contains, version)

    /**
     * Returns the maximum of this version and another, handling null gracefully.
     *
     * This function ensures that the returned version is at least as recent as both
     * input versions, which is useful for merge operations where we want to preserve
     * the most recent timestamp.
     *
     * @param other The version to compare with, or null
     * @return This version if other is null or this >= other; otherwise other
     */
    fun V.coerceAtLeast(other: V?): V {
        other ?: return this
        return if (this < other) other else this
    }

    /**
     * Ensures this version comes after the previous version by incrementing if necessary.
     *
     * This is a convenience method that delegates to [incrementNextIfNeeded] with
     * this version as the "next" parameter.
     *
     * **Use Case:** When applying a new version to ensure monotonic progression:
     * ```
     * val newVersion = incomingVersion.ensureAfter(currentVersion)
     * ```
     *
     * @param previous The previous version to compare against, or null
     * @return This version, potentially incremented to exceed previous
     */
    fun V.ensureAfter(previous: V?): V = incrementNextIfNeeded(previous, this)

    /**
     * Applies a resolution strategy to select or merge two versions.
     *
     * This extension function on [ResolutionStrategy] implements the logic for
     * choosing which version to use based on the resolution outcome:
     * - NO_CHANGE or LOCAL: return local version
     * - INCOMING: return incoming version
     * - MERGED_VALUES: return maximum of both versions
     *
     * **Example:**
     * ```
     * val strategy = ResolutionStrategy.MERGED_VALUES
     * val resultVersion = strategy.resolve(localVersion, incomingVersion)
     * ```
     *
     * @param local The local version
     * @param incoming The incoming version
     * @return The resolved version based on the strategy
     */
    fun ResolutionStrategy.resolve(local: V, incoming: V): V = when (this) {
        NO_CHANGE,
        LOCAL -> local
        INCOMING -> incoming
        MERGED_VALUES -> local.coerceAtLeast(incoming)
    }

    /**
     * Finds the minimum version across a collection of nodes.
     *
     * This function scans through all nodes in the collection and returns the
     * smallest version found. If a node has no version, the default version is
     * used for that node. If the collection is null or empty, returns the default.
     *
     * **Use Case:** Determining the oldest data in a set of updates to ensure
     * all nodes have progressed beyond a certain point.
     *
     * @param defaultVersion The version to use for nodes without versions or if collection is null/empty
     * @return The minimum version found in the collection, or defaultVersion
     */
    fun Iterable<N>?.minVersion(
        defaultVersion: V,
    ) = this?.minOfWithOrNull(comparator = this@VersionTreeResolver) {
        it.versionValue ?: defaultVersion
    } ?: defaultVersion

    /**
     * Finds the maximum version in a node and its descendant fields.
     *
     * This function recursively traverses a node's field structure to find the
     * most recent version anywhere in the subtree. This is useful for determining
     * the overall staleness/freshness of a complex data structure.
     *
     * **Algorithm:**
     * 1. If node is null, return defaultVersion
     * 2. Start with node's own version (or defaultVersion if absent)
     * 3. Recursively find max version across all field values
     * 4. Return the overall maximum
     *
     * @param defaultVersion The version to use if node is null or has no version
     * @return The maximum version found in the node tree
     */
    fun N?.maxVersion(defaultVersion: V): V {
        this ?: return defaultVersion
        // Only need to check fields because repeated/map types will have versionValue already max of children.
        return fields.values.maxVersion(versionValue ?: defaultVersion)
    }

    /**
     * Helper function to find maximum version across a collection of nodes.
     *
     * This is an internal utility that recursively computes the maximum version
     * by calling [maxVersion] on each node and comparing results.
     *
     * @param defaultVersion The version to use for nodes without versions
     * @return The maximum version in the collection
     */
    fun Iterable<N>?.maxVersion(defaultVersion: V) = this?.maxOfWithOrNull(
        comparator = this@VersionTreeResolver
    ) { it.maxVersion(defaultVersion) } ?: defaultVersion
}

fun Map<Long, Long>.resolutionStrategy(other: Map<Long, Long>): ResolutionStrategy {
    if (keys.containsAll(other.keys)) {
        var resolutionStrategy = NO_CHANGE
        forEach { (key, localVersion) ->
            val incomingVersion = other.getOrDefault(key, Long.MIN_VALUE)
            resolutionStrategy += if (localVersion == incomingVersion) {
                NO_CHANGE
            } else if (localVersion > incomingVersion) {
                LOCAL
            } else {
                INCOMING
            }

            if (resolutionStrategy == MERGED_VALUES) {
                return resolutionStrategy
            }
        }
        return resolutionStrategy
    } else {
        return MERGED_VALUES
    }
}
