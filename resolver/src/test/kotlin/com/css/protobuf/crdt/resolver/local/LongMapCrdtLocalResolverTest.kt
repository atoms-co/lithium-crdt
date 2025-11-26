package com.css.protobuf.crdt.resolver.local

import com.css.protobuf.crdt.resolver.LongMapResolver
import com.css.protobuf.crdt.resolver.ResolutionDeltaContext
import com.css.protobuf.crdt.resolver.SingleValueResolver
import com.css.protobuf.crdt.resolver.TestVersionTreeResolver
import com.css.protobuf.crdt.resolver.Version
import com.css.protobuf.crdt.resolver.VersionNode
import com.css.protobuf.crdt.resolver.descriptor.CollectionType
import com.css.protobuf.crdt.resolver.descriptor.KeyType
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LongMapCrdtLocalResolverTest {
    private val context = ResolutionDeltaContext<VersionNode, String>()
    private val decoder: (ByteArray) -> String = { it.decodeToString() }
    private val encoder: (String) -> ByteArray = { it.toByteArray() }
    private val valueResolver = SingleValueResolver(
        decoder = decoder,
        encoder = encoder,
        versionTreeResolver = TestVersionTreeResolver
    )
    private val mockMapDecoder: (ByteArray) -> Map<Long, String> = mockk()
    private val mockMapEncoder: (Map<Long, String>) -> ByteArray = mockk()
    private val resolver = LongMapResolver(
        config = CollectionType.Map(
            keyType = KeyType.LONG,
            maxTombstone = 5,
            tombstoneTtl = 2000
        ),
        decoder = mockMapDecoder,
        encoder = mockMapEncoder,
        valueResolver = valueResolver,
        versionTreeResolver = TestVersionTreeResolver,
    )

    @Test
    fun `long map resolver works with long keys`() {
        // Given
        val currentVersion = Version(1L, 1000L, 1000L)
        val newVersion = Version(1L, 1100L, 1100L)
        val currentMap = mapOf(1L to "value1", 2L to "value2")
        val newMap = mapOf(1L to "updated_value", 3L to "new_value") // Update key 1, add key 3, remove key 2

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = currentMap,
                currentNode = null,
                currentVersion = currentVersion,
                newValue = newMap,
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertTrue(result.resolution, "Should resolve when map changes")
        assertEquals(newMap, result.value, "Should return updated map")
        assertEquals(newVersion, result.node?.version, "Should set map version")
        assertNotNull(result.node?.long_map, "Should create long_map")

        val entries = result.node.long_map
        assertTrue(entries.containsKey(1), "Should track updated key")
        assertTrue(entries.containsKey(3), "Should track new key")
        assertTrue(entries.containsKey(2), "Should track removed key (tombstone)")
    }

    @Test
    fun `tombstone cleanup removes old tombstones outside TTL window`() {
        // Given: Map with tombstones at various timestamps
        val baseTime = 10000L
        val ttl = 2000L // resolver has TTL of 2000ms
        val oldTombstone = Version(1L, baseTime - 3000, baseTime - 3000) // Outside window
        val recentTombstone = Version(1L, baseTime - 1000, baseTime - 1000) // Inside window

        val existingNode = VersionNode(
            version = Version(1L, baseTime, baseTime),
            long_map = mutableMapOf(
                1L to VersionNode(version = oldTombstone), // Old tombstone - should be removed
                2L to VersionNode(version = recentTombstone), // Recent tombstone - should stay
                3L to VersionNode(version = Version(1L, baseTime, baseTime)) // Live data
            )
        )

        val currentMap = mapOf(3L to "live_value") // Only key 3 has live data
        val newMap = mapOf(3L to "live_value", 4L to "new_value") // No deletions, just addition
        val newVersion = Version(1L, baseTime + 100, baseTime + 100)

        // When: Apply write without creating new tombstones
        val result = resolver.applyLocalWrite(
            currentValue = currentMap,
            currentNode = existingNode,
            currentVersion = Version(1L, baseTime, baseTime),
            newValue = newMap,
            newVersion = newVersion,
            context = context,
        )

        // Then: Old tombstone should NOT be removed because we didn't create a new tombstone
        val entries = result.node?.long_map
        assertNotNull(entries)
        assertTrue(entries.containsKey(1), "Old tombstone should still exist (no new tombstone created)")
        assertTrue(entries.containsKey(2), "Recent tombstone should exist")
        assertTrue(entries.containsKey(3), "Live data should exist")
        assertTrue(entries.containsKey(4), "New key should exist")
    }

    @Test
    fun `tombstone cleanup removes old tombstones when new tombstone is created`() {
        // Given: Map with old tombstone (key 1) and live data (key 2)
        val baseTime = 10000L
        val oldTombstone = Version(1L, baseTime - 3000, baseTime - 3000)

        val existingNode = VersionNode(
            version = Version(1L, baseTime, baseTime),
            long_map = mutableMapOf(
                1L to VersionNode(version = oldTombstone), // Old tombstone (deleted key)
                2L to VersionNode(version = Version(1L, baseTime, baseTime)) // Live data
            )
        )

        val currentMap = mapOf(2L to "value2") // Only key 2 has live data (key 1 is tombstone)
        val newMap = emptyMap<Long, String>() // Delete key 2 - creates new tombstone
        val newVersion = Version(1L, baseTime + 100, baseTime + 100)

        // When: Apply write that creates a new tombstone
        val result = resolver.applyLocalWrite(
            currentValue = currentMap,
            currentNode = existingNode,
            currentVersion = Version(1L, baseTime, baseTime),
            newValue = newMap,
            newVersion = newVersion,
            context = context,
        )

        // Then: Old tombstone should be removed (outside TTL window)
        val entries = result.node?.long_map
        assertNotNull(entries)
        assertEquals(1, entries.size, "Should have 1 entry (new tombstone, old tombstone removed)")
        assertTrue(!entries.containsKey(1), "Old tombstone should be removed")
        assertTrue(entries.containsKey(2), "New tombstone should exist")
    }

    @Test
    fun `tombstone cleanup updates live data versions to stay in TTL window`() {
        // Given: Live data with version outside the new TTL window
        val baseTime = 10000L
        val oldDataVersion = Version(1L, baseTime - 5000, baseTime - 5000)

        val existingNode = VersionNode(
            version = Version(1L, baseTime, baseTime),
            long_map = mutableMapOf(
                1L to VersionNode(version = oldDataVersion), // Old live data version
                2L to VersionNode(version = Version(1L, baseTime, baseTime)) // Recent live data
            )
        )

        val currentMap = mapOf(1L to "old_value", 2L to "recent_value", 3L to "value3")
        val newMap = mapOf(1L to "old_value", 2L to "recent_value") // Delete key 3 - creates tombstone
        val newVersion = Version(2L, baseTime + 100, baseTime + 100)

        // When: Apply write that creates tombstone
        val result = resolver.applyLocalWrite(
            currentValue = currentMap,
            currentNode = existingNode,
            currentVersion = Version(1L, baseTime, baseTime),
            newValue = newMap,
            newVersion = newVersion,
            context = context,
        )

        // Then: Old live data version should be updated to window start
        val entries = result.node?.long_map
        assertNotNull(entries)

        val key1Version = entries[1L]?.version
        assertNotNull(key1Version, "Key 1 should have version")

        // TTL window: [newVersion.timestamp - 2000, newVersion.timestamp]
        val expectedWindowStart = newVersion.timestamp - 2000L
        assertEquals(expectedWindowStart, key1Version.timestamp, "Should update to window start timestamp")
        assertEquals(newVersion.actorId, key1Version.actorId, "Should use new write's actor ID")
    }

    @Test
    fun `tombstone cleanup removes oldest tombstones FIFO when exceeding max count`() {
        // Given: Resolver with maxTombstone = 5, create 6 tombstones
        val baseTime = 10000L
        val existingNode = VersionNode(
            version = Version(1L, baseTime, baseTime),
            long_map = mutableMapOf(
                // 5 existing tombstones
                1L to VersionNode(version = Version(1L, baseTime + 100, baseTime + 100)),
                2L to VersionNode(version = Version(1L, baseTime + 200, baseTime + 200)),
                3L to VersionNode(version = Version(1L, baseTime + 300, baseTime + 300)),
                4L to VersionNode(version = Version(1L, baseTime + 400, baseTime + 400)),
                5L to VersionNode(version = Version(1L, baseTime + 500, baseTime + 500)),
                6L to VersionNode(version = Version(1L, baseTime + 600, baseTime + 600)) // Live data
            )
        )

        val currentMap = mapOf(6L to "value6") // Only key 6 is live
        val newMap = emptyMap<Long, String>() // Delete key 6 - creates 6th tombstone
        val newVersion = Version(1L, baseTime + 700, baseTime + 700)

        // When: Apply write that creates 6th tombstone (exceeds maxTombstone = 5)
        val result = resolver.applyLocalWrite(
            currentValue = currentMap,
            currentNode = existingNode,
            currentVersion = Version(1L, baseTime, baseTime),
            newValue = newMap,
            newVersion = newVersion,
            context = context,
        )

        // Then: Oldest tombstone should be removed (FIFO)
        val entries = result.node?.long_map
        assertNotNull(entries)
        assertEquals(5, entries.size, "Should keep max 5 tombstones")
        assertTrue(!entries.containsKey(1), "Oldest tombstone (key 1) should be removed")
        assertTrue(entries.containsKey(2), "Key 2 tombstone should remain")
        assertTrue(entries.containsKey(6), "Newest tombstone (key 6) should remain")
    }

    @Test
    fun `no cleanup performed when no TTL and under max tombstone limit`() {
        // Given: Resolver with no TTL
        val noTtlResolver = LongMapResolver(
            config = CollectionType.Map(
                keyType = KeyType.LONG,
                maxTombstone = 10,
                tombstoneTtl = null // No TTL
            ),
            decoder = mockMapDecoder,
            encoder = mockMapEncoder,
            valueResolver = valueResolver,
            versionTreeResolver = TestVersionTreeResolver,
        )

        val baseTime = 10000L
        val oldTombstone = Version(1L, baseTime - 10000, baseTime - 10000)
        val existingNode = VersionNode(
            version = Version(1L, baseTime, baseTime),
            long_map = mutableMapOf(
                1L to VersionNode(version = oldTombstone), // Very old tombstone
                2L to VersionNode(version = Version(1L, baseTime, baseTime)) // Live data
            )
        )

        val currentMap = mapOf(2L to "value2", 3L to "value3")
        val newMap = mapOf(2L to "value2") // Delete key 3 - creates tombstone
        val newVersion = Version(1L, baseTime + 100, baseTime + 100)

        // When: Apply write (under max tombstones, no TTL)
        val result = noTtlResolver.applyLocalWrite(
            currentValue = currentMap,
            currentNode = existingNode,
            currentVersion = Version(1L, baseTime, baseTime),
            newValue = newMap,
            newVersion = newVersion,
            context = context,
        )

        // Then: Old tombstone should remain (early exit optimization)
        val entries = result.node?.long_map
        assertNotNull(entries)
        assertTrue(entries.containsKey(1), "Old tombstone should remain when under limit")
        assertTrue(entries.containsKey(3), "New tombstone should exist")
    }

    @Test
    fun `TTL cleanup happens before count-based cleanup`() {
        // Given: Many tombstones, some outside TTL window
        val baseTime = 10000L
        val existingNode = VersionNode(
            version = Version(1L, baseTime, baseTime),
            long_map = mutableMapOf(
                // 3 old tombstones (outside TTL)
                1L to VersionNode(version = Version(1L, baseTime - 5000, baseTime - 5000)),
                2L to VersionNode(version = Version(1L, baseTime - 4000, baseTime - 4000)),
                3L to VersionNode(version = Version(1L, baseTime - 3000, baseTime - 3000)),
                // 3 recent tombstones (inside TTL)
                4L to VersionNode(version = Version(1L, baseTime - 1000, baseTime - 1000)),
                5L to VersionNode(version = Version(1L, baseTime - 500, baseTime - 500)),
                6L to VersionNode(version = Version(1L, baseTime, baseTime)),
                // Live data
                7L to VersionNode(version = Version(1L, baseTime, baseTime))
            )
        )

        val currentMap = mapOf(7L to "value7", 8L to "value8")
        val newMap = mapOf(7L to "value7") // Delete key 8 - creates tombstone
        val newVersion = Version(1L, baseTime + 100, baseTime + 100)

        // When: Apply write
        val result = resolver.applyLocalWrite(
            currentValue = currentMap,
            currentNode = existingNode,
            currentVersion = Version(1L, baseTime, baseTime),
            newValue = newMap,
            newVersion = newVersion,
            context = context,
        )

        // Then: TTL cleanup removes old tombstones first, then count-based cleanup if needed
        val entries = result.node?.long_map
        assertNotNull(entries)

        // Old tombstones (outside TTL window) should be removed
        assertTrue(!entries.containsKey(1), "Old tombstone 1 removed by TTL")
        assertTrue(!entries.containsKey(2), "Old tombstone 2 removed by TTL")
        assertTrue(!entries.containsKey(3), "Old tombstone 3 removed by TTL")

        // Recent tombstones should remain (within TTL, and under max after TTL cleanup)
        assertTrue(entries.containsKey(4), "Recent tombstone 4 should remain")
        assertTrue(entries.containsKey(8), "New tombstone should exist")
    }
}
