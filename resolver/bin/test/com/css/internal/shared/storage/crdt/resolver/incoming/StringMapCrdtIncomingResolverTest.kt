package com.css.internal.shared.storage.crdt.resolver.incoming

import com.css.internal.shared.storage.crdt.resolver.ResolutionDeltaContext
import com.css.internal.shared.storage.crdt.resolver.SingleValueResolver
import com.css.internal.shared.storage.crdt.resolver.StringMapResolver
import com.css.internal.shared.storage.crdt.resolver.TestVersionTreeResolver
import com.css.internal.shared.storage.crdt.resolver.Version
import com.css.internal.shared.storage.crdt.resolver.VersionNode
import com.css.internal.shared.storage.crdt.resolver.descriptor.CollectionType
import com.css.internal.shared.storage.crdt.resolver.descriptor.KeyType
import com.css.internal.shared.storage.crdt.resolver.version.ResolutionStrategy
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class StringMapCrdtIncomingResolverTest {
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
    fun `identical maps - no change`() {
        // Given
        val version = Version(1L, 1000L, 1000L)
        val map = mapOf("key1" to "value1", "key2" to "value2")
        val entries =
            mapOf("key1" to VersionNode(version = version), "key2" to VersionNode(version = version))
        val node = VersionNode(version = version, string_map = entries)

        // When
        val result =
            resolver.resolveConflict(
                localValue = map,
                localNode = node,
                localVersion = version,
                incomingValue = map, // Same map
                incomingNode = node, // Same node
                incomingVersion = version,
                context = context,
            )

        // Then
        assertEquals(ResolutionStrategy.NO_CHANGE, result.resolution, "Should not change when identical")
        assertEquals(map, result.value, "Should return same map")
        assertEquals(node, result.node, "Should return same node")
    }

    @Test
    fun `per-key conflict resolution`() {
        // Given
        val localVersion = Version(1L, 1000L, 1000L)
        val incomingVersion = Version(1L, 1100L, 1100L)

        // Local: {1: "local1", 2: "local2"}
        val localMap = mapOf("k1" to "local1", "k2" to "local2", "k3" to "local3")
        val localEntries =
            mapOf(
                "k1" to VersionNode(version = Version(1L, 1200L, 1200L)), // k1 has newer version locally
                "k2" to VersionNode(version = Version(1L, 900L, 900L)), // k2 has older version locally
                "k3" to VersionNode(version = Version(1L, 900L, 900L)),
            )
        val localNode = VersionNode(version = localVersion, string_map = (localEntries))

        // Incoming: {1: "incoming1", 2: "incoming2"}
        val incomingMap = mapOf("k1" to "incoming1", "k2" to "incoming2")
        val incomingEntries =
            mapOf(
                "k1" to VersionNode(version = Version(1L, 1100L, 1100L)), // k1 older than local
                "k2" to VersionNode(version = Version(1L, 1000L, 1000L)), // k2 newer than local
            )
        val incomingNode = VersionNode(version = incomingVersion, string_map = (incomingEntries))

        // When
        val result =
            resolver.resolveConflict(
                localValue = localMap,
                localNode = localNode,
                localVersion = localVersion,
                incomingValue = incomingMap,
                incomingNode = incomingNode,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then
        assertEquals(ResolutionStrategy.MERGED_VALUES, result.resolution, "Should merge values")
        assertEquals("local1", result.value?.get("k1"), "k1 should use local (newer version)")
        assertEquals("incoming2", result.value?.get("k2"), "k2 should use incoming (newer version)")
        assertEquals("local3", result.value?.get("k3"), "k3 should merge")
    }

    @Test
    fun `add and remove keys`() {
        // Given
        val localVersion = Version(1L, 1000L, 1000L)
        val incomingVersion = Version(1L, 1100L, 1100L)

        // Local: {"existing": "value", "remove_me": "gone"}
        val localMap = mapOf("existing" to "value", "remove_me" to "gone")
        val localEntries =
            mapOf(
                "existing" to VersionNode(version = localVersion),
                "remove_me" to VersionNode(version = localVersion),
            )
        val localNode = VersionNode(version = localVersion, string_map = (localEntries))

        // Incoming: {"existing": "value", "new_key": "new_value"}
        val incomingMap = mapOf("existing" to "value", "new_key" to "new_value")
        val incomingEntries =
            mapOf(
                "existing" to VersionNode(version = localVersion), // Same version
                "remove_me" to VersionNode(version = incomingVersion), // Removed key
                "new_key" to VersionNode(version = incomingVersion), // New key
            )
        val incomingNode = VersionNode(version = incomingVersion, string_map = (incomingEntries))

        // When
        val result =
            resolver.resolveConflict(
                localValue = localMap,
                localNode = localNode,
                localVersion = localVersion,
                incomingValue = incomingMap,
                incomingNode = incomingNode,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then
        assertEquals(ResolutionStrategy.INCOMING, result.resolution, "Should adopt incoming")
        assertEquals("value", result.value?.get("existing"), "Should keep existing key")
        assertEquals("new_value", result.value?.get("new_key"), "Should add new key")
        assertEquals(false, result.value?.containsKey("remove_me"), "Should not contain removed key")
    }

    @Test
    fun `empty maps`() {
        // Given
        val localVersion = Version(1L, 1000L, 1000L)
        val incomingVersion = Version(1L, 1100L, 1100L)

        // When
        val result =
            resolver.resolveConflict(
                localValue = emptyMap(),
                localNode = null,
                localVersion = localVersion,
                incomingValue = emptyMap(),
                incomingNode = null,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then
        assertEquals(ResolutionStrategy.NO_CHANGE, result.resolution, "Should not change empty maps")
        assertEquals(emptyMap<String, String>(), result.value, "Should return empty map")
    }

    @Test
    fun `MERGED_VALUES should use resultEntries not localEntries in node`() {
        // This test captures a bug where MERGED_VALUES incorrectly uses localEntries
        // instead of resultEntries when creating the result node

        // Given
        val localVersion = Version(1L, 1000L, 1000L)
        val incomingVersion = Version(1L, 1100L, 1100L)

        // Local has keys: k1 (v1000), k2 (v1200)
        val localMap = mapOf("k1" to "local1", "k2" to "local2")
        val localEntries =
            mapOf(
                "k1" to VersionNode(version = Version(1L, 1000L, 1000L)),
                "k2" to VersionNode(version = Version(1L, 1200L, 1200L)), // k2 wins locally
            )
        val localNode = VersionNode(version = localVersion, string_map = localEntries)

        // Incoming has keys: k1 (v1500), k3 (v1100)
        val incomingMap = mapOf("k1" to "incoming1", "k3" to "incoming3")
        val incomingEntries =
            mapOf(
                "k1" to VersionNode(version = Version(1L, 1500L, 1500L)), // k1 wins from incoming
                "k3" to VersionNode(version = Version(1L, 1100L, 1100L)), // k3 is new from incoming
            )
        val incomingNode = VersionNode(version = incomingVersion, string_map = incomingEntries)

        // When
        val result =
            resolver.resolveConflict(
                localValue = localMap,
                localNode = localNode,
                localVersion = localVersion,
                incomingValue = incomingMap,
                incomingNode = incomingNode,
                incomingVersion = incomingVersion,
                context = context,
            )

        // Then
        assertEquals(ResolutionStrategy.MERGED_VALUES, result.resolution, "Should be MERGED_VALUES")

        // Verify the merged value map contains all three keys with correct values
        assertEquals("incoming1", result.value?.get("k1"), "k1 should use incoming (v1500 > v1000)")
        assertEquals("local2", result.value?.get("k2"), "k2 should use local (only in local)")
        assertEquals("incoming3", result.value?.get("k3"), "k3 should use incoming (only in incoming)")

        // CRITICAL: The result node should contain ALL merged entries (k1, k2, k3)
        // NOT just localEntries (k1, k2)
        val resultStringMap = result.node?.string_map
        assertEquals(3, resultStringMap?.size, "Result node should have 3 entries (k1, k2, k3), not just local entries")
        assertEquals(true, resultStringMap?.containsKey("k1"), "Result node should contain k1")
        assertEquals(true, resultStringMap?.containsKey("k2"), "Result node should contain k2")
        assertEquals(true, resultStringMap?.containsKey("k3"), "Result node should contain k3 from incoming")

        // Verify versions in result node are correct
        assertEquals(1500L, resultStringMap?.get("k1")?.version?.actorVersion, "k1 should have incoming version")
        assertEquals(1200L, resultStringMap?.get("k2")?.version?.actorVersion, "k2 should have local version")
        assertEquals(1100L, resultStringMap?.get("k3")?.version?.actorVersion, "k3 should have incoming version")
    }
}
