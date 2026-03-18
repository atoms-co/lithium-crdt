package co.atoms.lithium.crdt.protoc

import co.atoms.lithium.crdt.test.NestedMessageWithId
import co.atoms.lithium.crdt.test.TestMessage
import co.atoms.lithium.crdt.data.Version
import co.atoms.lithium.crdt.data.VersionNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Integration tests for tombstone cleanup using TestMessage fields with configured merge options.
 *
 * These tests validate that local write resolution correctly:
 * - Cleans up tombstones outside TTL windows
 * - Removes oldest tombstones when exceeding max count (FIFO)
 * - Updates live data versions to stay within TTL window
 */
class ProtoMapFieldTombstoneCleanupTest {
    private val provider = CrdtMessageResolverProvider()
    private val resolver = provider.getOrCreateResolverFor(TestMessage.getDefaultInstance())

    @Test
    fun `nestedListWithIdValue - cleanup removes tombstones outside TTL window`() {
        // Given: nestedListWithIdValue has maxTombstones=10, ttl=500
        val baseTime = 10000L
        val ttl = 500L

        // Create existing node with old tombstone (outside TTL) and recent tombstone (inside TTL)
        val oldTombstone = Version.newBuilder()
            .setActorId(1L)
            .setActorVersion(1L)
            .setTimestamp(baseTime - 1000)
            .build()
        val recentTombstone = Version.newBuilder()
            .setActorId(1L)
            .setActorVersion(2L)
            .setTimestamp(baseTime - 200)
            .build()

        // Create version node with tombstones in nestedListWithIdValue field (tag 23)
        val existingNode = VersionNode.newBuilder()
            .setVersion(
                Version.newBuilder()
                    .setActorId(1L)
                    .setActorVersion(3L)
                    .setTimestamp(baseTime)
            )
            .setStruct(
                VersionNode.Struct.newBuilder().putFields(
                    23, // nestedListWithIdValue tag
                    VersionNode.newBuilder()
                        .setVersion(
                            Version.newBuilder()
                                .setActorId(1L)
                                .setActorVersion(3L)
                                .setTimestamp(baseTime)
                        )
                        .setStringMap(
                            VersionNode.StringMap.newBuilder()
                                .putEntries("old", VersionNode.newBuilder().setVersion(oldTombstone).build())
                                .putEntries("recent", VersionNode.newBuilder().setVersion(recentTombstone).build()) // Inside TTL window
                                .putEntries(
                                    "live",
                                    VersionNode.newBuilder().setVersion(
                                        Version.newBuilder()
                                            .setActorId(1L)
                                            .setActorVersion(3L)
                                            .setTimestamp(baseTime)
                                    ).build()
                                )
                        )
                        .build()
                )
            )
            .build()

        val currentMessage = TestMessage.newBuilder()
            .addNestedListWithIdValue(
                NestedMessageWithId.newBuilder()
                    .setId("live")
                    .setStringValue("value")
                    .setIntValue(1)
            )
            .build()

        // When: Apply write that creates a new tombstone (delete all)
        val newMessage = TestMessage.newBuilder().build() // Empty - deletes all nested list items

        val delta = resolver.applyLocalWrite(
            currentValue = currentMessage,
            currentNode = existingNode,
            currentActors = null,
            newValue = newMessage,
            timestamp = baseTime + 100
        )
        val result = delta.mergeResult

        // Then: Old tombstone should be removed (outside TTL window)
        assertTrue(result.resolution, "Should resolve when creating tombstone")
        val fieldNode = result.node?.struct?.fieldsMap?.get(23) // nestedListWithIdValue field
        assertNotNull(fieldNode, "Field node should exist")
        val entries = fieldNode?.stringMap?.entriesMap
        assertNotNull(entries)
        assertFalse(entries?.containsKey("old") == true, "Old tombstone should be removed (outside TTL)")
        assertTrue(entries?.containsKey("recent") == true, "Recent tombstone should remain (inside TTL)")
        assertTrue(entries?.containsKey("live") == true, "New tombstone should exist")
    }

