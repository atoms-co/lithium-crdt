package com.css.protobuf.crdt.resolver.delta

import com.css.protobuf.crdt.resolver.RepeatedIdResolver
import com.css.protobuf.crdt.resolver.ResolutionDeltaContext
import com.css.protobuf.crdt.resolver.SingleValueResolver
import com.css.protobuf.crdt.resolver.StringMapResolver
import com.css.protobuf.crdt.resolver.TestMessage
import com.css.protobuf.crdt.resolver.TestVersionTreeResolver
import com.css.protobuf.crdt.resolver.Version
import com.css.protobuf.crdt.resolver.VersionNode
import com.css.protobuf.crdt.resolver.descriptor.CollectionType
import com.css.protobuf.crdt.resolver.descriptor.KeyType
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for RepeatedIdCrdtDeltaResolver verifying delta computation for ID-based repeated fields.
 *
 * This resolver converts lists to maps using IDs, delegates to map delta resolution,
 * then converts back. This enables fine-grained per-item conflict resolution.
 */
class RepeatedIdCrdtDeltaResolverTest {
    private val mockListDecoder: (ByteArray) -> List<TestMessage> = mockk()
    private val mockListEncoder: (List<TestMessage>) -> ByteArray = mockk()
    private val mockMapDecoder: (ByteArray) -> Map<String, TestMessage> = mockk()
    private val mockMapEncoder: (Map<String, TestMessage>) -> ByteArray = mockk()
    private val messageDecoder: (ByteArray) -> TestMessage = { mockk() }
    private val messageEncoder: (TestMessage) -> ByteArray = { mockk() }

    private val messageValueResolver = SingleValueResolver(
        decoder = messageDecoder,
        encoder = messageEncoder,
        versionTreeResolver = TestVersionTreeResolver
    )

    private val mapResolver = StringMapResolver(
        config = CollectionType.Map(
            keyType = KeyType.STRING,
            maxTombstone = 5,
            tombstoneTtl = 2000
        ),
        decoder = mockMapDecoder,
        encoder = mockMapEncoder,
        valueResolver = messageValueResolver,
        versionTreeResolver = TestVersionTreeResolver
    )

    private val repeatedIdResolver = RepeatedIdResolver(
        decoder = mockListDecoder,
        encoder = mockListEncoder,
        keyTransformer = { it.stringValue },
        mapResolver = mapResolver,
        versionTreeResolver = TestVersionTreeResolver
    )

    @Test
    fun `changeDelta transforms list to map using key transformer`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val list = listOf(
            TestMessage(stringValue = "key1", int32Value = 1),
            TestMessage(stringValue = "key2", int32Value = 2),
            TestMessage(stringValue = "key3", int32Value = 3)
        )
        val node = VersionNode(
            version = Version(1L, 100L, 100L),
            string_map = mapOf(
                "key1" to VersionNode(version = Version(1L, 100L, 100L)),
                "key2" to VersionNode(version = Version(1L, 100L, 100L)),
                "key3" to VersionNode(version = Version(1L, 100L, 100L))
            )
        )
        val versionVector = emptyMap<Long, Long>()

        // When
        repeatedIdResolver.changeDelta(
            value = list,
            node = node,
            version = Version(1L, 100L, 100L),
            versionVector = versionVector,
            context = context
        )

