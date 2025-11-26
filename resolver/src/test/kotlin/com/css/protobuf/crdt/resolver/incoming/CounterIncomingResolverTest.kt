package com.css.protobuf.crdt.resolver.incoming

import com.css.protobuf.crdt.resolver.Counter
import com.css.protobuf.crdt.resolver.LongCounterResolver
import com.css.protobuf.crdt.resolver.ResolutionDeltaContext
import com.css.protobuf.crdt.resolver.TestVersionTreeResolver
import com.css.protobuf.crdt.resolver.Version
import com.css.protobuf.crdt.resolver.VersionCount
import com.css.protobuf.crdt.resolver.VersionNode
import com.css.protobuf.crdt.resolver.version.ResolutionStrategy
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CounterIncomingResolverTest {
    private val context = ResolutionDeltaContext<VersionNode, String>()
    private val decoder: (ByteArray) -> Long = { ByteBuffer.wrap(it).getLong() }
    private val encoder: (Long) -> ByteArray = { ByteBuffer.allocate(8).putLong(it).array() }
    private val resolver =
        LongCounterResolver(decoder = decoder, encoder = encoder, versionTreeResolver = TestVersionTreeResolver)

    @Test
    fun `incoming null with zero value and null local node - no change`() {
        // Given - both local and incoming have no counter nodes
        val localVersion = Version(1L, 1L, 1000L)
        val incomingVersion = Version(1L, 1L, 1000L)

        // When
        val result =
            resolver.resolveConflict(
                localValue = null,
                localNode = null,
                localVersion = localVersion,
                incomingValue = null,
                incomingNode = null,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then
        assertNull(result.value, "Should set default value")
        assertNotNull(result.node, "Should create node")
        assertEquals(localVersion, result.node.version, "Should use local version")
        assertEquals(ResolutionStrategy.NO_CHANGE, result.resolution, "Should resolve as NO_CHANGE")
    }

    @Test
    fun `incoming only - use incoming value`() {
        // Given - local has nothing, incoming has counter
        val localVersion = Version(2L, 1L, 1000L)
        val incomingVersion = Version(2L, 2L, 1100L)
        val incomingNode =
            VersionNode(
                version = incomingVersion,
                counter = Counter(mapOf(2L to VersionCount(version = 2L, value = 15L))),
            )

        // When
        val result =
            resolver.resolveConflict(
                localValue = null,
                localNode = null,
                localVersion = localVersion,
                incomingValue = 15L,
                incomingNode = incomingNode,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then
        assertEquals(15L, result.value, "Should use incoming value")
        assertEquals(incomingNode, result.node, "Should use incoming node")
        assertEquals(ResolutionStrategy.INCOMING, result.resolution, "Should resolve as INCOMING")
    }

    @Test
    fun `local only - keep local value`() {
        // Given - local has counter, incoming has nothing
        val localVersion = Version(1L, 1L, 1000L)
        val incomingVersion = Version(2L, 1L, 1100L)
        val localNode =
            VersionNode(version = localVersion, counter = Counter(mapOf(1L to VersionCount(version = 1L, value = 10L))))

        // When
        val result =
            resolver.resolveConflict(
                localValue = 10L,
                localNode = localNode,
                localVersion = localVersion,
                incomingValue = 15L,
                incomingNode = null,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then - incoming node will be created from incoming value, then merged
        assertNotNull(result.value, "Should have a value")
        assertNotNull(result.node, "Should have a node")
        assertEquals(ResolutionStrategy.MERGED_VALUES, result.resolution, "Should merge counters")
    }

    @Test
    fun `merge counters from different actors`() {
        // Given - local and incoming have counters from different actors
        val localVersion = Version(1L, 2L, 1000L)
        val incomingVersion = Version(2L, 3L, 1100L)
        val localNode =
            VersionNode(version = localVersion, counter = Counter(mapOf(1L to VersionCount(version = 2L, value = 10L))))
        val incomingNode =
            VersionNode(
                version = incomingVersion,
                counter = Counter(mapOf(2L to VersionCount(version = 3L, value = 15L))),
            )

        // When
        val result =
            resolver.resolveConflict(
                localValue = 10L,
                localNode = localNode,
                localVersion = localVersion,
                incomingValue = 15L,
                incomingNode = incomingNode,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then - should merge both actors' counters
        assertEquals(25L, result.value, "Should sum both actors' counters")
        assertNotNull(result.node?.counter, "Should have counter")
        assertEquals(10L, result.node.counter.actorCount.get(1L)?.value, "Should have actor 1 counter")
        assertEquals(15L, result.node.counter.actorCount.get(2L)?.value, "Should have actor 2 counter")
        assertEquals(ResolutionStrategy.MERGED_VALUES, result.resolution, "Should resolve as MERGED_VALUES")
    }

    @Test
    fun `merge counters - prefer higher version from same actor`() {
        // Given - both nodes have counters from the same actor
        val localVersion = Version(1L, 2L, 1000L)
        val incomingVersion = Version(1L, 5L, 1100L) // Higher version
        val localNode =
            VersionNode(version = localVersion, counter = Counter(mapOf(1L to VersionCount(version = 2L, value = 10L))))
        val incomingNode =
            VersionNode(
                version = incomingVersion,
                counter =
                Counter(
                    mapOf(
                        1L to VersionCount(version = 5L, value = 20L) // Higher version
                    )
                ),
            )

        // When
        val result =
            resolver.resolveConflict(
                localValue = 10L,
                localNode = localNode,
                localVersion = localVersion,
                incomingValue = 20L,
                incomingNode = incomingNode,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then - merge returns incomingNode when incoming has higher version
        assertEquals(20L, result.value, "Should use incoming value with higher version")
        assertEquals(incomingNode, result.node, "Should return incoming node")
        assertEquals(20L, result.node?.counter?.actorCount?.get(1L)?.value, "Should use incoming counter")
        assertEquals(5L, result.node?.counter?.actorCount?.get(1L)?.version, "Should use incoming version")
        assertEquals(
            ResolutionStrategy.INCOMING,
            result.resolution,
            "Should resolve as INCOMING when merge returns incoming node",
        )
    }

    @Test
    fun `merge returns local node when unchanged`() {
        // Given - local has higher version counter from same actor
        val localVersion = Version(1L, 5L, 1100L)
        val incomingVersion = Version(1L, 2L, 1000L) // Lower version
        val localNode =
            VersionNode(version = localVersion, counter = Counter(mapOf(1L to VersionCount(version = 5L, value = 20L))))
        val incomingNode =
            VersionNode(
                version = incomingVersion,
                counter =
                Counter(
                    mapOf(
                        1L to VersionCount(version = 2L, value = 10L) // Lower version
                    )
                ),
            )

        // When
        val result =
            resolver.resolveConflict(
                localValue = 20L,
                localNode = localNode,
                localVersion = localVersion,
                incomingValue = 10L,
                incomingNode = incomingNode,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then - merge returns localNode when local has higher version
        assertEquals(20L, result.value, "Should keep local value")
        assertEquals(localNode, result.node, "Should keep local node unchanged")
        assertEquals(ResolutionStrategy.LOCAL, result.resolution, "Should resolve as LOCAL")
    }

    @Test
    fun `merge returns incoming node when local is null`() {
        // Given - local has value but no node
        val localVersion = Version(1L, 1L, 1000L)
        val incomingVersion = Version(2L, 1L, 1100L)
        val incomingNode =
            VersionNode(
                version = incomingVersion,
                counter = Counter(mapOf(2L to VersionCount(version = 1L, value = 15L))),
            )

        // When - local has non-zero value, so counter node will be created
        val result =
            resolver.resolveConflict(
                localValue = 5L,
                localNode = null,
                localVersion = localVersion,
                incomingValue = 15L,
                incomingNode = incomingNode,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then - should merge the created local counter with incoming
        assertEquals(20L, result.value, "Should sum local and incoming values")
        assertNotNull(result.node?.counter, "Should have counter")
        assertEquals(5L, result.node.counter.actorCount.get(1L)?.value, "Should have local actor counter")
        assertEquals(15L, result.node.counter.actorCount.get(2L)?.value, "Should have incoming actor counter")
        assertEquals(ResolutionStrategy.MERGED_VALUES, result.resolution, "Should resolve as MERGED_VALUES")
    }

    @Test
    fun `merge complex multi-actor counters`() {
        // Given - multiple actors with various versions
        val localVersion = Version(1L, 5L, 1000L)
        val incomingVersion = Version(2L, 3L, 1100L)
        val localNode =
            VersionNode(
                version = localVersion,
                counter =
                Counter(
                    mapOf(
                        1L to VersionCount(version = 5L, value = 10L),
                        2L to VersionCount(version = 2L, value = 8L), // Lower version than incoming
                        3L to VersionCount(version = 7L, value = 12L),
                    )
                ),
            )
        val incomingNode =
            VersionNode(
                version = incomingVersion,
                counter =
                Counter(
                    mapOf(
                        2L to VersionCount(version = 3L, value = 15L), // Higher version
                        4L to VersionCount(version = 1L, value = 5L),
                    )
                ),
            )

        // When
        val result =
            resolver.resolveConflict(
                localValue = 30L, // 10 + 8 + 12
                localNode = localNode,
                localVersion = localVersion,
                incomingValue = 20L, // 15 + 5
                incomingNode = incomingNode,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then
        assertEquals(42L, result.value, "Should sum: 10 + 15 + 12 + 5")
        assertNotNull(result.node?.counter, "Should have counter")
        assertEquals(10L, result.node.counter.actorCount[1L]?.value, "Actor 1 unchanged")
        assertEquals(15L, result.node.counter.actorCount[2L]?.value, "Actor 2 from incoming (higher version)")
        assertEquals(12L, result.node.counter.actorCount[3L]?.value, "Actor 3 unchanged")
        assertEquals(5L, result.node.counter.actorCount[4L]?.value, "Actor 4 from incoming")
        assertEquals(ResolutionStrategy.MERGED_VALUES, result.resolution, "Should resolve as MERGED_VALUES")
    }

    @Test
    fun `context change - adds change for incoming resolution`() {
        // Given
        val localVersion = Version(1L, 1L, 1000L)
        val incomingVersion = Version(2L, 1L, 1100L)
        val incomingNode =
            VersionNode(
                version = incomingVersion,
                counter = Counter(mapOf(2L to VersionCount(version = 1L, value = 15L))),
            )
        val context = ResolutionDeltaContext<VersionNode, String>()

        // When
        resolver.resolveConflict(
            localValue = null,
            localNode = null,
            localVersion = localVersion,
            incomingValue = 15L,
            incomingNode = incomingNode,
            incomingVersion = incomingVersion,
            context = context,
        )

        // Then
        assertEquals(1, context.changes.size, "Should add change for incoming")
    }

    @Test
    fun `context change - adds change for merged resolution`() {
        // Given
        val localVersion = Version(1L, 1L, 1000L)
        val incomingVersion = Version(2L, 1L, 1100L)
        val localNode =
            VersionNode(version = localVersion, counter = Counter(mapOf(1L to VersionCount(version = 1L, value = 10L))))
        val incomingNode =
            VersionNode(
                version = incomingVersion,
                counter = Counter(mapOf(2L to VersionCount(version = 1L, value = 15L))),
            )
        val context = ResolutionDeltaContext<VersionNode, String>()

        // When
        resolver.resolveConflict(
            localValue = 10L,
            localNode = localNode,
            localVersion = localVersion,
            incomingValue = 15L,
            incomingNode = incomingNode,
            incomingVersion = incomingVersion,
            context = context,
        )

        // Then
        assertEquals(1, context.changes.size, "Should add change for merged values")
    }

    @Test
    fun `context change - no change for local resolution`() {
        // Given
        val localVersion = Version(1L, 5L, 1100L)
        val incomingVersion = Version(1L, 2L, 1000L)
        val localNode =
            VersionNode(version = localVersion, counter = Counter(mapOf(1L to VersionCount(version = 5L, value = 20L))))
        val incomingNode =
            VersionNode(
                version = incomingVersion,
                counter = Counter(mapOf(1L to VersionCount(version = 2L, value = 10L))),
            )
        val context = ResolutionDeltaContext<VersionNode, String>()

        // When
        resolver.resolveConflict(
            localValue = 20L,
            localNode = localNode,
            localVersion = localVersion,
            incomingValue = 10L,
            incomingNode = incomingNode,
            incomingVersion = incomingVersion,
            context = context,
        )

        // Then
        assertEquals(0, context.changes.size, "Should not add change for local resolution")
    }

    @Test
    fun `merge single-actor nodes without counter structures - converts to multi-actor counter`() {
        // This test verifies the fix for merging nodes that use single-actor optimization
        // (no counter structure). Without the takeIf { it.counterActors > 0 } fix, this would fail.

        // Given - both nodes are simple version nodes WITHOUT counter structures
        // Actor 1 wrote value 10, creating a simple version node (single-actor optimization)
        val localVersion = Version(actorId = 1L, actorVersion = 1L, timestamp = 1000L)
        val localNode =
            VersionNode(
                version = localVersion,
                counter = null, // No counter structure - single-actor optimization
            )

        // Actor 2 wrote value 15, also creating a simple version node (single-actor optimization)
        val incomingVersion = Version(actorId = 2L, actorVersion = 1L, timestamp = 1100L)
        val incomingNode =
            VersionNode(
                version = incomingVersion,
                counter = null, // No counter structure - single-actor optimization
            )

        // When - merging two single-actor nodes
        val result =
            resolver.resolveConflict(
                localValue = 10L,
                localNode = localNode,
                localVersion = localVersion,
                incomingValue = 15L,
                incomingNode = incomingNode,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then - should create proper multi-actor counter structure and sum values
        assertEquals(25L, result.value, "Should sum both actors' values: 10 + 15 = 25")
        assertNotNull(result.node, "Should create merged node")
        assertNotNull(result.node.counter, "Should create counter structure for multi-actor merge")
        assertEquals(10L, result.node.counter.actorCount[1L]?.value, "Should have actor 1 contribution")
        assertEquals(1L, result.node.counter.actorCount[1L]?.version, "Should have actor 1 version")
        assertEquals(15L, result.node.counter.actorCount[2L]?.value, "Should have actor 2 contribution")
        assertEquals(1L, result.node.counter.actorCount[2L]?.version, "Should have actor 2 version")
        assertEquals(ResolutionStrategy.MERGED_VALUES, result.resolution, "Should resolve as MERGED_VALUES")

        // Verify counter sum matches value
        with(TestVersionTreeResolver) {
            assertEquals(25L, result.node.counterValue, "Counter should sum to final value")
            assertEquals(2, result.node.counterActors, "Should have 2 actors in counter")
        }
    }

    @Test
    fun `merge local single-actor node with incoming multi-actor counter`() {
        // Given - local has simple version node (no counter), incoming has multi-actor counter
        val localVersion = Version(actorId = 1L, actorVersion = 1L, timestamp = 1000L)
        val localNode =
            VersionNode(
                version = localVersion,
                counter = null, // Single-actor optimization
            )

        val incomingVersion = Version(actorId = 2L, actorVersion = 2L, timestamp = 1100L)
        val incomingNode =
            VersionNode(
                version = incomingVersion,
                counter =
                Counter(
                    mapOf(
                        2L to VersionCount(version = 2L, value = 15L),
                        3L to VersionCount(version = 1L, value = 5L),
                    )
                ),
            )

        // When
        val result =
            resolver.resolveConflict(
                localValue = 10L,
                localNode = localNode,
                localVersion = localVersion,
                incomingValue = 20L, // 15 + 5
                incomingNode = incomingNode,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then - should merge local single-actor with incoming multi-actor
        assertEquals(30L, result.value, "Should sum: 10 + 15 + 5 = 30")
        assertNotNull(result.node?.counter, "Should have counter")
        assertEquals(10L, result.node.counter.actorCount[1L]?.value, "Should have local actor contribution")
        assertEquals(15L, result.node.counter.actorCount[2L]?.value, "Should have incoming actor 2 contribution")
        assertEquals(5L, result.node.counter.actorCount[3L]?.value, "Should have incoming actor 3 contribution")
        assertEquals(ResolutionStrategy.MERGED_VALUES, result.resolution, "Should resolve as MERGED_VALUES")

        // Verify counter sum
        with(TestVersionTreeResolver) {
            assertEquals(30L, result.node.counterValue, "Counter should sum to final value")
            assertEquals(3, result.node.counterActors, "Should have 3 actors in counter")
        }
    }

    @Test
    fun `merge multi-actor counter with incoming single-actor node`() {
        // Given - local has multi-actor counter, incoming has simple version node
        val localVersion = Version(actorId = 1L, actorVersion = 2L, timestamp = 1000L)
        val localNode =
            VersionNode(
                version = localVersion,
                counter =
                Counter(
                    mapOf(
                        1L to VersionCount(version = 2L, value = 10L),
                        3L to VersionCount(version = 1L, value = 8L),
                    )
                ),
            )

        val incomingVersion = Version(actorId = 2L, actorVersion = 1L, timestamp = 1100L)
        val incomingNode =
            VersionNode(
                version = incomingVersion,
                counter = null, // Single-actor optimization
            )

        // When
        val result =
            resolver.resolveConflict(
                localValue = 18L, // 10 + 8
                localNode = localNode,
                localVersion = localVersion,
                incomingValue = 12L,
                incomingNode = incomingNode,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then - should merge multi-actor with incoming single-actor
        assertEquals(30L, result.value, "Should sum: 10 + 12 + 8 = 30")
        assertNotNull(result.node?.counter, "Should have counter")
        assertEquals(10L, result.node.counter.actorCount[1L]?.value, "Should have local actor 1 contribution")
        assertEquals(12L, result.node.counter.actorCount[2L]?.value, "Should have incoming actor contribution")
        assertEquals(8L, result.node.counter.actorCount[3L]?.value, "Should have local actor 3 contribution")
        assertEquals(ResolutionStrategy.MERGED_VALUES, result.resolution, "Should resolve as MERGED_VALUES")

        // Verify counter sum
        with(TestVersionTreeResolver) {
            assertEquals(30L, result.node.counterValue, "Counter should sum to final value")
            assertEquals(3, result.node.counterActors, "Should have 3 actors in counter")
        }
    }
}
