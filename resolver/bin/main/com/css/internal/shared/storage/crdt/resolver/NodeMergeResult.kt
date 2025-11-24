package com.css.internal.shared.storage.crdt.resolver

/**
 * Result of conflict resolution between two versioned values.
 */
data class NodeMergeResult<out T, out N, out R>(
    /**
     * How the conflict was resolved
     */
    val resolution: R,

    /**
     * The winning or merged value
     */
    val value: T?,

    /**
     * The merged VersionNode (contains resolved version + child structure)
     */
    val node: N?
)
