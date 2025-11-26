package com.css.protobuf.crdt.resolver.incoming

import com.css.protobuf.crdt.resolver.NodeMergeResult
import com.css.protobuf.crdt.resolver.ResolutionDeltaContext
import com.css.protobuf.crdt.resolver.version.ResolutionStrategy
import com.css.protobuf.crdt.resolver.version.VersionTreeResolver

interface CrdtIncomingResolver<T, N, V, C> {
    val encoder: (T) -> ByteArray
    val versionTreeResolver: VersionTreeResolver<N, V, C>

    /**
     * Resolve a conflict between local and incoming values with their VersionNodes.
     *
     * For conflict resolution, both values come with full VersionNode structures
     * that may contain child version information. The resolver merges both the
     * values and their version structures according to CRDT semantics.
     *
     * @param localValue The current local value
     * @param localNode The current node (contains version + structure info)
     * @param localVersion The effective version at this level (from node, parent, or empty)
     * @param incomingValue The incoming value from another source
     * @param incomingNode The VersionNode of the incoming value (contains version + children)
     * @param incomingVersion The effective incoming version at this level (from node, parent, or empty)
     * @return Result of the conflict resolution with the winning/merged value and VersionNode
     */
    fun resolveConflict(
        localValue: T?,
        localNode: N?,
        localVersion: V,
        incomingValue: T?,
        incomingNode: N?,
        incomingVersion: V,
        context: ResolutionDeltaContext<N, C>,
    ): NodeMergeResult<T, N, ResolutionStrategy>
}
