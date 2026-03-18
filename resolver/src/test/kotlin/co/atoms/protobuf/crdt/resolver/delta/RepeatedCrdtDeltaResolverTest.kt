package co.atoms.protobuf.crdt.resolver.delta

import co.atoms.protobuf.crdt.resolver.RepeatedResolver
import co.atoms.protobuf.crdt.resolver.ResolutionDeltaContext
import co.atoms.protobuf.crdt.resolver.SingleValueResolver
import co.atoms.protobuf.crdt.resolver.TestVersionTreeResolver
import co.atoms.protobuf.crdt.resolver.Version
import co.atoms.protobuf.crdt.resolver.VersionNode
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for RepeatedCrdtDeltaResolver verifying delta computation for repeated fields.
 *
 * Repeated field delta resolution has two phases:
 * 1. Check if the list version itself is in the version vector - if so, send the entire list
 * 2. Otherwise, process each element independently based on element versions
 */
class RepeatedCrdtDeltaResolverTest {
    private val decoder: (ByteArray) -> String = { it.decodeToString() }
    private val encoder: (String) -> ByteArray = { it.toByteArray() }
    private val mockListDecoder: (ByteArray) -> List<String> = mockk()
    private val mockListEncoder: (List<String>) -> ByteArray = mockk()
    private val valueResolver = SingleValueResolver(
        decoder = decoder,
        encoder = encoder,
        versionTreeResolver = TestVersionTreeResolver
    )
    private val repeatedResolver = RepeatedResolver(
        decoder = mockListDecoder,
        encoder = mockListEncoder,
        valueResolver = valueResolver,
        versionTreeResolver = TestVersionTreeResolver
    )

    @Test
    fun `changeDelta sends entire list when list version not in version vector`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val list = listOf("item1", "item2", "item3")
        val node = VersionNode(version = Version(1L, 150L, 150L)) // List version
        val versionVector = mapOf(1L to 100L) // Doesn't include 150

        // When
        repeatedResolver.changeDelta(
            value = list,
            node = node,
            version = Version(1L, 150L, 150L),
            versionVector = versionVector,
            context = context
        )

