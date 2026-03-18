package co.atoms.protobuf.crdt.resolver.local

import co.atoms.protobuf.crdt.resolver.BooleanMapResolver
import co.atoms.protobuf.crdt.resolver.ResolutionDeltaContext
import co.atoms.protobuf.crdt.resolver.SingleValueResolver
import co.atoms.protobuf.crdt.resolver.TestVersionTreeResolver
import co.atoms.protobuf.crdt.resolver.Version
import co.atoms.protobuf.crdt.resolver.VersionNode
import co.atoms.protobuf.crdt.resolver.descriptor.CollectionType
import co.atoms.protobuf.crdt.resolver.descriptor.KeyType
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BooleanMapCrdtLocalResolverTest {
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
    private val resolver =
        BooleanMapResolver(
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
    fun `bool map resolver works with boolean keys`() {
        // Given
        val currentVersion = Version(1L, 1000L, 1000L)
        val newVersion = Version(1L, 1100L, 1100L)
        val currentMap = mapOf(true to "value1", false to "value2")
        val newMap = mapOf(true to "updated_value") // Update key 1, remove key 2

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
        assertNotNull(result.node?.bool_map, "Should create bool_map")

        val entries = result.node.bool_map
        assertTrue(entries.containsKey(true), "Should track updated key")
        assertTrue(entries.containsKey(false), "Should track removed key (tombstone)")
    }
}
