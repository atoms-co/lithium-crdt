package co.atoms.protobuf.crdt.resolver.incoming

import co.atoms.protobuf.crdt.resolver.Counter
import co.atoms.protobuf.crdt.resolver.LongCounterResolver
import co.atoms.protobuf.crdt.resolver.NodeMergeChangeProvider
import co.atoms.protobuf.crdt.resolver.ResolutionDeltaContext
import co.atoms.protobuf.crdt.resolver.TestVersionTreeResolver
import co.atoms.protobuf.crdt.resolver.Version
import co.atoms.protobuf.crdt.resolver.VersionCount
import co.atoms.protobuf.crdt.resolver.VersionNode
import co.atoms.protobuf.crdt.resolver.version.ResolutionStrategy
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CounterIncomingChangeResolverTest {
    private val context = ResolutionDeltaContext<VersionNode, String>()
    private val decoder: (ByteArray) -> Long = { ByteBuffer.wrap(it).getLong() }
    private val encoder: (Long) -> ByteArray = { ByteBuffer.allocate(8).putLong(it).array() }
    private val resolver = LongCounterResolver(
        decoder = decoder,
        encoder = encoder,
        versionTreeResolver = TestVersionTreeResolver
    )

    @Test
    fun `applyChanges - single change with correct depth`() {
        // Given
        val localVersion = Version(2L, 1L, 1000L)
        val incomingVersion = Version(2L, 2L, 1100L)
        val incomingNode = VersionNode(
            version = incomingVersion,
            counter = Counter(
                mapOf(
                    2L to VersionCount(version = 2L, value = 15L)
                )
            )
        )
        val change = NodeMergeChangeProvider<Long, VersionNode, String>(
            encoder = encoder,
            pathComponents = emptyList(), // depth 0
            value = 15L,
            versionNode = incomingNode
        )

        // When
        val result = resolver.applyChanges(
            depth = 0,
            localValue = null,
            localNode = null,
            localVersion = localVersion,
            changes = listOf(change),
            context = context
        )

        // Then
        assertEquals(15L, result.value, "Should use incoming value")
        assertEquals(incomingNode, result.node, "Should use incoming node")
        assertEquals(ResolutionStrategy.INCOMING, result.resolution, "Should resolve as incoming")
    }

    @Test
    fun `applyChanges - delegates to resolveConflict`() {
        // Given
        val localVersion = Version(1L, 2L, 1000L)
        val incomingVersion = Version(2L, 3L, 1100L)
        val localNode = VersionNode(
            version = localVersion,
            counter = Counter(
                mapOf(
                    1L to VersionCount(version = 2L, value = 10L)
                )
            )
        )
        val incomingNode = VersionNode(
            version = incomingVersion,
            counter = Counter(
                mapOf(
                    2L to VersionCount(version = 3L, value = 15L)
                )
            )
        )
        val change = NodeMergeChangeProvider<Long, VersionNode, String>(
            encoder = encoder,
            pathComponents = emptyList(),
            value = 15L,
            versionNode = incomingNode
        )

        // When
        val result = resolver.applyChanges(
            depth = 0,
            localValue = 10L,
            localNode = localNode,
            localVersion = localVersion,
            changes = listOf(change),
            context = context
        )

        // Then - should merge counters from both actors
        assertEquals(25L, result.value, "Should sum both actors' counters")
        assertEquals(ResolutionStrategy.MERGED_VALUES, result.resolution, "Should merge values")
    }

    @Test
    fun `applyChanges - with path depth matching`() {
        // Given
        val localVersion = Version(1L, 1L, 1000L)
        val incomingVersion = Version(2L, 1L, 1100L)
        val incomingNode = VersionNode(
            version = incomingVersion,
            counter = Counter(
                mapOf(
                    2L to VersionCount(version = 1L, value = 20L)
                )
            )
        )
        val change = NodeMergeChangeProvider<Long, VersionNode, String>(
            encoder = encoder,
            pathComponents = listOf("field1", "field2"), // depth 2
            value = 20L,
            versionNode = incomingNode
        )

        // When
        val result = resolver.applyChanges(
            depth = 2,
            localValue = null,
            localNode = null,
            localVersion = localVersion,
            changes = listOf(change),
            context = context
        )

        // Then
        assertEquals(20L, result.value, "Should use incoming value")
        assertEquals(ResolutionStrategy.MERGED_VALUES, result.resolution, "Should resolve as incoming")
    }

    @Test
    fun `applyChanges - null incoming value treated as zero`() {
        // Given
        val localVersion = Version(1L, 1L, 1000L)
        val incomingVersion = Version(2L, 1L, 1100L)
        val incomingNode = VersionNode(
            version = incomingVersion,
            counter = Counter(
                mapOf(
                    2L to VersionCount(version = 1L, value = 0L)
                )
            )
        )
        val change = NodeMergeChangeProvider<Long, VersionNode, String>(
            encoder = encoder,
            pathComponents = emptyList(),
            value = null, // Null should be treated as 0
            versionNode = incomingNode
        )
        val localNode = VersionNode(
            version = localVersion,
            counter = Counter(
                mapOf(
                    1L to VersionCount(version = 1L, value = 10L)
                )
            )
        )

        // When
        val result = resolver.applyChanges(
            depth = 0,
            localValue = 10L,
            localNode = localNode,
            localVersion = localVersion,
            changes = listOf(change),
            context = context
        )

        // Then - should merge local counter with incoming zero counter
        assertEquals(10L, result.value, "Should keep local value")
        assertEquals(ResolutionStrategy.MERGED_VALUES, result.resolution, "Should merge")
    }

    @Test
    fun `applyChanges - fails with multiple changes`() {
        // Given
        val localVersion = Version(1L, 1L, 1000L)
        val incomingVersion = Version(2L, 1L, 1100L)
        val incomingNode = VersionNode(
            version = incomingVersion,
            counter = Counter(
                mapOf(
                    2L to VersionCount(version = 1L, value = 15L)
                )
            )
        )
        val change1 = NodeMergeChangeProvider<Long, VersionNode, String>(
            encoder = encoder,
            pathComponents = emptyList(),
            value = 15L,
            versionNode = incomingNode
        )
        val change2 = NodeMergeChangeProvider<Long, VersionNode, String>(
            encoder = encoder,
            pathComponents = emptyList(),
            value = 20L,
            versionNode = incomingNode
        )

        // When/Then - should assert because there should only be one change for a single value path
        assertFailsWith<AssertionError> {
            resolver.applyChanges(
                depth = 0,
                localValue = null,
                localNode = null,
                localVersion = localVersion,
                changes = listOf(change1, change2),
                context = context
            )
        }
    }

    @Test
    fun `applyChanges - fails with incorrect depth`() {
        // Given
        val localVersion = Version(1L, 1L, 1000L)
        val incomingVersion = Version(2L, 1L, 1100L)
        val incomingNode = VersionNode(
            version = incomingVersion,
            counter = Counter(
                mapOf(
                    2L to VersionCount(version = 1L, value = 15L)
                )
            )
        )
        val change = NodeMergeChangeProvider<Long, VersionNode, String>(
            encoder = encoder,
            pathComponents = listOf("field1", "field2"), // depth 2
            value = 15L,
            versionNode = incomingNode
        )

        // When/Then - depth parameter (1) doesn't match pathComponents size (2)
        assertFailsWith<AssertionError> {
            resolver.applyChanges(
                depth = 1, // Wrong depth!
                localValue = null,
                localNode = null,
                localVersion = localVersion,
                changes = listOf(change),
                context = context
            )
        }
    }

    @Test
    fun `applyChanges - uses minVersion when versionNode has no version`() {
        // Given
        val localVersion = Version(1L, 1L, 1000L)
        val incomingNode = VersionNode(
            version = Version(0L, 0L, 0L), // Invalid version (actorId = 0)
            counter = Counter(
                mapOf(
                    2L to VersionCount(version = 1L, value = 15L)
                )
            )
        )
        val change = NodeMergeChangeProvider<Long, VersionNode, String>(
            encoder = encoder,
            pathComponents = emptyList(),
            value = 15L,
            versionNode = incomingNode
        )

        // When
        val result = resolver.applyChanges(
            depth = 0,
            localValue = null,
            localNode = null,
            localVersion = localVersion,
            changes = listOf(change),
            context = context
        )

        // Then - versionValue is null when actorId is 0, so it uses minVersion and accepts incoming
        assertEquals(15L, result.value, "Should use incoming value")
        assertEquals(ResolutionStrategy.MERGED_VALUES, result.resolution, "Should resolve as incoming")
    }
}
