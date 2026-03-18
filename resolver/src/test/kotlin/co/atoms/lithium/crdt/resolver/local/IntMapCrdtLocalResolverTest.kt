package co.atoms.lithium.crdt.resolver.local

import co.atoms.lithium.crdt.resolver.IntMapResolver
import co.atoms.lithium.crdt.resolver.ResolutionDeltaContext
import co.atoms.lithium.crdt.resolver.SingleValueResolver
import co.atoms.lithium.crdt.resolver.TestVersionTreeResolver
import co.atoms.lithium.crdt.resolver.Version
import co.atoms.lithium.crdt.resolver.VersionNode
import co.atoms.lithium.crdt.resolver.descriptor.CollectionType
import co.atoms.lithium.crdt.resolver.descriptor.KeyType
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IntMapCrdtLocalResolverTest {
    private val context = ResolutionDeltaContext<VersionNode, String>()
    private val decoder: (ByteArray) -> String = { it.decodeToString() }
    private val encoder: (String) -> ByteArray = { it.toByteArray() }
    private val valueResolver = SingleValueResolver(
        decoder = decoder,
        encoder = encoder,
        versionTreeResolver = TestVersionTreeResolver
    )
    private val mockMapDecoder: (ByteArray) -> Map<Int, String> = mockk()
    private val mockMapEncoder: (Map<Int, String>) -> ByteArray = mockk()
    private val resolver =
        IntMapResolver(
            config = CollectionType.Map(
                keyType = KeyType.INT,
                maxTombstone = 5,
                tombstoneTtl = 2000
            ),
            decoder = mockMapDecoder,
            encoder = mockMapEncoder,
            valueResolver = valueResolver,
            versionTreeResolver = TestVersionTreeResolver,
        )

    @Test
    fun `int map resolver works with integer keys`() {
        // Given
        val currentVersion = Version(1L, 1000L, 1000L)
        val newVersion = Version(1L, 1100L, 1100L)
        val currentMap = mapOf(1 to "value1", 2 to "value2")
        val newMap = mapOf(1 to "updated_value", 3 to "new_value") // Update key 1, add key 3, remove key 2

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
        assertNotNull(result.node?.int_map, "Should create int_map")

        val entries = result.node.int_map
        assertTrue(entries.containsKey(1), "Should track updated key")
        assertTrue(entries.containsKey(3), "Should track new key")
        assertTrue(entries.containsKey(2), "Should track removed key (tombstone)")
    }
}
