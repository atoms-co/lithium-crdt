package com.css.internal.shared.storage.crdt.resolver.local

import com.css.internal.shared.storage.crdt.resolver.NodeMergeResult
import com.css.internal.shared.storage.crdt.resolver.ResolutionDeltaContext

/**
 * Primitive value resolver using simple equality and version comparison.
 */
interface SingleValueLocalResolver<T, N, V, C> :
    CrdtLocalResolver<T, N, V, C> {

    override fun applyLocalWrite(
        currentValue: T?,
        currentNode: N?,
        currentVersion: V,
        newValue: T?,
        newVersion: V,
        context: ResolutionDeltaContext<N, C>,
    ): NodeMergeResult<T, N, Boolean> = with(versionTreeResolver) {
        // Fast path: no change
        if (currentValue == newValue) {
            return NodeMergeResult(
                resolution = false,
                value = currentValue,
                node = currentNode ?: createVersionNode(currentVersion),
            )
        }

        // Value changed: update with monotonic version progression
        val finalVersion = newVersion.ensureAfter(
            currentNode?.versionValue ?: currentVersion
        )
        val node = createVersionNode(finalVersion)

        context.addChange(
            newValue = newValue,
            encoder = encoder,
            versionNode = node,
        )

        return NodeMergeResult(
            resolution = true,
            value = newValue,
            node = node,
        )
    }
}

/**
 * Field is optional, new null values are considered "unset"
 */
interface OptionalAnyValueLocalResolver<T, N, V, C> : CrdtLocalResolver<T, N, V, C> {
    val valueLocalResolver: CrdtLocalResolver<T, N, V, C>

    override fun applyLocalWrite(
        currentValue: T?,
        currentNode: N?,
        currentVersion: V,
        newValue: T?,
        newVersion: V,
        context: ResolutionDeltaContext<N, C>,
    ): NodeMergeResult<T, N, Boolean> = with(versionTreeResolver) {
        // Optional values are ignored
        if (newValue == null) {
            return NodeMergeResult(
                resolution = false,
                value = currentValue,
                node = currentNode ?: createVersionNode(currentVersion),
            )
        }

        return valueLocalResolver.applyLocalWrite(
            currentValue = currentValue,
            currentNode = currentNode,
            currentVersion = currentVersion,
            newValue = newValue,
            newVersion = newVersion,
            context = context,
        )
    }
}
