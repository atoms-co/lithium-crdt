package com.css.internal.shared.storage.crdt.resolver

data class ResolverDeltaResult<T, N, V, S, C, A>(
    val actors: A,
    val changes: List<ChangeEvent<*, N, C>>,
    val mergeResult: NodeMergeResult<T, N, S>
)
