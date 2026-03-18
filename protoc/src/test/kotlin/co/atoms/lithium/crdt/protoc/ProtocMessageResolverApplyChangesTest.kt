package co.atoms.lithium.crdt.protoc

import co.atoms.lithium.crdt.test.TestMessage
import co.atoms.lithium.crdt.data.Actors
import co.atoms.lithium.crdt.data.PathComponent
import co.atoms.lithium.crdt.data.Version
import co.atoms.lithium.crdt.data.VersionNode
import co.atoms.lithium.crdt.resolver.NodeMergeChangeProvider
import co.atoms.lithium.crdt.resolver.version.ApplyChangesResult
import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import org.junit.jupiter.api.Test

/**
 * Tests for MessageResolver.applyChanges function.
 *
 * This tests the top-level applyChanges logic in MessageResolver including:
 * - Version vector comparison and short-circuiting
 * - Early return when local state is ahead of incoming baseline
 * - Early return when no new changes after merge
 * - Normal change application path
 */
class ProtocMessageResolverApplyChangesTest {
    private val resolver = CrdtMessageResolverProvider().getOrCreateResolverFor(TestMessage.getDefaultInstance())

    // Encoder functions for primitive types
    private val intEncoder: (Int) -> ByteArray = { ByteBuffer.allocate(4).putInt(it).array() }
    private val longEncoder: (Long) -> ByteArray = { ByteBuffer.allocate(8).putLong(it).array() }

    @Test
    fun `applyChanges - empty changes list returns NO_CHANGE`() {
        // Given - local state exists
        val localValue =
            TestMessage.newBuilder()
                .setInt32Value(100)
                .setStringValue("local")
                .build()
        val localNode =
            VersionNode.newBuilder()
                .setVersion(
                    Version.newBuilder()
                        .setTimestamp(1000L)
                        .setActorId(1L)
                        .setActorVersion(5L)
                        .build(),
                ).build()
        val localActors =
            Actors.newBuilder()
                .setLocalActor(1L)
                .putAllVersionVector(mapOf(1L to 5L))
                .build()

        // When - apply empty changes
        val result =
            resolver.applyChanges(
                localValue = localValue,
                localNode = localNode,
                localActors = localActors,
                incomingChanges = emptyList(),
                incomingBaselineActors = mapOf(),
            )

        // Then - no change (ApplyChangesResult.UNCHANGED means local state is kept)
        assertThat(result.mergeResult.resolution).isEqualTo(ApplyChangesResult.UNCHANGED)
        assertThat(result.mergeResult.value).isEqualTo(localValue)
        assertThat(result.mergeResult.node).isEqualTo(localNode)
        assertThat(result.actors).isEqualTo(localActors)
    }

