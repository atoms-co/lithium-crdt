package co.atoms.lithium.crdt.resolver.local

import co.atoms.lithium.crdt.resolver.ResolutionDeltaContext
import co.atoms.lithium.crdt.resolver.SingleValueResolver
import co.atoms.lithium.crdt.resolver.StringMapResolver
import co.atoms.lithium.crdt.resolver.TestVersionTreeResolver
import co.atoms.lithium.crdt.resolver.Version
import co.atoms.lithium.crdt.resolver.VersionNode
import co.atoms.lithium.crdt.resolver.descriptor.CollectionType
import co.atoms.lithium.crdt.resolver.descriptor.KeyType
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StringMapCrdtLocalResolverTest {
    private val context = ResolutionDeltaContext<VersionNode, String>()
    private val decoder: (ByteArray) -> String = { it.decodeToString() }
    private val encoder: (String) -> ByteArray = { it.toByteArray() }
    private val valueResolver = SingleValueResolver(
        decoder = decoder,
        encoder = encoder,
        versionTreeResolver = TestVersionTreeResolver
    )
    private val mockMapDecoder: (ByteArray) -> Map<String, String> = mockk()
    private val mockMapEncoder: (Map<String, String>) -> ByteArray = mockk()
    private val resolver =
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
    fun `fast path - maps are equal`() {
        // Given
        val currentVersion = Version(1, 1000L, 1000L)
        val newVersion = Version(1, 1100L, 1100L)
        val currentMap = mapOf("key1" to "value1", "key2" to "value2")
        val newMap = mapOf("key1" to "value1", "key2" to "value2") // Same content
        val currentNode =
            VersionNode(
                version = currentVersion,
                string_map =
                (
                    mapOf(
                        "key1" to VersionNode(version = currentVersion),
                        "key2" to VersionNode(version = currentVersion),
                    )
                    ),
            )

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = currentMap,
                currentNode = currentNode,
                currentVersion = currentVersion,
                newValue = newMap,
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertFalse(result.resolution, "Should not resolve when maps are equal")
        assertEquals(currentMap, result.value, "Should return current map")
        assertEquals(currentNode, result.node, "Should return current node")
    }

    @Test
    fun `fast path - both maps null`() {
        // Given
        val currentVersion = Version(1, 1000L, 1000L)
        val newVersion = Version(1, 1100L, 1100L)

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = null,
                currentNode = null,
                currentVersion = currentVersion,
                newValue = null,
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertFalse(result.resolution, "Should not resolve when both maps are null")
        assertEquals(emptyMap(), result.value, "Should return empty map")
        assertEquals(currentVersion, result.node?.version, "Should create node with current version")
        assertEquals(emptyMap(), result.node?.string_map, "Should not create string_map for null maps")
    }

    @Test
    fun `add new key to empty map`() {
        // Given
        val currentVersion = Version(1, 1000L, 1000L)
        val newVersion = Version(1, 1100L, 1100L)
        val currentMap = emptyMap<String, String>()
        val newMap = mapOf("key1" to "value1")

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
        assertTrue(result.resolution, "Should resolve when adding key")
        assertEquals(newMap, result.value, "Should return new map")
        assertEquals(newVersion, result.node?.version, "Should set map version")
        assertNotNull(result.node?.string_map, "Should create string_map")
        assertEquals(newVersion, result.node.string_map["key1"]?.version, "Should track key version")
    }

    @Test
    fun `update existing key value`() {
        // Given
        val currentVersion = Version(1, 1000L, 1000L)
        val newVersion = Version(1, 1100L, 1100L)
        val currentMap = mapOf("key1" to "old_value")
        val newMap = mapOf("key1" to "new_value")
        val currentNode =
            VersionNode(
                version = currentVersion,
                string_map = (mapOf("key1" to VersionNode(version = currentVersion))),
            )

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = currentMap,
                currentNode = currentNode,
                currentVersion = currentVersion,
                newValue = newMap,
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertTrue(result.resolution, "Should resolve when updating value")
        assertEquals(newMap, result.value, "Should return updated map")
        assertEquals(newVersion, result.node?.version, "Should set map version")
        assertEquals(newVersion, result.node?.string_map?.get("key1")?.version, "Should update key version")
    }

    @Test
    fun `remove key from map`() {
        // Given
        val currentVersion = Version(1, 1000L, 1000L)
        val newVersion = Version(1, 1100L, 1100L)
        val currentMap = mapOf("key1" to "value1", "key2" to "value2")
        val newMap = mapOf("key1" to "value1") // key2 removed
        val currentNode =
            VersionNode(
                version = currentVersion,
                string_map =
                (
                    mapOf(
                        "key1" to VersionNode(version = currentVersion),
                        "key2" to VersionNode(version = currentVersion),
                    )
                    ),
            )

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = currentMap,
                currentNode = currentNode,
                currentVersion = currentVersion,
                newValue = newMap,
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertTrue(result.resolution, "Should resolve when removing key")
        assertEquals(newMap, result.value, "Should return map without removed key")
        assertEquals(newVersion, result.node?.version, "Should set map version")
        assertTrue(result.node?.string_map?.containsKey("key1") == true, "Should still contain key1")
        assertTrue(
            result.node.string_map.containsKey("key2"),
            "Should track removed key2 with tombstone",
        )
        assertEquals(
            newVersion,
            result.node.string_map["key2"]?.version,
            "Should update removed key version",
        )
    }

    @Test
    fun `unchanged key preserves existing version tracking`() {
        // Given
        val currentVersion = Version(1, 1000L, 1000L)
        val oldKeyVersion = Version(1L, 800L, 800L) // Older version
        val newVersion = Version(1, 1100L, 1100L)
        val currentMap = mapOf("unchanged" to "same_value", "changing" to "old_value")
        val newMap = mapOf("unchanged" to "same_value", "changing" to "new_value")
        val currentNode =
            VersionNode(
                version = currentVersion,
                string_map =
                (
                    mapOf(
                        "unchanged" to VersionNode(version = oldKeyVersion),
                        "changing" to VersionNode(version = currentVersion),
                    )
                    ),
            )

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = currentMap,
                currentNode = currentNode,
                currentVersion = currentVersion,
                newValue = newMap,
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertTrue(result.resolution, "Should resolve due to changing key")
        assertEquals(newMap, result.value, "Should return updated map")
        assertEquals(
            oldKeyVersion,
            result.node?.string_map?.get("unchanged")?.version,
            "Should preserve unchanged key version",
        )
        assertEquals(
            newVersion,
            result.node?.string_map?.get("changing")?.version,
            "Should update changing key version",
        )
    }

    @Test
    fun `mixed operations - add, update, remove`() {
        // Given
        val currentVersion = Version(1, 1000L, 1000L)
        val newVersion = Version(1, 1100L, 1100L)
        val currentMap = mapOf("keep" to "same_value", "update" to "old_value", "remove" to "will_be_gone")
        val newMap = mapOf("keep" to "same_value", "update" to "new_value", "add" to "new_key")
        val currentNode =
            VersionNode(
                version = currentVersion,
                string_map =
                (
                    mapOf(
                        "keep" to VersionNode(version = currentVersion),
                        "update" to VersionNode(version = currentVersion),
                        "remove" to VersionNode(version = currentVersion),
                    )
                    ),
            )

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = currentMap,
                currentNode = currentNode,
                currentVersion = currentVersion,
                newValue = newMap,
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertTrue(result.resolution, "Should resolve due to changes")
        assertEquals(newMap, result.value, "Should return updated map")

        val entries = result.node?.string_map
        assertNotNull(entries, "Should have string_map entries")

        // Check all keys are tracked
        assertTrue(entries.containsKey("keep"), "Should track kept key")
        assertTrue(entries.containsKey("update"), "Should track updated key")
        assertTrue(entries.containsKey("add"), "Should track added key")
        assertTrue(entries.containsKey("remove"), "Should track removed key (tombstone)")

        // Check versions
        assertEquals(currentVersion, entries["keep"]?.version, "Should preserve kept key version")
        assertEquals(newVersion, entries["update"]?.version, "Should update changed key version")
        assertEquals(newVersion, entries["add"]?.version, "Should set new key version")
        assertEquals(newVersion, entries["remove"]?.version, "Should set removal version")
    }

    @Test
    fun `remove all keys creates tombstones in string_map`() {
        // Given - remove all keys from map
        val currentVersion = Version(1, 1000L, 1000L)
        val newVersion = Version(1, 1100L, 1100L)
        val currentMap = mapOf("key1" to "value1")
        val newMap = emptyMap<String, String>() // Remove all

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
        assertTrue(result.resolution, "Should resolve when removing all keys")
        assertEquals(emptyMap<String, String>(), result.value, "Should return empty map")
        assertEquals(newVersion, result.node?.version, "Should set map version")
        assertNotNull(result.node?.string_map, "Should create string_map with tombstones")
        assertTrue(
            result.node.string_map.containsKey("key1"),
            "Should contain tombstone for removed key",
        )
        assertEquals(newVersion, result.node.string_map["key1"]?.version, "Should set tombstone version")
    }

    @Test
    fun `empty to empty map creates no string_map field`() {
        // Given - both current and new maps are empty, no changes needed
        val currentVersion = Version(1, 1000L, 1000L)
        val newVersion = Version(1, 1100L, 1100L)
        val currentMap = emptyMap<String, String>()
        val newMap = emptyMap<String, String>()

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
        assertFalse(result.resolution, "Should not resolve when both maps are empty")
        assertEquals(emptyMap(), result.value, "Should return empty map")
        assertEquals(currentVersion, result.node?.version, "Should create node with current version")
        assertEquals(emptyMap(), result.node?.string_map, "Should not create string_map for null maps")
    }

    @Test
    fun `version progression works in map values`() {
        // Given - value resolver will apply ensureAfter logic
        val currentVersion = Version(1, 1L, 1000L)
        val oldValueVersion = Version(1L, 2L, 1200L) // Newer than map
        val newVersion = Version(1, 3L, 1100L) // Older than current
        val expectedVersion = Version(1L, 3L, 1201L) // ensureAfter result

        val currentMap = mapOf("key1" to "old_value")
        val newMap = mapOf("key1" to "new_value")
        val currentNode =
            VersionNode(
                version = currentVersion,
                string_map = (mapOf("key1" to VersionNode(version = oldValueVersion))),
            )

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = currentMap,
                currentNode = currentNode,
                currentVersion = currentVersion,
                newValue = newMap,
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertTrue(result.resolution, "Should resolve")
        assertEquals(
            expectedVersion,
            result.node?.string_map?.get("key1")?.version,
            "Should apply ensureAfter to value version",
        )
    }

    @Test
    fun `initializes version tracking for previously untracked unchanged keys`() {
        // Given - existing map without version tracking
        val currentVersion = Version(1, 1000L, 1000L)
        val newVersion = Version(1, 1100L, 1100L)
        val currentMap = mapOf("untracked" to "same_value")
        val newMap = mapOf("untracked" to "same_value") // Same value
        val currentNode = null // No existing version tracking

        // When
        val result =
            resolver.applyLocalWrite(
                currentValue = currentMap,
                currentNode = currentNode,
                currentVersion = currentVersion,
                newValue = newMap,
                newVersion = newVersion,
                context = context,
            )

        // Then
        assertFalse(result.resolution, "Should not resolve when no changes")
        assertEquals(currentMap, result.value, "Should return current map")
        // Note: The logic initializes version tracking in the internal entries map,
        // but since resolution is false, the current node is returned unchanged
        assertEquals(currentVersion, result.node?.version, "Should preserve current version")
    }
}
