package co.atoms.protobuf.crdt.wire

import co.atoms.protobuf.crdt.test.TestMessage
import co.atoms.protobuf.crdt.data.Actors
import co.atoms.protobuf.crdt.data.PathComponent
import co.atoms.protobuf.crdt.data.Version
import co.atoms.protobuf.crdt.data.VersionNode
import co.atoms.protobuf.crdt.resolver.NodeMergeChangeProvider
import co.atoms.protobuf.crdt.resolver.version.ApplyChangesResult
import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import kotlin.test.Test

/**
 * Tests for MessageResolver.applyChanges function.
 *
 * This tests the top-level applyChanges logic in MessageResolver including:
 * - Version vector comparison and short-circuiting
 * - Early return when local state is ahead of incoming baseline
 * - Early return when no new changes after merge
 * - Normal change application path
 */
class WireMessageResolverApplyChangesTest {
    private val resolver = WireCrdtResolverProvider().messageResolver(adapter = TestMessage.ADAPTER)

    // Encoder functions for primitive types
    private val intEncoder: (Int) -> ByteArray = { ByteBuffer.allocate(4).putInt(it).array() }
    private val longEncoder: (Long) -> ByteArray = { ByteBuffer.allocate(8).putLong(it).array() }

    @Test
    fun `applyChanges - empty changes list returns NO_CHANGE`() {
        // Given - local state exists
        val localValue = TestMessage(int32Value = 100, stringValue = "local")
        val localNode = VersionNode(version = Version(timestamp = 1000L, actor_id = 1L, actor_version = 5L))
        val localActors = Actors(local_actor = 1L, version_vector = mapOf(1L to 5L))

        // When - apply empty changes
        val result =
            resolver.applyChanges(
                localValue = localValue,
                localNode = localNode,
                localActors = localActors,
                incomingChanges = emptyList(),
                incomingBaselineActors = mapOf(),
            )

        // Then - no change
        assertThat(result.mergeResult.resolution).isEqualTo(ApplyChangesResult.UNCHANGED)
        assertThat(result.mergeResult.value).isEqualTo(localValue)
        assertThat(result.mergeResult.node).isEqualTo(localNode)
        assertThat(result.actors).isEqualTo(localActors)
    }

    @Test
    fun `applyChanges - local state ahead of incoming baseline returns NO_CHANGE`() {
        // Given - local has version 10, incoming baseline is at version 5
        val localValue = TestMessage(int32Value = 100)
        val localNode = VersionNode(version = Version(timestamp = 1000L, actor_id = 1L, actor_version = 10L))
        val localActors = Actors(local_actor = 1L, version_vector = mapOf(1L to 10L))

        // Incoming baseline is behind (version 5)
        val incomingBaselineActors = mapOf(1L to 5L)

        // Incoming change at version 6 (already included in local version 10)
        val incomingChanges =
            listOf(
                NodeMergeChangeProvider(
                    encoder = intEncoder,
                    pathComponents =
                    listOf(
                        PathComponent(field_number = 3), // int32Value field
                    ),
                    value = 200,
                    versionNode = VersionNode(Version(timestamp = 1001L, actor_id = 1L, actor_version = 6L)),
                ),
            )

        // When - apply changes
        val result =
            resolver.applyChanges(
                localValue = localValue,
                localNode = localNode,
                localActors = localActors,
                incomingChanges = incomingChanges,
                incomingBaselineActors = incomingBaselineActors,
            )

        // Then - no change because local is already ahead (version 10 > version 6)
        assertThat(result.mergeResult.resolution).isEqualTo(ApplyChangesResult.UNCHANGED)
        assertThat(result.mergeResult.value).isEqualTo(localValue)
        assertThat(result.mergeResult.node).isEqualTo(localNode)
    }

