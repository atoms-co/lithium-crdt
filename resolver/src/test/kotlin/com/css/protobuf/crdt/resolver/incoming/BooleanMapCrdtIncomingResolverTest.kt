package com.css.protobuf.crdt.resolver.incoming

import com.css.protobuf.crdt.resolver.BooleanMapResolver
import com.css.protobuf.crdt.resolver.ResolutionDeltaContext
import com.css.protobuf.crdt.resolver.SingleValueResolver
import com.css.protobuf.crdt.resolver.TestVersionTreeResolver
import com.css.protobuf.crdt.resolver.Version
import com.css.protobuf.crdt.resolver.VersionNode
import com.css.protobuf.crdt.resolver.descriptor.CollectionType
import com.css.protobuf.crdt.resolver.descriptor.KeyType
import com.css.protobuf.crdt.resolver.version.ResolutionStrategy
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class BooleanMapCrdtIncomingResolverTest {
    private val context = ResolutionDeltaContext<VersionNode, String>()
    private val decoder: (ByteArray) -> String = { it.decodeToString() }
    private val encoder: (String) -> ByteArray = { it.toByteArray() }
    private val valueResolver = SingleValueResolver(
        decoder = decoder,
        encoder = encoder,
        versionTreeResolver = TestVersionTreeResolver
    )
    private val mockMapDecoder: (ByteArray) -> Map<Boolean, String> = mockk()
    private val mockMapEncoder: (Map<Boolean, String>) -> ByteArray = mockk()
    private val resolver = BooleanMapResolver(
        config = CollectionType.Map(
            keyType = KeyType.BOOL,
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
        val map = mapOf(true to "value1", false to "value2")
        val entries =
            mapOf(true to VersionNode(version = version), false to VersionNode(version = version))
        val node = VersionNode(version = version, bool_map = (entries))

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

        // Local: {true: "local1", false: "local2"}
        val localMap = mapOf(true to "local1", false to "local2")
        val localEntries =
            mapOf(
                true to VersionNode(version = Version(1L, 1200L, 1200L)), // k1 has newer version locally
                false to VersionNode(version = Version(1L, 900L, 900L)), // k2 has older version locally
            )
        val localNode = VersionNode(version = localVersion, bool_map = (localEntries))

        // Incoming: {true: "incoming1", false: "incoming2"}
        val incomingMap = mapOf(true to "incoming1", false to "incoming2")
        val incomingEntries =
            mapOf(
                true to VersionNode(version = Version(1L, 1100L, 1100L)), // k1 older than local
                false to VersionNode(version = Version(1L, 1000L, 1000L)), // k2 newer than local
            )
        val incomingNode = VersionNode(version = incomingVersion, bool_map = (incomingEntries))

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
        assertEquals("local1", result.value?.get(true), "k1 should use local (newer version)")
        assertEquals("incoming2", result.value?.get(false), "k2 should use incoming (newer version)")
    }

    @Test
    fun `add and remove keys`() {
        // Given
        val localVersion = Version(1L, 1000L, 1000L)
        val incomingVersion = Version(1L, 1100L, 1100L)

        // Local: {true: "value", false: "gone"}
        val localMap = mapOf(true to "value", false to "gone")
        val localEntries =
            mapOf(
                true to VersionNode(version = localVersion),
                false to VersionNode(version = localVersion),
            )
        val localNode = VersionNode(version = localVersion, bool_map = (localEntries))

        // Incoming: {true: "value", "new_key": "new_value"}
        val incomingMap = mapOf(true to "value")
        val incomingEntries =
            mapOf(
                true to VersionNode(version = localVersion), // Same version
                false to VersionNode(version = incomingVersion), // New key
            )
        val incomingNode = VersionNode(version = incomingVersion, bool_map = (incomingEntries))

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
        assertEquals("value", result.value?.get(true), "Should keep existing key")
        assertEquals(null, result.value?.get(false), "Should add new key")
        assertEquals(false, result.value?.containsKey(false), "Should not contain removed key")
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
        assertEquals(emptyMap<Boolean, String>(), result.value, "Should return empty map")
    }
}