        // Then - entire list sent as single change
        assertEquals(1, context.changes.size, "Should send entire list when version not in vector")
        assertEquals(list, context.changes[0].value)
        assertEquals(Version(1L, 150L, 150L), context.changes[0].versionNode.version)
        assertEquals(emptyList(), context.changes[0].pathComponents, "List change has no path components")
    }

    @Test
    fun `changeDelta processes elements when list version in version vector`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val list = listOf("item1", "item2", "item3")
        val node = VersionNode(
            version = Version(1L, 50L, 50L), // List version (in vector)
            repeated = listOf(
                VersionNode(version = Version(1L, 100L, 100L)), // Included (100 <= 200)
                VersionNode(version = Version(1L, 150L, 150L)), // Included (150 <= 200)
                VersionNode(version = Version(1L, 250L, 250L)) // NOT included (250 > 200)
            )
        )
        val versionVector = mapOf(1L to 200L) // Includes versions <= 200

        // When
        repeatedResolver.changeDelta(
            value = list,
            node = node,
            version = Version(1L, 50L, 50L),
            versionVector = versionVector,
            context = context
        )

        // Then - only element with version > 200 should be sent
        assertEquals(1, context.changes.size, "Should only send element 2 (version 250)")

        assertEquals(listOf("2"), context.changes[0].pathComponents)
        assertEquals("item3", context.changes[0].value)
        assertEquals(Version(1L, 250L, 250L), context.changes[0].versionNode.version)
    }

    @Test
    fun `changeDelta handles empty list`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val list = emptyList<String>()
        val node = VersionNode(version = Version(1L, 100L, 100L))
        val versionVector = mapOf<Long, Long>()

        // When
        repeatedResolver.changeDelta(
            value = list,
            node = node,
            version = Version(1L, 100L, 100L),
            versionVector = versionVector,
            context = context
        )

        // Then - empty list sent as single change
        assertEquals(1, context.changes.size)
        assertEquals(list, context.changes[0].value)
    }

    @Test
    fun `changeDelta handles null list value`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val node = VersionNode(version = Version(1L, 100L, 100L))
        val versionVector = mapOf<Long, Long>()

        // When
        repeatedResolver.changeDelta(
            value = null,
            node = node,
            version = Version(1L, 100L, 100L),
            versionVector = versionVector,
            context = context
        )

        // Then
        assertEquals(1, context.changes.size)
        assertEquals(null, context.changes[0].value)
    }

    @Test
    fun `changeDelta uses element versions from node entries`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val list = listOf("a", "b", "c")
        val node = VersionNode(
            version = Version(1L, 50L, 50L),
            repeated = listOf(
                VersionNode(version = Version(1L, 100L, 100L)), // Included (100 <= 150)
                VersionNode(version = Version(1L, 200L, 200L)), // NOT included (200 > 150)
                VersionNode(version = Version(1L, 300L, 300L)) // NOT included (300 > 150)
            )
        )
        val versionVector = mapOf(1L to 150L) // Includes versions <= 150

        // When
        repeatedResolver.changeDelta(
            value = list,
            node = node,
            version = Version(1L, 50L, 50L),
            versionVector = versionVector,
            context = context
        )

        // Then - should send elements with versions > 150
        assertEquals(2, context.changes.size)
        val changes = context.changes.sortedBy { it.versionNode.version.actorVersion }
        assertEquals(Version(1L, 200L, 200L), changes[0].versionNode.version)
        assertEquals("b", changes[0].value)
        assertEquals(Version(1L, 300L, 300L), changes[1].versionNode.version)
        assertEquals("c", changes[1].value)
    }

    @Test
    fun `changeDelta uses list version for elements without node entries`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val list = listOf("a", "b", "c")
        val node = VersionNode(
            version = Version(1L, 50L, 50L), // List version (included since 50 <= 100)
            repeated = listOf(
                VersionNode(version = Version(1L, 75L, 75L)) // Element 0 version (included since 75 <= 100)
                // Elements 1 and 2 have no entries, will inherit list version 50
            )
        )
        val versionVector = mapOf(1L to 100L) // Includes versions <= 100

        // When
        repeatedResolver.changeDelta(
            value = list,
            node = node,
            version = Version(1L, 50L, 50L),
            versionVector = versionVector,
            context = context
        )

        // Then - all elements have versions <= 100, so all are included (no changes)
        assertEquals(0, context.changes.size, "All elements included in version vector")
    }

    @Test
    fun `changeDelta processes correct indices`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val list = listOf("zero", "one", "two", "three")
        val node = VersionNode(
            version = Version(1L, 50L, 50L),
            repeated = listOf(
                VersionNode(version = Version(1L, 100L, 100L)),
                VersionNode(version = Version(1L, 200L, 200L)),
                VersionNode(version = Version(1L, 300L, 300L)),
                VersionNode(version = Version(1L, 400L, 400L))
            )
        )
        val versionVector = mapOf(1L to 250L) // Includes versions up to 250

        // When
        repeatedResolver.changeDelta(
            value = list,
            node = node,
            version = Version(1L, 50L, 50L),
            versionVector = versionVector,
            context = context
        )

        // Then - only elements at indices 2 and 3 (versions 300, 400)
        assertEquals(2, context.changes.size)
        val changes = context.changes.sortedBy { it.pathComponents[0] }
        assertEquals(listOf("2"), changes[0].pathComponents, "Index 2")
        assertEquals("two", changes[0].value)
        assertEquals(listOf("3"), changes[1].pathComponents, "Index 3")
        assertEquals("three", changes[1].value)
    }

    @Test
    fun `changeDelta with null node sends entire list`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val list = listOf("item1", "item2")
        val version = Version(1L, 100L, 100L)
        val versionVector = mapOf<Long, Long>()

        // When
        repeatedResolver.changeDelta(
            value = list,
            node = null,
            version = version,
            versionVector = versionVector,
            context = context
        )

        // Then
        assertEquals(1, context.changes.size, "Null node means send entire list")
        assertEquals(list, context.changes[0].value)
        assertEquals(version, context.changes[0].versionNode.version)
    }

    @Test
    fun `changeDelta handles list size change - list grew`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val list = listOf("a", "b", "c", "d", "e") // Size 5
        val node = VersionNode(
            version = Version(1L, 50L, 50L),
            repeated = listOf(
                VersionNode(version = Version(1L, 100L, 100L)),
                VersionNode(version = Version(1L, 100L, 100L)),
                VersionNode(version = Version(1L, 100L, 100L))
                // Only 3 entries, but list now has 5
            )
        )
        val versionVector = mapOf(1L to 200L) // Includes all existing versions

        // When
        repeatedResolver.changeDelta(
            value = list,
            node = node,
            version = Version(1L, 50L, 50L),
            versionVector = versionVector,
            context = context
        )

        // Then - new elements (indices 3, 4) should inherit list version
        assertEquals(0, context.changes.size, "New elements inherit list version (in vector)")
    }

    @Test
    fun `changeDelta handles list size change - list shrunk`() {
        // Given
        val context = ResolutionDeltaContext<VersionNode, String>()
        val list = listOf("a", "b") // Size 2
        val node = VersionNode(
            version = Version(1L, 50L, 50L),
            repeated = listOf(
                VersionNode(version = Version(1L, 100L, 100L)),
                VersionNode(version = Version(1L, 100L, 100L)),
                VersionNode(version = Version(1L, 100L, 100L)),
                VersionNode(version = Version(1L, 100L, 100L)),
                VersionNode(version = Version(1L, 100L, 100L))
                // Had 5 entries, now only 2
            )
        )
        val versionVector = mapOf(1L to 200L) // Includes all versions

        // When
        repeatedResolver.changeDelta(
            value = list,
            node = node,
            version = Version(1L, 50L, 50L),
            versionVector = versionVector,
            context = context
        )

        // Then - only process existing list elements (0, 1)
        assertEquals(0, context.changes.size, "Should only process current list size")
    }
}
