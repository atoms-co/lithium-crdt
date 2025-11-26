package com.css.protobuf.crdt.resolver

class ResolutionDeltaContext<N, C>(
    internal val changes: MutableList<ChangeEvent<*, N, C>> = ArrayList(32),
    internal var pathDepth: Int = 0,
    internal var pathStack: Array<C?> = arrayOfNulls(32),
) {
    companion object {
        private const val MAX_DEPTH = 1024
    }

    val result: List<ChangeEvent<*, N, C>> = changes

    fun pushPath(component: C) {
        if (pathDepth >= pathStack.size) {
            if (pathStack.size >= MAX_DEPTH) {
                throw IllegalStateException(
                    "Path depth exceeded $MAX_DEPTH. Possible circular reference in document structure."
                )
            }
            pathStack = pathStack.copyOf(pathStack.size * 2)
        }
        pathStack[pathDepth++] = component
    }

    fun popPath() {
        if (pathDepth > 0) pathDepth--
    }

    fun <T> addChange(newValue: T?, versionNode: N, encoder: (T) -> ByteArray) {
        changes.add(
            NodeMergeChangeProvider(
                value = newValue,
                pathComponents = captureCurrentPath(),
                encoder = encoder,
                versionNode = versionNode,
            )
        )
    }

    private fun captureCurrentPath(): List<C> {
        return List(pathDepth) { i ->
            pathStack[i] ?: error("Null path component at index $i")
        }
    }

    fun clear() {
        pathDepth = 0
        changes.clear()
    }
}

inline fun <N, C, R> ResolutionDeltaContext<N, C>.withPath(component: C, perform: () -> R): R {
    pushPath(component)
    try {
        return perform()
    } finally {
        popPath()
    }
}
