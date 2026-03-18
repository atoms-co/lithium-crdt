package co.atoms.lithium.crdt.wire

import co.atoms.lithium.crdt.test.NestedMessageWithId
import co.atoms.lithium.crdt.test.TestMessage
import co.atoms.lithium.crdt.data.Version
import co.atoms.lithium.crdt.data.VersionNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for tombstone cleanup using TestMessage fields with configured merge options.
 *
 * These tests validate that local write resolution correctly:
 * - Cleans up tombstones outside TTL windows
 * - Removes oldest tombstones when exceeding max count (FIFO)
 * - Updates live data versions to stay within TTL window
 */
class WireMapFieldTombstoneCleanupTest {
    private val provider = WireCrdtResolverProvider()
    private val resolver = provider.messageResolver(adapter = TestMessage.ADAPTER)

    @Test
    fun `nestedListWithIdValue - cleanup removes tombstones outside TTL window`() {
        // Given: nestedListWithIdValue has maxTombstones=10, ttl=500
        val baseTime = 10000L

        // Create existing node with old tombstone (outside TTL) and recent tombstone (inside TTL)
        val oldTombstone = Version(timestamp = baseTime - 1000, actor_id = 1L, actor_version = 1L)
        val recentTombstone = Version(timestamp = baseTime - 200, actor_id = 1L, actor_version = 2L)

        // Create version node with tombstones in nestedListWithIdValue field (tag 23)
        val existingNode =
            VersionNode(
                version = Version(timestamp = baseTime, actor_id = 1L, actor_version = 3L),
                struct =
                VersionNode.Struct(
                    fields =
                    mapOf(
                        23 to
                            VersionNode(
                                version = Version(timestamp = baseTime, actor_id = 1L, actor_version = 3L),
                                string_map =
                                VersionNode.StringMap(
                                    entries =
                                    mapOf(
                                        "old" to
                                            VersionNode(version = oldTombstone), // Outside TTL window
                                        "recent" to
                                            VersionNode(version = recentTombstone), // Inside TTL window
                                        "live" to
                                            VersionNode(
                                                version =
                                                Version(
                                                    timestamp = baseTime,
                                                    actor_id = 1L,
                                                    actor_version = 3L,
                                                )
                                            ),
                                    )
                                ),
                            )
                    )
                ),
            )

        val currentMessage =
            TestMessage(
                nestedListWithIdValue = listOf(NestedMessageWithId(id = "live", stringValue = "value", intValue = 1))
            )

        // When: Apply write that creates a new tombstone (delete all)
        val newMessage = TestMessage() // Empty - deletes all nested list items

        val delta =
            resolver.applyLocalWrite(
                currentValue = currentMessage,
                currentNode = existingNode,
                currentActors = null,
                newValue = newMessage,
                timestamp = baseTime + 100,
            )
        val result = delta.mergeResult

        // Then: Old tombstone should be removed (outside TTL window)
        assertTrue(result.resolution, "Should resolve when creating tombstone")
        val fieldNode = result.node?.struct?.fields?.get(23) // nestedListWithIdValue field
        assertNotNull(fieldNode, "Field node should exist")
        val entries = fieldNode.string_map?.entries
        assertNotNull(entries)
        assertFalse(entries.containsKey("old"), "Old tombstone should be removed (outside TTL)")
        assertTrue(entries.containsKey("recent"), "Recent tombstone should remain (inside TTL)")
        assertTrue(entries.containsKey("live"), "New tombstone should exist")
    }

    @Test
    fun `nestedListWithIdValue - cleanup removes oldest tombstones when exceeding max count`() {
        // Given: nestedListWithIdValue has maxTombstones=10, ttl=500
        val baseTime = 10000L

        // Create 10 existing tombstones in the field's version node
        // Use timestamps within TTL window to test count-based cleanup (not TTL-based)
        val fieldNodeEntries =
            (1..10)
                .associate { i ->
                    "tomb$i" to
                        VersionNode(
                            version =
                            Version(
                                timestamp = baseTime + 700 + i * 10, // All within [baseTime + 700, baseTime + 1200]
                                actor_id = 1L,
                                actor_version = i.toLong(),
                            )
                        )
                }
                .toMutableMap()
                .apply {
                    // Add one live entry
                    put(
                        "live",
                        VersionNode(version = Version(timestamp = baseTime + 1100, actor_id = 1L, actor_version = 11L)),
                    )
                }

        val existingNode =
            VersionNode(
                version = Version(timestamp = baseTime + 1100, actor_id = 1L, actor_version = 11L),
                struct =
                VersionNode.Struct(
                    fields =
                    mapOf(
                        23 to
                            VersionNode( // nestedListWithIdValue field
                                version =
                                Version(timestamp = baseTime + 1100, actor_id = 1L, actor_version = 11L),
                                string_map = VersionNode.StringMap(entries = fieldNodeEntries),
                            )
                    )
                ),
            )

        val currentMessage =
            TestMessage(
                nestedListWithIdValue = listOf(NestedMessageWithId(id = "live", stringValue = "value", intValue = 1))
            )

        // When: Delete the live entry to create 11th tombstone
        val newMessage = TestMessage() // Empty - deletes all

        val delta =
            resolver.applyLocalWrite(
                currentValue = currentMessage,
                currentNode = existingNode,
                currentActors = null,
                newValue = newMessage,
                timestamp = baseTime + 1200,
            )
        val result = delta.mergeResult

        // Then: Should keep max 10 tombstones, removing the oldest
        assertTrue(result.resolution)
        val fieldNode = result.node?.struct?.fields?.get(23)
        assertNotNull(fieldNode, "Field node should exist")
        val entries = fieldNode.string_map?.entries
        assertNotNull(entries)
        assertEquals(10, entries.size, "Should keep max 10 tombstones")
        assertFalse(entries.containsKey("tomb1"), "Oldest tombstone should be removed (FIFO)")
        assertTrue(entries.containsKey("tomb2"), "Second oldest should remain")
        assertTrue(entries.containsKey("live"), "New tombstone should exist")
    }