    @Test
    fun `nestedListWithIdValue - cleanup removes oldest tombstones when exceeding max count`() {
        // Given: nestedListWithIdValue has maxTombstones=10
        val baseTime = 10000L

        // Create 10 existing tombstones in the field's version node
        // Use timestamps within TTL window to test count-based cleanup (not TTL-based)
        val fieldNodeBuilder = VersionNode.newBuilder()
            .setVersion(
                Version.newBuilder()
                    .setActorId(1L)
                    .setActorVersion(10L)
                    .setTimestamp(baseTime)
            )

        val stringMapBuilder = VersionNode.StringMap.newBuilder()
        for (i in 1..10) {
            stringMapBuilder.putEntries(
                "tomb$i",
                VersionNode.newBuilder()
                    .setVersion(
                        Version.newBuilder()
                            .setActorId(1L)
                            .setActorVersion(i.toLong())
                            .setTimestamp(baseTime + 700 + i * 10) // All within [baseTime + 700, baseTime + 1200]
                    )
                    .build()
            )
        }

        // Add one live entry
        stringMapBuilder.putEntries(
            "live",
            VersionNode.newBuilder()
                .setVersion(
                    Version.newBuilder()
                        .setActorId(1L)
                        .setActorVersion(11L)
                        .setTimestamp(baseTime + 1100)
                )
                .build()
        )

        fieldNodeBuilder.setStringMap(stringMapBuilder)

        val existingNode = VersionNode.newBuilder()
            .setVersion(
                Version.newBuilder()
                    .setActorId(1L)
                    .setActorVersion(11L)
                    .setTimestamp(baseTime + 1100)
            )
            .setStruct(
                VersionNode.Struct.newBuilder().putFields(23, fieldNodeBuilder.build()) // nestedListWithIdValue field
            )
            .build()

        val currentMessage = TestMessage.newBuilder()
            .addNestedListWithIdValue(
                NestedMessageWithId.newBuilder()
                    .setId("live")
                    .setStringValue("value")
                    .setIntValue(1)
            )
            .build()

        // When: Delete the live entry to create 11th tombstone
        val newMessage = TestMessage.newBuilder().build() // Empty - deletes all

        val delta = resolver.applyLocalWrite(
            currentValue = currentMessage,
            currentNode = existingNode,
            currentActors = null,
            newValue = newMessage,
            timestamp = baseTime + 1200
        )
        val result = delta.mergeResult

        // Then: Should keep max 10 tombstones, removing the oldest
        assertTrue(result.resolution)
        val fieldNode = result.node?.struct?.fieldsMap?.get(23)
        assertNotNull(fieldNode, "Field node should exist")
        val entries = fieldNode?.stringMap?.entriesMap
        assertNotNull(entries)
        assertEquals(10, entries?.size ?: 0, "Should keep max 10 tombstones")
        assertFalse(entries?.containsKey("tomb1") == true, "Oldest tombstone should be removed (FIFO)")
        assertTrue(entries?.containsKey("tomb2") == true, "Second oldest should remain")
        assertTrue(entries?.containsKey("live") == true, "New tombstone should exist")
    }

