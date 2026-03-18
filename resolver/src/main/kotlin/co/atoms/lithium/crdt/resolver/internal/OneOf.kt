package co.atoms.lithium.crdt.resolver.internal

import co.atoms.lithium.crdt.resolver.NodeMergeResult
import co.atoms.lithium.crdt.resolver.descriptor.MessageFieldDescriptor
import co.atoms.lithium.crdt.resolver.version.VersionTreeResolver

/**
 * Represents a candidate value for a Protocol Buffer oneof field during CRDT conflict resolution.
 *
 * In Protocol Buffers, oneof fields allow only one field to be set within a group at a time.
 * During CRDT merging, multiple fields within the same oneof group may have been updated
 * concurrently, requiring version-based resolution to determine which field wins.
 *
 * @param M The Protocol Buffer message type
 * @param B The Protocol Buffer message builder type
 * @param binding The field binding that identifies which oneof field this represents
 * @param result The merge result containing the candidate value and its version information
 * @param parentVersion The parent version for fallback if no version set to node.
 *
 * @see resolved For the resolution logic that determines winning oneof fields
 */
internal data class OneOf<M, B, R, N, V, C>(
    val binding: MessageFieldDescriptor<M, B, Any>,
    val result: NodeMergeResult<Any, N, R>,
    val parentVersion: V,
    val version: V,
)

/**
 * Resolves conflicts among multiple OneOf field values by selecting the highest versioned value
 * and nullifying all others.
 *
 * This function implements Last-Write-Wins (LWW) semantics for protobuf OneOf fields. When multiple
 * values exist for the same OneOf field (due to concurrent updates), it:
 * 1. Selects the value with the highest version as the winner
 * 2. In case of version ties, prefers non-null values over null values
 * 3. Nullifies all non-winning values while preserving version information
 *
 * The nullification of non-winning values is crucial for OneOf semantics - only one field
 * in a OneOf group can have a value at any time. Losing values are explicitly set to null
 * with the winner's version to ensure they don't resurface during future merges.
 *
 * Example:
 * ```
 * Given OneOf fields with versions:
 *   - fieldA: [(value="foo", version=2), (value="bar", version=3)]
 *   - fieldB: [(value="baz", version=1)]
 *
 * Result after resolution:
 *   - fieldA: value="bar" (version 3 wins)
 *   - fieldA: value=null with version=3 (loser nullified)
 *   - fieldB: value="baz" (only one value, automatically wins)
 * ```
 *
 * @param M The protobuf message type
 * @param B The protobuf builder type
 * @param R The result type containing the resolved value
 * @param setValue Callback function to apply each resolved value (both winners and nullified losers).
 *                 Called once for each value in the input, ensuring all values are properly handled.
 */
internal fun <M, B, R, N, V, C> VersionTreeResolver<N, V, C>.resolved(
    oneOfMap: Map<String, List<OneOf<M, B, R, N, V, C>>>,
    setValue: (OneOf<M, B, R, N, V, C>) -> Unit
) {
    val emptyValues = mutableListOf<OneOf<M, B, R, N, V, C>>()
    oneOfMap.values.forEach { results ->
        // Get max version value for each result.
        var maxOneOfVersion: OneOf<M, B, R, N, V, C>? = null
        for (next in results) {
            if (maxOneOfVersion == null) {
                maxOneOfVersion = next
            } else {
                val maxResult = maxOneOfVersion.result
                val maxVersion = maxOneOfVersion.version
                val nextVersion = next.version
                if (nextVersion > maxVersion || (nextVersion == maxVersion && maxResult.value == null)) {
                    emptyValues.add(maxOneOfVersion)
                    maxOneOfVersion = next
                } else {
                    emptyValues.add(next)
                }
            }
        }
        maxOneOfVersion?.let {
            setValue(it)
        }
        emptyValues.forEach { oneOfResult ->
            // Merging with previous other one of field. Need to override the result to remove and keep fields in sync.
            setValue(
                oneOfResult.run {
                    copy(
                        result = result.copy(
                            node = createVersionNode(
                                // Set the same version as the update or fallback to previous version
                                version = maxOneOfVersion?.version ?: version
                            ),
                            value = null,
                            // If new version was updated this will also be the same update.
                            resolution = maxOneOfVersion?.result?.resolution ?: result.resolution
                        )
                    )
                }
            )
        }
        emptyValues.clear()
    }
}