    @Test
    fun `applyChanges - incoming changes already included returns NO_CHANGE`() {
        // Given - local already has the incoming changes
        val localActors = Actors(local_actor = 1L, version_vector = mapOf(1L to 10L, 2L to 5L))

        val localValue = TestMessage(int32Value = 200)
        val localNode = VersionNode(version = Version(timestamp = 1000L, actor_id = 1L, actor_version = 10L))

        // Incoming changes at version 8 (already included in local version 10)
        val incomingChanges =
            listOf(
                NodeMergeChangeProvider(
                    encoder = intEncoder,
                    pathComponents = listOf(PathComponent(field_number = 3)),
                    value = 200,
                    versionNode = VersionNode(Version(timestamp = 999L, actor_id = 1L, actor_version = 8L)),
                ),
            )

        val incomingBaselineActors = mapOf(1L to 7L)

        // When
        val result =
            resolver.applyChanges(
                localValue = localValue,
                localNode = localNode,
                localActors = localActors,
                incomingChanges = incomingChanges,
                incomingBaselineActors = incomingBaselineActors,
            )

        // Then - no change because we already have version 10 which includes version 8
        assertThat(result.mergeResult.resolution).isEqualTo(ApplyChangesResult.UNCHANGED)
        assertThat(result.mergeResult.value).isEqualTo(localValue)
        assertThat(result.actors.version_vector).isEqualTo(localActors.version_vector)
    }

    @Test
    fun `applyChanges - applies new changes and merges version vectors`() {
        // Given - local at version 5
        val localActors = Actors(local_actor = 1L, version_vector = mapOf(1L to 5L))
        val localValue = TestMessage(int32Value = 100, stringValue = "local")
        val localNode = VersionNode(version = Version(timestamp = 1000L, actor_id = 1L, actor_version = 5L))

        // Incoming baseline matches local
        val incomingBaselineActors = mapOf(1L to 5L)

        // Generate incoming changes from actor 2 using applyLocalWrite
        val incomingBaselineValue = TestMessage(int32Value = 100, stringValue = "local")
        val incomingBaselineNode = VersionNode(version = Version(timestamp = 1000L, actor_id = 1L, actor_version = 5L))
        val incomingBaselineActorsObj = Actors(local_actor = 2L, version_vector = mapOf(1L to 5L))

        val incomingNewValue = TestMessage(int32Value = 200, stringValue = "local")
        val writeResult = resolver.applyLocalWrite(
            currentValue = incomingBaselineValue,
            currentNode = incomingBaselineNode,
            currentActors = incomingBaselineActorsObj,
            newValue = incomingNewValue,
            timestamp = 2000L
        )
        val incomingChanges = writeResult.changes

        // When
        val result =
            resolver.applyChanges(
                localValue = localValue,
                localNode = localNode,
                localActors = localActors,
                incomingChanges = incomingChanges,
                incomingBaselineActors = incomingBaselineActors,
            )

        // Then - changes applied
        assertThat(result.mergeResult.value?.int32Value).isEqualTo(200)
        assertThat(result.mergeResult.value?.stringValue).isEqualTo("local") // Unchanged

        // Version vectors merged - actor 2 version 1 was added
        assertThat(result.actors.version_vector)
            .containsExactly(1L, 5L, 2L, 1L)
    }

    @Test
    fun `applyChanges - null local value applies changes`() {
        // Given - no local value
        val localActors = Actors(local_actor = 1L, version_vector = mapOf(1L to 1L))

        val incomingBaselineActors = mapOf(1L to 1L)
        val incomingChanges =
            listOf(
                NodeMergeChangeProvider(
                    encoder = intEncoder,
                    pathComponents = listOf(PathComponent(field_number = 3)),
                    value = 300,
                    versionNode = VersionNode(Version(timestamp = 2000L, actor_id = 2L, actor_version = 1L)),
                ),
            )

        // When
        val result =
            resolver.applyChanges(
                localValue = null,
                localNode = null,
                localActors = localActors,
                incomingChanges = incomingChanges,
                incomingBaselineActors = incomingBaselineActors,
            )

        // Then
        assertThat(result.mergeResult.value?.int32Value).isEqualTo(300)
        assertThat(result.actors.version_vector).containsExactly(1L, 1L, 2L, 1L)
    }

