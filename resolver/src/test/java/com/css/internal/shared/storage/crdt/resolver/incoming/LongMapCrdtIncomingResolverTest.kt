package com.css.internal.shared.storage.crdt.resolver.incoming

import com.css.internal.shared.storage.crdt.resolver.LongMapResolver
import com.css.internal.shared.storage.crdt.resolver.ResolutionDeltaContext
import com.css.internal.shared.storage.crdt.resolver.SingleValueResolver
import com.css.internal.shared.storage.crdt.resolver.TestVersionTreeResolver
import com.css.internal.shared.storage.crdt.resolver.Version
import com.css.internal.shared.storage.crdt.resolver.VersionNode
import com.css.internal.shared.storage.crdt.resolver.descriptor.CollectionType
import com.css.internal.shared.storage.crdt.resolver.descriptor.KeyType
import com.css.internal.shared.storage.crdt.resolver.version.ResolutionStrategy
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class LongMapCrdtIncomingResolverTest {
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
    private val resolver =
        LongMapResolver(
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
    fun `identical maps - no change`() {
        // Given
        val version = Version(1L, 1000L, 1000L)
        val map = mapOf(1L to "value1", 2L to "value2")
        val entries =
            mapOf(1L to VersionNode(version = version), 2L to VersionNode(version = version))
        val node = VersionNode(version = version, long_map = (entries))

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
        val localMap = mapOf(1L to "local1", 2L to "local2", 3L to "local3")
        val localEntries =
            mapOf(
                1L to VersionNode(version = Version(1L, 1200L, 1200L)), // k1 has newer version locally
                2L to VersionNode(version = Version(1L, 900L, 900L)), // k2 has older version locally
                3L to VersionNode(version = Version(1L, 900L, 900L)),
            )
        val localNode = VersionNode(version = localVersion, long_map = (localEntries))

        // Incoming: {1: "incoming1", 2: "incoming2"}
        val incomingMap = mapOf(1L to "incoming1", 2L to "incoming2")
        val incomingEntries =
            mapOf(
                1L to VersionNode(version = Version(1L, 1100L, 1100L)), // k1 older than local
                2L to VersionNode(version = Version(1L, 1000L, 1000L)), // k2 newer than local
            )
        val incomingNode = VersionNode(version = incomingVersion, long_map = (incomingEntries))

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
        assertEquals("local1", result.value?.get(1), "k1 should use local (newer version)")
        assertEquals("incoming2", result.value?.get(2), "k2 should use incoming (newer version)")
        assertEquals("local3", result.value?.get(3), "k3 should merge")
    }

    @Test
    fun `add and remove keys`() {
        // Given
        val localVersion = Version(1L, 1000L, 1000L)
        val incomingVersion = Version(1L, 1100L, 1100L)

        // Local: {1: "value", 2: "gone"}
        val localMap = mapOf(1L to "value", 2L to "gone")
        val localEntries =
            mapOf(
                1L to VersionNode(version = localVersion),
                2L to VersionNode(version = localVersion),
            )
        val localNode = VersionNode(version = localVersion, long_map = (localEntries))

        // Incoming: {1: "value", 3: "new_value"}
        val incomingMap = mapOf(1L to "value", 3L to "new_value")
        val incomingEntries =
            mapOf(
                1L to VersionNode(version = localVersion), // Same version
                2L to VersionNode(version = incomingVersion), // Removed key
                3L to VersionNode(version = incomingVersion), // New key
            )
        val incomingNode = VersionNode(version = incomingVersion, long_map = (incomingEntries))

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
        assertEquals("value", result.value?.get(1), "Should keep existing key")
        assertEquals("new_value", result.value?.get(3), "Should add new key")
        assertEquals(false, result.value?.containsKey(2), "Should not contain removed key")
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
        assertEquals(emptyMap<Long, String>(), result.value, "Should return empty map")
    }
}
