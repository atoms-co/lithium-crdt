package co.atoms.protobuf.crdt.resolver.incoming.partial

import co.atoms.protobuf.crdt.resolver.ChangeEvent
import co.atoms.protobuf.crdt.resolver.NodeMergeResult
import co.atoms.protobuf.crdt.resolver.ResolutionDeltaContext
import co.atoms.protobuf.crdt.resolver.incoming.CrdtIncomingResolver
import co.atoms.protobuf.crdt.resolver.version.ResolutionStrategy

/**
 * Core interface for applying incremental CRDT changes to local state.
 *
 * This interface provides the fundamental operation for processing incoming change events
 * and merging them with local state. Unlike [CrdtIncomingResolver.resolveConflict] which
 * handles full-value conflicts, this interface processes structured change deltas that
 * describe modifications at specific paths within the data structure.
 *
 * ## Change Application
 * The [applyChanges] method processes a list of [ChangeEvent]s, each representing a
 * modification at a specific path in the data structure. Changes are applied hierarchically:
 * - Path components guide navigation through nested structures (messages, maps, lists)
 * - The depth parameter tracks how far down the hierarchy processing has descended
 * - Each resolver implementation handles changes appropriate to its data type
 *
 * ## Type Parameters
 * @param T The value type being resolved (primitive, message, map, list, etc.)
 * @param N The version node type that tracks per-field/element version metadata
 * @param V The version type used for conflict resolution (typically a version vector)
 * @param C The path component type used to navigate nested structures
 *
 * @see CrdtIncomingResolver for full-value conflict resolution
 * @see ChangeEvent for the structure of individual changes
 */
interface CrdtIncomingChangeResolver<T, N, V, C> : CrdtIncomingResolver<T, N, V, C> {
    fun applyChanges(
        depth: Int,
        localValue: T?,
        localNode: N?,
        localVersion: V,
        changes: List<ChangeEvent<*, N, C>>,
        context: ResolutionDeltaContext<N, C>,
    ): NodeMergeResult<T, N, ResolutionStrategy>
}
