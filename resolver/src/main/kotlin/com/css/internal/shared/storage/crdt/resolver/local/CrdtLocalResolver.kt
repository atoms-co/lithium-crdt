package com.css.internal.shared.storage.crdt.resolver.local

import com.css.internal.shared.storage.crdt.resolver.NodeMergeResult
import com.css.internal.shared.storage.crdt.resolver.ResolutionDeltaContext
import com.css.internal.shared.storage.crdt.resolver.version.VersionTreeResolver

interface CrdtLocalResolver<T, N, V, C> {
    val encoder: (T) -> ByteArray
    val versionTreeResolver: VersionTreeResolver<N, V, C>

    /**
     * Apply a local write operation with a new version vector.
     *
     * For local writes, you provide a version that gets applied to this
     * specific location. The resolver handles updating the VersionNode structure
     * appropriately, creating a new node if the value changed.
     *
     * @param currentValue The current value stored locally
     * @param currentNode The current node (contains version + structure info)
     * @param currentVersion The effective version at this level (from node, parent, or empty)
     * @param newValue The new value being written locally
     * @param newVersion The version to use if the value changed
     * @return Result indicating whether the value was updated and the final VersionNode
     */
    fun applyLocalWrite(
        currentValue: T?,
        currentNode: N?,
        currentVersion: V,
        newValue: T?,
        newVersion: V,
        context: ResolutionDeltaContext<N, C>,
    ): NodeMergeResult<T, N, Boolean>
}