    @Test
    fun `nestedListWithIdValue - cleanup updates live data versions to stay in TTL window`() {
        // Given: nestedListWithIdValue has ttl=500
        val baseTime = 10000L
        val ttl = 500L

        // Create existing node with live data that has old version (outside new TTL window)
        val oldDataVersion = Version(timestamp = baseTime - 1000, actor_id = 1L, actor_version = 1L)

        val existingNode =
            VersionNode(
                version = Version(timestamp = baseTime, actor_id = 1L, actor_version = 2L),
                struct =
                VersionNode.Struct(
                    fields =
                    mapOf(
                        23 to
                            VersionNode(
                                version = Version(timestamp = baseTime, actor_id = 1L, actor_version = 2L),
                                string_map =
                                VersionNode.StringMap(
                                    entries =
                                    mapOf(
                                        "old_live" to VersionNode(version = oldDataVersion),
                                        "recent_live" to
                                            VersionNode(
                                                version =
                                                Version(
                                                    timestamp = baseTime,
                                                    actor_id = 1L,
                                                    actor_version = 2L,
                                                )
                                            ),
                                    )
                                ),
                            )
                    )
                ),
            )

        val currentMessage =
            TestMessage(
                nestedListWithIdValue =
                listOf(
                    NestedMessageWithId(id = "old_live", stringValue = "old", intValue = 1),
                    NestedMessageWithId(id = "recent_live", stringValue = "recent", intValue = 2),
                    NestedMessageWithId(id = "to_delete", stringValue = "delete", intValue = 3),
                )
            )

        // When: Delete an entry to trigger cleanup
        val newMessage =
            TestMessage(
                nestedListWithIdValue =
                listOf(
                    NestedMessageWithId(id = "old_live", stringValue = "old", intValue = 1),
                    NestedMessageWithId(id = "recent_live", stringValue = "recent", intValue = 2),
                )
            )

        val delta =
            resolver.applyLocalWrite(
                currentValue = currentMessage,
                currentNode = existingNode,
                currentActors = null,
                newValue = newMessage,
                timestamp = baseTime + 100,
            )
        val result = delta.mergeResult

        // Then: Old live data version should be updated to window start
        assertTrue(result.resolution)
        val fieldNode = result.node?.struct?.fields?.get(23)
        assertNotNull(fieldNode)
        val entries = fieldNode.string_map?.entries
        assertNotNull(entries)

        val oldLiveVersion = entries["old_live"]?.version
        assertNotNull(oldLiveVersion, "Old live data should exist")

        // TTL window: [newVersion.timestamp - 500, newVersion.timestamp]
        val newTimestamp = baseTime + 100
        val expectedWindowStart = newTimestamp - ttl
        assertEquals(expectedWindowStart, oldLiveVersion.timestamp, "Should update to window start timestamp")
        assertEquals(delta.actors.local_actor, oldLiveVersion.actor_id, "Should use new write's actor ID")
    }

    @Test
    fun `primitiveMapValue - uses default tombstone options`() {
        // Given: primitiveMapValue has default options (maxTombstones=1024, ttl=null)
        val baseTime = 10000L

        // Create a very old tombstone (no TTL, so it should remain)
        val veryOldTombstone = Version(timestamp = baseTime - 100000, actor_id = 1L, actor_version = 1L)

        val existingNode =
            VersionNode(
                version = Version(timestamp = baseTime, actor_id = 1L, actor_version = 2L),
                struct =
                VersionNode.Struct(
                    fields =
                    mapOf(
                        19 to
                            VersionNode( // primitiveMapValue field
                                version = Version(timestamp = baseTime, actor_id = 1L, actor_version = 2L),
                                string_map =
                                VersionNode.StringMap(
                                    entries =
                                    mapOf(
                                        "very_old" to VersionNode(version = veryOldTombstone),
                                        "live" to
                                            VersionNode(
                                                version =
                                                Version(
                                                    timestamp = baseTime,
                                                    actor_id = 1L,
                                                    actor_version = 2L,
                                                )
                                            ),
                                    )
                                ),
                            )
                    )
                ),
            )

        val currentMessage = TestMessage(primitiveMapValue = mapOf("live" to 42, "to_delete" to 99))

        // When: Delete an entry
        val newMessage = TestMessage(primitiveMapValue = mapOf("live" to 42))

        val delta =
            resolver.applyLocalWrite(
                currentValue = currentMessage,
                currentNode = existingNode,
                currentActors = null,
                newValue = newMessage,
                timestamp = baseTime + 100,
            )
        val result = delta.mergeResult

        // Then: Very old tombstone should remain (no TTL configured)
        assertTrue(result.resolution)
        val fieldNode = result.node?.struct?.fields?.get(19)
        assertNotNull(fieldNode, "Field node should exist")
        val entries = fieldNode.string_map?.entries
        assertNotNull(entries)
        assertTrue(entries.containsKey("very_old"), "Very old tombstone should remain (no TTL)")
        assertTrue(entries.containsKey("to_delete"), "New tombstone should exist")
    }
}