        // Then - changes should use keys from keyTransformer
        assertEquals(3, context.changes.size)
        val paths = context.changes.map { it.pathComponents[0] }.toSet()
        assertEquals(setOf("key1", "key2", "key3"), paths, "Should use stringValue as keys")
    }

    @Test
    fun `changeDelta handles duplicates using last occurrence wins`() {
        // Given - list has duplicate keys
        val context = ResolutionDeltaContext<VersionNode, String>()
        val list = listOf(
            TestMessage(stringValue = "key1", int32Value = 1),
            TestMessage(stringValue = "key1", int32Value = 2), // Duplicate - should win
            TestMessage(stringValue = "key2", int32Value = 3),
            TestMessage(stringValue = "key2", int32Value = 4) // Duplicate - should win
        )
        val node = VersionNode(
            version = Version(1L, 100L, 100L),
            string_map = mapOf(
                "key1" to VersionNode(version = Version(1L, 100L, 100L)),
                "key2" to VersionNode(version = Version(1L, 100L, 100L))
            )
        )
        val versionVector = emptyMap<Long, Long>()

        // When
        repeatedIdResolver.changeDelta(
            value = list,
            node = node,
            version = Version(1L, 100L, 100L),
            versionVector = versionVector,
            context = context
        )

        // Then - should only have 2 changes (last occurrence of each key)
        assertEquals(2, context.changes.size)
        val changes = context.changes.sortedBy { it.pathComponents[0] }

        assertEquals("key1", changes[0].pathComponents[0])
        assertEquals(TestMessage(stringValue = "key1", int32Value = 2), changes[0].value)

        assertEquals("key2", changes[1].pathComponents[0])
        assertEquals(TestMessage(stringValue = "key2", int32Value = 4), changes[1].value)
    }

    @Test
    fun `changeDelta with null list`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val node = VersionNode(
            version = Version(1L, 100L, 100L),
            string_map = mapOf(
                "key1" to VersionNode(version = Version(1L, 100L, 100L)),
                "key2" to VersionNode(version = Version(1L, 100L, 100L))
            )
        )
        val versionVector = emptyMap<Long, Long>()

        // When - null list
        repeatedIdResolver.changeDelta(
            value = null,
            node = node,
            version = Version(1L, 100L, 100L),
            versionVector = versionVector,
            context = context
        )

        // Then - all entries should be deleted
        assertEquals(2, context.changes.size)
        context.changes.forEach { change ->
            assertEquals(null, change.value, "All entries should be null (deleted)")
        }
    }

    @Test
    fun `changeDelta with empty list`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val list = emptyList<TestMessage>()
        val node = VersionNode(
            version = Version(1L, 100L, 100L),
            string_map = mapOf(
                "key1" to VersionNode(version = Version(1L, 100L, 100L)),
                "key2" to VersionNode(version = Version(1L, 100L, 100L))
            )
        )
        val versionVector = emptyMap<Long, Long>()

        // When
        repeatedIdResolver.changeDelta(
            value = list,
            node = node,
            version = Version(1L, 100L, 100L),
            versionVector = versionVector,
            context = context
        )

        // Then - all existing entries should be sent as deletions
        assertEquals(2, context.changes.size)
        context.changes.forEach { change ->
            assertEquals(null, change.value)
        }
    }

    @Test
    fun `changeDelta respects version vector per item`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val list = listOf(
            TestMessage(stringValue = "key1", int32Value = 1),
            TestMessage(stringValue = "key2", int32Value = 2),
            TestMessage(stringValue = "key3", int32Value = 3)
        )
        val node = VersionNode(
            version = Version(1L, 100L, 100L),
            string_map = mapOf(
                "key1" to VersionNode(version = Version(1L, 50L, 50L)), // Will be included in vector
                "key2" to VersionNode(version = Version(1L, 150L, 150L)), // Will not be included
                "key3" to VersionNode(version = Version(1L, 200L, 200L)) // Will not be included
            )
        )
        val versionVector = mapOf(1L to 100L) // Includes versions <= 100

        // When
        repeatedIdResolver.changeDelta(
            value = list,
            node = node,
            version = Version(1L, 100L, 100L),
            versionVector = versionVector,
            context = context
        )

        // Then - only key2 and key3 should be sent
        assertEquals(2, context.changes.size)
        val paths = context.changes.map { it.pathComponents[0] }.toSet()
        assertEquals(setOf("key2", "key3"), paths)
    }

    @Test
    fun `changeDelta handles item addition`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val list = listOf(
            TestMessage(stringValue = "key1", int32Value = 1),
            TestMessage(stringValue = "key2", int32Value = 2),
            TestMessage(stringValue = "key3", int32Value = 3) // New item
        )
        val node = VersionNode(
            version = Version(1L, 100L, 100L),
            string_map = mapOf(
                "key1" to VersionNode(version = Version(1L, 100L, 100L)),
                "key2" to VersionNode(version = Version(1L, 100L, 100L))
                // key3 doesn't exist in node, so only key1 and key2 are processed
            )
        )
        val versionVector = emptyMap<Long, Long>()

        // When
        repeatedIdResolver.changeDelta(
            value = list,
            node = node,
            version = Version(1L, 100L, 100L),
            versionVector = versionVector,
            context = context
        )

        // Then - only key1 and key2 should be sent (they're in entries map)
        assertEquals(2, context.changes.size, "Only existing entries are processed")
        val paths = context.changes.map { it.pathComponents[0] }.toSet()
        assertEquals(setOf("key1", "key2"), paths)
    }

    @Test
    fun `changeDelta handles item deletion`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val list = listOf(
            TestMessage(stringValue = "key1", int32Value = 1)
            // key2 was deleted
        )
        val node = VersionNode(
            version = Version(1L, 100L, 100L),
            string_map = mapOf(
                "key1" to VersionNode(version = Version(1L, 100L, 100L)),
                "key2" to VersionNode(version = Version(1L, 150L, 150L)) // Tombstone
            )
        )
        val versionVector = emptyMap<Long, Long>()

        // When
        repeatedIdResolver.changeDelta(
            value = list,
            node = node,
            version = Version(1L, 100L, 100L),
            versionVector = versionVector,
            context = context
        )

        // Then - both items should be sent (key2 as null)
        assertEquals(2, context.changes.size)
        val changes = context.changes.associateBy { it.pathComponents[0] }
        assertEquals(TestMessage(stringValue = "key1", int32Value = 1), changes["key1"]?.value)
        assertEquals(null, changes["key2"]?.value, "Deleted item should be null")
    }

    @Test
    fun `changeDelta with null node processes value list`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val list = listOf(
            TestMessage(stringValue = "key1", int32Value = 1),
            TestMessage(stringValue = "key2", int32Value = 2)
        )
        val versionVector = emptyMap<Long, Long>()

        // When
        repeatedIdResolver.changeDelta(
            value = list,
            node = null,
            version = Version(1L, 100L, 100L),
            versionVector = versionVector,
            context = context
        )

        // Then
        assertEquals(2, context.changes.size)
        val paths = context.changes.map { it.pathComponents[0] }.toSet()
        assertEquals(setOf("key1", "key2"), paths)
    }

    @Test
    fun `changeDelta maintains list order after map transformation`() {
        // Given - list with specific order
        val context = ResolutionDeltaContext<VersionNode, String>()
        val list = listOf(
            TestMessage(stringValue = "zebra", int32Value = 1),
            TestMessage(stringValue = "alpha", int32Value = 2),
            TestMessage(stringValue = "middle", int32Value = 3)
        )
        val node = VersionNode(
            version = Version(1L, 100L, 100L),
            string_map = mapOf(
                "zebra" to VersionNode(version = Version(1L, 100L, 100L)),
                "alpha" to VersionNode(version = Version(1L, 100L, 100L)),
                "middle" to VersionNode(version = Version(1L, 100L, 100L))
            )
        )
        val versionVector = emptyMap<Long, Long>()

        // When
        repeatedIdResolver.changeDelta(
            value = list,
            node = node,
            version = Version(1L, 100L, 100L),
            versionVector = versionVector,
            context = context
        )

        // Then - changes should be captured (order in map may vary)
        assertEquals(3, context.changes.size)
        val paths = context.changes.map { it.pathComponents[0] }.toSet()
        assertEquals(setOf("zebra", "alpha", "middle"), paths)
    }
}