    @Test
    fun `applyChanges - local state ahead of incoming baseline returns NO_CHANGE`() {
        // Given - local has version 10, incoming baseline is at version 5
        val localValue = TestMessage.newBuilder().setInt32Value(100).build()
        val localNode =
            VersionNode.newBuilder()
                .setVersion(
                    Version.newBuilder()
                        .setTimestamp(1000L)
                        .setActorId(1L)
                        .setActorVersion(10L)
                        .build(),
                ).build()
        val localActors =
            Actors.newBuilder()
                .setLocalActor(1L)
                .putAllVersionVector(mapOf(1L to 10L))
                .build()

        // Incoming baseline is behind (version 5)
        val incomingBaselineActors = mapOf(1L to 5L)

        // Incoming change at version 6 (already included in local version 10)
        val incomingChanges =
            listOf(
                NodeMergeChangeProvider(
                    encoder = intEncoder,
                    pathComponents =
                    listOf(
                        PathComponent.newBuilder().setFieldNumber(3).build(), // int32Value field
                    ),
                    value = 200,
                    versionNode = VersionNode.newBuilder()
                        .setVersion(Version.newBuilder()
                            .setTimestamp(1001L)
                            .setActorId(1L)
                            .setActorVersion(6L)
                            .build())
                        .build(),
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
        val localActors =
            Actors.newBuilder()
                .setLocalActor(1L)
                .putAllVersionVector(mapOf(1L to 10L, 2L to 5L))
                .build()

        val localValue = TestMessage.newBuilder().setInt32Value(200).build()
        val localNode =
            VersionNode.newBuilder()
                .setVersion(
                    Version.newBuilder()
                        .setTimestamp(1000L)
                        .setActorId(1L)
                        .setActorVersion(10L)
                        .build(),
                ).build()

        // Incoming changes at version 8 (already included in local version 10)
        val incomingChanges =
            listOf(
                NodeMergeChangeProvider(
                    encoder = intEncoder,
                    pathComponents = listOf(PathComponent.newBuilder().setFieldNumber(3).build()),
                    value = 200,
                    versionNode =
                    VersionNode.newBuilder()
                        .setVersion(Version.newBuilder()
                            .setTimestamp(999L)
                            .setActorId(1L)
                            .setActorVersion(8L)
                            .build()).build(),
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
        assertThat(result.actors.versionVectorMap).isEqualTo(localActors.versionVectorMap)
    }

    @Test
    fun `applyChanges - applies new changes and merges version vectors`() {
        // Given - local at version 5
        val localActors =
            Actors.newBuilder()
                .setLocalActor(1L)
                .putAllVersionVector(mapOf(1L to 5L))
                .build()
        val localValue =
            TestMessage.newBuilder()
                .setInt32Value(100)
                .setStringValue("local")
                .build()
        val localNode =
            VersionNode.newBuilder()
                .setVersion(
                    Version.newBuilder()
                        .setTimestamp(1000L)
                        .setActorId(1L)
                        .setActorVersion(5L)
                        .build(),
                ).build()

        // Incoming baseline matches local (at version 5)
        val incomingBaselineActors = mapOf(1L to 5L)

        // Generate incoming changes from actor 2 using applyLocalWrite
        // Start with baseline state matching local
        val incomingBaselineValue =
            TestMessage.newBuilder()
                .setInt32Value(100)
                .setStringValue("local")
                .build()
        val incomingBaselineNode =
            VersionNode.newBuilder()
                .setVersion(
                    Version.newBuilder()
                        .setTimestamp(1000L)
                        .setActorId(1L)
                        .setActorVersion(5L)
                        .build(),
                ).build()
        val incomingBaselineActorsObj =
            Actors.newBuilder()
                .setLocalActor(2L) // Actor 2 is making the write
                .putAllVersionVector(mapOf(1L to 5L))
                .build()

        // Apply a write from actor 2 to change int32Value to 200
        val incomingNewValue =
            TestMessage.newBuilder()
                .setInt32Value(200)
                .setStringValue("local")
                .build()

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
        assertThat((result.mergeResult.value as TestMessage).int32Value).isEqualTo(200)
        assertThat((result.mergeResult.value as TestMessage).stringValue).isEqualTo("local") // Unchanged

        // Version vectors merged - actor 2 version 1 was added
        assertThat(result.actors.versionVectorMap)
            .containsExactly(1L, 5L, 2L, 1L)
    }

    @Test
    fun `applyChanges - null local value applies changes`() {
        // Given - no local value
        val localActors =
            Actors.newBuilder()
                .setLocalActor(1L)
                .putAllVersionVector(mapOf(1L to 1L))
                .build()

        val incomingBaselineActors = mapOf(1L to 1L)
        val incomingChanges =
            listOf(
                NodeMergeChangeProvider(
                    encoder = intEncoder,
                    pathComponents = listOf(PathComponent.newBuilder().setFieldNumber(3).build()),
                    value = 300,
                    versionNode =
                    VersionNode.newBuilder()
                        .setVersion(Version.newBuilder()
                            .setTimestamp(2000L)
                            .setActorId(2L)
                            .setActorVersion(1L)
                            .build()).build(),
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
        assertThat((result.mergeResult.value as TestMessage).int32Value).isEqualTo(300)
        assertThat(result.actors.versionVectorMap).containsExactly(1L, 1L, 2L, 1L)
    }

    @Test
    fun `applyChanges - null local actors creates new actors`() {
        // Given - no local actors
        val incomingBaselineActors = emptyMap<Long, Long>()
        val incomingChanges =
            listOf(
                NodeMergeChangeProvider(
                    encoder = intEncoder,
                    pathComponents = listOf(PathComponent.newBuilder().setFieldNumber(3).build()),
                    value = 400,
                    versionNode =
                    VersionNode.newBuilder()
                        .setVersion(Version.newBuilder()
                            .setTimestamp(3000L)
                            .setActorId(3L)
                            .setActorVersion(2L)
                            .build()).build(),
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
        assertThat((result.mergeResult.value as TestMessage).int32Value).isEqualTo(400)
        assertThat(result.actors.versionVectorMap).containsEntry(3L, 2L)
    }

    @Test
    fun `applyChanges - multiple changes from different actors`() {
        // Given
        val localActors =
            Actors.newBuilder()
                .setLocalActor(1L)
                .putAllVersionVector(mapOf(1L to 5L))
                .build()
        val localValue =
            TestMessage.newBuilder()
                .setInt32Value(100)
                .setInt64Value(200L)
                .build()
        val localNode =
            VersionNode.newBuilder()
                .setVersion(
                    Version.newBuilder()
                        .setTimestamp(1000L)
                        .setActorId(1L)
                        .setActorVersion(5L)
                        .build(),
                ).build()

        val incomingBaselineActors = mapOf(1L to 5L)

        // Multiple changes from actors 2 and 3
        val incomingChanges =
            listOf(
                NodeMergeChangeProvider(
                    encoder = intEncoder,
                    pathComponents = listOf(PathComponent.newBuilder().setFieldNumber(3).build()), // int32Value
                    value = 999,
                    versionNode =
                    VersionNode.newBuilder()
                        .setVersion(Version.newBuilder()
                            .setTimestamp(2000L)
                            .setActorId(2L)
                            .setActorVersion(3L)
                            .build()).build(),
                ),
                NodeMergeChangeProvider(
                    encoder = longEncoder,
                    pathComponents = listOf(PathComponent.newBuilder().setFieldNumber(4).build()), // int64Value
                    value = 888L,
                    versionNode =
                    VersionNode.newBuilder()
                        .setVersion(Version.newBuilder()
                            .setTimestamp(3000L)
                            .setActorId(3L)
                            .setActorVersion(1L)
                            .build()).build(),
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
        assertThat((result.mergeResult.value as TestMessage).int32Value).isEqualTo(999)
        assertThat((result.mergeResult.value as TestMessage).int64Value).isEqualTo(888L)

        // All actors in version vector
        assertThat(result.actors.versionVectorMap)
            .containsExactly(1L, 5L, 2L, 3L, 3L, 1L)
    }

    @Test
    fun `applyChanges - version vector merge takes max per actor`() {
        // Given - local has actor 1 at version 10, actor 2 at version 5
        val localActors =
            Actors.newBuilder()
                .setLocalActor(1L)
                .putAllVersionVector(mapOf(1L to 10L, 2L to 5L))
                .build()
        val localValue = TestMessage.newBuilder().setInt32Value(100).build()
        val localNode =
            VersionNode.newBuilder()
                .setVersion(
                    Version.newBuilder()
                        .setTimestamp(1000L)
                        .setActorId(1L)
                        .setActorVersion(10L)
                        .build(),
                ).build()

        val incomingBaselineActors = mapOf(1L to 10L, 2L to 5L)

        // Incoming has actor 2 at version 8 (higher), actor 3 at version 2 (new)
        val incomingChanges =
            listOf(
                NodeMergeChangeProvider(
                    encoder = intEncoder,
                    pathComponents = listOf(PathComponent.newBuilder().setFieldNumber(3).build()),
                    value = 200,
                    versionNode =
                    VersionNode.newBuilder()
                        .setVersion(Version.newBuilder()
                            .setTimestamp(2000L)
                            .setActorId(2L)
                            .setActorVersion(8L)
                            .build()).build(),
                ),
                NodeMergeChangeProvider(
                    encoder = intEncoder,
                    pathComponents = listOf(PathComponent.newBuilder().setFieldNumber(3).build()),
                    value = 300,
                    versionNode =
                    VersionNode.newBuilder()
                        .setVersion(Version.newBuilder()
                            .setTimestamp(3000L)
                            .setActorId(3L)
                            .setActorVersion(2L)
                            .build()).build(),
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
        assertThat(result.actors.versionVectorMap)
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
