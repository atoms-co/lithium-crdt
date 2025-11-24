package com.css.internal.shared.storage.crdt.resolver.delta

import com.css.internal.shared.storage.crdt.resolver.ResolutionDeltaContext
import com.css.internal.shared.storage.crdt.resolver.SingleValueResolver
import com.css.internal.shared.storage.crdt.resolver.StringMapResolver
import com.css.internal.shared.storage.crdt.resolver.TestVersionTreeResolver
import com.css.internal.shared.storage.crdt.resolver.Version
import com.css.internal.shared.storage.crdt.resolver.VersionNode
import com.css.internal.shared.storage.crdt.resolver.descriptor.CollectionType
import com.css.internal.shared.storage.crdt.resolver.descriptor.KeyType
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for StringMapCrdtDeltaResolver verifying delta computation for string-keyed maps.
 *
 * Map delta resolution processes each map entry independently, comparing entry versions
 * against the version vector to determine what changes need to be sent.
 */
class StringMapCrdtDeltaResolverTest {
    private val decoder: (ByteArray) -> String = { it.decodeToString() }
    private val encoder: (String) -> ByteArray = { it.toByteArray() }
    private val mockMapDecoder: (ByteArray) -> Map<String, String> = mockk()
    private val mockMapEncoder: (Map<String, String>) -> ByteArray = mockk()
    private val valueResolver = SingleValueResolver(
        decoder = decoder,
        encoder = encoder,
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
        valueResolver = valueResolver,
        versionTreeResolver = TestVersionTreeResolver
    )

    @Test
    fun `changeDelta adds all entries when version vector is empty`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val map = mapOf("key1" to "value1", "key2" to "value2")
        val node = VersionNode(
            version = Version(1L, 100L, 100L),
            string_map = mapOf(
                "key1" to VersionNode(version = Version(1L, 100L, 100L)),
                "key2" to VersionNode(version = Version(1L, 100L, 100L))
            )
        )
        val versionVector = emptyMap<Long, Long>()

        // When
        mapResolver.changeDelta(
            value = map,
            node = node,
            version = Version(1L, 100L, 100L),
            versionVector = versionVector,
            context = context
        )

        // Then
        assertEquals(2, context.changes.size, "Should add all entries when vector is empty")

