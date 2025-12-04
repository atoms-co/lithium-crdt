package com.css.protobuf.crdt.resolver

/**
 * Represents a single field-level change that can be transmitted over the network.
 *
 * ChangeEvents are produced by local writes ([CrdtMessageLocalResolver.applyLocalWrite]) and
 * delta computation ([CrdtMessageDeltaResolver.changeDelta]), and consumed by incoming
 * change application ([CrdtMessageIncomingResolver.applyChanges]).
 *
 * ## Network Transmission Pattern
 *
 * The library uses a lazy encoding pattern optimized for async transport dispatch:
 *
 * 1. **Local operations produce typed ChangeEvents**: When you call `applyLocalWrite` or
 *    `changeDelta`, you get back `ChangeEvent` objects with the typed `value` and an
 *    `encoded()` function.
 *
 * 2. **Lazy encoding via `encoded()`**: The value is NOT serialized until you call `encoded()`.
 *    This allows you to:
 *    - Inspect the typed value for logging/debugging
 *    - Filter changes before serialization
 *    - Defer serialization to a background thread
 *
 * 3. **Non-blocking transport dispatch**: Call `encoded()` on a background thread when
 *    preparing to send over the network, avoiding serialization on the main/UI thread.
 *
 * 4. **Receiver decodes via `decodeChange()`**: The receiver uses [CrdtMessagePathChangeDecoder]
 *    to reconstruct ChangeEvents from the serialized format. The decoder navigates the path
 *    components to find the correct field-specific decoder for the encoded bytes.
 *
 * ## Actor-Based Filtering
 *
 * Before decoding incoming changes, receivers can compare version vectors to determine if
 * decoding is even necessary:
 *
 * ```kotlin
 * // Check if we already have all the changes
 * val incomingMaxVersion = incomingChanges.maxOf { it.versionNode.actorVersion }
 * if (localActors.versionVector[actorId] >= incomingMaxVersion) {
 *     // Skip decoding entirely - we already have these changes
 *     return
 * }
 * ```
 *
 * ## Example Flow
 *
 * ```kotlin
 * // === Sender Side ===
 * val writeResult = resolver.applyLocalWrite(current, node, actors, newValue, timestamp)
 *
 * // Dispatch to transport asynchronously
 * backgroundScope.launch {
 *     val serializedChanges = writeResult.changes.map { change ->
 *         SerializedChange(
 *             path = change.pathComponents,
 *             data = change.encoded(),  // Serialize on background thread
 *             version = change.versionNode
 *         )
 *     }
 *     transport.send(baselineActors, serializedChanges)
 * }
 *
 * // === Receiver Side ===
 * val serializedChanges = transport.receive()
 *
 * // Optionally filter by actor before decoding
 * val newChanges = serializedChanges.filter { serialized ->
 *     val actorId = serialized.version.actorId
 *     val actorVersion = serialized.version.actorVersion
 *     (localActors.versionVector[actorId] ?: 0) < actorVersion
 * }
 *
 * // Decode only the changes we need
 * val changes = newChanges.map { serialized ->
 *     resolver.decodeChange(serialized.data, serialized.path, serialized.version)
 * }
 *
 * // Apply decoded changes
 * resolver.applyChanges(localValue, localNode, localActors, changes, baselineActors)
 * ```
 *
 * @param T The typed value this change carries
 * @param N The version node type
 * @param C The path component type
 */
interface ChangeEvent<T, out N, out C> {
    /** The field path from document root to this change's location */
    val pathComponents: List<C>

    /** The typed value (null for deletions/tombstones) */
    val value: T?

    /** Version information for this change, used for conflict resolution */
    val versionNode: N

    /**
     * Lazily encodes the value to bytes for network transmission.
     *
     * This method is intentionally lazy to support non-blocking transport patterns:
     * - Call from a background thread to avoid blocking UI
     * - Skip calling entirely if filtering determines the change isn't needed
     *
     * @return The encoded value as bytes, or null if [value] is null (deletion)
     * @throws IllegalArgumentException if encoding fails
     */
    fun encoded(): ByteArray?
}

/**
 * Default implementation of [ChangeEvent] that captures an encoder function for lazy serialization.
 *
 * Created internally by resolvers when producing change deltas. The encoder is captured at
 * creation time (from the field's resolver) but not invoked until [encoded] is called.
 */
data class NodeMergeChangeProvider<T, out N, out C>(
    private val encoder: (T) -> ByteArray,
    override val pathComponents: List<C>,
    override val value: T?,
    override val versionNode: N,
) : ChangeEvent<T, N, C> {
    override fun encoded() = try {
        value?.let(encoder)
    } catch (e: Throwable) {
        throw IllegalArgumentException("$this", e)
    }
}