    @Test
    fun `applyChanges - null local actors creates new actors`() {
        // Given - no local actors
        val incomingBaselineActors = emptyMap<Long, Long>()
        val incomingChanges =
            listOf(
                NodeMergeChangeProvider(
                    encoder = intEncoder,
                    pathComponents = listOf(PathComponent(field_number = 3)),
                    value = 400,
                    versionNode = VersionNode(Version(timestamp = 3000L, actor_id = 3L, actor_version = 2L)),
                ),
            )

        // When
        val result =
            resolver.applyChanges(
                localValue = null,
                localNode = null,
                localActors = null,
                incomingChanges = incomingChanges,
                incomingBaselineActors = incomingBaselineActors,
            )

        // Then
        assertThat(result.mergeResult.value?.int32Value).isEqualTo(400)
        assertThat(result.actors.version_vector).containsEntry(3L, 2L)
    }

    @Test
    fun `applyChanges - multiple changes from different actors`() {
        // Given
        val localActors = Actors(local_actor = 1L, version_vector = mapOf(1L to 5L))
        val localValue = TestMessage(int32Value = 100, int64Value = 200L)
        val localNode = VersionNode(version = Version(timestamp = 1000L, actor_id = 1L, actor_version = 5L))

        val incomingBaselineActors = mapOf(1L to 5L)

        // Multiple changes from actors 2 and 3
        val incomingChanges =
            listOf(
                NodeMergeChangeProvider(
                    encoder = intEncoder,
                    pathComponents = listOf(PathComponent(field_number = 3)), // int32Value
                    value = 999,
                    versionNode = VersionNode(Version(timestamp = 2000L, actor_id = 2L, actor_version = 3L)),
                ),
                NodeMergeChangeProvider(
                    encoder = longEncoder,
                    pathComponents = listOf(PathComponent(field_number = 4)), // int64Value
                    value = 888L,
                    versionNode = VersionNode(Version(timestamp = 3000L, actor_id = 3L, actor_version = 1L)),
                ),
            )

        // When
        val result =
            resolver.applyChanges(
                localValue = localValue,
                localNode = localNode,
                localActors = localActors,
                incomingChanges = incomingChanges,
                incomingBaselineActors = incomingBaselineActors,
            )

        // Then - both changes applied
        assertThat(result.mergeResult.value?.int32Value).isEqualTo(999)
        assertThat(result.mergeResult.value?.int64Value).isEqualTo(888L)

        // All actors in version vector
        assertThat(result.actors.version_vector)
            .containsExactly(1L, 5L, 2L, 3L, 3L, 1L)
    }

    @Test
    fun `applyChanges - version vector merge takes max per actor`() {
        // Given - local has actor 1 at version 10, actor 2 at version 5
        val localActors = Actors(local_actor = 1L, version_vector = mapOf(1L to 10L, 2L to 5L))
        val localValue = TestMessage(int32Value = 100)
        val localNode = VersionNode(version = Version(timestamp = 1000L, actor_id = 1L, actor_version = 10L))

        val incomingBaselineActors = mapOf(1L to 10L, 2L to 5L)

        // Incoming has actor 2 at version 8 (higher), actor 3 at version 2 (new)
        val incomingChanges =
            listOf(
                NodeMergeChangeProvider(
                    encoder = intEncoder,
                    pathComponents = listOf(PathComponent(field_number = 3)),
                    value = 200,
                    versionNode = VersionNode(Version(timestamp = 2000L, actor_id = 2L, actor_version = 8L)),
                ),
                NodeMergeChangeProvider(
                    encoder = intEncoder,
                    pathComponents = listOf(PathComponent(field_number = 3)),
                    value = 300,
                    versionNode = VersionNode(Version(timestamp = 3000L, actor_id = 3L, actor_version = 2L)),
                ),
            )

        // When
        val result =
            resolver.applyChanges(
                localValue = localValue,
                localNode = localNode,
                localActors = localActors,
                incomingChanges = incomingChanges,
                incomingBaselineActors = incomingBaselineActors,
            )

        // Then - version vector has max of each actor
        assertThat(result.actors.version_vector)
            .containsExactly(
                1L,
                10L, // Local unchanged
                2L,
                8L, // Took incoming (8 > 5)
                3L,
                2L, // New from incoming
            )
    }
}
