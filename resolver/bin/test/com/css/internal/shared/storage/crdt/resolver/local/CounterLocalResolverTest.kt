package com.css.internal.shared.storage.crdt.resolver.local

import com.css.internal.shared.storage.crdt.resolver.Counter
import com.css.internal.shared.storage.crdt.resolver.LongCounterResolver
import com.css.internal.shared.storage.crdt.resolver.ResolutionDeltaContext
import com.css.internal.shared.storage.crdt.resolver.TestVersionTreeResolver
import com.css.internal.shared.storage.crdt.resolver.Version
import com.css.internal.shared.storage.crdt.resolver.VersionCount
import com.css.internal.shared.storage.crdt.resolver.VersionNode
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CounterLocalResolverTest {
    private val context = ResolutionDeltaContext<VersionNode, String>()
    private val decoder: (ByteArray) -> Long = { ByteBuffer.wrap(it).getLong() }
    private val encoder: (Long) -> ByteArray = { ByteBuffer.allocate(8).putLong(it).array() }
    private val resolver = LongCounterResolver(
        decoder = decoder,
        encoder = encoder,
        versionTreeResolver = TestVersionTreeResolver
    )

    @Test
    fun `fast path - no change when difference is zero`() {
        // Given
        val currentVersion = Version(1L, 1000L, 1000L)
        val newVersion = Version(1L, 1100L, 1100L)
        val currentValue = 42L
        val currentNode = VersionNode(
            version = currentVersion,
            counter = Counter(
                mapOf(
                    1L to VersionCount(version = 1000L, value = 42L)
                )
            )
        )

        // When
        val result = resolver.applyLocalWrite(
            currentValue = currentValue,
            currentNode = currentNode,
            currentVersion = currentVersion,
            newValue = 42L, // Same value
            newVersion = newVersion,
            context = context,
        )

        // Then
        assertFalse(result.resolution, "Should not resolve when difference is zero")
        assertEquals(currentValue, result.value, "Should return current value")
        assertEquals(currentNode, result.node, "Should return existing node")
    }

    @Test
    fun `fast path - treats null as zero`() {
        // Given
        val currentVersion = Version(1L, 1000L, 1000L)
        val newVersion = Version(1L, 1100L, 1100L)

        // When - both null (0) and 0 should be treated as no change
        val result = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentVersion = currentVersion,
            newValue = null,
            newVersion = newVersion,
            context = context,
        )

        // Then
        assertFalse(result.resolution, "Should not resolve when both are null/zero")
        assertNull(result.value, "Should return null value")
        assertNotNull(result.node, "Should create node with current version")
        assertEquals(currentVersion, result.node.version, "Should use current version")
    }

    @Test
    fun `value increment - creates counter node when currentNode exists`() {
        // Given
        val currentVersion = Version(actorId = 1L, actorVersion = 1L, timestamp = 1000L)
        val newVersion = Version(actorId = 1L, actorVersion = 2L, timestamp = 1100L)
        val currentNode = VersionNode(
            version = currentVersion,
            counter = Counter(
                mapOf(
                    1L to VersionCount(version = 1L, value = 10L)
                )
            )
        )

        // When
        val result = resolver.applyLocalWrite(
            currentValue = 10L,
            currentNode = currentNode,
            currentVersion = currentVersion,
            newValue = 15L, // Increment by 5
            newVersion = newVersion,
            context = context,
        )

        // Then
        assertTrue(result.resolution, "Should resolve when value changes")
        assertEquals(15L, result.value, "Should return new value")
        assertNotNull(result.node, "Should have a node")
        assertEquals(newVersion, result.node.version, "Should use new version")
        assertNotNull(result.node.counter, "Should have counter data")
        assertEquals(15L, result.node.counter.actorCount[1L]?.value, "Counter should be updated")
    }

    @Test
    fun `value increment - creates version node when currentNode is null`() {
        // Given - no existing counter node, just version tracking
        val currentVersion = Version(1L, 1L, 1000L)
        val newVersion = Version(1L, 2L, 1100L)

        // When
        val result = resolver.applyLocalWrite(
            currentValue = null,
            currentNode = null,
            currentVersion = currentVersion,
            newValue = 5L, // First increment
            newVersion = newVersion,
            context = context,
        )

        // Then
        assertTrue(result.resolution, "Should resolve when incrementing from null")
        assertEquals(5L, result.value, "Should return new value")
        assertNotNull(result.node, "Should create node")
        assertEquals(newVersion, result.node.version, "Should use new version")
        // When currentNode is null, creates a simple version node (not a counter node)
        assertNull(result.node.counter, "Should create simple version node, not counter node")
    }

    @Test
    fun `value decrement - handles negative differences`() {
        // Given
        val currentVersion = Version(1L, 1L, 1000L)
        val newVersion = Version(1L, 2L, 1100L)
        val currentNode = VersionNode(
            version = currentVersion,
            counter = Counter(
                mapOf(
                    1L to VersionCount(version = 1L, value = 20L)
                )
            )
        )

        // When
        val result = resolver.applyLocalWrite(
            currentValue = 20L,
            currentNode = currentNode,
            currentVersion = currentVersion,
            newValue = 15L, // Decrement by 5
            newVersion = newVersion,
            context = context,
        )

        // Then
        assertTrue(result.resolution, "Should resolve when value decreases")
        assertEquals(15L, result.value, "Should return new value")
        assertEquals(15L, result.node?.counter?.actorCount?.get(1L)?.value, "Counter should be decremented")
    }

    @Test
    fun `ensureAfter - increments version when newVersion is older`() {
        // Given - newVersion has older timestamp than currentNode
        val currentVersion = Version(actorId = 1L, actorVersion = 1L, timestamp = 1000L)
        val newVersion = Version(actorId = 1L, actorVersion = 2L, timestamp = 950L) // Older!
        val currentNode = VersionNode(
            version = currentVersion,
            counter = Counter(
                mapOf(
                    1L to VersionCount(version = 1L, value = 10L)
                )
            )
        )
        val expectedVersion = Version(actorId = 1L, actorVersion = 2L, timestamp = 1001L) // ensureAfter result

        // When
        val result = resolver.applyLocalWrite(
            currentValue = 10L,
            currentNode = currentNode,
            currentVersion = currentVersion,
            newValue = 20L,
            newVersion = newVersion,
            context = context,
        )

        // Then
        assertTrue(result.resolution, "Should resolve")
        assertEquals(expectedVersion, result.node?.version, "Should increment timestamp to 1001")
    }

    @Test
    fun `when currentNode is null - creates simple version node with finalVersion`() {
        // Given - currentNode is null, newVersion has older timestamp than currentVersion
        val currentVersion = Version(1L, 1L, 1000L)
        val newVersion = Version(1L, 2L, 950L) // Older timestamp
        val expectedVersion = Version(1L, 2L, 1001L) // ensureAfter currentVersion

        // When
        val result = resolver.applyLocalWrite(
            currentValue = 5L,
            currentNode = null,
            currentVersion = currentVersion,
            newValue = 10L,
            newVersion = newVersion,
            context = context,
        )

        // Then - uses finalVersion (with ensureAfter) even when currentNode is null
        assertTrue(result.resolution, "Should resolve")
        assertEquals(expectedVersion, result.node?.version, "Should use finalVersion to ensure monotonic progression")
        assertNull(result.node?.counter, "Should create simple version node, not counter node")
    }

    @Test
    fun `ensureAfter - keeps newVersion when already ahead`() {
        // Given - newVersion is already ahead
        val currentVersion = Version(1L, 1L, 1000L)
        val newVersion = Version(1L, 2L, 1500L) // Already ahead
        val currentNode = VersionNode(
            version = currentVersion,
            counter = Counter(
                mapOf(
                    1L to VersionCount(version = 1L, value = 10L)
                )
            )
        )

        // When
        val result = resolver.applyLocalWrite(
            currentValue = 10L,
            currentNode = currentNode,
            currentVersion = currentVersion,
            newValue = 20L,
            newVersion = newVersion,
            context = context,
        )

        // Then
        assertTrue(result.resolution, "Should resolve")
        assertEquals(newVersion, result.node?.version, "Should keep original newVersion unchanged")
    }

    @Test
    fun `context change - adds change to context when value differs`() {
        // Given
        val currentVersion = Version(1L, 1L, 1000L)
        val newVersion = Version(1L, 2L, 1100L)
        val context = ResolutionDeltaContext<VersionNode, String>()

        // When
        resolver.applyLocalWrite(
            currentValue = 10L,
            currentNode = null,
            currentVersion = currentVersion,
            newValue = 15L,
            newVersion = newVersion,
            context = context,
        )

        // Then
        assertTrue(context.changes.isNotEmpty(), "Should add change to context")
        assertEquals(1, context.changes.size, "Should have exactly one change")
    }

    @Test
    fun `context change - no change added when difference is zero`() {
        // Given
        val currentVersion = Version(1L, 1L, 1000L)
        val newVersion = Version(1L, 2L, 1100L)
        val currentNode = VersionNode(
            version = currentVersion,
            counter = Counter(
                mapOf(
                    1L to VersionCount(version = 1L, value = 10L)
                )
            )
        )
        val context = ResolutionDeltaContext<VersionNode, String>()

        // When
        resolver.applyLocalWrite(
            currentValue = 10L,
            currentNode = currentNode,
            currentVersion = currentVersion,
            newValue = 10L, // Same value
            newVersion = newVersion,
            context = context,
        )

        // Then
        assertTrue(context.changes.isEmpty(), "Should not add change when difference is zero")
    }

    @Test
    fun `multiple actor counters - plus increments actor contribution`() {
        // Given - node has counters from multiple actors
        val currentVersion = Version(actorId = 1L, actorVersion = 5L, timestamp = 1000L)
        val newVersion = Version(actorId = 1L, actorVersion = 6L, timestamp = 1100L)
        val currentNode = VersionNode(
            version = currentVersion,
            counter = Counter(
                mapOf(
                    1L to VersionCount(version = 5L, value = 10L),
                    2L to VersionCount(version = 3L, value = 5L),
                    3L to VersionCount(version = 2L, value = 7L)
                )
            )
        )

        // When
        val result = resolver.applyLocalWrite(
            currentValue = 22L, // Sum of all actors: 10 + 5 + 7
            currentNode = currentNode,
            currentVersion = currentVersion,
            newValue = 27L, // Increment by 5
            newVersion = newVersion,
            context = context,
        )

        // Then - plus adds the difference to actor 1's contribution
        assertTrue(result.resolution, "Should resolve")
        assertEquals(27L, result.value, "Should return new value")
        assertNotNull(result.node?.counter, "Should have counter")
        assertEquals(15L, result.node.counter.actorCount[1L]?.value, "Actor 1 counter incremented by 5 (10 + 5 = 15)")
        assertEquals(5L, result.node.counter.actorCount[2L]?.value, "Actor 2 counter should be preserved")
        assertEquals(7L, result.node.counter.actorCount[3L]?.value, "Actor 3 counter should be preserved")
        // Total: 15 + 5 + 7 = 27
        with(TestVersionTreeResolver) {
            assertEquals(27L, result.node.counterValue, "Counter sums to value")
        }
    }
}
