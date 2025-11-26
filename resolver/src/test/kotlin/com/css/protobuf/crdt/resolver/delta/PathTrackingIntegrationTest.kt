package com.css.protobuf.crdt.resolver.delta

import com.css.protobuf.crdt.resolver.ResolutionDeltaContext
import com.css.protobuf.crdt.resolver.SingleValueResolver
import com.css.protobuf.crdt.resolver.StringMapResolver
import com.css.protobuf.crdt.resolver.TestVersionTreeResolver
import com.css.protobuf.crdt.resolver.Version
import com.css.protobuf.crdt.resolver.VersionNode
import com.css.protobuf.crdt.resolver.descriptor.CollectionType
import com.css.protobuf.crdt.resolver.descriptor.KeyType
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Integration tests that validate path tracking through resolver operations. These tests ensure that the delta context
 * correctly captures paths for:
 * - Simple value changes
 * - Map entry changes (with map keys in path)
 * - Nested structure changes
 */
class PathTrackingIntegrationTest {
    private val context = ResolutionDeltaContext<VersionNode, String>()
    private val decoder: (ByteArray) -> String = { it.decodeToString() }
    private val encoder: (String) -> ByteArray = { it.toByteArray() }
    private val mockMapDecoder: (ByteArray) -> Map<String, String> = mockk()
    private val mockMapEncoder: (Map<String, String>) -> ByteArray = mockk()
    private val valueResolver =
        SingleValueResolver(decoder = decoder, encoder = encoder, versionTreeResolver = TestVersionTreeResolver)
    private val mapResolver =
        StringMapResolver(
            config = CollectionType.Map(
                keyType = KeyType.STRING,
                maxTombstone = 5,
                tombstoneTtl = 2000
            ),
            decoder = mockMapDecoder,
            encoder = mockMapEncoder,
            valueResolver = valueResolver,
            versionTreeResolver = TestVersionTreeResolver,
        )

    @Test
    fun `single value change captures empty path`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val resolver =
            SingleValueResolver(decoder = decoder, encoder = encoder, versionTreeResolver = TestVersionTreeResolver)

        // When - apply local write
        resolver.applyLocalWrite(
            currentValue = "old",
            currentNode = VersionNode(version = Version(1L, 100L, 100L)),
            currentVersion = Version(1L, 100L, 100L),
            newValue = "new",
            newVersion = Version(1L, 200L, 200L),
            context = context,
        )