    @Test
    fun `nestedListWithIdValue - cleanup updates live data versions to stay in TTL window`() {
        // Given: nestedListWithIdValue has ttl=500
        val baseTime = 10000L
        val ttl = 500L

        // Create existing node with live data that has old version (outside new TTL window)
        val oldDataVersion = Version.newBuilder()
            .setActorId(1L)
            .setActorVersion(1L)
            .setTimestamp(baseTime - 1000)
            .build()

        // Create version node with field data in nestedListWithIdValue field (tag 23)
        val existingNode = VersionNode.newBuilder()
            .setVersion(
                Version.newBuilder()
                    .setActorId(1L)
                    .setActorVersion(2L)
                    .setTimestamp(baseTime)
            )
            .setStruct(
                VersionNode.Struct.newBuilder().putFields(
                    23, // nestedListWithIdValue tag
                    VersionNode.newBuilder()
                        .setVersion(
                            Version.newBuilder()
                                .setActorId(1L)
                                .setActorVersion(2L)
                                .setTimestamp(baseTime)
                        )
                        .setStringMap(
                            VersionNode.StringMap.newBuilder()
                                .putEntries("old_live", VersionNode.newBuilder().setVersion(oldDataVersion).build())
                                .putEntries(
                                    "recent_live",
                                    VersionNode.newBuilder()
                                        .setVersion(
                                            Version.newBuilder()
                                                .setActorId(1L)
                                                .setActorVersion(2L)
                                                .setTimestamp(baseTime)
                                        )
                                        .build()
                                )
                        )
                        .build()
                )
            )
            .build()

        val currentMessage = TestMessage.newBuilder()
            .addNestedListWithIdValue(
                NestedMessageWithId.newBuilder()
                    .setId("old_live")
                    .setStringValue("old")
                    .setIntValue(1)
            )
            .addNestedListWithIdValue(
                NestedMessageWithId.newBuilder()
                    .setId("recent_live")
                    .setStringValue("recent")
                    .setIntValue(2)
            )
            .addNestedListWithIdValue(
                NestedMessageWithId.newBuilder()
                    .setId("to_delete")
                    .setStringValue("delete")
                    .setIntValue(3)
            )
            .build()

        // When: Delete an entry to trigger cleanup
        val newMessage = TestMessage.newBuilder()
            .addNestedListWithIdValue(
                NestedMessageWithId.newBuilder()
                    .setId("old_live")
                    .setStringValue("old")
                    .setIntValue(1)
            )
            .addNestedListWithIdValue(
                NestedMessageWithId.newBuilder()
                    .setId("recent_live")
                    .setStringValue("recent")
                    .setIntValue(2)
            )
            .build()

        val delta = resolver.applyLocalWrite(
            currentValue = currentMessage,
            currentNode = existingNode,
            currentActors = null,
            newValue = newMessage,
            timestamp = baseTime + 100
        )
        val result = delta.mergeResult

        // Then: Old live data version should be updated to window start
        assertTrue(result.resolution)
        val fieldNode = result.node?.struct?.fieldsMap?.get(23)
        assertNotNull(fieldNode, "Field node should exist")
        val entries = fieldNode?.stringMap?.entriesMap
        assertNotNull(entries)

        val oldLiveVersion = entries?.get("old_live")?.version
        assertNotNull(oldLiveVersion, "Old live data should exist")

        // TTL window: [(baseTime + 100) - 500, (baseTime + 100)]
        val expectedWindowStart = (baseTime + 100) - ttl
        assertEquals(expectedWindowStart, oldLiveVersion?.timestamp ?: 0, "Should update to window start timestamp")
    }

    @Test
    fun `primitiveMapValue - uses default tombstone options`() {
        // Given: primitiveMapValue has default options (maxTombstones=1024, ttl=null)
        val baseTime = 10000L

        // Create a very old tombstone (no TTL, so it should remain)
        val veryOldTombstone = Version.newBuilder()
            .setActorId(1L)
            .setActorVersion(1L)
            .setTimestamp(baseTime - 100000)
            .build()

        // Create version node with field data in primitiveMapValue field (tag 19)
        val existingNode = VersionNode.newBuilder()
            .setVersion(
                Version.newBuilder()
                    .setActorId(1L)
                    .setActorVersion(2L)
                    .setTimestamp(baseTime)
            )
            .setStruct(
                VersionNode.Struct.newBuilder().putFields(
                    19, // primitiveMapValue tag
                    VersionNode.newBuilder()
                        .setVersion(
                            Version.newBuilder()
                                .setActorId(1L)
                                .setActorVersion(2L)
                                .setTimestamp(baseTime)
                        )
                        .setStringMap(
                            VersionNode.StringMap.newBuilder()
                                .putEntries("very_old", VersionNode.newBuilder().setVersion(veryOldTombstone).build())
                                .putEntries(
                                    "live",
                                    VersionNode.newBuilder()
                                        .setVersion(
                                            Version.newBuilder()
                                                .setActorId(1L)
                                                .setActorVersion(2L)
                                                .setTimestamp(baseTime)
                                        )
                                        .build()
                                )
                        )
                        .build()
                )
            )
            .build()

        val currentMessage = TestMessage.newBuilder()
            .putPrimitiveMapValue("live", 42)
            .putPrimitiveMapValue("to_delete", 99)
            .build()

        // When: Delete an entry
        val newMessage = TestMessage.newBuilder()
            .putPrimitiveMapValue("live", 42)
            .build()

        val delta = resolver.applyLocalWrite(
            currentValue = currentMessage,
            currentNode = existingNode,
            currentActors = null,
            newValue = newMessage,
            timestamp = baseTime + 100
        )
        val result = delta.mergeResult

        // Then: Very old tombstone should remain (no TTL configured)
        assertTrue(result.resolution)
        val fieldNode = result.node?.struct?.fieldsMap?.get(19)
        assertNotNull(fieldNode, "Field node should exist")
        val entries = fieldNode?.stringMap?.entriesMap
        assertNotNull(entries)
        assertTrue(entries?.containsKey("very_old") == true, "Very old tombstone should remain (no TTL)")
        assertTrue(entries?.containsKey("to_delete") == true, "New tombstone should exist")
    }
}
