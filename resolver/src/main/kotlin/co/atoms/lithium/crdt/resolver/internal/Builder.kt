package co.atoms.lithium.crdt.resolver.internal

import co.atoms.lithium.crdt.resolver.descriptor.MessageFieldDescriptor
import co.atoms.lithium.crdt.resolver.NodeMergeResult

/**
 * Applies a CRDT merge result to a message builder field and tracks its version.
 *
 * Updates both the builder's field value and the version tracking map as a single
 * atomic operation to maintain consistency between data and version metadata.
 *
 * @param fieldBinding Field metadata for the field being updated
 * @param fieldResult CRDT merge result containing the resolved value and version
 * @param fieldVersions Mutable map tracking version nodes for each field by tag
 * @return The resolution strategy used (NO_CHANGE, CURRENT, INCOMING, or MERGED_VALUES)
 * @throws IllegalArgumentException if setting the field value fails, with field details added
 */
internal fun <M, B, R, N> B.setValue(
    fieldBinding: MessageFieldDescriptor<M, B, Any>,
    fieldResult: NodeMergeResult<Any, N, R>,
    fieldVersions: MutableMap<Int, N>,
): R {
    try {
        fieldBinding.set(this, fieldResult.value)
    } catch (e: Throwable) {
        throw IllegalArgumentException("$fieldBinding", e)
    }

    fieldResult.node?.let {
        fieldVersions[fieldBinding.tag] = it
    }

    return fieldResult.resolution
}