        val changePaths = context.changes.map { it.pathComponents to it.value }.sortedBy { it.first[0] }
        assertEquals(listOf("key1"), changePaths[0].first)
        assertEquals("value1", changePaths[0].second)
        assertEquals(listOf("key2"), changePaths[1].first)
        assertEquals("value2", changePaths[1].second)
    }

    @Test
    fun `changeDelta skips entries already in version vector`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val map = mapOf("key1" to "value1", "key2" to "value2")
        val node = VersionNode(
            version = Version(1L, 100L, 100L),
            string_map = mapOf(
                "key1" to VersionNode(version = Version(1L, 50L, 50L)), // Will be included in vector
                "key2" to VersionNode(version = Version(1L, 150L, 150L)) // Will not be included
            )
        )
        val versionVector = mapOf(1L to 100L) // Includes versions <= 100

        // When
        mapResolver.changeDelta(
            value = map,
            node = node,
            version = Version(1L, 100L, 100L),
            versionVector = versionVector,
            context = context
        )

        // Then
        assertEquals(1, context.changes.size, "Should only add key2 (version 150)")
        assertEquals(listOf("key2"), context.changes[0].pathComponents)
        assertEquals("value2", context.changes[0].value)
    }

    @Test
    fun `changeDelta processes deletions (null values)`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val map = mapOf("key1" to "value1") // key2 was deleted
        val node = VersionNode(
            version = Version(1L, 100L, 100L),
            string_map = mapOf(
                "key1" to VersionNode(version = Version(1L, 100L, 100L)),
                "key2" to VersionNode(version = Version(1L, 150L, 150L)) // Tombstone
            )
        )
        val versionVector = emptyMap<Long, Long>()

        // When
        mapResolver.changeDelta(
            value = map,
            node = node,
            version = Version(1L, 100L, 100L),
            versionVector = versionVector,
            context = context
        )

        // Then
        assertEquals(2, context.changes.size)

        val changes = context.changes.associateBy { it.pathComponents[0] }
        assertEquals("value1", changes["key1"]?.value)
        assertEquals(null, changes["key2"]?.value, "Deleted entry should have null value")
    }

    @Test
    fun `changeDelta uses entry versions from node`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val map = mapOf("key1" to "value1", "key2" to "value2")
        val node = VersionNode(
            version = Version(1L, 100L, 100L),
            string_map = mapOf(
                "key1" to VersionNode(version = Version(1L, 150L, 150L)),
                "key2" to VersionNode(version = Version(1L, 200L, 200L))
            )
        )
        val versionVector = emptyMap<Long, Long>()

        // When
        mapResolver.changeDelta(
            value = map,
            node = node,
            version = Version(1L, 100L, 100L),
            versionVector = versionVector,
            context = context
        )

        // Then
        assertEquals(2, context.changes.size)
        val changes = context.changes.associateBy { it.pathComponents[0] }
        assertEquals(150L, changes["key1"]?.versionNode?.version?.actorVersion)
        assertEquals(200L, changes["key2"]?.versionNode?.version?.actorVersion)
    }

    @Test
    fun `changeDelta uses map version as fallback when no entry node`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val map = mapOf("key1" to "value1", "key2" to "value2")
        val node = VersionNode(
            version = Version(1L, 100L, 100L),
            string_map = mapOf(
                "key1" to VersionNode(version = Version(1L, 150L, 150L))
                // key2 has entry in map but no node, so only key1 will be processed
            )
        )
        val versionVector = emptyMap<Long, Long>()

        // When
        mapResolver.changeDelta(
            value = map,
            node = node,
            version = Version(1L, 100L, 100L),
            versionVector = versionVector,
            context = context
        )

        // Then - only key1 is processed (it's in the entries map)
        assertEquals(1, context.changes.size, "Only key1 should be processed")
        assertEquals(listOf("key1"), context.changes[0].pathComponents)
        assertEquals(150L, context.changes[0].versionNode.version.actorVersion, "key1 should use its entry version")
    }

    @Test
    fun `changeDelta processes entries from node even if not in value map`() {
        // Given - local has key1, but node also tracks key2 (tombstone)
        val context = ResolutionDeltaContext<VersionNode, String>()
        val map = mapOf("key1" to "value1")
        val node = VersionNode(
            version = Version(1L, 100L, 100L),
            string_map = mapOf(
                "key1" to VersionNode(version = Version(1L, 100L, 100L)),
                "key2" to VersionNode(version = Version(1L, 150L, 150L)) // Tombstone entry
            )
        )
        val versionVector = emptyMap<Long, Long>()

        // When
        mapResolver.changeDelta(
            value = map,
            node = node,
            version = Version(1L, 100L, 100L),
            versionVector = versionVector,
            context = context
        )

        // Then - both entries should be processed
        assertEquals(2, context.changes.size)
        val changes = context.changes.associateBy { it.pathComponents[0] }
        assertEquals("value1", changes["key1"]?.value)
        assertEquals(null, changes["key2"]?.value)
    }

    @Test
    fun `changeDelta handles null map value`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val node = VersionNode(
            version = Version(1L, 100L, 100L),
            string_map = mapOf(
                "key1" to VersionNode(version = Version(1L, 100L, 100L)),
                "key2" to VersionNode(version = Version(1L, 150L, 150L))
            )
        )
        val versionVector = emptyMap<Long, Long>()

        // When - null map means all entries are deleted
        mapResolver.changeDelta(
            value = null,
            node = node,
            version = Version(1L, 100L, 100L),
            versionVector = versionVector,
            context = context
        )

        // Then - all entries should be sent as deletions
        assertEquals(2, context.changes.size)
        context.changes.forEach { change ->
            assertEquals(null, change.value, "All entries should be null (deleted)")
        }
    }

    @Test
    fun `changeDelta processes value keys when no node entries`() {
        // Given - new map with no existing version node
        val context = ResolutionDeltaContext<VersionNode, String>()
        val map = mapOf("key1" to "value1", "key2" to "value2")
        val node = VersionNode(version = Version(1L, 100L, 100L)) // No string_map entries
        val versionVector = emptyMap<Long, Long>()

        // When
        mapResolver.changeDelta(
            value = map,
            node = node,
            version = Version(1L, 100L, 100L),
            versionVector = versionVector,
            context = context
        )

        // Then - should process all value keys
        assertEquals(2, context.changes.size)
        val changePaths = context.changes.map { it.pathComponents to it.value }.sortedBy { it.first[0] }
        assertEquals("value1", changePaths[0].second)
        assertEquals("value2", changePaths[1].second)
    }

    @Test
    fun `changeDelta with null node uses version for all entries`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val map = mapOf("key1" to "value1", "key2" to "value2")
        val version = Version(1L, 100L, 100L)
        val versionVector = emptyMap<Long, Long>()

        // When
        mapResolver.changeDelta(
            value = map,
            node = null,
            version = version,
            versionVector = versionVector,
            context = context
        )

        // Then
        assertEquals(2, context.changes.size)
        context.changes.forEach { change ->
            assertEquals(version, change.versionNode.version, "All entries should use the provided version")
        }
    }

    @Test
    fun `changeDelta captures correct path components`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val map = mapOf("alpha" to "A", "beta" to "B", "gamma" to "C")
        val node = VersionNode(
            version = Version(1L, 100L, 100L),
            string_map = mapOf(
                "alpha" to VersionNode(version = Version(1L, 100L, 100L)),
                "beta" to VersionNode(version = Version(1L, 100L, 100L)),
                "gamma" to VersionNode(version = Version(1L, 100L, 100L))
            )
        )
        val versionVector = emptyMap<Long, Long>()

        // When
        mapResolver.changeDelta(
            value = map,
            node = node,
            version = Version(1L, 100L, 100L),
            versionVector = versionVector,
            context = context
        )

        // Then
        assertEquals(3, context.changes.size)
        val paths = context.changes.map { it.pathComponents[0] }.toSet()
        assertEquals(setOf("alpha", "beta", "gamma"), paths, "Should capture each key as path component")
    }
}
