package com.css.internal.shared.storage.crdt.data

import kotlin.random.Random

/**
 * Increments the local actor version for distributed CRDT coordination.
 *
 * This function manages actor state for distributed conflict-free replicated data types (CRDTs).
 * Each device/actor maintains a unique identifier and tracks version numbers for coordination
 * with other actors in the distributed system.
 *
 * **Actor Initialization**: If this [Actors] instance is null, creates a new actor with:
 * - A randomly generated actor ID (unless custom generator provided)
 * - Initial version of 1 for the new actor
 *
 * **Actor Advancement**: If this [Actors] instance exists, increments the local actor's version:
 * - Preserves existing actor ID (unless it's 0, then generates new one)
 * - Increments the version counter for the local actor
 * - Maintains all other actors' version information unchanged
 *
 * @param actorGenerator Function to generate new actor IDs. Defaults to [Random.nextLong]
 *                      for cryptographically secure random IDs. Custom generators can be
 *                      provided for testing or deterministic scenarios.
 * @return New [Actors] instance with incremented local actor version
 *
 * @see merge for combining actor states from different devices
 * @see Map.increment for the underlying version increment logic
 *
 * @sample
 * ```kotlin
 * // Initialize new actor system
 * val actors = null.incrementLocalActor()
 * // Result: Actors(local_actor=<random_id>, version_vector={<random_id>: 1})
 *
 * // Advance existing actor
 * val advanced = actors.incrementLocalActor()
 * // Result: Actors(local_actor=<same_id>, version_vector={<same_id>: 2, ...others})
 *
 * // Custom actor ID generation for testing
 * val testActors = null.incrementLocalActor { 12345L }
 * // Result: Actors(local_actor=12345, version_vector={12345: 1})
 * ```
 */
fun Actors?.incrementLocalActor(actorGenerator: () -> Long = Random::nextLong): Actors {
    this ?: return actorGenerator().let { Actors(local_actor = it, mapOf(it to 1L)) }

    val localActor = local_actor.takeIf { it != 0L } ?: actorGenerator()
    return copy(
        local_actor = localActor,
        version_vector = version_vector.increment(localActor),
    )
}

/**
 * Creates a new map with the specified key's value incremented by 1.
 *
 * This is a functional approach to incrementing map values that preserves immutability.
 * The original map is unchanged; a new map is returned with the incremented value.
 *
 * @param key The map key whose value should be incremented
 * @return New map with the incremented value for [key]. If [key] doesn't exist,
 *         it's initialized to 1. Existing keys retain their values unchanged.
 *
 * @see MutableMap.incrementAndGet for the mutable version used internally
 *
 * @sample
 * ```kotlin
 * val versions = mapOf("device1" to 5L, "device2" to 3L)
 * val updated = versions.increment("device1")
 * // Result: {"device1": 6, "device2": 3}
 *
 * val withNew = versions.increment("device3")
 * // Result: {"device1": 5, "device2": 3, "device3": 1}
 * ```
 */
fun <T> Map<T, Long>.increment(key: T): Map<T, Long> = toMutableMap().apply { incrementAndGet(key) }

/**
 * Increments the value for the specified key and returns the new value.
 *
 * This function modifies the map in-place and returns the incremented value for convenience.
 * If the key doesn't exist, it's initialized to 1. This is the mutable counterpart to
 * the immutable [Map.increment] extension.
 *
 * @param key The map key whose value should be incremented
 * @return The new value after incrementing (either existing_value + 1 or 1 if key was missing)
 *
 * @sample
 * ```kotlin
 * val versions = mutableMapOf("device1" to 5L)
 * val newValue = versions.incrementAndGet("device1")
 * // newValue = 6, versions = {"device1": 6}
 *
 * val firstValue = versions.incrementAndGet("device2")
 * // firstValue = 1, versions = {"device1": 6, "device2": 1}
 * ```
 */
fun <T> MutableMap<T, Long>.incrementAndGet(key: T): Long = compute(key) { _, i -> (i ?: 0L) + 1L } ?: 0L

/**
 * Merges incoming actor version information with the current actor state.
 *
 * This function is essential for distributed CRDT synchronization. When devices communicate,
 * they exchange their actor version maps to understand what changes have been seen by each
 * participant. This merge operation combines that information while preserving the local
 * actor identity.
 *
 * **Merge Strategy**: Uses [Map.merge] with `maxOf` to ensure each actor's version
 * represents the highest version seen across all devices. This guarantees that:
 * - No version information is lost during synchronization
 * - Each actor's version monotonically increases
 * - Duplicate operations can be detected and ignored
 *
 * @param incoming Map of actor IDs to their latest versions from a remote device
 * @return New [Actors] instance with merged version information. The [local_actor]
 *         field is preserved unchanged.
 *
 * @see Map.merge for the underlying merge implementation
 *
 * @sample
 * ```kotlin
 * val localActors = Actors(
 *     local_actor = 100L,
 *     version_vector = mapOf(100L to 5L, 200L to 3L)
 * )
 *
 * val remoteVersions = mapOf(200L to 7L, 300L to 2L)  // Remote device info
 * val merged = localActors.merge(remoteVersions)
 *
 * // Result: Actors(
 * //   local_actor = 100L,  // Unchanged
 * //   version_vector = {100L: 5, 200L: 7, 300L: 2}  // 200L took max, 300L added
 * // )
 * ```
 */
fun Actors?.merge(incoming: Map<Long, Long>) = this?.copy(
    version_vector = version_vector.merge(incoming, ::maxOf)
) ?: Actors(version_vector = incoming)

/**
 * If the specified key is not already associated with a value or is
 * associated with null, associates it with the given non-null value.
 * Otherwise, replaces the associated value with the results of the given
 * remapping function, or removes if the result is {@code null}. This
 * method may be of use when combining multiple mapped values for a key.
 */
fun <T, V : Comparable<V>> Map<T, V>.merge(other: Map<T, V>, combine: (V, V) -> V?): Map<T, V> {
    if (isEmpty()) return other

    return toMutableMap().apply {
        merge(
            other = other,
            combine = combine
        )
    }
}

/**
 * If the specified key is not already associated with a value or is
 * associated with null, associates it with the given non-null value.
 * Otherwise, replaces the associated value with the results of the given
 * remapping function, or removes if the result is {@code null}. This
 * method may be of use when combining multiple mapped values for a key.
 */
fun <K, V : Comparable<V>> MutableMap<K, V>.merge(other: Map<K, V>, combine: (V, V) -> V?) = other.forEach {
    merge(it.key, it.value, combine)
}
