package co.atoms.protobuf.crdt.resolver.decoder

import co.atoms.protobuf.crdt.resolver.ChangeEvent
import co.atoms.protobuf.crdt.resolver.version.VersionTreeResolver

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
interface CrdtPathChangeDecoder<T, N, V, C> {
    /**
     * Encoder function for the message type
     */
    val encoder: (T) -> ByteArray

    /**
     * Decoder function for the message type
     */
    val decoder: (ByteArray) -> T

    /**
     * Version tree resolver for working with version vectors
     */
    val versionTreeResolver: VersionTreeResolver<N, V, C>

    /**
     * Decodes a change from its serialized representation back into a ChangeEvent.
     *
     * @param encodedValue The encoded value as bytes (null for deletions)
     * @param pathComponents Remaining path components to navigate (empty if at target)
     * @param versionNode The version information for this change
     * @return A ChangeEvent with the decoded value and metadata
     * @throws IllegalArgumentException if the path is invalid or decoding fails
     */
    fun decodeChange(
        depth: Int,
        encodedValue: ByteArray?,
        pathComponents: List<C>,
        versionNode: N,
    ): ChangeEvent<*, N, C>
}