        // Then - change captured at root level
        assertEquals(1, context.changes.size, "Should have 1 change")
        assertEquals(emptyList(), context.changes[0].pathComponents, "Root change has empty path")
        assertEquals("new", context.changes[0].value)
    }

    @Test
    fun `map value change captures map key in path`() {
        // Given
        // When - add new key to map
        mapResolver.applyLocalWrite(
            currentValue = emptyMap(),
            currentNode = null,
            currentVersion = Version(1L, 100L, 100L),
            newValue = mapOf("key1" to "value1"),
            newVersion = Version(1L, 200L, 200L),
            context = context,
        )

        // Then - change should include map key in path
        assertEquals(1, context.changes.size, "Should have 1 change")
        assertEquals(listOf("key1"), context.changes[0].pathComponents, "Should capture map key in path")
        assertEquals("value1", context.changes[0].value)
    }

    @Test
    fun `multiple map changes capture different keys`() {
        // When - add multiple keys
        mapResolver.applyLocalWrite(
            currentValue = emptyMap(),
            currentNode = null,
            currentVersion = Version(1L, 100L, 100L),
            newValue = mapOf("key1" to "value1", "key2" to "value2", "key3" to "value3"),
            newVersion = Version(1L, 200L, 200L),
            context = context,
        )

        // Then - should have 3 changes, each with different key
        assertEquals(3, context.changes.size, "Should have 3 changes")

        // Extract and sort for deterministic testing
        val changePaths = context.changes.map { it.pathComponents to it.value }.sortedBy { it.first[0] }

        assertEquals(listOf("key1"), changePaths[0].first)
        assertEquals("value1", changePaths[0].second)

        assertEquals(listOf("key2"), changePaths[1].first)
        assertEquals("value2", changePaths[1].second)

        assertEquals(listOf("key3"), changePaths[2].first)
        assertEquals("value3", changePaths[2].second)
    }

    @Test
    fun `map update only captures changed keys`() {
        // Given
        val currentMap = mapOf("unchanged" to "same", "changing" to "old")
        val currentNode =
            VersionNode(
                version = Version(1L, 100L, 100L),
                string_map =
                mapOf(
                    "unchanged" to VersionNode(version = Version(1L, 100L, 100L)),
                    "changing" to VersionNode(version = Version(1L, 100L, 100L)),
                ),
            )

        // When - only change one key
        mapResolver.applyLocalWrite(
            currentValue = currentMap,
            currentNode = currentNode,
            currentVersion = Version(1L, 100L, 100L),
            newValue = mapOf("unchanged" to "same", "changing" to "new"),
            newVersion = Version(1L, 200L, 200L),
            context = context,
        )

        // Then - only the changed key should be in changes
        assertEquals(1, context.changes.size, "Should have 1 change (only the changed key)")
        assertEquals(listOf("changing"), context.changes[0].pathComponents)
        assertEquals("new", context.changes[0].value)
    }

    @Test
    fun `map deletion captures removed key`() {
        // Given
        val currentMap = mapOf("key1" to "value1")
        val currentNode =
            VersionNode(
                version = Version(1L, 100L, 100L),
                string_map = mapOf("key1" to VersionNode(version = Version(1L, 100L, 100L))),
            )

        // When - remove key
        mapResolver.applyLocalWrite(
            currentValue = currentMap,
            currentNode = currentNode,
            currentVersion = Version(1L, 100L, 100L),
            newValue = emptyMap(),
            newVersion = Version(1L, 200L, 200L),
            context = context,
        )

        // Then - deletion should be captured with null value
        assertEquals(1, context.changes.size, "Should have 1 change for deletion")
        assertEquals(listOf("key1"), context.changes[0].pathComponents)
        assertEquals(null, context.changes[0].value, "Deleted key should have null value")
    }

    @Test
    fun `incoming resolution captures changes when incoming wins`() {
        // Given
        // When - incoming has higher version
        valueResolver.resolveConflict(
            localValue = "local",
            localNode = VersionNode(version = Version(1L, 100L, 100L)),
            localVersion = Version(1L, 100L, 100L),
            incomingValue = "incoming",
            incomingNode = VersionNode(version = Version(1L, 200L, 200L)),
            incomingVersion = Version(1L, 200L, 200L),
            context = context,
        )

        // Then - incoming change should be captured
        assertEquals(1, context.changes.size, "Should have 1 change")
        assertEquals(emptyList(), context.changes[0].pathComponents)
        assertEquals("incoming", context.changes[0].value)
    }

    @Test
    fun `incoming resolution no changes when local wins`() {
        // Given
        // When - local has higher version
        valueResolver.resolveConflict(
            localValue = "local",
            localNode = VersionNode(version = Version(1L, 200L, 200L)),
            localVersion = Version(1L, 200L, 200L),
            incomingValue = "incoming",
            incomingNode = VersionNode(version = Version(1L, 100L, 100L)),
            incomingVersion = Version(1L, 100L, 100L),
            context = context,
        )

        // Then - no changes captured
        assertEquals(0, context.changes.size, "Should have no changes when local wins")
    }

    @Test
    fun `map incoming resolution captures per-key changes`() {
        // Given
        // Local: {k1: local1 (v200), k2: local2 (v100)}
        val localMap = mapOf("k1" to "local1", "k2" to "local2")
        val localNode =
            VersionNode(
                version = Version(1L, 100L, 100L),
                string_map =
                mapOf(
                    "k1" to VersionNode(version = Version(1L, 200L, 200L)), // k1 newer locally
                    "k2" to VersionNode(version = Version(1L, 100L, 100L)), // k2 older locally
                ),
            )

        // Incoming: {k1: incoming1 (v150), k2: incoming2 (v150)}
        val incomingMap = mapOf("k1" to "incoming1", "k2" to "incoming2")
        val incomingNode =
            VersionNode(
                version = Version(1L, 100L, 100L),
                string_map =
                mapOf(
                    "k1" to VersionNode(version = Version(1L, 150L, 150L)), // k1 older than local
                    "k2" to VersionNode(version = Version(1L, 150L, 150L)), // k2 newer than local
                ),
            )

        // When
        mapResolver.resolveConflict(
            localValue = localMap,
            localNode = localNode,
            localVersion = Version(1L, 100L, 100L),
            incomingValue = incomingMap,
            incomingNode = incomingNode,
            incomingVersion = Version(1L, 100L, 100L),
            context = context,
        )

        // Then - only k2 should be captured (incoming won for that key)
        assertEquals(1, context.changes.size, "Should have 1 change for k2")
        assertEquals(listOf("k2"), context.changes[0].pathComponents)
        assertEquals("incoming2", context.changes[0].value)
    }

    @Test
    fun `encoded method returns null for null values`() {
        // When - delete value (set to null)
        val currentMap = mapOf("key1" to "value1")

        mapResolver.applyLocalWrite(
            currentValue = currentMap,
            currentNode =
            VersionNode(
                version = Version(1L, 100L, 100L),
                string_map = mapOf("key1" to VersionNode(version = Version(1L, 100L, 100L))),
            ),
            currentVersion = Version(1L, 100L, 100L),
            newValue = emptyMap(),
            newVersion = Version(1L, 200L, 200L),
            context = context,
        )

        // Then
        val change = context.changes[0]
        assertEquals(null, change.value)
        assertEquals(null, change.encoded(), "encoded() should return null for null value")
    }

    @Test
    fun `encoded method returns encoded bytes for non-null values`() {
        // When
        valueResolver.applyLocalWrite(
            currentValue = "old",
            currentNode = VersionNode(version = Version(1L, 100L, 100L)),
            currentVersion = Version(1L, 100L, 100L),
            newValue = "new",
            newVersion = Version(1L, 200L, 200L),
            context = context,
        )

        // Then
        val change = context.changes[0]
        assertEquals("new", change.value)
        assertEquals("new".toByteArray().toList(), change.encoded()?.toList(), "encoded() should encode non-null value")
    }
}
